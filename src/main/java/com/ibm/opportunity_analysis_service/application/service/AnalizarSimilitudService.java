package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingCadidatoPort;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.domain.entity.CandidatoTecnologico;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingCandidatoTecnologico;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoSimilitudIbm;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AnalizarSimilitudService {

    private final EmbeddingCadidatoPort embeddingCadidatoPort;
    private final EmbeddingProductoIbmPersistencePort embeddingProductoIbmPersistencePort;
    private final ConfiguracionGranite configuracionGranite;

    public AnalizarSimilitudService(EmbeddingCadidatoPort embeddingCadidatoPort, EmbeddingProductoIbmPersistencePort embeddingProductoIbmPersistencePort, ConfiguracionGranite configuracionGranite) {
        this.embeddingCadidatoPort = embeddingCadidatoPort;
        this.embeddingProductoIbmPersistencePort = embeddingProductoIbmPersistencePort;
        this.configuracionGranite = configuracionGranite;
    }

    public void analizar() {

        List<CandidatoTecnologico> candidatos =
                embeddingCadidatoPort.obtenerCandidatosTecnologicos();

        if (candidatos.isEmpty()) {
            throw new IllegalStateException("No existen candidatos tecnologicos");
        }

        String modelo = configuracionGranite.getModelo();

        for (CandidatoTecnologico candidato : candidatos) {

            Optional<EmbeddingCandidatoTecnologico> embedding =
                    embeddingCadidatoPort.buscarPorOcidYModelo(candidato.getOcid(), modelo);

            if (embedding.isEmpty()) {
                System.out.println("No existe embedding para: " + candidato.getOcid());
                continue;
            }

            List<ResultadoSimilitudIbm> resultados =
                    embeddingProductoIbmPersistencePort.buscarSimilares(
                            embedding.get().getEmbedding(),
                            5
                    );

            System.out.println();
            System.out.println("==============================================");
            System.out.println("OCID: " + candidato.getOcid());
            System.out.println("TITULO: " + candidato.getTitulo());
            System.out.println("DESCRIPCION: " + candidato.getDescripcion());
            System.out.println("==============================================");

            for (ResultadoSimilitudIbm resultado : resultados) {
                System.out.println(
                        "Producto IBM: " + resultado.getNombreProducto()
                                + " | ID: " + resultado.getProductoIbmId()
                                + " | Similitud: " + resultado.getSimilitud()
                );
            }
        }
    }
}