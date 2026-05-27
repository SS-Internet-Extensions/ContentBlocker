package com.adguard.android.contentblocker.filtering.advanced;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class AdvancedRuleCompilerTest {

    private final AdvancedRuleCompiler compiler = new AdvancedRuleCompiler();

    @Test
    public void separatesRuntimeRuleTypes() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##:has-text(Sponsored)",
                "example.com##+js(set, adBlockDetected, false)",
                "||cdn.example.com/ads.js$script,redirect=noopjs",
                "||popup.example^$popup,domain=example.com",
                "*$removeparam=utm_source",
                "example.com##.ad-banner"));

        assertEquals(1, rules.getProceduralCosmeticRules().size());
        assertEquals(1, rules.getScriptletRules().size());
        assertEquals(1, rules.getRedirectRules().size());
        assertEquals(1, rules.getPopupRules().size());
        assertEquals(1, rules.getRemoveparamRules().size());
        assertEquals(1, rules.getCosmeticRules().size());
    }

    @Test
    public void keepsUnknownScriptletsAsRuntimeDiagnostics() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##+js(trusted-set-cookie, flag, 1)"));

        AdvancedRule rule = rules.getScriptletRules().get(0);
        assertEquals("trusted-set-cookie", rule.getScriptletName());
        assertEquals(AdvancedRuleType.SCRIPTLET, rule.getType());
        assertEquals("example.com##+js(trusted-set-cookie, flag, 1)", rule.getOriginalRule());
    }
}
