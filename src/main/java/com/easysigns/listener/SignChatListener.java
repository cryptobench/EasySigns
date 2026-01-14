package com.easysigns.listener;

import com.easysigns.EasySigns;
import com.easysigns.data.SignData;
import com.easysigns.data.SignStorage;
import com.easysigns.session.SignEditSession;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.awt.Color;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Listens for chat messages from players who are editing signs.
 * Captures their messages as sign text.
 */
public class SignChatListener {

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color YELLOW = new Color(255, 255, 85);
    private static final Color GOLD = new Color(255, 170, 0);
    private static final Color GRAY = new Color(170, 170, 170);

    private final EasySigns plugin;
    private final SignStorage signStorage;
    private final Logger logger;

    public SignChatListener(EasySigns plugin, SignStorage signStorage) {
        this.plugin = plugin;
        this.signStorage = signStorage;
        this.logger = plugin.getPluginLogger();
    }

    /**
     * Register event handlers.
     */
    public void register(EventRegistry eventRegistry) {
        eventRegistry.registerGlobal(PlayerChatEvent.class, this::onPlayerChat);
        logger.info("Registered sign chat listener");
    }

    /**
     * Handle player chat events.
     */
    private void onPlayerChat(PlayerChatEvent event) {
        PlayerRef sender = event.getSender();
        if (sender == null) return;

        UUID playerId = sender.getUuid();

        // Check if player is editing a sign
        if (!SignEditSession.isEditing(playerId)) {
            return;
        }

        // Cancel the chat message so it doesn't show publicly
        event.setCancelled(true);

        String content = event.getContent();

        // Check for "done" command to finish editing
        if (content.equalsIgnoreCase("done") || content.equalsIgnoreCase("/done")) {
            finishEditing(sender);
            return;
        }

        // Check for "cancel" command
        if (content.equalsIgnoreCase("cancel") || content.equalsIgnoreCase("/cancel")) {
            cancelEditing(sender);
            return;
        }

        // Check for "skip" command to leave line blank
        if (content.equalsIgnoreCase("skip") || content.equalsIgnoreCase("/skip")) {
            content = "";
        }

        // Add the line to the sign
        int lineNum = SignEditSession.getCurrentLineNumber(playerId);
        boolean hasMoreLines = SignEditSession.addLine(playerId, content);

        if (hasMoreLines) {
            // Prompt for next line
            int nextLine = SignEditSession.getCurrentLineNumber(playerId);
            sender.sendMessage(Message.raw("Line " + lineNum + " set. Enter line " + nextLine + ":").color(YELLOW));
        } else {
            // All lines entered, finish editing
            finishEditing(sender);
        }
    }

    /**
     * Finish editing and save the sign.
     */
    private void finishEditing(PlayerRef sender) {
        UUID playerId = sender.getUuid();
        SignEditSession.EditingState state = SignEditSession.endEditing(playerId);

        if (state != null) {
            // Save the sign
            signStorage.updateSign(state.worldName, state.position, state.signData);

            // Update the floating text display
            if (state.world != null) {
                plugin.getDisplayManager().createDisplay(state.world, state.position, state.signData.getLines());
            }

            sender.sendMessage(Message.raw("Sign saved!").color(GREEN));

            // Show preview
            sender.sendMessage(Message.raw("--- Sign Text ---").color(GOLD));
            for (int i = 0; i < SignData.MAX_LINES; i++) {
                String line = state.signData.getLine(i);
                if (!line.isEmpty()) {
                    sender.sendMessage(Message.raw((i + 1) + ": " + line).color(GRAY));
                }
            }

            logger.info("Sign saved at " + state.worldName + ":" + state.position);
        }
    }

    /**
     * Cancel editing without saving.
     */
    private void cancelEditing(PlayerRef sender) {
        UUID playerId = sender.getUuid();
        SignEditSession.EditingState state = SignEditSession.endEditing(playerId);

        if (state != null) {
            // Remove the sign since we're canceling
            signStorage.removeSign(state.worldName, state.position);

            // Remove the floating text display
            if (state.world != null) {
                plugin.getDisplayManager().removeDisplay(state.world, state.position);
            }

            sender.sendMessage(Message.raw("Sign editing cancelled.").color(YELLOW));
        }
    }
}
