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

    @Test
    public void allowsMatchingExceptionRule() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||ads.example^$script",
                "@@||ads.example^$script,domain=example.com"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision decision = engine.evaluate(new RequestContext(
                "https://ads.example/ad.js",
                "https://example.com",
                RequestContext.TYPE_SCRIPT,
                false));

        assertEquals(FilterDecision.Action.ALLOW, decision.getAction());
    }

    @Test
    public void importantBlockOverridesException() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "@@||ads.example^$script,domain=example.com",
                "||ads.example^$script,domain=example.com,important"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision decision = engine.evaluate(new RequestContext(
                "https://ads.example/ad.js",
                "https://example.com",
                RequestContext.TYPE_SCRIPT,
                false));

        assertEquals(FilterDecision.Action.BLOCK, decision.getAction());
    }

    @Test
    public void respectsResourceTypeOptions() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||cdn.example/resource$script"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision imageDecision = engine.evaluate(new RequestContext(
                "https://cdn.example/resource",
                "https://example.com",
                RequestContext.TYPE_IMAGE,
                false));
        FilterDecision scriptDecision = engine.evaluate(new RequestContext(
                "https://cdn.example/resource",
                "https://example.com",
                RequestContext.TYPE_SCRIPT,
                false));

        assertEquals(FilterDecision.Action.ALLOW, imageDecision.getAction());
        assertEquals(FilterDecision.Action.BLOCK, scriptDecision.getAction());
    }

    @Test
    public void respectsThirdPartyOptions() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||tracker.example^$third-party"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision firstPartyDecision = engine.evaluate(new RequestContext(
                "https://static.tracker.example/pixel.gif",
                "https://www.tracker.example/page",
                RequestContext.TYPE_IMAGE,
                false));
        FilterDecision thirdPartyDecision = engine.evaluate(new RequestContext(
                "https://tracker.example/pixel.gif",
                "https://example.com",
                RequestContext.TYPE_IMAGE,
                false));

        assertEquals(FilterDecision.Action.ALLOW, firstPartyDecision.getAction());
        assertEquals(FilterDecision.Action.BLOCK, thirdPartyDecision.getAction());
    }
}
