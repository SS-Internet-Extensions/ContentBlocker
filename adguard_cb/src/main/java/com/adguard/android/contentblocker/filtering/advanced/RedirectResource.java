package com.adguard.android.contentblocker.filtering.advanced;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class RedirectResource {

    private static final byte[] TRANSPARENT_GIF = new byte[]{
            71, 73, 70, 56, 57, 97, 1, 0, 1, 0, -128, 0, 0, 0, 0, 0,
            -1, -1, -1, 33, -7, 4, 1, 0, 0, 0, 0, 44, 0, 0, 0, 0,
            1, 0, 1, 0, 0, 2, 2, 68, 1, 0, 59
    };

    private final String name;
    private final String mimeType;
    private final String encoding;
    private final byte[] body;

    private RedirectResource(String name, String mimeType, String encoding, byte[] body) {
        this.name = name;
        this.mimeType = mimeType;
        this.encoding = encoding;
        this.body = body.clone();
    }

    public static RedirectResource fromRule(AdvancedRule rule) {
        String name = rule.getOptionValue("redirect");
        if (name.length() == 0) {
            name = rule.getOptionValue("redirect-rule");
        }
        return named(name);
    }

    public static RedirectResource named(String name) {
        String normalizedName = normalizeName(name);
        if ("noopjs".equals(normalizedName) || "noop.js".equals(normalizedName)) {
            return text(normalizedName, "application/javascript", "");
        }
        if ("noopcss".equals(normalizedName) || "noop.css".equals(normalizedName)) {
            return text(normalizedName, "text/css", "");
        }
        if ("nooptext".equals(normalizedName) || "noop.txt".equals(normalizedName)) {
            return text(normalizedName, "text/plain", "");
        }
        if ("noophtml".equals(normalizedName) || "noop.html".equals(normalizedName)) {
            return text(normalizedName, "text/html", "<!doctype html><html><head></head><body></body></html>");
        }
        if ("1x1.gif".equals(normalizedName) || "1x1-transparent.gif".equals(normalizedName)) {
            return new RedirectResource(normalizedName, "image/gif", null, TRANSPARENT_GIF);
        }
        if ("empty".equals(normalizedName)) {
            return text(normalizedName, "text/plain", "");
        }
        return text(normalizedName.length() == 0 ? "empty" : normalizedName, "text/plain", "");
    }

    public String getName() {
        return name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getEncoding() {
        return encoding;
    }

    public byte[] getBody() {
        return body.clone();
    }

    private static RedirectResource text(String name, String mimeType, String body) {
        return new RedirectResource(name, mimeType, "UTF-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeName(String name) {
        String value = name == null ? "" : name.trim().toLowerCase(Locale.US);
        int separator = value.indexOf(':');
        if (separator >= 0) {
            value = value.substring(0, separator);
        }
        return value;
    }
}
