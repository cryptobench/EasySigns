package com.easysigns;

import com.easysigns.command.SignCommand;
import com.easysigns.config.SignConfig;
import com.easysigns.data.SignData;
import com.easysigns.data.SignStorage;
import com.easysigns.listener.SignChatListener;
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

import java.util.logging.Logger;

/**
 * EasySigns - Typeable signs for Hytale servers
 *
 * Place a sign block to open the editor, or right-click existing signs to edit.
 * Enter text with lines separated by | (e.g., "Line1|Line2|Line3|Line4")
 */
public class EasySigns extends JavaPlugin {

    private static EasySigns instance;
    private Logger logger;
    private SignConfig config;
    private SignStorage signStorage;
    private SignDisplayManager displayManager;
    private SignInteractionListener interactionListener;
    private SignChatListener chatListener;
    private SignPlaceSystem signPlaceSystem;

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

        // Register block place system to detect sign placements
        this.signPlaceSystem = new SignPlaceSystem(this, signStorage);
        getEntityStoreRegistry().registerSystem(signPlaceSystem);

        // Register AllWorldsLoadedEvent to spawn displays after server starts
        getEventRegistry().registerGlobal(AllWorldsLoadedEvent.class, this::onAllWorldsLoaded);

        // Register ChunkPreLoadProcessEvent to spawn displays when chunks load
        getEventRegistry().registerGlobal(ChunkPreLoadProcessEvent.class, this::onChunkLoad);

        logger.info("EasySigns setup complete!");
    }

    /**
     * Called when all worlds are loaded on server startup.
     * Spawns display entities for all stored signs.
     */
    private void onAllWorldsLoaded(AllWorldsLoadedEvent event) {
        logger.info("AllWorldsLoadedEvent received - spawning sign displays...");

        Map<String, SignData> allSigns = signStorage.getAllSigns();
        int spawnedCount = 0;

        for (Map.Entry<String, SignData> entry : allSigns.entrySet()) {
            String key = entry.getKey();
            SignData signData = entry.getValue();

            String[] parts = SignStorage.parseKey(key);
            if (parts == null) {
                logger.warning("Invalid sign key: " + key);
                continue;
            }

            String worldName = parts[0];
            try {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);

                World world = Universe.get().getWorld(worldName);
                if (world == null) {
                    logger.warning("World not found for sign: " + worldName);
                    continue;
                }

                Vector3i position = new Vector3i(x, y, z);
                displayManager.createDisplay(world, position, signData.getLines());
                spawnedCount++;

            } catch (NumberFormatException e) {
                logger.warning("Failed to parse position from key: " + key);
            }
        }

        logger.info("Spawned displays for " + spawnedCount + " signs");
    }

    /**
     * Called when a chunk is loaded.
     * Spawns display entities for any signs in that chunk.
     */
    private void onChunkLoad(ChunkPreLoadProcessEvent event) {
        WorldChunk chunk = event.getChunk();
        if (chunk == null) return;

        World world = chunk.getWorld();
        if (world == null) return;

        String worldName = world.getName();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        // Chunk coordinates to block coordinates (assuming 16x16 chunks)
        int minX = chunkX * 16;
        int maxX = minX + 15;
        int minZ = chunkZ * 16;
        int maxZ = minZ + 15;

        Map<String, SignData> allSigns = signStorage.getAllSigns();

        for (Map.Entry<String, SignData> entry : allSigns.entrySet()) {
            String key = entry.getKey();
            String[] parts = SignStorage.parseKey(key);
            if (parts == null) continue;

            // Check if this sign is in the loading chunk
            if (!parts[0].equals(worldName)) continue;

            try {
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[3]);

                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    int y = Integer.parseInt(parts[2]);
                    Vector3i position = new Vector3i(x, y, z);
                    displayManager.createDisplay(world, position, entry.getValue().getLines());
                }
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
        if (displayManager != null) {
            displayManager.cleanup();
        }
        if (signStorage != null) {
            signStorage.save();
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
