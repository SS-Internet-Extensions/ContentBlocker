package com.adguard.android.contentblocker.filtering.advanced;

import java.net.URI;

public final class AdvancedFilterEvent {

    public enum Type {
        BLOCK,
        ALLOW_EXCEPTION,
        REDIRECT,
        REMOVEPARAM,
        SCRIPTLET,
        COSMETIC
    }

    private final Type type;
    private final String requestUrl;
    private final String pageUrl;
    private final String ruleText;
    private final String detail;
    private final long timestampMillis;

    public AdvancedFilterEvent(Type type, String requestUrl, String pageUrl, String ruleText, String detail) {
        this(type, requestUrl, pageUrl, ruleText, detail, System.currentTimeMillis());
    }

    public AdvancedFilterEvent(Type type, String requestUrl, String pageUrl, String ruleText, String detail, long timestampMillis) {
        this.type = type;
        this.requestUrl = redactUrl(requestUrl);
        this.pageUrl = redactUrl(pageUrl);
        this.ruleText = emptyIfNull(ruleText);
        this.detail = emptyIfNull(detail);
        this.timestampMillis = timestampMillis;
    }

    public Type getType() {
        return type;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public String getRuleText() {
        return ruleText;
    }

    public String getDetail() {
        return detail;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private static String redactUrl(String value) {
        String url = emptyIfNull(value);
        if (url.length() == 0) {
            return "";
        }
        try {
            URI uri = new URI(url);
            StringBuilder result = new StringBuilder();
            if (uri.getScheme() != null) {
                result.append(uri.getScheme()).append(':');
            }
            if (uri.getRawAuthority() != null) {
                result.append("//").append(uri.getRawAuthority());
            }
            if (uri.getRawPath() != null) {
                result.append(uri.getRawPath());
            }
            if (uri.getRawQuery() != null) {
                result.append("?...");
            }
            if (uri.getRawFragment() != null) {
                result.append("#...");
            }
            return result.length() == 0 ? stripSensitiveSuffix(url) : result.toString();
        } catch (Exception ignored) {
            return stripSensitiveSuffix(url);
        }
    }

    private static String stripSensitiveSuffix(String value) {
        int queryStart = value.indexOf('?');
        int fragmentStart = value.indexOf('#');
        int cutoff = -1;
        if (queryStart >= 0 && fragmentStart >= 0) {
            cutoff = Math.min(queryStart, fragmentStart);
        } else if (queryStart >= 0) {
            cutoff = queryStart;
        } else if (fragmentStart >= 0) {
            cutoff = fragmentStart;
        }
        return cutoff >= 0 ? value.substring(0, cutoff) : value;
    }
}
