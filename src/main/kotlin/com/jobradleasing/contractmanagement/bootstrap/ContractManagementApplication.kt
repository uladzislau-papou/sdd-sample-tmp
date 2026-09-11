package com.jobradleasing.contractmanagement.bootstrap

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication(scanBasePackages = ["com.jobradleasing.contractmanagement"])
class ContractManagementApplication

fun main(args: Array<String>) {
    SpringApplication.run(ContractManagementApplication::class.java, *args)
}
