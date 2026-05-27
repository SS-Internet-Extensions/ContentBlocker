package com.adguard.android.contentblocker.filtering.advanced;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AdvancedRuleCompiler {

    private static final String COMMENT = "!";
    private static final String ADBLOCK_META_START = "[Adblock";
    private static final String UBO_SCRIPTLET_MARKER = "##+js(";
    private static final String UBO_SCRIPTLET_EXCEPTION_MARKER = "#@#+js(";
    private static final String EXCEPTION_PREFIX = "@@";

    private static final Set<String> PROCEDURAL_MARKERS = new HashSet<>(Arrays.asList(
            ":has-text(",
            ":matches-css(",
            ":matches-attr(",
            ":xpath("));

    public AdvancedRuleSet compile(Collection<String> sourceRules) {
        List<AdvancedRule> rules = new ArrayList<>();
        for (String sourceRule : sourceRules) {
            AdvancedRule rule = compileOne(sourceRule);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return new AdvancedRuleSet(rules);
    }

    private AdvancedRule compileOne(String sourceRule) {
        String rule = StringUtils.trimToEmpty(sourceRule);
        if (StringUtils.isBlank(rule) ||
                StringUtils.startsWith(rule, COMMENT) ||
                StringUtils.startsWith(rule, ADBLOCK_META_START)) {
            return null;
        }

        if (StringUtils.contains(rule, UBO_SCRIPTLET_EXCEPTION_MARKER) ||
                StringUtils.contains(rule, UBO_SCRIPTLET_MARKER)) {
            return compileScriptlet(rule);
        }

        if (isCosmetic(rule)) {
            AdvancedRuleType type = isProcedural(rule) ? AdvancedRuleType.PROCEDURAL_COSMETIC : AdvancedRuleType.COSMETIC;
            return createRule(type, rule, isCosmeticException(rule), domainPrefix(rule), "", "", selector(rule), "", Collections.<String>emptyList());
        }

        boolean exception = StringUtils.startsWith(rule, EXCEPTION_PREFIX);
        String networkRule = exception ? rule.substring(EXCEPTION_PREFIX.length()) : rule;
        String options = optionText(networkRule);
        return createRule(networkType(networkRule), rule, exception, "", pattern(networkRule), options, "", "", Collections.<String>emptyList());
    }

    private AdvancedRule compileScriptlet(String rule) {
        boolean exception = StringUtils.contains(rule, UBO_SCRIPTLET_EXCEPTION_MARKER);
        String marker = exception ? UBO_SCRIPTLET_EXCEPTION_MARKER : UBO_SCRIPTLET_MARKER;
        int markerStart = rule.indexOf(marker);
        int argsStart = markerStart + marker.length();
        int argsEnd = rule.lastIndexOf(')');
        List<String> args = argsEnd > argsStart ? splitArguments(rule.substring(argsStart, argsEnd)) : new ArrayList<String>();
        String scriptletName = args.isEmpty() ? "" : args.remove(0).toLowerCase(Locale.US);
        return createRule(AdvancedRuleType.SCRIPTLET, rule, exception, rule.substring(0, markerStart), "", "", "", scriptletName, args);
    }

    private static AdvancedRule createRule(AdvancedRuleType type, String originalRule, boolean exception,
                                           String domainPrefix, String pattern, String optionText,
                                           String selector, String scriptletName, List<String> scriptletArgs) {
        return new AdvancedRule(
                type,
                originalRule,
                exception,
                optionEnabled(optionText, "important"),
                optionEnabled(optionText, "badfilter"),
                domainPrefix,
                pattern,
                optionText,
                selector,
                scriptletName,
                scriptletArgs);
    }

    private static AdvancedRuleType networkType(String rule) {
        String options = optionText(rule);
        if (StringUtils.contains(options, "redirect=") || StringUtils.contains(options, "redirect-rule=")) {
            return AdvancedRuleType.REDIRECT;
        }
        if (StringUtils.contains(options, "popup")) {
            return AdvancedRuleType.POPUP;
        }
        if (StringUtils.contains(options, "removeparam=")) {
            return AdvancedRuleType.REMOVEPARAM;
        }
        return AdvancedRuleType.NETWORK;
    }

    private static boolean isCosmetic(String rule) {
        return StringUtils.contains(rule, "##") || StringUtils.contains(rule, "#@#");
    }

    private static boolean isCosmeticException(String rule) {
        return StringUtils.contains(rule, "#@#");
    }

    private static boolean isProcedural(String rule) {
        for (String marker : PROCEDURAL_MARKERS) {
            if (StringUtils.contains(rule, marker)) {
                return true;
            }
        }
        return false;
    }

    private static String domainPrefix(String rule) {
        int index = rule.indexOf('#');
        return index > 0 ? rule.substring(0, index) : "";
    }

    private static String selector(String rule) {
        int index = rule.indexOf('#');
        return index >= 0 ? rule.substring(index) : "";
    }

    private static String pattern(String rule) {
        int optionsStart = rule.indexOf('$');
        return optionsStart >= 0 ? rule.substring(0, optionsStart) : rule;
    }

    private static String optionText(String rule) {
        int optionsStart = rule.indexOf('$');
        return optionsStart >= 0 && optionsStart < rule.length() - 1 ? rule.substring(optionsStart + 1) : "";
    }

    private static boolean optionEnabled(String options, String name) {
        if (StringUtils.isBlank(options)) {
            return false;
        }
        String[] parts = options.split(",");
        for (String part : parts) {
            if (name.equals(StringUtils.trim(part))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> splitArguments(String value) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quote = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c == '\'' || c == '"') && (i == 0 || value.charAt(i - 1) != '\\')) {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                } else if (quote == c) {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == ',' && !quoted) {
                result.add(StringUtils.trim(current.toString()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            result.add(StringUtils.trim(current.toString()));
        }

        return result;
    }
}
