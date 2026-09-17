package org.didban.monitor

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CertFingerprint] (JVM): the fingerprint format the TOFU
 * pin-capture flow stores, verified against a known self-signed certificate
 * (same fixture as [HttpClientPoolTest]; its fingerprint was computed
 * independently with `openssl x509 -fingerprint -sha256`).
 */
class CertFingerprintTest {

    private val EXPECTED_FP =
        "f57f843cc202cf23d8eb98740a9c7476f3e0e53325193bd35520cc12eacbf9b9"

    private val CERT_PEM =
"""-----BEGIN CERTIFICATE-----
MIIDFTCCAf2gAwIBAgIULuBpUQIK9u2VZ2dTxYXV+8ONtkAwDQYJKoZIhvcNAQEL
BQAwGjEYMBYGA1UEAwwPZGlkYmFuLWp2bS10ZXN0MB4XDTI2MDkxMzEwMjIwMVoX
DTM2MDkxMDEwMjIwMVowGjEYMBYGA1UEAwwPZGlkYmFuLWp2bS10ZXN0MIIBIjAN
BgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArSL9jdmiAnXvSoZ6yzg//FO8GB88
ptlije2XWr9A313CisOq6K7uom6d/ZbOkrrdYbAMAvFBeOis3ETLuDHXYEPG9FIE
XGrNqmqi54vTDyz0fuD+yWN5fBwxbDiGl95wFAEW0QeP1KhOo0Xza+uKNuECW6ps
fiOZPgyERChi3ll1aWJke+KVbvTTb9mVaJTRqjvtVln0ex2hxPloWSp46/oWWbaF
A0W59B1NYortGYuXD8Hw7XtKrjl/2ME9BAE+94vCL6qVuWsPJznN2j2fxy25P0yl
mMAe4r4sKEjlmXFWhOnZz84y3CRNbG3/mfFdoHMz6r6j7pTDsqMy2OnPaQIDAQAB
o1MwUTAdBgNVHQ4EFgQUwAVcGPLvrRKUfM4Oac5kLrK45M8wHwYDVR0jBBgwFoAU
wAVcGPLvrRKUfM4Oac5kLrK45M8wDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0B
AQsFAAOCAQEAHo5NqeoKjc9ZvIHk0c7IYcvYrhaLBbbv9qAxS9mG4FGmzTu12Vh6
MD1ZqBSgDaEHidEfBpxD4q+P0gqLsaTiqAcoNhPx3d9QtA9Uf28t42FiqEmz84ih
lfY9DSut41wpJHEdBIfO5HcUQy8TMVr5GP/qLqJ+at2xfTiT08rczs5d8p5QDoXl
k5uVLwF6JM8MSCdzp3qMF9IXAjN3E8qCBQWQXhdi32vgHDS3Atn+COIc/2YwORrN
Tn/eDTUE64VCrcSnVXYolnZhPWJDDrNrE7UYLxtrEWNQRGkqfzDnIbTDtKy/8Zkw
8O0c4EddqwS+o697/LWNDoS2gKBQPDd6yA==
-----END CERTIFICATE-----
"""

    private fun fixtureCert(): java.security.cert.X509Certificate =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(CERT_PEM.toByteArray()))
            as java.security.cert.X509Certificate

    @Test
    fun normalizedMatchesOpenSslKnownVector() {
        assertEquals(EXPECTED_FP, CertFingerprint.normalized(fixtureCert()))
    }

    @Test
    fun normalizedIsLowercaseHexWithoutSeparators() {
        val fp = CertFingerprint.normalized(fixtureCert())
        assertEquals(64, fp.length)
        assertEquals(fp.lowercase(), fp)
        assertFalse(fp.contains(":"))
        assertTrue(fp.all { it in ('0'..'9') || it in ('a'..'f') })
    }

    @Test
    fun normalizeFingerprintAcceptsUppercaseColonForm() {
        assertEquals(
            EXPECTED_FP,
            CertFingerprint.normalizeFingerprint(
                "F5:7F:84:3C:C2:02:CF:23:D8:EB:98:74:0A:9C:74:76:F3:E0:E5:33:25:19:3B:D3:55:20:CC:12:EA:CB:F9:B9"
            )
        )
    }

    @Test
    fun normalizeFingerprintTrimsAndDropsSpaces() {
        assertEquals(EXPECTED_FP, CertFingerprint.normalizeFingerprint(" $EXPECTED_FP "))
        val spaceSeparated = EXPECTED_FP.chunked(2).joinToString(" ")
        assertEquals(EXPECTED_FP, CertFingerprint.normalizeFingerprint(spaceSeparated))
    }

    @Test
    fun normalizeFingerprintEmptyStaysEmpty() {
        assertEquals("", CertFingerprint.normalizeFingerprint(""))
        assertEquals("", CertFingerprint.normalizeFingerprint("   "))
    }

    @Test
    fun normalizeFingerprintIdempotentOnStoredForm() {
        // the form the pin button stores is already normalized
        assertEquals(EXPECTED_FP, CertFingerprint.normalizeFingerprint(EXPECTED_FP))
    }

    @Test
    fun validationRequiresExactlySha256Hex() {
        assertTrue(CertFingerprint.isValidSha256(EXPECTED_FP))
        assertTrue(CertFingerprint.isValidSha256(EXPECTED_FP.chunked(2).joinToString(":")))
        assertFalse(CertFingerprint.isValidSha256(""))
        assertFalse(CertFingerprint.isValidSha256("ab"))
        assertFalse(CertFingerprint.isValidSha256("g".repeat(64)))
        assertFalse(CertFingerprint.isValidSha256("a".repeat(65)))
    }
}
