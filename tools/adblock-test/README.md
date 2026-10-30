# Ad blocking tests

What this does and does not prove: it runs `adblock.js` in a simulated browser (jsdom) against YouTube-shaped data,
and compiles `AdRules.kt` on a normal JVM. It does **not** run a real Android WebView or contact YouTube. After
changing the ad layer, still watch a few videos on a device.

## JavaScript (needs Node 18+)
```
cd tools/adblock-test
npm install
npm test                                    # tests app/src/main/assets/adblock.js
ADBLOCK_JS=/path/to/other.js node run.js    # test another version of the script
```

## Request rules (needs kotlinc, no Android SDK)
```
kotlinc ../../app/src/main/java/org/wishy/browser/AdBlock.kt AdRulesCheck.kt -include-runtime -d /tmp/adrules.jar
java -jar /tmp/adrules.jar
```

`fixtures/yt.js` holds the response shapes. When YouTube changes its data, add the new shape there first, watch the
test fail, then fix `adblock.js`.
