package com.easysigns.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Configuration for the EasySigns plugin.
 * Stored as JSON in the plugin's data directory.
 */
public class SignConfig {

    private static final Logger LOGGER = Logger.getLogger("EasySigns");
    private static final String CONFIG_FILE = "config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Configuration fields with defaults

    /**
     * Maximum number of lines per sign.
     */
    private int maxLines = 4;

    /**
     * Maximum characters per line.
     */
    private int maxLineLength = 32;

    /**
     * Default text color (RGB).
     */
    private int[] defaultTextColor = {0, 0, 0}; // Black

    /**
     * Whether signs glow by default.
     */
    private boolean defaultGlowing = false;

    /**
     * Whether to show text preview in the editor.
     */
    private boolean showPreview = true;

    /**
     * Vertical offset for text display above blocks (in blocks).
     */
    private double textDisplayOffsetY = 0.5;

    /**
     * Whether all players can create signs, or only admins.
     */
    private boolean allowPlayerSigns = true;

    /**
     * Whether to enable chat-based editing as fallback.
     */
    private boolean enableChatEditing = true;

    /**
     * Load configuration from file, or create default if not exists.
     */
    public static SignConfig load(Path dataDirectory) {
        Path configPath = dataDirectory.resolve(CONFIG_FILE);

        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                SignConfig config = GSON.fromJson(json, SignConfig.class);
                LOGGER.info("Loaded configuration from " + configPath);
                return config;
            } catch (IOException e) {
                LOGGER.warning("Failed to load config: " + e.getMessage());
            }
        }

        // Create default config
        SignConfig config = new SignConfig();
        config.save(dataDirectory);
        return config;
    }

    /**
     * Save configuration to file.
     */
    public void save(Path dataDirectory) {
        try {
            // Ensure directory exists
            Files.createDirectories(dataDirectory);

            Path configPath = dataDirectory.resolve(CONFIG_FILE);
            String json = GSON.toJson(this);
            Files.writeString(configPath, json);

            LOGGER.info("Saved configuration to " + configPath);

        } catch (IOException e) {
            LOGGER.warning("Failed to save config: " + e.getMessage());
        }
    }

    /**
     * Reload configuration from file.
     */
    public void reload(Path dataDirectory) {
        SignConfig reloaded = load(dataDirectory);

        // Copy values from reloaded config
        this.maxLines = reloaded.maxLines;
        this.maxLineLength = reloaded.maxLineLength;
        this.defaultTextColor = reloaded.defaultTextColor;
        this.defaultGlowing = reloaded.defaultGlowing;
        this.showPreview = reloaded.showPreview;
        this.textDisplayOffsetY = reloaded.textDisplayOffsetY;
        this.allowPlayerSigns = reloaded.allowPlayerSigns;
        this.enableChatEditing = reloaded.enableChatEditing;

        LOGGER.info("Configuration reloaded");
    }

    // Getters and setters

    public int getMaxLines() {
        return maxLines;
    }

    public void setMaxLines(int maxLines) {
        this.maxLines = Math.max(1, Math.min(maxLines, 8));
    }

    public int getMaxLineLength() {
        return maxLineLength;
    }

    public void setMaxLineLength(int maxLineLength) {
        this.maxLineLength = Math.max(1, Math.min(maxLineLength, 64));
    }

    public Color getDefaultTextColor() {
        return new Color(
            defaultTextColor[0],
            defaultTextColor[1],
            defaultTextColor[2]
        );
    }

    public void setDefaultTextColor(Color color) {
        this.defaultTextColor = new int[]{
            color.getRed(),
            color.getGreen(),
            color.getBlue()
        };
    }

    public boolean isDefaultGlowing() {
        return defaultGlowing;
    }

    public void setDefaultGlowing(boolean defaultGlowing) {
        this.defaultGlowing = defaultGlowing;
    }

    public boolean isShowPreview() {
        return showPreview;
    }

    public void setShowPreview(boolean showPreview) {
        this.showPreview = showPreview;
    }

    public double getTextDisplayOffsetY() {
        return textDisplayOffsetY;
    }

    public void setTextDisplayOffsetY(double textDisplayOffsetY) {
        this.textDisplayOffsetY = textDisplayOffsetY;
    }

    public boolean isAllowPlayerSigns() {
        return allowPlayerSigns;
    }

    public void setAllowPlayerSigns(boolean allowPlayerSigns) {
        this.allowPlayerSigns = allowPlayerSigns;
    }

    public boolean isEnableChatEditing() {
        return enableChatEditing;
    }

    public void setEnableChatEditing(boolean enableChatEditing) {
        this.enableChatEditing = enableChatEditing;
    }

    @Override
    public String toString() {
        return "SignConfig{" +
                "maxLines=" + maxLines +
                ", maxLineLength=" + maxLineLength +
                ", defaultGlowing=" + defaultGlowing +
                ", showPreview=" + showPreview +
                ", allowPlayerSigns=" + allowPlayerSigns +
                ", enableChatEditing=" + enableChatEditing +
                '}';
    }
}
