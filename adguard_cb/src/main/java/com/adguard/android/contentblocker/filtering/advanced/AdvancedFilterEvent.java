package com.adguard.android.contentblocker.filtering.advanced;

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
        this.requestUrl = emptyIfNull(requestUrl);
        this.pageUrl = emptyIfNull(pageUrl);
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
}
