package com.adguard.android.contentblocker.filtering.advanced;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class TrackingParameterCleaner {

    private final AdvancedRuleSet rules;

    public TrackingParameterCleaner(AdvancedRuleSet rules) {
        this.rules = rules;
    }

    public String clean(String url) {
        try {
            URI uri = new URI(url);
            String query = uri.getRawQuery();
            if (query == null || query.length() == 0) {
                return url;
            }

            List<String> kept = new ArrayList<>();
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String name = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
                if (!shouldRemove(name)) {
                    kept.add(pair);
                }
            }

            StringBuilder cleaned = new StringBuilder();
            cleaned.append(uri.getScheme()).append("://").append(uri.getRawAuthority()).append(uri.getRawPath());
            if (!kept.isEmpty()) {
                cleaned.append('?').append(join(kept));
            }
            if (uri.getRawFragment() != null) {
                cleaned.append('#').append(uri.getRawFragment());
            }
            return cleaned.toString();
        } catch (Exception ignored) {
            return url;
        }
    }

    private boolean shouldRemove(String parameterName) {
        for (AdvancedRule rule : rules.getRemoveparamRules()) {
            String configured = removeparamValue(rule.getOptionText());
            if (configured.equals(parameterName)) {
                return true;
            }
        }
        return false;
    }

    private static String removeparamValue(String options) {
        int start = options.indexOf("removeparam=");
        if (start < 0) {
            return "";
        }

        String configured = options.substring(start + "removeparam=".length());
        int end = configured.indexOf(',');
        return end >= 0 ? configured.substring(0, end) : configured;
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                result.append('&');
            }
            result.append(values.get(i));
        }
        return result.toString();
    }
}
