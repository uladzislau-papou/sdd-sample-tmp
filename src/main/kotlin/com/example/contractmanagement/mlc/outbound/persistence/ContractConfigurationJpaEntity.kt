package com.example.contractmanagement.mlc.outbound.persistence

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table

/**
 * Persistence row type for the `mlc_configuration` table — one version of a contract's terms.
 *
 * **[kuvJointLiability] keeps the source's name on purpose** (**PD-00**). The JCM data model
 * page calls the column `kuv_joint_liability`; `coding-style.definition.md` § 4.3 forbids
 * carrying a German abbreviation into an identifier, so the domain type is
 * `ContractConfiguration.groupJointLiability`. The column stays recognisable to whoever wrote
 * the data model and the domain stays English — which is exactly the freedom that having two
 * types rather than one shared type buys.
 *
 * [servicePackageOptions] is an `@ElementCollection` rather than a delimited column. A
 * delimiter would add an invariant — "no tier name contains a comma" — that is a persistence
 * concern leaking into the domain, and a split/join pair can be partially forgotten in a way a
 * join table cannot. `EAGER` because the aggregate is always loaded whole: the terms are part
 * of the contract, so a lazy collection would only add a proxy that every caller immediately
 * resolves.
 *
 * `LongParameterList` is suppressed for the reason it is on the other row types.
 *
 * SDD: see `documentation/ports/master-leasing-contract-repository.outport.spec.md`.
 */
@Suppress("LongParameterList")
@Entity
@Table(name = "mlc_configuration")
class ContractConfigurationJpaEntity(
    @Id
    @Column(name = "id", nullable = false, length = 36)
    var id: String = "",
    @Column(name = "version", nullable = false)
    var version: Int = 0,
    @Column(name = "credit_limit", nullable = false, precision = 19, scale = 4)
    var creditLimit: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    @Column(name = "contract_type", nullable = false)
    var contractType: String = "",
    @Column(name = "currency", nullable = false, length = 3)
    var currency: String = "",
    @Column(name = "eligible_employees", nullable = false)
    var eligibleEmployees: Int = 0,
    @Column(name = "sales_channel")
    var salesChannel: String? = null,
    @Column(name = "kuv_joint_liability", nullable = false)
    var kuvJointLiability: Boolean = false,
    @Column(name = "return_quota_percentage", precision = 5, scale = 2)
    var returnQuotaPercentage: java.math.BigDecimal? = null,
    @Column(name = "early_claim_fee_percentage", precision = 5, scale = 2)
    var earlyClaimFeePercentage: java.math.BigDecimal? = null,
    @Column(name = "early_claim_window_months")
    var earlyClaimWindowMonths: Int? = null,
    @Column(name = "notice_period_rule")
    var noticePeriodRule: String? = null,
    @Column(name = "payment_terms")
    var paymentTerms: String? = null,
    @Column(name = "price_range_min", precision = 19, scale = 4)
    var priceRangeMin: java.math.BigDecimal? = null,
    @Column(name = "price_range_max", precision = 19, scale = 4)
    var priceRangeMax: java.math.BigDecimal? = null,
    @Column(name = "calculation_basis")
    var calculationBasis: String? = null,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "mlc_configuration_service_package",
        joinColumns = [JoinColumn(name = "mlc_configuration_id")],
    )
    @Column(name = "service_package", nullable = false)
    var servicePackageOptions: MutableList<String> = mutableListOf(),
    @Column(name = "service_package_version")
    var servicePackageVersion: Int? = null,
    @Column(name = "categories_editable_in_portal", nullable = false)
    var categoriesEditableInPortal: Boolean = false,
)
