package com.hackathon.cprwatch.mobile

/**
 * Personal-dev secrets from `local.properties` → [BuildConfig] (debug only).
 * [BuildConfig.ANTHROPIC_API_KEY] is always empty for release builds.
 */
object DevApiKeys {
    fun anthropicApiKeyOrEmpty(): String = BuildConfig.ANTHROPIC_API_KEY

    /** From `ANTHROPIC_MODEL` in local.properties, or default `claude-sonnet-4-6`. */
    fun anthropicModelOrDefault(): String {
        val m = BuildConfig.ANTHROPIC_MODEL.trim()
        return m.ifEmpty { "claude-sonnet-4-6" }
    }
}
