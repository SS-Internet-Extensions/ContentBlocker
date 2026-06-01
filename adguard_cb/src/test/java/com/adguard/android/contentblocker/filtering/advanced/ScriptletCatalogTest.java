package com.adguard.android.contentblocker.filtering.advanced;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;

public class ScriptletCatalogTest {

    private final AdvancedRuleCompiler compiler = new AdvancedRuleCompiler();

    @Test
    public void buildsRemoveAttrScriptlet() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##+js(ra, data-ad, .ad)"));

        String script = new ScriptletScriptBuilder().build(rules.getScriptletRules(), "https://example.com");

        assertTrue(script.contains("removeAttribute"));
        assertTrue(script.contains("data-ad"));
        assertTrue(script.contains(".ad"));
    }

    @Test
    public void buildsRemoveClassScriptlet() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##+js(rc, sponsored, .item)"));

        String script = new ScriptletScriptBuilder().build(rules.getScriptletRules(), "https://example.com");

        assertTrue(script.contains("classList.remove"));
        assertTrue(script.contains("sponsored"));
        assertTrue(script.contains(".item"));
    }

    @Test
    public void buildsAbortCurrentInlineScriptScriptlet() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##+js(acis, document.createElement, adsbygoogle)"));

        String script = new ScriptletScriptBuilder().build(rules.getScriptletRules(), "https://example.com");

        assertTrue(script.contains("Object.defineProperty"));
        assertTrue(script.contains("document.createElement"));
        assertTrue(script.contains("adsbygoogle"));
    }

    @Test
    public void buildsNoEvalScriptlet() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##+js(noeval, tracker)"));

        String script = new ScriptletScriptBuilder().build(rules.getScriptletRules(), "https://example.com");

        assertTrue(script.contains("preventEval"));
        assertTrue(script.contains("window.eval"));
        assertTrue(script.contains("tracker"));
    }

    @Test
    public void buildsAddEventListenerDefuserAlias() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##+js(aeld, click, popup)"));

        String script = new ScriptletScriptBuilder().build(rules.getScriptletRules(), "https://example.com");

        assertTrue(script.contains("preventAddEventListener"));
        assertTrue(script.contains("EventTarget.prototype.addEventListener"));
        assertTrue(script.contains("click"));
        assertTrue(script.contains("popup"));
    }

    @Test
    public void buildsTimerDefuserAliases() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##+js(nostif, adblock, 1000)",
                "example.com##+js(nosiif, refreshAd, 5000)"));

        String script = new ScriptletScriptBuilder().build(rules.getScriptletRules(), "https://example.com");

        assertTrue(script.contains("preventTimer"));
        assertTrue(script.contains("setTimeout"));
        assertTrue(script.contains("setInterval"));
        assertTrue(script.contains("adblock"));
        assertTrue(script.contains("refreshAd"));
    }

    @Test
    public void scriptletExceptionSuppressesMatchingScriptlet() {
        AdvancedRuleSet rules = compiler.compile(Arrays.asList(
                "example.com##+js(ra, data-ad, .ad)",
                "example.com#@#+js(ra, data-ad, .ad)"));

        String script = new ScriptletScriptBuilder().build(rules.getScriptletRules(), "https://example.com");

        assertTrue(!script.contains("data-ad"));
    }
}
