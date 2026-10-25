(function() {
    const FOCUSABLE_SELECTOR = 'a, button, input, select, textarea, [tabindex="0"], [onclick], [role="button"]';
    let focusables = [];
    let isUpdating = false;
    let updateTimer = null;
    const port = browser.runtime.connectNative("wishy_core");

    function updateFocusables() {
        if (isUpdating) return;
        isUpdating = true;
        try {
            const all = document.querySelectorAll(FOCUSABLE_SELECTOR);
            const count = Math.min(all.length, 300);
            const list = [];
            for (let i = 0; i < count; i++) {
                const el = all[i];
                if (el.offsetWidth > 0 || el.offsetHeight > 0) {
                    if (!el.disabled) {
                        list.push(el);
                    }
                }
            }
            focusables = list;
        } catch (e) {
            /* ignore */
        } finally {
            isUpdating = false;
        }
    }

    function scheduleUpdate() {
        if (updateTimer) clearTimeout(updateTimer);
        updateTimer = setTimeout(() => {
            updateTimer = null;
            if (window.requestIdleCallback) {
                window.requestIdleCallback(updateFocusables, { timeout: 1000 });
            } else {
                updateFocusables();
            }
        }, 1200);
    }

    if (document.body) {
        const observer = new MutationObserver(scheduleUpdate);
        observer.observe(document.body, { childList: true });
    } else {
        document.addEventListener('DOMContentLoaded', () => {
            if (document.body) {
                const observer = new MutationObserver(scheduleUpdate);
                observer.observe(document.body, { childList: true });
            }
        });
    }
    updateFocusables();

    function getCenter(rect) {
        return {
            x: rect.left + rect.width / 2,
            y: rect.top + rect.height / 2
        };
    }

    function spatialNavigate(direction) {
        const active = document.activeElement;
        if (!focusables.length) updateFocusables();
        if (!focusables.length) return false;

        let activeCenter;
        if (!active || !focusables.includes(active) || active === document.body) {
            activeCenter = { x: 0, y: 0 };
        } else {
            activeCenter = getCenter(active.getBoundingClientRect());
        }

        let bestElement = null;
        let minScore = Infinity;

        focusables.forEach(el => {
            if (el === active) return;
            const rect = el.getBoundingClientRect();
            const center = getCenter(rect);
            let isValid = false;
            let forwardDist = 0, orthoDist = 0;

            switch (direction) {
                case 'Up': if (center.y < activeCenter.y) { isValid = true; forwardDist = activeCenter.y - center.y; orthoDist = Math.abs(activeCenter.x - center.x); } break;
                case 'Down': if (center.y > activeCenter.y) { isValid = true; forwardDist = center.y - activeCenter.y; orthoDist = Math.abs(activeCenter.x - center.x); } break;
                case 'Left': if (center.x < activeCenter.x) { isValid = true; forwardDist = activeCenter.x - center.x; orthoDist = Math.abs(activeCenter.y - center.y); } break;
                case 'Right': if (center.x > activeCenter.x) { isValid = true; forwardDist = center.x - activeCenter.x; orthoDist = Math.abs(activeCenter.y - center.y); } break;
            }

            if (isValid) {
                const score = forwardDist + (orthoDist * 2.5);
                if (score < minScore) { minScore = score; bestElement = el; }
            }
        });

        if (bestElement) {
            bestElement.focus();
            bestElement.scrollIntoView({ block: 'nearest', inline: 'nearest' });
            return true;
        }
        return false;
    }

    function findClosest(x, y) {
        if (!focusables.length) updateFocusables();
        let closest = null, minDistance = Infinity;
        const maxDist = 120;
        focusables.forEach(el => {
            const rect = el.getBoundingClientRect();
            const center = getCenter(rect);
            const dx = center.x - x;
            const dy = center.y - y;
            if (Math.abs(dx) < maxDist && Math.abs(dy) < maxDist) {
                const dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < maxDist && dist < minDistance) {
                    minDistance = dist;
                    closest = { x: center.x, y: center.y, width: rect.width, height: rect.height };
                }
            }
        });
        return closest;
    }

    port.onMessage.addListener((msg) => {
        if (msg.type === "navigate") {
            const handled = spatialNavigate(msg.direction);
            port.postMessage({ type: "navigate_result", handled: handled });
        } else if (msg.type === "find_closest") {
            const result = findClosest(msg.x, msg.y);
            port.postMessage({ type: "find_closest_result", result: result });
        }
    });

    const style = document.createElement('style');
    style.innerHTML = `
        :focus {
            outline: 3px solid #38bdf8 !important;
            outline-offset: 2px !important;
            box-shadow: 0 0 12px rgba(56,189,248,0.6) !important;
        }
    `;
    document.head.appendChild(style);
})();
