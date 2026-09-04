# CLAUDE.md

Guidance for Claude Code working in this repository.

## Commands

```bash
mvn clean package          # build (jar in target/)
mvn test                   # all tests
mvn test -Dtest=ClassName  # one test class
```

Deploy for in-game testing: `./scripts/deploy.sh` (builds, then installs to
`/Users/ben/Minecraft/26.2/plugins/BentoBox/addons/`).

**Never overwrite the jar while the server is running** - the deploy script
refuses to. The plugin classloader reads classes lazily from the jar, so
replacing it invalidates the handle and any not-yet-loaded class throws
`NoClassDefFoundError`. When that lands inside chunk generation, Paper calls it
an unrecoverable chunk system failure and stops the server. The script has a
second guard: it also refuses while `latest.log` has been written in the last
minute, so a just-stopped server needs about a minute's wait - wait it out
rather than bypass it.

After any change to the hold, boat or ocean model, the test server needs a
**clean slate**: delete the `tradewinds_world*` folders and the `BoatHold`,
`TWPlayerData`, `TWIslandData` database folders. Stale records from a previous
model are indistinguishable from bugs. Say so when handing a build over.

## Project

**TradeWinds** is a BentoBox GameModeAddon for Paper **26.2**: an endless
procedurally generated ocean of NPC trading islands — buy low, sell high,
smuggle, hunt bounties, turn pirate. Requirements live in `docs/TRADEWINDS_SPEC.md`
(the authoritative spec; read it first). Design rationale is in
`docs/tradewinds-design-decisions.md`, the stage plan in `docs/tradewinds-dev-plan.md`.
`docs/PROGRESS.md` records what is done and pitfalls hit; `docs/TESTING.md` is the
manual test plan, ordered by risk (Tier 1 smoke first) - add new checks to the
right tier, not to the end; `docs/TESTING-archive.md` is the old per-stage list,
history only. Update PROGRESS and TESTING as features land.
**`docs/` is git-ignored** (ruled 2026-08-07: internal design docs stay out of
the public repo) — the files live only in this working copy, so never delete
the folder, and don't expect it in a fresh clone.

Load-bearing design rules (from the spec — breaking one is a bug):
trading transacts only against the **virtual hold**; **trader-bought** cargo
leaves the hold only by sale or destruction (player-loaded salvage may be
withdrawn — narrowed 2026-08-03, see `docs/tradewinds-salvage-plan.md`, and the
distinction is a PDC mark, `travel/CargoMark`); everything downstream of the ocean seed is a pure
function of (seed, position) with **no Bukkit imports** (package
`world.bentobox.tradewinds.ocean`), unit-tested headlessly; no End world ever;
interstice re-engage is always free; police mobs never drop loot.

## The boat/hold model (read before touching cargo or boats)

`docs/tradewinds-hold-plan.md` is **normative** here — it wins over the spec on
hold and boat mechanics. `docs/tradewinds-salvage-plan.md` is normative for the
salvage economy, the NBT-aware hold and price discovery — read it before
touching pricing, drift or hold contents, because it deliberately narrows the
one-way-cargo rule below to trader-bought cargo only.

- **The hold belongs to the BOAT, not the player.** One `BoatHold` record
  (material, cargo, fuel, expanders, owner, last-seen position, item TTL) is
  identified by a PDC id (`tradewinds:boat-id`) stamped on **both** the boat
  entity and its item form. `TWPlayerData` only points at `activeBoat` and
  `oldBoat`. Cargo therefore exists exactly once, however the avatar travels.
- **Any new route that produces a boat must stamp it** (`BoatService.stamp`).
  An unstamped hull has no lore and can never open its hold. This has bitten
  three times: vanilla placement (`EntityPlaceEvent`), teleport pickup, and
  crafting (the item lands on the **cursor**, not in a slot). `HoldGui`
  self-heals one narrow case; do not rely on it.
- **Match boats by identity, never by material.** Two oak boats are not the
  same boat.
- **Losing a boat is not abandoning one.** `HoldManager.setActiveBoat` demotes
  the previous boat to an unowned OLD BOAT; `clearActiveBoat` just drops the
  pointer. Taking a boat also strips its former owner — miss that and the
  victim keeps phantom slots and the login path hands the boat back.
- **Cargo is a list of stacks, one per slot** (`BoatHold.cargo`), not
  material→amount: a worn bow, a mint bow and a Silk Touch pick are three goods.
  Slot arithmetic lives in `travel/CargoStore`; fuel stays material-keyed because
  fuel is fungible. **Match cargo with `isSimilar`, never by material.**
- **Money is whole coins.** Buy prices `ceil`, sell prices `floor` — the
  direction stops the spread closing (nearest-rounding is exploitable). Never
  format money by hand: `economy.Money.format(addon, amount)` renders
  "$1,728" — symbol from `economy.currency-symbol`, no cents. (It used to ask
  Vault; reversed 2026-08-03 when the server economy printed "27.00 Dollars"
  over our whole coins.) The locale never carries a `$` of its own — the symbol
  arrives inside the formatted value.

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
`org.mockbukkit.mockbukkit:mockbukkit-v26.1.2:4.113.4-p262-2`, which **ships
inside this repo** (`libs/`, a file-based Maven repository declared in the
pom) so CI and fresh clones build green. It was built by
`~/git/GushBlock/scripts/build_patched_mockbukkit.py` (prereq: unzip the
paper-api jar to `/tmp/paperapi`). To update it: rerun the script, **bump the
`-2` suffix** (a shared CI agent once served a stale same-versioned copy from
its cache - never reuse a version), copy jar+pom into `libs/`, update
`mock-bukkit.version` in the pom.
Two class-level shims are copied into test sources (Adventure 4→5 breakage):
`org/mockbukkit/mockbukkit/adventure/PlainTextComponentProviderImpl` and
`net/kyori/adventure/util/Buildable`. Surefire needs the long `--add-opens`
argLine (already in pom.xml) or MockBukkit reflection dies on Java 25.
Force-init `org.bukkit.Tag.LEAVES` before static-mocking Bukkit (stale-mock
trap). Tests extend `world.bentobox.tradewinds.CommonTestSetup` (adapted from
Gusher/AOneBlock). Never leave stale files in `target/test-classes` when
renaming resources.

More traps, each of which cost a playtest:
- **`ItemStack` meta does not work** under this setup (the item factory is a
  bare mock, so `getItemMeta()` returns null). To test PDC behaviour, mock the
  ItemStack with a mocked `ItemMeta` and `PersistentDataContainer`.
- **Do not test against a hand-written stand-in for a manager.** `TestHolds`
  drives the REAL `HoldManager` over an in-memory `Database` (see
  `dataobjects/TestHoldManager`); an earlier fake "passed" rules the production
  code did not implement, and five bugs shipped.
- The inherited `world` field in `CommonTestSetup` **shadows the `world.`
  package root**, so fully-qualified `world.bentobox...` references fail to
  compile inside those tests. Import the simple names.
- `PlayerInteractEvent` at AIR is *born cancelled*: never pair
  `ignoreCancelled = true` with RIGHT_CLICK_AIR handling. Check
  `useItemInHand() == DENY` instead.
- Bare `yes`/`no`/`on`/`off` are YAML 1.1 **booleans** — never use them as
  locale keys. `ResourceYamlTest` fails the build on duplicate keys, because
  Bukkit only warns and then silently drops one.
- **`Settings.java` can never hold static constants.** BentoBox's YAML loader
  builds a PropertyDescriptor for EVERY declared field; any field without a
  getter/setter (a `static final`, say) makes `loadConfigObject()` throw and
  the addon boots disabled with null settings (found 2026-08-07 via a Sonar
  cleanup). Duplicated string keys in Settings are the price of the loader.
- **BentoBox REPLACES `Map` settings from `config.yml`, it does not merge them**
  (`YamlDatabaseHandler.deserializeMap`). A partial map in the shipped config
  silently overrides the whole code default — `economy.base-prices` shipped 27
  of 112 entries, so ~50 goods were unsellable on every real server while the
  unit tests, which read the code default, were perfectly happy. Any `@ConfigEntry`
  map must be complete in `config.yml`; `SettingsTest` now fails the build if the
  two drift.

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
**Never name an item or a port by hand** (GitHub #7): `[material]` values
come from `economy.ItemNames.label(user, material)` — the locale's
`tradewinds.materials.<key>` line if written, else a `<lang_or>` tag the
client translates itself — and `[name]` for a port from
`PortNames.display(addon, user, spec)`, which transliterates the name's
syllables through `tradewinds.name-token.*`. `IslandSpec.name()` is the
island's identity (registry, PDC, logs, admin arguments), never its label.
`PriceEngine.prettify` is for logs and admin audits only. Enum names reach a
player only through `getLocaleKey()`.
