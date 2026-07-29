package world.bentobox.tradewinds;

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
import world.bentobox.bentobox.api.configuration.Config;
import world.bentobox.bentobox.api.configuration.WorldSettings;
import world.bentobox.bentobox.lists.Flags;
import world.bentobox.tradewinds.commands.AdminIslandsCommand;
import world.bentobox.tradewinds.commands.AdminTpIslandCommand;
import world.bentobox.tradewinds.commands.TWSpawnCommand;
import world.bentobox.tradewinds.listeners.IntersticePortalListener;
import world.bentobox.tradewinds.galaxy.GalaxyConfig;
import world.bentobox.tradewinds.galaxy.GalaxyEngine;
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

    /**
     * This addon uses the new chunk generation API for the sea bottom
     */
    @Override
    public boolean isUsesNewChunkGeneration() {
        return true;
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
                new IslandInfoCommand(this);
                new IslandLanguageCommand(this);
            }
        };
        adminCommand = new DefaultAdminCommand(this) {
            @Override
            public void setup() {
                super.setup();
                new AdminIslandsCommand(this);
                new AdminTpIslandCommand(this);
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
        // Register trading islands lazily as their center chunks first load
        registerListener(new GalaxyIslandRegistrar(this));
        // Seal both worlds against portals - the interstice is warp-failure-only
        registerListener(new IntersticePortalListener(this));
        // Deterministic ocean spawn on the sea surface at the galaxy origin
        if (islandWorld != null) {
            islandWorld.setSpawnLocation(0, getSettings().getSeaHeight() + 1, 0);
        }
    }

    @Override
    public void onDisable() {
        // Nothing to do here yet
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
                    s.getStarterClusterMinIslands(), s.getBandRadius()));
            log("TradeWinds galaxy seed: " + seed);
        }
        return galaxyEngine;
    }

    /**
     * @return the galaxy engine, or null if no world query has initialized it yet
     */
    @Nullable
    public GalaxyEngine getGalaxyEngine() {
        return galaxyEngine;
    }
}
