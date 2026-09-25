package com.server.textures;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class MojangApiClient {
    private final Logger logger;
    private HttpClient httpClient;
    private final Duration timeout;

    public MojangApiClient(Logger logger, String proxyHost, int proxyPort, int timeoutSeconds) {
        this.logger = logger;
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        rebuildHttpClient(proxyHost, proxyPort);
    }

    public void rebuildHttpClient(String proxyHost, int proxyPort) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL);

        if (proxyHost != null && !proxyHost.trim().isEmpty() && proxyPort > 0) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost.trim(), proxyPort)));
            logger.info("[MojangApiClient] Configured HTTP Proxy: " + proxyHost.trim() + ":" + proxyPort);
        }

        this.httpClient = builder.build();
    }

    /**
     * Asynchronously fetch Mojang profile with signed textures.
     * Returns null if player is not premium or request failed.
     */
    public CompletableFuture<MojangProfile> fetchProfileAsync(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Step 1: Query UUID by username
                String uuidUrl = "https://api.mojang.com/users/profiles/minecraft/" + username;
                HttpRequest uuidReq = HttpRequest.newBuilder()
                        .uri(URI.create(uuidUrl))
                        .timeout(timeout)
                        .header("User-Agent", "MojangTexturesInjector/1.0")
                        .GET()
                        .build();

                HttpResponse<String> uuidRes = httpClient.send(uuidReq, HttpResponse.BodyHandlers.ofString());
                if (uuidRes.statusCode() == 204 || uuidRes.statusCode() == 404) {
                    return null; // Not a premium account
                }
                if (uuidRes.statusCode() != 200) {
                    logger.warning("[MojangApiClient] UUID lookup for " + username + " returned HTTP " + uuidRes.statusCode());
                    return null;
                }

                JsonObject uuidJson = JsonParser.parseString(uuidRes.body()).getAsJsonObject();
                String rawUuid = uuidJson.get("id").getAsString();
                String officialName = uuidJson.has("name") ? uuidJson.get("name").getAsString() : username;
                UUID uuid = parseDashlessUuid(rawUuid);

                // Step 2: Query session profile (signed textures)
                String sessionUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + rawUuid + "?unsigned=false";
                HttpRequest sessionReq = HttpRequest.newBuilder()
                        .uri(URI.create(sessionUrl))
                        .timeout(timeout)
                        .header("User-Agent", "MojangTexturesInjector/1.0")
                        .GET()
                        .build();

                HttpResponse<String> sessionRes = httpClient.send(sessionReq, HttpResponse.BodyHandlers.ofString());
                if (sessionRes.statusCode() != 200) {
                    logger.warning("[MojangApiClient] Session profile lookup for " + username + " returned HTTP " + sessionRes.statusCode());
                    return null;
                }

                JsonObject profileJson = JsonParser.parseString(sessionRes.body()).getAsJsonObject();
                if (!profileJson.has("properties")) {
                    return null;
                }

                JsonArray properties = profileJson.getAsJsonArray("properties");
                String textureValue = null;
                String textureSignature = null;
                boolean hasCape = false;
                String capeUrl = null;

                for (JsonElement elem : properties) {
                    JsonObject prop = elem.getAsJsonObject();
                    if ("textures".equals(prop.get("name").getAsString())) {
                        textureValue = prop.get("value").getAsString();
                        if (prop.has("signature")) {
                            textureSignature = prop.get("signature").getAsString();
                        }
                        break;
                    }
                }

                if (textureValue == null) {
                    return null;
                }

                // Decode base64 to inspect Cape
                try {
                    byte[] decoded = Base64.getDecoder().decode(textureValue);
                    String texturesJsonStr = new String(decoded, StandardCharsets.UTF_8);
                    JsonObject texturesJson = JsonParser.parseString(texturesJsonStr).getAsJsonObject();
                    if (texturesJson.has("textures")) {
                        JsonObject texObj = texturesJson.getAsJsonObject("textures");
                        if (texObj.has("CAPE")) {
                            hasCape = true;
                            JsonObject capeObj = texObj.getAsJsonObject("CAPE");
                            if (capeObj.has("url")) {
                                capeUrl = capeObj.get("url").getAsString();
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.fine("[MojangApiClient] Failed to inspect texture json details: " + ex.getMessage());
                }

                TexturesProperty property = new TexturesProperty(textureValue, textureSignature, hasCape, capeUrl);
                return new MojangProfile(uuid, officialName, property, System.currentTimeMillis());

            } catch (Exception e) {
                logger.warning("[MojangApiClient] Error querying Mojang API for " + username + ": " + e.getMessage());
                return null;
            }
        });
    }

    private static UUID parseDashlessUuid(String raw) {
        if (raw.length() != 32) {
            return UUID.fromString(raw);
        }
        String formatted = raw.substring(0, 8) + "-" +
                raw.substring(8, 12) + "-" +
                raw.substring(12, 16) + "-" +
                raw.substring(16, 20) + "-" +
                raw.substring(20, 32);
        return UUID.fromString(formatted);
    }
}
