package com.adguard.android.contentblocker.filtering.advanced;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RedirectResourceTest {

    @Test
    public void createsNoopJsonResource() {
        RedirectResource resource = RedirectResource.named("noopjson");

        assertEquals("application/json", resource.getMimeType());
        assertEquals("{}", body(resource));
    }

    @Test
    public void createsNoopFrameResource() {
        RedirectResource resource = RedirectResource.named("noopframe");

        assertEquals("text/html", resource.getMimeType());
        assertTrue(body(resource).contains("<!doctype html>"));
    }

    @Test
    public void createsVastAndVmapResources() {
        RedirectResource vast = RedirectResource.named("noopvast-3.0");
        RedirectResource vmap = RedirectResource.named("noopvmap-1.0");

        assertEquals("application/xml", vast.getMimeType());
        assertTrue(body(vast).contains("<VAST version=\"3.0\""));
        assertEquals("application/xml", vmap.getMimeType());
        assertTrue(body(vmap).contains("<VMAP"));
    }

    @Test
    public void createsCommonNeuteredScriptResources() {
        RedirectResource analytics = RedirectResource.named("google-analytics_analytics.js");
        RedirectResource gpt = RedirectResource.named("googletagservices_gpt.js");

        assertEquals("application/javascript", analytics.getMimeType());
        assertTrue(body(analytics).contains("window.ga"));
        assertEquals("application/javascript", gpt.getMimeType());
        assertTrue(body(gpt).contains("window.googletag"));
    }

    private static String body(RedirectResource resource) {
        return new String(resource.getBody(), StandardCharsets.UTF_8);
    }
}
