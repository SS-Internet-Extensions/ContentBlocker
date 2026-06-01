package com.adguard.android.contentblocker.filtering.advanced;

import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class TrackingParameterCleaner {

    private static final int MAX_REGEX_PATTERN_LENGTH = 256;
    private static final int MAX_WILDCARD_PATTERN_LENGTH = 256;
    private static final int MAX_PARAMETER_NAME_LENGTH = 512;
    private static final int PATTERN_CACHE_SIZE = 256;
    private static final Map<PatternCacheKey, Pattern> PATTERN_CACHE =
            new LinkedHashMap<PatternCacheKey, Pattern>(PATTERN_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<PatternCacheKey, Pattern> eldest) {
                    return size() > PATTERN_CACHE_SIZE;
                }
            };

    private final AdvancedRuleSet rules;
    private final AdvancedFilterLogger logger;

    public TrackingParameterCleaner(AdvancedRuleSet rules) {
        this(rules, null);
    }

    public TrackingParameterCleaner(AdvancedRuleSet rules, AdvancedFilterLogger logger) {
        this.rules = rules;
        this.logger = logger;
    }

    public String clean(String url) {
        return clean(url, url);
    }

    public String clean(String url, String pageUrl) {
        try {
            URI uri = new URI(url);
            String query = uri.getRawQuery();
            if (query == null || query.length() == 0) {
                return url;
            }
            if (uri.getScheme() == null || uri.getRawAuthority() == null) {
                return url;
            }

            List<String> kept = new ArrayList<>();
            RequestContext context = new RequestContext(
                    url,
                    pageUrl == null || pageUrl.length() == 0 ? url : pageUrl,
                    RequestContext.TYPE_DOCUMENT,
                    true);
            boolean removed = false;
            String[] pairs = query.split("&", -1);
            for (String pair : pairs) {
                String name = parameterName(pair);
                if (shouldRemove(name, context)) {
                    removed = true;
                } else {
                    kept.add(pair);
                }
            }
            if (!removed) {
                return url;
            }

            StringBuilder cleaned = new StringBuilder();
            cleaned.append(uri.getScheme()).append("://").append(uri.getRawAuthority());
            if (uri.getRawPath() != null) {
                cleaned.append(uri.getRawPath());
            }
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

    private boolean shouldRemove(String parameterName, RequestContext context) {
        String decodedName = decode(parameterName);
        AdvancedRule importantRemoval = matchingRemoval(parameterName, decodedName, context, true);
        if (importantRemoval != null) {
            recordRemoveparam(importantRemoval, parameterName, context);
            return true;
        }
        AdvancedRule exception = matchingException(parameterName, decodedName, context);
        if (exception != null) {
            recordException(exception, parameterName, context);
            return false;
        }
        AdvancedRule removal = matchingRemoval(parameterName, decodedName, context, false);
        if (removal != null) {
            recordRemoveparam(removal, parameterName, context);
            return true;
        }
        return false;
    }

    private AdvancedRule matchingRemoval(String parameterName, String decodedName, RequestContext context, boolean importantOnly) {
        for (AdvancedRule rule : rules.getRemoveparamRules()) {
            if (!rule.isException() &&
                    (!importantOnly || rule.isImportant()) &&
                    AdvancedRuleEngine.matches(rule, context) &&
                    parameterMatches(rule.getOptionValue("removeparam"), parameterName, decodedName)) {
                return rule;
            }
        }
        return null;
    }

    private AdvancedRule matchingException(String parameterName, String decodedName, RequestContext context) {
        for (AdvancedRule rule : rules.getRemoveparamRules()) {
            if (rule.isException() &&
                    AdvancedRuleEngine.matches(rule, context) &&
                    parameterMatches(rule.getOptionValue("removeparam"), parameterName, decodedName)) {
                return rule;
            }
        }
        return null;
    }

    private void recordRemoveparam(AdvancedRule rule, String parameterName, RequestContext context) {
        if (logger == null) {
            return;
        }
        logger.record(new AdvancedFilterEvent(
                AdvancedFilterEvent.Type.REMOVEPARAM,
                context.getRequestUrl(),
                context.getPageUrl(),
                rule.getOriginalRule(),
                parameterName));
    }

    private void recordException(AdvancedRule rule, String parameterName, RequestContext context) {
        if (logger == null) {
            return;
        }
        logger.record(new AdvancedFilterEvent(
                AdvancedFilterEvent.Type.ALLOW_EXCEPTION,
                context.getRequestUrl(),
                context.getPageUrl(),
                rule.getOriginalRule(),
                parameterName));
    }

    private static boolean parameterMatches(String configured, String parameterName, String decodedName) {
        String value = configured == null ? "" : configured.trim();
        if (value.length() == 0) {
            return false;
        }
        if (isRegex(value)) {
            return regexMatches(value, parameterName) || regexMatches(value, decodedName);
        }
        if (value.indexOf('*') >= 0 || value.indexOf('?') >= 0) {
            return wildcardMatches(value, parameterName) || wildcardMatches(value, decodedName);
        }
        return value.equals(parameterName) || value.equals(decodedName);
    }

    private static boolean isRegex(String value) {
        return value.startsWith("/") && value.lastIndexOf('/') > 0;
    }

    private static boolean regexMatches(String configured, String parameterName) {
        int end = configured.lastIndexOf('/');
        String regex = configured.substring(1, end);
        if (!isSafeMatch(regex, parameterName, MAX_REGEX_PATTERN_LENGTH)) {
            return false;
        }
        String flags = configured.substring(end + 1);
        int patternFlags = flags.indexOf('i') >= 0 ? Pattern.CASE_INSENSITIVE : 0;
        try {
            return cachedPattern(regex, patternFlags).matcher(parameterName).find();
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    private static boolean wildcardMatches(String configured, String parameterName) {
        if (!isSafeMatch(configured, parameterName, MAX_WILDCARD_PATTERN_LENGTH)) {
            return false;
        }
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < configured.length(); i++) {
            char c = configured.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append('.');
            } else {
                appendQuoted(regex, c);
            }
        }
        regex.append('$');
        return cachedPattern(regex.toString(), 0).matcher(parameterName).matches();
    }

    private static boolean isSafeMatch(String pattern, String value, int maxPatternLength) {
        return pattern.length() <= maxPatternLength &&
                value != null &&
                value.length() <= MAX_PARAMETER_NAME_LENGTH;
    }

    private static Pattern cachedPattern(String regex, int flags) {
        PatternCacheKey key = new PatternCacheKey(regex, flags);
        synchronized (PATTERN_CACHE) {
            Pattern pattern = PATTERN_CACHE.get(key);
            if (pattern == null) {
                pattern = Pattern.compile(regex, flags);
                PATTERN_CACHE.put(key, pattern);
            }
            return pattern;
        }
    }

    private static void appendQuoted(StringBuilder regex, char c) {
        if ("\\.[]{}()+-^$?|".indexOf(c) >= 0) {
            regex.append('\\');
        }
        regex.append(c);
    }

    private static String parameterName(String pair) {
        int separator = pair.indexOf('=');
        return separator >= 0 ? pair.substring(0, separator) : pair;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
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

    private static final class PatternCacheKey {
        private final String regex;
        private final int flags;

        private PatternCacheKey(String regex, int flags) {
            this.regex = regex;
            this.flags = flags;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PatternCacheKey)) {
                return false;
            }
            PatternCacheKey that = (PatternCacheKey) other;
            return flags == that.flags && regex.equals(that.regex);
        }

        @Override
        public int hashCode() {
            return 31 * regex.hashCode() + flags;
        }
    }
}
