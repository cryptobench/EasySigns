package com.easysigns.command;

import com.easysigns.EasySigns;
import com.easysigns.data.SignData;
import com.easysigns.data.SignStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command for placing and managing floating text signs.
 *
 * Usage:
 *   /sign <text>           - Create floating text at your position (use | for multiple lines)
 *   /sign list             - List your signs with IDs
 *   /sign edit <id> <text> - Edit sign text by ID (keeps same location)
 *   /sign delete <id>      - Delete your sign by ID
 *   /sign remove           - Remove the nearest sign within 5 blocks (owner only)
 *   /sign status           - Show plugin status
 *   /sign help             - Show help
 *
 * Admin commands (requires signs.admin):
 *   /sign listall          - List ALL signs from all players
 *   /sign deleteany <id>   - Delete any sign by ID (bypass ownership)
 */
public class SignCommand extends AbstractPlayerCommand {

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);
    private static final Color YELLOW = new Color(255, 255, 85);
    private static final Color GOLD = new Color(255, 170, 0);
    private static final Color GRAY = new Color(170, 170, 170);

    private static final double REMOVE_RADIUS = 5.0;

    private final EasySigns plugin;
    private final SignStorage signStorage;

    public SignCommand(EasySigns plugin, SignStorage signStorage) {
        super("sign", "Create and manage floating text signs");
        this.plugin = plugin;
        this.signStorage = signStorage;
        setAllowsExtraArguments(true);
        requirePermission("signs.use");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> playerRef,
                          @Nonnull PlayerRef playerData,
                          @Nonnull World world) {

        // Parse arguments - everything after "/sign "
        String input = ctx.getInputString().trim();
        String afterCommand = input.length() > 5 ? input.substring(5).trim() : "";

        if (afterCommand.isEmpty()) {
            executeHelp(playerData);
            return;
        }

        // Check for subcommands
        String firstWord = afterCommand.split("\\s+")[0].toLowerCase();
        String rest = afterCommand.length() > firstWord.length()
            ? afterCommand.substring(firstWord.length()).trim()
            : "";

        // Get player for permission checks
        Player player = store.getComponent(playerRef, Player.getComponentType());

        switch (firstWord) {
            case "remove" -> executeRemove(playerData, playerRef, store, world);
            case "delete" -> executeDelete(playerData, world, rest);
            case "list" -> executeList(playerData, world);
            case "listall" -> executeListAll(playerData, player);
            case "deleteany" -> executeDeleteAny(playerData, player, world, rest);
            case "edit" -> executeEdit(playerData, player, world, rest);
            case "purge" -> executePurge(playerData, player, world, rest);
            case "reload" -> executeReload(playerData, player);
            case "status" -> executeStatus(playerData);
            case "help" -> executeHelp(playerData);
            default -> {
                // Treat the entire argument as text to display
                executeCreate(playerData, playerRef, store, world, afterCommand);
            }
        }
    }

    private void executeCreate(PlayerRef playerData, Ref<EntityStore> playerRef,
                               Store<EntityStore> store, World world, String text) {
        // Get player position
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            playerData.sendMessage(Message.raw("Could not get your position.").color(RED));
            return;
        }

        Vector3d pos = transform.getPosition();
        Vector3i blockPos = new Vector3i((int) Math.floor(pos.getX()), (int) Math.floor(pos.getY()), (int) Math.floor(pos.getZ()));
        String worldName = world.getName();

        // Check if sign already exists at this position
        if (signStorage.hasSign(worldName, blockPos)) {
            playerData.sendMessage(Message.raw("A sign already exists at this position. Use /sign remove first.").color(YELLOW));
            return;
        }

        // Check for banned words
        String[] manualLines = text.split("\\|");
        String bannedWord = plugin.getConfig().checkLinesForBannedWords(manualLines);
        if (bannedWord != null) {
            playerData.sendMessage(Message.raw(plugin.getConfig().getFilterMessage()).color(RED));
            plugin.getPluginLogger().warning("Player " + playerData.getUsername() + " tried to create sign with banned word: " + bannedWord);
            return;
        }

        // Create the sign data with owner
        SignData signData = signStorage.createSign(worldName, blockPos);
        signData.setOwner(playerData.getUuid(), playerData.getUsername());

        // Parse text - split by | for manual line breaks, then auto-wrap long lines
        String[] wrappedLines = wrapText(manualLines, SignData.MAX_LINE_LENGTH, SignData.MAX_LINES);
        for (int i = 0; i < wrappedLines.length && i < SignData.MAX_LINES; i++) {
            signData.setLine(i, wrappedLines[i].trim());
        }

        // Save the sign
        signStorage.updateSign(worldName, blockPos, signData);

        // Create the display entity
        plugin.getDisplayManager().createDisplay(world, blockPos, signData.getLines());

        playerData.sendMessage(Message.raw("Sign created at your position!").color(GREEN));

        // Show preview
        playerData.sendMessage(Message.raw("--- Sign Text ---").color(GOLD));
        for (int i = 0; i < SignData.MAX_LINES; i++) {
            String line = signData.getLine(i);
            if (line != null && !line.isEmpty()) {
                playerData.sendMessage(Message.raw((i + 1) + ": " + line).color(GRAY));
            }
        }
    }

    private void executeRemove(PlayerRef playerData, Ref<EntityStore> playerRef,
                               Store<EntityStore> store, World world) {
        // Get player position
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            playerData.sendMessage(Message.raw("Could not get your position.").color(RED));
            return;
        }

        Vector3d playerPos = transform.getPosition();
        String worldName = world.getName();
        Player player = store.getComponent(playerRef, Player.getComponentType());
        boolean isAdmin = player != null && player.hasPermission("signs.admin");

        // Find the nearest sign within radius
        Vector3i nearestSign = null;
        SignData nearestSignData = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (Vector3i signPos : signStorage.getSignsInWorld(worldName)) {
            double dx = playerPos.getX() - (signPos.getX() + 0.5);
            double dy = playerPos.getY() - (signPos.getY() + 0.5);
            double dz = playerPos.getZ() - (signPos.getZ() + 0.5);
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < nearestDistSq && distSq <= REMOVE_RADIUS * REMOVE_RADIUS) {
                nearestDistSq = distSq;
                nearestSign = signPos;
                nearestSignData = signStorage.getSign(worldName, signPos);
            }
        }

        if (nearestSign == null) {
            playerData.sendMessage(Message.raw("No sign found within " + (int) REMOVE_RADIUS + " blocks.").color(YELLOW));
            return;
        }

        // Check ownership (unless admin)
        if (!isAdmin && nearestSignData != null && !nearestSignData.isOwner(playerData.getUuid())) {
            String ownerName = nearestSignData.getOwnerName();
            playerData.sendMessage(Message.raw("This sign belongs to " + (ownerName != null ? ownerName : "someone else") + ".").color(RED));
            return;
        }

        // Remove the display entity first
        plugin.getDisplayManager().removeDisplay(world, nearestSign);

        // Remove from storage
        signStorage.removeSign(worldName, nearestSign);

        playerData.sendMessage(Message.raw("Sign removed at " + formatPos(nearestSign) + ".").color(GREEN));
    }

    private void executeList(PlayerRef playerData, World world) {
        String worldName = world.getName();
        List<Vector3i> signs = signStorage.getSignsInWorld(worldName);
        UUID playerUuid = playerData.getUuid();

        int count = 0;
        StringBuilder output = new StringBuilder();

        for (Vector3i pos : signs) {
            SignData data = signStorage.getSign(worldName, pos);
            if (data != null && data.isOwner(playerUuid)) {
                String preview = getPreview(data);
                output.append("[").append(data.getSignId()).append("] ")
                      .append(formatPos(pos)).append(" - ").append(preview).append("\n");
                count++;
            }
        }

        if (count == 0) {
            playerData.sendMessage(Message.raw("You have no signs in this world.").color(YELLOW));
            return;
        }

        playerData.sendMessage(Message.raw("=== Your Signs (" + count + ") ===").color(GOLD));
        for (String line : output.toString().split("\n")) {
            if (!line.isEmpty()) {
                playerData.sendMessage(Message.raw(line).color(GRAY));
            }
        }
        playerData.sendMessage(Message.raw("Use /sign delete <id> to remove a sign.").color(YELLOW));
    }

    private void executeDelete(PlayerRef playerData, World world, String idStr) {
        if (idStr.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /sign delete <id>").color(YELLOW));
            playerData.sendMessage(Message.raw("Use /sign list to see sign IDs.").color(GRAY));
            return;
        }

        // Find sign by ID
        Map.Entry<String, SignData> entry = signStorage.getSignById(idStr);
        if (entry == null) {
            playerData.sendMessage(Message.raw("Sign with ID '" + idStr + "' not found.").color(RED));
            playerData.sendMessage(Message.raw("Use /sign list to see your sign IDs.").color(GRAY));
            return;
        }

        SignData signData = entry.getValue();

        // Check ownership
        if (!signData.isOwner(playerData.getUuid())) {
            playerData.sendMessage(Message.raw("This sign belongs to " +
                (signData.getOwnerName() != null ? signData.getOwnerName() : "someone else") + ".").color(RED));
            return;
        }

        // Parse position from key
        String[] parts = SignStorage.parseKey(entry.getKey());
        if (parts == null) {
            playerData.sendMessage(Message.raw("Error parsing sign position.").color(RED));
            return;
        }

        String worldName = parts[0];
        Vector3i pos = new Vector3i(
            Integer.parseInt(parts[1]),
            Integer.parseInt(parts[2]),
            Integer.parseInt(parts[3])
        );

        // Remove the display entity
        World signWorld = world.getName().equals(worldName) ? world : null;
        if (signWorld != null) {
            plugin.getDisplayManager().removeDisplay(signWorld, pos);
        }

        // Remove from storage
        signStorage.removeSignById(idStr);

        playerData.sendMessage(Message.raw("Sign [" + idStr + "] at " + formatPos(pos) + " deleted.").color(GREEN));
    }

    /**
     * Edit an existing sign's text by ID.
     */
    private void executeEdit(PlayerRef playerData, Player player, World world, String args) {
        // Parse: <id> <new text>
        if (args.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /sign edit <id> <new text>").color(YELLOW));
            playerData.sendMessage(Message.raw("Use /sign list to see your sign IDs.").color(GRAY));
            return;
        }

        // Split into id and text
        String[] parts = args.split("\\s+", 2);
        String idStr = parts[0];
        String newText = parts.length > 1 ? parts[1] : "";

        if (newText.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /sign edit <id> <new text>").color(YELLOW));
            playerData.sendMessage(Message.raw("Use | to separate lines: /sign edit " + idStr + " Line1|Line2").color(GRAY));
            return;
        }

        // Find sign by ID
        Map.Entry<String, SignData> entry = signStorage.getSignById(idStr);
        if (entry == null) {
            playerData.sendMessage(Message.raw("Sign with ID '" + idStr + "' not found.").color(RED));
            playerData.sendMessage(Message.raw("Use /sign list to see your sign IDs.").color(GRAY));
            return;
        }

        SignData signData = entry.getValue();
        boolean isAdmin = player != null && player.hasPermission("signs.admin");

        // Check ownership (unless admin)
        if (!isAdmin && !signData.isOwner(playerData.getUuid())) {
            playerData.sendMessage(Message.raw("This sign belongs to " +
                (signData.getOwnerName() != null ? signData.getOwnerName() : "someone else") + ".").color(RED));
            return;
        }

        // Check for banned words
        String[] manualLines = newText.split("\\|");
        String bannedWord = plugin.getConfig().checkLinesForBannedWords(manualLines);
        if (bannedWord != null) {
            playerData.sendMessage(Message.raw(plugin.getConfig().getFilterMessage()).color(RED));
            plugin.getPluginLogger().warning("Player " + playerData.getUsername() + " tried to edit sign with banned word: " + bannedWord);
            return;
        }

        // Parse position from key
        String[] keyParts = SignStorage.parseKey(entry.getKey());
        if (keyParts == null) {
            playerData.sendMessage(Message.raw("Error parsing sign position.").color(RED));
            return;
        }

        String worldName = keyParts[0];
        Vector3i pos = new Vector3i(
            Integer.parseInt(keyParts[1]),
            Integer.parseInt(keyParts[2]),
            Integer.parseInt(keyParts[3])
        );

        // Parse and wrap text
        String[] wrappedLines = wrapText(manualLines, SignData.MAX_LINE_LENGTH, SignData.MAX_LINES);

        // Update sign data
        for (int i = 0; i < SignData.MAX_LINES; i++) {
            signData.setLine(i, i < wrappedLines.length ? wrappedLines[i].trim() : "");
        }

        // Save to storage
        signStorage.updateSign(worldName, pos, signData);

        // Update the display if in the same world
        if (world.getName().equals(worldName)) {
            plugin.getDisplayManager().updateDisplay(world, pos, signData.getLines());
        }

        playerData.sendMessage(Message.raw("Sign [" + idStr + "] updated!").color(GREEN));

        // Show preview
        playerData.sendMessage(Message.raw("--- New Sign Text ---").color(GOLD));
        for (int i = 0; i < SignData.MAX_LINES; i++) {
            String line = signData.getLine(i);
            if (line != null && !line.isEmpty()) {
                playerData.sendMessage(Message.raw((i + 1) + ": " + line).color(GRAY));
            }
        }
    }

    /**
     * Admin command: List ALL signs from all players.
     */
    private void executeListAll(PlayerRef playerData, Player player) {
        // Check admin permission
        if (player == null || !player.hasPermission("signs.admin")) {
            playerData.sendMessage(Message.raw("You need signs.admin permission.").color(RED));
            return;
        }

        Map<String, SignData> allSigns = signStorage.getAllSigns();

        if (allSigns.isEmpty()) {
            playerData.sendMessage(Message.raw("No signs exist on the server.").color(YELLOW));
            return;
        }

        playerData.sendMessage(Message.raw("=== All Signs (" + allSigns.size() + ") ===").color(GOLD));

        for (Map.Entry<String, SignData> entry : allSigns.entrySet()) {
            SignData data = entry.getValue();
            String[] parts = SignStorage.parseKey(entry.getKey());
            String posStr = parts != null ?
                "(" + parts[1] + "," + parts[2] + "," + parts[3] + ") in " + parts[0] :
                entry.getKey();

            String ownerName = data.getOwnerName() != null ? data.getOwnerName() : "Unknown";
            String preview = getPreview(data);

            playerData.sendMessage(Message.raw("[" + data.getSignId() + "] " + posStr).color(GRAY));
            playerData.sendMessage(Message.raw("  Owner: " + ownerName + " | Text: " + preview).color(GRAY));
        }

        playerData.sendMessage(Message.raw("Use /sign deleteany <id> to remove any sign.").color(YELLOW));
    }

    /**
     * Admin command: Delete any sign by ID (bypass ownership).
     */
    private void executeDeleteAny(PlayerRef playerData, Player player, World world, String idStr) {
        // Check admin permission
        if (player == null || !player.hasPermission("signs.admin")) {
            playerData.sendMessage(Message.raw("You need signs.admin permission.").color(RED));
            return;
        }

        if (idStr.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /sign deleteany <id>").color(YELLOW));
            playerData.sendMessage(Message.raw("Use /sign listall to see all sign IDs.").color(GRAY));
            return;
        }

        // Find sign by ID
        Map.Entry<String, SignData> entry = signStorage.getSignById(idStr);
        if (entry == null) {
            playerData.sendMessage(Message.raw("Sign with ID '" + idStr + "' not found.").color(RED));
            return;
        }

        SignData signData = entry.getValue();
        String ownerName = signData.getOwnerName() != null ? signData.getOwnerName() : "Unknown";

        // Parse position from key
        String[] parts = SignStorage.parseKey(entry.getKey());
        if (parts == null) {
            playerData.sendMessage(Message.raw("Error parsing sign position.").color(RED));
            return;
        }

        String worldName = parts[0];
        Vector3i pos = new Vector3i(
            Integer.parseInt(parts[1]),
            Integer.parseInt(parts[2]),
            Integer.parseInt(parts[3])
        );

        // Remove the display entity
        World signWorld = world.getName().equals(worldName) ? world : null;
        if (signWorld != null) {
            plugin.getDisplayManager().removeDisplay(signWorld, pos);
        }

        // Remove from storage
        signStorage.removeSignById(idStr);

        playerData.sendMessage(Message.raw("Sign [" + idStr + "] owned by " + ownerName + " at " + formatPos(pos) + " deleted.").color(GREEN));
    }

    /**
     * Admin command: Purge all signs from a specific player.
     */
    private void executePurge(PlayerRef playerData, Player player, World world, String playerName) {
        // Check admin permission
        if (player == null || !player.hasPermission("signs.admin")) {
            playerData.sendMessage(Message.raw("You need signs.admin permission.").color(RED));
            return;
        }

        if (playerName.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /sign purge <playername>").color(YELLOW));
            playerData.sendMessage(Message.raw("Deletes ALL signs owned by that player.").color(GRAY));
            return;
        }

        Map<String, SignData> allSigns = signStorage.getAllSigns();
        int purgedCount = 0;

        for (Map.Entry<String, SignData> entry : allSigns.entrySet()) {
            SignData data = entry.getValue();
            String ownerName = data.getOwnerName();

            // Match by owner name (case insensitive)
            if (ownerName != null && ownerName.equalsIgnoreCase(playerName)) {
                String[] parts = SignStorage.parseKey(entry.getKey());
                if (parts != null) {
                    String worldName = parts[0];
                    Vector3i pos = new Vector3i(
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])
                    );

                    // Remove display if in same world
                    if (world.getName().equals(worldName)) {
                        plugin.getDisplayManager().removeDisplay(world, pos);
                    }
                }

                // Remove from storage
                signStorage.removeSignById(data.getSignId());
                purgedCount++;
            }
        }

        if (purgedCount > 0) {
            playerData.sendMessage(Message.raw("Purged " + purgedCount + " signs from " + playerName + ".").color(GREEN));
            plugin.getPluginLogger().warning("Admin " + playerData.getUsername() + " purged " + purgedCount + " signs from " + playerName);
        } else {
            playerData.sendMessage(Message.raw("No signs found from player: " + playerName).color(YELLOW));
        }
    }

    /**
     * Admin command: Reload config (banned words list).
     */
    private void executeReload(PlayerRef playerData, Player player) {
        // Check admin permission
        if (player == null || !player.hasPermission("signs.admin")) {
            playerData.sendMessage(Message.raw("You need signs.admin permission.").color(RED));
            return;
        }

        plugin.getConfig().reload();
        playerData.sendMessage(Message.raw("Config reloaded! Banned words: " + plugin.getConfig().getBannedWords().size()).color(GREEN));
    }

    private String getPreview(SignData data) {
        if (data == null) return "(empty)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SignData.MAX_LINES; i++) {
            String line = data.getLine(i);
            if (line != null && !line.isEmpty()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(line);
            }
        }
        String preview = sb.toString();
        if (preview.length() > 30) {
            preview = preview.substring(0, 27) + "...";
        }
        return preview.isEmpty() ? "(empty)" : preview;
    }

    private void executeStatus(PlayerRef playerData) {
        playerData.sendMessage(Message.raw("=== EasySigns Status ===").color(GOLD));
        playerData.sendMessage(Message.raw("Total signs: " + signStorage.getSignCount()).color(GRAY));
        playerData.sendMessage(Message.raw("Active displays: " + plugin.getDisplayManager().getDisplayCount()).color(GRAY));
    }

    private void executeHelp(PlayerRef playerData) {
        playerData.sendMessage(Message.raw("=== Sign Commands ===").color(GOLD));
        playerData.sendMessage(Message.raw("/sign <text> - Create floating text at your position").color(YELLOW));
        playerData.sendMessage(Message.raw("  Use | to separate lines: /sign Line1|Line2|Line3").color(GRAY));
        playerData.sendMessage(Message.raw("/sign list - List your signs with IDs").color(YELLOW));
        playerData.sendMessage(Message.raw("/sign edit <id> <text> - Edit sign text by ID").color(YELLOW));
        playerData.sendMessage(Message.raw("/sign delete <id> - Delete your sign by ID").color(YELLOW));
        playerData.sendMessage(Message.raw("/sign remove - Remove the nearest sign (owner only)").color(YELLOW));
        playerData.sendMessage(Message.raw("/sign status - Show plugin status").color(YELLOW));
        playerData.sendMessage(Message.raw("--- Admin Commands (signs.admin) ---").color(GOLD));
        playerData.sendMessage(Message.raw("/sign listall - List ALL signs from all players").color(YELLOW));
        playerData.sendMessage(Message.raw("/sign deleteany <id> - Delete any sign by ID").color(YELLOW));
        playerData.sendMessage(Message.raw("/sign purge <player> - Delete ALL signs from a player").color(YELLOW));
        playerData.sendMessage(Message.raw("/sign reload - Reload config (banned words)").color(YELLOW));
    }

    private String formatPos(Vector3i pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    /**
     * Word-wrap text to fit within line length and line count limits.
     */
    private String[] wrapText(String[] inputLines, int maxLineLength, int maxLines) {
        java.util.List<String> result = new java.util.ArrayList<>();

        for (String line : inputLines) {
            if (result.size() >= maxLines) break;

            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                result.add("");
                continue;
            }

            // If line fits, add it directly
            if (trimmed.length() <= maxLineLength) {
                result.add(trimmed);
                continue;
            }

            // Word-wrap the line
            String[] words = trimmed.split("\\s+");
            StringBuilder current = new StringBuilder();

            for (String word : words) {
                if (result.size() >= maxLines) break;

                // If word itself is too long, split it
                if (word.length() > maxLineLength) {
                    // Flush current line first
                    if (current.length() > 0) {
                        result.add(current.toString());
                        current = new StringBuilder();
                        if (result.size() >= maxLines) break;
                    }
                    // Split long word across lines
                    while (word.length() > maxLineLength && result.size() < maxLines) {
                        result.add(word.substring(0, maxLineLength));
                        word = word.substring(maxLineLength);
                    }
                    if (word.length() > 0 && result.size() < maxLines) {
                        current.append(word);
                    }
                    continue;
                }

                // Check if word fits on current line
                if (current.length() == 0) {
                    current.append(word);
                } else if (current.length() + 1 + word.length() <= maxLineLength) {
                    current.append(" ").append(word);
                } else {
                    // Start new line
                    result.add(current.toString());
                    current = new StringBuilder(word);
                    if (result.size() >= maxLines) break;
                }
            }

            // Add remaining text
            if (current.length() > 0 && result.size() < maxLines) {
                result.add(current.toString());
            }
        }

        return result.toArray(new String[0]);
    }
}
