package com.jobradleasing.contractmanagement.masterleasing.inbound.graphql

import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * Minimal Spring Boot application for `@GraphQlTest` slices in this context.
 * `bootstrap` sits outside every bounded-context package, so `@GraphQlTest`
 * cannot find `ContractManagementApplication` by searching upwards
 * (test.definition.md § 1.3).
 */
@SpringBootApplication
class MasterLeasingGraphQlTestApplication
