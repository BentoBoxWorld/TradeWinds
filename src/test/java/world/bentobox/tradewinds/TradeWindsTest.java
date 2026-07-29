package world.bentobox.tradewinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import world.bentobox.bentobox.Settings;
import world.bentobox.bentobox.api.addons.Addon.State;
import world.bentobox.bentobox.api.addons.AddonDescription;
import world.bentobox.bentobox.database.AbstractDatabaseHandler;
import world.bentobox.bentobox.database.DatabaseSetup;
import world.bentobox.bentobox.database.DatabaseSetup.DatabaseType;
import world.bentobox.bentobox.managers.AddonsManager;
import world.bentobox.bentobox.managers.CommandsManager;
import world.bentobox.bentobox.managers.FlagsManager;

/**
 * Tests the TradeWinds addon lifecycle.
 *
 * @author tastybento
 */
class TradeWindsTest extends CommonTestSetup {

    private TradeWinds addon;
    @Mock
    private FlagsManager flagsManager;
    @Mock
    private Settings pluginSettings;

    private MockedStatic<DatabaseSetup> mockDb;

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        mockDb.closeOnDemand();
        super.tearDown();
        deleteAll(new File("database"));
        deleteAll(new File("database_backup"));
        new File("config.yml").delete();
        new File("addon.jar").delete();
        deleteAll(new File("addons"));
    }

    @SuppressWarnings("unchecked")
    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        // Database
        AbstractDatabaseHandler<Object> h = mock(AbstractDatabaseHandler.class);
        mockDb = Mockito.mockStatic(DatabaseSetup.class);
        DatabaseSetup dbSetup = mock(DatabaseSetup.class);
        mockDb.when(DatabaseSetup::getDatabase).thenReturn(dbSetup);
        when(dbSetup.getHandler(any())).thenReturn(h);
        when(h.saveObject(any())).thenReturn(CompletableFuture.completedFuture(true));

        // The database type has to be created one line before the thenReturn() to work!
        DatabaseType value = DatabaseType.JSON;
        when(plugin.getSettings()).thenReturn(pluginSettings);
        when(pluginSettings.getDatabaseType()).thenReturn(value);

        // Command manager
        CommandsManager cm = mock(CommandsManager.class);
        when(plugin.getCommandsManager()).thenReturn(cm);

        // Create the addon with a temporary jar containing config.yml
        addon = new TradeWinds();
        File jFile = new File("addon.jar");
        try (JarOutputStream tempJarOutputStream = new JarOutputStream(new FileOutputStream(jFile))) {
            Path fromPath = Paths.get("src/main/resources/config.yml");
            Path path = Paths.get("config.yml");
            Files.copy(fromPath, path);
            add(path, tempJarOutputStream);
        }

        File dataFolder = new File("addons/TradeWinds");
        addon.setDataFolder(dataFolder);
        addon.setFile(jFile);
        AddonDescription desc = new AddonDescription.Builder("bentobox", "tradewinds", "0.1.0").description("test")
                .authors("tastybento").build();
        addon.setDescription(desc);
        // Addons manager
        AddonsManager am = mock(AddonsManager.class);
        when(plugin.getAddonsManager()).thenReturn(am);

        // Flags manager
        when(plugin.getFlagsManager()).thenReturn(flagsManager);
        when(flagsManager.getFlags()).thenReturn(Collections.emptyList());
    }

    private void add(Path path, JarOutputStream tempJarOutputStream) throws IOException {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            byte[] buffer = new byte[1024];
            int bytesRead = 0;
            JarEntry entry = new JarEntry(path.toString());
            tempJarOutputStream.putNextEntry(entry);
            while ((bytesRead = fis.read(buffer)) != -1) {
                tempJarOutputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * Test method for {@link TradeWinds#onLoad()}.
     */
    @Test
    void testOnLoad() {
        addon.onLoad();
        // Check that config.yml file has been saved
        File check = new File("addons/TradeWinds", "config.yml");
        assertTrue(check.exists());
        assertTrue(addon.getPlayerCommand().isPresent());
        assertTrue(addon.getAdminCommand().isPresent());
        assertNotNull(addon.getBiomeProvider());
    }

    /**
     * Test method for {@link TradeWinds#onEnable()}.
     */
    @Test
    void testOnEnable() {
        testOnLoad();
        addon.setState(State.ENABLED);
        addon.onEnable();
        assertNotEquals(State.DISABLED, addon.getState());
    }

    /**
     * Test method for {@link TradeWinds#onReload()}.
     */
    @Test
    void testOnReload() {
        addon.onLoad();
        addon.onEnable();
        addon.onReload();
        File check = new File("addons/TradeWinds", "config.yml");
        assertTrue(check.exists());
    }

    /**
     * Test method for {@link TradeWinds#createWorlds()}. The ocean overworld and
     * the interstice are both created; the End never is.
     */
    @Test
    void testCreateWorlds() {
        addon.onLoad();
        addon.createWorlds();
        verify(plugin).log("[tradewinds] Creating TradeWinds ocean...");
        verify(plugin).log("[tradewinds] Creating TradeWinds interstice...");
        assertNotNull(addon.getOverWorld());
        assertNotNull(addon.getNetherWorld());
        // No End world, ever (spec principle 7)
        assertNull(addon.getEndWorld());
    }

    /**
     * Interstice generation can be turned off; then only the ocean is created.
     */
    @Test
    void testCreateWorldsNoInterstice() {
        addon.onLoad();
        addon.getSettings().setNetherGenerate(false);
        addon.createWorlds();
        verify(plugin).log("[tradewinds] Creating TradeWinds ocean...");
        verify(plugin, never()).log("[tradewinds] Creating TradeWinds interstice...");
        assertNull(addon.getNetherWorld());
    }

    /**
     * Test method for {@link TradeWinds#getSettings()}.
     */
    @Test
    void testGetSettings() {
        addon.onLoad();
        assertNotNull(addon.getSettings());
        assertEquals(addon.getSettings(), addon.getWorldSettings());
    }

    /**
     * The world generator is provided for both worlds.
     */
    @Test
    void testGetDefaultWorldGenerator() {
        addon.onLoad();
        assertNotNull(addon.getDefaultWorldGenerator("tradewinds_world", ""));
    }
}
