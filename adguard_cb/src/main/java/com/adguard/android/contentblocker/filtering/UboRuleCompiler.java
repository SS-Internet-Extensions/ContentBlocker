package com.adguard.android.contentblocker.filtering;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class UboRuleCompiler {

    private static final String COMMENT = "!";
    private static final String ADBLOCK_META_START = "[Adblock";
    private static final String UBO_SCRIPTLET_MARKER = "##+js(";
    private static final String UBO_EXCEPTION_SCRIPTLET_MARKER = "#@#+js(";

    private static final Set<String> PROCEDURAL_COSMETIC_MARKERS = new HashSet<>(Arrays.asList(
            ":has-text(",
            ":matches-attr(",
            ":matches-css(",
            ":xpath(",
            ":watch-attr("));

    private static final Set<String> PASS_THROUGH_OPTIONS = new HashSet<>(Arrays.asList(
            "1p",
            "3p",
            "all",
            "document",
            "domain",
            "elemhide",
            "font",
            "frame",
            "image",
            "media",
            "object",
            "other",
            "ping",
            "popup",
            "removeparam",
            "redirect",
            "redirect-rule",
            "script",
            "stylesheet",
            "subdocument",
            "third-party",
            "webrtc",
            "xmlhttprequest"));

    private static final Map<String, String> SCRIPTLET_ALIASES = new HashMap<>();

    static {
        SCRIPTLET_ALIASES.put("aopw", "abort-on-property-write");
        SCRIPTLET_ALIASES.put("abort-on-property-write", "abort-on-property-write");
        SCRIPTLET_ALIASES.put("aopr", "abort-on-property-read");
        SCRIPTLET_ALIASES.put("abort-on-property-read", "abort-on-property-read");
        SCRIPTLET_ALIASES.put("acis", "abort-current-inline-script");
        SCRIPTLET_ALIASES.put("abort-current-inline-script", "abort-current-inline-script");
        SCRIPTLET_ALIASES.put("ra", "remove-attr");
        SCRIPTLET_ALIASES.put("remove-attr", "remove-attr");
        SCRIPTLET_ALIASES.put("rc", "remove-class");
        SCRIPTLET_ALIASES.put("remove-class", "remove-class");
        SCRIPTLET_ALIASES.put("set", "set-constant");
        SCRIPTLET_ALIASES.put("set-constant", "set-constant");
    }

    public List<String> compileAll(Collection<String> sourceRules) {
        List<String> compiled = new ArrayList<>();
        for (String sourceRule : sourceRules) {
            compiled.addAll(compile(sourceRule).getCompiledRules());
        }
        return compiled;
    }

    public CompileResult compile(String sourceRule) {
        String rule = StringUtils.trimToEmpty(sourceRule);

        if (StringUtils.isBlank(rule)) {
            return CompileResult.supported(sourceRule, Collections.<String>emptyList());
        }

        if (StringUtils.startsWith(rule, COMMENT) || StringUtils.startsWith(rule, ADBLOCK_META_START)) {
            return CompileResult.supported(sourceRule, Collections.singletonList(rule));
        }

        if (StringUtils.contains(rule, UBO_EXCEPTION_SCRIPTLET_MARKER)) {
            return CompileResult.unsupported(rule, "scriptlet exception");
        }

        if (StringUtils.contains(rule, UBO_SCRIPTLET_MARKER)) {
            return compileScriptlet(rule);
        }

        if (isProceduralCosmetic(rule)) {
            return CompileResult.unsupported(rule, "procedural cosmetic filter");
        }

        if (isCosmeticRule(rule)) {
            return CompileResult.supported(sourceRule, Collections.singletonList(rule));
        }

        if (!hasSupportedOptions(rule)) {
            return CompileResult.unsupported(rule, "unsupported network option");
        }

        return CompileResult.supported(sourceRule, Collections.singletonList(rule));
    }

    private CompileResult compileScriptlet(String rule) {
        int markerStart = rule.indexOf(UBO_SCRIPTLET_MARKER);
        int argsStart = markerStart + UBO_SCRIPTLET_MARKER.length();
        int argsEnd = rule.lastIndexOf(')');

        if (argsEnd <= argsStart) {
            return CompileResult.unsupported(rule, "malformed scriptlet");
        }

        String domainPrefix = rule.substring(0, markerStart);
        List<String> args = splitArguments(rule.substring(argsStart, argsEnd));

        if (args.isEmpty()) {
            return CompileResult.unsupported(rule, "malformed scriptlet");
        }

        String scriptletName = args.remove(0).toLowerCase(Locale.US);
        String mappedName = SCRIPTLET_ALIASES.get(scriptletName);
        if (mappedName == null) {
            return CompileResult.unsupported(rule, "scriptlet " + scriptletName);
        }

        List<String> quoted = new ArrayList<>();
        quoted.add("'" + escapeSingleQuotes(mappedName) + "'");
        for (String arg : args) {
            quoted.add("'" + escapeSingleQuotes(arg) + "'");
        }

        String compiled = domainPrefix + "#%#//scriptlet(" + StringUtils.join(quoted, ", ") + ")";
        return CompileResult.supported(rule, Collections.singletonList(compiled));
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

    private static boolean isProceduralCosmetic(String rule) {
        if (!StringUtils.contains(rule, "##")) {
            return false;
        }

        for (String marker : PROCEDURAL_COSMETIC_MARKERS) {
            if (StringUtils.contains(rule, marker)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isCosmeticRule(String rule) {
        return StringUtils.contains(rule, "##") ||
                StringUtils.contains(rule, "#@#") ||
                StringUtils.contains(rule, "#$#") ||
                StringUtils.contains(rule, "#@$#") ||
                StringUtils.contains(rule, "$$") ||
                StringUtils.contains(rule, "$@$");
    }

    private static boolean hasSupportedOptions(String rule) {
        int optionsStart = rule.indexOf('$');
        if (optionsStart < 0 || optionsStart == rule.length() - 1) {
            return true;
        }

        String options = rule.substring(optionsStart + 1);
        String[] optionParts = StringUtils.split(options, ',');
        if (optionParts == null) {
            return true;
        }

        for (String optionPart : optionParts) {
            String option = StringUtils.trimToEmpty(optionPart);
            if (StringUtils.startsWith(option, "~")) {
                option = option.substring(1);
            }

            int valueStart = option.indexOf('=');
            String optionName = valueStart >= 0 ? option.substring(0, valueStart) : option;
            optionName = optionName.toLowerCase(Locale.US);

            if (!PASS_THROUGH_OPTIONS.contains(optionName)) {
                return false;
            }
        }

        return true;
    }

    private static String escapeSingleQuotes(String value) {
        return StringUtils.replace(StringUtils.trimToEmpty(value), "'", "\\'");
    }
}
