package com.easysigns;

import com.easysigns.command.SignCommand;
import com.easysigns.config.SignConfig;
import com.easysigns.data.SignData;
import com.easysigns.data.SignStorage;
import com.easysigns.listener.SignChatListener;
import com.easysigns.listener.SignDisconnectListener;
import com.easysigns.listener.SignInteractionListener;
import com.easysigns.sign.SignDisplayEntity;
import com.easysigns.sign.SignDisplayManager;
import com.easysigns.systems.SignPlaceSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * EasySigns - Typeable signs for Hytale servers
 *
 * Place a sign block to open the editor, or right-click existing signs to edit.
 * Enter text with lines separated by | (e.g., "Line1|Line2|Line3|Line4")
 */
public class EasySigns extends JavaPlugin {

    private static final long SAVE_INTERVAL_SECONDS = 300; // Save every 5 minutes

    private static EasySigns instance;
    private Logger logger;
    private SignConfig config;
    private SignStorage signStorage;
    private SignDisplayManager displayManager;
    private SignInteractionListener interactionListener;
    private SignChatListener chatListener;
    private SignDisconnectListener disconnectListener;
    private SignPlaceSystem signPlaceSystem;
    private ScheduledExecutorService saveScheduler;

    public EasySigns(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    public void setup() {
        this.logger = Logger.getLogger("EasySigns");
        logger.info("========== EASYSIGNS STARTING ==========");

        // Register custom entity type for floating text displays
        getEntityRegistry().registerEntity(
            "easysigns:sign_display",
            SignDisplayEntity.class,
            SignDisplayEntity::new,
            SignDisplayEntity.CODEC
        );
        logger.info("Registered sign display entity type");

        // Initialize config
        this.config = new SignConfig();
        config.init(getDataDirectory(), logger);

        // Initialize sign storage
        this.signStorage = new SignStorage(getDataDirectory(), logger);

        // Initialize display manager for floating text
        this.displayManager = new SignDisplayManager(this);

        // Register commands
        getCommandRegistry().registerCommand(new SignCommand(this, signStorage));

        // Register interaction listener for editing existing signs
        this.interactionListener = new SignInteractionListener(this, signStorage);
        interactionListener.register(getEventRegistry());

        // Register chat listener for sign text input
        this.chatListener = new SignChatListener(this, signStorage);
        chatListener.register(getEventRegistry());

        // Register disconnect listener for session cleanup
        this.disconnectListener = new SignDisconnectListener(this);
        disconnectListener.register(getEventRegistry());

        // Register block place system to detect sign placements
        this.signPlaceSystem = new SignPlaceSystem(this, signStorage);
        getEntityStoreRegistry().registerSystem(signPlaceSystem);

        // Register AllWorldsLoadedEvent to spawn all sign displays on startup
        getEventRegistry().registerGlobal(AllWorldsLoadedEvent.class, this::onAllWorldsLoaded);

        // Register ChunkPreLoadProcessEvent for signs in chunks loaded after startup
        getEventRegistry().registerGlobal(ChunkPreLoadProcessEvent.class, this::onChunkLoad);

        // Create periodic task scheduler for saves and display refresh
        this.saveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EasySigns-Scheduler");
            t.setDaemon(true);
            return t;
        });

        // Periodic save (every 5 minutes)
        saveScheduler.scheduleAtFixedRate(
            this::periodicSave,
            SAVE_INTERVAL_SECONDS,
            SAVE_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );

        // Periodic display refresh (every 10 seconds) - ensures signs stay visible
        saveScheduler.scheduleAtFixedRate(
            this::refreshDisplays,
            10,
            10,
            TimeUnit.SECONDS
        );

        logger.info("EasySigns setup complete!");
    }

    /**
     * Periodic save task - saves if there are unsaved changes.
     */
    private void periodicSave() {
        try {
            if (signStorage != null && signStorage.saveIfDirty()) {
                logger.fine("Periodic save completed");
            }
        } catch (Exception e) {
            logger.warning("Periodic save failed: " + e.getMessage());
        }
    }

    /**
     * Periodic display refresh - respawns any missing sign displays.
     * This handles cases where entities are removed by the game's entity tracker.
     * createDisplay() validates entity existence before skipping, so this is safe.
     */
    private void refreshDisplays() {
        try {
            if (signStorage == null || displayManager == null) return;

            Map<String, SignData> allSigns = signStorage.getAllSigns();

            for (Map.Entry<String, SignData> entry : allSigns.entrySet()) {
                String key = entry.getKey();
                SignData signData = entry.getValue();

                // Skip signs with no text
                if (!signData.hasText()) continue;

                String[] parts = SignStorage.parseKey(key);
                if (parts == null) continue;

                String worldName = parts[0];
                try {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);

                    World world = Universe.get().getWorld(worldName);
                    if (world == null) continue;

                    Vector3i position = new Vector3i(x, y, z);
                    displayManager.createDisplay(world, position, signData.getLines());

                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception e) {
            logger.warning("Display refresh failed: " + e.getMessage());
        }
    }

    /**
     * Called when all worlds are loaded on server startup.
     * Spawns display entities for all stored signs.
     */
    private void onAllWorldsLoaded(AllWorldsLoadedEvent event) {
        logger.info("Spawning sign displays...");

        Map<String, SignData> allSigns = signStorage.getAllSigns();
        int spawnedCount = 0;

        for (Map.Entry<String, SignData> entry : allSigns.entrySet()) {
            String key = entry.getKey();
            SignData signData = entry.getValue();

            String[] parts = SignStorage.parseKey(key);
            if (parts == null) continue;

            String worldName = parts[0];
            try {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);

                World world = Universe.get().getWorld(worldName);
                if (world == null) continue;

                Vector3i position = new Vector3i(x, y, z);
                displayManager.createDisplay(world, position, signData.getLines());
                spawnedCount++;

            } catch (NumberFormatException ignored) {
            }
        }

        logger.info("Spawned displays for " + spawnedCount + " signs");
    }

    /**
     * Called when a chunk is loaded.
     * Spawns display entities for any signs in that chunk.
     * Uses spatial index for O(1) lookup instead of scanning all signs.
     */
    private void onChunkLoad(ChunkPreLoadProcessEvent event) {
        WorldChunk chunk = event.getChunk();
        if (chunk == null) return;

        World world = chunk.getWorld();
        if (world == null) return;

        String worldName = world.getName();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        // O(1) lookup using spatial index - only get signs in this specific chunk
        Set<String> signsInChunk = signStorage.getSignsInChunk(worldName, chunkX, chunkZ);
        if (signsInChunk.isEmpty()) {
            return; // No signs in this chunk, fast exit
        }

        // Get all signs once (unmodifiable view, no copy)
        Map<String, SignData> allSigns = signStorage.getAllSigns();

        for (String posKey : signsInChunk) {
            SignData signData = allSigns.get(posKey);
            if (signData == null) continue;

            String[] parts = SignStorage.parseKey(posKey);
            if (parts == null) continue;

            try {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                Vector3i position = new Vector3i(x, y, z);

                // createDisplay already handles duplicates by removing existing display first
                displayManager.createDisplay(world, position, signData.getLines());
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Override
    public void start() {
        logger.info("========== EASYSIGNS STARTED ==========");
        logger.info("Signs loaded: " + signStorage.getSignCount());
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down EasySigns...");

        // Stop the save scheduler
        if (saveScheduler != null) {
            saveScheduler.shutdownNow();
        }

        if (displayManager != null) {
            displayManager.cleanup();
        }
        if (signStorage != null) {
            signStorage.save(); // Force final save
        }
        logger.info("EasySigns shut down.");
    }

    public static EasySigns getInstance() {
        return instance;
    }

    public SignStorage getSignStorage() {
        return signStorage;
    }

    public SignDisplayManager getDisplayManager() {
        return displayManager;
    }

    public Logger getPluginLogger() {
        return logger;
    }

    public SignConfig getConfig() {
        return config;
    }
}
