package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingCandidatoPort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingCandidatoTecnologico;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class GenerarEmbeddingCandidatoService {

    private final ObtenerProcesosSercopService obtenerProcesosSercopService;
    private final EmbeddingCandidatoPort embeddingCandidatoPort;
    private final GenerarEmbeddingService generarEmbeddingService;
    private final ConfiguracionGranite configuracionGranite;

    public GenerarEmbeddingCandidatoService(
            ObtenerProcesosSercopService obtenerProcesosSercopService,
            EmbeddingCandidatoPort embeddingCandidatoPort,
            GenerarEmbeddingService generarEmbeddingService,
            ConfiguracionGranite configuracionGranite) {
        this.obtenerProcesosSercopService = obtenerProcesosSercopService;
        this.embeddingCandidatoPort = embeddingCandidatoPort;
        this.generarEmbeddingService = generarEmbeddingService;
        this.configuracionGranite = configuracionGranite;
    }

    public EmbeddingCandidatoTecnologico generarOActualizarEmbedding(ProcesoSercop proceso) {
        String modelo = configuracionGranite.getModelo();
        String textoEmbedding = generarTextoEmbedding(proceso);

        Optional<EmbeddingCandidatoTecnologico> existente =
                embeddingCandidatoPort.buscarPorOcidYModelo(proceso.getOcid(), modelo);

        if (existente.isPresent()
                && textoEmbedding.equals(existente.get().getTextoEmbedding())
                && existente.get().getEmbedding() != null
                && existente.get().getEmbedding().length > 0) {
            return existente.get();
        }

        List<Float> valores = generarEmbeddingService.generar(textoEmbedding);
        float[] vector = convertirVector(valores);

        EmbeddingCandidatoTecnologico embedding =
                existente.orElseGet(EmbeddingCandidatoTecnologico::new);

        embedding.setOcid(proceso.getOcid());
        embedding.setModelo(modelo);
        embedding.setTextoEmbedding(textoEmbedding);
        embedding.setEmbedding(vector);
        embedding.setFechaGeneracion(OffsetDateTime.now());

        embeddingCandidatoPort.guardarEmbeddings(List.of(embedding));
        return embedding;
    }

    public void generarEmbeddings() {

        List<ProcesoSercop> procesos = obtenerProcesosSercopService.obtenerTodos();

        if (procesos.isEmpty()) {
            throw new IllegalStateException("Lista vacia o no existen datos en la BD de procesos SERCOP");
        }

        List<EmbeddingCandidatoTecnologico> embeddings = new ArrayList<>();

        String modelo = configuracionGranite.getModelo();

        for (ProcesoSercop proceso : procesos) {

            String textoEmbedding = generarTextoEmbedding(proceso);

            Optional<EmbeddingCandidatoTecnologico> existente =
                    embeddingCandidatoPort.buscarPorOcidYModelo(proceso.getOcid(), modelo);

            if (existente.isPresent()
                    && textoEmbedding.equals(existente.get().getTextoEmbedding())) {

                log.debug("Embedding ya existente, se omite: {}", proceso.getOcid());
                continue;
            }

            List<Float> valores = generarEmbeddingService.generar(textoEmbedding);

            float[] vector = convertirVector(valores);

            EmbeddingCandidatoTecnologico embedding =
                    existente.orElseGet(EmbeddingCandidatoTecnologico::new);

            embedding.setOcid(proceso.getOcid());
            embedding.setModelo(modelo);
            embedding.setTextoEmbedding(textoEmbedding);
            embedding.setEmbedding(vector);
            embedding.setFechaGeneracion(OffsetDateTime.now());

            embeddings.add(embedding);
        }
        if (!embeddings.isEmpty()) {
            embeddingCandidatoPort.guardarEmbeddings(embeddings);
        }
    }

    private String generarTextoEmbedding(ProcesoSercop proceso) {
        StringBuilder sb = new StringBuilder();
        if (proceso.getTitulo() != null && !proceso.getTitulo().isBlank()) {
            sb.append("Licitación: ").append(proceso.getTitulo()).append(". ");
        }
        if (proceso.getDescripcion() != null && !proceso.getDescripcion().isBlank()) {
            sb.append("Requerimiento: ").append(proceso.getDescripcion()).append(". ");
        }
        if (proceso.getTipoInterno() != null && !proceso.getTipoInterno().isBlank()) {
            sb.append("Categoria: ").append(proceso.getTipoInterno()).append(".");
        }

        return sb.toString().trim();
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