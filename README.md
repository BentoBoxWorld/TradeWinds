# TradeWinds

A BentoBox game mode of sea trading, smuggling, and piracy: an endless
procedurally generated ocean dotted with NPC trading islands. You start with a
rowing boat — **the boat is your cargo hold** — and get rich buying low and
selling high, honestly or otherwise: smuggling contraband past customs,
hunting bounties, or taking other sailors' ships.

Inspired by TradeWars 2002 and Elite, rebuilt in Minecraft terms.

> Status: in development. Playable end to end (trade, travel, crime, boats);
> player-owned islands are the remaining MVP stage. The data model is not
> stable yet — expect to wipe worlds and databases between builds.

## How it plays

**The ocean is one shareable seed.** Island positions, types, biomes, names,
security bands, tech levels and route costs are all pure functions of it, so
the same seed gives another server the same galaxy. Islands generate lazily as
players push outward, and the sea between them is dotted with wild islets —
free land to mine, farm and build on, with biome-appropriate ruins to find.

**The boat is the hold.** A player has exactly one boat, and it sizes their
cargo: twenty ranks from a two-slot bamboo raft to the 21-slot Pale Oak Chest
Boat, then installable cargo expanders beyond that. The hold is virtual and
server-authoritative — no real container ever holds cargo, and cargo leaves it
only by being sold or destroyed. Fuel lives in its own slots, so fuel and
cargo never compete for the same space.

**Trade reads the map.** Prices come from recipe-derived base values times the
island's type, tech level, security band and its own stock drift, so the best
routes are differentials you find by reading the chart. Margins are richest
where the law is thinnest.

**Travel is a choice.** Rowing is free, slow and lawless. Warping is instant
but burns fuel and can fail, dropping you into the interstice — a hostile
Nether sea — with ghasts inbound and a free way back out.

**Crime pays you into danger.** Contraband sells only at rougher ports.
Customs scan you on arrival; get caught and a patrol launches from the pier,
and you flee, fight or destroy the evidence. Reputation follows you: slip far
enough and police answer, your bounty hangs over your head, and safe markets
close to you.

**Boats can be taken.** A hull left unattended in protected island space is
safe; anywhere else it is fair game, and whoever boards it gets the cargo too.
Lose yours and the chart remembers where you left it.

## Documentation

| Document | What it is |
|---|---|
| [`TRADEWINDS_SPEC.md`](TRADEWINDS_SPEC.md) | The authoritative spec — read first |
| [`tradewinds-hold-plan.md`](tradewinds-hold-plan.md) | Normative detail for the hold, boats and capture rules |
| [`tradewinds-salvage-plan.md`](tradewinds-salvage-plan.md) | Normative plan for the salvage economy, NBT hold and price discovery |
| [`tradewinds-design-decisions.md`](tradewinds-design-decisions.md) | What was decided, why, and what is still open |
| [`tradewinds-dev-plan.md`](tradewinds-dev-plan.md) | The stage plan |
| [`tradewinds-overview.md`](tradewinds-overview.md) | The short pitch, for server admins |
| [`docs/PROGRESS.md`](docs/PROGRESS.md) | What is done, and every pitfall hit on the way |
| [`TESTING.md`](TESTING.md) | Manual test plan, ordered by risk |

## Building

```bash
mvn clean package        # jar lands in target/
mvn test                 # 250 headless tests
```

Build with **JDK 25** (Paper 26.2 ships Java 25 bytecode); the project
compiles at release 21. Requires BentoBox 3.18.1+ on Paper 26.2.

Everything downstream of the galaxy seed lives in
`world.bentobox.tradewinds.galaxy` with **no Bukkit imports**, so the world
generation and pricing maths are unit-tested headlessly.

## Installing

Drop the jar in `plugins/BentoBox/addons/` and restart. Vault is required for
the economy; PlaceholderAPI and the Border addon are optional. TradeWinds
coexists with other BentoBox game modes.

Nearly every number is a config knob: island spacing and density, tech and
band effects, fuel and warp costs, boat ranks and prices, scan chances, police
response, reputation thresholds. The contraband and crime layers can be turned
off entirely for family-friendly servers.
