// Wishy Core for WebView: D-pad link navigation, pointer snapping, reader view, YouTube ad skipper.
// Injected by WebViewEngine after a page becomes visible. Guarded so it only installs once per document.
(function () {
    "use strict";
    if (window.__wishy) return;

    var FOCUSABLE = 'a, button, input, select, textarea, [tabindex="0"], [onclick], [role="button"]';
    var focusables = [], updTimer = null;

    function updateFocusables() {
        try {
            var all = document.querySelectorAll(FOCUSABLE), list = [], n = Math.min(all.length, 300);
            for (var i = 0; i < n; i++) {
                var el = all[i];
                if ((el.offsetWidth > 0 || el.offsetHeight > 0) && !el.disabled) list.push(el);
            }
            focusables = list;
        } catch (e) { /* ignore */ }
    }
    function scheduleUpdate() {
        if (updTimer) clearTimeout(updTimer);
        updTimer = setTimeout(function () {
            updTimer = null;
            if (window.requestIdleCallback) window.requestIdleCallback(updateFocusables, { timeout: 1000 });
            else updateFocusables();
        }, 1200);
    }
    if (document.body) new MutationObserver(scheduleUpdate).observe(document.body, { childList: true });
    updateFocusables();

    function center(r) { return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; }

    function nav(direction) {
        var active = document.activeElement;
        if (!focusables.length) updateFocusables();
        if (!focusables.length) return false;
        var ac = (!active || focusables.indexOf(active) < 0 || active === document.body)
            ? { x: 0, y: 0 } : center(active.getBoundingClientRect());
        var best = null, min = Infinity;
        focusables.forEach(function (el) {
            if (el === active) return;
            var c = center(el.getBoundingClientRect()), ok = false, fwd = 0, ortho = 0;
            if (direction === "Up" && c.y < ac.y) { ok = true; fwd = ac.y - c.y; ortho = Math.abs(ac.x - c.x); }
            else if (direction === "Down" && c.y > ac.y) { ok = true; fwd = c.y - ac.y; ortho = Math.abs(ac.x - c.x); }
            else if (direction === "Left" && c.x < ac.x) { ok = true; fwd = ac.x - c.x; ortho = Math.abs(ac.y - c.y); }
            else if (direction === "Right" && c.x > ac.x) { ok = true; fwd = c.x - ac.x; ortho = Math.abs(ac.y - c.y); }
            if (ok) { var s = fwd + ortho * 2.5; if (s < min) { min = s; best = el; } }
        });
        if (best) { best.focus(); best.scrollIntoView({ block: "nearest", inline: "nearest" }); return true; }
        return false;
    }

    var viewList = [], viewAt = -1e9;
    function visible(l, t, r, b) {
        var now = performance.now();
        if (now - viewAt < 400) return viewList;
        viewAt = now;
        var out = [], all = document.querySelectorAll(FOCUSABLE);
        for (var i = 0; i < all.length && out.length < 300; i++) {
            var el = all[i];
            if (el.disabled) continue;
            var rc = el.getBoundingClientRect();
            if (rc.width > 0 && rc.height > 0 && rc.right > l && rc.left < r && rc.bottom > t && rc.top < b) out.push(el);
        }
        viewList = out;
        return out;
    }

    // px, py and viewW are in screen pixels; the page works in CSS pixels, so convert in and back out.
    function closest(px, py, viewW) {
        var vv = window.visualViewport;
        var s = (vv && viewW > 0) ? vv.width / viewW : 1 / (window.devicePixelRatio || 1);
        var ox = vv ? vv.offsetLeft : 0, oy = vv ? vv.offsetTop : 0;
        var x = px * s + ox, y = py * s + oy, maxD = 100 * s;
        var vw = vv ? vv.width : window.innerWidth, vh = vv ? vv.height : window.innerHeight;
        var res = null, min = Infinity;
        visible(ox, oy, ox + vw, oy + vh).forEach(function (el) {
            var rc = el.getBoundingClientRect(), c = center(rc), dx = c.x - x, dy = c.y - y;
            if (Math.abs(dx) < maxD && Math.abs(dy) < maxD) {
                var d = Math.sqrt(dx * dx + dy * dy);
                if (d < maxD && d < min) { min = d; res = { x: (c.x - ox) / s, y: (c.y - oy) / s }; }
            }
        });
        return res;
    }

    // ---- Reader view: a small heuristic extractor, not Mozilla Readability ----
    function pickRoot() {
        var c = document.querySelectorAll("article, main, [role=main], #content, .content, .post, .article");
        var list = c.length ? c : [document.body], best = null, bs = 0;
        for (var i = 0; i < list.length; i++) {
            var s = 0, ps = list[i].querySelectorAll("p");
            for (var j = 0; j < ps.length; j++) s += ps[j].textContent.trim().length;
            if (s > bs) { bs = s; best = list[i]; }
        }
        return bs >= 800 ? best : null;
    }
    function readerable() { try { return !!pickRoot(); } catch (e) { return false; } }
    function reader() {
        var root = pickRoot();
        if (!root) return false;
        var title = document.title, items = [];
        root.querySelectorAll("h1,h2,h3,p,li,blockquote,pre,img").forEach(function (el) {
            var t = el.tagName.toLowerCase();
            if (t === "img") { var src = el.currentSrc || el.src; if (/^https:/.test(src) && el.naturalWidth > 200) items.push(["img", src]); }
            else { var txt = el.textContent.trim(); if (txt.length > (t === "li" ? 2 : 20) || /^h/.test(t)) items.push([t, txt]); }
        });
        var head = document.head, body = document.body;
        head.innerHTML = ""; body.innerHTML = "";
        body.removeAttribute("class"); body.removeAttribute("style");
        var st = document.createElement("style");
        st.textContent = "body{margin:0;background:#111;color:#e8e8e8;font:28px/1.6 sans-serif}" +
            "main{max-width:900px;margin:0 auto;padding:48px 32px}h1,h2,h3{line-height:1.25}" +
            "img{max-width:100%;height:auto;border-radius:12px}pre{white-space:pre-wrap}blockquote{border-left:4px solid #888;margin-left:0;padding-left:20px}";
        head.appendChild(st);
        var m = document.createElement("main"), h = document.createElement("h1");
        h.textContent = title; m.appendChild(h);
        items.forEach(function (it) {
            var e = document.createElement(it[0] === "img" ? "img" : it[0]);
            if (it[0] === "img") e.src = it[1]; else e.textContent = it[1];
            m.appendChild(e);
        });
        body.appendChild(m);
        window.scrollTo(0, 0);
        return true;
    }

    window.__wishy = { nav: nav, closest: closest, readerable: readerable, reader: reader };

    // Global Privacy Control signal (the Sec-GPC header is sent on top-level loads by the app).
    try { Object.defineProperty(Navigator.prototype, "globalPrivacyControl", { get: function () { return true; }, configurable: true }); } catch (e) { /* ignore */ }

    // Visible focus ring for D-pad navigation.
    var st2 = document.createElement("style");
    st2.textContent = ":focus{outline:3px solid #38bdf8!important;outline-offset:2px!important}";
    (document.head || document.documentElement).appendChild(st2);

    // YouTube: click Skip when an ad offers it. (Best effort; layout changes can break it.)
    if (/(^|\.)youtube\.com$/.test(location.hostname)) {
        var SKIP = ".ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-ad-skip-button-container button";
        setInterval(function () {
            var b = document.querySelector(SKIP); if (b) b.click();
            var c = document.querySelector(".ytp-ad-overlay-close-button"); if (c) c.click();
            var p = document.querySelector(".html5-video-player"), v = document.querySelector("video.html5-main-video");
            if (p && v && p.classList.contains("ad-showing") && isFinite(v.duration) && v.duration > 0 && v.currentTime < v.duration - 0.2) {
                try { v.muted = true; v.currentTime = v.duration; } catch (e) { /* ignore */ }
            }
        }, 500);
    }
})();
