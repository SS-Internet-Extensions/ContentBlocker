package com.adguard.android.contentblocker.filtering.advanced;

import java.net.URI;
import java.util.List;
import java.util.Locale;

public final class ScriptletScriptBuilder {

    public String build(List<AdvancedRule> rules) {
        return build(rules, "");
    }

    public String build(List<AdvancedRule> rules, String pageUrl) {
        StringBuilder script = new StringBuilder();
        script.append("(function(){\n");
        script.append("window.open=function(){return null;};\n");
        script.append("function getScriptletTarget(path){var parts=path.split('.');var owner=window;");
        script.append("for(var i=0;i<parts.length-1;i++){var part=parts[i];");
        script.append("if(!part){continue;}if(owner[part]===undefined||owner[part]===null){owner[part]={};}");
        script.append("owner=owner[part];}return{owner:owner,prop:parts[parts.length-1]};}\n");
        script.append("function setConstant(path,value){var target=getScriptletTarget(path);");
        script.append("Object.defineProperty(target.owner,target.prop,{configurable:true,");
        script.append("get:function(){return value;},set:function(){}});}\n");
        script.append("function abortOnPropertyRead(path){var target=getScriptletTarget(path);");
        script.append("Object.defineProperty(target.owner,target.prop,{configurable:true,");
        script.append("get:function(){throw new ReferenceError('Blocked property read');},set:function(value){}});}\n");
        script.append("function abortOnPropertyWrite(path){var target=getScriptletTarget(path);");
        script.append("Object.defineProperty(target.owner,target.prop,{configurable:true,");
        script.append("set:function(){throw new ReferenceError('Blocked property write');}});}\n");

        if (rules != null) {
            for (AdvancedRule rule : rules) {
                if (domainPrefixMatches(rule, pageUrl)) {
                    appendRule(script, rule);
                }
            }
        }

        script.append("})();");
        return script.toString();
    }

    private static void appendRule(StringBuilder script, AdvancedRule rule) {
        if (rule == null || rule.getType() != AdvancedRuleType.SCRIPTLET) {
            return;
        }

        String scriptletName = normalizeName(rule.getScriptletName());
        List<String> args = rule.getScriptletArgs();
        if ("set-constant".equals(scriptletName)) {
            appendSetConstant(script, args);
        } else if ("abort-on-property-read".equals(scriptletName)) {
            appendAbortOnPropertyRead(script, args);
        } else if ("abort-on-property-write".equals(scriptletName)) {
            appendAbortOnPropertyWrite(script, args);
        }
    }

    private static void appendSetConstant(StringBuilder script, List<String> args) {
        if (args.size() < 2) {
            return;
        }

        script.append("try{setConstant('");
        script.append(escapeJsString(args.get(0)));
        script.append("',");
        script.append(toConstantValue(args.get(1)));
        script.append(");}catch(e){}\n");
    }

    private static void appendAbortOnPropertyRead(StringBuilder script, List<String> args) {
        if (args.isEmpty()) {
            return;
        }

        script.append("try{abortOnPropertyRead('");
        script.append(escapeJsString(args.get(0)));
        script.append("');}catch(e){}\n");
    }

    private static void appendAbortOnPropertyWrite(StringBuilder script, List<String> args) {
        if (args.isEmpty()) {
            return;
        }

        script.append("try{abortOnPropertyWrite('");
        script.append(escapeJsString(args.get(0)));
        script.append("');}catch(e){}\n");
    }

    private static String normalizeName(String name) {
        String value = trimToEmpty(name).toLowerCase(Locale.US);
        if ("set".equals(value)) {
            return "set-constant";
        }
        if ("aopr".equals(value)) {
            return "abort-on-property-read";
        }
        if ("aopw".equals(value)) {
            return "abort-on-property-write";
        }
        return value;
    }

    private static String toConstantValue(String value) {
        String trimmed = trimToEmpty(value);
        if ("true".equals(trimmed) ||
                "false".equals(trimmed) ||
                "null".equals(trimmed) ||
                "undefined".equals(trimmed) ||
                trimmed.matches("-?\\d+(\\.\\d+)?")) {
            return trimmed;
        }
        return "'" + escapeJsString(trimmed) + "'";
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
}
