package com.adguard.android.contentblocker.filtering.advanced;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class AdvancedRuleEngineTest {

    @Test
    public void redirectsMatchingResourceToNoop() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||cdn.example.com/ads.js$script,redirect=noopjs"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision decision = engine.evaluate("https://cdn.example.com/ads.js", "https://example.com");

        assertEquals(FilterDecision.Action.REDIRECT_NOOP_JS, decision.getAction());
    }

    @Test
    public void blocksPopupNavigation() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||popup.example^$popup,domain=example.com"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision decision = engine.evaluatePopup("https://popup.example/path", "https://example.com");

        assertEquals(FilterDecision.Action.BLOCK, decision.getAction());
    }

    @Test
    public void keepsDomainScopedPopupAllowedOnOtherSites() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||popup.example^$popup,domain=example.com"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision decision = engine.evaluatePopup("https://popup.example/path", "https://other.example");

        assertEquals(FilterDecision.Action.ALLOW, decision.getAction());
    }

    @Test
    public void blocksRegexPattern() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "/adserver\\d+\\.js/$script"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision decision = engine.evaluate("https://cdn.example.com/adserver42.js", "https://example.com");

        assertEquals(FilterDecision.Action.BLOCK, decision.getAction());
    }

    @Test
    public void blocksWildcardPattern() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "*://*.tracker.example/*$image"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision decision = engine.evaluate("https://img.tracker.example/pixel.gif", "https://example.com");

        assertEquals(FilterDecision.Action.BLOCK, decision.getAction());
    }
}
