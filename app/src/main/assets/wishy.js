(function(){ if (window.__wy) return; window.__wy = 1;
(function(){var id="__wy_css";if(document.getElementById(id))return;var s=document.createElement("style");s.id=id;
s.textContent='ins.adsbygoogle,.adsbygoogle,[id^="google_ads_iframe"],[id^="div-gpt-ad"],[id^="google_ads_"],iframe[src*="doubleclick.net"],iframe[src*="googlesyndication.com"],iframe[src*="adservice.google."],.ad-banner:not(body):not(html),.sponsored-ad,.advertisement:not(body):not(html),[aria-label="Advertisement"],[data-ad-slot]{display:none!important}';
(document.head||document.documentElement).appendChild(s)})();
(function() {
    const FOCUSABLE_SELECTOR = 'a, button, input, select, textarea, [tabindex="0"], [onclick], [role="button"]';
    let focusables = [];
    let isUpdating = false;
    let updateTimer = null;

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

    // Candidates for the pointer snap: what is on screen right now. focusables[] is capped at the first
    // 300 elements in page order, which on a long page like GitHub never reaches what you are looking at.
    let viewList = [];
    let viewListAt = -1e9;
    function visibleCandidates(left, top, right, bottom) {
        const now = performance.now();
        if (now - viewListAt < 400) return viewList;
        viewListAt = now;
        const out = [];
        const all = document.querySelectorAll(FOCUSABLE_SELECTOR);
        for (let i = 0; i < all.length && out.length < 300; i++) {
            const el = all[i];
            if (el.disabled) continue;
            const r = el.getBoundingClientRect();
            if (r.width > 0 && r.height > 0 && r.right > left && r.left < right && r.bottom > top && r.top < bottom) out.push(el);
        }
        viewList = out;
        return out;
    }

    // The app sends the pointer in screen pixels (px, py) and the width of the page area in screen pixels
    // (viewW). The page works in CSS pixels, so convert in, search, and convert the answer back out.
    function findClosest(px, py, viewW) {
        const vv = window.visualViewport;
        const s = (vv && viewW > 0) ? vv.width / viewW : 1 / (window.devicePixelRatio || 1);
        const ox = vv ? vv.offsetLeft : 0;
        const oy = vv ? vv.offsetTop : 0;
        const x = px * s + ox;
        const y = py * s + oy;
        const maxDist = 56 * s; // 56 screen pixels, expressed in CSS pixels (small, so the pointer never steals a precise click)
        const vw = vv ? vv.width : window.innerWidth;
        const vh = vv ? vv.height : window.innerHeight;
        let closest = null, minDistance = Infinity;
        visibleCandidates(ox, oy, ox + vw, oy + vh).forEach(el => {
            const rect = el.getBoundingClientRect();
            const center = getCenter(rect);
            const dx = center.x - x;
            const dy = center.y - y;
            if (Math.abs(dx) < maxDist && Math.abs(dy) < maxDist) {
                const dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < maxDist && dist < minDistance) {
                    minDistance = dist;
                    closest = {
                        x: (center.x - ox) / s, y: (center.y - oy) / s,
                        width: rect.width / s, height: rect.height / s
                    };
                }
            }
        });
        return closest;
    }

    // Called from the app through evaluateJavascript. Returns JSON (or "null") so no native bridge is exposed to pages.
    window.__wyNav = function (dir) { return spatialNavigate(dir); };
    window.__wyClosest = function (x, y, w) { return findClosest(x, y, w); };

    const style = document.createElement('style');
    style.innerHTML = `
        :focus {
            outline: 3px solid #38bdf8 !important;
            outline-offset: 2px !important;
            box-shadow: 0 0 12px rgba(56,189,248,0.6) !important;
        }
    `;
    // document.head does not exist yet at document_start, so this used to throw and the outline never appeared.
    (document.head || document.documentElement).appendChild(style);
})();

// Wishy Core: a small second layer on top of uBlock Origin. It never replaces the blocker; it only
// cleans up what is left (YouTube video ads that slip through, and empty ad frames).
(function () {
    "use strict";

    // ---- YouTube: skip or fast-forward any ad that still starts ----------------------------
    if (/(^|\.)youtube\.com$/.test(location.hostname)) {
        let mutedByUs = false;
        let lastRate = 1;
        const SKIP = [
            ".ytp-skip-ad-button", ".ytp-ad-skip-button", ".ytp-ad-skip-button-modern",
            ".ytp-ad-skip-button-container button", "button.ytp-ad-skip-button-modern",
        ].join(",");

        const tick = () => {
            const player = document.querySelector(".html5-video-player");
            const video = document.querySelector("video.html5-main-video") || document.querySelector("video");
            const adShowing = !!player && player.classList.contains("ad-showing");

            const skip = document.querySelector(SKIP);
            if (skip) skip.click();
            const close = document.querySelector(".ytp-ad-overlay-close-button");
            if (close) close.click();

            if (adShowing && video) {
                if (!mutedByUs) { lastRate = video.playbackRate || 1; mutedByUs = !video.muted; if (mutedByUs) video.muted = true; }
                try { video.playbackRate = 16; } catch (e) { /* ignore */ }
                if (isFinite(video.duration) && video.duration > 0 && video.currentTime < video.duration - 0.2) {
                    try { video.currentTime = video.duration; } catch (e) { /* ignore */ }
                }
            } else if (video && mutedByUs) {
                video.muted = false;
                try { video.playbackRate = lastRate; } catch (e) { /* ignore */ }
                mutedByUs = false;
            }
        };
        setInterval(tick, 500);
    }

    // ---- Everywhere: collapse ad frames that were blocked and left an empty box -------------
    const AD_FRAMES = 'iframe[src*="doubleclick.net"], iframe[src*="googlesyndication.com"], iframe[id^="google_ads_iframe"], ins.adsbygoogle';
    const collapse = () => {
        document.querySelectorAll(AD_FRAMES).forEach((el) => {
            if (el.dataset.wishyCollapsed) return;
            el.dataset.wishyCollapsed = "1";
            el.style.setProperty("display", "none", "important");
        });
    };
    let timer = null;
    const schedule = () => { if (!timer) timer = setTimeout(() => { timer = null; collapse(); }, 1200); };
    const start = () => {
        collapse();
        if (document.body) new MutationObserver(schedule).observe(document.body, { childList: true });
    };
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", start); else start();
})();

})();
