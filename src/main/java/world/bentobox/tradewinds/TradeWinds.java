package world.bentobox.tradewinds;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.NamespacedKey;
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
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.lists.Flags;
import world.bentobox.tradewinds.commands.AdminCustomsCommand;
import world.bentobox.tradewinds.commands.AdminPriceAuditCommand;
import world.bentobox.tradewinds.commands.AdminBoatCommand;
import world.bentobox.tradewinds.commands.AdminRankCommand;
import world.bentobox.tradewinds.commands.AdminIslandsCommand;
import world.bentobox.tradewinds.commands.AdminReflagCommand;
import world.bentobox.tradewinds.commands.AdminTpIslandCommand;
import world.bentobox.tradewinds.commands.AdminWarpFailCommand;
import world.bentobox.tradewinds.commands.TWChartCommand;
import world.bentobox.tradewinds.commands.TWClaimCommand;
import world.bentobox.tradewinds.commands.TWPricesCommand;
import world.bentobox.tradewinds.commands.TWRankCommand;
import world.bentobox.tradewinds.commands.TWFineCommand;
import world.bentobox.tradewinds.commands.TWHomeCommand;
import world.bentobox.tradewinds.commands.TWRestartCommand;
import world.bentobox.tradewinds.commands.TWSetHomeCommand;
import world.bentobox.tradewinds.commands.TWSpawnCommand;
import world.bentobox.tradewinds.commands.TWStarChartCommand;
import world.bentobox.tradewinds.commands.TWTradeCommand;
import world.bentobox.tradewinds.commands.TWUnclaimCommand;
import world.bentobox.tradewinds.commands.TWWarpCommand;
import world.bentobox.tradewinds.crime.BountyBoard;
import world.bentobox.tradewinds.crime.CrimeListener;
import world.bentobox.tradewinds.crime.CustomsListener;
import world.bentobox.tradewinds.crime.CustomsService;
import world.bentobox.tradewinds.crime.PoliceService;
import world.bentobox.tradewinds.crime.ReputationService;
import world.bentobox.tradewinds.crime.Standing;
import world.bentobox.tradewinds.dataobjects.HoldManager;
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
import world.bentobox.tradewinds.tasks.FuelWarningTask;
import world.bentobox.tradewinds.tasks.BorderCurtainTask;
import world.bentobox.tradewinds.tasks.NavigationBarTask;
import world.bentobox.tradewinds.tasks.ResidentAuditTask;
import world.bentobox.tradewinds.travel.BoatPickupListener;
import world.bentobox.tradewinds.travel.BorderPromptListener;
import world.bentobox.tradewinds.travel.ChartHolograms;
import world.bentobox.tradewinds.travel.DialogGuard;
import world.bentobox.tradewinds.travel.ChartingListener;
import world.bentobox.tradewinds.travel.BoatRanks;
import world.bentobox.tradewinds.travel.BoatService;
import world.bentobox.tradewinds.travel.FuelService;
import world.bentobox.tradewinds.travel.IntersticeService;
import world.bentobox.tradewinds.travel.HoldService;
import world.bentobox.tradewinds.travel.SeaPositionTracker;
import world.bentobox.tradewinds.travel.StarChartService;
import world.bentobox.tradewinds.travel.StarterKit;
import world.bentobox.tradewinds.travel.WarpService;
import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
import world.bentobox.tradewinds.galaxy.IslandType;
import world.bentobox.tradewinds.galaxy.SeabedConfig;
import world.bentobox.tradewinds.galaxy.ShapeConfig;
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
    private HoldManager holdManager;
    private BoatRanks boatRanks;
    private BoatService boatService;
    private world.bentobox.tradewinds.travel.HoldGui holdGui;
    private world.bentobox.tradewinds.travel.BoatListener boatListener;
    private IslandDataManager islandDataManager;
    private MarketService marketService;
    private TradeDialog tradeDialog;
    private ChartHolograms chartHolograms;
    private StarChartService starChartService;
    private world.bentobox.tradewinds.travel.RankService rankService;
    private world.bentobox.tradewinds.travel.ChartLeaderboard chartLeaderboard;
    private world.bentobox.tradewinds.travel.IsletClaimService isletClaimService;
    private world.bentobox.tradewinds.galaxy.IntersticeMap intersticeMap;
    private IntersticeService intersticeService;
    private @Nullable EncounterService encounterService;
    private ReputationService reputationService;
    private CrimeListener crimeListener;
    private CustomsService customsService;
    private PoliceService policeService;
    private BountyBoard bountyBoard;
    private NamespacedKey policeKey;
    private @Nullable ResidentAuditTask residentAuditTask;
    private @Nullable NavigationBarTask navigationBarTask;
    private @Nullable BorderCurtainTask borderCurtainTask;
    private @Nullable FuelWarningTask fuelWarningTask;
    private SeaPositionTracker seaPositionTracker;
    private DialogGuard dialogGuard;

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
                // /tw go is the door into the ocean and nothing more: it
                // refuses when you are already at sea, and returns you to the
                // water you left rather than to spawn, so neither it nor a
                // hop through another game mode is a free ride to a market.
                new TWWarpCommand(this);
                new TWChartCommand(this);
                if (TradeWinds.this.getSettings().isPriceLogbookEnabled()) {
                    // Behind a flag, off by default: parked pre-MVP (2026-08-03)
                    new TWPricesCommand(this);
                }
                new TWStarChartCommand(this);
                new TWTradeCommand(this);
                new TWRestartCommand(this);
                new TWFineCommand(this);
                new TWRankCommand(this);
                new TWClaimCommand(this);
                // Home commands work only INSIDE your own island's protection
                // range - a convenience around the base, never a way home
                new TWSetHomeCommand(this);
                new TWHomeCommand(this);
                new TWUnclaimCommand(this);
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
                new AdminCustomsCommand(this);
                new AdminWarpFailCommand(this);
                new AdminPriceAuditCommand(this);
                new AdminRankCommand(this);
                new AdminBoatCommand(this);
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
        // Economy: the virtual hold, market data, prices, trade dialogs
        holdManager = new HoldManager(this);
        boatRanks = new BoatRanks(this);
        boatService = new BoatService(this);
        holdService = new HoldService(this);
        holdGui = new world.bentobox.tradewinds.travel.HoldGui(this);
        registerListener(holdGui);
        // Crafting a boat obeys the One Boat Rule: bigger replaces, smaller refused
        registerListener(new world.bentobox.tradewinds.travel.BoatCraftListener(this));
        // Boats: capture, protection, name plates, TTL on hulls left as items
        boatListener = new world.bentobox.tradewinds.travel.BoatListener(this);
        boatListener.start();
        registerListener(boatListener);
        islandDataManager = new IslandDataManager(this);
        marketService = new MarketService(this);
        tradeDialog = new TradeDialog(this);
        registerListener(new TradeListener(this));
        chartHolograms = new ChartHolograms(this);
        starChartService = new StarChartService(this);
        registerListener(starChartService);
        registerListener(new ChartingListener(this));
        // Seafarer ranks and the charted-islands leaderboard (Stage 7)
        rankService = new world.bentobox.tradewinds.travel.RankService(this);
        chartLeaderboard = new world.bentobox.tradewinds.travel.ChartLeaderboard(this);
        chartLeaderboard.seed();
        chartLeaderboard.registerPlaceholders(rankService);
        registerListener(chartLeaderboard);
        isletClaimService = new world.bentobox.tradewinds.travel.IsletClaimService(this, rankService);
        registerListener(new BorderPromptListener(this));
        // Teleporting while boated brings the boat (and cargo) along
        registerListener(new BoatPickupListener(this));
        // Being attacked closes any open menu, and gates the warp
        dialogGuard = new DialogGuard(this);
        registerListener(dialogGuard);
        // Where a sailor left the ocean, so coming back is not a teleport
        seaPositionTracker = new SeaPositionTracker(this);
        registerListener(seaPositionTracker);
        // Risk at sea: the interstice for warpers, encounters for rowers
        intersticeService = new IntersticeService(this);
        intersticeService.start();
        // A moment to read the way out before anything in there notices you
        registerListener(new world.bentobox.tradewinds.travel.IntersticeGraceListener(this));
        encounterService = new EncounterService(this);
        encounterService.start();
        registerListener(new EncounterListener(this));
        // The law: reputation, and the listeners that notice a crime
        reputationService = new ReputationService(this);
        reputationService.start();
        crimeListener = new CrimeListener(this);
        registerListener(crimeListener);
        // Customs: the scan on entering island space, and the chase after it
        customsService = new CustomsService(this);
        customsService.start();
        registerListener(new CustomsListener(this));
        // The standing response to a wanted player, and what they are worth
        policeService = new PoliceService(this);
        policeService.start();
        registerListener(policeService);
        bountyBoard = new BountyBoard(this);
        bountyBoard.start();
        // Residents survive the night: no mob targeting, tether, respawn
        registerListener(new ResidentProtectionListener());
        residentAuditTask = new ResidentAuditTask(this);
        residentAuditTask.start();
        // Navigation boss bar: island, standing, distance to dock
        navigationBarTask = new NavigationBarTask(this);
        navigationBarTask.start();
        // The visible sea-lanes: red warp ring, blue island edge
        borderCurtainTask = new BorderCurtainTask(this);
        borderCurtainTask.start();
        // "You cannot afford to leave" - said at the port, where it is fixable
        fuelWarningTask = new FuelWarningTask(this);
        fuelWarningTask.start();
        // Spawn (and bed-less respawn) is the spawn island's market plaza.
        // The island itself is adopted in allLoaded() - see below.
        registerListener(new world.bentobox.tradewinds.listeners.SpawnRespawnListener(this));
    }

    @Override
    public void onDisable() {
        if (residentAuditTask != null) {
            residentAuditTask.stop();
        }
        if (navigationBarTask != null) {
            navigationBarTask.stop();
        }
        if (borderCurtainTask != null) {
            borderCurtainTask.stop();
        }
        if (fuelWarningTask != null) {
            fuelWarningTask.stop();
        }
        if (intersticeService != null) {
            intersticeService.stop();
        }
        if (encounterService != null) {
            encounterService.stop();
        }
        if (reputationService != null) {
            reputationService.stop();
        }
        if (customsService != null) {
            customsService.stop();
        }
        if (policeService != null) {
            policeService.stop();
        }
        if (bountyBoard != null) {
            bountyBoard.stop();
        }
        if (chartHolograms != null) {
            chartHolograms.clearAll();
        }
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        if (boatListener != null) {
            boatListener.stop();
        }
        if (holdManager != null) {
            holdManager.saveAll();
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
        // IMPORTANT: BentoBox loads islands from the database AFTER addon
        // onEnable, so the island cache is empty there. Adopting the spawn
        // island any earlier creates a duplicate island every startup and the
        // database load then shadows the changes.
        if (islandWorld != null) {
            bootstrapSpawnIsland();
        }
    }

    public BiomeProvider getBiomeProvider() {
        return this.biomeProvider;
    }

    /**
     * Designate the origin trading island as BentoBox's spawn island: a
     * working port where players arrive, meet the market, and set sail from
     * the dock - no long empty row to start. Admins can rename it, move its
     * spawn point and change its flags with the usual BentoBox commands
     * afterwards; only the first registration sets these.
     */
    private void bootstrapSpawnIsland() {
        GalaxyEngine engine = getGalaxyEngine(islandWorld.getSeed());
        world.bentobox.tradewinds.galaxy.IslandSpec spec = engine.spawnIsland();
        // The plaza is deterministic geometry - no chunk needs to be loaded
        world.bentobox.tradewinds.galaxy.DockPlan plan = engine.dockPlan(spec);
        // Three blocks off the plaza centre: clear of the bell that stands
        // there, and well inside the stall ring
        org.bukkit.Location plaza = new org.bukkit.Location(islandWorld, plan.plazaX() + 3.5,
                getSettings().getSeaHeight() + GalaxyEngine.PLAZA_RISE + 1.0, plan.plazaZ() + 3.5);
        islandWorld.setSpawnLocation(plaza);

        GalaxyIslandRegistrar registrar = new GalaxyIslandRegistrar(this);
        world.bentobox.bentobox.database.objects.Island spawn = getIslands().getSpawn(islandWorld)
                .orElseGet(() -> registrar.register(spec, islandWorld));
        if (spawn == null) {
            logError("Could not register the spawn island - spawn is unprotected");
            return;
        }
        if (!spawn.isSpawn()) {
            spawn.setSpawnPoint(Environment.NORMAL, plaza);
            // Adopting an island from an older build: give it the full trading
            // island geometry it should have had
            spawn.setProtectionRange(getSettings().getIslandProtectionRange());
            spawn.setRange(getSettings().getIslandProtectionRange());
            spawn.setName(spec.name());
            getIslands().setSpawn(spawn);
            log("Designated " + spec.name() + " (" + spec.type() + ") as the spawn island");
        }
        // Flags LAST: Island.setSpawn() resets the flag map to defaults, so
        // anything applied before designating spawn would be wiped.
        // applyBandFlags carries the harbor allowances (boats, self-defense,
        // crafting, doors) that every trading island needs.
        registrar.applyBandFlags(spawn, spec);
        spawn.setSettingsFlag(Flags.MONSTER_NATURAL_SPAWN, false);
        spawn.setSettingsFlag(Flags.TNT_DAMAGE, false);
        spawn.setSettingsFlag(Flags.BLOCK_EXPLODE_DAMAGE, false);
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public world.bentobox.tradewinds.travel.RankService getRankService() {
        return rankService;
    }

    public world.bentobox.tradewinds.travel.ChartLeaderboard getChartLeaderboard() {
        return chartLeaderboard;
    }

    public world.bentobox.tradewinds.travel.IsletClaimService getIsletClaimService() {
        return isletClaimService;
    }

    public NavigationBarTask getNavigationBarTask() {
        return navigationBarTask;
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

    /**
     * @return the virtual hold database manager
     */
    public HoldManager getHoldManager() {
        return holdManager;
    }

    /**
     * @return the boat ladder
     */
    public BoatRanks getBoatRanks() {
        return boatRanks;
    }

    /**
     * @return the physical-boat keeper
     */
    public BoatService getBoatService() {
        return boatService;
    }

    /**
     * @return the boat lifecycle listener (purchases use its swap-quiet so a
     *         hull shed at the quay is not offered straight back)
     */
    public world.bentobox.tradewinds.travel.BoatListener getBoatListener() {
        return boatListener;
    }

    /**
     * @return the hold GUI
     */
    public world.bentobox.tradewinds.travel.HoldGui getHoldGui() {
        return holdGui;
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

    public ReputationService getReputationService() {
        return reputationService;
    }

    public CrimeListener getCrimeListener() {
        return crimeListener;
    }

    public CustomsService getCustomsService() {
        return customsService;
    }

    public DialogGuard getDialogGuard() {
        return dialogGuard;
    }

    public SeaPositionTracker getSeaPositionTracker() {
        return seaPositionTracker;
    }

    public PoliceService getPoliceService() {
        return policeService;
    }

    public BountyBoard getBountyBoard() {
        return bountyBoard;
    }

    /**
     * The PDC key marking an entity as a police unit: dispatched by the law,
     * never drops loot, and killing one is a crime.
     *
     * @return the police tag key
     */
    public NamespacedKey getPoliceKey() {
        if (policeKey == null) {
            policeKey = new NamespacedKey(getPlugin(), "police");
        }
        return policeKey;
    }

    /**
     * The player's legal standing, for HUDs - translated for the viewer.
     *
     * @param user the viewer
     * @param playerId the player whose standing is wanted
     * @return display name of the player's standing
     */
    public String getPlayerStanding(User user, java.util.UUID playerId) {
        if (reputationService == null || !getSettings().isCrimeEnabled()) {
            return user.getTranslation(Standing.CLEAN.getLocaleKey());
        }
        return user.getTranslation(reputationService.standing(playerId).getLocaleKey());
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
                    spawnIslandType(), s.getWildIsletChance(), s.getWildIsletRadius(), s.getWildIsletGrid(),
                    s.getMushroomIsletChance(), seabedConfig(),
                    new ShapeConfig(s.getCoastRoughness(), s.getIslandHilliness())));
            log("TradeWinds galaxy seed: " + seed);
        }
        return galaxyEngine;
    }

    /**
     * The interstice's feature map (wart shoals, watchtowers, the ship
     * graveyard) - pure seeded geometry off the GALAXY seed (fixed
     * 2026-08-07; it used to salt the interstice world's own seed, which is
     * random on every world creation, so each nether regen shuffled every
     * feature and two servers sharing a galaxy seed got different
     * interstices - against spec principle 5, "the seed is the world").
     * The world-seed fallback only applies when galaxy.seed is 0.
     *
     * @param worldSeed the interstice world's seed, used only as the
     *        galaxy.seed=0 fallback
     * @return the map
     */
    public world.bentobox.tradewinds.galaxy.IntersticeMap getIntersticeMap(long worldSeed) {
        if (intersticeMap == null) {
            Settings s = getSettings();
            long seed = s.getGalaxySeed() != 0 ? s.getGalaxySeed() : worldSeed;
            intersticeMap = new world.bentobox.tradewinds.galaxy.IntersticeMap(seed ^ 0x1E7E2571CEL,
                    s.getIntersticeShoalGrid(), s.getIntersticeShoalChance(), s.getIntersticeShoalRadius(),
                    s.getIntersticeGrandShoalChance(), s.getIntersticeWatchtowerGrid(),
                    s.getIntersticeWatchtowerChance(), s.getIntersticeWreckGrid(),
                    s.getIntersticeWreckChance(), s.getIntersticeWreckLootChance());
        }
        return intersticeMap;
    }

    /**
     * The configured shape of the sea floor, or a flat floor at the shelf depth
     * when {@code world.seabed.vary} is off.
     */
    private SeabedConfig seabedConfig() {
        Settings s = getSettings();
        if (!s.isVarySeabed()) {
            return SeabedConfig.flat(s.getSeabedShelfDepth());
        }
        return new SeabedConfig(s.getSeabedShelfDepth(), s.getSeabedAbyssDepth(), s.getSeabedIslandShelfDepth(),
                s.getSeabedRelief(), s.getSeabedRiftDepth(), s.getSeabedRiftThreshold(),
                s.getSeabedSeamountHeight());
    }

    /**
     * The configured spawn island economy, or null to let the seed roll it.
     */
    private IslandType spawnIslandType() {
        String name = getSettings().getSpawnIslandType();
        if (name == null || name.isBlank() || "RANDOM".equalsIgnoreCase(name)) {
            return null;
        }
        try {
            return IslandType.valueOf(name.toUpperCase(java.util.Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            logError("Unknown galaxy.spawn-island-type: " + name + " - using a seeded type");
            return null;
        }
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
