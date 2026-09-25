package com.ibm.opportunity_analysis_service.adapter.out.granite;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import com.ibm.opportunity_analysis_service.adapter.out.granite.dtoGranite.RespuestaOllama;
import com.ibm.opportunity_analysis_service.adapter.out.granite.dtoGranite.SolicitudOllama;
import com.ibm.opportunity_analysis_service.application.port.out.GranitePort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoAnalisisGranite;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class GraniteLocalClient implements GranitePort {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SolicitudOllamaBuilder solicitudOllamaBuilder;

    public GraniteLocalClient(ObjectMapper objectMapper, ConfiguracionGranite configuracionGranite, SolicitudOllamaBuilder solicitudOllamaBuilder) {
        this.objectMapper = objectMapper;
        this.solicitudOllamaBuilder = solicitudOllamaBuilder;
        this.restClient = RestClient.builder()
                .baseUrl(configuracionGranite.getUrl())
                .build();
    }

    @Override
    public List<ResultadoAnalisisGranite> analizar(List<ProcesoSercop> procesos) {

        SolicitudOllama solicitud = solicitudOllamaBuilder.construir(procesos);

        RespuestaOllama respuesta = restClient.post()
                .uri("/api/chat")
                .body(solicitud)
                .retrieve()
                .body(RespuestaOllama.class);

        if (respuesta == null || respuesta.getMessage() == null || respuesta.getMessage().getContent() == null) {
            throw new IllegalStateException("Ollama no devolvió una respuesta válida.");
        }
        System.out.println("Procesos enviados: " + procesos.size());
        System.out.println("Tokens de entrada: " + respuesta.getPromptEvalEntrada());
        System.out.println("Tokens de salida: " + respuesta.getEvalTokensSalida());

        return convertirRespuesta(respuesta.getMessage().getContent());
    }

    private List<ResultadoAnalisisGranite> convertirRespuesta(String contenido) {

        try {
            JavaType tipoLista = objectMapper.getTypeFactory().constructCollectionType(List.class, ResultadoAnalisisGranite.class);

            return objectMapper.readValue(contenido, tipoLista);
        } catch (JacksonException e) {
            throw new IllegalStateException("Granite devolvió un JSON inválido: " + contenido, e);
        }
    }
}