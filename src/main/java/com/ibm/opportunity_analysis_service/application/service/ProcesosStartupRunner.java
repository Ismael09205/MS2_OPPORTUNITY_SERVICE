package com.ibm.opportunity_analysis_service.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class ProcesosStartupRunner implements ApplicationRunner {

    private final CatalogoIbmBootstrapService catalogoIbmBootstrapService;
    private final ProcesoSercopPipelineService pipelineService;

    public ProcesosStartupRunner(
            CatalogoIbmBootstrapService catalogoIbmBootstrapService,
            ProcesoSercopPipelineService pipelineService) {
        this.catalogoIbmBootstrapService = catalogoIbmBootstrapService;
        this.pipelineService = pipelineService;
    }

    @Override
    public void run(ApplicationArguments args) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Verificación inicial al arranque: Catálogo IBM y Procesos SERCOP...");
                catalogoIbmBootstrapService.asegurarCatalogoIbmListo();
                pipelineService.sincronizarProcesos();
                log.info("Verificación inicial completada.");
            } catch (Exception e) {
                log.error("Error durante la verificación al arranque: {}", e.getMessage(), e);
            }
        });
    }
}
