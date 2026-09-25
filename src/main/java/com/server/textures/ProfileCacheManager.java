package com.server.textures;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ProfileCacheManager {
    private final File cacheDir;
    private final Logger logger;
    private final long cacheTtlMillis;
    private final Map<String, MojangProfile> memoryCache = new ConcurrentHashMap<>();

    public ProfileCacheManager(File dataFolder, Logger logger, long cacheTtlHours) {
        this.cacheDir = new File(dataFolder, "cache");
        this.logger = logger;
        this.cacheTtlMillis = cacheTtlHours * 3600 * 1000L;

        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        loadAllFromDisk();
    }

    public MojangProfile get(String username) {
        String key = username.toLowerCase(Locale.ROOT);
        MojangProfile profile = memoryCache.get(key);
        if (profile != null) {
            if (profile.isExpired(cacheTtlMillis)) {
                memoryCache.remove(key);
                return null;
            }
            return profile;
        }
        return null;
    }

    public void put(String username, MojangProfile profile) {
        String key = username.toLowerCase(Locale.ROOT);
        memoryCache.put(key, profile);
        saveToDiskAsync(key, profile);
    }

    private void saveToDiskAsync(String key, MojangProfile profile) {
        File file = new File(cacheDir, key + ".json");
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("uuid", profile.getUuid().toString());
            obj.addProperty("name", profile.getName());
            obj.addProperty("cachedAt", profile.getCachedAt());

            JsonObject tex = new JsonObject();
            tex.addProperty("value", profile.getTextures().getValue());
            if (profile.getTextures().getSignature() != null) {
                tex.addProperty("signature", profile.getTextures().getSignature());
            }
            tex.addProperty("hasCape", profile.getTextures().hasCape());
            if (profile.getTextures().getCapeUrl() != null) {
                tex.addProperty("capeUrl", profile.getTextures().getCapeUrl());
            }
            obj.add("textures", tex);

            writer.write(obj.toString());
        } catch (Exception e) {
            logger.warning("[ProfileCache] Failed to save cache for " + key + ": " + e.getMessage());
        }
    }

    private void loadAllFromDisk() {
        File[] files = cacheDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;

        int loaded = 0;
        long now = System.currentTimeMillis();
        for (File file : files) {
            try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                long cachedAt = obj.get("cachedAt").getAsLong();
                if (now - cachedAt > cacheTtlMillis) {
                    file.delete();
                    continue;
                }

                UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
                String name = obj.get("name").getAsString();

                JsonObject tex = obj.getAsJsonObject("textures");
                String val = tex.get("value").getAsString();
                String sig = tex.has("signature") ? tex.get("signature").getAsString() : null;
                boolean hasCape = tex.has("hasCape") && tex.get("hasCape").getAsBoolean();
                String capeUrl = tex.has("capeUrl") ? tex.get("capeUrl").getAsString() : null;

                TexturesProperty tp = new TexturesProperty(val, sig, hasCape, capeUrl);
                MojangProfile profile = new MojangProfile(uuid, name, tp, cachedAt);
                String key = file.getName().replace(".json", "").toLowerCase(Locale.ROOT);
                memoryCache.put(key, profile);
                loaded++;
            } catch (Exception e) {
                logger.fine("[ProfileCache] Failed reading cache file " + file.getName() + ": " + e.getMessage());
            }
        }
        if (loaded > 0) {
            logger.info("[ProfileCache] Loaded " + loaded + " cached profiles from disk.");
        }
    }
}
