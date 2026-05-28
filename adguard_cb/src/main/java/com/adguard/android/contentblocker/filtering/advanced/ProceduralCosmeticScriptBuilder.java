package com.adguard.android.contentblocker.filtering.advanced;

import java.net.URI;
import java.util.List;
import java.util.Locale;

public final class ProceduralCosmeticScriptBuilder {

    private static final String HAS_TEXT_MARKER = ":has-text(";
    private static final String MATCHES_ATTR_MARKER = ":matches-attr(";
    private static final String MATCHES_CSS_MARKER = ":matches-css(";
    private static final String XPATH_MARKER = ":xpath(";

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
        script.append("function hideMatchesAttr(selector,name,value){var nodes;try{nodes=document.querySelectorAll(selector||'*');}");
        script.append("catch(e){nodes=document.getElementsByTagName('*');}");
        script.append("var matcher=value?new RegExp(value):null;");
        script.append("for(var i=0;i<nodes.length;i++){var node=nodes[i];if(!node||!node.getAttribute){continue;}");
        script.append("var attr=node.getAttribute(name);if(attr!==null&&(!matcher||matcher.test(attr))){");
        script.append("node.style.setProperty('display','none','important');}}}\n");
        script.append("function hideMatchesCss(selector,name,value){var nodes;try{nodes=document.querySelectorAll(selector||'*');}");
        script.append("catch(e){nodes=document.getElementsByTagName('*');}");
        script.append("var matcher=value?new RegExp(value):null;");
        script.append("for(var i=0;i<nodes.length;i++){var node=nodes[i];if(!node){continue;}");
        script.append("var style=window.getComputedStyle?window.getComputedStyle(node):null;");
        script.append("var css=style?style.getPropertyValue(name):'';if(css&&(!matcher||matcher.test(css))){");
        script.append("node.style.setProperty('display','none','important');}}}\n");
        script.append("function hideXpath(xpath){try{var result=document.evaluate(xpath,document,null,XPathResult.ORDERED_NODE_SNAPSHOT_TYPE,null);");
        script.append("for(var i=0;i<result.snapshotLength;i++){var node=result.snapshotItem(i);");
        script.append("if(node&&node.style){node.style.setProperty('display','none','important');}}}catch(e){}}\n");
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
        ProceduralRule parsed = parseRule(rule);
        if (parsed == null) {
            return;
        }

        if (parsed.type == ProceduralRule.TYPE_HAS_TEXT) {
            script.append("hideHasText('");
            script.append(escapeJsString(parsed.cssSelector));
            script.append("','");
            script.append(escapeJsString(parsed.firstValue));
            script.append("');\n");
        } else if (parsed.type == ProceduralRule.TYPE_MATCHES_ATTR) {
            script.append("hideMatchesAttr('");
            script.append(escapeJsString(parsed.cssSelector));
            script.append("','");
            script.append(escapeJsString(parsed.firstValue));
            script.append("','");
            script.append(escapeJsString(parsed.secondValue));
            script.append("');\n");
        } else if (parsed.type == ProceduralRule.TYPE_MATCHES_CSS) {
            script.append("hideMatchesCss('");
            script.append(escapeJsString(parsed.cssSelector));
            script.append("','");
            script.append(escapeJsString(parsed.firstValue));
            script.append("','");
            script.append(escapeJsString(parsed.secondValue));
            script.append("');\n");
        } else if (parsed.type == ProceduralRule.TYPE_XPATH) {
            script.append("hideXpath('");
            script.append(escapeJsString(parsed.firstValue));
            script.append("');\n");
        }
    }

    private static ProceduralRule parseRule(AdvancedRule rule) {
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

        ProceduralMarker marker = proceduralMarker(selector);
        if (marker == null) {
            return null;
        }

        int markerStart = selector.indexOf(marker.marker);
        if (markerStart < 0) {
            return null;
        }

        int valueStart = markerStart + marker.marker.length();
        int valueEnd = selector.lastIndexOf(')');
        if (valueEnd < valueStart) {
            return null;
        }

        String cssSelector = trimToEmpty(selector.substring(0, markerStart));
        if (cssSelector.length() == 0) {
            cssSelector = "*";
        }
        String value = unquote(trimToEmpty(selector.substring(valueStart, valueEnd)));

        if (marker.type == ProceduralRule.TYPE_HAS_TEXT) {
            return new ProceduralRule(marker.type, cssSelector, value, "");
        }
        if (marker.type == ProceduralRule.TYPE_XPATH) {
            return new ProceduralRule(marker.type, cssSelector, value, "");
        }

        String first = value;
        String second = "";
        int separator = value.indexOf('=');
        if (separator < 0) {
            separator = value.indexOf(':');
        }
        if (separator >= 0) {
            first = trimToEmpty(value.substring(0, separator));
            second = trimToEmpty(value.substring(separator + 1));
        }
        return new ProceduralRule(marker.type, cssSelector, unquote(first), unquote(second));
    }

    private static ProceduralMarker proceduralMarker(String selector) {
        if (selector.indexOf(HAS_TEXT_MARKER) >= 0) {
            return new ProceduralMarker(HAS_TEXT_MARKER, ProceduralRule.TYPE_HAS_TEXT);
        }
        if (selector.indexOf(MATCHES_ATTR_MARKER) >= 0) {
            return new ProceduralMarker(MATCHES_ATTR_MARKER, ProceduralRule.TYPE_MATCHES_ATTR);
        }
        if (selector.indexOf(MATCHES_CSS_MARKER) >= 0) {
            return new ProceduralMarker(MATCHES_CSS_MARKER, ProceduralRule.TYPE_MATCHES_CSS);
        }
        if (selector.indexOf(XPATH_MARKER) >= 0) {
            return new ProceduralMarker(XPATH_MARKER, ProceduralRule.TYPE_XPATH);
        }
        return null;
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

    private static final class ProceduralMarker {
        private final String marker;
        private final int type;

        private ProceduralMarker(String marker, int type) {
            this.marker = marker;
            this.type = type;
        }
    }

    private static final class ProceduralRule {
        private static final int TYPE_HAS_TEXT = 1;
        private static final int TYPE_MATCHES_ATTR = 2;
        private static final int TYPE_MATCHES_CSS = 3;
        private static final int TYPE_XPATH = 4;

        private final int type;
        private final String cssSelector;
        private final String firstValue;
        private final String secondValue;

        private ProceduralRule(int type, String cssSelector, String firstValue, String secondValue) {
            this.type = type;
            this.cssSelector = cssSelector;
            this.firstValue = firstValue;
            this.secondValue = secondValue;
        }
    }
}
