package com.adguard.android.contentblocker.filtering.advanced;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AdvancedRuleEngineTest {

    @Test
    public void redirectsMatchingResourceToNoop() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||cdn.example.com/ads.js$script,redirect=noopjs"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision decision = engine.evaluate("https://cdn.example.com/ads.js", "https://example.com");

        assertRedirect(decision, "noopjs", "application/javascript", 0);
    }

    @Test
    public void redirectsToTextResource() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||cdn.example.com/pixel.txt$redirect=nooptext"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision decision = engine.evaluate("https://cdn.example.com/pixel.txt", "https://example.com");

        assertRedirect(decision, "nooptext", "text/plain", 0);
    }

    @Test
    public void redirectsToHtmlResource() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||cdn.example.com/frame.html$subdocument,redirect=noophtml"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision decision = engine.evaluate(new RequestContext(
                "https://cdn.example.com/frame.html",
                "https://example.com",
                RequestContext.TYPE_SUBDOCUMENT,
                false));

        assertRedirect(decision, "noophtml", "text/html", 54);
    }

    @Test
    public void redirectsToEmptyResource() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||cdn.example.com/beacon$redirect=empty"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision decision = engine.evaluate("https://cdn.example.com/beacon", "https://example.com");

        assertRedirect(decision, "empty", "text/plain", 0);
    }

    @Test
    public void redirectsToTransparentGifResource() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||cdn.example.com/pixel.gif$image,redirect=1x1.gif"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision decision = engine.evaluate("https://cdn.example.com/pixel.gif", "https://example.com");

        assertRedirect(decision, "1x1.gif", "image/gif", 43);
    }

    @Test
    public void supportsRedirectRuleOption() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||cdn.example.com/style.css$stylesheet,redirect-rule=noopcss"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision decision = engine.evaluate("https://cdn.example.com/style.css", "https://example.com");

        assertRedirect(decision, "noopcss", "text/css", 0);
    }

    @Test
    public void unknownRedirectFallsBackToEmptyTextResource() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||cdn.example.com/resource$redirect=unknown-resource"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision decision = engine.evaluate("https://cdn.example.com/resource", "https://example.com");

        assertRedirect(decision, "unknown-resource", "text/plain", 0);
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
    public void matchesPatternsCaseInsensitiveByDefault() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "/adserver\\d+\\.js/$script"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision decision = engine.evaluate("https://cdn.example.com/ADSERVER42.JS", "https://example.com");

        assertEquals(FilterDecision.Action.BLOCK, decision.getAction());
    }

    @Test
    public void respectsMatchCaseOption() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||cdn.example/CaseSensitive.js$script,match-case"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision lowerCaseDecision = engine.evaluate(new RequestContext(
                "https://cdn.example/casesensitive.js",
                "https://example.com",
                RequestContext.TYPE_SCRIPT,
                false));
        FilterDecision exactCaseDecision = engine.evaluate(new RequestContext(
                "https://cdn.example/CaseSensitive.js",
                "https://example.com",
                RequestContext.TYPE_SCRIPT,
                false));

        assertEquals(FilterDecision.Action.ALLOW, lowerCaseDecision.getAction());
        assertEquals(FilterDecision.Action.BLOCK, exactCaseDecision.getAction());
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
    public void supportsResourceTypeAliases() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||cdn.example/style.css$css",
                "||cdn.example/frame.html$frame",
                "||api.example/data.json$xhr"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision cssDecision = engine.evaluate(new RequestContext(
                "https://cdn.example/style.css",
                "https://example.com",
                RequestContext.TYPE_STYLESHEET,
                false));
        FilterDecision frameDecision = engine.evaluate(new RequestContext(
                "https://cdn.example/frame.html",
                "https://example.com",
                RequestContext.TYPE_SUBDOCUMENT,
                false));
        FilterDecision xhrDecision = engine.evaluate(RequestContext.infer(
                "https://api.example/data.json",
                "https://example.com",
                false,
                "application/json"));

        assertEquals(FilterDecision.Action.BLOCK, cssDecision.getAction());
        assertEquals(FilterDecision.Action.BLOCK, frameDecision.getAction());
        assertEquals(FilterDecision.Action.BLOCK, xhrDecision.getAction());
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

    @Test
    public void supportsThirdPartyAliases() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||tracker.example/pixel$3p",
                "||tracker.example/account$1p"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision thirdPartyDecision = engine.evaluate(new RequestContext(
                "https://tracker.example/pixel",
                "https://example.com",
                RequestContext.TYPE_IMAGE,
                false));
        FilterDecision firstPartyDecision = engine.evaluate(new RequestContext(
                "https://static.tracker.example/account",
                "https://www.tracker.example/profile",
                RequestContext.TYPE_IMAGE,
                false));
        FilterDecision wrongContextDecision = engine.evaluate(new RequestContext(
                "https://tracker.example/account",
                "https://example.com",
                RequestContext.TYPE_IMAGE,
                false));

        assertEquals(FilterDecision.Action.BLOCK, thirdPartyDecision.getAction());
        assertEquals(FilterDecision.Action.BLOCK, firstPartyDecision.getAction());
        assertEquals(FilterDecision.Action.ALLOW, wrongContextDecision.getAction());
    }

    @Test
    public void respectsMethodOption() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||api.example/collect$method=POST",
                "||api.example/ping$method=~POST"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision getCollectDecision = engine.evaluate(new RequestContext(
                "https://api.example/collect",
                "https://example.com",
                RequestContext.TYPE_XMLHTTPREQUEST,
                false,
                "GET"));
        FilterDecision postCollectDecision = engine.evaluate(new RequestContext(
                "https://api.example/collect",
                "https://example.com",
                RequestContext.TYPE_XMLHTTPREQUEST,
                false,
                "POST"));
        FilterDecision postPingDecision = engine.evaluate(new RequestContext(
                "https://api.example/ping",
                "https://example.com",
                RequestContext.TYPE_XMLHTTPREQUEST,
                false,
                "POST"));
        FilterDecision getPingDecision = engine.evaluate(new RequestContext(
                "https://api.example/ping",
                "https://example.com",
                RequestContext.TYPE_XMLHTTPREQUEST,
                false,
                "GET"));

        assertEquals(FilterDecision.Action.ALLOW, getCollectDecision.getAction());
        assertEquals(FilterDecision.Action.BLOCK, postCollectDecision.getAction());
        assertEquals(FilterDecision.Action.ALLOW, postPingDecision.getAction());
        assertEquals(FilterDecision.Action.BLOCK, getPingDecision.getAction());
    }

    @Test
    public void respectsDenyallowOption() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "*$third-party,script,domain=example.com,denyallow=allowed.cdn.example|static.partner.example"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules);

        FilterDecision blockedDecision = engine.evaluate(new RequestContext(
                "https://ads.example/ad.js",
                "https://example.com",
                RequestContext.TYPE_SCRIPT,
                false));
        FilterDecision allowedDecision = engine.evaluate(new RequestContext(
                "https://sub.allowed.cdn.example/app.js",
                "https://example.com",
                RequestContext.TYPE_SCRIPT,
                false));
        FilterDecision partnerDecision = engine.evaluate(new RequestContext(
                "https://static.partner.example/app.js",
                "https://example.com",
                RequestContext.TYPE_SCRIPT,
                false));

        assertEquals(FilterDecision.Action.BLOCK, blockedDecision.getAction());
        assertEquals(FilterDecision.Action.ALLOW, allowedDecision.getAction());
        assertEquals(FilterDecision.Action.ALLOW, partnerDecision.getAction());
    }

    private static void assertRedirect(FilterDecision decision, String resourceName, String mimeType, int bodySize) {
        assertEquals(FilterDecision.Action.REDIRECT, decision.getAction());
        assertNotNull(decision.getRedirectResource());
        assertEquals(resourceName, decision.getRedirectResource().getName());
        assertEquals(mimeType, decision.getRedirectResource().getMimeType());
        assertEquals(bodySize, decision.getRedirectResource().getBody().length);
    }
}
