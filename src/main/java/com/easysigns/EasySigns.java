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
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent;

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
    private static final long VALIDATION_INTERVAL_SECONDS = 120; // Full validation every 2 minutes

    private static volatile EasySigns instance;
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

        // Register ChunkPreLoadProcessEvent for signs in chunks loaded after startup
        getEventRegistry().registerGlobal(ChunkPreLoadProcessEvent.class, this::onChunkLoad);

        // Register StartWorldEvent to know when worlds are fully ready for entity operations
        getEventRegistry().registerGlobal(StartWorldEvent.class, this::onWorldStart);

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

        // Periodic display refresh (every 10 seconds) - refreshes only dirty displays
        saveScheduler.scheduleAtFixedRate(
            this::refreshDisplays,
            10,
            10,
            TimeUnit.SECONDS
        );

        // Periodic full validation (every 2 minutes) - marks all displays for refresh
        // This catches any displays that were invalidated but not marked dirty
        saveScheduler.scheduleAtFixedRate(
            this::markAllDisplaysDirty,
            VALIDATION_INTERVAL_SECONDS,
            VALIDATION_INTERVAL_SECONDS,
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
     * Mark all displays as dirty for a full refresh.
     * Runs periodically to catch any invalidated displays that weren't marked.
     */
    private void markAllDisplaysDirty() {
        try {
            if (signStorage == null || displayManager == null) return;

            // Mark all signs with text as dirty
            Map<String, SignData> allSigns = signStorage.getAllSigns();
            int count = 0;
            for (Map.Entry<String, SignData> entry : allSigns.entrySet()) {
                if (entry.getValue().hasText()) {
                    displayManager.markDisplayDirty(entry.getKey());
                    count++;
                }
            }
            logger.fine("Marked " + count + " displays as dirty for refresh");
        } catch (Exception e) {
            logger.warning("Failed to mark displays dirty: " + e.getMessage());
        }
    }

    /**
     * Periodic display refresh - only refreshes dirty displays.
     * This handles cases where entities are removed by the game's entity tracker.
     * Much more efficient than scanning all signs every 10 seconds.
     *
     * Signs whose chunks are not loaded are left for ChunkPreLoadProcessEvent to handle
     * when a player approaches and the chunk loads. Signs whose worlds are not found
     * are re-queued for later retry (transient startup condition).
     */
    private void refreshDisplays() {
        try {
            if (signStorage == null || displayManager == null) return;

            // Only process dirty displays - O(dirty count) instead of O(all signs)
            Set<String> dirty = displayManager.getDirtyDisplaysAndClear();
            if (dirty.isEmpty()) {
                return; // Nothing to refresh!
            }

            logger.fine("Refreshing " + dirty.size() + " dirty displays");
            Map<String, SignData> allSigns = signStorage.getAllSigns();

            int spawned = 0;
            int requeued = 0;
            int skipped = 0;

            for (String key : dirty) {
                SignData signData = allSigns.get(key);
                if (signData == null) {
                    skipped++;
                    continue; // Sign was deleted, don't re-queue
                }

                // Skip signs with no text
                if (!signData.hasText()) {
                    skipped++;
                    continue;
                }

                String[] parts = SignStorage.parseKey(key);
                if (parts == null) {
                    logger.warning("Invalid sign position key in dirty set: " + key);
                    skipped++;
                    continue;
                }

                String worldName = parts[0];
                try {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);

                    World world = Universe.get().getWorld(worldName);
                    if (world == null) {
                        // World not available yet - re-queue for retry (transient during startup)
                        logger.warning("World '" + worldName + "' not found for sign at " + key
                            + ", re-queuing for retry");
                        displayManager.markDisplayDirty(key);
                        requeued++;
                        continue;
                    }

                    if (!world.isAlive()) {
                        logger.warning("World '" + worldName + "' is not alive for sign at " + key
                            + ", re-queuing for retry");
                        displayManager.markDisplayDirty(key);
                        requeued++;
                        continue;
                    }

                    // createDisplay now handles chunk loading internally via ensureChunkAndRun.
                    // If the chunk isn't loaded, it will async-load it and spawn once ready.
                    // If async load fails, createDisplay re-queues the sign as dirty automatically.
                    Vector3i position = new Vector3i(x, y, z);
                    displayManager.createDisplay(world, position, signData.getLines());
                    spawned++;

                } catch (NumberFormatException e) {
                    logger.warning("Invalid coordinates in sign key: " + key);
                    skipped++;
                }
            }

            if (spawned > 0 || requeued > 0) {
                logger.info("Display refresh: attempted=" + spawned + " requeued=" + requeued
                    + " skipped=" + skipped);
            }
        } catch (Exception e) {
            logger.severe("Display refresh failed with exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Called when a chunk is loaded (player approaches area, server loads chunk from disk, etc.).
     * This is the PRIMARY mechanism for spawning sign displays when players enter an area.
     * Spawns display entities for any signs in that chunk.
     * Uses spatial index for O(1) lookup instead of scanning all signs.
     */
    private void onChunkLoad(ChunkPreLoadProcessEvent event) {
        try {
            WorldChunk chunk = event.getChunk();
            if (chunk == null) {
                logger.warning("ChunkPreLoadProcessEvent fired with null chunk");
                return;
            }

            World world = chunk.getWorld();
            if (world == null) {
                logger.warning("ChunkPreLoadProcessEvent chunk has null world");
                return;
            }

            String worldName = world.getName();
            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();

            logger.fine("ChunkLoad event: world=" + worldName + " chunk=(" + chunkX + "," + chunkZ + ")"
                + " newlyGenerated=" + event.isNewlyGenerated());

            // O(1) lookup using spatial index - only get signs in this specific chunk
            Set<String> signsInChunk = signStorage.getSignsInChunk(worldName, chunkX, chunkZ);
            if (signsInChunk.isEmpty()) {
                return; // No signs in this chunk, fast exit
            }

            logger.info("ChunkLoad: Spawning displays for " + signsInChunk.size()
                + " signs in chunk (" + chunkX + "," + chunkZ + ") world=" + worldName);

            // Get all signs once (unmodifiable view, no copy)
            Map<String, SignData> allSigns = signStorage.getAllSigns();
            int attempted = 0;

            for (String posKey : signsInChunk) {
                SignData signData = allSigns.get(posKey);
                if (signData == null) {
                    logger.warning("Sign data missing for position key: " + posKey
                        + " (chunk index out of sync?)");
                    continue;
                }

                if (!signData.hasText()) continue;

                String[] parts = SignStorage.parseKey(posKey);
                if (parts == null) {
                    logger.warning("Invalid position key in chunk index: " + posKey);
                    continue;
                }

                try {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);

                    // Verify this block position actually maps to this chunk
                    int expectedChunkX = ChunkUtil.chunkCoordinate(x);
                    int expectedChunkZ = ChunkUtil.chunkCoordinate(z);
                    if (expectedChunkX != chunkX || expectedChunkZ != chunkZ) {
                        logger.severe("CHUNK INDEX MISMATCH: Sign at " + posKey
                            + " maps to chunk (" + expectedChunkX + "," + expectedChunkZ + ")"
                            + " but was indexed in chunk (" + chunkX + "," + chunkZ + ")."
                            + " The chunk size assumption may be wrong!");
                        // Still try to spawn - createDisplay handles chunk loading
                    }

                    Vector3i position = new Vector3i(x, y, z);
                    displayManager.createDisplay(world, position, signData.getLines());
                    attempted++;
                } catch (NumberFormatException e) {
                    logger.warning("Invalid coordinates in sign key: " + posKey);
                }
            }

            logger.fine("ChunkLoad: Attempted to spawn " + attempted + " sign displays in chunk ("
                + chunkX + "," + chunkZ + ")");
        } catch (Exception e) {
            logger.severe("Exception in onChunkLoad handler: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Called when a world starts ticking. Marks all signs in that world as dirty
     * so they get spawned by the periodic refresh or chunk load events.
     */
    private void onWorldStart(StartWorldEvent event) {
        try {
            World world = event.getWorld();
            if (world == null) return;

            String worldName = world.getName();
            logger.info("World started: " + worldName + " - marking signs for refresh");

            Set<String> signsInWorld = signStorage.getSignKeysInWorld(worldName);
            if (signsInWorld.isEmpty()) {
                logger.info("No signs in world " + worldName);
                return;
            }

            int count = 0;
            Map<String, SignData> allSigns = signStorage.getAllSigns();
            for (String posKey : signsInWorld) {
                SignData signData = allSigns.get(posKey);
                if (signData != null && signData.hasText()) {
                    displayManager.markDisplayDirty(posKey);
                    count++;
                }
            }

            logger.info("Marked " + count + " signs in world " + worldName + " for refresh");
        } catch (Exception e) {
            logger.severe("Exception in onWorldStart handler: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void start() {
        logger.info("========== EASYSIGNS STARTED ==========");
        logger.info("Signs loaded: " + signStorage.getSignCount());

        // After a server restart, all previous entity references are invalid.
        // Clear stale tracking so displays are fully recreated.
        displayManager.invalidateAllDisplays();
        logger.info("Cleared stale display tracking from previous session");

        // Wait for universe to be ready, then mark all signs for refresh.
        // Signs will be spawned by either:
        // 1. The periodic refresh task (every 10s) which calls createDisplay with chunk verification
        // 2. ChunkPreLoadProcessEvent when a player approaches and the chunk loads
        // 3. StartWorldEvent which marks all signs in each world as dirty
        // All three paths now guarantee chunk is loaded before entity spawn.
        Universe.get().getUniverseReady().thenRun(() -> {
            logger.info("Universe ready - marking all " + signStorage.getSignCount()
                + " signs for refresh...");
            markAllDisplaysDirty();
        });
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
