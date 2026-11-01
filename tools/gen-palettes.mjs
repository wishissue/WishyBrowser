// Generates app/src/main/java/org/wishy/browser/ui/theme/Palettes.kt: 45 Material 3 colour schemes
// (Light / Dark / Dusk / AMOLED each) from seed colours, using material-color-utilities.
// Method adapted from Orion Store's tools/gen-scheme.mjs (GPL-3.0, RookieEnough/Orion-Store).
// Usage: npm i @material/material-color-utilities in a scratch dir, then
//   node tools/gen-palettes.mjs <scratch>/node_modules/@material/material-color-utilities/index.js
import { writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
const { Hct, DynamicScheme, Variant, TonalPalette, argbFromHex, hexFromArgb } = await import(pathToFileURL(process.argv[2]).href);

// id, label, seed, tertiary seed, primary chroma, tertiary chroma
const PALETTES = [
  ['wishy', 'Wishy Blue', '#51A9E8', '#F59E0B', 56, 52],
  ['orion', 'Orion', '#6366F1', '#A3E635', 64, 56],
  ['ocean', 'Ocean', '#0EA5E9', '#FB7185', 56, 48],
  ['forest', 'Forest', '#16A34A', '#F59E0B', 48, 52],
  ['sunset', 'Sunset', '#F97316', '#8B5CF6', 60, 48],
  ['rose', 'Rose', '#EC4899', '#14B8A6', 60, 44],
  ['mono', 'Mono', '#64748B', '#64748B', 6, 6],
  ['grape', 'Grape', '#8B5CF6', '#F472B6', 60, 48],
  ['mint', 'Mint', '#10B981', '#6366F1', 48, 44],
  ['ember', 'Ember', '#EF4444', '#F59E0B', 64, 52],
  ['amber', 'Amber', '#F59E0B', '#0EA5E9', 52, 44],
  ['cyber', 'Cyber', '#06B6D4', '#D946EF', 56, 56],
  ['midnight', 'Midnight', '#3B82F6', '#FACC15', 52, 52],
  ['lime', 'Lime', '#84CC16', '#EC4899', 52, 48],
  ['cocoa', 'Cocoa', '#92400E', '#65A30D', 32, 40],
  ['sky', 'Sky', '#38BDF8', '#F472B6', 52, 48],
  ['azure', 'Azure', '#2563EB', '#F97316', 60, 52],
  ['indigo', 'Indigo', '#4F46E5', '#22D3EE', 64, 52],
  ['violet', 'Violet', '#7C3AED', '#FBBF24', 64, 52],
  ['orchid', 'Orchid', '#C026D3', '#34D399', 60, 48],
  ['magenta', 'Magenta', '#DB2777', '#A3E635', 62, 52],
  ['crimson', 'Crimson', '#DC2626', '#38BDF8', 64, 48],
  ['coral', 'Coral', '#FB7185', '#2DD4BF', 52, 44],
  ['peach', 'Peach', '#FDBA74', '#818CF8', 40, 44],
  ['tangerine', 'Tangerine', '#EA580C', '#60A5FA', 62, 48],
  ['gold', 'Gold', '#EAB308', '#A78BFA', 52, 44],
  ['banana', 'Banana', '#FACC15', '#F472B6', 48, 44],
  ['olive', 'Olive', '#65A30D', '#F97316', 44, 48],
  ['jade', 'Jade', '#059669', '#F472B6', 48, 44],
  ['emerald', 'Emerald', '#22C55E', '#8B5CF6', 52, 48],
  ['teal', 'Teal', '#0D9488', '#FB923C', 46, 48],
  ['aqua', 'Aqua', '#22D3EE', '#F43F5E', 48, 52],
  ['glacier', 'Glacier', '#7DD3FC', '#FDA4AF', 36, 40],
  ['arctic', 'Arctic', '#67E8F9', '#C4B5FD', 36, 40],
  ['lavender', 'Lavender', '#A78BFA', '#6EE7B7', 44, 40],
  ['lilac', 'Lilac', '#D8B4FE', '#FCD34D', 36, 40],
  ['bubblegum', 'Bubblegum', '#F9A8D4', '#5EEAD4', 40, 40],
  ['blush', 'Blush', '#FDA4AF', '#93C5FD', 36, 36],
  ['sand', 'Sand', '#D6BC8E', '#6EE7B7', 24, 32],
  ['clay', 'Clay', '#B45309', '#14B8A6', 44, 40],
  ['espresso', 'Espresso', '#78350F', '#F59E0B', 28, 44],
  ['slate', 'Slate', '#475569', '#38BDF8', 10, 40],
  ['graphite', 'Graphite', '#374151', '#F472B6', 8, 40],
  ['steel', 'Steel', '#94A3B8', '#F59E0B', 14, 44],
  ['neon', 'Neon', '#10F5A0', '#F000FF', 60, 60],
];
if (PALETTES.length !== 45) throw new Error('need exactly 45 palettes, have ' + PALETTES.length);
const roles = ['primary','onPrimary','primaryContainer','onPrimaryContainer','inversePrimary','secondary','onSecondary','secondaryContainer','onSecondaryContainer',
 'tertiary','onTertiary','tertiaryContainer','onTertiaryContainer','background','onBackground','surface','onSurface','surfaceVariant','onSurfaceVariant','surfaceTint',
 'inverseSurface','inverseOnSurface','error','onError','errorContainer','onErrorContainer','outline','outlineVariant','scrim','surfaceBright','surfaceContainer',
 'surfaceContainerHigh','surfaceContainerHighest','surfaceContainerLow','surfaceContainerLowest','surfaceDim',
 'primaryFixed','primaryFixedDim','onPrimaryFixed','onPrimaryFixedVariant','secondaryFixed','secondaryFixedDim','onSecondaryFixed','onSecondaryFixedVariant',
 'tertiaryFixed','tertiaryFixedDim','onTertiaryFixed','onTertiaryFixedVariant'];
const k = v => 'Color(0xFF' + hexFromArgb(v).slice(1).toUpperCase() + ')';
const wrap = s => s.replace(/(([^,]*,){4})/g, '$1\n        ');

const fns = PALETTES.map(([id, label, seed, tert, pc, tc]) => {
  const src = Hct.fromInt(argbFromHex(seed)), t3 = Hct.fromInt(argbFromHex(tert));
  const mk = dark => new DynamicScheme({ sourceColorHct: src, variant: Variant.TONAL_SPOT, contrastLevel: 0, isDark: dark, specVersion: '2025', platform: 'phone',
    primaryPalette: TonalPalette.fromHueAndChroma(src.hue, pc), secondaryPalette: TonalPalette.fromHueAndChroma(src.hue, Math.min(20, pc)),
    neutralPalette: TonalPalette.fromHueAndChroma(src.hue, Math.min(5, pc)), neutralVariantPalette: TonalPalette.fromHueAndChroma(src.hue, Math.min(10, pc)),
    tertiaryPalette: TonalPalette.fromHueAndChroma(t3.hue, tc) });
  const scheme = dark => { const s = mk(dark); return `${dark ? 'dark' : 'light'}ColorScheme(\n        ` + wrap(roles.map(r => `${r} = ${k(s[r])}`).join(', ')) + ',\n    )'; };
  const dusk = TonalPalette.fromHueAndChroma(src.hue, Math.min(14, pc)), nv = TonalPalette.fromHueAndChroma(src.hue, Math.min(18, pc)), d = n => k(dusk.tone(n));
  const n = TonalPalette.fromHueAndChroma(src.hue, Math.min(5, pc)), o = x => k(n.tone(x));
  return `private fun p_${id}(mode: String): ColorScheme {
    if (mode == "light") return ${scheme(false)}
    val d = ${scheme(true)}
    return when (mode) {
        "dusk" -> d.copy(background = ${d(12)}, surface = ${d(12)}, surfaceDim = ${d(12)}, surfaceBright = ${d(32)}, surfaceContainerLowest = ${d(8)}, surfaceContainerLow = ${d(15)},
            surfaceContainer = ${d(18)}, surfaceContainerHigh = ${d(22)}, surfaceContainerHighest = ${d(26)}, surfaceVariant = ${d(26)}, onSurfaceVariant = ${k(nv.tone(76))}, outlineVariant = ${k(nv.tone(34))})
        "oled" -> d.copy(background = Color.Black, surface = Color.Black, surfaceDim = Color.Black, surfaceContainerLowest = Color.Black, surfaceContainerLow = ${o(4)},
            surfaceContainer = ${o(7)}, surfaceContainerHigh = ${o(10)}, surfaceContainerHighest = ${o(14)}, surfaceVariant = ${o(14)}, surfaceBright = ${o(20)})
        else -> d
    }
}`;
});

const meta = PALETTES.map(([id, label, seed]) => `    PaletteInfo("${id}", "${label}", Color(0xFF${seed.slice(1).toUpperCase()}))`).join(',\n');
const whens = PALETTES.map(([id]) => `        "${id}" -> p_${id}(mode)`).join('\n');
const out = `// Generated by tools/gen-palettes.mjs from material-color-utilities (spec 2025, tonal spot). Do not edit by hand.
// 45 palettes x (light, dark, dusk, amoled). Each palette is its own function so nothing is built until used.
package org.wishy.browser.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

class PaletteInfo(val id: String, val label: String, val seed: Color)

val PALETTE_INFOS: List<PaletteInfo> = listOf(
${meta}
)

/** [mode] is one of "dark", "light", "dusk", "oled". Unknown ids fall back to the first palette. */
fun paletteScheme(id: String, mode: String): ColorScheme = when (id) {
${whens}
        else -> p_${PALETTES[0][0]}(mode)
}

${fns.join('\n\n')}
`;
mkdirSync2();
function mkdirSync2() { import('node:fs').then(fs => fs.mkdirSync('app/src/main/java/org/wishy/browser/ui/theme', { recursive: true })); }
await new Promise(r => setTimeout(r, 200));
writeFileSync('app/src/main/java/org/wishy/browser/ui/theme/Palettes.kt', out);
console.log(`wrote ${PALETTES.length} palettes, ${(out.length / 1024).toFixed(0)} KB`);
