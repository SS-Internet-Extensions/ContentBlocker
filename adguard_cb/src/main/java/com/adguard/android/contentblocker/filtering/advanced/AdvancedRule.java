package com.adguard.android.contentblocker.filtering.advanced;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AdvancedRule {

    private final AdvancedRuleType type;
    private final String originalRule;
    private final boolean exception;
    private final boolean important;
    private final boolean badfilter;
    private final String domainPrefix;
    private final String pattern;
    private final String optionText;
    private final String selector;
    private final String scriptletName;
    private final List<String> scriptletArgs;

    public AdvancedRule(AdvancedRuleType type, String originalRule, String domainPrefix, String pattern,
                        String optionText, String selector, String scriptletName, List<String> scriptletArgs) {
        this(type, originalRule, false, false, false, domainPrefix, pattern, optionText, selector, scriptletName, scriptletArgs);
    }

    public AdvancedRule(AdvancedRuleType type, String originalRule, boolean exception, boolean important,
                        boolean badfilter, String domainPrefix, String pattern, String optionText,
                        String selector, String scriptletName, List<String> scriptletArgs) {
        this.type = type;
        this.originalRule = originalRule;
        this.exception = exception;
        this.important = important;
        this.badfilter = badfilter;
        this.domainPrefix = domainPrefix;
        this.pattern = pattern;
        this.optionText = optionText;
        this.selector = selector;
        this.scriptletName = scriptletName;
        this.scriptletArgs = Collections.unmodifiableList(new ArrayList<>(scriptletArgs));
    }

    public AdvancedRuleType getType() {
        return type;
    }

    public String getOriginalRule() {
        return originalRule;
    }

    public boolean isException() {
        return exception;
    }

    public boolean isImportant() {
        return important;
    }

    public boolean isBadfilter() {
        return badfilter;
    }

    public String getDomainPrefix() {
        return domainPrefix;
    }

    public String getPattern() {
        return pattern;
    }

    public String getOptionText() {
        return optionText;
    }

    public String getSelector() {
        return selector;
    }

    public String getScriptletName() {
        return scriptletName;
    }

    public List<String> getScriptletArgs() {
        return scriptletArgs;
    }

    public boolean hasOption(String optionName) {
        for (String option : optionParts()) {
            if (option.equals(optionName) || option.startsWith(optionName + "=")) {
                return true;
            }
        }
        return false;
    }

    public String getOptionValue(String optionName) {
        String prefix = optionName + "=";
        for (String option : optionParts()) {
            if (option.startsWith(prefix)) {
                return option.substring(prefix.length());
            }
        }
        return "";
    }

    public String getComparableOptions() {
        StringBuilder result = new StringBuilder();
        for (String option : optionParts()) {
            if ("badfilter".equals(option)) {
                continue;
            }
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(option);
        }
        return result.toString();
    }

    public String getComparableKey() {
        return type + "|" + exception + "|" + domainPrefix + "|" + pattern + "|" + getComparableOptions() +
                "|" + selector + "|" + scriptletName + "|" + scriptletArgs;
    }

    private List<String> optionParts() {
        if (optionText == null || optionText.length() == 0) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        String[] parts = optionText.split(",");
        for (String part : parts) {
            String option = part.trim();
            if (option.length() > 0) {
                result.add(option);
            }
        }
        return result;
    }
}
