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
    private final List<AdvancedRule> badfilterRules;

    AdvancedRuleSet(List<AdvancedRule> rules) {
        badfilterRules = filterBadfilters(rules);
        List<AdvancedRule> enabledRules = removeDisabledRules(rules, badfilterRules);
        networkRules = filter(enabledRules, AdvancedRuleType.NETWORK);
        redirectRules = filter(enabledRules, AdvancedRuleType.REDIRECT);
        popupRules = filter(enabledRules, AdvancedRuleType.POPUP);
        removeparamRules = filter(enabledRules, AdvancedRuleType.REMOVEPARAM);
        cosmeticRules = filter(enabledRules, AdvancedRuleType.COSMETIC);
        proceduralCosmeticRules = filter(enabledRules, AdvancedRuleType.PROCEDURAL_COSMETIC);
        scriptletRules = filter(enabledRules, AdvancedRuleType.SCRIPTLET);
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

    private static List<AdvancedRule> filterBadfilters(List<AdvancedRule> rules) {
        List<AdvancedRule> result = new ArrayList<>();
        for (AdvancedRule rule : rules) {
            if (rule.isBadfilter()) {
                result.add(rule);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static List<AdvancedRule> removeDisabledRules(List<AdvancedRule> rules, List<AdvancedRule> badfilterRules) {
        List<AdvancedRule> result = new ArrayList<>();
        for (AdvancedRule rule : rules) {
            if (!rule.isBadfilter() && !disabledByBadfilter(rule, badfilterRules)) {
                result.add(rule);
            }
        }
        return result;
    }

    private static boolean disabledByBadfilter(AdvancedRule rule, List<AdvancedRule> badfilterRules) {
        String key = rule.getComparableKey();
        for (AdvancedRule badfilterRule : badfilterRules) {
            if (key.equals(badfilterRule.getComparableKey())) {
                return true;
            }
        }
        return false;
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

    public List<AdvancedRule> getBadfilterRules() {
        return badfilterRules;
    }
}
