package com.adguard.android.contentblocker.filtering.advanced;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class StaticCosmeticScriptBuilder {

    public String build(List<AdvancedRule> rules) {
        return build(rules, "");
    }

    public String build(List<AdvancedRule> rules, String pageUrl) {
        return build(rules, pageUrl, null);
    }

    public String build(List<AdvancedRule> rules, String pageUrl, List<AdvancedRule> networkRules) {
        StringBuilder css = new StringBuilder();
        Set<String> exceptions = exceptionSelectors(rules, pageUrl);
        boolean elemhideDisabled = CosmeticRuleControl.elemhideDisabled(networkRules, pageUrl);
        boolean generichideDisabled = CosmeticRuleControl.generichideDisabled(networkRules, pageUrl);
        boolean specifichideDisabled = CosmeticRuleControl.specifichideDisabled(networkRules, pageUrl);

        if (rules != null && !elemhideDisabled) {
            for (AdvancedRule rule : rules) {
                if (rule == null || rule.getType() != AdvancedRuleType.COSMETIC || rule.isException()) {
                    continue;
                }
                if (generichideDisabled && isGenericRule(rule)) {
                    continue;
                }
                if (specifichideDisabled && !isGenericRule(rule)) {
                    continue;
                }
                if (!domainPrefixMatches(rule, pageUrl)) {
                    continue;
                }
                String selector = cosmeticSelector(rule);
                if (selector.length() > 0 && !exceptions.contains(selector)) {
                    css.append(selector).append("{display:none!important;}\n");
                }
            }
        }

        return "(function(){var css='" + escapeJsString(css.toString()) + "';" +
                "if(!css){return;}var style=document.createElement('style');" +
                "style.setAttribute('data-adguard-advanced','static-cosmetic');" +
                "style.appendChild(document.createTextNode(css));" +
                "(document.documentElement||document.head||document.body).appendChild(style);})();";
    }

    private static Set<String> exceptionSelectors(List<AdvancedRule> rules, String pageUrl) {
        Set<String> selectors = new HashSet<>();
        if (rules == null) {
            return selectors;
        }
        for (AdvancedRule rule : rules) {
            if (rule != null && rule.getType() == AdvancedRuleType.COSMETIC &&
                    rule.isException() && domainPrefixMatches(rule, pageUrl)) {
                String selector = cosmeticSelector(rule);
                if (selector.length() > 0) {
                    selectors.add(selector);
                }
            }
        }
        return selectors;
    }

    private static String cosmeticSelector(AdvancedRule rule) {
        String selector = trimToEmpty(rule.getSelector());
        if (selector.startsWith("#@#")) {
            return trimToEmpty(selector.substring("#@#".length()));
        }
        if (selector.startsWith("##")) {
            return trimToEmpty(selector.substring("##".length()));
        }
        return "";
    }

    private static boolean isGenericRule(AdvancedRule rule) {
        return trimToEmpty(rule.getDomainPrefix()).length() == 0;
    }

    private static boolean domainPrefixMatches(AdvancedRule rule, String pageUrl) {
        String domainPrefix = trimToEmpty(rule.getDomainPrefix()).toLowerCase(Locale.US);
        if (domainPrefix.length() == 0 || pageUrl == null || pageUrl.length() == 0) {
            return true;
        }

        String pageHost = host(pageUrl);
        if (pageHost.length() == 0) {
            return false;
        }

        boolean hasIncludedDomains = false;
        boolean includedDomainMatches = false;
        String[] domains = domainPrefix.split(",");
        for (String domain : domains) {
            String normalizedDomain = trimToEmpty(domain).toLowerCase(Locale.US);
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

    private static boolean domainMatches(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private static String host(String url) {
        try {
            String host = new URI(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String escapeJsString(String value) {
        return trimToEmpty(value)
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
