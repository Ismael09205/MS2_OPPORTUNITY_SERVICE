package com.ibm.opportunity_analysis_service.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProcesosScheduler {

    private final ProcesoSercopPipelineService pipelineService;
    private final RecomendarOportunidadesService recomendarOportunidadesService;

    @Value("${pipeline.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    public ProcesosScheduler(
            ProcesoSercopPipelineService pipelineService,
            RecomendarOportunidadesService recomendarOportunidadesService) {
        this.pipelineService = pipelineService;
        this.recomendarOportunidadesService = recomendarOportunidadesService;
    }

    /**
     * Tarea programada en segundo plano para verificar periódicamente si llegaron
     * nuevos procesos de contratación pública desde sercop_ingestion_service a la BD.
     */
    @Scheduled(
            fixedDelayString = "${pipeline.scheduler.delay-ms:60000}",
            initialDelayString = "${pipeline.scheduler.initial-delay-ms:15000}"
    )
    public void ejecutarSincronizacionPeriodica() {
        if (!schedulerEnabled) {
            return;
        }
        log.debug("Ejecutando verificación programada de procesos SERCOP...");
        try {
            pipelineService.sincronizarProcesos();
        } catch (Exception e) {
            log.error("Error en la ejecución programada de sincronización: {}", e.getMessage(), e);
        }
    }

    /**
     * Tarea programada en segundo plano para evaluar lotes pequeños de procesos pendientes con Granite
     * en horas valle o de forma continua sin bloquear las peticiones del usuario.
     */
    @Scheduled(
            fixedDelayString = "${pipeline.scheduler.evaluacion-delay-ms:300000}",
            initialDelayString = "${pipeline.scheduler.evaluacion-initial-delay-ms:60000}"
    )
    public void ejecutarPrecalculoPeriodico() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            int evaluadas = recomendarOportunidadesService.precalcularRecomendaciones(3);
            if (evaluadas > 0) {
                log.info("Evaluación periódica en segundo plano: {} procesos analizados con Granite.", evaluadas);
            }
        } catch (Exception e) {
            log.error("Error en la evaluación periódica en segundo plano con Granite: {}", e.getMessage());
        }
    }
}
