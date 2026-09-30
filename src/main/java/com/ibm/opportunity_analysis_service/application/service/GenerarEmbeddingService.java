package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GenerarEmbeddingService {

    private final EmbeddingPort embeddingPort;

    public GenerarEmbeddingService(EmbeddingPort embeddingPort) {
        this.embeddingPort = embeddingPort;
    }

    public List<Float> generar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El texto para generar el embedding no puede estar vacio");
        }

        return embeddingPort.generarEmbedding(texto);
    }
}