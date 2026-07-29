# TradeWinds

A BentoBox game mode of sea trading, smuggling, and piracy: an endless
procedurally generated ocean dotted with NPC trading islands. Start with a boat
and a single trading bundle; get rich buying low and selling high — honestly,
or by smuggling contraband past customs, hunting bounties, or outright piracy.

Inspired by TradeWars 2002 and Elite, rebuilt in Minecraft terms.

- Spec: [`TRADEWINDS_SPEC.md`](TRADEWINDS_SPEC.md)
- Progress: [`docs/PROGRESS.md`](docs/PROGRESS.md)
- Manual test checklists: [`TESTING.md`](TESTING.md)

## Building

```bash
mvn clean package        # requires JDK 25; jar lands in target/
```

Requires BentoBox 3.18.1+ on Paper 26.2. Drop the jar in
`plugins/BentoBox/addons/`.
