package com.adguard.android.contentblocker.filtering.advanced;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void marksExceptionAndImportantNetworkRules() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "@@||ads.example^$script,domain=example.com,important"));

        AdvancedRule rule = rules.getNetworkRules().get(0);
        assertTrue(rule.isException());
        assertTrue(rule.isImportant());
        assertTrue(rule.hasOption("script"));
        assertEquals("example.com", rule.getOptionValue("domain"));
        assertEquals("||ads.example^", rule.getPattern());
    }

    @Test
    public void badfilterDisablesMatchingRule() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "||ads.example^$script",
                "||ads.example^$script,badfilter"));

        assertEquals(0, rules.getNetworkRules().size());
        assertEquals(1, rules.getBadfilterRules().size());
    }

    @Test
    public void marksCosmeticAndScriptletExceptions() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com#@#.ad-banner",
                "example.com#@#+js(set, adBlockDetected, false)"));

        assertTrue(rules.getCosmeticRules().get(0).isException());
        assertTrue(rules.getScriptletRules().get(0).isException());
        assertFalse(rules.getScriptletRules().get(0).isBadfilter());
    }
}
