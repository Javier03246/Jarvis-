package com.jarvez.assistant.verification

sealed class VerificationResult {
    data class Verified(val message: String) : VerificationResult()
    data class Unverified(val reason: String) : VerificationResult()
    object Skipped : VerificationResult()
}
