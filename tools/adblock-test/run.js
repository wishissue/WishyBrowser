const { boot, check, done, wait, hasKey } = require("./harness");
const F = require("./fixtures/yt");

const AD_KEYS = ["adPlacements", "playerAds", "adSlots", "adBreakHeartbeatParams", "adSlotRenderer", "videoMastheadAdV3Renderer",
  "statementBannerRenderer", "inFeedAdLayoutRenderer", "searchPyvRenderer", "promotedSparklesWebRenderer", "compactPromotedVideoRenderer"];
const noAds = (o) => !AD_KEYS.some((k) => hasKey(o, k));
const ytRoutes = (u) => {
  if (u.includes("/youtubei/v1/player")) return JSON.stringify(F.playerResponse());
  if (u.includes("/youtubei/v1/next")) return JSON.stringify(F.next());
  if (u.includes("/youtubei/v1/browse")) return JSON.stringify(F.feed());
  if (u.includes("/youtubei/v1/search")) return JSON.stringify(F.search());
  if (u.includes("pbj=1")) return JSON.stringify(F.pbj());
  if (u.includes("other.json")) return JSON.stringify({ adPlacements: ["keep me"], x: 1 });
  return "{}";
};

(async () => {
  console.log("\n[1] YouTube: data the page reads before the player starts");
  {
    const html = `<!doctype html><html><head></head><body>
      <script>var ytInitialPlayerResponse = ${JSON.stringify(F.playerResponse())};</script>
      <script>var ytInitialData = ${JSON.stringify(F.feed())};</script></body></html>`;
    const w = boot({ html }).window;
    const pr = w.ytInitialPlayerResponse, id = w.ytInitialData;
    check("ytInitialPlayerResponse has no ad keys", noAds(pr), JSON.stringify(Object.keys(pr)));
    check("ytInitialPlayerResponse keeps video details + streams", pr.videoDetails.videoId === "abc123" && pr.streamingData.formats.length === 1);
    check("enforcement message data removed", !hasKey(pr, "enforcementMessageViewModel") && !hasKey(pr, "bkaEnforcementMessageViewModel"));
    check("unrelated auxiliaryUi data kept", hasKey(pr, "somethingElse"));
    check("ytInitialData: feed/masthead ad items removed from data", noAds(id));
    const items = id.contents.twoColumnBrowseResultsRenderer.tabs[0].tabRenderer.content.richGridRenderer.contents;
    check("ytInitialData: real videos + continuation kept (a,b,c,continuation)", items.length === 4 && hasKey(items, "continuationItemRenderer") && ["a", "b", "c"].every((v) => hasKey(items, v)), "len=" + items.length);
  }

  console.log("\n[2] YouTube: fetch()");
  {
    const w = boot({ routes: ytRoutes }).window;
    const j = await (await w.fetch("/youtubei/v1/player?key=1")).json();
    check("fetch .json() player: ads gone, video kept", noAds(j) && j.videoDetails.videoId === "abc123");
    const t = JSON.parse(await (await w.fetch("/youtubei/v1/player?key=1")).text());
    check("fetch .text() player: ads gone, video kept", noAds(t) && t.videoDetails.videoId === "abc123");
    const c = await (await w.fetch("/youtubei/v1/player?key=1")).clone().json();
    check("fetch .clone().json() player: ads gone", noAds(c));
    const n = await (await w.fetch("/youtubei/v1/next")).json();
    check("fetch next: sidebar ads + nested playerResponse ads gone", noAds(n));
    check("fetch next: real sidebar videos kept", hasKey(n, "n1") && hasKey(n, "n2"));
    const b = JSON.parse(await (await w.fetch("/youtubei/v1/browse")).text());
    check("fetch browse .text(): feed ads gone", noAds(b));
    const s = await (await w.fetch("/youtubei/v1/search")).json();
    check("fetch search: ads gone, results kept", noAds(s) && hasKey(s, "s1") && hasKey(s, "s2"));
    const raw = await (await w.fetch("/some/other.json")).text();
    check("non-API response text left byte-for-byte untouched", raw === JSON.stringify({ adPlacements: ["keep me"], x: 1 }));
  }

  console.log("\n[3] YouTube: XMLHttpRequest and JSON.parse");
  {
    const w = boot({ routes: ytRoutes }).window;
    const xhr = (url, type) => new Promise((res) => { const x = new w.XMLHttpRequest(); x.responseType = type || ""; x.open("GET", url); x.onload = () => res(x); x.send(); });
    const a = await xhr("/youtubei/v1/player");
    check("XHR responseText player: ads gone", noAds(JSON.parse(a.responseText)));
    const b = await xhr("/youtubei/v1/player", "json");
    check("XHR response (json type) player: ads gone", noAds(b.response));
    const c = await xhr("/watch?v=abc123&pbj=1");
    check("XHR pbj array (playerResponse nested): ads gone", noAds(JSON.parse(c.responseText)));
    const d = await xhr("/other/thing.json");
    check("XHR to unrelated URL untouched", d.responseText === JSON.stringify({ adPlacements: ["keep me"], x: 1 }) || d.responseText === "{}");
    check("JSON.parse(player) prunes", noAds(w.JSON.parse(JSON.stringify(F.playerResponse()))));
    check("JSON.parse(pbj array) prunes", noAds(w.JSON.parse(JSON.stringify(F.pbj()))));
    check("JSON.parse leaves ordinary data alone", JSON.stringify(w.JSON.parse('{"adPlacements":[1],"a":[{"b":2}]}')) === '{"adPlacements":[1],"a":[{"b":2}]}');
    check("JSON.parse still throws on bad JSON", (() => { try { w.JSON.parse("{bad"); return false; } catch (e) { return true; } })());
    check("JSON.parse reviver still works", w.JSON.parse('{"a":1}', (k, v) => (typeof v === "number" ? v + 1 : v)).a === 2);
  }

  console.log("\n[4] YouTube: ad that still starts, and the 'ad blockers not allowed' pop-up");
  {
    const html = `<!doctype html><html><head></head><body>
      <div id="movie_player" class="html5-video-player ad-showing ad-interrupting"><video class="html5-main-video"></video>
      <button class="ytp-skip-ad-button"></button></div></body></html>`;
    const dom = boot({ html }); const w = dom.window; const d = w.document;
    let clicked = 0; d.querySelector(".ytp-skip-ad-button").addEventListener("click", () => clicked++);
    const v = d.querySelector("video");
    Object.defineProperty(v, "duration", { value: 30, configurable: true });
    v.currentTime = 1; v.muted = false; v.playbackRate = 1;
    await wait(700);
    check("Skip button clicked", clicked > 0);
    check("ad muted", v.muted === true);
    check("ad run to its end", v.currentTime >= 29.9, "t=" + v.currentTime);
    d.getElementById("movie_player").classList.remove("ad-showing", "ad-interrupting");
    await wait(700);
    check("sound restored after the ad", v.muted === false);
    check("speed restored after the ad", v.playbackRate === 1);

    const dom2 = boot({ html: `<!doctype html><html><head></head><body style="overflow:hidden"><div id="movie_player" class="html5-video-player"><video class="html5-main-video"></video></div>
      <tp-yt-paper-dialog><ytd-enforcement-message-view-model></ytd-enforcement-message-view-model></tp-yt-paper-dialog><tp-yt-iron-overlay-backdrop></tp-yt-iron-overlay-backdrop></body></html>` });
    const d2 = dom2.window.document; const v2 = d2.querySelector("video"); v2.pause = () => {}; Object.defineProperty(v2, "paused", { value: true });
    await wait(700);
    check("anti-adblock pop-up removed", !d2.querySelector("ytd-enforcement-message-view-model") && !d2.querySelector("tp-yt-paper-dialog"));
    check("backdrop removed + scrolling unlocked", !d2.querySelector("tp-yt-iron-overlay-backdrop") && d2.body.style.overflow === "");
    check("video resumed", v2.__played > 0);
  }

  console.log("\n[5] Cosmetic rules");
  {
    const w = boot({ url: "https://www.youtube.com/", html: `<!doctype html><html><head></head><body><ytd-ad-slot-renderer id=a></ytd-ad-slot-renderer><div id="masthead-ad"></div><ytd-rich-item-renderer id=b><ytd-ad-slot-renderer></ytd-ad-slot-renderer></ytd-rich-item-renderer><ytd-rich-item-renderer id=c><ytd-video-renderer></ytd-video-renderer></ytd-rich-item-renderer></body></html>` }).window;
    await wait(150);
    const hidden = (id) => w.getComputedStyle(w.document.getElementById(id)).display === "none";
    check("YouTube ad slot hidden", hidden("a"));
    check("YouTube masthead ad hidden", hidden("masthead-ad"));
    check("normal feed tile NOT hidden", !hidden("c"));
  }
  {
    const html = `<!doctype html><html><head></head><body><ins class="adsbygoogle" id=a></ins><div id="div-gpt-ad-1" ></div><iframe id=f src="https://securepubads.g.doubleclick.net/x"></iframe><div class="adsbox" id=bait></div><div class="advertisement" id=d></div><p id=text>hello</p></body></html>`;
    const w = boot({ url: "https://example.com/", html }).window;
    await wait(150);
    const hidden = (id) => w.getComputedStyle(w.document.getElementById(id)).display === "none";
    check("adsbygoogle hidden", hidden("a"));
    check("gpt slot hidden", hidden("div-gpt-ad-1"));
    check("doubleclick iframe hidden", hidden("f"));
    check(".advertisement hidden", hidden("d"));
    check("bait class (.adsbox) left alone on purpose", !hidden("bait"));
    check("page text untouched", !hidden("text"));
    check("adsbygoogle stand-in exists and push() is harmless", typeof w.adsbygoogle.push === "function" && (w.adsbygoogle.push({}), true));
    check("googletag stand-in runs queued commands and chains", (() => { let ran = 0; w.googletag.cmd.push(() => { ran++; w.googletag.pubads().enableSingleRequest().collapseEmptyDivs(); }); return ran === 1; })());
    const yt = boot({ url: "https://www.youtube.com/", html }).window;
    check("YouTube does not get the generic ad-script stand-ins", yt.googletag === undefined);
  }

  console.log("\n[6] Switches and per-site shields");
  {
    const off = boot({ cfg: { yt: false, cos: false, allow: [] }, routes: ytRoutes }).window;
    const j = await (await off.fetch("/youtubei/v1/player")).json();
    check("YouTube switch off: nothing is touched", hasKey(j, "adPlacements"));
    const allow = boot({ cfg: { yt: true, cos: true, allow: ["www.youtube.com"] }, routes: ytRoutes }).window;
    const j2 = await (await allow.fetch("/youtubei/v1/player")).json();
    check("Shields off for this site: nothing is touched", hasKey(j2, "adPlacements"));
    const ex = boot({ url: "https://example.com/", cfg: { yt: true, cos: false, allow: [] }, routes: ytRoutes }).window;
    const j3 = await (await ex.fetch("/youtubei/v1/player")).json();
    check("Non-YouTube site: JSON never rewritten", hasKey(j3, "adPlacements"));
    check("Cosmetic switch off: no ad-box stand-ins", ex.adsbygoogle === undefined);
  }

  console.log("\n[7] Safety: must not damage real content, must stay fast");
  {
    const w = boot({ routes: ytRoutes }).window;
    // Real content that merely mentions ad words must survive.
    const real = { responseContext: {}, contents: [{ videoRenderer: { videoId: "x", title: { runs: [{ text: "How adSlots and adPlacements work" }] } } }, { adSlotRenderer: {} }] };
    const out = w.JSON.parse(JSON.stringify(real));
    check("video titles that mention ad words are kept; only the real ad item goes", out.contents.length === 1 && out.contents[0].videoRenderer.videoId === "x");
    const twice = w.JSON.parse(JSON.stringify(out));
    check("pruning twice changes nothing (idempotent)", JSON.stringify(twice) === JSON.stringify(out));
    const sec = w.JSON.parse(JSON.stringify({ responseContext: {}, contents: [{ itemSectionRenderer: { contents: [{ adSlotRenderer: {} }] } }, { itemSectionRenderer: { contents: [{ videoRenderer: { videoId: "k" } }] } }] }));
    check("section that only held ads is removed, other sections kept", sec.contents.length === 1 && hasKey(sec, "k"));
    check("adBlocksFound is 0, cannot be set, and stays invisible to loops/JSON", (() => { const o = w.eval("({a:1})"); o.adBlocksFound = 7; return o.adBlocksFound === 0 && Object.keys(o).join() === "a" && w.JSON.stringify(o) === '{"a":1}' && w.eval("(function(){var n=[];for(var k in {a:1})n.push(k);return n.join()})()") === "a"; })());
    // binary / blob XHR answers must pass through untouched
    const bin = await new Promise((res) => { const x = new w.XMLHttpRequest(); x.responseType = "arraybuffer"; x.open("GET", "/youtubei/v1/player"); x.onload = () => res(x); x.send(); });
    check("XHR with non-text response type does not throw", (() => { try { void bin.response; return true; } catch (e) { return false; } })());
    // speed: a big home feed (about 6 MB of JSON) must be cleaned in well under a second
    const tiles = []; for (let i = 0; i < 20000; i++) { tiles.push({ richItemRenderer: { content: { videoRenderer: { videoId: "v" + i, title: { runs: [{ text: "t".repeat(120) }] }, thumbnail: { thumbnails: [{ url: "https://i.ytimg.com/" + i, width: 1, height: 1 }] } } } } }); if (i % 10 === 0) tiles.push({ richItemRenderer: { content: { adSlotRenderer: { x: i } } } }); }
    const big = JSON.stringify({ responseContext: {}, contents: { richGridRenderer: { contents: tiles } } });
    const t0 = Date.now(); const cleaned = w.JSON.parse(big); const ms = Date.now() - t0;
    check(`${(big.length / 1e6).toFixed(1)} MB feed: all 2000 ads removed, all 20000 videos kept`, cleaned.contents.richGridRenderer.contents.length === 20000 && !hasKey(cleaned, "adSlotRenderer"));
    check(`...and fast enough for a TV box (${ms} ms here, limit 1500)`, ms < 1500);
  }

  console.log("\n[8] Safety: the ad fast-forward must never skip a real video");
  {
    const html = `<!doctype html><html><head></head><body><div id="movie_player" class="html5-video-player ad-showing"><video class="html5-main-video"></video></div></body></html>`;
    const w = boot({ html }).window; const v = w.document.querySelector("video");
    Object.defineProperty(v, "duration", { value: 3600, configurable: true }); v.currentTime = 10;
    await wait(700);
    check("stale ad flag on a 1-hour video: playback position untouched", v.currentTime === 10, "t=" + v.currentTime);
    const nf = boot({ html: html.replace(" ad-showing", "") }).window; const v2 = nf.document.querySelector("video");
    Object.defineProperty(v2, "duration", { value: 30, configurable: true }); v2.currentTime = 5; v2.muted = false;
    await wait(700);
    check("no ad flag: a normal video is never muted or seeked", v2.currentTime === 5 && v2.muted === false);
  }

  console.log("\n[9] Other YouTube hosts");
  {
    for (const u of ["https://m.youtube.com/watch?v=1", "https://www.youtube-nocookie.com/embed/abc", "https://music.youtube.com/"]) {
      const w = boot({ url: u, routes: ytRoutes }).window;
      const j = await (await w.fetch("/youtubei/v1/player")).json();
      check(`${new URL(u).host}: ads removed`, noAds(j));
    }
    const w = boot({ url: "https://notyoutube.com/", routes: ytRoutes }).window;
    const j = await (await w.fetch("/youtubei/v1/player")).json();
    check("lookalike host notyoutube.com is NOT treated as YouTube", hasKey(j, "adPlacements"));
  }

  console.log("\n[10] Every injected CSS selector is valid (one bad selector would void a whole rule)");
  for (const u of ["https://example.com/", "https://www.youtube.com/"]) {
    const w = boot({ url: u }).window; await wait(150);
    const css = [...w.document.querySelectorAll("style")].map((s) => s.textContent).join("\n");
    const sels = css.split(/\{display:none!important\}/).join(",").split(",").map((x) => x.trim()).filter(Boolean);
    const bad = sels.filter((x) => { try { w.document.querySelector(x); return false; } catch (e) { return true; } });
    check(`${new URL(u).host}: ${sels.length} selectors, all parse`, sels.length > 5 && bad.length === 0, bad.join(" | "));
  }
  {
    const html = `<!doctype html><html><head></head><body><amp-ad id=a></amp-ad><iframe id=f src="https://pixel.adsafeprotected.com/x"></iframe><article id=ok><h1>News</h1><p>text</p></article></body></html>`;
    const w = boot({ url: "https://example.com/", html }).window; await wait(150);
    const hidden = (id) => w.getComputedStyle(w.document.getElementById(id)).display === "none";
    check("new selectors hide amp-ad and ad-verification frames", hidden("a") && hidden("f"));
    check("article content still visible", !hidden("ok"));
  }
  done();
})().catch((e) => { console.error(e); process.exit(2); });
