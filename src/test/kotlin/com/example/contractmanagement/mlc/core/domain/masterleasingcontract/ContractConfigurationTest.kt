package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Domain tests for [ContractConfiguration] — the first version of a contract's terms
 * (`MLC_CONFIGURATION`).
 *
 * Two of `uc07-create-master-leasing-contract.spec.md` § 2.3's rules are enforced here rather
 * than in a value object, and the spec says why: `earlyClaimWindowMonths` and
 * `servicePackageVersion` are bounded `Int`s whose only invariant is a range, and a
 * one-field value object per bounded `Int` would add two types carrying no meaning beyond it.
 *
 * Both rules are **PD-10** — ours, not sourced.
 */
class ContractConfigurationTest {
    @Test
    fun create_throwsInvalidMasterLeasingContractException_whenEarlyClaimWindowMonthsNegative() {
        assertThatThrownBy { MlcTestData.configuration(earlyClaimWindowMonths = -1) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
            .hasMessageContaining("earlyClaimWindowMonths")
    }

    @Test
    fun create_throwsInvalidMasterLeasingContractException_whenServicePackageVersionBelowOne() {
        assertThatThrownBy { MlcTestData.configuration(servicePackageVersion = 0) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
            .hasMessageContaining("servicePackageVersion")
    }

    @Test
    fun create_acceptsAbsentOptionalTerms() {
        val configuration = MlcTestData.minimalConfiguration()

        assertThat(configuration.earlyClaimWindowMonths).isNull()
        assertThat(configuration.servicePackageVersion).isNull()
        assertThat(configuration.priceRange).isNull()
        assertThat(configuration.servicePackageOptions).isEmpty()
    }

    @Test
    fun create_defaultsBooleanTermsToFalse() {
        val configuration =
            MlcTestData.configuration(groupJointLiability = false, categoriesEditableInPortal = false)

        assertThat(configuration.groupJointLiability).isFalse()
        assertThat(configuration.categoriesEditableInPortal).isFalse()
    }

    @Test
    fun create_copiesServicePackageOptions_soALaterCallerCannotMutateThem() {
        val supplied = mutableListOf("basic")

        val configuration = MlcTestData.configuration(servicePackageOptions = supplied)
        supplied += "comfort"

        assertThat(configuration.servicePackageOptions).containsExactly("basic")
    }
}
