package world.bentobox.tradewinds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Difficulty;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.configuration.ConfigComment;
import world.bentobox.bentobox.api.configuration.ConfigEntry;
import world.bentobox.bentobox.api.configuration.StoreAt;
import world.bentobox.bentobox.api.configuration.WorldSettings;
import world.bentobox.bentobox.api.flags.Flag;
import world.bentobox.tradewinds.crime.Crime;
import world.bentobox.tradewinds.crime.ReputationScale;
import world.bentobox.bentobox.database.objects.adapters.Adapter;
import world.bentobox.bentobox.database.objects.adapters.FlagBooleanSerializer;


/**
 * All the plugin settings are here
 *
 * @author Tastybento
 */
@StoreAt(filename="config.yml", path="addons/TradeWinds") // Explicitly call out what name this should have.
@ConfigComment("TradeWinds Configuration [version]")
public class Settings implements WorldSettings {

    /* Commands */
    @ConfigComment("Player command. What command users will run to access their island.")
    @ConfigComment("To define alias, just separate commands with white space.")
    @ConfigEntry(path = "tradewinds.command.island") //, since = "1.3.0")
    private String playerCommandAliases = "tw tradewinds";

    @ConfigComment("The admin command.")
    @ConfigComment("To define alias, just separate commands with white space.")
    @ConfigEntry(path = "tradewinds.command.admin") // , since = "1.3.0")
    private String adminCommandAliases = "twadmin";

    @ConfigComment("The default action for new player command call.")
    @ConfigComment("Sub-command of main player command that will be run on first player command call.")
    @ConfigComment("'go' is the door into the ocean: it refuses if you are already at sea, and")
    @ConfigComment("returns you to the water you left rather than to spawn, so it is not a free")
    @ConfigComment("warp home. Only a sailor who has never set out starts at the spawn port.")
    @ConfigEntry(path = "tradewinds.command.new-player-action")
    private String defaultNewPlayerAction = "go";

    @ConfigComment("The default action for player command.")
    @ConfigComment("Sub-command of main player command that will be run on each player command call.")
    @ConfigEntry(path = "tradewinds.command.default-action")
    private String defaultPlayerAction = "go";

    /*      GALAXY      */
    @ConfigComment("The galaxy seed. Every island position, type, security band, biome, name and")
    @ConfigComment("route cost derives deterministically from this one number - share it and another")
    @ConfigComment("server gets the same trading galaxy. 0 means: use the world seed.")
    @ConfigEntry(path = "galaxy.seed", needsReset = true)
    private long galaxySeed = 0;

    @ConfigComment("Minimum separation between trading island centers in blocks. Enforced by")
    @ConfigComment("rejection sampling at placement; must be at least 2x distance-between-islands.")
    @ConfigEntry(path = "galaxy.min-separation", needsReset = true)
    private int galaxyMinSeparation = 2500;

    @ConfigComment("Radius in blocks around spawn inside which the starter-cluster density floor applies.")
    @ConfigEntry(path = "galaxy.starter-cluster-radius", needsReset = true)
    private int starterClusterRadius = 5000;

    @ConfigComment("Minimum number of trading islands guaranteed inside the starter cluster radius,")
    @ConfigComment("regardless of seed luck. These are pre-charted for new players.")
    @ConfigEntry(path = "galaxy.starter-cluster-min-islands", needsReset = true)
    private int starterClusterMinIslands = 5;

    @ConfigComment("Chance (0.0-1.0) that a galaxy grid cell hosts a trading island.")
    @ConfigComment("Cells are 2 x min-separation across, so 0.5 averages one island per ~2 cells.")
    @ConfigEntry(path = "galaxy.density", needsReset = true)
    private double galaxyDensity = 0.5;

    @ConfigComment("Radius in blocks of an island's terrain footprint (land plus underwater shelf).")
    @ConfigEntry(path = "galaxy.island-terrain-radius", needsReset = true)
    private int islandTerrainRadius = 160;

    @ConfigComment("Blocks of terrain lift at an island's center. With sea-floor 25 and sea-height 70,")
    @ConfigComment("45 puts mean island centers just above the waves with hills to ~30 blocks.")
    @ConfigEntry(path = "galaxy.land-lift", needsReset = true)
    private int landLift = 45;

    @ConfigComment("Distance from spawn per security-band step (Safe -> Policed -> Frontier -> Lawless -> Anarchic).")
    @ConfigEntry(path = "galaxy.band-radius", needsReset = true)
    private int bandRadius = 5000;

    @ConfigComment("Economy of the spawn island - the trading island reserved at the world origin,")
    @ConfigComment("where players spawn and respawn. A working port: dock, plaza, market, warp zone.")
    @ConfigComment("One of AGRICULTURAL, FOREST, FISHING, MINING, INDUSTRIAL, LUXURY, FROZEN,")
    @ConfigComment("or RANDOM to let the seed decide.")
    @ConfigEntry(path = "galaxy.spawn-island-type", needsReset = true)
    private String spawnIslandType = "FISHING";

    @ConfigComment("Chance (0-1) that a wild-islet grid cell hosts one - small unnamed islands,")
    @ConfigComment("unprotected: mine, farm, build, live. Minecraft-stuff land.")
    @ConfigEntry(path = "galaxy.wild-islet-chance", needsReset = true)
    private double wildIsletChance = 0.55;

    @ConfigComment("Grid size in blocks for wild islets. Much finer than the trading island grid,")
    @ConfigComment("so the open sea is dotted with land: at 900 with chance 0.55 there is usually")
    @ConfigComment("an islet within a few hundred blocks of anywhere - close enough to row to")
    @ConfigComment("without the sea feeling empty.")
    @ConfigEntry(path = "galaxy.wild-islet-grid", needsReset = true)
    private int wildIsletGrid = 900;

    @ConfigComment("Mean terrain radius of wild islets. 0 disables them. Each islet rolls its own")
    @ConfigComment("size between roughly half and one and a half times this, so the sea holds")
    @ConfigComment("everything from sandbars to proper little islands.")
    @ConfigEntry(path = "galaxy.wild-islet-radius", needsReset = true)
    private int wildIsletRadius = 75;

    @ConfigComment("Chance (0-1) that a wild islet is a mushroom island: mycelium, mooshrooms,")
    @ConfigComment("and no hostile spawns. Rare enough to be worth the find.")
    @ConfigEntry(path = "galaxy.mushroom-islet-chance", needsReset = true)
    private double mushroomIsletChance = 0.06;

    @ConfigComment("Chance (0-1) that a wild islet carries a small biome-appropriate vanilla")
    @ConfigComment("structure at its heart: an igloo on the snowfields, fossils in the desert,")
    @ConfigComment("a ruined portal in the jungle, an abandoned camp in the woods. Seeded and")
    @ConfigComment("deterministic per islet. Mushroom islets and pale gardens always stay")
    @ConfigComment("empty - there the biome itself is the find. 0 disables. Safe to change")
    @ConfigComment("mid-game: it only affects islets whose center chunk is not yet generated.")
    @ConfigEntry(path = "galaxy.islet-structure-chance")
    private double isletStructureChance = 0.25;

    @ConfigComment("How far a coastline wanders in and out from the island's nominal radius, as a")
    @ConfigComment("fraction of it: bays and headlands. 0 gives perfect circles - a radial mask")
    @ConfigComment("on its own draws a coin. Above about 0.3 coasts start breaking into fragments.")
    @ConfigEntry(path = "galaxy.coast-roughness", needsReset = true)
    private double coastRoughness = 0.25;

    @ConfigComment("How much the land height varies across an island, as a fraction of its full")
    @ConfigComment("lift: hills and hollows instead of a smooth dome. 0 gives the dome.")
    @ConfigEntry(path = "galaxy.island-hilliness", needsReset = true)
    private double islandHilliness = 0.30;

    @ConfigComment("Relative spawn weight per island type. Higher = more common; 0 disables a type.")
    @ConfigComment("Types: AGRICULTURAL, FOREST, FISHING, MINING, INDUSTRIAL, LUXURY, FROZEN.")
    @ConfigEntry(path = "galaxy.type-weights", needsReset = true)
    private Map<String, Integer> typeWeights = defaultTypeWeights();

    private static Map<String, Integer> defaultTypeWeights() {
        Map<String, Integer> map = new HashMap<>();
        for (world.bentobox.tradewinds.galaxy.IslandType t : world.bentobox.tradewinds.galaxy.IslandType.values()) {
            map.put(t.name(), t.getWeight());
        }
        return map;
    }

    /*      SECURITY BANDS      */
    @ConfigComment("Hostile mob natural spawning inside the protection range, per security band.")
    @ConfigComment("Outside the protection range (the island approaches and open ocean) mobs always spawn.")
    @ConfigEntry(path = "bands.monster-natural-spawn")
    private Map<String, Boolean> bandMonsterSpawn = defaultBandMonsterSpawn();

    private static Map<String, Boolean> defaultBandMonsterSpawn() {
        Map<String, Boolean> map = new HashMap<>();
        map.put("SAFE", false);
        map.put("POLICED", false);
        map.put("FRONTIER", true);
        map.put("LAWLESS", true);
        map.put("ANARCHIC", true);
        return map;
    }

    @ConfigComment("PvP inside island space, per security band.")
    @ConfigEntry(path = "bands.pvp")
    private Map<String, Boolean> bandPvp = defaultBandPvp();

    private static Map<String, Boolean> defaultBandPvp() {
        Map<String, Boolean> map = new HashMap<>();
        map.put("SAFE", false);
        map.put("POLICED", false);
        map.put("FRONTIER", false);
        map.put("LAWLESS", true);
        map.put("ANARCHIC", true);
        return map;
    }

    @ConfigComment("Minimum rank required to hurt villagers, per security band (0 = anyone/visitor,")
    @ConfigComment("500 = member - effectively nobody on unowned trading islands).")
    @ConfigComment("SAFE prevents it outright; lower bands allow it - and Stage 6 punishes it.")
    @ConfigEntry(path = "bands.hurt-villagers-rank")
    private Map<String, Integer> bandHurtVillagersRank = defaultBandHurtVillagers();

    private static Map<String, Integer> defaultBandHurtVillagers() {
        Map<String, Integer> map = new HashMap<>();
        map.put("SAFE", 500);
        map.put("POLICED", 0);
        map.put("FRONTIER", 0);
        map.put("LAWLESS", 0);
        map.put("ANARCHIC", 0);
        return map;
    }

    @ConfigComment("Trading islands are public markets: EVERY protection flag is allowed at visitor")
    @ConfigComment("rank except a built-in deny list (anti-grief, the island's own crops, stores and")
    @ConfigComment("livestock, and direct villager trading - which would bypass the hold).")
    @ConfigComment("BentoBox flags default to member rank and nobody is ever a member of an unowned")
    @ConfigComment("island, so allow-listing instead meant finding each missing permission in play.")
    @ConfigComment("List extra flag IDs here to deny them as well, e.g. [BED, ENDER_PEARL].")
    @ConfigEntry(path = "bands.port-denied-flags")
    private List<String> portDeniedFlags = new ArrayList<>();

    @ConfigComment("Flag IDs to force back to visitor rank, overriding the built-in deny list above.")
    @ConfigComment("Use this to open up something the deny list closes, e.g. [HARVEST].")
    @ConfigEntry(path = "bands.port-allowed-flags")
    private List<String> portAllowedFlags = new ArrayList<>();

    /*      CRIME AND REPUTATION      */
    @ConfigComment("Master switch for the whole law layer: reputation, customs scans, police and")
    @ConfigComment("bounties. False makes the world lawless in the sense of 'nothing is tracked'.")
    @ConfigEntry(path = "crime.enabled")
    private boolean crimeEnabled = true;

    @ConfigComment("The reputation number line. Positive is good; the bands hang off these.")
    @ConfigEntry(path = "crime.reputation.floor")
    private int reputationFloor = -1000;
    @ConfigEntry(path = "crime.reputation.ceiling")
    private int reputationCeiling = 1000;
    @ConfigComment("At or above this score a player is UPSTANDING: better prices, fewer scans.")
    @ConfigEntry(path = "crime.reputation.upstanding")
    private int reputationUpstanding = 250;
    @ConfigComment("Below this a player is an OFFENDER - and this is where a paid fine returns you.")
    @ConfigEntry(path = "crime.reputation.offender")
    private int reputationOffender = 0;
    @ConfigComment("At or below this a player is WANTED: police respond, and killing them is lawful.")
    @ConfigEntry(path = "crime.reputation.wanted")
    private int reputationWanted = -200;
    @ConfigComment("At or below this a player is a FUGITIVE: shot on sight, barred from safe trade.")
    @ConfigEntry(path = "crime.reputation.fugitive")
    private int reputationFugitive = -500;

    @ConfigComment("Minutes of online play per decay tick, and points moved toward zero each tick.")
    @ConfigComment("Credited only while online and in a TradeWinds world, so logging out for a week")
    @ConfigComment("does not launder a reputation. Crimes should shadow you for sessions.")
    @ConfigEntry(path = "crime.reputation.decay-minutes")
    private int reputationDecayMinutes = 15;
    @ConfigEntry(path = "crime.reputation.decay-points")
    private int reputationDecayPoints = 1;

    @ConfigComment("Currency charged per point of reputation debt when paying a fine. A fine can")
    @ConfigComment("only ever take you back to Clean - money buys you out of Wanted, not into virtue.")
    @ConfigEntry(path = "crime.fine-per-point")
    private double finePerPoint = 20.0;

    @ConfigComment("The safest band that will still trade with a FUGITIVE. Safer ports refuse them")
    @ConfigComment("outright: having burned your name, the only markets left are where the law is")
    @ConfigComment("thin. One of SAFE, POLICED, FRONTIER, LAWLESS, ANARCHIC.")
    @ConfigEntry(path = "crime.safest-fugitive-trader")
    private String safestFugitiveTrader = "FRONTIER";

    @ConfigComment("Show a player's bounty beside their name, via a scoreboard team suffix.")
    @ConfigComment("A bounty nobody can see is not a bounty - a hunter has to be able to tell at a")
    @ConfigComment("glance. Turn this OFF if the server runs TAB or another nametag plugin (they")
    @ConfigComment("will fight over the team) and render %tradewinds_bounty% there instead.")
    @ConfigEntry(path = "crime.bounty-nameplate")
    private boolean bountyNameplate = true;

    @ConfigComment("Reputation lost per crime (negative numbers).")
    @ConfigEntry(path = "crime.penalties")
    private Map<String, Integer> crimePenalties = defaultCrimePenalties();

    @ConfigComment("Money added to the offender's bounty per crime. Paid once, to whoever kills")
    @ConfigComment("them while they are a lawful target.")
    @ConfigEntry(path = "crime.bounties")
    private Map<String, Double> crimeBounties = defaultCrimeBounties();

    private static Map<String, Integer> defaultCrimePenalties() {
        Map<String, Integer> map = new HashMap<>();
        for (Crime crime : Crime.values()) {
            map.put(crime.name(), crime.getDefaultPenalty());
        }
        return map;
    }

    private static Map<String, Double> defaultCrimeBounties() {
        Map<String, Double> map = new HashMap<>();
        for (Crime crime : Crime.values()) {
            map.put(crime.name(), (double) crime.getDefaultBounty());
        }
        return map;
    }

    /**
     * The configured reputation number line.
     *
     * @return the scale
     */
    public ReputationScale reputationScale() {
        return new ReputationScale(reputationFloor, reputationCeiling, reputationUpstanding, reputationOffender,
                reputationWanted, reputationFugitive);
    }

    /**
     * Reputation cost of a crime, from config.
     *
     * @param crime the crime
     * @return points lost, negative
     */
    public int penaltyFor(Crime crime) {
        return crimePenalties.getOrDefault(crime.name(), crime.getDefaultPenalty());
    }

    /**
     * Bounty added by a crime, from config.
     *
     * @param crime the crime
     * @return money added to the bounty
     */
    public double bountyFor(Crime crime) {
        return crimeBounties.getOrDefault(crime.name(), (double) crime.getDefaultBounty());
    }

    /*      RESIDENTS      */
    @ConfigComment("Distance from the plaza center beyond which a resident is teleported home.")
    @ConfigEntry(path = "residents.tether-radius")
    private int residentTetherRadius = 24;

    @ConfigComment("Minutes before a killed resident respawns at the plaza. The market always recovers.")
    @ConfigEntry(path = "residents.respawn-delay-minutes")
    private int residentRespawnDelayMinutes = 10;

    @ConfigComment("Seconds between resident audits (tether check + respawn accounting).")
    @ConfigEntry(path = "residents.audit-period-seconds")
    private int residentAuditPeriodSeconds = 30;

    /*      CHART NAVIGATION      */
    @ConfigComment("Ring radius in blocks for /tw chart's direction holograms.")
    @ConfigEntry(path = "chart.hologram-distance")
    private double chartHologramDistance = 10.0;

    @ConfigComment("Seconds before chart holograms fade away.")
    @ConfigEntry(path = "chart.hologram-duration-seconds")
    private int chartHologramSeconds = 15;

    @ConfigComment("Maximum holograms shown at once (nearest islands first).")
    @ConfigEntry(path = "chart.hologram-max")
    private int chartHologramMax = 12;

    @ConfigComment("Islands added to a sailor's chart for free when they open the chart while at a")
    @ConfigComment("trading island - the port's own harbour charts. 8 matches the warp dialog, so")
    @ConfigComment("docking anywhere leaves with a full menu of onward routes. 0 disables.")
    @ConfigEntry(path = "chart.port-scan")
    private int portScan = 8;

    @ConfigComment("How close a sailor must pass an island to chart it, in blocks. Slightly beyond")
    @ConfigComment("island waters (distance-between-islands) so islands are charted as they are")
    @ConfigComment("sighted. Only charted islands can be warped to.")
    @ConfigEntry(path = "chart.sighting-range")
    private int chartSightingRange = 1200;

    @ConfigComment("Show the hologram chart automatically whenever a player boards a boat -")
    @ConfigComment("new players see where to go the moment they set sail, untold.")
    @ConfigEntry(path = "chart.show-on-boarding")
    private boolean chartOnBoarding = true;

    @ConfigComment("Star Chart map scale: blocks per map pixel. 64 shows ~8km across;")
    @ConfigComment("smaller values zoom in (islands render larger).")
    @ConfigEntry(path = "chart.starchart-blocks-per-pixel")
    private int starChartBlocksPerPixel = 64;

    /*      HUD      */
    @ConfigComment("Show the navigation boss bar in island waters: island name, your standing,")
    @ConfigComment("and the distance to the dock - so you can steer for it after a warp.")
    @ConfigEntry(path = "hud.navigation-bossbar")
    private boolean navigationBossbar = true;

    /*      TRAVEL      */
    @ConfigComment("Base warp fuel cost multiplier: fuel units per block of Euclidean route distance.")
    @ConfigComment("Per-edge overrides come later via the route-graph config.")
    @ConfigEntry(path = "travel.warp.fuel-per-block")
    private double fuelPerBlock = 0.01;

    @ConfigComment("Chance (0.0-1.0) that a warp fails and drops the player into the interstice.")
    @ConfigComment("Re-engaging the warp from the interstice to the original destination is always free.")
    @ConfigEntry(path = "travel.warp.failure-chance")
    private double warpFailureChance = 0.05;

    @ConfigComment("Half-width of the offer ring around an island's visible border (the protection")
    @ConfigComment("edge - the RED particle curtain): boated players crossing it get the warp")
    @ConfigComment("dialog. Small on purpose: you should have to touch the curtain. Keep a few")
    @ConfigComment("blocks of width or ice-lane boats can cross the line between ticks.")
    @ConfigEntry(path = "travel.warp.trigger-distance")
    private int warpTriggerDistance = 5;

    @ConfigComment("How far from the destination island's CENTRE a warp arrival lands.")
    @ConfigComment("This wants to be just INSIDE the warp-offer ring (protection-range minus")
    @ConfigComment("trigger-distance, with margin): landing on the ring itself reopened the warp")
    @ConfigComment("dialog the sailor had just come through. 130 put sailors inside the island's")
    @ConfigComment("own 160-block terrain footprint, on its underwater shelf: too close to feel")
    @ConfigComment("like a voyage, and too close for a customs patrol to have anywhere to come")
    @ConfigComment("from. 400 sat exactly on the offer ring.")
    @ConfigEntry(path = "travel.warp.arrival-distance")
    private int warpArrivalDistance = 320;

    @ConfigComment("Seconds between automatic warp-dialog offers at the same island's border.")
    @ConfigEntry(path = "travel.warp.prompt-cooldown-seconds")
    private int warpPromptCooldownSeconds = 30;

    @ConfigComment("Maximum destinations listed in the warp dialog (nearest first).")
    @ConfigComment("8 fits the dialog without scrolling - scrolling is easy to miss.")
    @ConfigEntry(path = "travel.warp.max-destinations")
    private int maxWarpDestinations = 8;

    @ConfigComment("Seconds a player must hold still before an engaged warp fires (moving cancels it,")
    @ConfigComment("fuel refunded). Stops the warp being a panic button in a fight or a customs chase.")
    @ConfigComment("0 = instant. Ops and holders of tradewinds.mod.bypassdelays always warp instantly.")
    @ConfigEntry(path = "travel.warp.stand-still-seconds")
    private int warpStandStillSeconds = 0;

    @ConfigComment("Hostile mobs within this many blocks stop a warp engaging, the way monsters")
    @ConfigComment("stop you sleeping in a bed. A warp is not a panic button out of a fight or a")
    @ConfigComment("customs chase - run, fight, or jettison. 0 disables the check.")
    @ConfigComment("The interstice re-engage is always exempt: it is the way OUT of somewhere")
    @ConfigComment("dangerous, and gating it could strand a player for good.")
    @ConfigEntry(path = "travel.warp.enemy-radius")
    private double warpEnemyRadius = 12.0;

    @ConfigComment("Seconds of nausea after a warp. Warping hurts - it gates the under-equipped.")
    @ConfigEntry(path = "travel.warp.nausea-seconds")
    private int warpNauseaSeconds = 8;

    @ConfigComment("Seconds of blindness after a warp.")
    @ConfigEntry(path = "travel.warp.blindness-seconds")
    private int warpBlindnessSeconds = 3;

    @ConfigComment("Damage (in half-hearts) taken on warp arrival. 0 disables.")
    @ConfigEntry(path = "travel.warp.damage")
    private double warpDamage = 2.0;

    @ConfigComment("Warn a sailor standing at a port when they cannot afford to warp anywhere from")
    @ConfigComment("it. Running dry at a port is not a soft failure - the only way onward is rowing -")
    @ConfigComment("and it is only fixable while they are still standing next to the fuel.")
    @ConfigEntry(path = "travel.fuel-warning.enabled")
    private boolean fuelWarningEnabled = true;

    @ConfigComment("Seconds between repeats of the low-fuel action bar while ashore. It repeats")
    @ConfigComment("because an action bar fades and a one-shot is missed by exactly the players this")
    @ConfigComment("is for; the chat line and the highlighted shop button are the other two tellings.")
    @ConfigEntry(path = "travel.fuel-warning.repeat-seconds")
    private int fuelWarningSeconds = 8;

    @ConfigComment("Warn when fuel is below this multiple of the cheapest route out. 1.0 warns only")
    @ConfigComment("when genuinely stuck; raise it to warn with something still in reserve.")
    @ConfigEntry(path = "travel.fuel-warning.margin")
    private double fuelWarningMargin = 1.0;

    @ConfigComment("Fuel unit value per material. Fuel is consumed from the hold only:")
    @ConfigComment("the chest boat's inventory and trading bundles - never loose pockets.")
    @ConfigComment("Lava buckets leave their empty bucket behind. Non-stackable but potent: deliberate tension.")
    @ConfigEntry(path = "travel.fuel-values")
    private Map<String, Double> fuelValues = defaultFuelValues();

    private static Map<String, Double> defaultFuelValues() {
        Map<String, Double> map = new HashMap<>();
        for (String log : List.of("OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG", "ACACIA_LOG", "DARK_OAK_LOG",
                "MANGROVE_LOG", "CHERRY_LOG")) {
            map.put(log, 1.0);
        }
        map.put("COAL", 8.0);
        map.put("CHARCOAL", 8.0);
        map.put("COAL_BLOCK", 80.0);
        map.put("BLAZE_ROD", 12.0);
        map.put("DRIED_KELP_BLOCK", 4.0);
        map.put("LAVA_BUCKET", 100.0);
        return map;
    }

    @ConfigComment("Per-edge fuel cost overrides, replacing the distance-based cost in both directions.")
    @ConfigComment("Key: the two island cells, smaller first, e.g. '0,0>1,2'. Value: absolute fuel units.")
    @ConfigComment("Cheap lanes and expensive frontiers without regenerating the world.")
    @ConfigEntry(path = "travel.warp.edge-overrides")
    private Map<String, Double> edgeOverrides = new HashMap<>();

    /*      ECONOMY      */
    @ConfigComment("Money given to brand-new players with their starter kit.")
    @ConfigEntry(path = "economy.starting-balance")
    private double startingBalance = 2500.0;

    @ConfigComment("Coal tucked into the starter bundle - enough fuel that the first island hop")
    @ConfigComment("can be a warp instead of a seven-minute row. 0 disables.")
    @ConfigEntry(path = "economy.starter-coal")
    private int starterCoal = 8;

    @ConfigComment("Multiplier on what players PAY an island (buying).")
    @ConfigEntry(path = "economy.buy-spread")
    private double buySpread = 1.15;

    @ConfigComment("Multiplier on what an island PAYS players (selling). Together with buy-spread")
    @ConfigComment("this makes a same-island round trip always lose money.")
    @ConfigEntry(path = "economy.sell-spread")
    private double sellSpread = 0.85;

    @ConfigComment("Price multiplier for categories an island produces - its own goods are cheap.")
    @ConfigEntry(path = "economy.produce-factor")
    private double produceFactor = 0.6;

    @ConfigComment("Price multiplier for categories an island demands - it pays over the odds.")
    @ConfigEntry(path = "economy.demand-factor")
    private double demandFactor = 1.4;

    @ConfigComment("Extra demand multiplier per security band step - margins scale with danger:")
    @ConfigComment("an ANARCHIC island pays (1 + 4 x this) times more on demanded goods.")
    @ConfigEntry(path = "economy.band-demand-bonus")
    private double bandDemandBonus = 0.125;

    @ConfigComment("Price tilt per tech-level step away from 4 (the neutral level): high-tech")
    @ConfigComment("islands sell FINISHED goods (metals, food) cheaper and pay more for RAW")
    @ConfigComment("goods (ores, crops, wood, fish, stone) by this fraction per step; low-tech")
    @ConfigComment("islands the inverse. The best routes are tech differentials. 0 disables.")
    @ConfigEntry(path = "economy.tech-price-step")
    private double techPriceStep = 0.03;

    @ConfigComment("The boat ladder: cargo slots per boat material. A player has exactly ONE")
    @ConfigComment("boat (or none) and this is the ONLY thing that sizes their hold. Order is")
    @ConfigComment("by slots; shops list only boats bigger than yours, up to the island's tech")
    @ConfigComment("(rank <= tech level x ranks-per-tech-level). Crafting ignores tech gates.")
    @ConfigEntry(path = "boats.ranks")
    private Map<String, Integer> boatRanks = defaultBoatRanks();

    @ConfigComment("Boat shop price = this x slots squared. Bamboo Raft 100, Pale Oak Chest 11025.")
    @ConfigEntry(path = "boats.price-per-slot-squared")
    private double boatPricePerSlotSquared = 250.0;

    @ConfigComment("Boat ranks sold at an island = its tech level x this (TL7 sells all 20).")
    @ConfigEntry(path = "boats.ranks-per-tech-level")
    private int boatRanksPerTechLevel = 3;

    @ConfigComment("Show island boundaries as particle curtains: the warp-offer ring at the")
    @ConfigComment("protection edge, and the edge of island space. Paint, not a wall - both")
    @ConfigComment("stay fully passable.")
    @ConfigEntry(path = "border.particles-enabled")
    private boolean borderParticlesEnabled = true;

    @ConfigComment("Blocks from a boundary line within which its curtain renders.")
    @ConfigEntry(path = "border.view-distance")
    private int borderViewDistance = 32;

    @ConfigComment("Dust color 'r,g,b' (0-255) of the warp-offer ring - where the warp dialog")
    @ConfigComment("fires, at the island's protection edge. Default red.")
    @ConfigEntry(path = "border.warp-ring-color")
    private String warpRingColor = "255,64,64";

    @ConfigComment("Dust color 'r,g,b' (0-255) of the island-space edge - where scans and")
    @ConfigComment("island rules begin. Default blue.")
    @ConfigEntry(path = "border.edge-color")
    private String edgeRingColor = "64,128,255";

    @ConfigComment("How close your own boat must be for you to move another hull's cargo into")
    @ConfigComment("it. Beyond this the option is not offered at all - cargo is not teleported")
    @ConfigComment("across the ocean; you may still take the boat itself.")
    @ConfigEntry(path = "boats.cargo-transfer-range")
    private int cargoTransferRange = 200;

    @ConfigComment("The boat a boatless player is lent when they respawn (their own boat")
    @ConfigComment("dropped where they died - rowing back out to reclaim it is the recovery")
    @ConfigComment("trip). A boat-rank material name; NONE disables the loaner.")
    @ConfigEntry(path = "boats.respawn-boat")
    private String respawnBoat = "BAMBOO_RAFT";

    @ConfigComment("Minutes a dropped boat item (and the cargo riding in its record) survives")
    @ConfigComment("before the sea claims it. Persisted in the database - restarts do not")
    @ConfigComment("reset the clock. The TTL pauses while a mob is holding the item.")
    @ConfigEntry(path = "boats.dropped-boat-ttl-minutes")
    private int droppedBoatTtlMinutes = 30;

    private static Map<String, Integer> defaultBoatRanks() {
        Map<String, Integer> ranks = new java.util.LinkedHashMap<>();
        ranks.put("BAMBOO_RAFT", 2);
        ranks.put("OAK_BOAT", 3);
        ranks.put("SPRUCE_BOAT", 4);
        ranks.put("BAMBOO_CHEST_RAFT", 5);
        ranks.put("BIRCH_BOAT", 6);
        ranks.put("OAK_CHEST_BOAT", 7);
        ranks.put("JUNGLE_BOAT", 8);
        ranks.put("SPRUCE_CHEST_BOAT", 9);
        ranks.put("ACACIA_BOAT", 10);
        ranks.put("BIRCH_CHEST_BOAT", 11);
        ranks.put("DARK_OAK_BOAT", 12);
        ranks.put("JUNGLE_CHEST_BOAT", 13);
        ranks.put("MANGROVE_BOAT", 14);
        ranks.put("ACACIA_CHEST_BOAT", 15);
        ranks.put("CHERRY_BOAT", 16);
        ranks.put("DARK_OAK_CHEST_BOAT", 17);
        ranks.put("PALE_OAK_BOAT", 18);
        ranks.put("MANGROVE_CHEST_BOAT", 19);
        ranks.put("CHERRY_CHEST_BOAT", 20);
        ranks.put("PALE_OAK_CHEST_BOAT", 21);
        return ranks;
    }

    @ConfigComment("COINS of stock for a full price swing - the size of a trader's purse.")
    @ConfigComment("A port's capacity is its capacity to SPEND, not to count crates: ten")
    @ConfigComment("diamonds are far more business than ten wheat, so drift is valued in money.")
    @ConfigComment("Combined with drift-min below, a port saturates after absorbing")
    @ConfigComment("(1 - drift-min) x this many coins of one category - then sail on.")
    @ConfigEntry(path = "economy.drift-value-scale")
    private int driftValueScale = 30000;

    @ConfigComment("Lower clamp of the stock drift price factor.")
    @ConfigEntry(path = "economy.drift-min")
    private double driftMin = 0.7;

    @ConfigComment("Upper clamp of the stock drift price factor.")
    @ConfigEntry(path = "economy.drift-max")
    private double driftMax = 1.3;

    @ConfigComment("Coins of stock that decay back toward equilibrium per hour - markets recover")
    @ConfigComment("as the trader works through the inventory. A fully saturated category")
    @ConfigComment("recovers in about (1 - drift-min) x drift-value-scale / this many hours.")
    @ConfigEntry(path = "economy.stock-decay-value-per-hour")
    private int stockDecayValuePerHour = 3000;

    @ConfigComment("Base price of the first cargo expander. Each further one costs double.")
    @ConfigEntry(path = "economy.expander-base-price")
    private double expanderBasePrice = 50000.0;

    @ConfigComment("The harbourmaster's charity: a destitute sailor - no boat, and too little")
    @ConfigComment("money to buy even a bamboo raft - is granted one, so the economy is never")
    @ConfigComment("fully closed to them. 0 disables it.")
    @ConfigEntry(path = "economy.charity-cooldown-minutes")
    private int charityCooldownMinutes = 15;

    @ConfigComment("The contraband list: what customs care about, and what black markets pay")
    @ConfigComment("the premium for. Only honored while illegal-trade.enabled is true.")
    @ConfigEntry(path = "illegal-trade.contraband-materials")
    private List<String> contrabandMaterials = new ArrayList<>(List.of("SUGAR"));

    @ConfigComment("Career restarts a destitute player may use (/tw restart): fresh kit, starting")
    @ConfigComment("balance, chart kept. -1 = unlimited, 0 = none.")
    @ConfigEntry(path = "player.max-restarts")
    private int maxRestarts = 3;

    @ConfigComment("The secondhand shelf: notable goods sold to a trader go back out for sale")
    @ConfigComment("instead of vanishing, so the world feels inhabited - 'someone dumped a Silk")
    @ConfigComment("Touch pick at Baker's Reach'. They surface at a DIFFERENT port from the one")
    @ConfigComment("they were sold at (the trader shipped it on), which is what stops shelves")
    @ConfigComment("becoming an alt-account laundering channel: you cannot predict where your own")
    @ConfigComment("goods reappear. false disables resale entirely.")
    @ConfigEntry(path = "economy.resale-enabled")
    private boolean resaleEnabled = true;

    @ConfigComment("Only NOTABLE goods resurface: enchanted, renamed, or worth at least this much.")
    @ConfigComment("Ordinary cargo is not interesting to find and would only clutter the shelves.")
    @ConfigEntry(path = "economy.resale-notable-value")
    private double resaleNotableValue = 500.0;

    @ConfigComment("Listings a port may carry at once; the oldest makes way for a new arrival.")
    @ConfigEntry(path = "economy.resale-slots")
    private int resaleSlots = 2;

    @ConfigComment("How long a listing lasts, in hours. 0 for never expiring.")
    @ConfigEntry(path = "economy.resale-ttl-hours")
    private int resaleTtlHours = 72;

    @ConfigComment("What the trader adds on top of book price to resell. Must be above 1 or the")
    @ConfigComment("shelf becomes a way to buy back your own goods at a profit.")
    @ConfigEntry(path = "economy.resale-markup")
    private double resaleMarkup = 1.6;

    @ConfigComment("How far a trader will ship a consignment to put it on someone else's shelf.")
    @ConfigEntry(path = "economy.resale-ship-radius")
    private int resaleShipRadius = 8000;

    @ConfigComment("Harbour reports: what a broker charges PER PORT to fill in your logbook, and")
    @ConfigComment("how far the report reaches per tech level of the island selling it. This gives")
    @ConfigComment("a developed port a role beyond its shelves, and a reason to call somewhere you")
    @ConfigComment("are not trading with. 0 for either disables reports.")
    @ConfigEntry(path = "economy.market-report-price-per-island")
    private double marketReportPricePerIsland = 250.0;

    @ConfigEntry(path = "economy.market-report-radius-per-tech-level")
    private double marketReportRadiusPerTechLevel = 1500.0;

    @ConfigComment("Enchantment premium: an item's price is multiplied by (1 + quality x this),")
    @ConfigComment("where quality weights each enchantment by usefulness and level. 0 ignores")
    @ConfigComment("enchantments, so a Silk Touch pick sells as a plain one.")
    @ConfigEntry(path = "economy.enchantment-price-factor")
    private double enchantmentPriceFactor = 0.05;

    @ConfigComment("Coins added per potion effect, times (1 + amplifier). Every potion shares one")
    @ConfigComment("Material, so without this a Potion of Strength II is priced as a water bottle.")
    @ConfigEntry(path = "economy.potion-effect-price")
    private double potionEffectPrice = 150.0;

    @ConfigComment("How much value a port will handle in a SINGLE salvage item, per tech level:")
    @ConfigComment("a TL1 fishing hamlet has no use for a diamond sword and nobody there could")
    @ConfigComment("pay for one, while a TL7 industrial hub will take anything. This is what gives")
    @ConfigComment("loot a DESTINATION - and a destination is a voyage. Recognised trade goods are")
    @ConfigComment("exempt: a port always deals in what its own shelves stock, whatever its tech.")
    @ConfigComment("0 disables the gate.")
    @ConfigEntry(path = "economy.salvage-value-per-tech-level")
    private double salvageValuePerTechLevel = 1500.0;

    @ConfigComment("What a port pays for SALVAGE - anything that is not a recognised trade good")
    @ConfigComment("on some island's shelves: mob drops, worn gear, odd blocks. A fraction of book")
    @ConfigComment("price. Keep this well under 1: if scavenging and piracy pay as well as trading,")
    @ConfigComment("they replace it rather than feeding it. Salvage also drifts against its own")
    @ConfigComment("stock pool, so dumping junk cannot crater a port's legitimate cargo prices.")
    @ConfigEntry(path = "economy.salvage-discount")
    private double salvageDiscount = 0.375;

    @ConfigComment("Base prices (Material -> price). Anything not listed is priced by deriving")
    @ConfigComment("from its crafting recipe (BlueBook logic, embedded); underivable = untradeable.")
    @ConfigEntry(path = "economy.base-prices")
    private Map<String, Double> basePrices = defaultBasePrices();

    private static Map<String, Double> defaultBasePrices() {
        Map<String, Double> map = new HashMap<>();
        map.put("WHEAT", 20.0); map.put("CARROT", 15.0); map.put("POTATO", 15.0); map.put("BEETROOT", 15.0);
        map.put("SUGAR_CANE", 10.0); map.put("SUGAR", 15.0); map.put("PUMPKIN", 30.0); map.put("MELON_SLICE", 5.0);
        map.put("BREAD", 30.0); map.put("COOKED_BEEF", 40.0); map.put("COOKED_COD", 30.0); map.put("CAKE", 200.0);
        map.put("GOLDEN_APPLE", 1500.0); map.put("EGG", 10.0); map.put("HAY_BLOCK", 180.0);
        map.put("COD", 20.0); map.put("SALMON", 30.0); map.put("TROPICAL_FISH", 50.0); map.put("PUFFERFISH", 40.0);
        map.put("KELP", 3.0);
        map.put("OAK_LOG", 15.0); map.put("SPRUCE_LOG", 15.0); map.put("BIRCH_LOG", 15.0); map.put("DARK_OAK_LOG", 15.0);
        map.put("ACACIA_LOG", 15.0); map.put("JUNGLE_LOG", 15.0); map.put("CHERRY_LOG", 20.0);
        map.put("STONE", 5.0); map.put("COBBLESTONE", 3.0); map.put("GRANITE", 4.0); map.put("DIORITE", 4.0);
        map.put("ANDESITE", 4.0); map.put("DEEPSLATE", 6.0); map.put("SAND", 3.0); map.put("GRAVEL", 3.0);
        map.put("COAL", 40.0); map.put("CHARCOAL", 30.0); map.put("RAW_IRON", 60.0); map.put("RAW_COPPER", 30.0);
        map.put("RAW_GOLD", 120.0); map.put("FLINT", 10.0);
        map.put("IRON_INGOT", 90.0); map.put("COPPER_INGOT", 40.0); map.put("GOLD_INGOT", 180.0);
        map.put("IRON_NUGGET", 10.0); map.put("GOLD_NUGGET", 20.0);
        map.put("DIAMOND", 1000.0); map.put("EMERALD", 600.0); map.put("AMETHYST_SHARD", 100.0);
        map.put("QUARTZ", 80.0); map.put("LAPIS_LAZULI", 60.0); map.put("REDSTONE", 30.0);
        map.put("LEATHER", 40.0); map.put("WHITE_WOOL", 20.0); map.put("STRING", 15.0);
        map.put("BEEF", 25.0); map.put("PORKCHOP", 25.0); map.put("CHICKEN", 20.0); map.put("MUTTON", 20.0);
        // Natural drops: no recipe exists for any of these, so the engine can
        // never derive them however deep it recurses - they have to be stated or
        // a scavenger's whole haul is unsellable. This is the salvage economy's
        // raw material (Stage 7.5 Phase 2).
        map.put("BONE", 20.0); map.put("BONE_MEAL", 8.0); map.put("GUNPOWDER", 50.0);
        map.put("SPIDER_EYE", 30.0); map.put("ROTTEN_FLESH", 5.0); map.put("FEATHER", 15.0);
        map.put("INK_SAC", 25.0); map.put("GLOW_INK_SAC", 90.0); map.put("SLIME_BALL", 40.0);
        map.put("RABBIT", 20.0); map.put("RABBIT_HIDE", 15.0); map.put("RABBIT_FOOT", 150.0);
        map.put("ENDER_PEARL", 250.0); map.put("BLAZE_ROD", 200.0); map.put("GHAST_TEAR", 400.0);
        map.put("PHANTOM_MEMBRANE", 180.0); map.put("NETHER_STAR", 5000.0);
        map.put("PRISMARINE_SHARD", 60.0); map.put("PRISMARINE_CRYSTALS", 90.0);
        map.put("NAUTILUS_SHELL", 300.0); map.put("HEART_OF_THE_SEA", 2500.0);
        map.put("SHULKER_SHELL", 800.0); map.put("ECHO_SHARD", 600.0);
        map.put("TOTEM_OF_UNDYING", 4000.0); map.put("DRAGON_BREATH", 500.0);
        map.put("TURTLE_SCUTE", 120.0); map.put("ARMADILLO_SCUTE", 60.0); map.put("HONEYCOMB", 40.0);
        map.put("SADDLE", 300.0); map.put("NAME_TAG", 400.0);
        // Potions: the bottle is nearly worthless; what is IN it is priced by
        // effect (see economy.potion-effect-price)
        map.put("POTION", 60.0); map.put("SPLASH_POTION", 80.0); map.put("LINGERING_POTION", 120.0);
        // Foraged and gathered: apple and melon drop from leaves and vines, the
        // rest are picked, dug or fished up
        map.put("APPLE", 25.0); map.put("MELON", 35.0); map.put("SWEET_BERRIES", 10.0);
        map.put("GLOW_BERRIES", 30.0); map.put("COCOA_BEANS", 20.0); map.put("BAMBOO", 5.0);
        map.put("CACTUS", 10.0); map.put("BROWN_MUSHROOM", 15.0); map.put("RED_MUSHROOM", 15.0);
        map.put("SEA_PICKLE", 25.0); map.put("SPONGE", 200.0); map.put("NETHER_WART", 30.0);
        map.put("CLAY_BALL", 8.0); map.put("SNOWBALL", 3.0); map.put("DIRT", 2.0);
        map.put("OBSIDIAN", 80.0); map.put("NETHERRACK", 3.0); map.put("SOUL_SAND", 15.0);
        map.put("END_STONE", 20.0); map.put("GLOWSTONE_DUST", 40.0); map.put("MAGMA_CREAM", 90.0);
        // Hulls: priced above raw plank cost - shipwright labor. Frugal
        // players craft their own from wild-islet timber.
        map.put("OAK_BOAT", 200.0);
        map.put("OAK_CHEST_BOAT", 600.0);
        return map;
    }

    /*      INTERSTICE      */
    @ConfigComment("Chance (0-1) that anything is waiting when a warp fails. Below 1 some failures")
    @ConfigComment("are just dark water and a long silence, which is unsettling in its own right -")
    @ConfigComment("and means a failed warp is not automatically a fight.")
    @ConfigEntry(path = "interstice.ghast-chance")
    private double intersticeGhastChance = 0.6;

    @ConfigComment("How far away ghasts appear, in blocks - close enough to see and identify, far")
    @ConfigComment("enough to decide about. They arrive without a target and acquire one only when")
    @ConfigComment("the arrival grace runs out, so a castaway can always leave before it starts.")
    @ConfigComment("Keep it well under the server's monster entity-tracking-range (48 on a default")
    @ConfigComment("spigot.yml) or the client is never sent them at all: the first cut used 90 and")
    @ConfigComment("the ghasts were real, present, and completely invisible.")
    @ConfigEntry(path = "interstice.ghast-distance")
    private double intersticeGhastDistance = 28;

    @ConfigComment("Seconds after a failed warp during which nothing in the interstice may target")
    @ConfigComment("or hurt the player - long enough to read the dialog and take the free way out.")
    @ConfigEntry(path = "interstice.grace-seconds")
    private int intersticeGraceSeconds = 20;

    @ConfigComment("Give the interstice a ceiling this many blocks above its sea. A lid makes the")
    @ConfigComment("place feel like somewhere you are trapped rather than an empty void, and stops")
    @ConfigComment("anything escaping upward. 0 leaves it open.")
    @ConfigEntry(path = "interstice.ceiling-height", needsReset = true)
    private int intersticeCeilingHeight = 48;

    @ConfigComment("Chance (0-1) per chunk of a burning netherrack brazier rising out of the")
    @ConfigComment("interstice sea. They are the only light out there - without them it is a flat")
    @ConfigComment("black nothing, and a player cannot see what is coming.")
    @ConfigEntry(path = "interstice.brazier-chance", needsReset = true)
    private double intersticeBrazierChance = 0.18;

    @ConfigComment("Ghasts spawned around a stranded sailor in the interstice.")
    @ConfigEntry(path = "interstice.ghasts-min")
    private int intersticeGhastsMin = 1;

    @ConfigEntry(path = "interstice.ghasts-max")
    private int intersticeGhastsMax = 3;

    @ConfigComment("Seconds between offers of the free re-engage while adrift in the interstice.")
    @ConfigEntry(path = "interstice.prompt-seconds")
    private int intersticePromptSeconds = 20;

    /*      SEA ENCOUNTERS      */
    @ConfigComment("Random mob encounters at sea - the risk that balances free rowing against")
    @ConfigComment("the warp's fuel cost and interstice risk.")
    @ConfigEntry(path = "encounters.enabled")
    private boolean encountersEnabled = true;

    @ConfigComment("Seconds between encounter rolls per player at sea.")
    @ConfigEntry(path = "encounters.check-seconds")
    private int encounterCheckSeconds = 45;

    @ConfigComment("Blocks ahead of the sailor that an encounter appears - far enough to see")
    @ConfigComment("and flee from. Fleeing should always be a real option.")
    @ConfigEntry(path = "encounters.distance")
    private int encounterDistance = 28;

    @ConfigComment("Chance per roll of an encounter, by the security band of the nearest island")
    @ConfigComment("(ANARCHIC also governs the deep ocean far from anywhere). Scaled by distance")
    @ConfigComment("from that island: quiet by the docks, dangerous in open water.")
    @ConfigEntry(path = "encounters.chance")
    private Map<String, Double> encounterChance = defaultEncounterChance();

    private static Map<String, Double> defaultEncounterChance() {
        Map<String, Double> map = new HashMap<>();
        map.put("SAFE", 0.02);
        map.put("POLICED", 0.06);
        map.put("FRONTIER", 0.12);
        map.put("LAWLESS", 0.20);
        map.put("ANARCHIC", 0.28);
        return map;
    }

    @ConfigComment("Chance a spawned drowned carries a trident.")
    @ConfigEntry(path = "encounters.drowned-trident-chance")
    private double drownedTridentChance = 0.35;

    @ConfigComment("Chance that a slain encounter mob yields booty.")
    @ConfigEntry(path = "encounters.booty-chance")
    private double bootyChance = 0.5;

    @ConfigComment("Booty materials. Dropped customs-stamped, so salvage can be sold -")
    @ConfigComment("fighting is the third way to earn, beside trading and smuggling.")
    @ConfigEntry(path = "encounters.booty-table")
    private List<String> bootyTable = new ArrayList<>(List.of("NAUTILUS_SHELL", "PRISMARINE_SHARD",
            "PRISMARINE_CRYSTALS", "GOLD_INGOT", "IRON_INGOT", "EMERALD", "COAL", "COOKED_COD", "TRIDENT"));

    /*      ILLEGAL TRADE      */
    @ConfigComment("Master gate for all illegal-goods mechanics: contraband, customs scans, smuggling.")
    @ConfigComment("Set false for family-friendly servers - removes the entire crime layer cleanly.")
    @ConfigEntry(path = "illegal-trade.enabled")
    private boolean illegalTradeEnabled = true;

    @ConfigComment("The safest security band that will buy contraband at all. Safer ports refuse it,")
    @ConfigComment("which is what pushes smuggling runs outward - crime pays, into danger.")
    @ConfigComment("One of SAFE, POLICED, FRONTIER, LAWLESS, ANARCHIC.")
    @ConfigEntry(path = "illegal-trade.safest-contraband-buyer")
    private String safestContrabandBuyer = "FRONTIER";

    @ConfigComment("What a black market pays for contraband, as a multiplier on its ordinary price.")
    @ConfigComment("This is the reward half of high-risk/high-reward, and the main lever on how")
    @ConfigComment("profitable smuggling is. Contraband is priced from its crafting recipe like")
    @ConfigComment("everything else, and sugar's recipe price is about one unit - so without this")
    @ConfigComment("a smuggler runs a customs patrol and is offered a dollar for the cargo.")
    @ConfigComment("Ports that deal in it always want it, so the band demand bonus applies too:")
    @ConfigComment("the rougher the port, the better it pays. Raise with care - contraband is")
    @ConfigComment("farmable, and this is the one price in the game that is not paid for by a")
    @ConfigComment("purchase somewhere else.")
    @ConfigEntry(path = "illegal-trade.contraband-price-multiplier")
    private double contrabandPriceMultiplier = 8.0;

    @ConfigComment("Chance (0-1) that entering an island's space triggers a customs scan, per band.")
    @ConfigComment("Safe space searches everyone; out in the lawless bands nobody is looking.")
    @ConfigEntry(path = "illegal-trade.scan-chance")
    private Map<String, Double> scanChance = defaultScanChance();

    @ConfigComment("Scan chance multipliers by standing: a clean name is worth something at the")
    @ConfigComment("border, and a known offender is searched harder.")
    @ConfigEntry(path = "illegal-trade.scan-upstanding-factor")
    private double scanUpstandingFactor = 0.4;
    @ConfigEntry(path = "illegal-trade.scan-offender-factor")
    private double scanOffenderFactor = 1.6;

    @ConfigComment("Minutes before the same island will scan the same player again. Without this a")
    @ConfigComment("smuggler could bounce across the border re-rolling until they got a pass.")
    @ConfigEntry(path = "illegal-trade.scan-cooldown-minutes")
    private int scanCooldownMinutes = 10;

    @ConfigComment("Minutes an island remembers a smuggler who ran from its patrol. While flagged,")
    @ConfigComment("re-entering that island's space skips the roll: the patrol simply launches.")
    @ConfigEntry(path = "illegal-trade.flee-flag-minutes")
    private int fleeFlagMinutes = 20;

    @ConfigComment("Customs patrol size per security band. 0 means that band has nobody to send.")
    @ConfigEntry(path = "illegal-trade.patrol-size")
    private Map<String, Integer> patrolSize = defaultPatrolSize();

    @ConfigComment("The furthest a customs patrol may be from the smuggler when it launches. They")
    @ConfigComment("set out from the island's pier, but a patrol beyond the server's simulation")
    @ConfigComment("distance (10 chunks = 160 blocks by default) never ticks - it does not swim,")
    @ConfigComment("does not chase, and does nothing at all - so the launch point is pulled along")
    @ConfigComment("the line toward the smuggler until it is close enough to be alive. Keep it")
    @ConfigComment("under the monster entity-tracking-range too, or it is invisible as well.")
    @ConfigEntry(path = "illegal-trade.patrol-distance")
    private double patrolDistance = 80.0;

    @ConfigComment("How close a patrol unit must get to make the arrest, in blocks.")
    @ConfigEntry(path = "illegal-trade.caught-radius")
    private double caughtRadius = 4.0;

    @ConfigComment("How far beyond the island's protection range a smuggler must get to shake the")
    @ConfigComment("chase, and the longest a chase can run before they are counted as away.")
    @ConfigEntry(path = "illegal-trade.chase-break-off-distance")
    private int chaseBreakOffDistance = 400;
    @ConfigEntry(path = "illegal-trade.chase-seconds")
    private int chaseSeconds = 120;

    @ConfigComment("Fine per contraband item seized when caught, on top of losing the cargo and")
    @ConfigComment("the reputation. A player who cannot cover it pays what they have.")
    @ConfigEntry(path = "illegal-trade.smuggling-fine-per-item")
    private double smugglingFinePerItem = 50.0;

    @ConfigComment("Tell players when a scan finds nothing. On by default: being waved through is")
    @ConfigComment("how a player learns the mechanic exists before it costs them anything.")
    @ConfigEntry(path = "illegal-trade.announce-clean-scans")
    private boolean announceCleanScans = true;

    private static Map<String, Double> defaultScanChance() {
        Map<String, Double> map = new HashMap<>();
        map.put("SAFE", 0.9);
        map.put("POLICED", 0.6);
        map.put("FRONTIER", 0.3);
        map.put("LAWLESS", 0.1);
        map.put("ANARCHIC", 0.0);
        return map;
    }

    private static Map<String, Integer> defaultPatrolSize() {
        Map<String, Integer> map = new HashMap<>();
        map.put("SAFE", 4);
        map.put("POLICED", 3);
        map.put("FRONTIER", 2);
        map.put("LAWLESS", 1);
        map.put("ANARCHIC", 0);
        return map;
    }

    /**
     * The safest band that still buys contraband, parsed from config.
     *
     * @return the band
     */
    public world.bentobox.tradewinds.galaxy.SecurityBand safestContrabandBuyer() {
        try {
            return world.bentobox.tradewinds.galaxy.SecurityBand
                    .valueOf(safestContrabandBuyer.toUpperCase(java.util.Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            return world.bentobox.tradewinds.galaxy.SecurityBand.FRONTIER;
        }
    }

    /*      DEBUG       */
    @ConfigComment("Log detailed [TradeWinds DEBUG] lines around galaxy generation, travel and trade.")
    @ConfigEntry(path = "debug")
    private boolean debug = false;

    /*      WORLD       */
    @ConfigComment("Friendly name for this world. Used in admin commands. Must be a single word")
    @ConfigEntry(path = "world.friendly-name")
    private String friendlyName = "TradeWinds";

    @ConfigComment("Name of the world - if it does not exist then it will be generated.")
    @ConfigComment("It acts like a prefix for nether and end (e.g. oneblock_world, oneblock_world_nether, oneblock_world_end)")
    @ConfigEntry(path = "world.world-name")
    private String worldName = "tradewinds_world";

    @ConfigComment("World difficulty setting - PEACEFUL, EASY, NORMAL, HARD")
    @ConfigComment("Other plugins may override this setting")
    @ConfigEntry(path = "world.difficulty")
    private Difficulty difficulty = Difficulty.NORMAL;
    


    











    @ConfigComment("Spawn limits. These override the limits set in bukkit.yml")
    @ConfigComment("If set to a negative number, the server defaults will be used")
    @ConfigEntry(path = "world.spawn-limits.monsters") // , since = "1.11.2")
    private int spawnLimitMonsters = -1;
    @ConfigEntry(path = "world.spawn-limits.animals") // , since = "1.11.2")
    private int spawnLimitAnimals = -1;
    @ConfigEntry(path = "world.spawn-limits.water-animals") // , since = "1.11.2")
    private int spawnLimitWaterAnimals = -1;
    @ConfigEntry(path = "world.spawn-limits.ambient") // , since = "1.11.2")
    private int spawnLimitAmbient = -1;
    @ConfigComment("Setting to 0 will disable animal spawns, but this is not recommended. Minecraft default is 400.")
    @ConfigComment("A negative value uses the server default")
    @ConfigEntry(path = "world.spawn-limits.ticks-per-animal-spawns") // , since = "1.11.2")
    private int ticksPerAnimalSpawns = -1;
    @ConfigComment("Setting to 0 will disable monster spawns, but this is not recommended. Minecraft default is 400.")
    @ConfigComment("A negative value uses the server default")
    @ConfigEntry(path = "world.spawn-limits.ticks-per-monster-spawns") // , since = "1.11.2")
    private int ticksPerMonsterSpawns = -1;

    @ConfigComment("Radius of island in blocks. (So distance between islands is twice this)")
    @ConfigComment("It is the same for every dimension : Overworld, Nether and End.")
    @ConfigComment("This value cannot be changed mid-game and the plugin will not start if it is different.")
    @ConfigEntry(path = "world.distance-between-islands", needsReset = true)
    private int islandDistance = 1000;

    @ConfigComment("Default protection range radius in blocks. Cannot be larger than distance.")
    @ConfigComment("Admins can change protection sizes for players individually using /obadmin range set <player> <new range>")
    @ConfigComment("or set this permission: aoneblock.island.range.<number>")
    @ConfigEntry(path = "world.protection-range")
    private int islandProtectionRange = 400;

    @ConfigComment("Start islands at these coordinates. This is where new islands will start in the")
    @ConfigComment("world. These must be a factor of your island distance, but the plugin will auto")
    @ConfigComment("calculate the closest location on the grid. Islands develop around this location")
    @ConfigComment("both positively and negatively in a square grid.")
    @ConfigComment("If none of this makes sense, leave it at 0,0.")
    @ConfigEntry(path = "world.start-x", needsReset = true)
    private int islandStartX = 0;

    @ConfigEntry(path = "world.start-z", needsReset = true)
    private int islandStartZ = 0;

    @ConfigEntry(path = "world.offset-x")
    private int islandXOffset;
    @ConfigEntry(path = "world.offset-z")
    private int islandZOffset;

    @ConfigComment("Island height - Lowest is 5.")
    @ConfigComment("It is the y coordinate of the bedrock block in the schem.")
    @ConfigEntry(path = "world.island-height")
    private int islandHeight = 70;

    @ConfigComment("The number of concurrent islands a player can have in the world")
    @ConfigComment("A value of 0 will use the BentoBox config.yml default")
    @ConfigEntry(path = "world.concurrent-islands")
    private int concurrentIslands = 0;

    @ConfigComment("Disallow team members from having their own islands.")
    @ConfigEntry(path = "world.disallow-team-member-islands")
    private boolean disallowTeamMemberIslands = true;

    @ConfigComment("Use your own world generator for this world.")
    @ConfigComment("In this case, the plugin will not generate anything.")
    @ConfigComment("If used, you must specify the world name and generator in the bukkit.yml file.")
    @ConfigComment("See https://bukkit.gamepedia.com/Bukkit.yml#.2AOPTIONAL.2A_worlds")
    @ConfigEntry(path = "world.use-own-generator")
    private boolean useOwnGenerator;

    @ConfigComment("Sea height (don't changes this mid-game unless you delete the world)")
    @ConfigComment("Minimum is 0")
    @ConfigComment("If sea height is less than about 10, then players will drop right through it")
    @ConfigComment("if it exists.")
    @ConfigEntry(path = "world.sea-height", needsReset = true)
    private int seaHeight = 70;

    @ConfigComment("Sea floor level - the base y below which the ocean floor terrain noise starts.")
    @ConfigEntry(path = "world.sea-floor", needsReset = true)
    private int seaFloor = 25;

    @ConfigComment("Water block for the overworld ocean.")
    @ConfigEntry(path = "world.water-block", needsReset = true)
    private Material waterBlock = Material.WATER;

    @ConfigComment("Allow vanilla cave generation: caves, ravines and cheese caverns under the")
    @ConfigComment("sea floor, and inside the islands. Dry air pockets down there are a feature -")
    @ConfigComment("they are somewhere to surface and something to mine.")
    @ConfigEntry(path = "world.make-caves")
    private boolean makeCaves = true;

    @ConfigComment("Allow vanilla decoration (kelp, seagrass, coral, trees on island land).")
    @ConfigEntry(path = "world.make-decorations")
    private boolean makeDecorations = true;

    @ConfigComment("Allow vanilla structure generation: shipwrecks, ocean ruins, ocean monuments,")
    @ConfigComment("buried treasure and trial chambers. Which of them appear where is decided by")
    @ConfigComment("the biomes the galaxy hands out, so deep basins get monuments, warm shallows")
    @ConfigComment("get warm ruins, and islet beaches get treasure.")
    @ConfigEntry(path = "world.make-structures")
    private boolean makeStructures = true;

    @ConfigComment("Keep vanilla structures off the trading islands themselves. A monument or a")
    @ConfigComment("village dropped through a market plaza would wreck the one part of the world")
    @ConfigComment("that is hand-built. Wild islets are fair game either way.")
    @ConfigEntry(path = "world.keep-structures-off-islands")
    private boolean keepStructuresOffIslands = true;

    @ConfigComment("Vary the sea floor. False gives the old featureless flat floor at the shelf")
    @ConfigComment("depth; true gives shelves, basins, rifts and seamounts.")
    @ConfigEntry(path = "world.seabed.vary", needsReset = true)
    private boolean varySeabed = true;

    @ConfigComment("Depth in blocks below sea level of the shallowest open water - the sunlit")
    @ConfigComment("banks where coral, kelp and ocean ruins sit.")
    @ConfigEntry(path = "world.seabed.shelf-depth", needsReset = true)
    private int seabedShelfDepth = 14;

    @ConfigComment("Depth in blocks below sea level of the deepest basins. Deep water gets the")
    @ConfigComment("deep ocean biomes, which is what lets vanilla place ocean monuments.")
    @ConfigEntry(path = "world.seabed.abyss-depth", needsReset = true)
    private int seabedAbyssDepth = 46;

    @ConfigComment("Depth in blocks of the shelf every island and islet sits on. The natural floor")
    @ConfigComment("is blended toward this near land, so an island over an abyssal plain still")
    @ConfigComment("stands in shallow water and still breaks the surface by the same amount.")
    @ConfigEntry(path = "world.seabed.island-shelf-depth", needsReset = true)
    private int seabedIslandShelfDepth = 18;

    @ConfigComment("Amplitude in blocks of the rolling hills on top of the basins - the difference")
    @ConfigComment("between a dune field and a flat plain.")
    @ConfigEntry(path = "world.seabed.relief", needsReset = true)
    private int seabedRelief = 9;

    @ConfigComment("How far below the surrounding floor a rift cuts at its deepest, in blocks.")
    @ConfigComment("0 disables underwater canyons.")
    @ConfigEntry(path = "world.seabed.rift-depth", needsReset = true)
    private int seabedRiftDepth = 26;

    @ConfigComment("How rare and narrow the rifts are (0-1). Higher means fewer, tighter canyons.")
    @ConfigEntry(path = "world.seabed.rift-threshold", needsReset = true)
    private double seabedRiftThreshold = 0.80;

    @ConfigComment("How far a seamount rises above the floor at its peak, in blocks. They only")
    @ConfigComment("grow on the deeper plains and never break the surface. 0 disables them.")
    @ConfigEntry(path = "world.seabed.seamount-height", needsReset = true)
    private int seabedSeamountHeight = 20;

    @ConfigComment("Maximum number of islands in the world. Set to -1 or 0 for unlimited.")
    @ConfigComment("If the number of islands is greater than this number, it will stop players from creating islands.")
    @ConfigEntry(path = "world.max-islands")
    private int maxIslands = -1;

    @ConfigComment("The default game mode for this world. Players will be set to this mode when they create")
    @ConfigComment("a new island for example. Options are SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR")
    @ConfigEntry(path = "world.default-game-mode")
    private GameMode defaultGameMode = GameMode.SURVIVAL;

    @ConfigComment("Vary the open sea through the ocean biomes (frozen, cold, ocean, lukewarm,")
    @ConfigComment("warm) using a seeded temperature field, so voyages cross visibly different")
    @ConfigComment("water. Transitions are always gradual - warm sea never borders frozen sea.")
    @ConfigComment("False uses the single default-biome everywhere.")
    @ConfigEntry(path = "world.vary-ocean-biomes")
    private boolean varyOceanBiomes = true;

    @ConfigComment("The default biome for the ocean (at and below sea level)")
    @ConfigEntry(path = "world.default-biome")
    private Biome defaultBiome;

    @ConfigComment("The default biome above sea level in the overworld")
    @ConfigEntry(path = "world.default-air-biome")
    private Biome defaultAirBiome;
    @ConfigComment("The default biome for the interstice (this may affect what mobs can spawn)")
    @ConfigEntry(path = "world.default-nether-biome")
    private Biome defaultNetherBiome;
    @ConfigComment("The default biome for the end world (this may affect what mobs can spawn)")
    @ConfigEntry(path = "world.default-end-biome")
    private Biome defaultEndBiome;

    @ConfigComment("The maximum number of players a player can ban at any one time in this game mode.")
    @ConfigComment("The permission acidisland.ban.maxlimit.X where X is a number can also be used per player")
    @ConfigComment("-1 = unlimited")
    @ConfigEntry(path = "world.ban-limit")
    private int banLimit = -1;

    // Interstice (registered as this gamemode's nether world)
    @ConfigComment("Generate the interstice - the hostile Nether-environment sea that failed warps")
    @ConfigComment("drop players into. If false, warps never fail and the world is not created.")
    @ConfigEntry(path = "world.nether.generate")
    private boolean netherGenerate = true;

    @ConfigComment("Use the TradeWinds ocean generator for the interstice (true) instead of vanilla nether.")
    @ConfigEntry(path = "world.nether.islands", needsReset = true)
    private boolean netherIslands = true;

    @ConfigComment("Sea height in the interstice.")
    @ConfigEntry(path = "world.nether.sea-height", needsReset = true)
    private int intersticeSeaHeight = 70;

    @ConfigComment("Sea floor level in the interstice.")
    @ConfigEntry(path = "world.nether.sea-floor", needsReset = true)
    private int intersticeSeaFloor = 25;

    @ConfigComment("Water block for the interstice sea.")
    @ConfigEntry(path = "world.nether.water-block", needsReset = true)
    private Material intersticeWaterBlock = Material.WATER;

    @ConfigComment("Make the nether roof, if false, there is nothing up there")
    @ConfigComment("Change to false if lag is a problem from the generation")
    @ConfigComment("Only applies to islands Nether")
    @ConfigEntry(path = "world.nether.roof")
    private boolean netherRoof = false;

    @ConfigComment("Nether spawn protection radius - this is the distance around the nether spawn")
    @ConfigComment("that will be protected from player interaction (breaking blocks, pouring lava etc.)")
    @ConfigComment("Minimum is 0 (not recommended), maximum is 100. Default is 25.")
    @ConfigComment("Only applies to vanilla nether")
    @ConfigEntry(path = "world.nether.spawn-radius")
    private int netherSpawnRadius = 32;

    @ConfigComment("This option indicates if nether portals should be linked via dimensions.")
    @ConfigComment("Option will simulate vanilla portal mechanics that links portals together")
    @ConfigComment("or creates a new portal, if there is not a portal in that dimension.")
    @ConfigComment("This option requires `allow-nether=true` in server.properties.")
    @ConfigEntry(path = "world.nether.create-and-link-portals") // , since = "1.16")
    private boolean makeNetherPortals = false;

    // End
    @ConfigComment("End Nether - if this is false, the end world will not be made and access to")
    @ConfigComment("the end will not occur. Other plugins may still enable portal usage.")
    @ConfigEntry(path = "world.end.generate")
    private boolean endGenerate = false;

    @ConfigComment("Islands in The End. Change to false for standard vanilla end.")
    @ConfigComment("Note that there is currently no magic block in the End")
    @ConfigEntry(path = "world.end.islands", needsReset = true)
    private boolean endIslands = false;

    @ConfigComment("This option indicates if obsidian platform in the end should be generated")
    @ConfigComment("when player enters the end world.")
    @ConfigComment("This option requires `allow-end=true` in bukkit.yml.")
    @ConfigEntry(path = "world.end.create-obsidian-platform") // , since = "1.16")
    private boolean makeEndPortals = false;

    @ConfigComment("Mob white list - these mobs will NOT be removed when logging in or doing /island")
    @ConfigEntry(path = "world.remove-mobs-whitelist")
    private Set<EntityType> removeMobsWhitelist = new HashSet<>();

    @ConfigComment("World flags. These are boolean settings for various flags for this world")
    @ConfigEntry(path = "world.flags")
    private Map<String, Boolean> worldFlags = new HashMap<>();

    @ConfigComment("These are the default protection settings for new islands.")
    @ConfigComment("The value is the minimum island rank required allowed to do the action")
    @ConfigComment("Ranks are the following:")
    @ConfigComment("  VISITOR   = 0")
    @ConfigComment("  COOP      = 200")
    @ConfigComment("  TRUSTED   = 400")
    @ConfigComment("  MEMBER    = 500")
    @ConfigComment("  SUB-OWNER = 900")
    @ConfigComment("  OWNER     = 1000")
    @ConfigEntry(path = "world.default-island-flags")
    private Map<String, Integer> defaultIslandFlagNames = new HashMap<>();

    @ConfigComment("These are the default settings for new islands")
    @ConfigEntry(path = "world.default-island-settings")
    @Adapter(FlagBooleanSerializer.class)
    private Map<String, Integer> defaultIslandSettingNames = new HashMap<>();

    @ConfigComment("These settings/flags are hidden from users")
    @ConfigComment("Ops can toggle hiding in-game using SHIFT-LEFT-CLICK on flags in settings")
    @ConfigEntry(path = "world.hidden-flags") // , since = "1.4.1")
    private List<String> hiddenFlags = new ArrayList<>();

    @ConfigComment("Visitor banned commands - Visitors to islands cannot use these commands in this world")
    @ConfigEntry(path = "world.visitor-banned-commands")
    private List<String> visitorBannedCommands = new ArrayList<>();

    @ConfigComment("Falling banned commands - players cannot use these commands when falling")
    @ConfigComment("if the PREVENT_TELEPORT_WHEN_FALLING world setting flag is active")
    @ConfigEntry(path = "world.falling-banned-commands") // , since = "1.8.0")
    private List<String> fallingBannedCommands = new ArrayList<>();

    // ---------------------------------------------

    /*      ISLAND      */



    @ConfigComment("Default max team size")
    @ConfigComment("Permission size cannot be less than the default below. ")
    @ConfigEntry(path = "island.max-team-size")
    private int maxTeamSize = 4;

    @ConfigComment("Default maximum number of coop rank members per island")
    @ConfigComment("Players can have the aoneblock.coop.maxsize.<number> permission to be bigger but")
    @ConfigComment("permission size cannot be less than the default below. ")
    @ConfigEntry(path = "island.max-coop-size") // , since = "1.13.0")
    private int maxCoopSize = 4;

    @ConfigComment("Default maximum number of trusted rank members per island")
    @ConfigComment("Players can have the aoneblock.trust.maxsize.<number> permission to be bigger but")
    @ConfigComment("permission size cannot be less than the default below. ")
    @ConfigEntry(path = "island.max-trusted-size") // , since = "1.13.0")
    private int maxTrustSize = 4;

    @ConfigComment("Default maximum number of homes a player can have. Min = 1")
    @ConfigComment("Accessed via /is sethome <number> or /is go <number>")
    @ConfigEntry(path = "island.max-homes")
    private int maxHomes = 5;

    // Reset
    @ConfigComment("How many resets a player is allowed (manage with /obadmin reset add/remove/reset/set command)")
    @ConfigComment("Value of -1 means unlimited, 0 means hardcore - no resets.")
    @ConfigComment("Example, 2 resets means they get 2 resets or 3 islands lifetime")
    @ConfigEntry(path = "island.reset.reset-limit")
    private int resetLimit = -1;

    @ConfigComment("Kicked or leaving players lose resets")
    @ConfigComment("Players who leave a team will lose an island reset chance")
    @ConfigComment("If a player has zero resets left and leaves a team, they cannot make a new")
    @ConfigComment("island by themselves and can only join a team.")
    @ConfigComment("Leave this true to avoid players exploiting free islands")
    @ConfigEntry(path = "island.reset.leavers-lose-reset")
    private boolean leaversLoseReset = false;

    @ConfigComment("Allow kicked players to keep their inventory.")
    @ConfigComment("Overrides the on-leave inventory reset for kicked players.")
    @ConfigEntry(path = "island.reset.kicked-keep-inventory")
    private boolean kickedKeepInventory = false;

    @ConfigComment("What the addon should reset when the player joins or creates an island")
    @ConfigComment("Reset Money - if this is true, will reset the player's money to the starting money")
    @ConfigComment("Recommendation is that this is set to true, but if you run multi-worlds")
    @ConfigComment("make sure your economy handles multi-worlds too.")
    @ConfigEntry(path = "island.reset.on-join.money")
    private boolean onJoinResetMoney = false;

    @ConfigComment("Reset inventory - if true, the player's inventory will be cleared.")
    @ConfigComment("Note: if you have MultiInv running or a similar inventory control plugin, that")
    @ConfigComment("plugin may still reset the inventory when the world changes.")
    @ConfigEntry(path = "island.reset.on-join.inventory")
    private boolean onJoinResetInventory = true;

    @ConfigComment("Reset health - if true, the player's health will be reset.")
    @ConfigEntry(path = "island.reset.on-join.health") // , since = "1.8.0")
    private boolean onJoinResetHealth = true;

    @ConfigComment("Reset hunger - if true, the player's hunger will be reset.")
    @ConfigEntry(path = "island.reset.on-join.hunger") // , since = "1.8.0")
    private boolean onJoinResetHunger = true;

    @ConfigComment("Reset experience points - if true, the player's experience will be reset.")
    @ConfigEntry(path = "island.reset.on-join.exp") // , since = "1.8.0")
    private boolean onJoinResetXP = true;


    @ConfigComment("Reset Ender Chest - if true, the player's Ender Chest will be cleared.")
    @ConfigEntry(path = "island.reset.on-join.ender-chest")
    private boolean onJoinResetEnderChest = false;

    @ConfigComment("What the plugin should reset when the player leaves or is kicked from an island")
    @ConfigComment("Reset Money - if this is true, will reset the player's money to the starting money")
    @ConfigComment("Recommendation is that this is set to true, but if you run multi-worlds")
    @ConfigComment("make sure your economy handles multi-worlds too.")
    @ConfigEntry(path = "island.reset.on-leave.money")
    private boolean onLeaveResetMoney = false;

    @ConfigComment("Reset inventory - if true, the player's inventory will be cleared.")
    @ConfigComment("Note: if you have MultiInv running or a similar inventory control plugin, that")
    @ConfigComment("plugin may still reset the inventory when the world changes.")
    @ConfigEntry(path = "island.reset.on-leave.inventory")
    private boolean onLeaveResetInventory = false;

    @ConfigComment("Reset health - if true, the player's health will be reset.")
    @ConfigEntry(path = "island.reset.on-leave.health") // , since = "1.8.0")
    private boolean onLeaveResetHealth = false;

    @ConfigComment("Reset hunger - if true, the player's hunger will be reset.")
    @ConfigEntry(path = "island.reset.on-leave.hunger") // , since = "1.8.0")
    private boolean onLeaveResetHunger = false;

    @ConfigComment("Reset experience - if true, the player's experience will be reset.")
    @ConfigEntry(path = "island.reset.on-leave.exp") // , since = "1.8.0")
    private boolean onLeaveResetXP = false;

    @ConfigComment("Reset Ender Chest - if true, the player's Ender Chest will be cleared.")
    @ConfigEntry(path = "island.reset.on-leave.ender-chest")
    private boolean onLeaveResetEnderChest = false;

    @ConfigComment("Toggles the automatic island creation upon the player's first login on your server.")
    @ConfigComment("If set to true,")
    @ConfigComment("   * Upon connecting to your server for the first time, the player will be told that")
    @ConfigComment("    an island will be created for him.")
    @ConfigComment("  * Make sure you have a Blueprint Bundle called \"default\": this is the one that will")
    @ConfigComment("    be used to create the island.")
    @ConfigComment("  * An island will be created for the player without needing him to run the create command.")
    @ConfigComment("If set to false, this will disable this feature entirely.")
    @ConfigComment("Warning:")
    @ConfigComment("  * If you are running multiple gamemodes on your server, and all of them have")
    @ConfigComment("    this feature enabled, an island in all the gamemodes will be created simultaneously.")
    @ConfigComment("    However, it is impossible to know on which island the player will be teleported to afterwards.")
    @ConfigComment("  * Island creation can be resource-intensive, please consider the options below to help mitigate")
    @ConfigComment("    the potential issues, especially if you expect a lot of players to connect to your server")
    @ConfigComment("    in a limited period of time.")
    @ConfigEntry(path = "island.create-island-on-first-login.enable") // , since = "1.9.0")
    private boolean createIslandOnFirstLoginEnabled;

    @ConfigComment("Time in seconds after the player logged in, before his island gets created.")
    @ConfigComment("If set to 0 or less, the island will be created directly upon the player's login.")
    @ConfigComment("It is recommended to keep this value under a minute's time.")
    @ConfigEntry(path = "island.create-island-on-first-login.delay") // , since = "1.9.0")
    private int createIslandOnFirstLoginDelay = 5;

    @ConfigComment("Toggles whether the island creation should be aborted if the player logged off while the")
    @ConfigComment("delay (see the option above) has not worn off yet.")
    @ConfigComment("If set to true,")
    @ConfigComment("  * If the player has logged off the server while the delay (see the option above) has not")
    @ConfigComment("    worn off yet, this will cancel the island creation.")
    @ConfigComment("  * If the player relogs afterward, since he will not be recognized as a new player, no island")
    @ConfigComment("    would be created for him.")
    @ConfigComment("  * If the island creation started before the player logged off, it will continue.")
    @ConfigComment("If set to false, the player's island will be created even if he went offline in the meantime.")
    @ConfigComment("Note this option has no effect if the delay (see the option above) is set to 0 or less.")
    @ConfigEntry(path = "island.create-island-on-first-login.abort-on-logout") // , since = "1.9.0")
    private boolean createIslandOnFirstLoginAbortOnLogout = true;

    @ConfigComment("Toggles whether the player should be teleported automatically to his island when it is created.")
    @ConfigComment("If set to false, the player will be told his island is ready but will have to teleport to his island using the command.")
    @ConfigEntry(path = "island.teleport-player-to-island-when-created") // , since = "1.10.0")
    private boolean teleportPlayerToIslandUponIslandCreation = true;

    @ConfigComment("Create Nether or End islands if they are missing when a player goes through a portal.")
    @ConfigComment("Nether and End islands are usually pasted when a player makes their island, but if they are")
    @ConfigComment("missing for some reason, you can switch this on.")
    @ConfigComment("Note that bedrock removal glitches can exploit this option.")
    @ConfigEntry(path = "island.create-missing-nether-end-islands") // , since = "1.10.0")
    private boolean pasteMissingIslands = false;

    // Commands
    @ConfigComment("List of commands to run when a player joins an island or creates one.")
    @ConfigComment("These commands are run by the console, unless otherwise stated using the [SUDO] prefix,")
    @ConfigComment("in which case they are executed by the player.")
    @ConfigComment("")
    @ConfigComment("Available placeholders for the commands are the following:")
    @ConfigComment("   * [name]: name of the player")
    @ConfigComment("")
    @ConfigComment("Here are some examples of valid commands to execute:")
    @ConfigComment("   * \"[SUDO] bbox version\"")
    @ConfigComment("   * \"obadmin deaths set [player] 0\"")
    @ConfigEntry(path = "island.commands.on-join") // , since = "1.8.0")
    private List<String> onJoinCommands = new ArrayList<>();

    @ConfigComment("List of commands to run when a player leaves an island, resets his island or gets kicked from it.")
    @ConfigComment("These commands are run by the console, unless otherwise stated using the [SUDO] prefix,")
    @ConfigComment("in which case they are executed by the player.")
    @ConfigComment("")
    @ConfigComment("Available placeholders for the commands are the following:")
    @ConfigComment("   * [name]: name of the player")
    @ConfigComment("")
    @ConfigComment("Here are some examples of valid commands to execute:")
    @ConfigComment("   * '[SUDO] bbox version'")
    @ConfigComment("   * 'obadmin deaths set [player] 0'")
    @ConfigComment("")
    @ConfigComment("Note that player-executed commands might not work, as these commands can be run with said player being offline.")
    @ConfigEntry(path = "island.commands.on-leave") // , since = "1.8.0")
    private List<String> onLeaveCommands = new ArrayList<>();

    @ConfigComment("List of commands that should be executed when the player respawns after death if Flags.ISLAND_RESPAWN is true.")
    @ConfigComment("These commands are run by the console, unless otherwise stated using the [SUDO] prefix,")
    @ConfigComment("in which case they are executed by the player.")
    @ConfigComment("")
    @ConfigComment("Available placeholders for the commands are the following:")
    @ConfigComment("   * [name]: name of the player")
    @ConfigComment("")
    @ConfigComment("Here are some examples of valid commands to execute:")
    @ConfigComment("   * '[SUDO] bbox version'")
    @ConfigComment("   * 'obadmin deaths set [player] 0'")
    @ConfigComment("")
    @ConfigComment("Note that player-executed commands might not work, as these commands can be run with said player being offline.")
    @ConfigEntry(path = "island.commands.on-respawn") // , since = "1.14.0")
    private List<String> onRespawnCommands = new ArrayList<>();

    // Sethome
    @ConfigEntry(path = "island.sethome.nether.allow")
    private boolean allowSetHomeInNether = true;

    @ConfigEntry(path = "island.sethome.nether.require-confirmation")
    private boolean requireConfirmationToSetHomeInNether = true;

    @ConfigEntry(path = "island.sethome.the-end.allow")
    private boolean allowSetHomeInTheEnd = true;

    @ConfigEntry(path = "island.sethome.the-end.require-confirmation")
    private boolean requireConfirmationToSetHomeInTheEnd = true;

    // Deaths
    @ConfigComment("Whether deaths are counted or not.")
    @ConfigEntry(path = "island.deaths.counted")
    private boolean deathsCounted = true;

    @ConfigComment("Maximum number of deaths to count. The death count can be used by add-ons.")
    @ConfigEntry(path = "island.deaths.max")
    private int deathsMax = 10;

    @ConfigComment("When a player joins a team, reset their death count")
    @ConfigEntry(path = "island.deaths.team-join-reset")
    private boolean teamJoinDeathReset = true;

    @ConfigComment("Reset player death count when they start a new island or reset an island")
    @ConfigEntry(path = "island.deaths.reset-on-new-island") // , since = "1.6.0")
    private boolean deathsResetOnNewIsland = true;

    // ---------------------------------------------
    /*      PROTECTION      */

    @ConfigComment("Geo restrict mobs.")
    @ConfigComment("Mobs that exit the island space where they were spawned will be removed.")
    @ConfigEntry(path = "protection.geo-limit-settings")
    private List<String> geoLimitSettings = new ArrayList<>();

    @ConfigComment("AOneBlock blocked mobs.")
    @ConfigComment("List of mobs that should not spawn in AOneBlock.")
    @ConfigEntry(path = "protection.block-mobs") // , since = "1.2.0")
    private List<String> mobLimitSettings = new ArrayList<>();


    // Invincible visitor settings
    @ConfigComment("Invincible visitors. List of damages that will not affect visitors.")
    @ConfigComment("Make list blank if visitors should receive all damages")
    @ConfigEntry(path = "protection.invincible-visitors")
    private List<String> ivSettings = new ArrayList<>();

    //---------------------------------------------------------------------------------------/
    @ConfigComment("These settings should not be edited")
    @ConfigEntry(path = "do-not-edit-these-settings.reset-epoch")
    private long resetEpoch = 0;

    /**
     * @return the friendlyName
     */
    @Override
    public String getFriendlyName() {
        return friendlyName;
    }

    /**
     * @return the worldName
     */
    @Override
    public String getWorldName() {
        return worldName;
    }

    /**
     * @return the difficulty
     */
    @Override
    public Difficulty getDifficulty() {
        return difficulty;
    }

    /**
     * @return the islandDistance
     */
    @Override
    public int getIslandDistance() {
        return islandDistance;
    }

    /**
     * @return the islandProtectionRange
     */
    @Override
    public int getIslandProtectionRange() {
        return islandProtectionRange;
    }

    /**
     * @return the islandStartX
     */
    @Override
    public int getIslandStartX() {
        return islandStartX;
    }

    /**
     * @return the islandStartZ
     */
    @Override
    public int getIslandStartZ() {
        return islandStartZ;
    }

    /**
     * @return the islandXOffset
     */
    @Override
    public int getIslandXOffset() {
        return islandXOffset;
    }

    /**
     * @return the islandZOffset
     */
    @Override
    public int getIslandZOffset() {
        return islandZOffset;
    }

    /**
     * @return the islandHeight
     */
    @Override
    public int getIslandHeight() {
        return islandHeight;
    }

    /**
     * @return the useOwnGenerator
     */
    @Override
    public boolean isUseOwnGenerator() {
        return useOwnGenerator;
    }

    /**
     * @return the seaHeight
     */
    @Override
    public int getSeaHeight() {
        return seaHeight;
    }

    /**
     * @return the maxIslands
     */
    @Override
    public int getMaxIslands() {
        return maxIslands;
    }

    /**
     * @return the defaultGameMode
     */
    @Override
    public GameMode getDefaultGameMode() {
        return defaultGameMode;
    }

    /**
     * @return the netherGenerate
     */
    @Override
    public boolean isNetherGenerate() {
        return netherGenerate;
    }

    /**
     * @return the netherIslands
     */
    @Override
    public boolean isNetherIslands() {
        return netherIslands;
    }

    /**
     * @return the netherRoof
     */
    public boolean isNetherRoof() {
        return netherRoof;
    }

    /**
     * @return the netherSpawnRadius
     */
    @Override
    public int getNetherSpawnRadius() {
        return netherSpawnRadius;
    }

    /**
     * @return the endGenerate
     */
    @Override
    public boolean isEndGenerate() {
        return endGenerate;
    }

    /**
     * @return the endIslands
     */
    @Override
    public boolean isEndIslands() {
        return endIslands;
    }

    /**
     * @return the dragonSpawn
     */
    @Override
    public boolean isDragonSpawn() {
        return false;
    }

    /**
     * @return the removeMobsWhitelist
     */
    @Override
    public Set<EntityType> getRemoveMobsWhitelist() {
        return removeMobsWhitelist;
    }

    /**
     * @return the worldFlags
     */
    @Override
    public Map<String, Boolean> getWorldFlags() {
        return worldFlags;
    }


    /**
     * @return the defaultIslandFlags
     * @since 1.21.0
     */
    @Override
    public Map<String, Integer> getDefaultIslandFlagNames()
    {
        return defaultIslandFlagNames;
    }


    /**
     * @return the defaultIslandSettings
     * @since 1.21.0
     */
    @Override
    public Map<String, Integer> getDefaultIslandSettingNames()
    {
        return defaultIslandSettingNames;
    }


    /**
     * @return the defaultIslandFlags
     * @deprecated Replaced with #getDefaultIslandFlagNames
     * @since 1.21
     */
    @Override
    @Deprecated(since = "1.21", forRemoval = true)
    public Map<Flag, Integer> getDefaultIslandFlags() {
        return Collections.emptyMap();
    }

    /**
     * @return the defaultIslandSettings
     * @deprecated Replaced with #getDefaultIslandSettingNames
     * @since 1.21
     */
    @Override
    @Deprecated(since = "1.21", forRemoval = true)
    public Map<Flag, Integer> getDefaultIslandSettings() {
        return Collections.emptyMap();
    }

    /**
     * @return the hidden flags
     */
    @Override
    public List<String> getHiddenFlags() {
        return hiddenFlags;
    }

    /**
     * @return the visitorBannedCommands
     */
    @Override
    public List<String> getVisitorBannedCommands() {
        return visitorBannedCommands;
    }

    /**
     * @return the fallingBannedCommands
     */
    @Override
    public List<String> getFallingBannedCommands() {
        return fallingBannedCommands;
    }

    /**
     * @return the maxTeamSize
     */
    @Override
    public int getMaxTeamSize() {
        return maxTeamSize;
    }

    /**
     * @return the maxHomes
     */
    @Override
    public int getMaxHomes() {
        return maxHomes;
    }

    /**
     * @return the resetLimit
     */
    @Override
    public int getResetLimit() {
        return resetLimit;
    }

    /**
     * @return the leaversLoseReset
     */
    @Override
    public boolean isLeaversLoseReset() {
        return leaversLoseReset;
    }

    /**
     * @return the kickedKeepInventory
     */
    @Override
    public boolean isKickedKeepInventory() {
        return kickedKeepInventory;
    }


    /**
     * This method returns the createIslandOnFirstLoginEnabled boolean value.
     * @return the createIslandOnFirstLoginEnabled value
     * @since 1.9.0
     */
    @Override
    public boolean isCreateIslandOnFirstLoginEnabled()
    {
        return createIslandOnFirstLoginEnabled;
    }


    /**
     * This method returns the createIslandOnFirstLoginDelay int value.
     * @return the createIslandOnFirstLoginDelay value
     * @since 1.9.0
     */
    @Override
    public int getCreateIslandOnFirstLoginDelay()
    {
        return createIslandOnFirstLoginDelay;
    }


    /**
     * This method returns the createIslandOnFirstLoginAbortOnLogout boolean value.
     * @return the createIslandOnFirstLoginAbortOnLogout value
     * @since 1.9.0
     */
    @Override
    public boolean isCreateIslandOnFirstLoginAbortOnLogout()
    {
        return createIslandOnFirstLoginAbortOnLogout;
    }


    /**
     * @return the onJoinResetMoney
     */
    @Override
    public boolean isOnJoinResetMoney() {
        return onJoinResetMoney;
    }

    /**
     * @return the onJoinResetInventory
     */
    @Override
    public boolean isOnJoinResetInventory() {
        return onJoinResetInventory;
    }

    /**
     * @return the onJoinResetEnderChest
     */
    @Override
    public boolean isOnJoinResetEnderChest() {
        return onJoinResetEnderChest;
    }

    /**
     * @return the onLeaveResetMoney
     */
    @Override
    public boolean isOnLeaveResetMoney() {
        return onLeaveResetMoney;
    }

    /**
     * @return the onLeaveResetInventory
     */
    @Override
    public boolean isOnLeaveResetInventory() {
        return onLeaveResetInventory;
    }

    /**
     * @return the onLeaveResetEnderChest
     */
    @Override
    public boolean isOnLeaveResetEnderChest() {
        return onLeaveResetEnderChest;
    }

    /**
     * @return the isDeathsCounted
     */
    @Override
    public boolean isDeathsCounted() {
        return deathsCounted;
    }

    /**
     * @return the allowSetHomeInNether
     */
    @Override
    public boolean isAllowSetHomeInNether() {
        return allowSetHomeInNether;
    }

    /**
     * @return the allowSetHomeInTheEnd
     */
    @Override
    public boolean isAllowSetHomeInTheEnd() {
        return allowSetHomeInTheEnd;
    }

    /**
     * @return the requireConfirmationToSetHomeInNether
     */
    @Override
    public boolean isRequireConfirmationToSetHomeInNether() {
        return requireConfirmationToSetHomeInNether;
    }

    /**
     * @return the requireConfirmationToSetHomeInTheEnd
     */
    @Override
    public boolean isRequireConfirmationToSetHomeInTheEnd() {
        return requireConfirmationToSetHomeInTheEnd;
    }

    /**
     * @return the deathsMax
     */
    @Override
    public int getDeathsMax() {
        return deathsMax;
    }

    /**
     * @return the teamJoinDeathReset
     */
    @Override
    public boolean isTeamJoinDeathReset() {
        return teamJoinDeathReset;
    }

    /**
     * @return the geoLimitSettings
     */
    @Override
    public List<String> getGeoLimitSettings() {
        return geoLimitSettings;
    }

    /**
     * @return the ivSettings
     */
    @Override
    public List<String> getIvSettings() {
        return ivSettings;
    }

    /**
     * @return the resetEpoch
     */
    @Override
    public long getResetEpoch() {
        return resetEpoch;
    }

    /**
     * @param friendlyName the friendlyName to set
     */
    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    /**
     * @param worldName the worldName to set
     */
    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    /**
     * @param difficulty the difficulty to set
     */
    @Override
    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * @param islandDistance the islandDistance to set
     */
    public void setIslandDistance(int islandDistance) {
        this.islandDistance = islandDistance;
    }

    /**
     * @param islandProtectionRange the islandProtectionRange to set
     */
    public void setIslandProtectionRange(int islandProtectionRange) {
        this.islandProtectionRange = islandProtectionRange;
    }

    /**
     * @param islandStartX the islandStartX to set
     */
    public void setIslandStartX(int islandStartX) {
        this.islandStartX = islandStartX;
    }

    /**
     * @param islandStartZ the islandStartZ to set
     */
    public void setIslandStartZ(int islandStartZ) {
        this.islandStartZ = islandStartZ;
    }

    /**
     * @param islandXOffset the islandXOffset to set
     */
    public void setIslandXOffset(int islandXOffset) {
        this.islandXOffset = islandXOffset;
    }

    /**
     * @param islandZOffset the islandZOffset to set
     */
    public void setIslandZOffset(int islandZOffset) {
        this.islandZOffset = islandZOffset;
    }

    /**
     * @param islandHeight the islandHeight to set
     */
    public void setIslandHeight(int islandHeight) {
        this.islandHeight = islandHeight;
    }

    /**
     * @param useOwnGenerator the useOwnGenerator to set
     */
    public void setUseOwnGenerator(boolean useOwnGenerator) {
        this.useOwnGenerator = useOwnGenerator;
    }

    /**
     * @param seaHeight the seaHeight to set
     */
    public void setSeaHeight(int seaHeight) {
        this.seaHeight = seaHeight;
    }

    /**
     * @param maxIslands the maxIslands to set
     */
    public void setMaxIslands(int maxIslands) {
        this.maxIslands = maxIslands;
    }

    /**
     * @param defaultGameMode the defaultGameMode to set
     */
    public void setDefaultGameMode(GameMode defaultGameMode) {
        this.defaultGameMode = defaultGameMode;
    }

    /**
     * @param netherGenerate the netherGenerate to set
     */
    public void setNetherGenerate(boolean netherGenerate) {
        this.netherGenerate = netherGenerate;
    }

    /**
     * @param netherIslands the netherIslands to set
     */
    public void setNetherIslands(boolean netherIslands) {
        this.netherIslands = netherIslands;
    }

    /**
     * @param netherRoof the netherRoof to set
     */
    public void setNetherRoof(boolean netherRoof) {
        this.netherRoof = netherRoof;
    }

    /**
     * @param netherSpawnRadius the netherSpawnRadius to set
     */
    public void setNetherSpawnRadius(int netherSpawnRadius) {
        this.netherSpawnRadius = netherSpawnRadius;
    }

    /**
     * @param endGenerate the endGenerate to set
     */
    public void setEndGenerate(boolean endGenerate) {
        this.endGenerate = endGenerate;
    }

    /**
     * @param endIslands the endIslands to set
     */
    public void setEndIslands(boolean endIslands) {
        this.endIslands = endIslands;
    }

    /**
     * @param removeMobsWhitelist the removeMobsWhitelist to set
     */
    public void setRemoveMobsWhitelist(Set<EntityType> removeMobsWhitelist) {
        this.removeMobsWhitelist = removeMobsWhitelist;
    }

    /**
     * @param worldFlags the worldFlags to set
     */
    public void setWorldFlags(Map<String, Boolean> worldFlags) {
        this.worldFlags = worldFlags;
    }


    /**
     * Sets default island flag names.
     *
     * @param defaultIslandFlagNames the default island flag names
     */
    public void setDefaultIslandFlagNames(Map<String, Integer> defaultIslandFlagNames)
    {
        this.defaultIslandFlagNames = defaultIslandFlagNames;
    }


    /**
     * Sets default island setting names.
     *
     * @param defaultIslandSettingNames the default island setting names
     */
    public void setDefaultIslandSettingNames(Map<String, Integer> defaultIslandSettingNames)
    {
        this.defaultIslandSettingNames = defaultIslandSettingNames;
    }


    /**
     * @param hiddenFlags the hidden flags to set
     */
    public void setHiddenFlags(List<String> hiddenFlags) {
        this.hiddenFlags = hiddenFlags;
    }

    /**
     * @param visitorBannedCommands the visitorBannedCommands to set
     */
    public void setVisitorBannedCommands(List<String> visitorBannedCommands) {
        this.visitorBannedCommands = visitorBannedCommands;
    }

    /**
     * @param fallingBannedCommands the fallingBannedCommands to set
     */
    public void setFallingBannedCommands(List<String> fallingBannedCommands) {
        this.fallingBannedCommands = fallingBannedCommands;
    }

    /**
     * @param maxTeamSize the maxTeamSize to set
     */
    public void setMaxTeamSize(int maxTeamSize) {
        this.maxTeamSize = maxTeamSize;
    }

    /**
     * @param maxHomes the maxHomes to set
     */
    public void setMaxHomes(int maxHomes) {
        this.maxHomes = maxHomes;
    }

    /**
     * @param resetLimit the resetLimit to set
     */
    public void setResetLimit(int resetLimit) {
        this.resetLimit = resetLimit;
    }

    /**
     * @param leaversLoseReset the leaversLoseReset to set
     */
    public void setLeaversLoseReset(boolean leaversLoseReset) {
        this.leaversLoseReset = leaversLoseReset;
    }

    /**
     * @param kickedKeepInventory the kickedKeepInventory to set
     */
    public void setKickedKeepInventory(boolean kickedKeepInventory) {
        this.kickedKeepInventory = kickedKeepInventory;
    }

    /**
     * @param onJoinResetMoney the onJoinResetMoney to set
     */
    public void setOnJoinResetMoney(boolean onJoinResetMoney) {
        this.onJoinResetMoney = onJoinResetMoney;
    }

    /**
     * @param onJoinResetInventory the onJoinResetInventory to set
     */
    public void setOnJoinResetInventory(boolean onJoinResetInventory) {
        this.onJoinResetInventory = onJoinResetInventory;
    }

    /**
     * @param onJoinResetEnderChest the onJoinResetEnderChest to set
     */
    public void setOnJoinResetEnderChest(boolean onJoinResetEnderChest) {
        this.onJoinResetEnderChest = onJoinResetEnderChest;
    }

    /**
     * @param onLeaveResetMoney the onLeaveResetMoney to set
     */
    public void setOnLeaveResetMoney(boolean onLeaveResetMoney) {
        this.onLeaveResetMoney = onLeaveResetMoney;
    }

    /**
     * @param onLeaveResetInventory the onLeaveResetInventory to set
     */
    public void setOnLeaveResetInventory(boolean onLeaveResetInventory) {
        this.onLeaveResetInventory = onLeaveResetInventory;
    }

    /**
     * @param onLeaveResetEnderChest the onLeaveResetEnderChest to set
     */
    public void setOnLeaveResetEnderChest(boolean onLeaveResetEnderChest) {
        this.onLeaveResetEnderChest = onLeaveResetEnderChest;
    }

    /**
     * @param createIslandOnFirstLoginEnabled the createIslandOnFirstLoginEnabled to set
     */
    public void setCreateIslandOnFirstLoginEnabled(boolean createIslandOnFirstLoginEnabled)
    {
        this.createIslandOnFirstLoginEnabled = createIslandOnFirstLoginEnabled;
    }

    /**
     * @param createIslandOnFirstLoginDelay the createIslandOnFirstLoginDelay to set
     */
    public void setCreateIslandOnFirstLoginDelay(int createIslandOnFirstLoginDelay)
    {
        this.createIslandOnFirstLoginDelay = createIslandOnFirstLoginDelay;
    }

    /**
     * @param createIslandOnFirstLoginAbortOnLogout the createIslandOnFirstLoginAbortOnLogout to set
     */
    public void setCreateIslandOnFirstLoginAbortOnLogout(boolean createIslandOnFirstLoginAbortOnLogout)
    {
        this.createIslandOnFirstLoginAbortOnLogout = createIslandOnFirstLoginAbortOnLogout;
    }

    /**
     * @param deathsCounted the deathsCounted to set
     */
    public void setDeathsCounted(boolean deathsCounted) {
        this.deathsCounted = deathsCounted;
    }

    /**
     * @param deathsMax the deathsMax to set
     */
    public void setDeathsMax(int deathsMax) {
        this.deathsMax = deathsMax;
    }

    /**
     * @param teamJoinDeathReset the teamJoinDeathReset to set
     */
    public void setTeamJoinDeathReset(boolean teamJoinDeathReset) {
        this.teamJoinDeathReset = teamJoinDeathReset;
    }

    /**
     * @param geoLimitSettings the geoLimitSettings to set
     */
    public void setGeoLimitSettings(List<String> geoLimitSettings) {
        this.geoLimitSettings = geoLimitSettings;
    }

    /**
     * @param ivSettings the ivSettings to set
     */
    public void setIvSettings(List<String> ivSettings) {
        this.ivSettings = ivSettings;
    }

    /**
     * @param allowSetHomeInNether the allowSetHomeInNether to set
     */
    public void setAllowSetHomeInNether(boolean allowSetHomeInNether) {
        this.allowSetHomeInNether = allowSetHomeInNether;
    }

    /**
     * @param allowSetHomeInTheEnd the allowSetHomeInTheEnd to set
     */
    public void setAllowSetHomeInTheEnd(boolean allowSetHomeInTheEnd) {
        this.allowSetHomeInTheEnd = allowSetHomeInTheEnd;
    }

    /**
     * @param requireConfirmationToSetHomeInNether the requireConfirmationToSetHomeInNether to set
     */
    public void setRequireConfirmationToSetHomeInNether(boolean requireConfirmationToSetHomeInNether) {
        this.requireConfirmationToSetHomeInNether = requireConfirmationToSetHomeInNether;
    }

    /**
     * @param requireConfirmationToSetHomeInTheEnd the requireConfirmationToSetHomeInTheEnd to set
     */
    public void setRequireConfirmationToSetHomeInTheEnd(boolean requireConfirmationToSetHomeInTheEnd) {
        this.requireConfirmationToSetHomeInTheEnd = requireConfirmationToSetHomeInTheEnd;
    }

    /**
     * @param resetEpoch the resetEpoch to set
     */
    @Override
    public void setResetEpoch(long resetEpoch) {
        this.resetEpoch = resetEpoch;
    }

    @Override
    public String getPermissionPrefix() {
        return "tradewinds";
    }

    @Override
    public boolean isWaterUnsafe() {
        return false;
    }

    /**
     * @return default biome
     */
    public Biome getDefaultBiome() {
        return defaultBiome == null ? Biome.OCEAN : defaultBiome;
    }

    /**
     * @param defaultBiome the defaultBiome to set
     */
    public void setDefaultBiome(Biome defaultBiome) {
        this.defaultBiome = defaultBiome;
    }

    /**
     * @return the banLimit
     */
    @Override
    public int getBanLimit() {
        return banLimit;
    }

    /**
     * @param banLimit the banLimit to set
     */
    public void setBanLimit(int banLimit) {
        this.banLimit = banLimit;
    }

    /**
     * @return the playerCommandAliases
     */
    @Override
    public String getPlayerCommandAliases() {
        return playerCommandAliases;
    }

    /**
     * @param playerCommandAliases the playerCommandAliases to set
     */
    public void setPlayerCommandAliases(String playerCommandAliases) {
        this.playerCommandAliases = playerCommandAliases;
    }

    /**
     * @return the adminCommandAliases
     */
    @Override
    public String getAdminCommandAliases() {
        return adminCommandAliases;
    }

    /**
     * @param adminCommandAliases the adminCommandAliases to set
     */
    public void setAdminCommandAliases(String adminCommandAliases) {
        this.adminCommandAliases = adminCommandAliases;
    }

    /**
     * @return the deathsResetOnNew
     */
    @Override
    public boolean isDeathsResetOnNewIsland() {
        return deathsResetOnNewIsland;
    }

    /**
     * @param deathsResetOnNew the deathsResetOnNew to set
     */
    public void setDeathsResetOnNewIsland(boolean deathsResetOnNew) {
        this.deathsResetOnNewIsland = deathsResetOnNew;
    }

    /**
     * @return the onJoinCommands
     */
    @Override
    public List<String> getOnJoinCommands() {
        return onJoinCommands;
    }

    /**
     * @param onJoinCommands the onJoinCommands to set
     */
    public void setOnJoinCommands(List<String> onJoinCommands) {
        this.onJoinCommands = onJoinCommands;
    }

    /**
     * @return the onLeaveCommands
     */
    @Override
    public List<String> getOnLeaveCommands() {
        return onLeaveCommands;
    }

    /**
     * @param onLeaveCommands the onLeaveCommands to set
     */
    public void setOnLeaveCommands(List<String> onLeaveCommands) {
        this.onLeaveCommands = onLeaveCommands;
    }

    /**
     * @return the onRespawnCommands
     */
    @Override
    public List<String> getOnRespawnCommands() {
        return onRespawnCommands;
    }

    /**
     * Sets on respawn commands.
     *
     * @param onRespawnCommands the on respawn commands
     */
    public void setOnRespawnCommands(List<String> onRespawnCommands) {
        this.onRespawnCommands = onRespawnCommands;
    }

    /**
     * @return the onJoinResetHealth
     */
    @Override
    public boolean isOnJoinResetHealth() {
        return onJoinResetHealth;
    }

    /**
     * @param onJoinResetHealth the onJoinResetHealth to set
     */
    public void setOnJoinResetHealth(boolean onJoinResetHealth) {
        this.onJoinResetHealth = onJoinResetHealth;
    }

    /**
     * @return the onJoinResetHunger
     */
    @Override
    public boolean isOnJoinResetHunger() {
        return onJoinResetHunger;
    }

    /**
     * @param onJoinResetHunger the onJoinResetHunger to set
     */
    public void setOnJoinResetHunger(boolean onJoinResetHunger) {
        this.onJoinResetHunger = onJoinResetHunger;
    }

    /**
     * @return the onJoinResetXP
     */
    @Override
    public boolean isOnJoinResetXP() {
        return onJoinResetXP;
    }

    /**
     * @param onJoinResetXP the onJoinResetXP to set
     */
    public void setOnJoinResetXP(boolean onJoinResetXP) {
        this.onJoinResetXP = onJoinResetXP;
    }

    /**
     * @return the onLeaveResetHealth
     */
    @Override
    public boolean isOnLeaveResetHealth() {
        return onLeaveResetHealth;
    }

    /**
     * @param onLeaveResetHealth the onLeaveResetHealth to set
     */
    public void setOnLeaveResetHealth(boolean onLeaveResetHealth) {
        this.onLeaveResetHealth = onLeaveResetHealth;
    }

    /**
     * @return the onLeaveResetHunger
     */
    @Override
    public boolean isOnLeaveResetHunger() {
        return onLeaveResetHunger;
    }

    /**
     * @param onLeaveResetHunger the onLeaveResetHunger to set
     */
    public void setOnLeaveResetHunger(boolean onLeaveResetHunger) {
        this.onLeaveResetHunger = onLeaveResetHunger;
    }

    /**
     * @return the onLeaveResetXP
     */
    @Override
    public boolean isOnLeaveResetXP() {
        return onLeaveResetXP;
    }

    /**
     * @param onLeaveResetXP the onLeaveResetXP to set
     */
    public void setOnLeaveResetXP(boolean onLeaveResetXP) {
        this.onLeaveResetXP = onLeaveResetXP;
    }

    /**
     * @return the pasteMissingIslands
     */
    @Override
    public boolean isPasteMissingIslands() {
        return pasteMissingIslands;
    }

    /**
     * @param pasteMissingIslands the pasteMissingIslands to set
     */
    public void setPasteMissingIslands(boolean pasteMissingIslands) {
        this.pasteMissingIslands = pasteMissingIslands;
    }

    /**
     * Toggles whether the player should be teleported automatically to his island when it is created.
     * @return {@code true} if the player should be teleported automatically to his island when it is created,
     *         {@code false} otherwise.
     * @since 1.10.0
     */
    @Override
    public boolean isTeleportPlayerToIslandUponIslandCreation() {
        return teleportPlayerToIslandUponIslandCreation;
    }

    /**
     * @param teleportPlayerToIslandUponIslandCreation the teleportPlayerToIslandUponIslandCreation to set
     * @since 1.10.0
     */
    public void setTeleportPlayerToIslandUponIslandCreation(boolean teleportPlayerToIslandUponIslandCreation) {
        this.teleportPlayerToIslandUponIslandCreation = teleportPlayerToIslandUponIslandCreation;
    }

    /**
     * @return the spawnLimitMonsters
     */
    public int getSpawnLimitMonsters() {
        return spawnLimitMonsters;
    }

    /**
     * @param spawnLimitMonsters the spawnLimitMonsters to set
     */
    public void setSpawnLimitMonsters(int spawnLimitMonsters) {
        this.spawnLimitMonsters = spawnLimitMonsters;
    }

    /**
     * @return the spawnLimitAnimals
     */
    public int getSpawnLimitAnimals() {
        return spawnLimitAnimals;
    }

    /**
     * @param spawnLimitAnimals the spawnLimitAnimals to set
     */
    public void setSpawnLimitAnimals(int spawnLimitAnimals) {
        this.spawnLimitAnimals = spawnLimitAnimals;
    }

    /**
     * @return the spawnLimitWaterAnimals
     */
    public int getSpawnLimitWaterAnimals() {
        return spawnLimitWaterAnimals;
    }

    /**
     * @param spawnLimitWaterAnimals the spawnLimitWaterAnimals to set
     */
    public void setSpawnLimitWaterAnimals(int spawnLimitWaterAnimals) {
        this.spawnLimitWaterAnimals = spawnLimitWaterAnimals;
    }

    /**
     * @return the spawnLimitAmbient
     */
    public int getSpawnLimitAmbient() {
        return spawnLimitAmbient;
    }

    /**
     * @param spawnLimitAmbient the spawnLimitAmbient to set
     */
    public void setSpawnLimitAmbient(int spawnLimitAmbient) {
        this.spawnLimitAmbient = spawnLimitAmbient;
    }

    /**
     * @return the ticksPerAnimalSpawns
     */
    public int getTicksPerAnimalSpawns() {
        return ticksPerAnimalSpawns;
    }

    /**
     * @param ticksPerAnimalSpawns the ticksPerAnimalSpawns to set
     */
    public void setTicksPerAnimalSpawns(int ticksPerAnimalSpawns) {
        this.ticksPerAnimalSpawns = ticksPerAnimalSpawns;
    }

    /**
     * @return the ticksPerMonsterSpawns
     */
    public int getTicksPerMonsterSpawns() {
        return ticksPerMonsterSpawns;
    }

    /**
     * @param ticksPerMonsterSpawns the ticksPerMonsterSpawns to set
     */
    public void setTicksPerMonsterSpawns(int ticksPerMonsterSpawns) {
        this.ticksPerMonsterSpawns = ticksPerMonsterSpawns;
    }

    /**
     * @return the maxCoopSize
     */
    @Override
    public int getMaxCoopSize() {
        return maxCoopSize;
    }

    /**
     * @param maxCoopSize the maxCoopSize to set
     */
    public void setMaxCoopSize(int maxCoopSize) {
        this.maxCoopSize = maxCoopSize;
    }

    /**
     * @return the maxTrustSize
     */
    @Override
    public int getMaxTrustSize() {
        return maxTrustSize;
    }

    /**
     * @param maxTrustSize the maxTrustSize to set
     */
    public void setMaxTrustSize(int maxTrustSize) {
        this.maxTrustSize = maxTrustSize;
    }





    /**
     * @return the defaultNewPlayerAction
     */
    @Override
    public String getDefaultNewPlayerAction() {
        return defaultNewPlayerAction;
    }

    /**
     * @param defaultNewPlayerAction the defaultNewPlayerAction to set
     */
    public void setDefaultNewPlayerAction(String defaultNewPlayerAction) {
        this.defaultNewPlayerAction = defaultNewPlayerAction;
    }

    /**
     * @return the defaultPlayerAction
     */
    @Override
    public String getDefaultPlayerAction() {
        return defaultPlayerAction;
    }

    /**
     * @param defaultPlayerAction the defaultPlayerAction to set
     */
    public void setDefaultPlayerAction(String defaultPlayerAction) {
        this.defaultPlayerAction = defaultPlayerAction;
    }

    /**
     * @return the mobLimitSettings
     */
    @Override
    public List<String> getMobLimitSettings() {
        return mobLimitSettings;
    }

    /**
     * @param mobLimitSettings the mobLimitSettings to set
     */
    public void setMobLimitSettings(List<String> mobLimitSettings) {
        this.mobLimitSettings = mobLimitSettings;
    }



    /**
     * @return the defaultNetherBiome
     */
    public Biome getDefaultNetherBiome() {
        return defaultNetherBiome == null ? Biome.NETHER_WASTES : defaultNetherBiome;
    }

    /**
     * @param defaultNetherBiome the defaultNetherBiome to set
     */
    public void setDefaultNetherBiome(Biome defaultNetherBiome) {
        this.defaultNetherBiome = defaultNetherBiome;
    }

    /**
     * @return the defaultEndBiome
     */
    public Biome getDefaultEndBiome() {
        return defaultEndBiome == null ? Biome.THE_END : defaultEndBiome;
    }

    /**
     * @param defaultEndBiome the defaultEndBiome to set
     */
    public void setDefaultEndBiome(Biome defaultEndBiome) {
        this.defaultEndBiome = defaultEndBiome;
    }

    /**
     * @return the makeNetherPortals
     */
    @Override
    public boolean isMakeNetherPortals() {
        return makeNetherPortals;
    }

    /**
     * @return the makeEndPortals
     */
    @Override
    public boolean isMakeEndPortals() {
        return makeEndPortals;
    }

    /**
     * Sets make nether portals.
     * @param makeNetherPortals the make nether portals
     */
    public void setMakeNetherPortals(boolean makeNetherPortals) {
        this.makeNetherPortals = makeNetherPortals;
    }

    /**
     * Sets make end portals.
     * @param makeEndPortals the make end portals
     */
    public void setMakeEndPortals(boolean makeEndPortals) {
        this.makeEndPortals = makeEndPortals;
    }





































    /**
     * @return the disallowTeamMemberIslands
     */
    @Override
    public boolean isDisallowTeamMemberIslands() {
        return disallowTeamMemberIslands;
    }

    /**
     * @param disallowTeamMemberIslands the disallowTeamMemberIslands to set
     */
    public void setDisallowTeamMemberIslands(boolean disallowTeamMemberIslands) {
        this.disallowTeamMemberIslands = disallowTeamMemberIslands;
    }



    /**
     * @return the concurrentIslands
     */
    @Override
    public int getConcurrentIslands() {
        if (concurrentIslands <= 0) {
            return BentoBox.getInstance().getSettings().getIslandNumber();
        }
        return concurrentIslands;
    }

    /**
     * @param concurrentIslands the concurrentIslands to set
     */
    public void setConcurrentIslands(int concurrentIslands) {
        this.concurrentIslands = concurrentIslands;
    }

    // ---------------------------------------------------------------------
    // TradeWinds-specific accessors
    // ---------------------------------------------------------------------

    public long getGalaxySeed() { return galaxySeed; }
    public void setGalaxySeed(long galaxySeed) { this.galaxySeed = galaxySeed; }
    public int getGalaxyMinSeparation() { return galaxyMinSeparation; }
    public void setGalaxyMinSeparation(int galaxyMinSeparation) { this.galaxyMinSeparation = galaxyMinSeparation; }
    public int getStarterClusterRadius() { return starterClusterRadius; }
    public void setStarterClusterRadius(int starterClusterRadius) { this.starterClusterRadius = starterClusterRadius; }
    public int getStarterClusterMinIslands() { return starterClusterMinIslands; }
    public void setStarterClusterMinIslands(int starterClusterMinIslands) { this.starterClusterMinIslands = starterClusterMinIslands; }
    public double getGalaxyDensity() { return galaxyDensity; }
    public void setGalaxyDensity(double galaxyDensity) { this.galaxyDensity = galaxyDensity; }
    public int getIslandTerrainRadius() { return islandTerrainRadius; }
    public void setIslandTerrainRadius(int islandTerrainRadius) { this.islandTerrainRadius = islandTerrainRadius; }
    public int getLandLift() { return landLift; }
    public void setLandLift(int landLift) { this.landLift = landLift; }
    public int getBandRadius() { return bandRadius; }
    public void setBandRadius(int bandRadius) { this.bandRadius = bandRadius; }
    public String getSpawnIslandType() { return spawnIslandType; }
    public void setSpawnIslandType(String spawnIslandType) { this.spawnIslandType = spawnIslandType; }
    public double getWildIsletChance() { return wildIsletChance; }
    public void setWildIsletChance(double wildIsletChance) { this.wildIsletChance = wildIsletChance; }
    public int getWildIsletRadius() { return wildIsletRadius; }
    public void setWildIsletRadius(int wildIsletRadius) { this.wildIsletRadius = wildIsletRadius; }
    public int getWildIsletGrid() { return wildIsletGrid; }
    public void setWildIsletGrid(int wildIsletGrid) { this.wildIsletGrid = wildIsletGrid; }
    public double getMushroomIsletChance() { return mushroomIsletChance; }
    public void setMushroomIsletChance(double mushroomIsletChance) { this.mushroomIsletChance = mushroomIsletChance; }
    public double getIsletStructureChance() { return isletStructureChance; }
    public void setIsletStructureChance(double isletStructureChance) { this.isletStructureChance = isletStructureChance; }
    public double getCoastRoughness() { return coastRoughness; }
    public void setCoastRoughness(double coastRoughness) { this.coastRoughness = coastRoughness; }
    public double getIslandHilliness() { return islandHilliness; }
    public void setIslandHilliness(double islandHilliness) { this.islandHilliness = islandHilliness; }
    public boolean isVarySeabed() { return varySeabed; }
    public void setVarySeabed(boolean varySeabed) { this.varySeabed = varySeabed; }
    public int getSeabedShelfDepth() { return seabedShelfDepth; }
    public void setSeabedShelfDepth(int seabedShelfDepth) { this.seabedShelfDepth = seabedShelfDepth; }
    public int getSeabedAbyssDepth() { return seabedAbyssDepth; }
    public void setSeabedAbyssDepth(int seabedAbyssDepth) { this.seabedAbyssDepth = seabedAbyssDepth; }
    public int getSeabedIslandShelfDepth() { return seabedIslandShelfDepth; }
    public void setSeabedIslandShelfDepth(int d) { this.seabedIslandShelfDepth = d; }
    public int getSeabedRelief() { return seabedRelief; }
    public void setSeabedRelief(int seabedRelief) { this.seabedRelief = seabedRelief; }
    public int getSeabedRiftDepth() { return seabedRiftDepth; }
    public void setSeabedRiftDepth(int seabedRiftDepth) { this.seabedRiftDepth = seabedRiftDepth; }
    public double getSeabedRiftThreshold() { return seabedRiftThreshold; }
    public void setSeabedRiftThreshold(double t) { this.seabedRiftThreshold = t; }
    public int getSeabedSeamountHeight() { return seabedSeamountHeight; }
    public void setSeabedSeamountHeight(int h) { this.seabedSeamountHeight = h; }
    public boolean isKeepStructuresOffIslands() { return keepStructuresOffIslands; }
    public void setKeepStructuresOffIslands(boolean k) { this.keepStructuresOffIslands = k; }
    public Map<String, Integer> getTypeWeights() { return typeWeights; }
    public void setTypeWeights(Map<String, Integer> typeWeights) { this.typeWeights = typeWeights; }
    public double getFuelPerBlock() { return fuelPerBlock; }
    public void setFuelPerBlock(double fuelPerBlock) { this.fuelPerBlock = fuelPerBlock; }
    public double getWarpFailureChance() { return warpFailureChance; }
    public void setWarpFailureChance(double warpFailureChance) { this.warpFailureChance = warpFailureChance; }
    public int getWarpTriggerDistance() { return warpTriggerDistance; }
    public void setWarpTriggerDistance(int warpTriggerDistance) { this.warpTriggerDistance = warpTriggerDistance; }
    public int getWarpArrivalDistance() { return warpArrivalDistance; }
    public void setWarpArrivalDistance(int warpArrivalDistance) { this.warpArrivalDistance = warpArrivalDistance; }
    public int getWarpPromptCooldownSeconds() { return warpPromptCooldownSeconds; }
    public void setWarpPromptCooldownSeconds(int warpPromptCooldownSeconds) { this.warpPromptCooldownSeconds = warpPromptCooldownSeconds; }
    public int getMaxWarpDestinations() { return maxWarpDestinations; }
    public void setMaxWarpDestinations(int maxWarpDestinations) { this.maxWarpDestinations = maxWarpDestinations; }
    public double getWarpEnemyRadius() { return warpEnemyRadius; }
    public void setWarpEnemyRadius(double v) { this.warpEnemyRadius = v; }
    public int getWarpStandStillSeconds() { return warpStandStillSeconds; }
    public void setWarpStandStillSeconds(int warpStandStillSeconds) { this.warpStandStillSeconds = warpStandStillSeconds; }
    public int getWarpNauseaSeconds() { return warpNauseaSeconds; }
    public void setWarpNauseaSeconds(int warpNauseaSeconds) { this.warpNauseaSeconds = warpNauseaSeconds; }
    public int getWarpBlindnessSeconds() { return warpBlindnessSeconds; }
    public void setWarpBlindnessSeconds(int warpBlindnessSeconds) { this.warpBlindnessSeconds = warpBlindnessSeconds; }
    public double getWarpDamage() { return warpDamage; }
    public void setWarpDamage(double warpDamage) { this.warpDamage = warpDamage; }
    public boolean isFuelWarningEnabled() { return fuelWarningEnabled; }
    public void setFuelWarningEnabled(boolean v) { this.fuelWarningEnabled = v; }
    public int getFuelWarningSeconds() { return fuelWarningSeconds; }
    public void setFuelWarningSeconds(int v) { this.fuelWarningSeconds = v; }
    public double getFuelWarningMargin() { return fuelWarningMargin; }
    public void setFuelWarningMargin(double v) { this.fuelWarningMargin = v; }
    public Map<String, Double> getFuelValues() { return fuelValues; }
    public void setFuelValues(Map<String, Double> fuelValues) { this.fuelValues = fuelValues; }
    public Map<String, Double> getEdgeOverrides() { return edgeOverrides; }
    public void setEdgeOverrides(Map<String, Double> edgeOverrides) { this.edgeOverrides = edgeOverrides; }
    public Map<String, Boolean> getBandMonsterSpawn() { return bandMonsterSpawn; }
    public void setBandMonsterSpawn(Map<String, Boolean> bandMonsterSpawn) { this.bandMonsterSpawn = bandMonsterSpawn; }
    public Map<String, Boolean> getBandPvp() { return bandPvp; }
    public void setBandPvp(Map<String, Boolean> bandPvp) { this.bandPvp = bandPvp; }
    public boolean isCrimeEnabled() { return crimeEnabled; }
    public void setCrimeEnabled(boolean crimeEnabled) { this.crimeEnabled = crimeEnabled; }
    public int getReputationFloor() { return reputationFloor; }
    public void setReputationFloor(int v) { this.reputationFloor = v; }
    public int getReputationCeiling() { return reputationCeiling; }
    public void setReputationCeiling(int v) { this.reputationCeiling = v; }
    public int getReputationUpstanding() { return reputationUpstanding; }
    public void setReputationUpstanding(int v) { this.reputationUpstanding = v; }
    public int getReputationOffender() { return reputationOffender; }
    public void setReputationOffender(int v) { this.reputationOffender = v; }
    public int getReputationWanted() { return reputationWanted; }
    public void setReputationWanted(int v) { this.reputationWanted = v; }
    public int getReputationFugitive() { return reputationFugitive; }
    public void setReputationFugitive(int v) { this.reputationFugitive = v; }
    public int getReputationDecayMinutes() { return reputationDecayMinutes; }
    public void setReputationDecayMinutes(int v) { this.reputationDecayMinutes = v; }
    public int getReputationDecayPoints() { return reputationDecayPoints; }
    public void setReputationDecayPoints(int v) { this.reputationDecayPoints = v; }
    public String getSafestFugitiveTrader() { return safestFugitiveTrader; }
    public void setSafestFugitiveTrader(String v) { this.safestFugitiveTrader = v; }
    public boolean isBountyNameplate() { return bountyNameplate; }
    public void setBountyNameplate(boolean v) { this.bountyNameplate = v; }

    /**
     * The safest band that will still trade with a fugitive.
     *
     * @return the band
     */
    public world.bentobox.tradewinds.galaxy.SecurityBand safestFugitiveTrader() {
        try {
            return world.bentobox.tradewinds.galaxy.SecurityBand
                    .valueOf(safestFugitiveTrader.toUpperCase(java.util.Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            return world.bentobox.tradewinds.galaxy.SecurityBand.FRONTIER;
        }
    }

    public double getFinePerPoint() { return finePerPoint; }
    public void setFinePerPoint(double finePerPoint) { this.finePerPoint = finePerPoint; }
    public Map<String, Integer> getCrimePenalties() { return crimePenalties; }
    public void setCrimePenalties(Map<String, Integer> m) { this.crimePenalties = m; }
    public Map<String, Double> getCrimeBounties() { return crimeBounties; }
    public void setCrimeBounties(Map<String, Double> m) { this.crimeBounties = m; }
    public List<String> getPortDeniedFlags() { return portDeniedFlags; }
    public void setPortDeniedFlags(List<String> portDeniedFlags) { this.portDeniedFlags = portDeniedFlags; }
    public List<String> getPortAllowedFlags() { return portAllowedFlags; }
    public void setPortAllowedFlags(List<String> portAllowedFlags) { this.portAllowedFlags = portAllowedFlags; }
    public Map<String, Integer> getBandHurtVillagersRank() { return bandHurtVillagersRank; }
    public void setBandHurtVillagersRank(Map<String, Integer> bandHurtVillagersRank) { this.bandHurtVillagersRank = bandHurtVillagersRank; }
    public int getResidentTetherRadius() { return residentTetherRadius; }
    public void setResidentTetherRadius(int residentTetherRadius) { this.residentTetherRadius = residentTetherRadius; }
    public int getResidentRespawnDelayMinutes() { return residentRespawnDelayMinutes; }
    public void setResidentRespawnDelayMinutes(int residentRespawnDelayMinutes) { this.residentRespawnDelayMinutes = residentRespawnDelayMinutes; }
    public int getResidentAuditPeriodSeconds() { return residentAuditPeriodSeconds; }
    public void setResidentAuditPeriodSeconds(int residentAuditPeriodSeconds) { this.residentAuditPeriodSeconds = residentAuditPeriodSeconds; }
    public double getChartHologramDistance() { return chartHologramDistance; }
    public void setChartHologramDistance(double chartHologramDistance) { this.chartHologramDistance = chartHologramDistance; }
    public int getChartHologramSeconds() { return chartHologramSeconds; }
    public void setChartHologramSeconds(int chartHologramSeconds) { this.chartHologramSeconds = chartHologramSeconds; }
    public int getChartHologramMax() { return chartHologramMax; }
    public void setChartHologramMax(int chartHologramMax) { this.chartHologramMax = chartHologramMax; }
    public int getPortScan() { return portScan; }
    public void setPortScan(int portScan) { this.portScan = portScan; }
    public int getChartSightingRange() { return chartSightingRange; }
    public void setChartSightingRange(int chartSightingRange) { this.chartSightingRange = chartSightingRange; }
    public boolean isChartOnBoarding() { return chartOnBoarding; }
    public void setChartOnBoarding(boolean chartOnBoarding) { this.chartOnBoarding = chartOnBoarding; }
    public int getStarChartBlocksPerPixel() { return starChartBlocksPerPixel; }
    public void setStarChartBlocksPerPixel(int starChartBlocksPerPixel) { this.starChartBlocksPerPixel = starChartBlocksPerPixel; }
    public boolean isNavigationBossbar() { return navigationBossbar; }
    public void setNavigationBossbar(boolean navigationBossbar) { this.navigationBossbar = navigationBossbar; }
    public double getStartingBalance() { return startingBalance; }
    public void setStartingBalance(double startingBalance) { this.startingBalance = startingBalance; }
    public int getStarterCoal() { return starterCoal; }
    public void setStarterCoal(int starterCoal) { this.starterCoal = starterCoal; }
    public double getBuySpread() { return buySpread; }
    public void setBuySpread(double buySpread) { this.buySpread = buySpread; }
    public double getSellSpread() { return sellSpread; }
    public void setSellSpread(double sellSpread) { this.sellSpread = sellSpread; }
    public double getProduceFactor() { return produceFactor; }
    public void setProduceFactor(double produceFactor) { this.produceFactor = produceFactor; }
    public double getDemandFactor() { return demandFactor; }
    public void setDemandFactor(double demandFactor) { this.demandFactor = demandFactor; }
    public double getBandDemandBonus() { return bandDemandBonus; }
    public void setBandDemandBonus(double bandDemandBonus) { this.bandDemandBonus = bandDemandBonus; }
    public double getTechPriceStep() { return techPriceStep; }
    public void setTechPriceStep(double techPriceStep) { this.techPriceStep = techPriceStep; }
    public Map<String, Integer> getBoatRanks() { return boatRanks; }
    public void setBoatRanks(Map<String, Integer> boatRanks) { this.boatRanks = boatRanks; }
    public double getBoatPricePerSlotSquared() { return boatPricePerSlotSquared; }
    public void setBoatPricePerSlotSquared(double boatPricePerSlotSquared) { this.boatPricePerSlotSquared = boatPricePerSlotSquared; }
    public int getBoatRanksPerTechLevel() { return boatRanksPerTechLevel; }
    public void setBoatRanksPerTechLevel(int boatRanksPerTechLevel) { this.boatRanksPerTechLevel = boatRanksPerTechLevel; }
    public boolean isBorderParticlesEnabled() { return borderParticlesEnabled; }
    public void setBorderParticlesEnabled(boolean borderParticlesEnabled) { this.borderParticlesEnabled = borderParticlesEnabled; }
    public int getBorderViewDistance() { return borderViewDistance; }
    public void setBorderViewDistance(int borderViewDistance) { this.borderViewDistance = borderViewDistance; }
    public String getWarpRingColor() { return warpRingColor; }
    public void setWarpRingColor(String warpRingColor) { this.warpRingColor = warpRingColor; }
    public String getEdgeRingColor() { return edgeRingColor; }
    public void setEdgeRingColor(String edgeRingColor) { this.edgeRingColor = edgeRingColor; }
    public int getCargoTransferRange() { return cargoTransferRange; }
    public void setCargoTransferRange(int cargoTransferRange) { this.cargoTransferRange = cargoTransferRange; }
    public String getRespawnBoat() { return respawnBoat; }
    public void setRespawnBoat(String respawnBoat) { this.respawnBoat = respawnBoat; }
    public int getDroppedBoatTtlMinutes() { return droppedBoatTtlMinutes; }
    public void setDroppedBoatTtlMinutes(int droppedBoatTtlMinutes) { this.droppedBoatTtlMinutes = droppedBoatTtlMinutes; }
    public int getDriftValueScale() { return driftValueScale; }
    public void setDriftValueScale(int driftValueScale) { this.driftValueScale = driftValueScale; }
    public double getDriftMin() { return driftMin; }
    public void setDriftMin(double driftMin) { this.driftMin = driftMin; }
    public double getDriftMax() { return driftMax; }
    public void setDriftMax(double driftMax) { this.driftMax = driftMax; }
    public int getStockDecayValuePerHour() { return stockDecayValuePerHour; }
    public void setStockDecayValuePerHour(int stockDecayValuePerHour) { this.stockDecayValuePerHour = stockDecayValuePerHour; }
    public double getExpanderBasePrice() { return expanderBasePrice; }
    public void setExpanderBasePrice(double expanderBasePrice) { this.expanderBasePrice = expanderBasePrice; }
    public int getCharityCooldownMinutes() { return charityCooldownMinutes; }
    public void setCharityCooldownMinutes(int charityCooldownMinutes) { this.charityCooldownMinutes = charityCooldownMinutes; }
    public List<String> getContrabandMaterials() { return contrabandMaterials; }
    public void setContrabandMaterials(List<String> contrabandMaterials) { this.contrabandMaterials = contrabandMaterials; }
    public int getMaxRestarts() { return maxRestarts; }
    public void setMaxRestarts(int maxRestarts) { this.maxRestarts = maxRestarts; }
    public boolean isResaleEnabled() { return resaleEnabled; }
    public void setResaleEnabled(boolean resaleEnabled) { this.resaleEnabled = resaleEnabled; }
    public double getResaleNotableValue() { return resaleNotableValue; }
    public void setResaleNotableValue(double v) { this.resaleNotableValue = v; }
    public int getResaleSlots() { return resaleSlots; }
    public void setResaleSlots(int resaleSlots) { this.resaleSlots = resaleSlots; }
    public int getResaleTtlHours() { return resaleTtlHours; }
    public void setResaleTtlHours(int resaleTtlHours) { this.resaleTtlHours = resaleTtlHours; }
    public double getResaleMarkup() { return resaleMarkup; }
    public void setResaleMarkup(double resaleMarkup) { this.resaleMarkup = resaleMarkup; }
    public int getResaleShipRadius() { return resaleShipRadius; }
    public void setResaleShipRadius(int resaleShipRadius) { this.resaleShipRadius = resaleShipRadius; }
    public double getMarketReportPricePerIsland() { return marketReportPricePerIsland; }
    public void setMarketReportPricePerIsland(double v) { this.marketReportPricePerIsland = v; }
    public double getMarketReportRadiusPerTechLevel() { return marketReportRadiusPerTechLevel; }
    public void setMarketReportRadiusPerTechLevel(double v) { this.marketReportRadiusPerTechLevel = v; }
    public double getEnchantmentPriceFactor() { return enchantmentPriceFactor; }
    public void setEnchantmentPriceFactor(double v) { this.enchantmentPriceFactor = v; }
    public double getPotionEffectPrice() { return potionEffectPrice; }
    public void setPotionEffectPrice(double v) { this.potionEffectPrice = v; }
    public double getSalvageValuePerTechLevel() { return salvageValuePerTechLevel; }
    public void setSalvageValuePerTechLevel(double v) { this.salvageValuePerTechLevel = v; }
    public double getSalvageDiscount() { return salvageDiscount; }
    public void setSalvageDiscount(double salvageDiscount) { this.salvageDiscount = salvageDiscount; }
    public Map<String, Double> getBasePrices() { return basePrices; }
    public void setBasePrices(Map<String, Double> basePrices) { this.basePrices = basePrices; }
    public double getIntersticeGhastChance() { return intersticeGhastChance; }
    public void setIntersticeGhastChance(double v) { this.intersticeGhastChance = v; }
    public double getIntersticeGhastDistance() { return intersticeGhastDistance; }
    public void setIntersticeGhastDistance(double v) { this.intersticeGhastDistance = v; }
    public int getIntersticeCeilingHeight() { return intersticeCeilingHeight; }
    public void setIntersticeCeilingHeight(int v) { this.intersticeCeilingHeight = v; }
    public double getIntersticeBrazierChance() { return intersticeBrazierChance; }
    public void setIntersticeBrazierChance(double v) { this.intersticeBrazierChance = v; }
    public int getIntersticeGraceSeconds() { return intersticeGraceSeconds; }
    public void setIntersticeGraceSeconds(int v) { this.intersticeGraceSeconds = v; }
    public int getIntersticeGhastsMin() { return intersticeGhastsMin; }
    public void setIntersticeGhastsMin(int intersticeGhastsMin) { this.intersticeGhastsMin = intersticeGhastsMin; }
    public int getIntersticeGhastsMax() { return intersticeGhastsMax; }
    public void setIntersticeGhastsMax(int intersticeGhastsMax) { this.intersticeGhastsMax = intersticeGhastsMax; }
    public int getIntersticePromptSeconds() { return intersticePromptSeconds; }
    public void setIntersticePromptSeconds(int intersticePromptSeconds) { this.intersticePromptSeconds = intersticePromptSeconds; }
    public boolean isEncountersEnabled() { return encountersEnabled; }
    public void setEncountersEnabled(boolean encountersEnabled) { this.encountersEnabled = encountersEnabled; }
    public int getEncounterCheckSeconds() { return encounterCheckSeconds; }
    public void setEncounterCheckSeconds(int encounterCheckSeconds) { this.encounterCheckSeconds = encounterCheckSeconds; }
    public int getEncounterDistance() { return encounterDistance; }
    public void setEncounterDistance(int encounterDistance) { this.encounterDistance = encounterDistance; }
    public Map<String, Double> getEncounterChance() { return encounterChance; }
    public void setEncounterChance(Map<String, Double> encounterChance) { this.encounterChance = encounterChance; }
    public double getDrownedTridentChance() { return drownedTridentChance; }
    public void setDrownedTridentChance(double drownedTridentChance) { this.drownedTridentChance = drownedTridentChance; }
    public double getBootyChance() { return bootyChance; }
    public void setBootyChance(double bootyChance) { this.bootyChance = bootyChance; }
    public List<String> getBootyTable() { return bootyTable; }
    public void setBootyTable(List<String> bootyTable) { this.bootyTable = bootyTable; }
    public String getSafestContrabandBuyer() { return safestContrabandBuyer; }
    public void setSafestContrabandBuyer(String v) { this.safestContrabandBuyer = v; }
    public double getContrabandPriceMultiplier() { return contrabandPriceMultiplier; }
    public void setContrabandPriceMultiplier(double v) { this.contrabandPriceMultiplier = v; }
    public Map<String, Double> getScanChance() { return scanChance; }
    public void setScanChance(Map<String, Double> v) { this.scanChance = v; }
    public double getScanUpstandingFactor() { return scanUpstandingFactor; }
    public void setScanUpstandingFactor(double v) { this.scanUpstandingFactor = v; }
    public double getScanOffenderFactor() { return scanOffenderFactor; }
    public void setScanOffenderFactor(double v) { this.scanOffenderFactor = v; }
    public int getScanCooldownMinutes() { return scanCooldownMinutes; }
    public void setScanCooldownMinutes(int v) { this.scanCooldownMinutes = v; }
    public int getFleeFlagMinutes() { return fleeFlagMinutes; }
    public void setFleeFlagMinutes(int v) { this.fleeFlagMinutes = v; }
    public Map<String, Integer> getPatrolSize() { return patrolSize; }
    public void setPatrolSize(Map<String, Integer> v) { this.patrolSize = v; }
    public double getPatrolDistance() { return patrolDistance; }
    public void setPatrolDistance(double v) { this.patrolDistance = v; }
    public double getCaughtRadius() { return caughtRadius; }
    public void setCaughtRadius(double v) { this.caughtRadius = v; }
    public int getChaseBreakOffDistance() { return chaseBreakOffDistance; }
    public void setChaseBreakOffDistance(int v) { this.chaseBreakOffDistance = v; }
    public int getChaseSeconds() { return chaseSeconds; }
    public void setChaseSeconds(int v) { this.chaseSeconds = v; }
    public double getSmugglingFinePerItem() { return smugglingFinePerItem; }
    public void setSmugglingFinePerItem(double v) { this.smugglingFinePerItem = v; }
    public boolean isAnnounceCleanScans() { return announceCleanScans; }
    public void setAnnounceCleanScans(boolean v) { this.announceCleanScans = v; }
    public boolean isIllegalTradeEnabled() { return illegalTradeEnabled; }
    public void setIllegalTradeEnabled(boolean illegalTradeEnabled) { this.illegalTradeEnabled = illegalTradeEnabled; }

    public int getSeaFloor() { return seaFloor; }
    public void setSeaFloor(int seaFloor) { this.seaFloor = seaFloor; }
    public Material getWaterBlock() { return waterBlock == null ? Material.WATER : waterBlock; }
    public void setWaterBlock(Material waterBlock) { this.waterBlock = waterBlock; }
    public boolean isMakeCaves() { return makeCaves; }
    public void setMakeCaves(boolean makeCaves) { this.makeCaves = makeCaves; }
    public boolean isMakeDecorations() { return makeDecorations; }
    public void setMakeDecorations(boolean makeDecorations) { this.makeDecorations = makeDecorations; }
    public boolean isMakeStructures() { return makeStructures; }
    public void setMakeStructures(boolean makeStructures) { this.makeStructures = makeStructures; }
    public int getIntersticeSeaHeight() { return intersticeSeaHeight; }
    public void setIntersticeSeaHeight(int intersticeSeaHeight) { this.intersticeSeaHeight = intersticeSeaHeight; }
    public int getIntersticeSeaFloor() { return intersticeSeaFloor; }
    public void setIntersticeSeaFloor(int intersticeSeaFloor) { this.intersticeSeaFloor = intersticeSeaFloor; }
    public Material getIntersticeWaterBlock() { return intersticeWaterBlock == null ? Material.WATER : intersticeWaterBlock; }
    public void setIntersticeWaterBlock(Material intersticeWaterBlock) { this.intersticeWaterBlock = intersticeWaterBlock; }
    public boolean isVaryOceanBiomes() { return varyOceanBiomes; }
    public void setVaryOceanBiomes(boolean varyOceanBiomes) { this.varyOceanBiomes = varyOceanBiomes; }
    public Biome getDefaultAirBiome() { return defaultAirBiome == null ? Biome.OCEAN : defaultAirBiome; }
    public void setDefaultAirBiome(Biome defaultAirBiome) { this.defaultAirBiome = defaultAirBiome; }

    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }
}
