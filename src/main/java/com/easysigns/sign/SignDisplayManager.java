package com.easysigns.sign;

import com.easysigns.EasySigns;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages the visual display of sign text in the world.
 *
 * Uses invisible marker entities with Nameplate components to show
 * floating text above sign blocks.
 *
 * This manager tracks all active sign displays and handles:
 * - Creating display entities when signs are placed
 * - Updating display text when signs are edited
 * - Removing display entities when signs are destroyed
 */
public class SignDisplayManager {

    private final EasySigns plugin;
    private final Logger logger;

    // Maps position key -> entity reference for active displays
    // Key format: "world:x:y:z"
    private final Map<String, Ref<EntityStore>> displayEntities;

    // Offset for text display position (centered on the sign face)
    private static final double TEXT_DISPLAY_OFFSET_Y = 0.5;  // Center of block height
    private static final double TEXT_DISPLAY_OFFSET_Z = 0.05; // Slightly in front of block

    public SignDisplayManager(EasySigns plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        this.displayEntities = new ConcurrentHashMap<>();
    }

    /**
     * Create a display entity for a sign at the given position.
     *
     * @param world The world containing the sign
     * @param position The block position of the sign
     * @param lines The text lines to display
     */
    public void createDisplay(World world, Vector3i position, String[] lines) {
        if (world == null || position == null || lines == null) {
            return;
        }

        String posKey = getPositionKey(world, position);

        // Remove existing display if present
        removeDisplay(world, position);

        // Build display text from lines
        StringBuilder sb = new StringBuilder();
        boolean hasText = false;
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
                hasText = true;
            }
        }

        // Only create display if sign has text
        if (!hasText) {
            logger.info("No text for sign at " + position + ", skipping display");
            return;
        }

        String displayText = sb.toString();
        logger.info("Creating display for sign at " + position + " with text: " + displayText);

        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                logger.info("Inside world.execute, spawning entity...");

                // Calculate display position (centered on the sign block face)
                Vector3d displayPos = new Vector3d(
                    position.getX() + 0.5,
                    position.getY() + TEXT_DISPLAY_OFFSET_Y,
                    position.getZ() + 0.5
                );

                // Create and spawn the display entity
                SignDisplayEntity displayEntity = new SignDisplayEntity(world);
                SignDisplayEntity spawned = world.spawnEntity(displayEntity, displayPos, new Vector3f(0, 0, 0));

                if (spawned == null) {
                    logger.warning("Failed to spawn display entity - spawnEntity returned null");
                    return;
                }

                // Get the entity reference
                Ref<EntityStore> entityRef = spawned.getReference();
                if (entityRef == null) {
                    logger.warning("Failed to get entity reference after spawn");
                    return;
                }

                logger.info("Entity spawned, adding components...");

                // Add nameplate component to the entity
                Nameplate nameplate = store.ensureAndGetComponent(entityRef, Nameplate.getComponentType());
                nameplate.setText(displayText);
                logger.info("Nameplate text set to: " + displayText);

                // Add Visible component to make entity trackable by clients
                store.ensureAndGetComponent(entityRef, EntityTrackerSystems.Visible.getComponentType());
                logger.info("Visible component added");

                // Add ModelComponent so client knows entity exists
                try {
                    // Create a model reference with a tiny scale (nearly invisible)
                    Model.ModelReference modelRef = new Model.ModelReference("hytale:player", 0.01f, null);
                    Model model = modelRef.toModel();
                    if (model != null) {
                        store.addComponent(entityRef, ModelComponent.getComponentType(), new ModelComponent(model));
                        logger.info("ModelComponent added with tiny scale");
                    } else {
                        logger.warning("Model is null");
                    }
                } catch (Exception modelEx) {
                    logger.warning("Failed to add ModelComponent: " + modelEx.getMessage());
                }

                // Track the entity reference
                displayEntities.put(posKey, entityRef);

                logger.info("SUCCESS: Created sign display at " + position + " with text: " + displayText);

            } catch (Exception e) {
                logger.warning("Failed to create sign display: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Update the display for an existing sign.
     *
     * @param world The world containing the sign
     * @param position The block position of the sign
     * @param lines The updated text lines
     */
    public void updateDisplay(World world, Vector3i position, String[] lines) {
        if (position == null || lines == null) {
            return;
        }

        if (world == null) {
            logger.fine("World is null for sign update, skipping display update");
            return;
        }

        String posKey = getPositionKey(world, position);
        Ref<EntityStore> entityRef = displayEntities.get(posKey);

        if (entityRef == null) {
            // No existing display, create new one
            createDisplay(world, position, lines);
            return;
        }

        // Check if sign has any text
        boolean hasText = false;
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
                hasText = true;
            }
        }

        // If sign is now empty, remove display
        if (!hasText) {
            removeDisplay(world, position);
            return;
        }

        String displayText = sb.toString();

        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();

                // Get the nameplate component and update text
                Nameplate nameplate = store.getComponent(entityRef, Nameplate.getComponentType());
                if (nameplate != null) {
                    nameplate.setText(displayText);
                    logger.fine("Updated sign display at " + position);
                } else {
                    // Nameplate component missing, recreate the display
                    logger.fine("Nameplate component missing, recreating display");
                    displayEntities.remove(posKey);
                    createDisplay(world, position, lines);
                }

            } catch (Exception e) {
                logger.warning("Failed to update sign display: " + e.getMessage());
                // Try recreating the display
                displayEntities.remove(posKey);
                createDisplay(world, position, lines);
            }
        });
    }

    /**
     * Remove the display entity for a sign.
     *
     * @param world The world containing the sign
     * @param position The block position of the sign
     */
    public void removeDisplay(World world, Vector3i position) {
        if (position == null) {
            return;
        }

        String posKey = world != null ? getPositionKey(world, position) : null;

        // Try to find and remove by position
        Ref<EntityStore> entityRef = posKey != null ? displayEntities.remove(posKey) : null;

        if (entityRef != null && world != null) {
            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    // Remove the entity from the store
                    store.removeEntity(entityRef, RemoveReason.REMOVE);

                    logger.fine("Removed sign display at " + position);

                } catch (Exception e) {
                    logger.warning("Failed to remove sign display: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Clean up all display entities.
     * Called during plugin shutdown.
     */
    public void cleanup() {
        logger.info("Cleaning up " + displayEntities.size() + " sign displays...");

        // Note: Entities will be cleaned up automatically when worlds unload
        // We just clear our tracking map
        displayEntities.clear();
    }

    /**
     * Generate a unique key for a sign position.
     */
    private String getPositionKey(World world, Vector3i position) {
        String worldName = world.getName();
        return String.format("%s:%d:%d:%d",
            worldName != null ? worldName : "unknown",
            position.getX(),
            position.getY(),
            position.getZ()
        );
    }

    /**
     * Get the number of active sign displays.
     */
    public int getDisplayCount() {
        return displayEntities.size();
    }
}
