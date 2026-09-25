package com.server.textures;

public class TexturesProperty {
    private final String value;
    private final String signature;
    private final boolean hasCape;
    private final String capeUrl;

    public TexturesProperty(String value, String signature, boolean hasCape, String capeUrl) {
        this.value = value;
        this.signature = signature;
        this.hasCape = hasCape;
        this.capeUrl = capeUrl;
    }

    public String getValue() {
        return value;
    }

    public String getSignature() {
        return signature;
    }

    public boolean hasCape() {
        return hasCape;
    }

    public String getCapeUrl() {
        return capeUrl;
    }
}
