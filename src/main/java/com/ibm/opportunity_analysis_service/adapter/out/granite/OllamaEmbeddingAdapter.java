package com.ibm.opportunity_analysis_service.adapter.out.granite;

import com.ibm.opportunity_analysis_service.adapter.out.granite.dtoGranite.RespuestaEmbedding;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingPort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OllamaEmbeddingAdapter implements EmbeddingPort {

    private static final int MAX_REINTENTOS = 3;
    private static final long PAUSA_BASE_MS = 1000L;

    private final RestClient restClient;
    private final ConfiguracionGranite configuracionGranite;

    public OllamaEmbeddingAdapter(ConfiguracionGranite configuracionGranite) {
        this.configuracionGranite = configuracionGranite;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .baseUrl(configuracionGranite.getUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public List<Float> generarEmbedding(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El texto para generar embedding no puede ser nulo ni vacío");
        }

        Exception ultimoError = null;

        for (int intento = 1; intento <= MAX_REINTENTOS; intento++) {
            try {
                RespuestaEmbedding respuesta = restClient.post()
                        .uri("/api/embed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("model", configuracionGranite.getModelo(), "input", texto))
                        .retrieve()
                        .body(RespuestaEmbedding.class);

                if (respuesta == null || respuesta.getEmbeddings() == null || respuesta.getEmbeddings().isEmpty()) {
                    throw new IllegalStateException("Ollama no devolvió ningún embedding");
                }

                return respuesta.getEmbeddings().get(0);
            } catch (Exception e) {
                ultimoError = e;
                log.warn("Fallo al generar embedding con Ollama (intento {}/{}): {}",
                        intento, MAX_REINTENTOS, e.getMessage());

                if (intento < MAX_REINTENTOS) {
                    try {
                        Thread.sleep(PAUSA_BASE_MS * intento);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupción durante reintento de embedding", ie);
                    }
                }
            }
        }

        log.error("Agotados los {} reintentos para generar embedding con Ollama. Error final: {}",
                MAX_REINTENTOS, ultimoError != null ? ultimoError.getMessage() : "Desconocido");
        throw new IllegalStateException("No fue posible generar embedding tras " + MAX_REINTENTOS + " intentos: "
                + (ultimoError != null ? ultimoError.getMessage() : "Ollama no disponible"), ultimoError);
    }
}
