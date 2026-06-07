package com.duecare.journey.harness

/**
 * Public metadata for the harness bundle that ships inside the APK.
 *
 * Keep this object aligned with:
 * - app/src/main/assets/duecare_harness_manifest.json
 * - app/build.gradle.kts versionCode/versionName
 * - .github/workflows/build-apk.yml APK-manifest verification checks
 */
object HarnessInfo {
    const val APP_VERSION_NAME = "0.9.1-equivocation-rules"
    const val APP_VERSION_CODE = 11
    const val MANIFEST_ASSET = "duecare_harness_manifest.json"

    const val PROMPT_GREP_RULE_COUNT = 9
    const val PROMPT_RAG_DOC_COUNT = 4
    const val PROMPT_LOOKUP_FUNCTION_COUNT = 2
    const val DOMAIN_RISK_RULE_COUNT = 16
    const val ILO_INDICATOR_COUNT = 11
    const val CORRIDOR_PROFILE_COUNT = 20

    const val ANDROID_REPO =
        "github.com/TaylorAmarelTech/duecare-journey-android"
    const val PARENT_REPO =
        "github.com/TaylorAmarelTech/gemma4_comp"

    val promptHarnessSummary: String
        get() = "$PROMPT_GREP_RULE_COUNT GREP rules, " +
            "$PROMPT_RAG_DOC_COUNT RAG docs, " +
            "$PROMPT_LOOKUP_FUNCTION_COUNT lookup functions"

    val domainBundleSummary: String
        get() = "$DOMAIN_RISK_RULE_COUNT report risk rules, " +
            "$ILO_INDICATOR_COUNT ILO indicators, " +
            "$CORRIDOR_PROFILE_COUNT corridor profiles"
}
