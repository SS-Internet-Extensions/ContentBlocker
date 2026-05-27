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
}
