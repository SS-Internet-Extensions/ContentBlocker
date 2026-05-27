package com.adguard.android.contentblocker.filtering.advanced;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AdvancedRule {

    private final AdvancedRuleType type;
    private final String originalRule;
    private final String domainPrefix;
    private final String pattern;
    private final String optionText;
    private final String selector;
    private final String scriptletName;
    private final List<String> scriptletArgs;

    public AdvancedRule(AdvancedRuleType type, String originalRule, String domainPrefix, String pattern,
                        String optionText, String selector, String scriptletName, List<String> scriptletArgs) {
        this.type = type;
        this.originalRule = originalRule;
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
}
