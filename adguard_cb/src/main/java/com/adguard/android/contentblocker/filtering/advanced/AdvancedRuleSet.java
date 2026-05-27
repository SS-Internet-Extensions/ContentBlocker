package com.adguard.android.contentblocker.filtering.advanced;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AdvancedRuleSet {

    private final List<AdvancedRule> networkRules;
    private final List<AdvancedRule> redirectRules;
    private final List<AdvancedRule> popupRules;
    private final List<AdvancedRule> removeparamRules;
    private final List<AdvancedRule> cosmeticRules;
    private final List<AdvancedRule> proceduralCosmeticRules;
    private final List<AdvancedRule> scriptletRules;

    AdvancedRuleSet(List<AdvancedRule> rules) {
        networkRules = filter(rules, AdvancedRuleType.NETWORK);
        redirectRules = filter(rules, AdvancedRuleType.REDIRECT);
        popupRules = filter(rules, AdvancedRuleType.POPUP);
        removeparamRules = filter(rules, AdvancedRuleType.REMOVEPARAM);
        cosmeticRules = filter(rules, AdvancedRuleType.COSMETIC);
        proceduralCosmeticRules = filter(rules, AdvancedRuleType.PROCEDURAL_COSMETIC);
        scriptletRules = filter(rules, AdvancedRuleType.SCRIPTLET);
    }

    private static List<AdvancedRule> filter(List<AdvancedRule> rules, AdvancedRuleType type) {
        List<AdvancedRule> result = new ArrayList<>();
        for (AdvancedRule rule : rules) {
            if (rule.getType() == type) {
                result.add(rule);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<AdvancedRule> getNetworkRules() {
        return networkRules;
    }

    public List<AdvancedRule> getRedirectRules() {
        return redirectRules;
    }

    public List<AdvancedRule> getPopupRules() {
        return popupRules;
    }

    public List<AdvancedRule> getRemoveparamRules() {
        return removeparamRules;
    }

    public List<AdvancedRule> getCosmeticRules() {
        return cosmeticRules;
    }

    public List<AdvancedRule> getProceduralCosmeticRules() {
        return proceduralCosmeticRules;
    }

    public List<AdvancedRule> getScriptletRules() {
        return scriptletRules;
    }
}
