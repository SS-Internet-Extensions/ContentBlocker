package com.adguard.android.contentblocker.filtering.advanced;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;

public class ScriptBuilderTest {

    private final AdvancedRuleCompiler compiler = new AdvancedRuleCompiler();

    @Test
    public void buildsSetScriptlet() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##+js(set, adBlockDetected, false)"));

        String script = new ScriptletScriptBuilder().build(rules.getScriptletRules());

        assertTrue(script.startsWith("(function(){"));
        assertTrue(script.contains("window.open=function(){return null;}"));
        assertTrue(script.contains("Object.defineProperty"));
        assertTrue(script.contains("adBlockDetected"));
        assertTrue(script.contains("false"));
    }

    @Test
    public void buildsHasTextProceduralCosmetic() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##div:has-text(Sponsored)"));

        String script = new ProceduralCosmeticScriptBuilder().build(rules.getProceduralCosmeticRules());

        assertTrue(script.startsWith("(function(){"));
        assertTrue(script.contains("MutationObserver"));
        assertTrue(script.contains("display','none','important"));
        assertTrue(script.contains("Sponsored"));
    }

    @Test
    public void appliesScriptletsOnlyToMatchingDomain() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##+js(set, adBlockDetected, false)"));

        String script = new ScriptletScriptBuilder().build(rules.getScriptletRules(), "https://other.example");

        assertTrue(script.contains("window.open=function(){return null;}"));
        assertTrue(!script.contains("adBlockDetected"));
    }

    @Test
    public void appliesProceduralCosmeticsOnlyToMatchingDomain() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##div:has-text(Sponsored)"));

        String script = new ProceduralCosmeticScriptBuilder().build(rules.getProceduralCosmeticRules(), "https://other.example");

        assertTrue(script.contains("MutationObserver"));
        assertTrue(!script.contains("Sponsored"));
    }

    @Test
    public void buildsStaticCosmeticCss() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##.ad-banner"));

        String script = new StaticCosmeticScriptBuilder().build(rules.getCosmeticRules(), "https://example.com");

        assertTrue(script.contains("document.createElement('style')"));
        assertTrue(script.contains(".ad-banner{display:none!important;}"));
    }

    @Test
    public void staticCosmeticExceptionSuppressesMatchingSelector() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##.ad-banner",
                "example.com#@#.ad-banner"));

        String script = new StaticCosmeticScriptBuilder().build(rules.getCosmeticRules(), "https://example.com");

        assertTrue(!script.contains(".ad-banner{display:none!important;}"));
    }

    @Test
    public void elemhideExceptionDisablesStaticCosmeticsForPage() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "##.generic-ad",
                "example.com##.specific-ad",
                "@@||example.com^$elemhide"));

        String script = new StaticCosmeticScriptBuilder().build(
                rules.getCosmeticRules(),
                "https://example.com/article",
                rules.getNetworkRules());

        assertTrue(!script.contains(".generic-ad{display:none!important;}"));
        assertTrue(!script.contains(".specific-ad{display:none!important;}"));
    }

    @Test
    public void generichideExceptionKeepsDomainSpecificStaticCosmetics() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "##.generic-ad",
                "example.com##.specific-ad",
                "@@||example.com^$generichide"));

        String script = new StaticCosmeticScriptBuilder().build(
                rules.getCosmeticRules(),
                "https://example.com/article",
                rules.getNetworkRules());

        assertTrue(!script.contains(".generic-ad{display:none!important;}"));
        assertTrue(script.contains(".specific-ad{display:none!important;}"));
    }

    @Test
    public void specifichideExceptionKeepsGenericStaticCosmetics() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "##.generic-ad",
                "example.com##.specific-ad",
                "@@||example.com^$specifichide"));

        String script = new StaticCosmeticScriptBuilder().build(
                rules.getCosmeticRules(),
                "https://example.com/article",
                rules.getNetworkRules());

        assertTrue(script.contains(".generic-ad{display:none!important;}"));
        assertTrue(!script.contains(".specific-ad{display:none!important;}"));
    }

    @Test
    public void buildsMatchesAttrProceduralCosmetic() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##div:matches-attr(data-ad=sponsored)"));

        String script = new ProceduralCosmeticScriptBuilder().build(rules.getProceduralCosmeticRules(), "https://example.com");

        assertTrue(script.contains("hideMatchesAttr"));
        assertTrue(script.contains("data-ad"));
        assertTrue(script.contains("sponsored"));
    }

    @Test
    public void buildsMatchesCssProceduralCosmetic() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##div:matches-css(display: block)"));

        String script = new ProceduralCosmeticScriptBuilder().build(rules.getProceduralCosmeticRules(), "https://example.com");

        assertTrue(script.contains("hideMatchesCss"));
        assertTrue(script.contains("display"));
        assertTrue(script.contains("block"));
    }

    @Test
    public void buildsXpathProceduralCosmetic() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##:xpath(//div[contains(text(),'Sponsored')])"));

        String script = new ProceduralCosmeticScriptBuilder().build(rules.getProceduralCosmeticRules(), "https://example.com");

        assertTrue(script.contains("document.evaluate"));
        assertTrue(script.contains("Sponsored"));
    }

    @Test
    public void elemhideExceptionDisablesProceduralCosmeticsForPage() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "##div:has-text(Generic)",
                "example.com##div:has-text(Specific)",
                "@@||example.com^$elemhide"));

        String script = new ProceduralCosmeticScriptBuilder().build(
                rules.getProceduralCosmeticRules(),
                "https://example.com/article",
                rules.getNetworkRules());

        assertTrue(!script.contains("Generic"));
        assertTrue(!script.contains("Specific"));
    }

    @Test
    public void generichideExceptionKeepsDomainSpecificProceduralCosmetics() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "##div:has-text(Generic)",
                "example.com##div:has-text(Specific)",
                "@@||example.com^$generichide"));

        String script = new ProceduralCosmeticScriptBuilder().build(
                rules.getProceduralCosmeticRules(),
                "https://example.com/article",
                rules.getNetworkRules());

        assertTrue(!script.contains("Generic"));
        assertTrue(script.contains("Specific"));
    }

    @Test
    public void specifichideExceptionKeepsGenericProceduralCosmetics() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "##div:has-text(Generic)",
                "example.com##div:has-text(Specific)",
                "@@||example.com^$specifichide"));

        String script = new ProceduralCosmeticScriptBuilder().build(
                rules.getProceduralCosmeticRules(),
                "https://example.com/article",
                rules.getNetworkRules());

        assertTrue(script.contains("Generic"));
        assertTrue(!script.contains("Specific"));
    }
}
