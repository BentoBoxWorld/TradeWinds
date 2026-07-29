# TradeWinds
### A BentoBox gamemode of sea trading, smuggling, and piracy

**The pitch:** Your players are merchant sailors in an endless procedurally generated ocean dotted with trading islands. They start with nothing but a boat and a single trading bundle, and build a fortune by buying goods cheap on one island and selling them dear on another. How they get rich is up to them — honest hauling, smuggling contraband past customs patrols, hunting bounties, or outright piracy. Every choice has a price, and the game keeps score.

---

## The world
The ocean is generated from a single shareable seed. Trading islands appear at random positions across the ocean — sparsely scattered, and always far enough apart that you can never see one island from another. Each has its own biome, procedurally generated name, and economic character: farm islands sell food cheap, industrial islands want raw materials, and so on. Islands generate lazily as players push outward, so the world grows with your community. Villagers run the markets; the islands themselves are protected — no building or breaking.

Each island has a **security level**, from *safe* (no PvP, no hostile mobs, heavy policing) down to *anarchic* (anything goes). The best profit margins — and the black markets — are in dangerous space.

## Getting around
Boats are everything. Players row freely between islands, or reach an island's border and **warp**: a fuel-powered jump straight to any charted island, paid for with wood, coal, or lava carried in the boat's hold. Warping is fast but fuel and cargo compete for the same space — the game's central tradeoff. Rowing is free but slow, and the open ocean between islands is lawless.

Warps occasionally *fail*, dropping the player into a hostile Nether sea mid-route with Ghasts inbound. Fight or re-engage the warp — the detour is free, the fright isn't.

Cargo grows in stages: bundles → chest boat → purchasable cargo expanders, with costs that scale steeply. A maxed-out trade ship carries a fortune and is worth defending — or attacking.

## Law and disorder
Every player has a **reputation**. Attacking villagers, killing innocents, or trading contraband (sugar, by default) drives it down; clean living slowly restores it. Slip too far and you're *Wanted*: open to PvP everywhere, carrying a bounty any player can collect, and met at civilized islands by the police — Iron Golems on land, Guardians at sea, Phantoms in the air. A wanted player's bounty hangs right over their head for all to see (configurable) — a target sign for hunters, a badge of infamy for pirates.

Entering an island's waters triggers a **customs scan**. Get flagged with contraband and the patrol launches — flee, fight, or dump the evidence overboard before they close in. Smuggling is a skill, not a tax.

Players who earn enough can **buy their own island**, placed in the open ocean between trading islands, and eventually sell their own crafted goods into the economy.

## For admins
- Runs on **BentoBox** alongside your other gamemodes; prices derive from the **BlueBook** economy addon.
- Nearly everything is a config knob: island spacing, fuel costs and warp-route pricing, scan chances, police response size, reputation thresholds, warp failure rate, cargo expansion caps.
- **Contraband and slave-trading mechanics can be disabled entirely** for family-friendly servers.
- One seed defines the whole galaxy — share it and another server gets the same world.

*Inspired by the classic trading games TradeWars 2002 and Elite — rebuilt entirely in Minecraft terms.*
