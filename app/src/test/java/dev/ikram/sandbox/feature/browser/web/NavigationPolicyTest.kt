package dev.ikram.sandbox.feature.browser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {
    private fun main(url: String) = NavigationPolicy.decide(url, isMainFrame = true)

    @Test
    fun webPagesLoad() {
        assertEquals(NavigationDecision.Allow, main("https://example.org/page"))
        assertEquals(NavigationDecision.Allow, main("http://example.org/page"))
        assertEquals(NavigationDecision.Allow, main("HTTPS://EXAMPLE.ORG"))
    }

    @Test
    fun everythingElseIsBlockedInTheTopFrame() {
        listOf(
            "file:///sdcard/secret.txt",
            "content://com.android.contacts/contacts",
            "javascript:alert(1)",
            "data:text/html,hi",
            "blob:https://example.org/uuid",
            "market://details?id=com.evil",
            "tel:123",
            "mailto:a@example.org",
            "sms:123",
            "intent://scan#Intent;package=com.evil;end",
        ).forEach { assertEquals(it, NavigationDecision.Block, main(it)) }
    }

    @Test
    fun intentLinksUseTheirWebFallback() {
        val url = "intent://open#Intent;scheme=myapp;package=com.example;" +
            "S.browser_fallback_url=https%3A%2F%2Fexample.org%2Fget;end"
        assertEquals(NavigationDecision.Redirect("https://example.org/get"), main(url))
    }

    @Test
    fun intentFallbackMustBeAWebPage() {
        val url = "intent://x#Intent;S.browser_fallback_url=javascript%3Aalert(1);end"
        assertEquals(NavigationDecision.Block, main(url))
    }

    @Test
    fun subFramesMayNotReachLocalFiles() {
        assertEquals(NavigationDecision.Block, NavigationPolicy.decide("file:///etc/hosts", isMainFrame = false))
        assertEquals(NavigationDecision.Block, NavigationPolicy.decide("content://x/y", isMainFrame = false))
        assertEquals(NavigationDecision.Allow, NavigationPolicy.decide("about:blank", isMainFrame = false))
    }

    @Test
    fun localResources() {
        assertTrue(NavigationPolicy.isLocalResource("file:///android_asset/x.js"))
        assertTrue(NavigationPolicy.isLocalResource("CONTENT://x"))
        assertFalse(NavigationPolicy.isLocalResource("https://cdn.example/x.js"))
    }
}
