package com.easysigns.data;

import java.awt.Color;
import java.util.Arrays;
import java.util.UUID;

/**
 * Data for a single sign - text lines, color, and ownership.
 */
public class SignData {
    public static final int MAX_LINES = 4;
    public static final int MAX_LINE_LENGTH = 32;

    private String[] lines;
    private int colorR;
    private int colorG;
    private int colorB;

    // Ownership and identification
    private String signId;      // Unique stable ID for this sign
    private String ownerUuid;   // UUID of the player who created the sign
    private String ownerName;   // Username of the owner (for display)

    public SignData() {
        this.lines = new String[]{"", "", "", ""};
        this.colorR = 0;
        this.colorG = 0;
        this.colorB = 0;
        this.signId = UUID.randomUUID().toString().substring(0, 8); // Short unique ID
        this.ownerUuid = null;
        this.ownerName = null;
    }

    public String[] getLines() {
        return Arrays.copyOf(lines, lines.length);
    }

    public String getLine(int index) {
        if (index < 0 || index >= MAX_LINES) return "";
        return lines[index] != null ? lines[index] : "";
    }

    public void setLine(int index, String text) {
        if (index < 0 || index >= MAX_LINES) return;
        if (text == null) {
            lines[index] = "";
        } else if (text.length() > MAX_LINE_LENGTH) {
            lines[index] = text.substring(0, MAX_LINE_LENGTH);
        } else {
            lines[index] = text;
        }
    }

    public void setLines(String[] newLines) {
        for (int i = 0; i < MAX_LINES; i++) {
            setLine(i, newLines != null && i < newLines.length ? newLines[i] : "");
        }
    }

    public Color getColor() {
        return new Color(colorR, colorG, colorB);
    }

    public void setColor(Color color) {
        this.colorR = color.getRed();
        this.colorG = color.getGreen();
        this.colorB = color.getBlue();
    }

    public String getAllText() {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
        }
        return sb.toString();
    }

    public boolean hasText() {
        for (String line : lines) {
            if (line != null && !line.isEmpty()) return true;
        }
        return false;
    }

    // Ownership and ID methods
    public String getSignId() {
        // Generate ID if missing (for backwards compatibility with old signs)
        if (signId == null || signId.isEmpty()) {
            signId = UUID.randomUUID().toString().substring(0, 8);
        }
        return signId;
    }

    public String getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(String ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public void setOwner(UUID uuid, String name) {
        this.ownerUuid = uuid != null ? uuid.toString() : null;
        this.ownerName = name;
    }

    public boolean isOwner(UUID playerUuid) {
        if (ownerUuid == null || playerUuid == null) return false;
        return ownerUuid.equals(playerUuid.toString());
    }
}
