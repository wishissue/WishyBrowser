// Runs adblock.js the way the app does: config object first, then the script, at document start of a page.
const fs = require("fs");
const path = require("path");
const { JSDOM } = require("jsdom");

const JS_PATH = process.env.ADBLOCK_JS || path.join(__dirname, "../../app/src/main/assets/adblock.js");
const SRC = fs.readFileSync(JS_PATH, "utf8");

// A stand-in for XMLHttpRequest with the same shape as the real one: response/responseText are
// accessor properties on the prototype, which is where a hook has to sit to be effective.
function makeXhr(window, routes) {
  class XHR {
    constructor() { this.readyState = 0; this.responseType = ""; this._body = null; this.onload = null; }
    open(m, u) { this._url = new window.URL(u, window.location.href).href; this.readyState = 1; }
    send() {
      const body = routes(this._url);
      setTimeout(() => { this._body = body; this.readyState = 4; this.status = 200; this.onload && this.onload(); }, 0);
    }
    get responseText() { return this._body; }
    get response() { return this.responseType === "json" ? window.JSON.parse(this._body) : this._body; }
    get responseURL() { return this._url; }
  }
  return XHR;
}

function boot({ url = "https://www.youtube.com/watch?v=abc123", cfg = { yt: true, cos: true, allow: [] }, html = "<!doctype html><html><head></head><body></body></html>", routes = () => "{}", script = SRC } = {}) {
  const dom = new JSDOM(html, {
    url, runScripts: "dangerously", pretendToBeVisual: true,
    beforeParse(window) {
      // A private Response class per window: hooks must never leak from one test page into the next.
      window.Response = class extends Response {
        // In a browser clone() keeps the prototype and url; Node's returns a plain Response.
        clone() { const c = super.clone(); Object.setPrototypeOf(c, Object.getPrototypeOf(this)); Object.defineProperty(c, "url", { value: this.url }); return c; }
      };
      window.fetch = (u) => {
        const abs = new window.URL(u, window.location.href).href;
        const r = new window.Response(routes(abs), { status: 200 });
        Object.defineProperty(r, "url", { value: abs });
        return Promise.resolve(r);
      };
      window.XMLHttpRequest = makeXhr(window, routes);
      window.HTMLMediaElement.prototype.play = function () { this.__played = (this.__played || 0) + 1; return Promise.resolve(); };
      window.eval("window.__wyAd=" + JSON.stringify(cfg) + ";" + script);
    },
  });
  return dom;
}

let pass = 0, fail = 0; const failures = [];
function check(name, cond, extra) {
  if (cond) { pass++; console.log("  ok    " + name); }
  else { fail++; failures.push(name); console.log("  FAIL  " + name + (extra ? "  -> " + extra : "")); }
}
const done = () => { console.log(`\n${pass} passed, ${fail} failed  (${path.relative(process.cwd(), JS_PATH)})`); process.exit(fail ? 1 : 0); };
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const hasKey = (o, k) => JSON.stringify(o).includes('"' + k + '"');

module.exports = { boot, check, done, wait, hasKey, SRC };
