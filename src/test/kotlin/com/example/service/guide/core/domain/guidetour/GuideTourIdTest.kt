package com.example.service.guide.core.domain.guidetour

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class GuideTourIdTest {
    @Test
    fun construction_wrapsTheGivenValue() {
        val uuid = UUID.randomUUID()

        assertThat(GuideTourId(uuid).value).isEqualTo(uuid)
    }

    @Test
    fun generate_producesADistinctValue_onEachCall() {
        assertThat(GuideTourId.generate()).isNotEqualTo(GuideTourId.generate())
    }
}
