package com.duecare.journey.journal

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A person or organization involved in the worker's journey:
 * recruitment agency, sub-agent, employer, sponsor, lender, NGO,
 * regulator, or the worker themselves.
 *
 * First-class so that "show me everything Pacific Coast Manpower
 * appears in" is a single foreign-key JOIN rather than a fuzzy
 * text search across journal bodies. Complaint packets can name
 * a Party with their POEA / BMET license number when one is on
 * file.
 *
 * Privacy: party contact info is encrypted at rest via the same
 * SQLCipher-backed DB.
 */
@Entity(tableName = "parties")
data class Party(
    @PrimaryKey val id: String,                  // UUID
    val kind: PartyKind,
    val name: String,
    val country: String? = null,                 // ISO-3166 alpha-2
    val licenseNumber: String? = null,           // POEA license, BMET RL, etc.
    val licenseStatus: LicenseStatus = LicenseStatus.UNKNOWN,
    val contactPhone: String? = null,            // encrypted at rest
    val contactEmail: String? = null,            // encrypted at rest
    val notes: String? = null,
)

enum class PartyKind {
    WORKER_SELF,
    RECRUITMENT_AGENCY,        // licensed Tier-1 (POEA-licensed PRA, etc.)
    SUB_AGENT,                  // sub-tier broker / village recruiter
    EMPLOYER,
    SPONSOR,                    // kafala-system sponsor
    LENDER,                     // money-lender, P2P loan platform
    AGENCY_REPRESENTATIVE,      // a named individual at an agency
    NGO,                        // POEA, MfMW HK, IJM, Polaris, etc.
    REGULATOR,                  // POEA Anti-Illegal Recruitment Branch, BMET
    EMBASSY,                    // PH Consulate HK, NP Embassy Doha, etc.
    UNKNOWN,
}

enum class LicenseStatus {
    UNKNOWN,
    LICENSED_VERIFIED,          // worker confirmed via POEA / BMET lookup
    LICENSED_CLAIMED,           // recruiter claims to be licensed; not verified
    UNLICENSED,                 // confirmed unlicensed
    REVOKED,                    // license revoked / expired
}
