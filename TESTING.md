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

## Stage 1 — Seeded galaxy *(placeholder — filled in when Stage 1 lands)*
