package com.adguard.android.contentblocker.filtering;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UboRuleCompilerTest {

    private final UboRuleCompiler compiler = new UboRuleCompiler();

    @Test
    public void compilesNetworkPatternsAndContextOptions() {
        assertEquals(
                Arrays.asList("||ads.example.com^$third-party,script"),
                compiler.compileAll(Arrays.asList("||ads.example.com^$third-party,script")));

        assertEquals(
                Arrays.asList("/adserver\\d+\\.js/$script,domain=example.com"),
                compiler.compileAll(Arrays.asList("/adserver\\d+\\.js/$script,domain=example.com")));

        assertEquals(
                Arrays.asList("*://*.tracker.example/*$image,domain=example.com|example.org"),
                compiler.compileAll(Arrays.asList("*://*.tracker.example/*$image,domain=example.com|example.org")));
    }

    @Test
    public void compilesCosmeticFilters() {
        assertEquals(
                Arrays.asList("example.com##.ad-banner"),
                compiler.compileAll(Arrays.asList("example.com##.ad-banner")));

        assertEquals(
                Arrays.asList("example.com#@#.allowed-ad"),
                compiler.compileAll(Arrays.asList("example.com#@#.allowed-ad")));
    }

    @Test
    public void convertsCommonScriptletAliases() {
        assertEquals(
                Arrays.asList("example.com#%#//scriptlet('set-constant', 'adBlockDetected', 'false')"),
                compiler.compileAll(Arrays.asList("example.com##+js(set, adBlockDetected, false)")));

        assertEquals(
                Arrays.asList("example.com#%#//scriptlet('abort-on-property-read', 'canRunAds')"),
                compiler.compileAll(Arrays.asList("example.com##+js(aopr, canRunAds)")));
    }

    @Test
    public void preservesRedirectPopupAndRemoveparamRules() {
        assertEquals(
                Arrays.asList("||cdn.example.com/ads.js$script,redirect=noopjs"),
                compiler.compileAll(Arrays.asList("||cdn.example.com/ads.js$script,redirect=noopjs")));

        assertEquals(
                Arrays.asList("||popup.example^$popup,domain=example.com"),
                compiler.compileAll(Arrays.asList("||popup.example^$popup,domain=example.com")));

        assertEquals(
                Arrays.asList("*$removeparam=utm_source"),
                compiler.compileAll(Arrays.asList("*$removeparam=utm_source")));
    }

    @Test
    public void preservesExistingAdguardOptionsAndCustomOptions() {
        assertEquals(
                Arrays.asList("||ceespc.biz^$empty,important"),
                compiler.compileAll(Arrays.asList("||ceespc.biz^$empty,important")));

        assertEquals(
                Arrays.asList("@@||photolessons.org^$generichide"),
                compiler.compileAll(Arrays.asList("@@||photolessons.org^$generichide")));

        assertEquals(
                Arrays.asList("||sports.qq.com^$csp=media-src https: http:"),
                compiler.compileAll(Arrays.asList("||sports.qq.com^$csp=media-src https: http:")));

        assertEquals(
                Arrays.asList("$cookie=has_adblock,domain=24smi.org"),
                compiler.compileAll(Arrays.asList("$cookie=has_adblock,domain=24smi.org")));

        assertEquals(
                Arrays.asList("||example.com^$custom-option=value"),
                compiler.compileAll(Arrays.asList("||example.com^$custom-option=value")));
    }

    @Test
    public void preservesRegexAnchorsAndAdguardScriptInjectionRules() {
        assertEquals(
                Arrays.asList("/adserver\\d+\\.js$/"),
                compiler.compileAll(Arrays.asList("/adserver\\d+\\.js$/")));

        assertEquals(
                Arrays.asList("/adserver\\d+\\.js$/$script,domain=example.com"),
                compiler.compileAll(Arrays.asList("/adserver\\d+\\.js$/$script,domain=example.com")));

        assertEquals(
                Arrays.asList("example.com#%#window.canRunAds = true;"),
                compiler.compileAll(Arrays.asList("example.com#%#window.canRunAds = true;")));
    }

    @Test
    public void commentsUnsupportedProceduralCosmeticFilters() {
        List<String> compiled = compiler.compileAll(Arrays.asList("example.com##:has-text(Sponsored)"));

        assertEquals(1, compiled.size());
        assertTrue(compiled.get(0).startsWith("! ubo-unsupported: procedural cosmetic filter: "));
    }

    @Test
    public void commentsUnsupportedScriptletsWithoutDroppingTheOriginalLine() {
        List<String> compiled = compiler.compileAll(Arrays.asList("example.com##+js(trusted-set-cookie, flag, 1)"));

        assertEquals(1, compiled.size());
        assertTrue(compiled.get(0).startsWith("! ubo-unsupported: scriptlet trusted-set-cookie: "));
        assertTrue(compiled.get(0).contains("example.com##+js(trusted-set-cookie, flag, 1)"));
    }

    @Test
    public void keepsOrderWhenCompilingMixedRuleLists() {
        List<String> compiled = compiler.compileAll(Arrays.asList(
                "! user rules",
                "||ads.example.com^",
                "example.com##+js(set, adBlockDetected, false)",
                "example.com##:has-text(Sponsored)",
                "*$removeparam=utm_campaign"));

        assertEquals("! user rules", compiled.get(0));
        assertEquals("||ads.example.com^", compiled.get(1));
        assertEquals("example.com#%#//scriptlet('set-constant', 'adBlockDetected', 'false')", compiled.get(2));
        assertTrue(compiled.get(3).startsWith("! ubo-unsupported: procedural cosmetic filter: "));
        assertEquals("*$removeparam=utm_campaign", compiled.get(4));
    }
}
