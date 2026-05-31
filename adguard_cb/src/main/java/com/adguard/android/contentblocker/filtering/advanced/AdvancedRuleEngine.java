package com.adguard.android.contentblocker.filtering.advanced;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class AdvancedRuleEngine {

    private final AdvancedRuleSet rules;
    private final AdvancedFilterLogger logger;
    private static final String[] RESOURCE_TYPE_OPTIONS = new String[]{
            "script",
            "image",
            "stylesheet",
            "font",
            "media",
            "document",
            "subdocument"
    };

    public AdvancedRuleEngine(AdvancedRuleSet rules) {
        this(rules, null);
    }

    public AdvancedRuleEngine(AdvancedRuleSet rules, AdvancedFilterLogger logger) {
        this.rules = rules;
        this.logger = logger;
    }

    public FilterDecision evaluate(String requestUrl, String pageUrl) {
        return evaluate(RequestContext.infer(requestUrl, pageUrl));
    }

    public FilterDecision evaluate(RequestContext context) {
        FilterDecision importantDecision = firstMatchingDecision(context, true);
        if (importantDecision.getAction() != FilterDecision.Action.ALLOW) {
            recordDecision(importantDecision, context);
            return importantDecision;
        }

        AdvancedRule exception = firstMatchingException(context, rules.getRedirectRules());
        if (exception == null) {
            exception = firstMatchingException(context, rules.getNetworkRules());
        }
        if (exception != null) {
            recordException(exception, context);
            return FilterDecision.allow();
        }

        FilterDecision decision = firstMatchingDecision(context, false);
        recordDecision(decision, context);
        return decision;
    }

    private FilterDecision firstMatchingDecision(RequestContext context, boolean importantOnly) {
        for (AdvancedRule rule : rules.getRedirectRules()) {
            if (!rule.isException() && (!importantOnly || rule.isImportant()) && matches(rule, context)) {
                return FilterDecision.redirect(rule, RedirectResource.fromRule(rule));
            }
        }

        for (AdvancedRule rule : rules.getNetworkRules()) {
            if (!rule.isException() && (!importantOnly || rule.isImportant()) && matches(rule, context)) {
                return FilterDecision.of(FilterDecision.Action.BLOCK, rule);
            }
        }

        return FilterDecision.allow();
    }

    public FilterDecision evaluatePopup(String requestUrl, String pageUrl) {
        return evaluatePopup(RequestContext.infer(requestUrl, pageUrl, true, ""));
    }

    public FilterDecision evaluatePopup(RequestContext context) {
        for (AdvancedRule rule : rules.getPopupRules()) {
            if (!rule.isException() && rule.isImportant() && matches(rule, context)) {
                FilterDecision decision = FilterDecision.of(FilterDecision.Action.BLOCK, rule);
                recordDecision(decision, context);
                return decision;
            }
        }

        AdvancedRule exception = firstMatchingException(context, rules.getPopupRules());
        if (exception != null) {
            recordException(exception, context);
            return FilterDecision.allow();
        }

        for (AdvancedRule rule : rules.getPopupRules()) {
            if (!rule.isException() && matches(rule, context)) {
                FilterDecision decision = FilterDecision.of(FilterDecision.Action.BLOCK, rule);
                recordDecision(decision, context);
                return decision;
            }
        }
        return FilterDecision.allow();
    }

    private static AdvancedRule firstMatchingException(RequestContext context, Iterable<AdvancedRule> rules) {
        for (AdvancedRule rule : rules) {
            if (rule.isException() && matches(rule, context)) {
                return rule;
            }
        }
        return null;
    }

    private void recordDecision(FilterDecision decision, RequestContext context) {
        if (logger == null || decision.getAction() == FilterDecision.Action.ALLOW) {
            return;
        }

        AdvancedFilterEvent.Type type = decision.getAction() == FilterDecision.Action.REDIRECT
                ? AdvancedFilterEvent.Type.REDIRECT
                : AdvancedFilterEvent.Type.BLOCK;
        String detail = decision.getAction() == FilterDecision.Action.REDIRECT && decision.getRedirectResource() != null
                ? decision.getRedirectResource().getName()
                : context.getResourceType();
        logger.record(new AdvancedFilterEvent(
                type,
                context.getRequestUrl(),
                context.getPageUrl(),
                decision.getRule() == null ? "" : decision.getRule().getOriginalRule(),
                detail));
    }

    private void recordException(AdvancedRule rule, RequestContext context) {
        if (logger == null) {
            return;
        }
        logger.record(new AdvancedFilterEvent(
                AdvancedFilterEvent.Type.ALLOW_EXCEPTION,
                context.getRequestUrl(),
                context.getPageUrl(),
                rule.getOriginalRule(),
                rule.getType().name()));
    }

    static boolean matches(AdvancedRule rule, RequestContext context) {
        return contextMatches(rule, context) && patternMatches(rule.getPattern(), context.getRequestUrl());
    }

    private static boolean contextMatches(AdvancedRule rule, RequestContext context) {
        if (!resourceTypeMatches(rule, context)) {
            return false;
        }
        if (!thirdPartyMatches(rule, context)) {
            return false;
        }

        String domainOption = rule.getOptionValue("domain");
        if (domainOption.length() == 0) {
            return true;
        }

        String pageHost = host(context.getPageUrl());
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

    private static boolean resourceTypeMatches(AdvancedRule rule, RequestContext context) {
        boolean hasPositiveType = false;
        boolean positiveTypeMatches = false;

        for (String resourceType : RESOURCE_TYPE_OPTIONS) {
            boolean positive = rule.hasOption(resourceType);
            boolean negative = rule.hasOption("~" + resourceType);
            boolean typeMatches = resourceType.equals(context.getResourceType());

            if (negative && typeMatches) {
                return false;
            }
            if (positive) {
                hasPositiveType = true;
                positiveTypeMatches = positiveTypeMatches || typeMatches;
            }
        }

        return !hasPositiveType || positiveTypeMatches;
    }

    private static boolean thirdPartyMatches(AdvancedRule rule, RequestContext context) {
        if (rule.hasOption("third-party") && !context.isThirdParty()) {
            return false;
        }
        return !rule.hasOption("~third-party") || !context.isThirdParty();
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
                ? wildcardMatches(hostPattern.toLowerCase(Locale.US), requestHost)
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
