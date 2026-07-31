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
    @ConfigComment("TradeWinds players do not start with an island, so the default is 'spawn'.")
    @ConfigEntry(path = "tradewinds.command.new-player-action")
    private String defaultNewPlayerAction = "spawn";

    @ConfigComment("The default action for player command.")
    @ConfigComment("Sub-command of main player command that will be run on each player command call.")
    @ConfigComment("TradeWinds players do not start with an island, so the default is 'spawn'.")
    @ConfigEntry(path = "tradewinds.command.default-action")
    private String defaultPlayerAction = "spawn";

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

    @ConfigComment("Radius of the safe spawn islet generated at the world origin - players spawn")
    @ConfigComment("and respawn there. 0 disables it (not recommended: seabed respawns kill).")
    @ConfigEntry(path = "galaxy.spawn-islet-radius", needsReset = true)
    private int spawnIsletRadius = 48;

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
    @ConfigComment("edge, where 'Now leaving...' appears): boated players crossing it get the warp dialog.")
    @ConfigEntry(path = "travel.warp.trigger-distance")
    private int warpTriggerDistance = 30;

    @ConfigComment("How far from the destination island's CENTER a warp arrival lands. Default 130:")
    @ConfigComment("inside the default view distance, so you materialize seeing your destination.")
    @ConfigEntry(path = "travel.warp.arrival-distance")
    private int warpArrivalDistance = 130;

    @ConfigComment("Seconds between automatic warp-dialog offers at the same island's border.")
    @ConfigEntry(path = "travel.warp.prompt-cooldown-seconds")
    private int warpPromptCooldownSeconds = 30;

    @ConfigComment("Maximum destinations listed in the warp dialog (nearest first).")
    @ConfigComment("8 fits the dialog without scrolling - scrolling is easy to miss.")
    @ConfigEntry(path = "travel.warp.max-destinations")
    private int maxWarpDestinations = 8;

    @ConfigComment("Seconds of nausea after a warp. Warping hurts - it gates the under-equipped.")
    @ConfigEntry(path = "travel.warp.nausea-seconds")
    private int warpNauseaSeconds = 8;

    @ConfigComment("Seconds of blindness after a warp.")
    @ConfigEntry(path = "travel.warp.blindness-seconds")
    private int warpBlindnessSeconds = 3;

    @ConfigComment("Damage (in half-hearts) taken on warp arrival. 0 disables.")
    @ConfigEntry(path = "travel.warp.damage")
    private double warpDamage = 2.0;

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
    private double startingBalance = 250.0;

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

    @ConfigComment("Stock units for a full price swing. Selling this much of a category to one")
    @ConfigComment("island drives its prices to the drift minimum.")
    @ConfigEntry(path = "economy.drift-scale")
    private int driftScale = 500;

    @ConfigComment("Lower clamp of the stock drift price factor.")
    @ConfigEntry(path = "economy.drift-min")
    private double driftMin = 0.7;

    @ConfigComment("Upper clamp of the stock drift price factor.")
    @ConfigEntry(path = "economy.drift-max")
    private double driftMax = 1.3;

    @ConfigComment("Stock units that decay back toward equilibrium per hour - markets recover.")
    @ConfigEntry(path = "economy.stock-decay-per-hour")
    private int stockDecayPerHour = 50;

    @ConfigComment("Base price of the first cargo expander. Each further one costs double.")
    @ConfigEntry(path = "economy.expander-base-price")
    private double expanderBasePrice = 5000.0;

    @ConfigComment("Maximum cargo expanders a player may ever buy.")
    @ConfigEntry(path = "economy.expander-cap")
    private int expanderCap = 4;

    @ConfigComment("Maximum trading bundles that count as hold space.")
    @ConfigEntry(path = "economy.max-bundles")
    private int maxBundles = 3;

    @ConfigComment("Base prices (Material -> price). Anything not listed is priced by deriving")
    @ConfigComment("from its crafting recipe (BlueBook logic, embedded); underivable = untradeable.")
    @ConfigEntry(path = "economy.base-prices")
    private Map<String, Double> basePrices = defaultBasePrices();

    private static Map<String, Double> defaultBasePrices() {
        Map<String, Double> map = new HashMap<>();
        map.put("WHEAT", 2.0); map.put("CARROT", 1.5); map.put("POTATO", 1.5); map.put("BEETROOT", 1.5);
        map.put("SUGAR_CANE", 1.0); map.put("SUGAR", 1.5); map.put("PUMPKIN", 3.0); map.put("MELON_SLICE", 0.5);
        map.put("BREAD", 3.0); map.put("COOKED_BEEF", 4.0); map.put("COOKED_COD", 3.0); map.put("CAKE", 20.0);
        map.put("GOLDEN_APPLE", 150.0); map.put("EGG", 1.0); map.put("HAY_BLOCK", 18.0);
        map.put("COD", 2.0); map.put("SALMON", 3.0); map.put("TROPICAL_FISH", 5.0); map.put("PUFFERFISH", 4.0);
        map.put("KELP", 0.3);
        map.put("OAK_LOG", 1.5); map.put("SPRUCE_LOG", 1.5); map.put("BIRCH_LOG", 1.5); map.put("DARK_OAK_LOG", 1.5);
        map.put("ACACIA_LOG", 1.5); map.put("JUNGLE_LOG", 1.5); map.put("CHERRY_LOG", 2.0);
        map.put("STONE", 0.5); map.put("COBBLESTONE", 0.3); map.put("GRANITE", 0.4); map.put("DIORITE", 0.4);
        map.put("ANDESITE", 0.4); map.put("DEEPSLATE", 0.6); map.put("SAND", 0.3); map.put("GRAVEL", 0.3);
        map.put("COAL", 4.0); map.put("CHARCOAL", 3.0); map.put("RAW_IRON", 6.0); map.put("RAW_COPPER", 3.0);
        map.put("RAW_GOLD", 12.0); map.put("FLINT", 1.0);
        map.put("IRON_INGOT", 9.0); map.put("COPPER_INGOT", 4.0); map.put("GOLD_INGOT", 18.0);
        map.put("IRON_NUGGET", 1.0); map.put("GOLD_NUGGET", 2.0);
        map.put("DIAMOND", 100.0); map.put("EMERALD", 60.0); map.put("AMETHYST_SHARD", 10.0);
        map.put("QUARTZ", 8.0); map.put("LAPIS_LAZULI", 6.0); map.put("REDSTONE", 3.0);
        map.put("LEATHER", 4.0); map.put("WHITE_WOOL", 2.0); map.put("STRING", 1.5);
        map.put("BEEF", 2.5); map.put("PORKCHOP", 2.5); map.put("CHICKEN", 2.0); map.put("MUTTON", 2.0);
        return map;
    }

    /*      ILLEGAL TRADE      */
    @ConfigComment("Master gate for all illegal-goods mechanics: contraband, customs scans, smuggling.")
    @ConfigComment("Set false for family-friendly servers - removes the entire crime layer cleanly.")
    @ConfigEntry(path = "illegal-trade.enabled")
    private boolean illegalTradeEnabled = true;

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

    @ConfigComment("Allow vanilla cave generation under the ocean floor.")
    @ConfigEntry(path = "world.make-caves")
    private boolean makeCaves = false;

    @ConfigComment("Allow vanilla decoration (kelp, seagrass, trees on island land).")
    @ConfigEntry(path = "world.make-decorations")
    private boolean makeDecorations = true;

    @ConfigComment("Allow vanilla structure generation (shipwrecks, ruins...).")
    @ConfigEntry(path = "world.make-structures")
    private boolean makeStructures = false;

    @ConfigComment("Maximum number of islands in the world. Set to -1 or 0 for unlimited.")
    @ConfigComment("If the number of islands is greater than this number, it will stop players from creating islands.")
    @ConfigEntry(path = "world.max-islands")
    private int maxIslands = -1;

    @ConfigComment("The default game mode for this world. Players will be set to this mode when they create")
    @ConfigComment("a new island for example. Options are SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR")
    @ConfigEntry(path = "world.default-game-mode")
    private GameMode defaultGameMode = GameMode.SURVIVAL;

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
    public int getSpawnIsletRadius() { return spawnIsletRadius; }
    public void setSpawnIsletRadius(int spawnIsletRadius) { this.spawnIsletRadius = spawnIsletRadius; }
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
    public int getWarpNauseaSeconds() { return warpNauseaSeconds; }
    public void setWarpNauseaSeconds(int warpNauseaSeconds) { this.warpNauseaSeconds = warpNauseaSeconds; }
    public int getWarpBlindnessSeconds() { return warpBlindnessSeconds; }
    public void setWarpBlindnessSeconds(int warpBlindnessSeconds) { this.warpBlindnessSeconds = warpBlindnessSeconds; }
    public double getWarpDamage() { return warpDamage; }
    public void setWarpDamage(double warpDamage) { this.warpDamage = warpDamage; }
    public Map<String, Double> getFuelValues() { return fuelValues; }
    public void setFuelValues(Map<String, Double> fuelValues) { this.fuelValues = fuelValues; }
    public Map<String, Double> getEdgeOverrides() { return edgeOverrides; }
    public void setEdgeOverrides(Map<String, Double> edgeOverrides) { this.edgeOverrides = edgeOverrides; }
    public Map<String, Boolean> getBandMonsterSpawn() { return bandMonsterSpawn; }
    public void setBandMonsterSpawn(Map<String, Boolean> bandMonsterSpawn) { this.bandMonsterSpawn = bandMonsterSpawn; }
    public Map<String, Boolean> getBandPvp() { return bandPvp; }
    public void setBandPvp(Map<String, Boolean> bandPvp) { this.bandPvp = bandPvp; }
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
    public int getStarChartBlocksPerPixel() { return starChartBlocksPerPixel; }
    public void setStarChartBlocksPerPixel(int starChartBlocksPerPixel) { this.starChartBlocksPerPixel = starChartBlocksPerPixel; }
    public boolean isNavigationBossbar() { return navigationBossbar; }
    public void setNavigationBossbar(boolean navigationBossbar) { this.navigationBossbar = navigationBossbar; }
    public double getStartingBalance() { return startingBalance; }
    public void setStartingBalance(double startingBalance) { this.startingBalance = startingBalance; }
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
    public int getDriftScale() { return driftScale; }
    public void setDriftScale(int driftScale) { this.driftScale = driftScale; }
    public double getDriftMin() { return driftMin; }
    public void setDriftMin(double driftMin) { this.driftMin = driftMin; }
    public double getDriftMax() { return driftMax; }
    public void setDriftMax(double driftMax) { this.driftMax = driftMax; }
    public int getStockDecayPerHour() { return stockDecayPerHour; }
    public void setStockDecayPerHour(int stockDecayPerHour) { this.stockDecayPerHour = stockDecayPerHour; }
    public double getExpanderBasePrice() { return expanderBasePrice; }
    public void setExpanderBasePrice(double expanderBasePrice) { this.expanderBasePrice = expanderBasePrice; }
    public int getExpanderCap() { return expanderCap; }
    public void setExpanderCap(int expanderCap) { this.expanderCap = expanderCap; }
    public int getMaxBundles() { return maxBundles; }
    public void setMaxBundles(int maxBundles) { this.maxBundles = maxBundles; }
    public Map<String, Double> getBasePrices() { return basePrices; }
    public void setBasePrices(Map<String, Double> basePrices) { this.basePrices = basePrices; }
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
    public Biome getDefaultAirBiome() { return defaultAirBiome == null ? Biome.OCEAN : defaultAirBiome; }
    public void setDefaultAirBiome(Biome defaultAirBiome) { this.defaultAirBiome = defaultAirBiome; }

    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }
}
