package com.adguard.android.contentblocker.filtering.advanced;

import java.net.URI;
import java.util.Locale;

public final class RequestContext {

    public static final String TYPE_DOCUMENT = "document";
    public static final String TYPE_SUBDOCUMENT = "subdocument";
    public static final String TYPE_SCRIPT = "script";
    public static final String TYPE_STYLESHEET = "stylesheet";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_FONT = "font";
    public static final String TYPE_MEDIA = "media";
    public static final String TYPE_OTHER = "other";

    private final String requestUrl;
    private final String pageUrl;
    private final String resourceType;
    private final boolean mainFrame;
    private final boolean thirdParty;

    public RequestContext(String requestUrl, String pageUrl, String resourceType, boolean mainFrame) {
        this.requestUrl = requestUrl;
        this.pageUrl = pageUrl;
        this.resourceType = normalizeResourceType(resourceType);
        this.mainFrame = mainFrame;
        this.thirdParty = computeThirdParty(requestUrl, pageUrl);
    }

    public static RequestContext infer(String requestUrl, String pageUrl) {
        return infer(requestUrl, pageUrl, false, "");
    }

    public static RequestContext infer(String requestUrl, String pageUrl, boolean mainFrame, String acceptHeader) {
        return new RequestContext(requestUrl, pageUrl, inferResourceType(requestUrl, mainFrame, acceptHeader), mainFrame);
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public String getResourceType() {
        return resourceType;
    }

    public boolean isMainFrame() {
        return mainFrame;
    }

    public boolean isThirdParty() {
        return thirdParty;
    }

    private static String inferResourceType(String requestUrl, boolean mainFrame, String acceptHeader) {
        if (mainFrame) {
            return TYPE_DOCUMENT;
        }

        String accept = acceptHeader == null ? "" : acceptHeader.toLowerCase(Locale.US);
        if (accept.contains("text/css")) {
            return TYPE_STYLESHEET;
        }
        if (accept.contains("image/")) {
            return TYPE_IMAGE;
        }
        if (accept.contains("font/")) {
            return TYPE_FONT;
        }
        if (accept.contains("audio/") || accept.contains("video/")) {
            return TYPE_MEDIA;
        }
        if (accept.contains("text/html")) {
            return TYPE_SUBDOCUMENT;
        }

        String path = path(requestUrl).toLowerCase(Locale.US);
        if (path.endsWith(".js") || path.endsWith(".mjs")) {
            return TYPE_SCRIPT;
        }
        if (path.endsWith(".css")) {
            return TYPE_STYLESHEET;
        }
        if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".svg")) {
            return TYPE_IMAGE;
        }
        if (path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") || path.endsWith(".otf")) {
            return TYPE_FONT;
        }
        if (path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mp3") || path.endsWith(".m4a")) {
            return TYPE_MEDIA;
        }

        return TYPE_OTHER;
    }

    private static String normalizeResourceType(String resourceType) {
        String value = resourceType == null ? "" : resourceType.trim().toLowerCase(Locale.US);
        return value.length() == 0 ? TYPE_OTHER : value;
    }

    private static boolean computeThirdParty(String requestUrl, String pageUrl) {
        String requestHost = registrableHost(requestUrl);
        String pageHost = registrableHost(pageUrl);
        return requestHost.length() > 0 && pageHost.length() > 0 && !requestHost.equals(pageHost);
    }

    private static String registrableHost(String url) {
        String host = host(url);
        if (host.length() == 0) {
            return "";
        }
        String[] labels = host.split("\\.");
        if (labels.length < 2) {
            return host;
        }
        return labels[labels.length - 2] + "." + labels[labels.length - 1];
    }

    private static String host(String url) {
        try {
            String host = new URI(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String path(String url) {
        try {
            String path = new URI(url).getRawPath();
            return path == null ? "" : path;
        } catch (Exception ignored) {
            return "";
        }
    }
}
