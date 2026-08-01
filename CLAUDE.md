# CLAUDE.md

Guidance for Claude Code working in this repository.

## Commands

```bash
mvn clean package          # build (jar in target/)
mvn test                   # all tests
mvn test -Dtest=ClassName  # one test class
```

Deploy for in-game testing: copy `target/TradeWinds-*-LOCAL.jar` to
`/Users/ben/Minecraft/26.2/plugins/BentoBox/addons/`.

## Project

**TradeWinds** is a BentoBox GameModeAddon for Paper **26.2**: an endless
procedurally generated ocean of NPC trading islands — buy low, sell high,
smuggle, hunt bounties, turn pirate. Requirements live in `TRADEWINDS_SPEC.md`
(the authoritative spec; read it first). Design rationale is in
`tradewinds-design-decisions.md`, the stage plan in `tradewinds-dev-plan.md`.
`docs/PROGRESS.md` records what is done and pitfalls hit; `TESTING.md` holds
the manual in-game test checklist per stage. Update both as features land.

Load-bearing design rules (from the spec — breaking one is a bug):
trading transacts only against bundles/boat hold; everything downstream of the
galaxy seed is a pure function of (seed, position) with **no Bukkit imports**
(package `world.bentobox.tradewinds.galaxy`), unit-tested headlessly; no End
world ever; interstice re-engage is always free; police mobs never drop loot.

## Environment (verified — do not "upgrade" blindly)

- Paper API `26.2.build.40-alpha` (Java 25 bytecode: build with **JDK 25**,
  compile at release 21), BentoBox `3.18.1` from `~/.m2`, test server runs
  BentoBox 3.21.1-SNAPSHOT.
- Test server: `/Users/ben/Minecraft/26.2` — has Vault, PlaceholderAPI,
  LuckPerms, Multiverse, Border addon, and other gamemodes (AcidIsland,
  AOneBlock, Gusher…) — TradeWinds must coexist with all of them.
- `org.bukkit.Sound` is an **interface** now — no `valueOf`; resolve
  config-supplied sound names via
  `RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT)`.

## Testing quirks (important)

MockBukkit has **no 26.2 release**; tests run against the locally patched jar
`org.mockbukkit.mockbukkit:mockbukkit-v26.1.2:4.113.4-p262`, built by
`~/git/GushBlock/scripts/build_patched_mockbukkit.py` (rerun it if the version
disappears from `~/.m2`; prereq: unzip the paper-api jar to `/tmp/paperapi`).
Two class-level shims are copied into test sources (Adventure 4→5 breakage):
`org/mockbukkit/mockbukkit/adventure/PlainTextComponentProviderImpl` and
`net/kyori/adventure/util/Buildable`. Surefire needs the long `--add-opens`
argLine (already in pom.xml) or MockBukkit reflection dies on Java 25.
Force-init `org.bukkit.Tag.LEAVES` before static-mocking Bukkit (stale-mock
trap). Tests extend `world.bentobox.tradewinds.CommonTestSetup` (adapted from
Gusher/AOneBlock). Never leave stale files in `target/test-classes` when
renaming resources.

## Reference repos (all local)

- `~/git/bentobox` — BentoBox core source.
- `~/git/addon-acidisland` — GameModeAddon anatomy, ocean worlds, nether roof,
  the dismount/teleport/re-seat boat pattern.
- `~/git/poseidon` — ocean generator (PerlinOctaveGenerator, sea floor noise,
  BiomeProvider, BlockPopulator decoration).
- `~/git/GushBlock` — newest 26.2-era addon: pom, test bootstrap, patched
  MockBukkit, `docs/API_VERIFICATION.md` (verified 26.2 API facts).
- `~/git/Boxed` — structure/jigsaw placement patterns (Stage 2).
- `~/git/Border` — border display addon (passable visual border, Stage 3).
- `~/git/bluebook` — origin of the pricing logic now embedded in
  `economy.PriceEngine` (recipe-derived base prices).

Follow BentoBox conventions: `Config<Settings>` + `@ConfigEntry`/`@StoreAt`,
`Database<DataObject>` + cache managers, `FlagListener`, `DefaultPlayerCommand`
/`DefaultAdminCommand`, locale keys under `tradewinds.`, every gameplay number
in config from the stage it's introduced — no hardcoded gameplay values.

**All player-facing text lives in the locale** — never build strings or
Components in code, or the game mode cannot be translated. Locale formatting
is MiniMessage (`<red>text</red>`); legacy `&` codes are deprecated.
Never call MiniMessage yourself: use the `User` API, which is locale-aware —
`user.sendMessage(key, vars...)` for chat, and
`user.getTranslationAsComponent(key, vars...)` wherever a Component is needed
(dialogs, boss bars, holograms, item names, inventory titles). The no-variable
call is ambiguous between overloads, so pass `new String[0]`.
Locale strings can also carry `[actionbar]`, `[title]`, `[subtitle]` and
`[sound:...]` markers — BentoBox routes and formats those itself, so prefer
them over calling `sendActionBar`/`playSound` around a message.
