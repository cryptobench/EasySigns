package com.easysigns.systems;

import com.easysigns.EasySigns;
import com.easysigns.data.SignData;
import com.easysigns.data.SignStorage;
import com.easysigns.ui.SignEditPage;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;

/**
 * ECS System that intercepts block place events to detect when a sign is placed.
 * Opens the sign editor UI when a player places a sign block.
 */
public class SignPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private final EasySigns plugin;
    private final SignStorage signStorage;
    private final Logger logger;

    public SignPlaceSystem(EasySigns plugin, SignStorage signStorage) {
        super(PlaceBlockEvent.class);
        this.plugin = plugin;
        this.signStorage = signStorage;
        this.logger = plugin.getPluginLogger();
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }

    @Override
    public void handle(int entityIndex, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlaceBlockEvent event) {

        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) return;

        // Get the entity that triggered this event
        Ref<EntityStore> entityRef = chunk.getReferenceTo(entityIndex);
        if (entityRef == null) return;

        // Get player components
        Player player = store.getComponent(entityRef, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
        if (player == null || playerRef == null) return;

        // Check if the placed item is a sign
        ItemStack itemInHand = event.getItemInHand();
        if (itemInHand == null || itemInHand.isEmpty()) return;

        String itemId = itemInHand.getItemId();
        String blockKey = itemInHand.getBlockKey();

        // Check if this is a sign block (contains "Sign" in the ID or block key)
        if (!isSignBlock(itemId, blockKey)) {
            return;
        }

        // Check permission
        if (!player.hasPermission("signs.use")) {
            return; // No permission to create signs
        }

        World world = player.getWorld();
        if (world == null) return;

        String worldName = world.getName();

        logger.fine("Sign placed at " + targetBlock + " in " + worldName + " by " + playerRef.getUsername());

        // Create sign data for this position with owner in a single operation (avoids double save)
        SignData signData = signStorage.createSignWithOwner(
            worldName,
            targetBlock,
            playerRef.getUuid().toString(),
            playerRef.getUsername()
        );

        // Open the sign editor UI
        try {
            SignEditPage editPage = new SignEditPage(
                plugin,
                signStorage,
                worldName,
                targetBlock,
                signData,
                playerRef,
                world
            );

            player.getPageManager().openCustomPage(entityRef, store, editPage);
            logger.fine("Opened sign editor for player at " + targetBlock);

        } catch (Exception e) {
            logger.warning("Failed to open sign editor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if the item/block is a sign based on its ID or block key.
     * Uses allocation-free case-insensitive check for performance.
     */
    private boolean isSignBlock(String itemId, String blockKey) {
        return containsIgnoreCase(itemId, "sign") || containsIgnoreCase(blockKey, "sign");
    }

    /**
     * Allocation-free case-insensitive contains check.
     * Avoids creating new strings via toLowerCase() on every block place.
     */
    private static boolean containsIgnoreCase(String str, String search) {
        if (str == null || str.length() < search.length()) return false;
        for (int i = 0; i <= str.length() - search.length(); i++) {
            if (str.regionMatches(true, i, search, 0, search.length())) {
                return true;
            }
        }
        return false;
    }
}
