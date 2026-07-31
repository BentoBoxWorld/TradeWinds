package world.bentobox.tradewinds;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.commands.admin.DefaultAdminCommand;
import world.bentobox.bentobox.api.commands.island.DefaultPlayerCommand;
import world.bentobox.bentobox.api.commands.island.IslandInfoCommand;
import world.bentobox.bentobox.api.commands.island.IslandLanguageCommand;
import world.bentobox.bentobox.api.commands.island.IslandSettingsCommand;
import world.bentobox.bentobox.api.configuration.Config;
import world.bentobox.bentobox.api.configuration.WorldSettings;
import world.bentobox.bentobox.lists.Flags;
import world.bentobox.tradewinds.commands.AdminIslandsCommand;
import world.bentobox.tradewinds.commands.AdminReflagCommand;
import world.bentobox.tradewinds.commands.AdminTpIslandCommand;
import world.bentobox.tradewinds.commands.TWChartCommand;
import world.bentobox.tradewinds.commands.TWRestartCommand;
import world.bentobox.tradewinds.commands.TWSpawnCommand;
import world.bentobox.tradewinds.commands.TWStarChartCommand;
import world.bentobox.tradewinds.commands.TWTradeCommand;
import world.bentobox.tradewinds.commands.TWWarpCommand;
import world.bentobox.tradewinds.dataobjects.IslandDataManager;
import world.bentobox.tradewinds.dataobjects.PlayerDataManager;
import world.bentobox.tradewinds.economy.MarketService;
import world.bentobox.tradewinds.encounters.EncounterListener;
import world.bentobox.tradewinds.encounters.EncounterService;
import world.bentobox.tradewinds.economy.TradeDialog;
import world.bentobox.tradewinds.economy.TradeListener;
import world.bentobox.tradewinds.galaxy.RouteGraph;
import world.bentobox.tradewinds.listeners.IntersticePortalListener;
import world.bentobox.tradewinds.listeners.ResidentProtectionListener;
import world.bentobox.tradewinds.tasks.NavigationBarTask;
import world.bentobox.tradewinds.tasks.ResidentAuditTask;
import world.bentobox.tradewinds.travel.BoatPickupListener;
import world.bentobox.tradewinds.travel.BorderPromptListener;
import world.bentobox.tradewinds.travel.ChartHolograms;
import world.bentobox.tradewinds.travel.ChartingListener;
import world.bentobox.tradewinds.travel.FuelService;
import world.bentobox.tradewinds.travel.IntersticeService;
import world.bentobox.tradewinds.travel.HoldService;
import world.bentobox.tradewinds.travel.StarChartService;
import world.bentobox.tradewinds.travel.StarterKit;
import world.bentobox.tradewinds.travel.WarpService;
import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandType;
import world.bentobox.tradewinds.generator.ChunkGeneratorWorld;
import world.bentobox.tradewinds.generator.GalaxyIslandRegistrar;
import world.bentobox.tradewinds.generator.TradeWindsBiomeProvider;

/**
 * TradeWinds - a sea-trading game mode: an endless procedurally generated ocean
 * of NPC trading islands. Buy low, sell high, smuggle, hunt bounties, turn pirate.
 * <p>
 * The gamemode's "nether" world is the <b>interstice</b>: the hostile sea that
 * failed warps drop players into. It has no portals and is unreachable until the
 * warp system (Stage 5) sends players there.
 *
 * @author tastybento
 */
public class TradeWinds extends GameModeAddon {

    private static final String NETHER = "_nether";

    private @Nullable Settings settings;
    private @Nullable ChunkGenerator chunkGenerator;
    private final Config<Settings> configObject = new Config<>(this, Settings.class);
    private BiomeProvider biomeProvider;
    private @Nullable GalaxyEngine galaxyEngine;
    private PlayerDataManager playerDataManager;
    private FuelService fuelService;
    private WarpService warpService;
    private RouteGraph routeGraph;
    private StarterKit starterKit;
    private HoldService holdService;
    private IslandDataManager islandDataManager;
    private MarketService marketService;
    private TradeDialog tradeDialog;
    private ChartHolograms chartHolograms;
    private StarChartService starChartService;
    private IntersticeService intersticeService;
    private @Nullable EncounterService encounterService;
    private @Nullable ResidentAuditTask residentAuditTask;
    private @Nullable NavigationBarTask navigationBarTask;

    /**
     * This addon uses the new chunk generation API for the sea bottom
     */
    @Override
    public boolean isUsesNewChunkGeneration() {
        return true;
    }

    /**
     * TradeWinds islands live at arbitrary seeded positions - never realign
     * them to the grid (Stranger Realms pattern).
     */
    @Override
    public boolean isFixIslandCenter() {
        return false;
    }

    /**
     * Island ranges vary here: trading islands use the full distance, the
     * spawn island is small, and Stage 7 claims will size to their islet
     * (Stranger Realms pattern).
     */
    @Override
    public boolean isEnforceEqualRanges() {
        return false;
    }

    @Override
    public void onLoad() {
        // Save the default config from config.yml
        saveDefaultConfig();
        // Load settings from config.yml. This will check if there are any issues with it too.
        if (!loadSettings()) {
            // Settings did not load - the addon has been disabled
            return;
        }
        // Make the biome provider
        this.biomeProvider = new TradeWindsBiomeProvider(this);
        // Chunk generator
        chunkGenerator = settings.isUseOwnGenerator() ? null : new ChunkGeneratorWorld(this);
        // Register commands. TradeWinds players own no island until Stage 7, so
        // the default create/reset/team/home commands are deliberately absent:
        // player islands are purchased, never free.
        playerCommand = new DefaultPlayerCommand(this) {
            @Override
            public void setup() {
                setDescription("tradewinds.commands.help.description");
                setOnlyPlayer(true);
                setPermission("island");
                new TWSpawnCommand(this);
                new TWWarpCommand(this);
                new TWChartCommand(this);
                new TWStarChartCommand(this);
                new TWTradeCommand(this);
                new TWRestartCommand(this);
                new IslandInfoCommand(this);
                new IslandSettingsCommand(this);
                new IslandLanguageCommand(this);
            }
        };
        adminCommand = new DefaultAdminCommand(this) {
            @Override
            public void setup() {
                super.setup();
                new AdminIslandsCommand(this);
                new AdminTpIslandCommand(this);
                new AdminReflagCommand(this);
            }
        };
    }

    private boolean loadSettings() {
        settings = configObject.loadConfigObject();
        if (settings == null) {
            // Woops
            this.logError("TradeWinds settings could not load! Addon disabled.");
            this.setState(State.DISABLED);
            return false;
        }
        return true;
    }

    @Override
    public void onEnable() {
        if (settings == null) {
            return;
        }
        // Boats are the whole game - visitors must always be able to use them
        Flags.BOAT.setDefaultSetting(islandWorld, true);
        if (netherWorld != null) {
            Flags.BOAT.setDefaultSetting(netherWorld, true);
        }
        // Island names are announced on entry ("Now entering [name]")
        Flags.ENTER_EXIT_MESSAGES.setDefaultSetting(islandWorld, true);
        // Markets must survive: creeper explosions never break blocks anywhere
        // in TradeWinds (no lure-bombing the stalls or landmarks). GRIEFING
        // stays true because false would cancel the whole explosion for
        // non-members - and everyone is a non-member on a trading island;
        // creepers killing players is legitimate (and, later, piracy).
        Flags.CREEPER_DAMAGE.setDefaultSetting(islandWorld, false);
        Flags.CREEPER_GRIEFING.setDefaultSetting(islandWorld, true);
        if (netherWorld != null) {
            Flags.CREEPER_DAMAGE.setDefaultSetting(netherWorld, false);
            Flags.CREEPER_GRIEFING.setDefaultSetting(netherWorld, true);
        }
        // The open ocean and wild islets are free country: every protection
        // flag defaults to allowed OUTSIDE island protection ranges. Trading
        // islands keep their own band flags; player islands (Stage 7) theirs.
        getPlugin().getFlagsManager().getFlags().stream()
                .filter(flag -> flag.getType() == world.bentobox.bentobox.api.flags.Flag.Type.PROTECTION)
                .forEach(flag -> {
                    flag.setDefaultSetting(islandWorld, true);
                    if (netherWorld != null) {
                        flag.setDefaultSetting(netherWorld, true);
                    }
                });
        // Register trading islands lazily as their center chunks first load
        registerListener(new GalaxyIslandRegistrar(this));
        // Seal both worlds against portals - the interstice is warp-failure-only
        registerListener(new IntersticePortalListener(this));
        // Travel: charting, fuel, warp
        playerDataManager = new PlayerDataManager(this);
        fuelService = new FuelService(this);
        routeGraph = new RouteGraph(getSettings().getFuelPerBlock(), getSettings().getEdgeOverrides());
        warpService = new WarpService(this);
        starterKit = new StarterKit(this);
        // Economy: hold, market data, prices, trade dialogs
        holdService = new HoldService(this);
        islandDataManager = new IslandDataManager(this);
        marketService = new MarketService(this);
        tradeDialog = new TradeDialog(this);
        registerListener(new TradeListener(this));
        chartHolograms = new ChartHolograms(this);
        starChartService = new StarChartService(this);
        registerListener(starChartService);
        registerListener(new ChartingListener(this));
        registerListener(new BorderPromptListener(this));
        // Teleporting while boated brings the boat (and cargo) along
        registerListener(new BoatPickupListener(this));
        // Risk at sea: the interstice for warpers, encounters for rowers
        intersticeService = new IntersticeService(this);
        intersticeService.start();
        encounterService = new EncounterService(this);
        encounterService.start();
        registerListener(new EncounterListener(this));
        // Residents survive the night: no mob targeting, tether, respawn
        registerListener(new ResidentProtectionListener());
        residentAuditTask = new ResidentAuditTask(this);
        residentAuditTask.start();
        // Navigation boss bar: island, standing, distance to dock
        navigationBarTask = new NavigationBarTask(this);
        navigationBarTask.start();
        // Spawn (and bed-less respawn) is on the spawn islet's surface
        registerListener(new world.bentobox.tradewinds.listeners.SpawnRespawnListener(this));
        if (islandWorld != null) {
            int top = islandWorld.getHighestBlockYAt(0, 0);
            islandWorld.setSpawnLocation(0, Math.max(top + 1, getSettings().getSeaHeight() + 1), 0);
            bootstrapSpawnIsland(top);
        }
    }

    @Override
    public void onDisable() {
        if (residentAuditTask != null) {
            residentAuditTask.stop();
        }
        if (navigationBarTask != null) {
            navigationBarTask.stop();
        }
        if (intersticeService != null) {
            intersticeService.stop();
        }
        if (encounterService != null) {
            encounterService.stop();
        }
        if (chartHolograms != null) {
            chartHolograms.clearAll();
        }
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        if (islandDataManager != null) {
            islandDataManager.saveAll();
        }
    }

    @NonNull
    public Settings getSettings() {
        return Objects.requireNonNull(settings);
    }

    @Override
    public void createWorlds() {
        String worldName = getSettings().getWorldName().toLowerCase();
        if (getServer().getWorld(worldName) == null) {
            log("Creating TradeWinds ocean...");
        }
        // Create the world if it does not exist
        chunkGenerator = new ChunkGeneratorWorld(this);
        islandWorld = getWorld(worldName, Environment.NORMAL);
        // Make the interstice if it does not exist
        if (getSettings().isNetherGenerate()) {
            if (getServer().getWorld(worldName + NETHER) == null) {
                log("Creating TradeWinds interstice...");
            }
            netherWorld = getWorld(worldName, Environment.NETHER);
        }
        // No End world, ever: shulker shells must stay unobtainable so cargo
        // expanders remain a purchase-only money sink (spec principle 7).
    }

    /**
     * Gets a world or generates a new world if it does not exist
     * @param worldBaseName - the overworld name
     * @param env - the environment
     * @return world loaded or generated
     */
    private World getWorld(String worldBaseName, Environment env) {
        String name = env.equals(Environment.NETHER) ? worldBaseName + NETHER : worldBaseName;
        WorldCreator wc = WorldCreator.name(name).environment(env).type(WorldType.NORMAL);
        World w = getSettings().isUseOwnGenerator() ? wc.createWorld() : wc.generator(chunkGenerator).createWorld();
        if (w != null) {
            configureSpawnRates(w);
        }
        return w;
    }

    private void configureSpawnRates(World w) {
        Settings s = getSettings();
        if (s.getSpawnLimitMonsters() > 0) w.setSpawnLimit(SpawnCategory.MONSTER, s.getSpawnLimitMonsters());
        if (s.getSpawnLimitAmbient() > 0) w.setSpawnLimit(SpawnCategory.AMBIENT, s.getSpawnLimitAmbient());
        if (s.getSpawnLimitAnimals() > 0) w.setSpawnLimit(SpawnCategory.ANIMAL, s.getSpawnLimitAnimals());
        if (s.getSpawnLimitWaterAnimals() > 0) w.setSpawnLimit(SpawnCategory.WATER_ANIMAL, s.getSpawnLimitWaterAnimals());
        if (s.getTicksPerAnimalSpawns() > 0) w.setTicksPerSpawns(SpawnCategory.ANIMAL, s.getTicksPerAnimalSpawns());
        if (s.getTicksPerMonsterSpawns() > 0) w.setTicksPerSpawns(SpawnCategory.MONSTER, s.getTicksPerMonsterSpawns());
    }

    @Override
    public WorldSettings getWorldSettings() {
        return getSettings();
    }

    @Override
    public void onReload() {
        if (loadSettings()) {
            if (marketService != null) {
                marketService.getPriceEngine().invalidate();
            }
            log("Reloaded TradeWinds settings");
        }
    }

    @Override
    public @NonNull ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return chunkGenerator;
    }

    @Override
    public void saveWorldSettings() {
        if (settings != null) {
            configObject.saveConfigObject(settings);
        }
    }

    @Override
    public void allLoaded() {
        // Save settings. This will occur after all addons have loaded
        this.saveWorldSettings();
    }

    public BiomeProvider getBiomeProvider() {
        return this.biomeProvider;
    }

    /**
     * The spawn islet is a real (small-range) BentoBox spawn island, so it is
     * protected like any island - non-ops cannot grief it - and core spawn
     * mechanics recognize it. Possible only because ranges are not enforced
     * equal (see isEnforceEqualRanges).
     */
    private void bootstrapSpawnIsland(int surfaceY) {
        world.bentobox.bentobox.database.objects.Island spawn = getIslands().getSpawn(islandWorld).orElse(null);
        if (spawn == null) {
            org.bukkit.Location center = new org.bukkit.Location(islandWorld, 0.5,
                    Math.max(surfaceY + 1, getSettings().getSeaHeight() + 1), 0.5);
            spawn = getIslands().createIsland(center, null, getSettings().getSpawnProtectionRange());
            if (spawn == null) {
                logError("Could not register the spawn island - spawn is unprotected");
                return;
            }
            // Small range: the spawn island must never crowd the starter cluster
            // (starter centers can be as close as ~1250 per axis)
            spawn.setRange(getSettings().getSpawnProtectionRange() * 2);
            spawn.setName("Spawn");
            spawn.setSpawnPoint(Environment.NORMAL, center);
            getIslands().setSpawn(spawn);
            log("Registered the spawn island (protection " + getSettings().getSpawnProtectionRange() + ")");
        }
        // (Re-)assert spawn island policy every enable, so existing spawn
        // islands pick up rule changes too:
        // - visitors may use boats (it is a harbor), fight monsters in
        //   self-defense, and use workbenches (craft a boat from wild timber)
        spawn.setFlag(Flags.BOAT, 0);
        spawn.setFlag(Flags.HURT_MONSTERS, 0);
        spawn.setFlag(Flags.CRAFTING, 0);
        // - nothing hostile spawns and TNT cannot blow the harbor apart
        spawn.setSettingsFlag(Flags.MONSTER_NATURAL_SPAWN, false);
        spawn.setSettingsFlag(Flags.TNT_DAMAGE, false);
        spawn.setSettingsFlag(Flags.BLOCK_EXPLODE_DAMAGE, false);
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public FuelService getFuelService() {
        return fuelService;
    }

    public WarpService getWarpService() {
        return warpService;
    }

    public RouteGraph getRouteGraph() {
        return routeGraph;
    }

    public StarterKit getStarterKit() {
        return starterKit;
    }

    public HoldService getHoldService() {
        return holdService;
    }

    public IslandDataManager getIslandDataManager() {
        return islandDataManager;
    }

    public MarketService getMarketService() {
        return marketService;
    }

    public TradeDialog getTradeDialog() {
        return tradeDialog;
    }

    public ChartHolograms getChartHolograms() {
        return chartHolograms;
    }

    public StarChartService getStarChartService() {
        return starChartService;
    }

    public IntersticeService getIntersticeService() {
        return intersticeService;
    }

    public EncounterService getEncounterService() {
        return encounterService;
    }

    /**
     * The player's legal standing, for HUDs. Until the reputation system
     * (Stage 6) lands, everyone is Clean.
     *
     * @param playerId the player
     * @return display name of the player's standing
     */
    public String getPlayerStanding(java.util.UUID playerId) {
        return "Clean";
    }

    /**
     * The seeded galaxy for the overworld. Created on first use because the
     * effective seed may be the world's own seed (config galaxy.seed = 0), which
     * is only known once the world exists.
     *
     * @param worldSeed the overworld seed, used when the config seed is 0
     * @return the galaxy engine
     */
    public GalaxyEngine getGalaxyEngine(long worldSeed) {
        if (galaxyEngine == null) {
            Settings s = getSettings();
            long seed = s.getGalaxySeed() != 0 ? s.getGalaxySeed() : worldSeed;
            galaxyEngine = new GalaxyEngine(new GalaxyConfig(seed, s.getGalaxyMinSeparation(),
                    s.getIslandTerrainRadius(), s.getLandLift(), s.getGalaxyDensity(),
                    s.getStarterClusterMinIslands(), s.getBandRadius(), s.getSeaHeight(), typeWeights(),
                    s.getSpawnIsletRadius(), s.getWildIsletChance(), s.getWildIsletRadius()));
            log("TradeWinds galaxy seed: " + seed);
        }
        return galaxyEngine;
    }

    /**
     * Parse the configured island type weights; unknown type names are logged
     * and skipped (a zero total falls back to built-in defaults inside
     * GalaxyConfig).
     */
    private Map<IslandType, Integer> typeWeights() {
        Map<IslandType, Integer> weights = new EnumMap<>(IslandType.class);
        getSettings().getTypeWeights().forEach((name, weight) -> {
            try {
                weights.put(IslandType.valueOf(name.toUpperCase(java.util.Locale.ENGLISH)), weight);
            } catch (IllegalArgumentException e) {
                logError("Unknown island type in galaxy.type-weights: " + name);
            }
        });
        return weights;
    }

    /**
     * @return the galaxy engine, or null if no world query has initialized it yet
     */
    @Nullable
    public GalaxyEngine getGalaxyEngine() {
        return galaxyEngine;
    }
}
