package com.adguard.android.contentblocker.filtering.advanced;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class TrackingParameterCleanerTest {

    @Test
    public void removesConfiguredTrackingParameter() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList("*$removeparam=utm_source"));
        TrackingParameterCleaner cleaner = new TrackingParameterCleaner(rules);

        assertEquals(
                "https://example.com/page?id=42",
                cleaner.clean("https://example.com/page?utm_source=newsletter&id=42"));
    }

    @Test
    public void keepsUrlWithoutQueryUnchanged() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList("*$removeparam=utm_source"));
        TrackingParameterCleaner cleaner = new TrackingParameterCleaner(rules);

        assertEquals(
                "https://example.com/page#section",
                cleaner.clean("https://example.com/page#section"));
    }

    @Test
    public void preservesPortFragmentAndEncodedValues() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList("*$removeparam=utm_source"));
        TrackingParameterCleaner cleaner = new TrackingParameterCleaner(rules);

        assertEquals(
                "https://example.com:8443/page?name=hello%20world#frag%20one",
                cleaner.clean("https://example.com:8443/page?name=hello%20world&utm_source=newsletter#frag%20one"));
    }

    @Test
    public void respectsRequestPatternContext() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||tracker.example^$removeparam=utm_source"));
        TrackingParameterCleaner cleaner = new TrackingParameterCleaner(rules);

        assertEquals(
                "https://tracker.example/page?id=42",
                cleaner.clean("https://tracker.example/page?utm_source=newsletter&id=42"));
        assertEquals(
                "https://cdn.example/page?utm_source=newsletter&id=42",
                cleaner.clean("https://cdn.example/page?utm_source=newsletter&id=42"));
    }

    @Test
    public void respectsPageDomainContext() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "*$removeparam=utm_source,domain=example.com"));
        TrackingParameterCleaner cleaner = new TrackingParameterCleaner(rules);

        assertEquals(
                "https://tracker.example/page?id=42",
                cleaner.clean("https://tracker.example/page?utm_source=newsletter&id=42", "https://example.com/article"));
        assertEquals(
                "https://tracker.example/page?utm_source=newsletter&id=42",
                cleaner.clean("https://tracker.example/page?utm_source=newsletter&id=42", "https://other.example/article"));
    }

    @Test
    public void supportsWildcardParameterNames() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList("*$removeparam=utm_*"));
        TrackingParameterCleaner cleaner = new TrackingParameterCleaner(rules);

        assertEquals(
                "https://example.com/page?id=42",
                cleaner.clean("https://example.com/page?utm_source=newsletter&utm_medium=email&id=42"));
    }

    @Test
    public void supportsRegexParameterNames() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList("*$removeparam=/^utm_/i"));
        TrackingParameterCleaner cleaner = new TrackingParameterCleaner(rules);

        assertEquals(
                "https://example.com/page?id=42",
                cleaner.clean("https://example.com/page?UTM_Source=newsletter&utm_medium=email&id=42"));
    }

    @Test
    public void removeparamExceptionKeepsMatchingParameter() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "*$removeparam=utm_source",
                "@@*$removeparam=utm_source,domain=example.com"));
        TrackingParameterCleaner cleaner = new TrackingParameterCleaner(rules);

        assertEquals(
                "https://example.com/page?utm_source=newsletter&id=42",
                cleaner.clean("https://example.com/page?utm_source=newsletter&id=42", "https://example.com/article"));
        assertEquals(
                "https://example.com/page?id=42",
                cleaner.clean("https://example.com/page?utm_source=newsletter&id=42", "https://other.example/article"));
    }

    @Test
    public void importantRemoveparamOverridesException() {
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "@@*$removeparam=utm_source,domain=example.com",
                "*$removeparam=utm_source,domain=example.com,important"));
        TrackingParameterCleaner cleaner = new TrackingParameterCleaner(rules);

        assertEquals(
                "https://example.com/page?id=42",
                cleaner.clean("https://example.com/page?utm_source=newsletter&id=42", "https://example.com/article"));
    }
}
