package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingCadidatoPort;
import com.ibm.opportunity_analysis_service.domain.entity.CandidatoTecnologico;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GenerarEmbeddingCandidatoService {

    private final EmbeddingCadidatoPort embeddingCadidatoPort;
    private final GenerarEmbeddingService generarEmbeddingService;

    public GenerarEmbeddingCandidatoService(EmbeddingCadidatoPort embeddingCadidatoPort, GenerarEmbeddingService generarEmbeddingService) {
        this.embeddingCadidatoPort = embeddingCadidatoPort;
        this.generarEmbeddingService = generarEmbeddingService;
    }

    public String generarTextoEmbedding(CandidatoTecnologico candidatoTecnologico) {
        return "Titulo: " + candidatoTecnologico.getTitulo()
                + "\nDescripcion: " + candidatoTecnologico.getDescripcion();
    }

    public void generarEmbeddings() {

        List<CandidatoTecnologico> candidatos =
                embeddingCadidatoPort.obtenerCandidatosTecnologicos();

        if (candidatos.isEmpty()) {
            throw new IllegalStateException("Lista vacia o no existen datos en la BD");
        }

        for (CandidatoTecnologico candidato : candidatos) {

            String textoEmbedding = generarTextoEmbedding(candidato);

            List<Float> valores = generarEmbeddingService.generar(textoEmbedding);

            float[] vector = convertirVector(valores);

            System.out.println("OCID: " + candidato.getOcid());
            System.out.println("Texto: " + textoEmbedding);
            System.out.println("Dimensiones: " + vector.length);
        }
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