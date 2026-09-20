package org.didban.monitor

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Keep HTTP status distinct from transport failures; never show untrusted response bodies. */
internal fun describeAgentToolFailure(failure: Throwable, copy: CommandCopy, server: ServerConfig, bandwidth: Boolean): String {
    // A rotated agent certificate carries both pins: explain it in the
    // user's language instead of surfacing a raw TLS alert.
    (failure as? FingerprintMismatchException)?.let { mismatch ->
        return copy.connFingerprintMismatch
            .replace("%1", mismatch.expected.take(12))
            .replace("%2", mismatch.observed.take(12))
    }
    val status = (failure as? ApiException)?.statusCode
    return when {
        status == 404 || status == 405 -> if (bandwidth) copy.bandwidthUnsupported else copy.dockerUnsupported
        status == 401 || status == 403 -> "HTTP $status: ${copy.agentToolAuthFailed}"
        status == 413 -> "HTTP 413: ${copy.agentToolPayloadRejected}"
        failure is SocketTimeoutException -> copy.agentToolTimeout
        failure is UnknownHostException -> copy.agentToolDnsFailed
        failure is SSLException -> copy.agentToolTlsFailed
        failure is ConnectException -> copy.agentToolUnreachable
        else -> SecretRedactor.redact(
            failure.message?.takeIf { it.isNotBlank() } ?: "${copy.networkError} (${failure.javaClass.simpleName})",
            listOf(server.token, server.adminToken)
        ).take(300)
    }
}
