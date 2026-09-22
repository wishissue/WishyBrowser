(function() {
    const FOCUSABLE_SELECTOR = 'a, button, input, select, textarea, [tabindex="0"], [onclick], [role="button"]';
    let focusables = [];
    const port = browser.runtime.connectNative("wishy_core");

    function updateFocusables() {
        focusables = Array.from(document.querySelectorAll(FOCUSABLE_SELECTOR))
            .filter(el => {
                const rect = el.getBoundingClientRect();
                return rect.width > 0 && rect.height > 0 &&
                       !el.disabled &&
                       window.getComputedStyle(el).visibility !== 'hidden' &&
                       window.getComputedStyle(el).display !== 'none';
            });
    }

    const observer = new MutationObserver(() => {
        if (this.timer) clearTimeout(this.timer);
        this.timer = setTimeout(updateFocusables, 500);
    });

    observer.observe(document.body, { childList: true, subtree: true });
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
        focusables.forEach(el => {
            const rect = el.getBoundingClientRect();
            const center = getCenter(rect);
            const dist = Math.sqrt(Math.pow(center.x - x, 2) + Math.pow(center.y - y, 2));
            if (dist < 100 && dist < minDistance) {
                minDistance = dist;
                closest = { x: center.x, y: center.y, width: rect.width, height: rect.height };
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
            outline: 4px solid #ffffff !important;
            outline-offset: 2px !important;
            box-shadow: 0 0 15px rgba(255,255,255,0.6) !important;
        }
        /* Enlarge small tap targets for TV usability */
        a, button, [role="button"], input[type="submit"] {
            min-width: 44px !important;
            min-height: 44px !important;
            box-sizing: border-box !important;
        }
    `;
    document.head.appendChild(style);
})();
