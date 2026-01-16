package com.easysigns.listener;

import com.easysigns.EasySigns;
import com.easysigns.session.SignEditSession;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Listens for player disconnections to clean up editing sessions.
 * Prevents memory leaks from abandoned edit sessions.
 */
public class SignDisconnectListener {

    private final EasySigns plugin;
    private final Logger logger;

    public SignDisconnectListener(EasySigns plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
    }

    /**
     * Register event handlers.
     */
    public void register(EventRegistry eventRegistry) {
        eventRegistry.registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        logger.info("Registered sign disconnect listener");
    }

    /**
     * Handle player disconnect events.
     * Cleans up any active editing sessions to prevent memory leaks.
     */
    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef player = event.getPlayerRef();
        if (player == null) return;

        UUID playerId = player.getUuid();

        // Check if player was editing a sign and clean up
        if (SignEditSession.isEditing(playerId)) {
            SignEditSession.EditingState state = SignEditSession.endEditing(playerId);
            if (state != null) {
                // Remove the incomplete sign since player disconnected mid-edit
                plugin.getSignStorage().removeSign(state.worldName, state.position);

                // Remove the display if it was created
                if (state.world != null) {
                    plugin.getDisplayManager().removeDisplay(state.world, state.position);
                }

                logger.fine("Cleaned up editing session for disconnected player: " + player.getUsername());
            }
        }
    }
}
