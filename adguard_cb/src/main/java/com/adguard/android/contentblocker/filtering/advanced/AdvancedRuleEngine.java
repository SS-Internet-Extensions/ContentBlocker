package com.adguard.android.contentblocker.filtering.advanced;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class AdvancedRuleEngine {

    private final AdvancedRuleSet rules;

    public AdvancedRuleEngine(AdvancedRuleSet rules) {
        this.rules = rules;
    }

    public FilterDecision evaluate(String requestUrl, String pageUrl) {
        for (AdvancedRule rule : rules.getRedirectRules()) {
            if (matches(rule, requestUrl, pageUrl)) {
                String options = rule.getOptionText();
                if (options.contains("noopcss")) {
                    return FilterDecision.of(FilterDecision.Action.REDIRECT_NOOP_CSS, rule);
                }
                if (options.contains("noopjs")) {
                    return FilterDecision.of(FilterDecision.Action.REDIRECT_NOOP_JS, rule);
                }
                return FilterDecision.of(FilterDecision.Action.REDIRECT_EMPTY, rule);
            }
        }

        for (AdvancedRule rule : rules.getNetworkRules()) {
            if (matches(rule, requestUrl, pageUrl)) {
                return FilterDecision.of(FilterDecision.Action.BLOCK, rule);
            }
        }

        return FilterDecision.allow();
    }

    public FilterDecision evaluatePopup(String requestUrl, String pageUrl) {
        for (AdvancedRule rule : rules.getPopupRules()) {
            if (matches(rule, requestUrl, pageUrl)) {
                return FilterDecision.of(FilterDecision.Action.BLOCK, rule);
            }
        }
        return FilterDecision.allow();
    }

    private static boolean matches(AdvancedRule rule, String requestUrl, String pageUrl) {
        return contextMatches(rule, pageUrl) && patternMatches(rule.getPattern(), requestUrl);
    }

    private static boolean contextMatches(AdvancedRule rule, String pageUrl) {
        String domainOption = optionValue(rule.getOptionText(), "domain");
        if (domainOption.length() == 0) {
            return true;
        }

        String pageHost = host(pageUrl);
        if (pageHost.length() == 0) {
            return false;
        }

        boolean hasIncludedDomains = false;
        boolean includedDomainMatches = false;
        String[] domains = domainOption.split("\\|");
        for (String domain : domains) {
            String normalizedDomain = domain.trim().toLowerCase(Locale.US);
            if (normalizedDomain.length() == 0) {
                continue;
            }
            if (normalizedDomain.startsWith("~")) {
                if (domainMatches(pageHost, normalizedDomain.substring(1))) {
                    return false;
                }
            } else {
                hasIncludedDomains = true;
                includedDomainMatches = includedDomainMatches || domainMatches(pageHost, normalizedDomain);
            }
        }

        return !hasIncludedDomains || includedDomainMatches;
    }

    private static boolean patternMatches(String pattern, String requestUrl) {
        if (pattern.length() == 0 || "*".equals(pattern)) {
            return true;
        }

        if (isRegexPattern(pattern)) {
            return regexMatches(pattern, requestUrl);
        }

        if (pattern.startsWith("||")) {
            return anchoredDomainMatches(pattern.substring(2), requestUrl);
        }

        return wildcardMatches(pattern, requestUrl);
    }

    private static boolean anchoredDomainMatches(String pattern, String requestUrl) {
        String normalizedPattern = pattern;
        int separator = normalizedPattern.indexOf('^');
        if (separator >= 0) {
            normalizedPattern = normalizedPattern.substring(0, separator);
        }

        String requestHost = host(requestUrl);
        if (requestHost.length() == 0) {
            return wildcardMatches("||" + pattern, requestUrl);
        }

        String hostPattern = normalizedPattern;
        String pathPattern = "";
        int pathStart = normalizedPattern.indexOf('/');
        if (pathStart >= 0) {
            hostPattern = normalizedPattern.substring(0, pathStart);
            pathPattern = normalizedPattern.substring(pathStart);
        }

        boolean hostMatches = hostPattern.indexOf('*') >= 0
                ? wildcardMatches(hostPattern, requestHost)
                : domainMatches(requestHost, hostPattern.toLowerCase(Locale.US));
        if (!hostMatches) {
            return false;
        }

        return pathPattern.length() == 0 || wildcardMatches(pathPattern + "*", pathAndQuery(requestUrl));
    }

    private static boolean isRegexPattern(String pattern) {
        return pattern.startsWith("/") && pattern.lastIndexOf('/') > 0;
    }

    private static boolean regexMatches(String pattern, String requestUrl) {
        int end = pattern.lastIndexOf('/');
        String regex = pattern.substring(1, end);
        try {
            return Pattern.compile(regex).matcher(requestUrl).find();
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    private static boolean wildcardMatches(String pattern, String value) {
        StringBuilder regex = new StringBuilder();
        if (pattern.startsWith("|")) {
            regex.append('^');
            pattern = pattern.substring(1);
        }

        boolean endAnchored = pattern.endsWith("|");
        if (endAnchored) {
            pattern = pattern.substring(0, pattern.length() - 1);
        }

        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '^') {
                regex.append("(?:[^A-Za-z0-9_.%-]|$)");
            } else {
                appendQuoted(regex, c);
            }
        }

        if (endAnchored) {
            regex.append('$');
        }

        return Pattern.compile(regex.toString()).matcher(value).find();
    }

    private static void appendQuoted(StringBuilder regex, char c) {
        if ("\\.[]{}()+-^$?|".indexOf(c) >= 0) {
            regex.append('\\');
        }
        regex.append(c);
    }

    private static String optionValue(String options, String optionName) {
        String[] parts = options.split(",");
        String prefix = optionName + "=";
        for (String part : parts) {
            String option = part.trim();
            if (option.startsWith(prefix)) {
                return option.substring(prefix.length());
            }
        }
        return "";
    }

    private static boolean domainMatches(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private static String host(String url) {
        if (url == null || url.length() == 0) {
            return "";
        }
        try {
            String host = new URI(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String pathAndQuery(String url) {
        try {
            URI uri = new URI(url);
            StringBuilder result = new StringBuilder();
            String path = uri.getRawPath();
            result.append(path == null || path.length() == 0 ? "/" : path);
            if (uri.getRawQuery() != null) {
                result.append('?').append(uri.getRawQuery());
            }
            return result.toString();
        } catch (Exception ignored) {
            return url;
        }
    }
}
