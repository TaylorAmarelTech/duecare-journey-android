package com.duecare.journey.harness

/**
 * Data classes for the Android harness layers adapted from the Python
 * Duecare harness.
 *
 * Android v0.9 ships two explicit on-device surfaces:
 * - active advice prompt harness: 4 GREP rules, 4 RAG docs, 2 lookup functions
 * - deterministic domain intelligence: 16 report risk rules, 11 ILO indicators,
 *   20 corridor profiles
 *
 * The full desktop/web harness remains in the parent repository. Android v1
 * should expand/sync this subset through code generation so parent-side rule
 * edits stay the source of truth.
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
