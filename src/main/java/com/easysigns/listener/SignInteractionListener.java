package com.easysigns.listener;

import com.easysigns.EasySigns;
import com.easysigns.data.SignData;
import com.easysigns.data.SignStorage;
import com.easysigns.ui.SignEditPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.awt.Color;
import java.util.logging.Logger;

/**
 * Listens for player interactions with sign blocks.
 * Opens the sign editor UI when a player right-clicks a sign.
 */
public class SignInteractionListener {

    private final EasySigns plugin;
    private final SignStorage signStorage;
    private final Logger logger;

    public SignInteractionListener(EasySigns plugin, SignStorage signStorage) {
        this.plugin = plugin;
        this.signStorage = signStorage;
        this.logger = plugin.getPluginLogger();
    }

    /**
     * Register event handlers.
     */
    public void register(EventRegistry eventRegistry) {
        eventRegistry.registerGlobal(PlayerInteractEvent.class, this::onPlayerInteract);
        logger.info("Registered sign interaction listener");
    }

    /**
     * Handle player interaction events.
     */
    private void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle secondary (right-click) interactions
        if (event.getActionType() != InteractionType.Secondary) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) return;

        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) return;

        World world = player.getWorld();
        if (world == null) return;

        String worldName = world.getName();

        // Check if there's a sign at this position
        SignData signData = signStorage.getSign(worldName, targetBlock);
        if (signData == null) {
            return; // No sign here
        }

        // Check permission
        if (!player.hasPermission("signs.use")) {
            return; // No permission to edit signs
        }

        // Cancel the interaction to prevent other actions
        event.setCancelled(true);

        // Get the entity ref and store to get PlayerRef component
        Ref<EntityStore> entityRef = event.getPlayerRef();
        if (entityRef == null) return;

        Store<EntityStore> store = world.getEntityStore().getStore();
        PlayerRef playerRefComponent = store.getComponent(entityRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) return;

        // Open the sign editor UI
        try {
            SignEditPage editPage = new SignEditPage(
                plugin,
                signStorage,
                worldName,
                targetBlock,
                signData,
                playerRefComponent,
                world
            );

            player.getPageManager().openCustomPage(entityRef, store, editPage);
            logger.fine("Opened sign editor for player at " + targetBlock);

        } catch (Exception e) {
            logger.warning("Failed to open sign editor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
