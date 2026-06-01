package com.adguard.android.contentblocker.filtering.advanced;

import java.util.List;

final class CosmeticRuleControl {

    private CosmeticRuleControl() {
    }

    static boolean elemhideDisabled(List<AdvancedRule> networkRules, String pageUrl) {
        return hasMatchingException(networkRules, pageUrl, "elemhide");
    }

    static boolean generichideDisabled(List<AdvancedRule> networkRules, String pageUrl) {
        return hasMatchingException(networkRules, pageUrl, "generichide");
    }

    private static boolean hasMatchingException(List<AdvancedRule> networkRules, String pageUrl, String option) {
        if (networkRules == null || pageUrl == null || pageUrl.length() == 0) {
            return false;
        }

        RequestContext context = new RequestContext(pageUrl, pageUrl, RequestContext.TYPE_DOCUMENT, true);
        for (AdvancedRule rule : networkRules) {
            if (rule != null && rule.isException() && rule.hasOption(option) && AdvancedRuleEngine.matches(rule, context)) {
                return true;
            }
        }
        return false;
    }
}
