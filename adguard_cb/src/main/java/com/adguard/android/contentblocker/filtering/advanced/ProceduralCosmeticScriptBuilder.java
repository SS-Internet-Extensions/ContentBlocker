package com.adguard.android.contentblocker.filtering.advanced;

import java.net.URI;
import java.util.List;
import java.util.Locale;

public final class ProceduralCosmeticScriptBuilder {

    private static final String HAS_TEXT_MARKER = ":has-text(";

    public String build(List<AdvancedRule> rules) {
        return build(rules, "");
    }

    public String build(List<AdvancedRule> rules, String pageUrl) {
        StringBuilder script = new StringBuilder();
        script.append("(function(){\n");
        script.append("function hideHasText(selector,text){var nodes;try{nodes=document.querySelectorAll(selector||'*');}");
        script.append("catch(e){nodes=document.getElementsByTagName('*');}");
        script.append("for(var i=0;i<nodes.length;i++){var node=nodes[i];");
        script.append("if(node&&node.textContent&&node.textContent.indexOf(text)!==-1){");
        script.append("node.style.setProperty('display','none','important');}}}\n");
        script.append("function applyProceduralCosmetics(){\n");

        if (rules != null) {
            for (AdvancedRule rule : rules) {
                if (domainPrefixMatches(rule, pageUrl)) {
                    appendRule(script, rule);
                }
            }
        }

        script.append("}\n");
        script.append("applyProceduralCosmetics();\n");
        script.append("if(typeof MutationObserver!=='undefined'){");
        script.append("new MutationObserver(function(){applyProceduralCosmetics();})");
        script.append(".observe(document.documentElement||document,{childList:true,subtree:true,characterData:true});}\n");
        script.append("})();");
        return script.toString();
    }

    private static void appendRule(StringBuilder script, AdvancedRule rule) {
        HasTextRule parsed = parseHasTextRule(rule);
        if (parsed == null) {
            return;
        }

        script.append("hideHasText('");
        script.append(escapeJsString(parsed.cssSelector));
        script.append("','");
        script.append(escapeJsString(parsed.text));
        script.append("');\n");
    }

    private static HasTextRule parseHasTextRule(AdvancedRule rule) {
        if (rule == null || rule.getType() != AdvancedRuleType.PROCEDURAL_COSMETIC) {
            return null;
        }

        String selector = trimToEmpty(rule.getSelector());
        if (selector.startsWith("#@#")) {
            return null;
        }
        if (selector.startsWith("##")) {
            selector = selector.substring(2);
        }

        int markerStart = selector.indexOf(HAS_TEXT_MARKER);
        if (markerStart < 0) {
            return null;
        }

        int textStart = markerStart + HAS_TEXT_MARKER.length();
        int textEnd = selector.lastIndexOf(')');
        if (textEnd < textStart) {
            return null;
        }

        String cssSelector = trimToEmpty(selector.substring(0, markerStart));
        if (cssSelector.length() == 0) {
            cssSelector = "*";
        }
        String text = unquote(trimToEmpty(selector.substring(textStart, textEnd)));
        return new HasTextRule(cssSelector, text);
    }

    private static String unquote(String value) {
        if (value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String escapeJsString(String value) {
        return trimToEmpty(value)
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
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

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class HasTextRule {
        private final String cssSelector;
        private final String text;

        private HasTextRule(String cssSelector, String text) {
            this.cssSelector = cssSelector;
            this.text = text;
        }
    }
}
