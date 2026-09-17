package org.didban.monitor

/** Must stay aligned with the Agent's validateAuthToken policy. */
object AgentTokenPolicy {
    const val MIN_LENGTH = 32
    const val MAX_LENGTH = 256

    fun isValid(token: String): Boolean =
        token.length in MIN_LENGTH..MAX_LENGTH && token.all { it.code in 0x21..0x7e }
}
