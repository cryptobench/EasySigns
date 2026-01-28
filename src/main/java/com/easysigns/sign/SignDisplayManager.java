package com.easysigns.sign;

import com.easysigns.EasySigns;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages the visual display of sign text in the world.
 *
 * Uses invisible marker entities with Nameplate components to show
 * floating text above sign blocks. Each line is a separate entity
 * stacked vertically.
 *
 * Performance optimizations:
 * - In-place text updates when line count doesn't change
 * - Tracks display state to prevent duplicate spawns
 */
public class SignDisplayManager {

    private final EasySigns plugin;
    private final Logger logger;

    // Maps position key -> list of entity references (one per line)
    // Key format: "world:x:y:z"
    private final Map<String, List<Ref<EntityStore>>> displayEntities;

    // Tracks the current text for each display (for detecting what changed)
    private final Map<String, List<String>> displayText;

    // Dirty displays that need refresh (chunk unload, entity validation failure, etc.)
    private final Set<String> dirtyDisplays;

    // Spacing between lines
    private static final double LINE_HEIGHT = 0.25;
    // Base Y offset from block position
    private static final double BASE_Y_OFFSET = 1.0;

    // Cached tiny model reference - avoids creating new model per display line
    private static final Model TINY_MODEL;
    static {
        Model temp = null;
        try {
            temp = new Model.ModelReference("hytale:player", 0.01f, null).toModel();
        } catch (Exception e) {
            // Model creation failed - will fall back to no model
        }
        TINY_MODEL = temp;
    }

    public SignDisplayManager(EasySigns plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        this.displayEntities = new ConcurrentHashMap<>();
        this.displayText = new ConcurrentHashMap<>();
        this.dirtyDisplays = ConcurrentHashMap.newKeySet();
    }

    /**
     * Create display entities for a sign at the given position.
     * Creates one entity per line, stacked vertically.
     * If display already exists with same structure, skips recreation.
     * Ensures the chunk is loaded before spawning entities.
     */
    public void createDisplay(World world, Vector3i position, String[] lines) {
        if (world == null || position == null || lines == null) {
            logger.warning("createDisplay called with null argument: world=" + world
                + " position=" + position + " lines=" + (lines != null ? lines.length : "null"));
            return;
        }

        String posKey = getPositionKey(world, position);

        // Collect non-empty lines
        List<String> textLines = new ArrayList<>();
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                textLines.add(line);
            }
        }

        if (textLines.isEmpty()) {
            // No text - remove display if exists
            removeDisplay(world, position);
            logger.fine("No text for sign at " + position + ", display removed/skipped");
            return;
        }

        // Check if we already have a display with the same text (thread-safe: just comparing strings)
        List<String> existingText = displayText.get(posKey);
        if (existingText != null && existingText.equals(textLines)) {
            // Text matches - validate entities exist, with chunk guarantee
            ensureChunkAndRun(world, position, posKey, () -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    List<Ref<EntityStore>> existingRefs = displayEntities.get(posKey);

                    if (existingRefs != null && !existingRefs.isEmpty()) {
                        boolean allValid = true;
                        for (Ref<EntityStore> ref : existingRefs) {
                            if (ref == null || !ref.isValid()) {
                                allValid = false;
                                break;
                            }
                            try {
                                if (store.getComponent(ref, Nameplate.getComponentType()) == null) {
                                    allValid = false;
                                    break;
                                }
                            } catch (IllegalStateException e) {
                                allValid = false;
                                break;
                            }
                        }
                        if (allValid) {
                            logger.fine("Display at " + position + " still valid, skipping");
                            return;
                        }
                        logger.info("Display at " + position + " entities invalidated, recreating");
                    }

                    // Entities are invalid or missing - recreate them
                    recreateDisplayEntities(store, world, position, posKey, textLines);

                } catch (Exception e) {
                    logger.warning("Display validation failed at " + posKey + ": " + e.getMessage());
                    e.printStackTrace();
                    dirtyDisplays.add(posKey);
                }
            });
            return;
        }

        // Text changed or no existing display - remove old and create new
        removeDisplay(world, position);

        logger.fine("Creating display for sign at " + position + " with " + textLines.size() + " lines");

        // Store the text we're creating
        displayText.put(posKey, new ArrayList<>(textLines));

        ensureChunkAndRun(world, position, posKey, () -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                recreateDisplayEntities(store, world, position, posKey, textLines);
            } catch (Exception e) {
                logger.severe("Failed to create sign display at " + posKey + ": " + e.getMessage());
                e.printStackTrace();
                dirtyDisplays.add(posKey);
            }
        });
    }

    /**
     * Runs the action on the world thread if the chunk at the given block
     * position is loaded. If the chunk is not loaded, the display is marked
     * dirty for later retry — the ChunkPreLoadProcessEvent handler will
     * spawn it when a player approaches and the chunk loads naturally.
     *
     * Previously this method force-loaded chunks via getChunkAsync(), but that
     * caused an infinite load-unload cycle for signs in player-less areas:
     * chunk loads → entities spawn → chunk unloads (no players) → refs
     * invalidated → dirty → repeat every 10 seconds with error spam.
     *
     * @param world    the world containing the sign
     * @param position the block position of the sign
     * @param posKey   the position key for logging and dirty tracking
     * @param action   the action to run on the world thread once chunk is ready
     */
    private void ensureChunkAndRun(World world, Vector3i position, String posKey, Runnable action) {
        long chunkIdx = ChunkUtil.indexChunkFromBlock(position.getX(), position.getZ());

        world.execute(() -> {
            WorldChunk chunk = world.getChunkIfLoaded(chunkIdx);
            if (chunk != null) {
                action.run();
            } else {
                // Chunk not loaded - no players nearby, defer to chunk load event
                logger.fine("Chunk not loaded for sign at " + posKey + ", deferring to chunk load event");
                dirtyDisplays.add(posKey);
            }
        });
    }

    /**
     * Internal method to create display entities. MUST be called from world thread.
     */
    private void recreateDisplayEntities(Store<EntityStore> store, World world, Vector3i position,
                                         String posKey, List<String> textLines) {
        // Remove any existing entities for this position
        List<Ref<EntityStore>> oldRefs = displayEntities.remove(posKey);
        if (oldRefs != null) {
            for (Ref<EntityStore> ref : oldRefs) {
                if (ref == null || !ref.isValid()) continue;
                try {
                    store.removeEntity(ref, RemoveReason.REMOVE);
                } catch (Exception e) {
                    logger.fine("Old display entity already removed at " + posKey + ": " + e.getMessage());
                }
            }
        }

        // Store text tracking
        displayText.put(posKey, new ArrayList<>(textLines));

        List<Ref<EntityStore>> entityRefs = new ArrayList<>();
        int failedLines = 0;

        // Create one entity per line, stacked from top to bottom
        for (int i = 0; i < textLines.size(); i++) {
            String lineText = textLines.get(i);

            // Calculate Y position - first line at top, subsequent lines below
            double yOffset = BASE_Y_OFFSET - (i * LINE_HEIGHT);

            Vector3d displayPos = new Vector3d(
                position.getX() + 0.5,
                position.getY() + yOffset,
                position.getZ() + 0.5
            );

            try {
                // Spawn the entity
                SignDisplayEntity displayEntity = new SignDisplayEntity(world);
                SignDisplayEntity spawned = world.spawnEntity(displayEntity, displayPos, new Vector3f(0, 0, 0));

                if (spawned == null) {
                    logger.warning("spawnEntity returned null for sign line " + i + " at " + posKey
                        + " (pos=" + displayPos + ")");
                    failedLines++;
                    continue;
                }

                Ref<EntityStore> entityRef = spawned.getReference();
                if (entityRef == null) {
                    logger.warning("Spawned entity has null reference for sign line " + i + " at " + posKey);
                    failedLines++;
                    continue;
                }

                // Add nameplate with this line's text
                Nameplate nameplate = store.ensureAndGetComponent(entityRef, Nameplate.getComponentType());
                nameplate.setText(lineText);

                // Add Visible component
                store.ensureAndGetComponent(entityRef, EntityTrackerSystems.Visible.getComponentType());

                // Add tiny ModelComponent using cached model
                if (TINY_MODEL != null) {
                    try {
                        store.addComponent(entityRef, ModelComponent.getComponentType(), new ModelComponent(TINY_MODEL));
                    } catch (Exception modelEx) {
                        logger.fine("ModelComponent add failed for sign at " + posKey + " line " + i
                            + ": " + modelEx.getMessage());
                    }
                }

                entityRefs.add(entityRef);
            } catch (Exception e) {
                logger.severe("Exception spawning display entity for sign line " + i + " at " + posKey
                    + ": " + e.getMessage());
                e.printStackTrace();
                failedLines++;
            }
        }

        // Track all entities for this sign
        if (!entityRefs.isEmpty()) {
            displayEntities.put(posKey, entityRefs);
            if (failedLines > 0) {
                logger.warning("Created " + entityRefs.size() + "/" + textLines.size()
                    + " display entities at " + posKey + " (" + failedLines + " lines failed)");
                // Re-queue so the failed lines get retried
                dirtyDisplays.add(posKey);
            } else {
                logger.fine("Created " + entityRefs.size() + " display entities at " + posKey);
            }
        } else {
            logger.severe("Failed to create ANY display entities for sign at " + posKey
                + " (" + textLines.size() + " lines attempted). Re-queuing for retry.");
            dirtyDisplays.add(posKey);
        }
    }

    /**
     * Update the display for an existing sign.
     * Attempts in-place update if line count matches, otherwise recreates.
     * Ensures chunk is loaded before modifying entities.
     */
    public void updateDisplay(World world, Vector3i position, String[] lines) {
        if (world == null || position == null || lines == null) {
            logger.warning("updateDisplay called with null argument: world=" + world
                + " position=" + position + " lines=" + (lines != null ? lines.length : "null"));
            return;
        }

        String posKey = getPositionKey(world, position);

        // Collect non-empty lines
        List<String> textLines = new ArrayList<>();
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                textLines.add(line);
            }
        }

        // Check if text is identical (skip update entirely)
        List<String> existingText = displayText.get(posKey);
        if (existingText != null && existingText.equals(textLines)) {
            logger.fine("Display at " + position + " unchanged, skipping update");
            return;
        }

        // Get existing entities
        List<Ref<EntityStore>> existingRefs = displayEntities.get(posKey);

        // If line count matches and we have existing entities, try in-place update
        if (existingRefs != null && existingRefs.size() == textLines.size() && !textLines.isEmpty()) {
            logger.fine("In-place update for sign at " + position + " (" + textLines.size() + " lines)");

            // Update text tracking
            displayText.put(posKey, new ArrayList<>(textLines));

            ensureChunkAndRun(world, position, posKey, () -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    for (int i = 0; i < textLines.size(); i++) {
                        Ref<EntityStore> entityRef = existingRefs.get(i);
                        if (entityRef == null || !entityRef.isValid()) {
                            logger.warning("Invalid entity ref for sign line " + i + " at " + posKey
                                + " during in-place update, falling back to recreate");
                            createDisplay(world, position, lines);
                            return;
                        }

                        // Update nameplate text directly
                        Nameplate nameplate = store.getComponent(entityRef, Nameplate.getComponentType());
                        if (nameplate != null) {
                            nameplate.setText(textLines.get(i));
                        } else {
                            logger.warning("Nameplate component missing for sign line " + i + " at " + posKey
                                + ", entity may have been destroyed");
                        }
                    }

                    logger.fine("In-place updated " + textLines.size() + " display entities at " + posKey);
                } catch (Exception e) {
                    logger.warning("Failed in-place update at " + posKey + ", falling back to recreate: "
                        + e.getMessage());
                    createDisplay(world, position, lines);
                }
            });
        } else {
            // Line count changed or no existing display - recreate
            createDisplay(world, position, lines);
        }
    }

    /**
     * Remove all display entities for a sign.
     */
    public void removeDisplay(World world, Vector3i position) {
        if (position == null) {
            return;
        }

        String posKey = world != null ? getPositionKey(world, position) : null;
        if (posKey == null) return;

        List<Ref<EntityStore>> entityRefs = displayEntities.remove(posKey);
        displayText.remove(posKey); // Also clean up text tracking
        dirtyDisplays.remove(posKey); // Clean up dirty tracking

        if (entityRefs != null && !entityRefs.isEmpty() && world != null) {
            world.execute(() -> {
                Store<EntityStore> store = world.getEntityStore().getStore();
                int removed = 0;
                for (Ref<EntityStore> entityRef : entityRefs) {
                    if (entityRef == null || !entityRef.isValid()) continue;
                    try {
                        store.removeEntity(entityRef, RemoveReason.REMOVE);
                        removed++;
                    } catch (Exception e) {
                        logger.fine("Could not remove display entity at " + posKey
                            + " (already removed?): " + e.getMessage());
                    }
                }
                logger.fine("Removed " + removed + "/" + entityRefs.size()
                    + " display entities at " + posKey);
            });
        }
    }

    /**
     * Clean up all display entities.
     */
    public void cleanup() {
        logger.info("Cleaning up sign displays...");
        displayEntities.clear();
        displayText.clear();
    }

    /**
     * Check if a display exists for the given position.
     */
    public boolean hasDisplay(World world, Vector3i position) {
        if (world == null || position == null) return false;
        String posKey = getPositionKey(world, position);
        return displayEntities.containsKey(posKey);
    }

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

    /**
     * Clear tracking for a specific position, forcing recreation on next createDisplay call.
     * Used when entities may have been removed by the game.
     */
    public void invalidateDisplay(World world, Vector3i position) {
        if (world == null || position == null) return;
        String posKey = getPositionKey(world, position);
        displayEntities.remove(posKey);
        displayText.remove(posKey);
    }

    /**
     * Clear all display tracking. Forces all displays to be recreated on next spawn attempt.
     * Use this when entities may have been invalidated by chunk unloads etc.
     */
    public void invalidateAllDisplays() {
        displayEntities.clear();
        displayText.clear();
        logger.fine("Invalidated all display tracking");
    }

    /**
     * Mark a display as dirty (needs refresh).
     * Called when entities may have been removed/invalidated.
     */
    public void markDisplayDirty(String posKey) {
        dirtyDisplays.add(posKey);
    }

    /**
     * Mark all displays in a world as dirty (e.g., after chunk unload).
     */
    public void markWorldDirty(String worldName) {
        String prefix = worldName + ":";
        for (String posKey : displayEntities.keySet()) {
            if (posKey.startsWith(prefix)) {
                dirtyDisplays.add(posKey);
            }
        }
    }

    /**
     * Get all dirty displays and clear the dirty set.
     * Returns a snapshot of dirty position keys.
     */
    public Set<String> getDirtyDisplaysAndClear() {
        if (dirtyDisplays.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new HashSet<>(dirtyDisplays);
        dirtyDisplays.clear();
        return result;
    }

    /**
     * Check if there are any dirty displays pending refresh.
     */
    public boolean hasDirtyDisplays() {
        return !dirtyDisplays.isEmpty();
    }
}
