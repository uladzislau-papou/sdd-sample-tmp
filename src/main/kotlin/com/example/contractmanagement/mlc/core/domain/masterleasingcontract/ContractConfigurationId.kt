package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import java.util.UUID

/**
 * Identity of a [ContractConfiguration] — the source's `mlc_config_id`.
 *
 * The configuration is an entity *inside* the [MasterLeasingContract] aggregate, not an
 * aggregate of its own, so this identity is never used to load anything. It exists because
 * the source defines `mlc_config_id` as "the current configuration version" and § 3 returns
 * it to the caller: a later terms version needs the previous one to be nameable.
 *
 * SDD: see `documentation/domain/aggregate-master-leasing-contract.spec.md`.
 */
data class ContractConfigurationId(
    val value: UUID,
) {
    companion object {
        fun of(value: UUID): ContractConfigurationId = ContractConfigurationId(value)

        fun generate(): ContractConfigurationId = ContractConfigurationId(UUID.randomUUID())
    }
}
