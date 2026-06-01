package com.adguard.android.contentblocker.filtering.advanced;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class AdvancedRuleEngine {

    private final AdvancedRuleSet rules;
    private final AdvancedFilterLogger logger;
    private static final ResourceTypeOption[] RESOURCE_TYPE_OPTIONS = new ResourceTypeOption[]{
            new ResourceTypeOption("script", RequestContext.TYPE_SCRIPT),
            new ResourceTypeOption("image", RequestContext.TYPE_IMAGE),
            new ResourceTypeOption("stylesheet", RequestContext.TYPE_STYLESHEET),
            new ResourceTypeOption("css", RequestContext.TYPE_STYLESHEET),
            new ResourceTypeOption("font", RequestContext.TYPE_FONT),
            new ResourceTypeOption("media", RequestContext.TYPE_MEDIA),
            new ResourceTypeOption("document", RequestContext.TYPE_DOCUMENT),
            new ResourceTypeOption("subdocument", RequestContext.TYPE_SUBDOCUMENT),
            new ResourceTypeOption("frame", RequestContext.TYPE_SUBDOCUMENT),
            new ResourceTypeOption("xmlhttprequest", RequestContext.TYPE_XMLHTTPREQUEST),
            new ResourceTypeOption("xhr", RequestContext.TYPE_XMLHTTPREQUEST),
            new ResourceTypeOption("websocket", RequestContext.TYPE_WEBSOCKET),
            new ResourceTypeOption("ping", RequestContext.TYPE_PING),
            new ResourceTypeOption("other", RequestContext.TYPE_OTHER)
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
        FilterDecision importantDecision = firstMatchingDecision(context, true, false);
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

        FilterDecision decision = firstMatchingDecision(context, false, genericblockDisabled(context));
        recordDecision(decision, context);
        return decision;
    }

    private FilterDecision firstMatchingDecision(RequestContext context, boolean importantOnly, boolean skipGenericRules) {
        for (AdvancedRule rule : rules.getRedirectRules()) {
            if (!rule.isException() &&
                    (!skipGenericRules || !isGenericNetworkRule(rule)) &&
                    (!importantOnly || rule.isImportant()) &&
                    matches(rule, context)) {
                return FilterDecision.redirect(rule, RedirectResource.fromRule(rule));
            }
        }

        for (AdvancedRule rule : rules.getNetworkRules()) {
            if (!rule.isException() &&
                    (!skipGenericRules || !isGenericNetworkRule(rule)) &&
                    (!importantOnly || rule.isImportant()) &&
                    matches(rule, context)) {
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

    private boolean genericblockDisabled(RequestContext context) {
        RequestContext pageContext = new RequestContext(
                context.getPageUrl(),
                context.getPageUrl(),
                RequestContext.TYPE_DOCUMENT,
                true,
                "GET");
        for (AdvancedRule rule : rules.getNetworkRules()) {
            if (rule.isException() && rule.hasOption("genericblock") && matches(rule, pageContext)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGenericNetworkRule(AdvancedRule rule) {
        return rule.getOptionValue("domain").length() == 0 && rule.getOptionValue("from").length() == 0;
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
        return contextMatches(rule, context) && patternMatches(rule, context.getRequestUrl());
    }

    private static boolean contextMatches(AdvancedRule rule, RequestContext context) {
        if (!resourceTypeMatches(rule, context)) {
            return false;
        }
        if (!thirdPartyMatches(rule, context)) {
            return false;
        }
        if (!methodMatches(rule, context)) {
            return false;
        }
        if (!denyallowMatches(rule, context)) {
            return false;
        }
        if (!domainListMatches(rule.getOptionValue("from"), context.getPageUrl())) {
            return false;
        }
        if (!domainListMatches(rule.getOptionValue("to"), context.getRequestUrl())) {
            return false;
        }

        return domainListMatches(rule.getOptionValue("domain"), context.getPageUrl());
    }

    private static boolean domainListMatches(String domainOption, String url) {
        if (domainOption.length() == 0) {
            return true;
        }

        String targetHost = host(url);
        if (targetHost.length() == 0) {
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
                if (domainMatches(targetHost, normalizedDomain.substring(1))) {
                    return false;
                }
            } else {
                hasIncludedDomains = true;
                includedDomainMatches = includedDomainMatches || domainMatches(targetHost, normalizedDomain);
            }
        }

        return !hasIncludedDomains || includedDomainMatches;
    }

    private static boolean resourceTypeMatches(AdvancedRule rule, RequestContext context) {
        boolean hasPositiveType = false;
        boolean positiveTypeMatches = false;

        for (ResourceTypeOption option : RESOURCE_TYPE_OPTIONS) {
            boolean positive = rule.hasOption(option.optionName);
            boolean negative = rule.hasOption("~" + option.optionName);
            boolean typeMatches = option.resourceType.equals(context.getResourceType());

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
        if ((rule.hasOption("third-party") || rule.hasOption("3p")) && !context.isThirdParty()) {
            return false;
        }
        if ((rule.hasOption("~third-party") || rule.hasOption("~3p")) && context.isThirdParty()) {
            return false;
        }
        if (rule.hasOption("1p") && context.isThirdParty()) {
            return false;
        }
        return !rule.hasOption("~1p") || context.isThirdParty();
    }

    private static boolean methodMatches(AdvancedRule rule, RequestContext context) {
        String methodOption = rule.getOptionValue("method");
        if (methodOption.length() == 0) {
            return true;
        }

        boolean hasIncludedMethods = false;
        boolean includedMethodMatches = false;
        String[] methods = methodOption.split("\\|");
        for (String method : methods) {
            String normalizedMethod = method.trim().toUpperCase(Locale.US);
            if (normalizedMethod.length() == 0) {
                continue;
            }
            if (normalizedMethod.startsWith("~")) {
                if (context.getRequestMethod().equals(normalizedMethod.substring(1))) {
                    return false;
                }
            } else {
                hasIncludedMethods = true;
                includedMethodMatches = includedMethodMatches || context.getRequestMethod().equals(normalizedMethod);
            }
        }
        return !hasIncludedMethods || includedMethodMatches;
    }

    private static boolean denyallowMatches(AdvancedRule rule, RequestContext context) {
        String denyallowOption = rule.getOptionValue("denyallow");
        if (denyallowOption.length() == 0) {
            return true;
        }

        String requestHost = host(context.getRequestUrl());
        if (requestHost.length() == 0) {
            return true;
        }

        String[] domains = denyallowOption.split("\\|");
        for (String domain : domains) {
            String normalizedDomain = domain.trim().toLowerCase(Locale.US);
            if (normalizedDomain.length() > 0 && domainMatches(requestHost, normalizedDomain)) {
                return false;
            }
        }
        return true;
    }

    private static boolean patternMatches(AdvancedRule rule, String requestUrl) {
        String pattern = rule.getPattern();
        boolean matchCase = rule.hasOption("match-case");
        if (pattern.length() == 0 || "*".equals(pattern)) {
            return true;
        }

        if (isRegexPattern(pattern)) {
            return regexMatches(pattern, requestUrl, matchCase);
        }

        if (pattern.startsWith("||")) {
            return anchoredDomainMatches(pattern.substring(2), requestUrl, matchCase);
        }

        return wildcardMatches(pattern, requestUrl, matchCase);
    }

    private static boolean anchoredDomainMatches(String pattern, String requestUrl, boolean matchCase) {
        String normalizedPattern = pattern;
        int separator = normalizedPattern.indexOf('^');
        if (separator >= 0) {
            normalizedPattern = normalizedPattern.substring(0, separator);
        }

        String requestHost = host(requestUrl);
        if (requestHost.length() == 0) {
            return wildcardMatches("||" + pattern, requestUrl, matchCase);
        }

        String hostPattern = normalizedPattern;
        String pathPattern = "";
        int pathStart = normalizedPattern.indexOf('/');
        if (pathStart >= 0) {
            hostPattern = normalizedPattern.substring(0, pathStart);
            pathPattern = normalizedPattern.substring(pathStart);
        }

        boolean hostMatches = hostPattern.indexOf('*') >= 0
                ? wildcardMatches(hostPattern.toLowerCase(Locale.US), requestHost, false)
                : domainMatches(requestHost, hostPattern.toLowerCase(Locale.US));
        if (!hostMatches) {
            return false;
        }

        return pathPattern.length() == 0 || wildcardMatches(pathPattern + "*", pathAndQuery(requestUrl), matchCase);
    }

    private static boolean isRegexPattern(String pattern) {
        return pattern.startsWith("/") && pattern.lastIndexOf('/') > 0;
    }

    private static boolean regexMatches(String pattern, String requestUrl, boolean matchCase) {
        int end = pattern.lastIndexOf('/');
        String regex = pattern.substring(1, end);
        int patternFlags = matchCase ? 0 : Pattern.CASE_INSENSITIVE;
        try {
            return Pattern.compile(regex, patternFlags).matcher(requestUrl).find();
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    private static boolean wildcardMatches(String pattern, String value, boolean matchCase) {
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

        int flags = matchCase ? 0 : Pattern.CASE_INSENSITIVE;
        return Pattern.compile(regex.toString(), flags).matcher(value).find();
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

    private static final class ResourceTypeOption {
        private final String optionName;
        private final String resourceType;

        private ResourceTypeOption(String optionName, String resourceType) {
            this.optionName = optionName;
            this.resourceType = resourceType;
        }
    }
}
