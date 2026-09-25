package com.server.textures;

import java.util.UUID;

public class MojangProfile {
    private final UUID uuid;
    private final String name;
    private final TexturesProperty textures;
    private final long cachedAt;

    public MojangProfile(UUID uuid, String name, TexturesProperty textures, long cachedAt) {
        this.uuid = uuid;
        this.name = name;
        this.textures = textures;
        this.cachedAt = cachedAt;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public TexturesProperty getTextures() {
        return textures;
    }

    public long getCachedAt() {
        return cachedAt;
    }

    public boolean isExpired(long ttlMillis) {
        return System.currentTimeMillis() - cachedAt > ttlMillis;
    }
}
