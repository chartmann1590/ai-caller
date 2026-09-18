package com.example.localcallagent.core.privacy

object NetworkPolicyEnforcer {
    /**
     * Checks if a network request target is permitted.
     * In the pstnControl flavor, isNetworkAllowed MUST be false (zero network traffic).
     * In the sipAgent flavor, only explicitly configured SIP registrars, proxies, STUN and TURN are allowed.
     */
    fun validateConnection(
        targetHost: String,
        isPstnFlavor: Boolean,
        configuredSipHosts: Set<String>
    ) {
        if (isPstnFlavor) {
            throw SecurityException("Privacy violation: Network access is strictly forbidden in pstnControl flavor")
        }

        val normalized = targetHost.trim().lowercase()
        val allowed = configuredSipHosts.map { it.trim().lowercase() }.toSet()

        if (!allowed.contains(normalized)) {
            throw SecurityException("Privacy violation: Connection to non-allowlisted host '$targetHost' is blocked")
        }
    }
}
