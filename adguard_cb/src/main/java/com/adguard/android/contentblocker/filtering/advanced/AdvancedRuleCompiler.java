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

        if (StringUtils.contains(rule, UBO_SCRIPTLET_MARKER)) {
            return compileScriptlet(rule);
        }

        if (isCosmetic(rule)) {
            AdvancedRuleType type = isProcedural(rule) ? AdvancedRuleType.PROCEDURAL_COSMETIC : AdvancedRuleType.COSMETIC;
            return new AdvancedRule(type, rule, domainPrefix(rule), "", "", selector(rule), "", Collections.<String>emptyList());
        }

        return new AdvancedRule(networkType(rule), rule, "", pattern(rule), optionText(rule), "", "", Collections.<String>emptyList());
    }

    private AdvancedRule compileScriptlet(String rule) {
        int markerStart = rule.indexOf(UBO_SCRIPTLET_MARKER);
        int argsStart = markerStart + UBO_SCRIPTLET_MARKER.length();
        int argsEnd = rule.lastIndexOf(')');
        List<String> args = argsEnd > argsStart ? splitArguments(rule.substring(argsStart, argsEnd)) : new ArrayList<String>();
        String scriptletName = args.isEmpty() ? "" : args.remove(0).toLowerCase(Locale.US);
        return new AdvancedRule(AdvancedRuleType.SCRIPTLET, rule, rule.substring(0, markerStart), "", "", "", scriptletName, args);
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
