package com.example.contractmanagement.mlc.inbound.graphql

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping

/**
 * Inbound GraphQL contract for master leasing contract operations.
 *
 * Holds the GraphQL surface — field mappings and argument binding — so the adapter
 * implementing it carries no GraphQL annotation, mirroring the REST split
 * (`coding-style.definition.md` § 3.3).
 *
 * This is **not** the inbound port. The inbound port is
 * `core.inport.usecase.CreateMasterLeasingContractUseCase`, and this is merely its first
 * adapter (`adr/0020`). The eventual trigger is employer onboarding (AGO), which will be a
 * second adapter on the same port — which is why the port was described from the domain's
 * needs and not from this mutation's shape.
 *
 * No `Query` field: `booking.graphqls` already declares the root the GraphQL specification
 * requires, and this service still has no read side. Adding a domain query here needs the
 * read-side ADR that `HANDOFF.md` § 7 lists as outstanding.
 *
 * SDD: see `documentation/use-cases/uc07-create-master-leasing-contract.spec.md` § 9.
 */
interface MasterLeasingContractGraphQLAPI {
    @MutationMapping
    fun createMasterLeasingContract(
        @Argument input: CreateMasterLeasingContractInput,
    ): CreateMasterLeasingContractPayload
}
