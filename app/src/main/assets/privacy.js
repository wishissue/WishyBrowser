// Wishy privacy layer. The app puts window.__wyCfg = {fp, ref, banners} in front of this file.
// Runs at document start when the WebView supports it, otherwise as soon as the page begins loading.
// Everything is wrapped in try/catch: a failed protection must never break a page.
(function () {
  "use strict";
  if (window.__wyP) return;
  try { Object.defineProperty(window, "__wyP", { value: 1 }); } catch (e) { window.__wyP = 1; }
  var C = window.__wyCfg || {};

  function def(obj, key, value) {
    try { Object.defineProperty(obj, key, { get: function () { return value; }, configurable: true }); } catch (e) { /* ignore */ }
  }

  // ---- Fingerprint protection ------------------------------------------------------------
  if (C.fp) {
    try {
      def(navigator, "hardwareConcurrency", 4);
      def(navigator, "deviceMemory", 8);
      def(navigator, "languages", ["en-US", "en"]);
      def(navigator, "language", "en-US");
      def(navigator, "getBattery", undefined);
      def(navigator, "maxTouchPoints", 0);
    } catch (e) { /* ignore */ }

    // Canvas read-back gets a tiny per-session change, so the same canvas hashes differently on every launch.
    try {
      var seed = (Math.random() * 255) | 0;
      var twoD = typeof WeakSet === "function" ? new WeakSet() : null;
      var origGet = HTMLCanvasElement.prototype.getContext;
      HTMLCanvasElement.prototype.getContext = function (type) {
        if (twoD && type === "2d") twoD.add(this);
        return origGet.apply(this, arguments);
      };
      var origGid = CanvasRenderingContext2D.prototype.getImageData;
      var origUrl = HTMLCanvasElement.prototype.toDataURL;
      var origBlob = HTMLCanvasElement.prototype.toBlob;

      var nudge = function (data) {
        var n = Math.min(data.length, 64);
        for (var i = 0; i < n; i += 4) data[i] = data[i] ^ ((seed >> ((i >> 2) & 7)) & 1);
      };
      var noisyCopy = function (c) {
        try {
          if (!twoD || !twoD.has(c) || c.width * c.height < 256) return c;
          var t = document.createElement("canvas");
          t.width = c.width; t.height = c.height;
          var x = t.getContext("2d");
          x.drawImage(c, 0, 0);
          var d = origGid.call(x, 0, 0, Math.min(c.width, 8), Math.min(c.height, 2));
          nudge(d.data);
          x.putImageData(d, 0, 0);
          return t;
        } catch (e) { return c; }
      };
      CanvasRenderingContext2D.prototype.getImageData = function () {
        var d = origGid.apply(this, arguments);
        try { if (d && d.data && d.width * d.height >= 256) nudge(d.data); } catch (e) { /* ignore */ }
        return d;
      };
      HTMLCanvasElement.prototype.toDataURL = function () {
        return origUrl.apply(noisyCopy(this), arguments);
      };
      HTMLCanvasElement.prototype.toBlob = function () {
        return origBlob.apply(noisyCopy(this), arguments);
      };
    } catch (e) { /* ignore */ }
  }

  // ---- Referrer: nothing is sent to other sites when you click a link to them -------------
  if (C.ref) {
    try {
      var m = document.createElement("meta");
      m.name = "referrer"; m.content = "strict-origin-when-cross-origin";
      (document.head || document.documentElement).appendChild(m);
      var strip = function (ev) {
        try {
          var a = ev.target && ev.target.closest ? ev.target.closest("a[href]") : null;
          if (a && a.origin && a.origin !== location.origin) a.rel = ((a.rel || "") + " noreferrer noopener").trim();
        } catch (e) { /* ignore */ }
      };
      document.addEventListener("click", strip, true);
      document.addEventListener("keydown", function (ev) { if (ev.key === "Enter") strip(ev); }, true);
    } catch (e) { /* ignore */ }
  }

  // ---- Cookie and consent pop-ups ---------------------------------------------------------
  if (C.banners) {
    try {
      var SEL = [
        "#onetrust-consent-sdk", "#onetrust-banner-sdk", "#CybotCookiebotDialog", "#CybotCookiebotDialogBodyUnderlay",
        ".cc-window", ".cc-banner", "#cookie-law-info-bar", "#cookiebanner", "#cookie-banner", ".cookie-banner",
        ".cookie-consent", "#cookieConsent", "#cookie-consent", ".cookie-notice", "#cookie-notice", "#cookie-consent-banner",
        ".truste_overlay", ".truste_box_overlay", "#truste-consent-track", "#qc-cmp2-container", ".qc-cmp2-container",
        "#didomi-host", "#didomi-popup", ".didomi-popup-backdrop", "#usercentrics-root", "[id^=\"sp_message_container\"]",
        ".fc-consent-root", "#gdpr-cookie-notice", ".gdpr-banner", "#gdpr-banner", ".osano-cm-window", ".osano-cm-dialog",
        "#cmpbox", "#cmpwrapper", "#iubenda-cs-banner", ".iubenda-cs-container", "#CookieBanner", ".js-cookie-consent",
        "#consent-banner", ".consent-banner", "#cookiescript_injected", "#moove_gdpr_cookie_info_bar", "#cookieChoiceInfo",
        "#sd-cmp", "#tarteaucitronRoot", "#tarteaucitronAlertBig", "#klaro", ".klaro"
      ].join(",");
      var css = document.createElement("style");
      css.textContent = SEL + "{display:none!important}";
      (document.head || document.documentElement).appendChild(css);
      var unlock = function () {
        try {
          if (!document.querySelector(SEL)) return;
          var r = document.documentElement, b = document.body;
          if (r) r.style.setProperty("overflow", "auto", "important");
          if (b) b.style.setProperty("overflow", "auto", "important");
        } catch (e) { /* ignore */ }
      };
      var tries = 0;
      var timer = setInterval(function () { unlock(); if (++tries >= 8) clearInterval(timer); }, 700);
    } catch (e) { /* ignore */ }
  }
})();
