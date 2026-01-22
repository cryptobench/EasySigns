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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Stores all sign data and persists to JSON.
 *
 * Performance optimizations:
 * - Spatial index for O(1) chunk-based lookups
 * - Secondary index for O(1) sign ID lookups
 * - Dirty flag with batched saves to reduce disk I/O
 */
public class SignStorage {
    private static final String SIGNS_FILE = "signs.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORAGE_TYPE = new TypeToken<Map<String, SignData>>(){}.getType();
    private static final int CHUNK_SIZE = 32; // Hytale uses 32x32 chunks, not 16x16 like Minecraft

    private final Path dataDirectory;
    private final Logger logger;
    private final Map<String, SignData> signs;

    // Secondary index: signId -> positionKey for O(1) ID lookups
    private final Map<String, String> signIdIndex;

    // Spatial index: "world:chunkX:chunkZ" -> Set of position keys in that chunk
    private final Map<String, Set<String>> chunkIndex;

    // World index: "worldName" -> Set of position keys in that world
    private final Map<String, Set<String>> worldIndex;

    // Dirty flag for batched saves
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public SignStorage(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        this.signs = new ConcurrentHashMap<>();
        this.signIdIndex = new ConcurrentHashMap<>();
        this.chunkIndex = new ConcurrentHashMap<>();
        this.worldIndex = new ConcurrentHashMap<>();
        load();
        rebuildIndexes();
    }

    /**
     * Rebuild all secondary indexes from the main signs map.
     * Called on load and can be called to fix corrupted indexes.
     */
    private void rebuildIndexes() {
        signIdIndex.clear();
        chunkIndex.clear();
        worldIndex.clear();

        for (Map.Entry<String, SignData> entry : signs.entrySet()) {
            String posKey = entry.getKey();
            SignData data = entry.getValue();

            // Build sign ID index
            String signId = data.getSignId();
            if (signId != null && !signId.isEmpty()) {
                signIdIndex.put(signId, posKey);
            }

            // Build chunk index
            String chunkKey = getChunkKeyFromPositionKey(posKey);
            if (chunkKey != null) {
                chunkIndex.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(posKey);
            }

            // Build world index
            String worldName = getWorldFromKey(posKey);
            if (worldName != null) {
                worldIndex.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet()).add(posKey);
            }
        }

        logger.info("Rebuilt indexes: " + signIdIndex.size() + " sign IDs, " + chunkIndex.size() + " chunks, " + worldIndex.size() + " worlds");
    }

    /**
     * Get chunk key from a position key.
     * Position key format: "world:x:y:z"
     * Chunk key format: "world:chunkX:chunkZ"
     */
    private String getChunkKeyFromPositionKey(String posKey) {
        String[] parts = parseKey(posKey);
        if (parts == null) return null;

        try {
            int x = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[3]);
            int chunkX = x >> 5; // Equivalent to x / 32 (Hytale chunks are 32x32)
            int chunkZ = z >> 5;
            return parts[0] + ":" + chunkX + ":" + chunkZ;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extract world name from position key without full parsing.
     * More efficient than parseKey() when only world name is needed.
     */
    private static String getWorldFromKey(String posKey) {
        if (posKey == null) return null;
        int idx = posKey.indexOf(':');
        return idx > 0 ? posKey.substring(0, idx) : null;
    }

    /**
     * Get chunk key for given world and chunk coordinates.
     */
    public static String getChunkKey(String worldName, int chunkX, int chunkZ) {
        return worldName + ":" + chunkX + ":" + chunkZ;
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
     * Does NOT save immediately - call saveIfDirty() or save() when appropriate.
     */
    public SignData createSign(String worldName, Vector3i pos) {
        String key = getKey(worldName, pos);
        SignData data = new SignData();
        signs.put(key, data);

        // Update indexes
        String signId = data.getSignId();
        if (signId != null && !signId.isEmpty()) {
            signIdIndex.put(signId, key);
        }
        String chunkKey = getChunkKeyFromPositionKey(key);
        if (chunkKey != null) {
            chunkIndex.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(key);
        }
        worldIndex.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet()).add(key);

        markDirty();
        logger.fine("Created sign at " + key);
        return data;
    }

    /**
     * Create a new sign with owner in a single operation.
     * More efficient than createSign() + updateSign() separately.
     */
    public SignData createSignWithOwner(String worldName, Vector3i pos, String ownerUuid, String ownerName) {
        String key = getKey(worldName, pos);
        SignData data = new SignData();
        data.setOwnerUuid(ownerUuid);
        data.setOwnerName(ownerName);
        signs.put(key, data);

        // Update indexes
        String signId = data.getSignId();
        if (signId != null && !signId.isEmpty()) {
            signIdIndex.put(signId, key);
        }
        String chunkKey = getChunkKeyFromPositionKey(key);
        if (chunkKey != null) {
            chunkIndex.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(key);
        }
        worldIndex.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet()).add(key);

        markDirty();
        logger.fine("Created sign with owner at " + key);
        return data;
    }

    /**
     * Update sign data.
     * Does NOT save immediately - call saveIfDirty() or save() when appropriate.
     */
    public void updateSign(String worldName, Vector3i pos, SignData data) {
        String key = getKey(worldName, pos);

        // Update sign ID index if ID changed
        SignData oldData = signs.get(key);
        if (oldData != null) {
            String oldId = oldData.getSignId();
            if (oldId != null && !oldId.isEmpty()) {
                signIdIndex.remove(oldId);
            }
        }
        String newId = data.getSignId();
        if (newId != null && !newId.isEmpty()) {
            signIdIndex.put(newId, key);
        }

        signs.put(key, data);
        markDirty();
    }

    /**
     * Remove a sign at position.
     * Does NOT save immediately - call saveIfDirty() or save() when appropriate.
     */
    public void removeSign(String worldName, Vector3i pos) {
        String key = getKey(worldName, pos);
        SignData removed = signs.remove(key);
        if (removed != null) {
            // Update indexes
            String signId = removed.getSignId();
            if (signId != null && !signId.isEmpty()) {
                signIdIndex.remove(signId);
            }
            String chunkKey = getChunkKeyFromPositionKey(key);
            if (chunkKey != null) {
                Set<String> chunkSigns = chunkIndex.get(chunkKey);
                if (chunkSigns != null) {
                    chunkSigns.remove(key);
                    if (chunkSigns.isEmpty()) {
                        chunkIndex.remove(chunkKey);
                    }
                }
            }
            // Update world index
            Set<String> worldSigns = worldIndex.get(worldName);
            if (worldSigns != null) {
                worldSigns.remove(key);
                if (worldSigns.isEmpty()) {
                    worldIndex.remove(worldName);
                }
            }

            markDirty();
            logger.fine("Removed sign at " + key);
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
     * Returns an UNMODIFIABLE view - do not modify!
     * For iteration only. Much faster than previous implementation.
     */
    public Map<String, SignData> getAllSigns() {
        return Collections.unmodifiableMap(signs);
    }

    /**
     * Get all signs in a specific chunk. O(1) lookup using spatial index.
     * Returns position keys for signs in the chunk.
     */
    public Set<String> getSignsInChunk(String worldName, int chunkX, int chunkZ) {
        String chunkKey = getChunkKey(worldName, chunkX, chunkZ);
        Set<String> result = chunkIndex.get(chunkKey);
        return result != null ? Collections.unmodifiableSet(result) : Collections.emptySet();
    }

    /**
     * Check if a chunk has any signs. O(1) lookup.
     */
    public boolean hasSignsInChunk(String worldName, int chunkX, int chunkZ) {
        String chunkKey = getChunkKey(worldName, chunkX, chunkZ);
        Set<String> signs = chunkIndex.get(chunkKey);
        return signs != null && !signs.isEmpty();
    }

    /**
     * Find signs near a position using chunk-based spatial index.
     * Returns list of [position, signData] entries within radius.
     * Much more efficient than iterating all signs in a world.
     */
    public List<Map.Entry<Vector3i, SignData>> getSignsNearPosition(
            String worldName, double playerX, double playerY, double playerZ, double radius) {

        List<Map.Entry<Vector3i, SignData>> result = new ArrayList<>();
        double radiusSq = radius * radius;

        // Calculate chunk range to search
        int playerChunkX = (int) Math.floor(playerX) >> 5; // Hytale uses 32x32 chunks
        int playerChunkZ = (int) Math.floor(playerZ) >> 5;
        int chunkRadius = (int) Math.ceil(radius / 32.0) + 1;

        // Only search chunks within range
        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                Set<String> signsInChunk = getSignsInChunk(worldName, cx, cz);
                if (signsInChunk.isEmpty()) continue;

                for (String posKey : signsInChunk) {
                    String[] parts = parseKey(posKey);
                    if (parts == null) continue;

                    try {
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        int z = Integer.parseInt(parts[3]);

                        // Calculate distance to sign center
                        double dx = playerX - (x + 0.5);
                        double dy = playerY - (y + 0.5);
                        double dz = playerZ - (z + 0.5);
                        double distSq = dx * dx + dy * dy + dz * dz;

                        if (distSq <= radiusSq) {
                            SignData data = signs.get(posKey);
                            if (data != null) {
                                result.add(Map.entry(new Vector3i(x, y, z), data));
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        return result;
    }

    /**
     * Parse a position key into components using index-based parsing.
     * Returns [worldName, x, y, z] or null if invalid.
     * More efficient than String.split() - avoids regex and array allocation overhead.
     */
    public static String[] parseKey(String key) {
        if (key == null) return null;

        int i1 = key.indexOf(':');
        if (i1 <= 0) return null;

        int i2 = key.indexOf(':', i1 + 1);
        if (i2 <= i1) return null;

        int i3 = key.indexOf(':', i2 + 1);
        if (i3 <= i2 || i3 >= key.length() - 1) return null;

        // Verify no more colons (exactly 4 parts)
        if (key.indexOf(':', i3 + 1) != -1) return null;

        return new String[] {
            key.substring(0, i1),
            key.substring(i1 + 1, i2),
            key.substring(i2 + 1, i3),
            key.substring(i3 + 1)
        };
    }

    /**
     * Find a sign by its unique ID. O(1) lookup using index.
     * Returns the position key and SignData, or null if not found.
     */
    public Map.Entry<String, SignData> getSignById(String signId) {
        if (signId == null || signId.isEmpty()) return null;

        // O(1) lookup using index
        String posKey = signIdIndex.get(signId);
        if (posKey == null) return null;

        SignData data = signs.get(posKey);
        if (data == null) {
            // Index is stale, clean it up
            signIdIndex.remove(signId);
            return null;
        }

        return Map.entry(posKey, data);
    }

    /**
     * Remove a sign by its unique ID.
     * Returns the position key if removed, null otherwise.
     */
    public String removeSignById(String signId) {
        Map.Entry<String, SignData> entry = getSignById(signId);
        if (entry != null) {
            String posKey = entry.getKey();

            // Remove from main storage
            signs.remove(posKey);

            // Remove from indexes
            signIdIndex.remove(signId);
            String chunkKey = getChunkKeyFromPositionKey(posKey);
            if (chunkKey != null) {
                Set<String> chunkSigns = chunkIndex.get(chunkKey);
                if (chunkSigns != null) {
                    chunkSigns.remove(posKey);
                    if (chunkSigns.isEmpty()) {
                        chunkIndex.remove(chunkKey);
                    }
                }
            }
            // Update world index
            String worldName = getWorldFromKey(posKey);
            if (worldName != null) {
                Set<String> worldSigns = worldIndex.get(worldName);
                if (worldSigns != null) {
                    worldSigns.remove(posKey);
                    if (worldSigns.isEmpty()) {
                        worldIndex.remove(worldName);
                    }
                }
            }

            markDirty();
            logger.fine("Removed sign with ID " + signId + " at " + posKey);
            return posKey;
        }
        return null;
    }

    /**
     * Get all sign position keys in a specific world. O(1) lookup using world index.
     * Returns position keys for signs in the world.
     */
    public Set<String> getSignKeysInWorld(String worldName) {
        Set<String> result = worldIndex.get(worldName);
        return result != null ? Collections.unmodifiableSet(result) : Collections.emptySet();
    }

    /**
     * Get all sign positions in a specific world.
     * Uses world index for O(1) initial lookup instead of O(n) prefix scan.
     */
    public List<Vector3i> getSignsInWorld(String worldName) {
        Set<String> keys = worldIndex.get(worldName);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        List<Vector3i> result = new ArrayList<>(keys.size());
        for (String key : keys) {
            String[] parts = parseKey(key);
            if (parts != null) {
                try {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    result.add(new Vector3i(x, y, z));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return result;
    }

    // ========== Dirty Flag Management ==========

    /**
     * Mark storage as dirty (needs saving).
     */
    public void markDirty() {
        dirty.set(true);
    }

    /**
     * Check if storage has unsaved changes.
     */
    public boolean isDirty() {
        return dirty.get();
    }

    /**
     * Save only if there are unsaved changes.
     * Returns true if a save was performed.
     */
    public boolean saveIfDirty() {
        if (dirty.compareAndSet(true, false)) {
            saveInternal();
            return true;
        }
        return false;
    }

    /**
     * Force save to disk. Clears dirty flag.
     */
    public void save() {
        dirty.set(false);
        saveInternal();
    }

    private void saveInternal() {
        try {
            Files.createDirectories(dataDirectory);
            Path file = dataDirectory.resolve(SIGNS_FILE);
            Files.writeString(file, GSON.toJson(signs));
        } catch (IOException e) {
            logger.warning("Failed to save signs: " + e.getMessage());
            // Re-mark as dirty so we retry later
            dirty.set(true);
        }
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
                    // Initialize transient caches after JSON deserialization
                    for (SignData signData : signs.values()) {
                        signData.initializeCache();
                    }
                }
                logger.info("Loaded " + signs.size() + " signs from storage");
            } catch (IOException e) {
                logger.warning("Failed to load signs: " + e.getMessage());
            }
        }
    }
}
