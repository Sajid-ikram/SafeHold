package dev.ikram.sandbox.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressInputTest {
    private fun search(query: String) = AddressInput.search(query)

    @Test
    fun webAddresses() {
        assertEquals("https://harbourlight.org/guides", AddressInput.resolve("harbourlight.org/guides"))
        assertEquals("https://example.com", AddressInput.resolve("  https://example.com  "))
        assertEquals("http://example.com/x", AddressInput.resolve("http://example.com/x"))
        assertEquals("https://localhost:8080/x", AddressInput.resolve("localhost:8080/x"))
        assertEquals("https://example.com:8443", AddressInput.resolve("example.com:8443"))
        assertEquals("https://192.168.1.1", AddressInput.resolve("192.168.1.1"))
    }

    @Test
    fun searches() {
        assertEquals(search("what is kotlin"), AddressInput.resolve("what is kotlin"))
        assertEquals(search("hello"), AddressInput.resolve("hello"))
        assertEquals(search("1.5"), AddressInput.resolve("1.5"))
        assertTrue(search("a b&c").endsWith("a+b%26c"))
    }

    @Test
    fun nonWebSchemesAreSearchedNeverLoaded() {
        listOf(
            "javascript:alert(1)",
            "file:///etc/passwd",
            "content://media/external",
            "intent://scan#Intent;end",
            "data:text/html,<script>x</script>",
            "about:blank",
        ).forEach { input -> assertEquals(input, search(input), AddressInput.resolve(input)) }
    }

    @Test
    fun blankInputDoesNothing() = assertNull(AddressInput.resolve("   "))

    @Test
    fun display() {
        assertEquals("example.org/guides", AddressInput.display("https://www.example.org/guides/"))
        assertEquals("example.org", AddressInput.display("https://example.org/?q=1"))
        assertEquals("http://plain.example/page", AddressInput.display("http://plain.example/page"))
        assertEquals("", AddressInput.display(null))
    }

    @Test
    fun security() {
        assertTrue(AddressInput.isSecure("https://example.org"))
        assertFalse(AddressInput.isSecure("http://example.org"))
        assertFalse(AddressInput.isSecure(null))
    }
}
