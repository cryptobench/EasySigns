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

/**
 * Command for placing and managing floating text signs.
 *
 * Usage:
 *   /sign <text>           - Create floating text at your position (use | for multiple lines)
 *   /sign remove           - Remove the nearest sign within 5 blocks
 *   /sign status           - Show plugin status
 *   /sign help             - Show help
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

        switch (firstWord) {
            case "remove" -> executeRemove(playerData, playerRef, store, world);
            case "delete" -> executeDelete(playerData, world, rest);
            case "list" -> executeList(playerData, world);
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

        // Create the sign data
        SignData signData = signStorage.createSign(worldName, blockPos);

        // Parse text - split by | for manual line breaks, then auto-wrap long lines
        String[] manualLines = text.split("\\|");
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

        // Find the nearest sign within radius
        Vector3i nearestSign = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (Vector3i signPos : signStorage.getSignsInWorld(worldName)) {
            double dx = playerPos.getX() - (signPos.getX() + 0.5);
            double dy = playerPos.getY() - (signPos.getY() + 0.5);
            double dz = playerPos.getZ() - (signPos.getZ() + 0.5);
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < nearestDistSq && distSq <= REMOVE_RADIUS * REMOVE_RADIUS) {
                nearestDistSq = distSq;
                nearestSign = signPos;
            }
        }

        if (nearestSign == null) {
            playerData.sendMessage(Message.raw("No sign found within " + (int) REMOVE_RADIUS + " blocks.").color(YELLOW));
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

        if (signs.isEmpty()) {
            playerData.sendMessage(Message.raw("No signs in this world.").color(YELLOW));
            return;
        }

        playerData.sendMessage(Message.raw("=== Signs in " + worldName + " ===").color(GOLD));
        int id = 1;
        for (Vector3i pos : signs) {
            SignData data = signStorage.getSign(worldName, pos);
            String preview = getPreview(data);
            playerData.sendMessage(Message.raw("[" + id + "] " + formatPos(pos) + " - " + preview).color(GRAY));
            id++;
        }
        playerData.sendMessage(Message.raw("Use /sign delete <id> to remove a sign.").color(YELLOW));
    }

    private void executeDelete(PlayerRef playerData, World world, String idStr) {
        if (idStr.isEmpty()) {
            playerData.sendMessage(Message.raw("Usage: /sign delete <id>").color(YELLOW));
            playerData.sendMessage(Message.raw("Use /sign list to see sign IDs.").color(GRAY));
            return;
        }

        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            playerData.sendMessage(Message.raw("Invalid ID. Use a number from /sign list.").color(RED));
            return;
        }

        String worldName = world.getName();
        List<Vector3i> signs = signStorage.getSignsInWorld(worldName);

        if (id < 1 || id > signs.size()) {
            playerData.sendMessage(Message.raw("Invalid ID. Use /sign list to see valid IDs.").color(RED));
            return;
        }

        Vector3i pos = signs.get(id - 1);

        // Remove the display entity
        plugin.getDisplayManager().removeDisplay(world, pos);

        // Remove from storage
        signStorage.removeSign(worldName, pos);

        playerData.sendMessage(Message.raw("Sign #" + id + " at " + formatPos(pos) + " deleted.").color(GREEN));
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
        playerData.sendMessage(Message.raw("/sign list - List all signs with IDs").color(YELLOW));
        playerData.sendMessage(Message.raw("/sign delete <id> - Delete a sign by ID").color(YELLOW));
        playerData.sendMessage(Message.raw("/sign remove - Remove the nearest sign (within 5 blocks)").color(YELLOW));
        playerData.sendMessage(Message.raw("/sign status - Show plugin status").color(YELLOW));
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
