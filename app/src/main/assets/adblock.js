// Wishy ad layer. The app puts window.__wyAd = {yt, cos, allow} in front of this file.
//   yt    remove YouTube ads (before the player sees them) and click through any that still start
//   cos   hide empty ad boxes, and stand in for blocked ad scripts so pages keep working
//   allow hosts where shields are switched off by the user
// Runs at document start in every frame when the WebView supports it. Each part is wrapped in try/catch:
// a failed protection must never break a page.
(function () {
  "use strict";
  if (window.__wyA) return;
  try { Object.defineProperty(window, "__wyA", { value: 1 }); } catch (e) { window.__wyA = 1; }
  var C = window.__wyAd || {};
  var host = location.hostname;
  if (C.allow && C.allow.indexOf(host) >= 0) return;

  function addCss(text) {
    function put() {
      var p = document.head || document.documentElement;
      if (!p) return false;
      var s = document.createElement("style");
      s.textContent = text;
      p.appendChild(s);
      return true;
    }
    try {
      if (put()) return;
      var t = setInterval(function () { if (put()) clearInterval(t); }, 25);
      setTimeout(function () { clearInterval(t); }, 6000);
    } catch (e) { /* ignore */ }
  }

  // ======================================================================================
  // YouTube
  // ======================================================================================
  var YT = /(^|\.)(youtube\.com|youtube-nocookie\.com|youtubekids\.com)$/.test(host);
  if (C.yt && YT) {
    // 1. Take the ads out of the data before the player or the feed reads it. This is what stops pre-roll,
    //    mid-roll, feed, search and side-bar ads from ever being created; everything after step 1 only
    //    catches what still gets through. Data reaches the page in four ways and all four are covered:
    //    the inline first response, fetch(), XMLHttpRequest and JSON.parse.
    var DROP_KEYS = { adPlacements: 1, playerAds: 1, adSlots: 1, adBreakHeartbeatParams: 1 };
    // Renderers that are ads. As an item of a list the whole item is dropped; as a property it is deleted.
    var AD_R = {
      adSlotRenderer: 1, adSlotAndLayoutRenderer: 1, inFeedAdLayoutRenderer: 1, displayAdRenderer: 1,
      promotedSparklesWebRenderer: 1, promotedSparklesTextSearchRenderer: 1, promotedVideoRenderer: 1,
      compactPromotedVideoRenderer: 1, searchPyvRenderer: 1, actionCompanionAdRenderer: 1,
      videoMastheadAdV3Renderer: 1, mastheadAdRenderer: 1, brandVideoShelfRenderer: 1, brandVideoSingletonRenderer: 1,
      primetimePromoRenderer: 1, statementBannerRenderer: 1, bannerPromoRenderer: 1,
      playerLegacyDesktopWatchAdsRenderer: 1
    };
    // Wrappers whose "content" is the real renderer (grid tiles and sections).
    var WRAP = { richItemRenderer: 1, richSectionRenderer: 1, gridVideoRenderer: 0 };
    // The "ad blockers are not allowed" messages that the player response can carry.
    var NAG = { enforcementMessageViewModel: 1, bkaEnforcementMessageViewModel: 1 };
    var removed = 0;

    var isAdItem = function (it) {
      if (!it || typeof it !== "object") return false;
      for (var k in it) {
        if (!Object.prototype.hasOwnProperty.call(it, k)) continue;
        if (AD_R[k] === 1) return true;
        if (WRAP[k] === 1) {
          var c = it[k] && it[k].content;
          if (c && typeof c === "object") for (var k2 in c) if (AD_R[k2] === 1) return true;
        }
      }
      return false;
    };
    var emptyBox = function (it) {
      // A section that only held ads is now an empty shell; drop it too.
      var r = it && (it.itemSectionRenderer || it.shelfRenderer || it.richSectionRenderer);
      return !!(r && Array.isArray(r.contents) && r.contents.length === 0);
    };

    var walk = function (o, depth) {
      if (!o || typeof o !== "object" || depth > 60) return;
      var i, k;
      if (Array.isArray(o)) {
        for (i = o.length - 1; i >= 0; i--) {
          var it = o[i];
          if (isAdItem(it)) { o.splice(i, 1); removed++; continue; }
          var before = removed;
          walk(it, depth + 1);
          if (removed > before && emptyBox(it)) { o.splice(i, 1); }
        }
        return;
      }
      for (k in DROP_KEYS) if (k in o) { delete o[k]; removed++; }
      for (k in AD_R) if (k in o) { delete o[k]; removed++; }
      var mr = o.messageRenderers;
      if (mr && typeof mr === "object") for (k in NAG) if (k in mr) { delete mr[k]; removed++; }
      var sp = o.supportedPopups;
      if (sp && typeof sp === "object" && "adBlockMessageViewModel" in sp) sp.adBlockMessageViewModel = false;
      for (k in o) {
        if (!Object.prototype.hasOwnProperty.call(o, k)) continue;
        var v = o[k];
        if (v && typeof v === "object") walk(v, depth + 1);
      }
    };

    var looksYt = function (o) {
      return !!(o.responseContext || o.playerResponse ||
        ((o.adPlacements || o.playerAds || o.adSlots) && (o.videoDetails || o.streamingData || o.playabilityStatus)));
    };
    var prune = function (o) {
      try {
        if (!o || typeof o !== "object") return o;
        if (Array.isArray(o)) {
          // watch?pbj=1 answers: [{page, playerResponse}, {page, response}, ...]
          for (var i = 0; i < o.length && i < 8; i++) {
            var it = o[i];
            if (it && typeof it === "object" && (it.playerResponse || it.response || it.responseContext)) walk(it, 0);
          }
          return o;
        }
        if (looksYt(o)) walk(o, 0);
      } catch (e) { /* ignore */ }
      return o;
    };
    window.__wyPrune = prune;

    var oParse = JSON.parse;
    try {
      JSON.parse = function () { return prune(oParse.apply(this, arguments)); };
    } catch (e) { /* ignore */ }

    // Text answers (fetch .text(), XHR responseText): only YouTube's own data endpoints, and only when an ad
    // marker is in the text at all, so nothing else is ever re-written.
    var API = /\/youtubei\/v1\/|[?&]pbj=1/;
    var QUICK = /"(adPlacements|playerAds|adSlots|adBreakHeartbeatParams|enforcementMessageViewModel|bkaEnforcementMessageViewModel|adSlotRenderer|adSlotAndLayoutRenderer|inFeedAdLayoutRenderer|displayAdRenderer|promotedSparklesWebRenderer|promotedSparklesTextSearchRenderer|promotedVideoRenderer|compactPromotedVideoRenderer|searchPyvRenderer|videoMastheadAdV3Renderer|mastheadAdRenderer|brandVideoShelfRenderer|brandVideoSingletonRenderer|primetimePromoRenderer|statementBannerRenderer|bannerPromoRenderer)"/;
    var cleanText = function (s) {
      try {
        if (typeof s !== "string" || !QUICK.test(s)) return s;
        var o = oParse.call(JSON, s);
        removed = 0;
        prune(o);
        return removed > 0 ? JSON.stringify(o) : s;
      } catch (e) { return s; }
    };

    try {
      if (window.Response && Response.prototype) {
        var oJson = Response.prototype.json;
        if (oJson) Response.prototype.json = function () { return oJson.apply(this, arguments).then(prune); };
        var oText = Response.prototype.text;
        if (oText) Response.prototype.text = function () {
          var url = this.url || "";
          var p = oText.apply(this, arguments);
          return API.test(url) ? p.then(cleanText) : p;
        };
      }
    } catch (e) { /* ignore */ }

    try {
      var xp = window.XMLHttpRequest && XMLHttpRequest.prototype;
      if (xp) {
        var urls = new WeakMap(), cache = new WeakMap();
        var oOpen = xp.open;
        xp.open = function (m, u) { try { urls.set(this, String(u)); } catch (e) { /* ignore */ } return oOpen.apply(this, arguments); };
        ["responseText", "response"].forEach(function (name) {
          var d = Object.getOwnPropertyDescriptor(xp, name);
          if (!d || !d.get) return;
          var og = d.get;
          Object.defineProperty(xp, name, {
            configurable: true, enumerable: d.enumerable, set: d.set,
            get: function () {
              var val = og.call(this);
              try {
                if (this.readyState !== 4 || !val) return val;
                var u = urls.get(this) || "";
                if (!API.test(u)) return val;
                if (typeof val === "string") {
                  var c = cache.get(this);
                  if (c && c.i === val) return c.o;
                  var out = cleanText(val);
                  cache.set(this, { i: val, o: out });
                  return out;
                }
                if (typeof val === "object") return prune(val);
              } catch (e) { /* ignore */ }
              return val;
            }
          });
        });
      }
    } catch (e) { /* ignore */ }

    // The page's own inline script assigns the first responses; clean them on the way in.
    ["ytInitialPlayerResponse", "ytInitialData"].forEach(function (name) {
      try {
        var held;
        Object.defineProperty(window, name, {
          configurable: true, enumerable: true,
          get: function () { return held; },
          set: function (v) { held = prune(v); }
        });
      } catch (e) { /* ignore */ }
    });
    // Same idea as uBlock Origin's rule: nothing may record that an ad blocker was found.
    try {
      Object.defineProperty(Object.prototype, "adBlocksFound", { configurable: true, enumerable: false, get: function () { return 0; }, set: function () {} });
    } catch (e) { /* ignore */ }

    // 2. Whatever the data step missed on the page: feed, search, side and banner ads.
    addCss([
      "#masthead-ad", "#player-ads", "#promoted-sparkles-web", "#companion",
      "ytd-ad-slot-renderer", "ytd-in-feed-ad-layout-renderer", "ytd-banner-promo-renderer", "ytd-statement-banner-renderer",
      "ytd-promoted-sparkles-web-renderer", "ytd-promoted-sparkles-text-search-renderer", "ytd-promoted-video-renderer",
      "ytd-display-ad-renderer", "ytd-companion-slot-renderer", "ytd-action-companion-ad-renderer",
      "ytd-player-legacy-desktop-watch-ads-renderer", "ytd-video-masthead-ad-v3-renderer", "ytd-primetime-promo-renderer",
      "ytd-brand-video-shelf-renderer", "ytd-brand-video-singleton-renderer", "ytd-compact-promoted-video-renderer",
      "ytd-engagement-panel-section-list-renderer[target-id=\"engagement-panel-ads\"]",
      "ytm-promoted-sparkles-web-renderer", "ytm-promoted-video-renderer", "ytm-companion-ad-renderer", "ytm-ad-slot-renderer",
      "ytm-in-feed-ad-layout-renderer", "ytm-banner-promo-renderer", "ad-slot-renderer",
      ".ytp-ad-overlay-container", ".ytp-ad-image-overlay", ".ytp-ad-text-overlay", ".ytp-ad-module .ytp-ad-player-overlay",
      ".video-ads.ytp-ad-module", ".ytp-ad-progress-list"
    ].join(",") + "{display:none!important}");
    // Grid tiles that only wrap an ad (needs :has, so it is a rule of its own: an old WebView drops only this one).
    addCss("ytd-rich-item-renderer:has(ytd-ad-slot-renderer),ytd-rich-section-renderer:has(ytd-statement-banner-renderer),ytd-item-section-renderer:has(ytd-ad-slot-renderer){display:none!important}");

    // 3. Whatever still starts: click Skip, run the ad to its last frame, and clear YouTube's
    //    "ad blockers are not allowed" pop-up (then let the video carry on).
    var SKIP = ".ytp-skip-ad-button, .ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-ad-skip-button-container button, .ytp-ad-overlay-close-button";
    var st = { ad: false, muted: false, rate: 1 };
    var tick = function () {
      try {
        if (document.hidden) return;
        var p = document.getElementById("movie_player") || document.querySelector(".html5-video-player");
        var v = (p && (p.querySelector("video.html5-main-video") || p.querySelector("video"))) || document.querySelector("video");

        var btn = document.querySelector(SKIP);
        if (btn) btn.click();

        var nag = document.querySelector("ytd-enforcement-message-view-model, tp-yt-paper-dialog:has(ytd-enforcement-message-view-model)");
        if (nag) {
          var dlg = nag.closest ? (nag.closest("tp-yt-paper-dialog") || nag) : nag;
          dlg.remove();
          var bd = document.querySelectorAll("tp-yt-iron-overlay-backdrop");
          for (var b = 0; b < bd.length; b++) bd[b].remove();
          if (document.body) document.body.style.removeProperty("overflow");
          if (document.documentElement) document.documentElement.style.removeProperty("overflow");
          if (v && v.paused) { var pr = v.play(); if (pr && pr.catch) pr.catch(function () {}); }
        }

        var ad = !!p && (p.classList.contains("ad-showing") || p.classList.contains("ad-interrupting"));
        if (ad && v) {
          if (!st.ad) { st.ad = true; st.muted = v.muted; st.rate = v.playbackRate || 1; }
          v.muted = true;
          try { v.playbackRate = 16; } catch (e) { /* ignore */ }
          // Only a short clip is an ad. A long "duration" means the real video is in this element
          // (or the ad flag is stale): never seek that, it would skip the video itself.
          if (isFinite(v.duration) && v.duration > 0 && v.duration <= 300 && v.currentTime < v.duration - 0.1) {
            try { v.currentTime = v.duration - 0.05; } catch (e) { /* ignore */ }
          }
        } else if (st.ad && v) {
          st.ad = false;
          v.muted = st.muted;
          try { v.playbackRate = st.rate; } catch (e) { /* ignore */ }
        }
      } catch (e) { /* ignore */ }
    };
    setInterval(tick, 250);
    // The timer alone leaves up to a quarter of a second of ad sound; the video's own events react at once.
    ["loadstart", "loadedmetadata", "durationchange", "playing", "timeupdate"].forEach(function (ev) {
      try { document.addEventListener(ev, tick, true); } catch (e) { /* ignore */ }
    });
  }

  // ======================================================================================
  // Every other site
  // ======================================================================================
  if (C.cos && !YT) {
    // Ad containers. Names that sites use as "bait" for ad-block detectors (adsbox, ad-banner, textAd, pub_300x250)
    // are left alone on purpose: hiding them is what triggers the "please turn off your ad blocker" walls.
    var SEL = [
      "ins.adsbygoogle", ".adsbygoogle", "[id^=\"google_ads_\"]", "[id^=\"div-gpt-ad\"]", "[id^=\"gpt-ad\"]", "[data-google-query-id]",
      "[data-ad-slot]", "[data-ad-unit]", "[data-ad-client]", "[data-adunit]", ".google-auto-placed",
      "iframe[id^=\"google_ads_iframe\"]", "iframe[src*=\"doubleclick.net\"]", "iframe[src*=\"googlesyndication.com\"]",
      "iframe[src*=\"adservice.google.\"]", "iframe[src*=\"amazon-adsystem.com\"]", "iframe[src*=\"adnxs.com\"]",
      "iframe[src*=\"taboola.com\"]", "iframe[src*=\"outbrain.com\"]", "iframe[src*=\"criteo.\"]", "iframe[src*=\"pubmatic.com\"]",
      "iframe[src*=\"rubiconproject.com\"]", "iframe[src*=\"openx.net\"]", "iframe[src*=\"2mdn.net\"]",
      "#taboola-below-article", "[id^=\"taboola-\"]", ".taboola", ".trc_rbox_container", ".trc_related_container",
      ".OUTBRAIN", ".ob-widget", "[data-widget-id^=\"AR_\"]", "[id^=\"outbrain_widget\"]", "[id^=\"mgid_\"]", ".mgbox",
      ".advertisement:not(body):not(html)", ".advertisements:not(body):not(html)", ".advert:not(body):not(html)",
      ".ad-wrapper", ".ad_wrapper", ".adWrapper", ".ad-unit", ".adunit", ".ad-slot", ".ad-placeholder", ".sticky-ad", ".sponsored-ad",
      ".dfp-ad", ".dfp-tag-wrapper", ".adthrive-ad", "[id^=\"adthrive-\"]", ".mv-ad-box",
      "[aria-label=\"Advertisement\" i]", "[aria-label=\"Ad\" i]",
      "amp-ad", "amp-embed", "amp-sticky-ad", "ins[data-ad-status]", ".GoogleActiveViewElement",
      "iframe[src*=\"adsafeprotected.com\"]", "iframe[src*=\"adform.net\"]", "iframe[src*=\"smartadserver.com\"]",
      "iframe[src*=\"teads.tv\"]", "iframe[src*=\"media.net\"]", "iframe[src*=\"adclick.g.doubleclick.net\"]",
      // sign-in nags that float over the page
      "#credential_picker_container", "#credential_picker_iframe", "iframe[src*=\"accounts.google.com/gsi/\"]"
    ].join(",");
    addCss(SEL + "{display:none!important}");

    // Stand in for ad scripts that were blocked, so page code that expects them does not fail
    // (a failing page script is what usually breaks a layout or a login after blocking).
    try {
      if (!window.adsbygoogle) window.adsbygoogle = { loaded: true, push: function () {} };
    } catch (e) { /* ignore */ }
    try {
      if (!window.googletag) {
        var chain;
        var fn = function () { return chain; };
        var empty = function () { return ""; };
        chain = new Proxy(fn, {
          get: function (t, k) {
            if (k === Symbol.toPrimitive || k === "toString" || k === "valueOf") return empty;
            if (k === "then") return undefined;
            return chain;
          },
          apply: function () { return chain; }
        });
        window.googletag = {
          cmd: { push: function (f) { try { if (typeof f === "function") f(); } catch (e) { /* ignore */ } return 1; } },
          apiReady: true, pubadsReady: true,
          pubads: fn, defineSlot: fn, defineOutOfPageSlot: fn, enableServices: fn, display: fn, sizeMapping: fn,
          companionAds: fn, content: fn, destroySlots: function () { return true; }
        };
      }
    } catch (e) { /* ignore */ }
  }
})();
