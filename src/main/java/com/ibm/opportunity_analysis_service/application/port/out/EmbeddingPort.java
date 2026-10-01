package com.ibm.opportunity_analysis_service.application.port.out;


import java.util.List;

public interface EmbeddingPort {
    List<Float> generarEmbedding(String texto);
}