package org.didban.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactorTest {
    @Test fun `redacts bearer and github tokens`() {
        val input = "Authorization: Bearer abcdefghijklmnop github_pat_ABC_def123"
        val safe = SecretRedactor.redact(input)
        assertFalse(safe.contains("abcdefghijklmnop"))
        assertFalse(safe.contains("github_pat_ABC_def123"))
        assertTrue(safe.contains("[REDACTED]"))
    }

    @Test fun `redacts telegram and discord URL credentials`() {
        val input = "https://api.telegram.org/bot123456:ABCDEF/sendMessage https://discord.com/api/webhooks/123456/secret-token"
        val safe = SecretRedactor.redact(input)
        assertFalse(safe.contains("123456:ABCDEF"))
        assertFalse(safe.contains("secret-token"))
    }

    @Test fun `redacts json credential fields and known values`() {
        val safe = SecretRedactor.redact("token=\"super-secret\" host secret-value", listOf("secret-value"))
        assertFalse(safe.contains("super-secret"))
        assertFalse(safe.contains("secret-value"))
    }

    @Test fun `redacts query command line and URI credentials`() {
        val input = "https://host/path?access_token=query-secret&ok=1 --password cli-secret https://user:uri-secret@host/path"
        val safe = SecretRedactor.redact(input)
        assertFalse(safe.contains("query-secret"))
        assertFalse(safe.contains("cli-secret"))
        assertFalse(safe.contains("uri-secret"))
        assertTrue(safe.contains("ok=1"))
    }

    @Test fun `does not redact ordinary diagnostics`() {
        val input = "Connection refused at server.example:8686"
        assertTrue(SecretRedactor.redact(input).contains("server.example:8686"))
    }
}
