package com.duecare.journey.harness

/**
 * Data classes for the harness layers ported from the Python
 * `duecare.chat.harness` module. The Android app bundles the same
 * 37 GREP rules + 26 RAG docs + 4 tools so the on-device chat surface
 * has the same safety floor as the desktop / Kaggle / HF Spaces UIs.
 *
 * STATUS (skeleton): the actual rule + doc bodies live in the Python
 * package today and will be ported to Kotlin via a code-generation
 * step in v1 MVP (so a Python-side rule edit stays the source of
 * truth and propagates to Android automatically).
 */

data class GrepHit(
    val rule: String,
    val severity: String,
    val citation: String,
    val indicator: String,
    val matchExcerpt: String,
)

data class RagDoc(
    val id: String,
    val title: String,
    val source: String,
    val snippet: String,
)
