package com.easysigns.session;

import com.easysigns.data.SignData;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages sign editing sessions for players.
 * When a player is editing a sign, their chat messages are captured as sign text.
 */
public class SignEditSession {

    private static final Map<UUID, EditingState> editingSessions = new ConcurrentHashMap<>();

    /**
     * State for a player's current sign editing session.
     */
    public static class EditingState {
        public final String worldName;
        public final Vector3i position;
        public final SignData signData;
        public final World world;
        public int currentLine = 0;

        public EditingState(String worldName, Vector3i position, SignData signData, World world) {
            this.worldName = worldName;
            this.position = position;
            this.signData = signData;
            this.world = world;
        }
    }

    /**
     * Start an editing session for a player.
     */
    public static void startEditing(UUID playerId, String worldName, Vector3i position, SignData signData, World world) {
        editingSessions.put(playerId, new EditingState(worldName, position, signData, world));
    }

    /**
     * Check if a player is currently editing a sign.
     */
    public static boolean isEditing(UUID playerId) {
        return editingSessions.containsKey(playerId);
    }

    /**
     * Get the editing state for a player.
     */
    public static EditingState getEditingState(UUID playerId) {
        return editingSessions.get(playerId);
    }

    /**
     * End an editing session for a player.
     */
    public static EditingState endEditing(UUID playerId) {
        return editingSessions.remove(playerId);
    }

    /**
     * Add a line of text to the sign being edited.
     * Returns true if there are more lines to add, false if done.
     */
    public static boolean addLine(UUID playerId, String text) {
        EditingState state = editingSessions.get(playerId);
        if (state == null) return false;

        if (state.currentLine < SignData.MAX_LINES) {
            state.signData.setLine(state.currentLine, text);
            state.currentLine++;
        }

        return state.currentLine < SignData.MAX_LINES;
    }

    /**
     * Get how many lines remain to be entered.
     */
    public static int getLinesRemaining(UUID playerId) {
        EditingState state = editingSessions.get(playerId);
        if (state == null) return 0;
        return SignData.MAX_LINES - state.currentLine;
    }

    /**
     * Get the current line number being edited (1-indexed for display).
     */
    public static int getCurrentLineNumber(UUID playerId) {
        EditingState state = editingSessions.get(playerId);
        if (state == null) return 0;
        return state.currentLine + 1;
    }
}
