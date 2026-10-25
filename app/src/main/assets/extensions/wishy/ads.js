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
