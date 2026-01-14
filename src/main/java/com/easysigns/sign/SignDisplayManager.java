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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages the visual display of sign text in the world.
 *
 * Uses invisible marker entities with Nameplate components to show
 * floating text above sign blocks. Each line is a separate entity
 * stacked vertically.
 */
public class SignDisplayManager {

    private final EasySigns plugin;
    private final Logger logger;

    // Maps position key -> list of entity references (one per line)
    // Key format: "world:x:y:z"
    private final Map<String, List<Ref<EntityStore>>> displayEntities;

    // Spacing between lines
    private static final double LINE_HEIGHT = 0.25;
    // Base Y offset from block position
    private static final double BASE_Y_OFFSET = 1.0;

    public SignDisplayManager(EasySigns plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        this.displayEntities = new ConcurrentHashMap<>();
    }

    /**
     * Create display entities for a sign at the given position.
     * Creates one entity per line, stacked vertically.
     */
    public void createDisplay(World world, Vector3i position, String[] lines) {
        if (world == null || position == null || lines == null) {
            return;
        }

        String posKey = getPositionKey(world, position);

        // Remove existing display if present
        removeDisplay(world, position);

        // Collect non-empty lines
        List<String> textLines = new ArrayList<>();
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                textLines.add(line);
            }
        }

        if (textLines.isEmpty()) {
            logger.info("No text for sign at " + position + ", skipping display");
            return;
        }

        logger.info("Creating display for sign at " + position + " with " + textLines.size() + " lines");

        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                List<Ref<EntityStore>> entityRefs = new ArrayList<>();

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

                    // Spawn the entity
                    SignDisplayEntity displayEntity = new SignDisplayEntity(world);
                    SignDisplayEntity spawned = world.spawnEntity(displayEntity, displayPos, new Vector3f(0, 0, 0));

                    if (spawned == null) {
                        logger.warning("Failed to spawn display entity for line " + i);
                        continue;
                    }

                    Ref<EntityStore> entityRef = spawned.getReference();
                    if (entityRef == null) {
                        logger.warning("Failed to get entity reference for line " + i);
                        continue;
                    }

                    // Add nameplate with this line's text
                    Nameplate nameplate = store.ensureAndGetComponent(entityRef, Nameplate.getComponentType());
                    nameplate.setText(lineText);

                    // Add Visible component
                    store.ensureAndGetComponent(entityRef, EntityTrackerSystems.Visible.getComponentType());

                    // Add tiny ModelComponent
                    try {
                        Model.ModelReference modelRef = new Model.ModelReference("hytale:player", 0.01f, null);
                        Model model = modelRef.toModel();
                        if (model != null) {
                            store.addComponent(entityRef, ModelComponent.getComponentType(), new ModelComponent(model));
                        }
                    } catch (Exception modelEx) {
                        // Ignore model errors
                    }

                    entityRefs.add(entityRef);
                }

                // Track all entities for this sign
                if (!entityRefs.isEmpty()) {
                    displayEntities.put(posKey, entityRefs);
                    logger.info("SUCCESS: Created " + entityRefs.size() + " display entities at " + position);
                }

            } catch (Exception e) {
                logger.warning("Failed to create sign display: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Update the display for an existing sign.
     */
    public void updateDisplay(World world, Vector3i position, String[] lines) {
        // Just recreate the display - simpler than trying to update individual lines
        createDisplay(world, position, lines);
    }

    /**
     * Remove all display entities for a sign.
     */
    public void removeDisplay(World world, Vector3i position) {
        if (position == null) {
            return;
        }

        String posKey = world != null ? getPositionKey(world, position) : null;
        List<Ref<EntityStore>> entityRefs = posKey != null ? displayEntities.remove(posKey) : null;

        if (entityRefs != null && !entityRefs.isEmpty() && world != null) {
            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    for (Ref<EntityStore> entityRef : entityRefs) {
                        store.removeEntity(entityRef, RemoveReason.REMOVE);
                    }
                    logger.fine("Removed " + entityRefs.size() + " display entities at " + position);
                } catch (Exception e) {
                    logger.warning("Failed to remove sign display: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Clean up all display entities.
     */
    public void cleanup() {
        logger.info("Cleaning up sign displays...");
        displayEntities.clear();
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
}
