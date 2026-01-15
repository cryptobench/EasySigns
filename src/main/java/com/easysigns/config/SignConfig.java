package com.easysigns.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Configuration for EasySigns plugin.
 * Handles banned words filtering and other settings.
 */
public class SignConfig {
    private static final String CONFIG_FILE = "config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Config values
    private boolean filterEnabled = true;
    private List<String> bannedWords = new ArrayList<>();
    private String filterMessage = "Your sign contains inappropriate content.";
    private boolean notifyAdmins = true;

    // Transient (not saved)
    private transient Path dataDirectory;
    private transient Logger logger;
    private transient List<Pattern> bannedPatterns;

    public SignConfig() {
        // Default banned words (examples - replace with real ones)
        bannedWords.add("example_bad_word");
    }

    /**
     * Initialize the config with data directory and logger.
     */
    public void init(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        load();
        compilePatterns();
    }

    /**
     * Check if text contains banned words.
     * Returns the matched word if found, null otherwise.
     */
    public String checkForBannedWords(String text) {
        if (!filterEnabled || text == null || bannedPatterns == null) {
            return null;
        }

        String lowerText = text.toLowerCase();
        for (int i = 0; i < bannedPatterns.size(); i++) {
            if (bannedPatterns.get(i).matcher(lowerText).find()) {
                return bannedWords.get(i);
            }
        }
        return null;
    }

    /**
     * Check multiple lines for banned words.
     * Returns the matched word if found, null otherwise.
     */
    public String checkLinesForBannedWords(String[] lines) {
        if (lines == null) return null;
        for (String line : lines) {
            String match = checkForBannedWords(line);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    /**
     * Compile banned words into regex patterns for efficient matching.
     */
    private void compilePatterns() {
        bannedPatterns = new ArrayList<>();
        for (String word : bannedWords) {
            // Match word boundaries, case insensitive
            String pattern = "\\b" + Pattern.quote(word.toLowerCase()) + "\\b";
            bannedPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
        }
        logger.info("Compiled " + bannedPatterns.size() + " banned word patterns");
    }

    /**
     * Load config from file.
     */
    private void load() {
        Path file = dataDirectory.resolve(CONFIG_FILE);
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                SignConfig loaded = GSON.fromJson(json, SignConfig.class);
                if (loaded != null) {
                    this.filterEnabled = loaded.filterEnabled;
                    this.bannedWords = loaded.bannedWords != null ? loaded.bannedWords : new ArrayList<>();
                    this.filterMessage = loaded.filterMessage;
                    this.notifyAdmins = loaded.notifyAdmins;
                }
                logger.info("Loaded config with " + bannedWords.size() + " banned words");
            } catch (IOException e) {
                logger.warning("Failed to load config: " + e.getMessage());
            }
        } else {
            // Save default config
            save();
            logger.info("Created default config file");
        }
    }

    /**
     * Save config to file.
     */
    public void save() {
        try {
            Files.createDirectories(dataDirectory);
            Path file = dataDirectory.resolve(CONFIG_FILE);
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException e) {
            logger.warning("Failed to save config: " + e.getMessage());
        }
    }

    /**
     * Reload config from file.
     */
    public void reload() {
        load();
        compilePatterns();
    }

    // Getters
    public boolean isFilterEnabled() {
        return filterEnabled;
    }

    public String getFilterMessage() {
        return filterMessage;
    }

    public boolean shouldNotifyAdmins() {
        return notifyAdmins;
    }

    public List<String> getBannedWords() {
        return new ArrayList<>(bannedWords);
    }
}
