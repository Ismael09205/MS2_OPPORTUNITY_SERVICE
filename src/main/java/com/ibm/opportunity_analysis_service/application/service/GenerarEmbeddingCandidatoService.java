package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingCadidatoPort;
import com.ibm.opportunity_analysis_service.domain.entity.CandidatoTecnologico;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingCandidatoTecnologico;

import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GenerarEmbeddingCandidatoService {

    private final EmbeddingCadidatoPort embeddingCadidatoPort;
    private final GenerarEmbeddingService generarEmbeddingService;
    private final ConfiguracionGranite configuracionGranite;

    public GenerarEmbeddingCandidatoService(EmbeddingCadidatoPort embeddingCadidatoPort, GenerarEmbeddingService generarEmbeddingService, ConfiguracionGranite configuracionGranite) {
        this.embeddingCadidatoPort = embeddingCadidatoPort;
        this.generarEmbeddingService = generarEmbeddingService;
        this.configuracionGranite = configuracionGranite;
    }

    public void generarEmbeddings() {

        List<CandidatoTecnologico> candidatos =
                embeddingCadidatoPort.obtenerCandidatosTecnologicos();

        if (candidatos.isEmpty()) {
            throw new IllegalStateException("Lista vacia o no existen datos en la BD");
        }

        List<EmbeddingCandidatoTecnologico> embeddings = new ArrayList<>();

        String modelo = configuracionGranite.getModelo();

        for (CandidatoTecnologico candidato : candidatos) {

            String textoEmbedding = generarTextoEmbedding(candidato);

            Optional<EmbeddingCandidatoTecnologico> existente =
                    embeddingCadidatoPort.buscarPorOcidYModelo(candidato.getOcid(), modelo);

            if (existente.isPresent()
                    && textoEmbedding.equals(existente.get().getTextoEmbedding())) {

                System.out.println("Embedding ya existente, se omite: " + candidato.getOcid());
                continue;
            }


            List<Float> valores = generarEmbeddingService.generar(textoEmbedding);

            float[] vector = convertirVector(valores);

            EmbeddingCandidatoTecnologico embedding =
                    existente.orElseGet(EmbeddingCandidatoTecnologico::new);

            embedding.setOcid(candidato.getOcid());
            embedding.setModelo(modelo);
            embedding.setTextoEmbedding(textoEmbedding);
            embedding.setEmbedding(vector);
            embedding.setFechaGeneracion(OffsetDateTime.now());

            embeddings.add(embedding);
        }
        if (!embeddings.isEmpty()) {
            embeddingCadidatoPort.guardarEmbeddings(embeddings);
        }
    }

    private String generarTextoEmbedding(CandidatoTecnologico candidatoTecnologico) {
        return candidatoTecnologico.getDescripcion();
    }

    private float[] convertirVector(List<Float> valores) {

        if (valores == null || valores.isEmpty()) {
            throw new IllegalStateException("Granite no devolvio valores para el embedding");
        }

        float[] vector = new float[valores.size()];

        for (int i = 0; i < valores.size(); i++) {
            vector[i] = valores.get(i);
        }

        return vector;
    }
}