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
}
