package com.adguard.android.contentblocker.filtering.advanced;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AdvancedFilterLoggerTest {

    @Test
    public void keepsBoundedChronologicalSnapshot() {
        AdvancedFilterLogger logger = new AdvancedFilterLogger(2);

        logger.record(new AdvancedFilterEvent(AdvancedFilterEvent.Type.BLOCK, "https://one.example", "", "", "one", 1));
        logger.record(new AdvancedFilterEvent(AdvancedFilterEvent.Type.REDIRECT, "https://two.example", "", "", "two", 2));
        logger.record(new AdvancedFilterEvent(AdvancedFilterEvent.Type.REMOVEPARAM, "https://three.example", "", "", "three", 3));

        List<AdvancedFilterEvent> events = logger.snapshot();
        assertEquals(2, events.size());
        assertEquals("two", events.get(0).getDetail());
        assertEquals("three", events.get(1).getDetail());

        logger.clear();
        assertEquals(0, logger.snapshot().size());
    }

    @Test
    public void recordsEngineDecisions() {
        AdvancedFilterLogger logger = new AdvancedFilterLogger(8);
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "||ads.example^$script",
                "@@||ads.example^$script,domain=example.com",
                "||cdn.example/ads.js$script,redirect=noopjs"));
        AdvancedRuleEngine engine = new AdvancedRuleEngine(rules, logger);

        engine.evaluate(new RequestContext(
                "https://ads.example/ad.js",
                "https://other.example",
                RequestContext.TYPE_SCRIPT,
                false));
        engine.evaluate(new RequestContext(
                "https://cdn.example/ads.js",
                "https://example.com",
                RequestContext.TYPE_SCRIPT,
                false));
        engine.evaluate(new RequestContext(
                "https://ads.example/ad.js",
                "https://example.com",
                RequestContext.TYPE_SCRIPT,
                false));

        List<AdvancedFilterEvent> events = logger.snapshot();
        assertEquals(3, events.size());
        assertEquals(AdvancedFilterEvent.Type.BLOCK, events.get(0).getType());
        assertEquals(AdvancedFilterEvent.Type.REDIRECT, events.get(1).getType());
        assertEquals("noopjs", events.get(1).getDetail());
        assertEquals(AdvancedFilterEvent.Type.ALLOW_EXCEPTION, events.get(2).getType());
    }

    @Test
    public void recordsRemoveparamDecisions() {
        AdvancedFilterLogger logger = new AdvancedFilterLogger(8);
        AdvancedRuleSet rules = new AdvancedRuleCompiler().compile(Arrays.asList(
                "*$removeparam=utm_*",
                "@@*$removeparam=utm_source,domain=example.com"));
        TrackingParameterCleaner cleaner = new TrackingParameterCleaner(rules, logger);

        assertEquals(
                "https://example.com/page?utm_source=newsletter&id=42",
                cleaner.clean(
                        "https://example.com/page?utm_source=newsletter&utm_medium=email&id=42",
                        "https://example.com/article"));

        List<AdvancedFilterEvent> events = logger.snapshot();
        assertEquals(2, events.size());
        assertEquals(AdvancedFilterEvent.Type.ALLOW_EXCEPTION, events.get(0).getType());
        assertEquals("utm_source", events.get(0).getDetail());
        assertEquals(AdvancedFilterEvent.Type.REMOVEPARAM, events.get(1).getType());
        assertEquals("utm_medium", events.get(1).getDetail());
    }
}
