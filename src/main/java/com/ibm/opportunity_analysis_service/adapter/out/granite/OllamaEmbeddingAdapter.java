package com.ibm.opportunity_analysis_service.adapter.out.granite;

import com.ibm.opportunity_analysis_service.adapter.out.granite.dtoGranite.RespuestaEmbedding;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingPort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class OllamaEmbeddingAdapter implements EmbeddingPort {

    private final RestClient restClient;
    private final ConfiguracionGranite configuracionGranite;

    public OllamaEmbeddingAdapter(ConfiguracionGranite configuracionGranite) {
        this.configuracionGranite = configuracionGranite;
        this.restClient = RestClient.builder()
                .baseUrl(configuracionGranite.getUrl())
                .build();
    }

    @Override
    public List<Float> generarEmbedding(String texto) {
        RespuestaEmbedding respuesta = restClient.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", configuracionGranite.getModelo(), "input", texto))
                .retrieve()
                .body(RespuestaEmbedding.class);

        if (respuesta == null || respuesta.getEmbeddings() == null || respuesta.getEmbeddings().isEmpty()) {
            throw new IllegalStateException("Ollama no devolvio ningun embedding");
        }

        return respuesta.getEmbeddings().get(0);
    }
}
