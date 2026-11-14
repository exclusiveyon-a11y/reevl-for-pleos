package kr.co.reevl.ontheway.ui.ds.comp

import androidx.compose.ui.graphics.toArgb

/**
 * 동선 지도 바탕 (MapLibre style JSON) — a port of the web's `reevl/frontend/lib/planMapStyle.ts` (2026-11-11): OpenFreeMap
 * vector tiles (keyless, free), REEVL's quiet map palette. Land · water · green · road hierarchy · subway · place names; the route
 * and the numbered pins are the subject and are drawn over it by [PlanRouteMap].
 *
 * ★The palette is the web's PLAN_MAP — the map-only colours come from the Reevl car tokens (tokens.json car.color.map), not literals here.
 * ★Korean names are drawn with the device font (MapLibre localIdeographFontFamily); the glyph PBFs only serve Latin text.
 * ★Attribution (OpenFreeMap © OpenStreetMap) is shown on the map by [PlanRouteMap] — required by both.
 */
object PlanMapStyle {
    data class Palette(val land: String, val water: String, val green: String, val road: String, val road2: String, val road3: String,
                       val casing: String, val rail: String, val label: String, val halo: String, val bld: String)

    /** `#rrggbb` of a generated Reevl token colour (MapLibre takes CSS colour strings). */
    private fun hex(c: androidx.compose.ui.graphics.Color): String = "#%06x".format(c.toArgb() and 0xFFFFFF)
    private val L = kr.co.reevl.design.ReevlPalette.Light
    private val D = kr.co.reevl.design.ReevlPalette.Dark

    private val ML = kr.co.reevl.design.car.ReevlCarTheme.MapColors.Light
    private val MD = kr.co.reevl.design.car.ReevlCarTheme.MapColors.Dark

    // Web lib/planMapStyle.ts PLAN_MAP. Values equal to a Reevl token read the token (land = chip, roads = bg, rail = ink2 / sub);
    // the map-only colours (water, green, buildings, label grey, night land and roads) are the car token group
    // ReevlCarTheme.MapColors (reevl tokens.json car.color.map, checked against the web PLAN_MAP by `npm run car:tokens`).
    val Light = Palette(land = hex(L.chip), water = hex(ML.water), green = hex(ML.green), road = hex(L.bg), road2 = hex(L.bg), road3 = hex(L.bg),
        casing = "rgba(25,31,40,0.10)", rail = hex(L.ink2), label = hex(ML.label), halo = "rgba(255,255,255,0.85)", bld = hex(ML.building))
    val Dark = Palette(land = hex(MD.land), water = hex(MD.water), green = hex(MD.green), road = hex(MD.road), road2 = hex(MD.road2), road3 = hex(MD.road3),
        casing = "rgba(0,0,0,0)", rail = hex(D.sub), label = hex(MD.label), halo = "rgba(34,40,49,0.88)", bld = hex(MD.building))

    const val TILES = "https://tiles.openfreemap.org/planet"
    const val GLYPHS = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"
    // OpenFreeMap's required attribution (openfreemap.org quick start: "OpenFreeMap © OpenMapTiles Data from OpenStreetMap") and
    // the OSM licence wording (osm.org/copyright: "© OpenStreetMap contributors"). Also listed in 설정 › 오픈소스 라이선스.
    const val ATTRIBUTION = "OpenFreeMap © OpenMapTiles © OpenStreetMap contributors"

    /** The style for the car: the web's layers, label sizes × 2 (car scale, car-ui-spec §1.5) so a name is readable from the seat. */
    fun json(dark: Boolean): String {
        val c = if (dark) Dark else Light
        val name = """["coalesce",["get","name:ko"],["get","name"]]"""
        return """
{"version":8,"glyphs":"$GLYPHS",
 "sources":{"omt":{"type":"vector","url":"$TILES","attribution":"$ATTRIBUTION"}},
 "layers":[
  {"id":"bg","type":"background","paint":{"background-color":"${c.land}"}},
  {"id":"park","type":"fill","source":"omt","source-layer":"park","minzoom":10,"paint":{"fill-color":"${c.green}"}},
  {"id":"green","type":"fill","source":"omt","source-layer":"landcover","minzoom":10,"filter":["match",["get","class"],["wood","grass"],true,false],"paint":{"fill-color":"${c.green}"}},
  {"id":"water","type":"fill","source":"omt","source-layer":"water","filter":["!=",["get","brunnel"],"tunnel"],"paint":{"fill-color":"${c.water}"}},
  {"id":"building","type":"fill","source":"omt","source-layer":"building","minzoom":14.5,"paint":{"fill-color":"${c.bld}","fill-opacity":0.8}},
  {"id":"road-case","type":"line","source":"omt","source-layer":"transportation","minzoom":12,"filter":["match",["get","class"],["motorway","trunk","primary","secondary"],true,false],
   "layout":{"line-cap":"round","line-join":"round"},"paint":{"line-color":"${c.casing}","line-width":["interpolate",["exponential",1.5],["zoom"],12,2.4,14,5,16,11]}},
  {"id":"road-minor","type":"line","source":"omt","source-layer":"transportation","minzoom":14.5,"filter":["match",["get","class"],["minor","service","tertiary"],true,false],
   "layout":{"line-cap":"round","line-join":"round"},"paint":{"line-color":"${c.road3}","line-width":["interpolate",["exponential",1.5],["zoom"],14.5,0.8,16,3,17,5]}},
  {"id":"road-secondary","type":"line","source":"omt","source-layer":"transportation","minzoom":11.5,"filter":["==",["get","class"],"secondary"],
   "layout":{"line-cap":"round","line-join":"round"},"paint":{"line-color":"${c.road3}","line-width":["interpolate",["exponential",1.5],["zoom"],11.5,0.6,14,2,16,6]}},
  {"id":"road-primary","type":"line","source":"omt","source-layer":"transportation","minzoom":9,"filter":["match",["get","class"],["primary","trunk"],true,false],
   "layout":{"line-cap":"round","line-join":"round"},"paint":{"line-color":"${c.road2}","line-width":["interpolate",["exponential",1.5],["zoom"],9,0.5,12,1.3,14,3.4,16,9]}},
  {"id":"road-major","type":"line","source":"omt","source-layer":"transportation","minzoom":6,"filter":["==",["get","class"],"motorway"],
   "layout":{"line-cap":"round","line-join":"round"},"paint":{"line-color":"${c.road}","line-width":["interpolate",["exponential",1.5],["zoom"],6,0.5,12,2.2,14,4.5,16,11]}},
  {"id":"subway","type":"line","source":"omt","source-layer":"transportation","minzoom":10,
   "filter":["all",["match",["get","class"],["transit","rail"],true,false],["match",["get","subclass"],["subway","light_rail"],true,false]],
   "paint":{"line-color":"${c.rail}","line-opacity":0.3,"line-width":["interpolate",["linear"],["zoom"],10,0.6,14,1.4]}},
  {"id":"road-name","type":"symbol","source":"omt","source-layer":"transportation_name","minzoom":14,"filter":["match",["get","class"],["motorway","trunk","primary","secondary"],true,false],
   "layout":{"symbol-placement":"line","symbol-spacing":420,"text-size":20,"text-field":$name,"text-font":["Noto Sans Regular"]},
   "paint":{"text-color":"${c.label}","text-halo-color":"${c.halo}","text-halo-width":1.6}},
  {"id":"place","type":"symbol","source":"omt","source-layer":"place","filter":["match",["get","class"],["city","town","suburb","quarter","neighbourhood"],true,false],
   "layout":{"text-field":$name,"text-font":["Noto Sans Regular"],"text-size":["match",["get","class"],["city","town"],24,20]},
   "paint":{"text-color":"${c.label}","text-halo-color":"${c.halo}","text-halo-width":1.6}}
 ]}
""".trimIndent()
    }
}
