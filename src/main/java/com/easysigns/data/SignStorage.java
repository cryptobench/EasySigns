package com.easysigns.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.math.vector.Vector3i;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Stores all sign data and persists to JSON.
 */
public class SignStorage {
    private static final String SIGNS_FILE = "signs.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORAGE_TYPE = new TypeToken<Map<String, SignData>>(){}.getType();

    private final Path dataDirectory;
    private final Logger logger;
    private final Map<String, SignData> signs;

    public SignStorage(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        this.signs = new ConcurrentHashMap<>();
        load();
    }

    /**
     * Get position key for a block.
     */
    public static String getKey(String worldName, Vector3i pos) {
        return worldName + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    public static String getKey(String worldName, int x, int y, int z) {
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    /**
     * Check if a sign exists at position.
     */
    public boolean hasSign(String worldName, Vector3i pos) {
        return signs.containsKey(getKey(worldName, pos));
    }

    /**
     * Get sign data at position, or null if none.
     */
    public SignData getSign(String worldName, Vector3i pos) {
        return signs.get(getKey(worldName, pos));
    }

    /**
     * Create a new sign at position.
     */
    public SignData createSign(String worldName, Vector3i pos) {
        String key = getKey(worldName, pos);
        SignData data = new SignData();
        signs.put(key, data);
        save();
        logger.info("Created sign at " + key);
        return data;
    }

    /**
     * Update sign data and save.
     */
    public void updateSign(String worldName, Vector3i pos, SignData data) {
        String key = getKey(worldName, pos);
        signs.put(key, data);
        save();
    }

    /**
     * Remove a sign at position.
     */
    public void removeSign(String worldName, Vector3i pos) {
        String key = getKey(worldName, pos);
        if (signs.remove(key) != null) {
            save();
            logger.info("Removed sign at " + key);
        }
    }

    /**
     * Get total number of signs.
     */
    public int getSignCount() {
        return signs.size();
    }

    /**
     * Get all signs with their keys.
     * Returns a copy of the internal map.
     */
    public Map<String, SignData> getAllSigns() {
        return new ConcurrentHashMap<>(signs);
    }

    /**
     * Parse a position key into components.
     * Returns [worldName, x, y, z] or null if invalid.
     */
    public static String[] parseKey(String key) {
        if (key == null) return null;
        String[] parts = key.split(":");
        if (parts.length == 4) {
            return parts;
        }
        return null;
    }

    /**
     * Get all sign positions in a specific world.
     */
    public List<Vector3i> getSignsInWorld(String worldName) {
        List<Vector3i> result = new ArrayList<>();
        String prefix = worldName + ":";

        for (String key : signs.keySet()) {
            if (key.startsWith(prefix)) {
                // Parse position from key: "world:x:y:z"
                String[] parts = key.split(":");
                if (parts.length == 4) {
                    try {
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        int z = Integer.parseInt(parts[3]);
                        result.add(new Vector3i(x, y, z));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        return result;
    }

    /**
     * Load signs from file.
     */
    private void load() {
        Path file = dataDirectory.resolve(SIGNS_FILE);
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                Map<String, SignData> loaded = GSON.fromJson(json, STORAGE_TYPE);
                if (loaded != null) {
                    signs.putAll(loaded);
                }
                logger.info("Loaded " + signs.size() + " signs from storage");
            } catch (IOException e) {
                logger.warning("Failed to load signs: " + e.getMessage());
            }
        }
    }

    /**
     * Save signs to file.
     */
    public void save() {
        try {
            Files.createDirectories(dataDirectory);
            Path file = dataDirectory.resolve(SIGNS_FILE);
            Files.writeString(file, GSON.toJson(signs));
        } catch (IOException e) {
            logger.warning("Failed to save signs: " + e.getMessage());
        }
    }
}
