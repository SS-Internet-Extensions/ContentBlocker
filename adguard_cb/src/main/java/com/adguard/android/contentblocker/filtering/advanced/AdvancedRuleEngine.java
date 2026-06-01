package com.adguard.android.contentblocker.filtering.advanced;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class AdvancedRuleEngine {

    private static final int MAX_REGEX_PATTERN_LENGTH = 1024;
    private static final int MAX_WILDCARD_PATTERN_LENGTH = 2048;
    private static final int MAX_MATCH_INPUT_LENGTH = 16384;
    private static final int PATTERN_CACHE_SIZE = 512;
    private static final int PAGE_EXCEPTION_CACHE_SIZE = 128;
    private static final Map<PatternCacheKey, Pattern> PATTERN_CACHE =
            new LinkedHashMap<PatternCacheKey, Pattern>(PATTERN_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<PatternCacheKey, Pattern> eldest) {
                    return size() > PATTERN_CACHE_SIZE;
                }
            };

    private final AdvancedRuleSet rules;
    private final AdvancedFilterLogger logger;
    private final Map<String, Boolean> genericblockCache =
            new LinkedHashMap<String, Boolean>(PAGE_EXCEPTION_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > PAGE_EXCEPTION_CACHE_SIZE;
                }
            };
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
        FilterDecision importantAllDecision = firstMatchingAllPopupDecision(context, true);
        if (importantAllDecision.getAction() != FilterDecision.Action.ALLOW) {
            recordDecision(importantAllDecision, context);
            return importantAllDecision;
        }

        AdvancedRule exception = firstMatchingException(context, rules.getPopupRules());
        if (exception == null) {
            exception = firstMatchingAllPopupException(context);
        }
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
        FilterDecision allDecision = firstMatchingAllPopupDecision(context, false);
        if (allDecision.getAction() != FilterDecision.Action.ALLOW) {
            recordDecision(allDecision, context);
            return allDecision;
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

    private FilterDecision firstMatchingAllPopupDecision(RequestContext context, boolean importantOnly) {
        for (AdvancedRule rule : rules.getNetworkRules()) {
            if (!rule.isException() &&
                    rule.hasOption("all") &&
                    (!importantOnly || rule.isImportant()) &&
                    matches(rule, context)) {
                return FilterDecision.of(FilterDecision.Action.BLOCK, rule);
            }
        }
        return FilterDecision.allow();
    }

    private AdvancedRule firstMatchingAllPopupException(RequestContext context) {
        for (AdvancedRule rule : rules.getNetworkRules()) {
            if (rule.isException() && rule.hasOption("all") && matches(rule, context)) {
                return rule;
            }
        }
        return null;
    }

    private boolean genericblockDisabled(RequestContext context) {
        String pageUrl = context.getPageUrl();
        Boolean cached = cachedGenericblock(pageUrl);
        if (cached != null) {
            return cached;
        }

        RequestContext pageContext = new RequestContext(
                pageUrl,
                pageUrl,
                RequestContext.TYPE_DOCUMENT,
                true,
                "GET");
        for (AdvancedRule rule : rules.getNetworkRules()) {
            if (rule.isException() && rule.hasOption("genericblock") && matches(rule, pageContext)) {
                cacheGenericblock(pageUrl, true);
                return true;
            }
        }
        cacheGenericblock(pageUrl, false);
        return false;
    }

    private Boolean cachedGenericblock(String pageUrl) {
        synchronized (genericblockCache) {
            return genericblockCache.get(pageUrl);
        }
    }

    private void cacheGenericblock(String pageUrl, boolean disabled) {
        synchronized (genericblockCache) {
            genericblockCache.put(pageUrl, disabled);
        }
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
        if ((rule.hasOption("first-party") || rule.hasOption("1p")) && context.isThirdParty()) {
            return false;
        }
        if ((rule.hasOption("~first-party") || rule.hasOption("~1p")) && !context.isThirdParty()) {
            return false;
        }

        boolean strictFirstParty = strictFirstParty(context);
        boolean strictThirdParty = strictThirdParty(context);
        if (rule.hasOption("strict1p") && !strictFirstParty) {
            return false;
        }
        if (rule.hasOption("~strict1p") && strictFirstParty) {
            return false;
        }
        if (rule.hasOption("strict3p") && !strictThirdParty) {
            return false;
        }
        return !rule.hasOption("~strict3p") || !strictThirdParty;
    }

    private static boolean strictFirstParty(RequestContext context) {
        String requestHost = host(context.getRequestUrl());
        String pageHost = host(context.getPageUrl());
        return requestHost.length() > 0 && requestHost.equals(pageHost);
    }

    private static boolean strictThirdParty(RequestContext context) {
        String requestHost = host(context.getRequestUrl());
        String pageHost = host(context.getPageUrl());
        return requestHost.length() > 0 && pageHost.length() > 0 && !requestHost.equals(pageHost);
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
        if (!isSafeMatch(regex, requestUrl, MAX_REGEX_PATTERN_LENGTH)) {
            return false;
        }
        int patternFlags = matchCase ? 0 : Pattern.CASE_INSENSITIVE;
        try {
            return cachedPattern(regex, patternFlags).matcher(requestUrl).find();
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    private static boolean wildcardMatches(String pattern, String value, boolean matchCase) {
        if (!isSafeMatch(pattern, value, MAX_WILDCARD_PATTERN_LENGTH)) {
            return false;
        }

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
        return cachedPattern(regex.toString(), flags).matcher(value).find();
    }

    private static boolean isSafeMatch(String pattern, String value, int maxPatternLength) {
        return pattern.length() <= maxPatternLength &&
                value != null &&
                value.length() <= MAX_MATCH_INPUT_LENGTH;
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
