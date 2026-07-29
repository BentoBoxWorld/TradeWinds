# TradeWinds — Manual In-Game Test Checklists

Per-stage manual verification on the test server (`/Users/ben/Minecraft/26.2`).
Deploy: `cp target/TradeWinds-*-LOCAL.jar /Users/ben/Minecraft/26.2/plugins/BentoBox/addons/`
then restart the server. Automated coverage lives in `src/test`; this file is for
what only a live server can prove.

## Stage 0 — Addon scaffold

- [ ] Server starts with no errors/warnings from TradeWinds in the console.
- [ ] `bbox version` lists TradeWinds alongside the other gamemodes (AcidIsland, AOneBlock, Gusher…), state ENABLED.
- [ ] Worlds `tradewinds_world` and `tradewinds_world_nether` exist (`mv list` / console log).
- [ ] `/tw` (and `/tradewinds`) teleports the player to spawn in open ocean — water to the horizon, no land, no vanilla continents.
- [ ] Ocean floor exists (dive down: sand/sandstone floor roughly y25–y50, bedrock at bottom, no caves by default).
- [ ] Above-water world is air up to build height (no floating junk).
- [ ] The interstice is inaccessible: nether portals in `tradewinds_world` do not activate/link (build one and light it), and no command teleports there.
- [ ] Interstice world (teleport there as admin, e.g. `mv tp`): water sea over basalt/soul-sand floor, nether ambience.
- [ ] No `tradewinds_world_the_end` world is created.
- [ ] `/twadmin` responds (admin help).
- [ ] `addons/TradeWinds/config.yml` generated with all Stage 0 sections (galaxy, travel, illegal-trade, world).
- [ ] Restart the server: worlds reload, no duplicate-world or generator errors, chunks unchanged (fly the same area).
- [ ] Other gamemodes still work (create/visit an AcidIsland island).

## Stage 1 — Seeded galaxy

Set `galaxy.seed` in `addons/TradeWinds/config.yml` to a known value (e.g. `20260729`)
and delete the `tradewinds_world*` folders for a clean generation, then:

- [ ] Console logs `TradeWinds galaxy seed: <seed>` on first world access.
- [ ] Fly (or `/twadmin tp` / creative-fly a boat) toward the nearest island — one of the 5 starter islands should be within ~4–8k blocks of spawn (console logs each registration: name, type, band, coords).
- [ ] Islands rise smoothly from the ocean: underwater shelf → beach → grassy interior; no cliffs of floating terrain, no chunk-border seams, no pop-in (land is generated, not pasted).
- [ ] Each island has a single whole-island biome matching its logged type (e.g. MINING → windswept hills; FROZEN → snowy, with **ice sheets in the surrounding water ring** — ride a boat over the ice: it should be fast).
- [ ] Entering an island's protection range announces its name ("Now entering <name>").
- [ ] `bbox` island info at an island (`/twadmin info` while standing there) shows an unowned island, range 1000, protection 400.
- [ ] Restart the server: the same islands are still registered (no duplicate-registration log lines), names unchanged.
- [ ] Regenerate the world from scratch with the same seed (stop server, delete world folders AND `database/` TradeWinds islands): identical island positions, names, types.
- [ ] Islands are never within sight of one another (min separation 2500).
- [ ] PvP setting: on a LAWLESS/ANARCHIC island the island PVP flag is on; on SAFE it is off.
