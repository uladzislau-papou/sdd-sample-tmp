package com.example.service.guide.core.inport.usecase

import com.example.service.guide.core.inport.command.StartTourCommand
import com.example.service.guide.core.inport.result.StartTourResult

/**
 * Inbound port for UC05 — StartTour.
 *
 * The transaction boundary belongs to the driver. Framework-free.
 *
 * SDD: see `documentation/ports/start-tour.inport.spec.md`.
 */
interface StartTourUseCase {
    fun start(command: StartTourCommand): StartTourResult
}
