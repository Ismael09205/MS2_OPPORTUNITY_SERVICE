package com.ibm.opportunity_analysis_service.adapter.out.granite;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import com.ibm.opportunity_analysis_service.adapter.out.granite.dtoGranite.MensajeOllama;
import com.ibm.opportunity_analysis_service.adapter.out.granite.dtoGranite.RespuestaOllama;
import com.ibm.opportunity_analysis_service.adapter.out.granite.dtoGranite.SolicitudOllama;
import com.ibm.opportunity_analysis_service.application.port.out.GranitePort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoAnalisisGranite;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GraniteLocalClient implements GranitePort {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ConfiguracionGranite configuracionGranite;

    public GraniteLocalClient(ObjectMapper objectMapper, ConfiguracionGranite configuracionGranite) {
        this.objectMapper = objectMapper;
        this.configuracionGranite = configuracionGranite;
        this.restClient = RestClient.builder()
                .baseUrl(configuracionGranite.getUrl())
                .build();
    }

    @Override
    public List<ResultadoAnalisisGranite> analizar(List<ProcesoSercop> procesos) {

        SolicitudOllama solicitud = construirSolicitud(procesos);

        RespuestaOllama respuesta = restClient.post()
                .uri("/api/chat")
                .body(solicitud)
                .retrieve()
                .body(RespuestaOllama.class);

        if (respuesta == null || respuesta.getMessage() == null || respuesta.getMessage().getContent() == null) {
            throw new IllegalStateException("Ollama no devolvió una respuesta válida.");
        }

        return convertirRespuesta(respuesta.getMessage().getContent());
    }

    private SolicitudOllama construirSolicitud(List<ProcesoSercop> procesos) {

        SolicitudOllama solicitud = new SolicitudOllama();

        solicitud.setModel(configuracionGranite.getModelo());
        solicitud.setStream(false);
        solicitud.setFormat(construirEsquemaRespuesta());
        solicitud.setOptions(Map.of(
                "think", false,
                "temperature", 0
        ));



        MensajeOllama mensaje = new MensajeOllama();
        mensaje.setRole("user");
        mensaje.setContent(construirPrompt(procesos));

        solicitud.setMessages(List.of(mensaje));

        return solicitud;
    }

    private String construirPrompt(List<ProcesoSercop> procesos) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
            Analiza estos procesos y determina cuáles representan una oportunidad
            de negocio potencialmente alineada con soluciones del catálogo IBM.

            Evalúa principalmente el objeto principal de contratación.

            APROBAR:
            - Desarrollo o adquisición de soluciones de software especializadas.
            - Implementación de plataformas o sistemas informáticos.
            - Servicios o infraestructura cloud.
            - Soluciones de inteligencia artificial.
            - Soluciones para procesamiento, integración o análisis de datos, y software especializado para automatización de procesos.
            - Soluciones de redes o telecomunicaciones relacionadas con capacidades IBM.

            RECHAZAR:
            - Software de uso general para usuarios, como Office o Windows.
            - Licencias utilizadas únicamente para el funcionamiento cotidiano de equipos.
            - Hardware general como computadores, laptops, impresoras o periféricos.
            - Tecnología que sea solamente un componente secundario o incidental.
            - Contrataciones cuyo objeto principal no sea una necesidad tecnológica
              alineable con IBM.

            No clasifiques únicamente por palabras clave. Interpreta el objeto principal.
            Analiza cada proceso de forma independiente.

            Categorías permitidas:
            software, cloud, inteligencia-artificial, redes.

            Si no corresponde, usa categoria: null.

            Devuelve exactamente un resultado por proceso.
            La justificacion debe tener máximo 10 palabras.
            No agregues texto fuera del JSON.

            Procesos:

            """);

        for (ProcesoSercop proceso : procesos) {
            prompt.append("OCID: ").append(valor(proceso.getOcid())).append("\n");
            prompt.append("Título: ").append(valor(proceso.getTitulo())).append("\n");
            prompt.append("Descripción: ").append(valor(proceso.getDescripcion())).append("\n");
            prompt.append("Tipo interno: ").append(valor(proceso.getTipoInterno())).append("\n");
            prompt.append("---\n");
        }

        return prompt.toString();
    }

    private Map<String, Object> construirEsquemaRespuesta() {

        Map<String, Object> esquema = new HashMap<>();

        esquema.put("type", "array");

        Map<String, Object> elemento = new HashMap<>();
        elemento.put("type", "object");

        Map<String, Object> propiedades = new HashMap<>();

        propiedades.put("ocid", Map.of(
                "type", "string"
        ));

        propiedades.put("aprobado", Map.of(
                "type", "boolean"
        ));

        propiedades.put("categoria", Map.of(
                "anyOf", List.of(
                        Map.of(
                                "type", "string",
                                "enum", List.of(
                                        "software",
                                        "cloud",
                                        "inteligencia-artificial",
                                        "infraestructura",
                                        "redes"
                                )
                        ),
                        Map.of(
                                "type", "null"
                        )
                )
        ));

        propiedades.put("justificacion", Map.of(
                "type", "string"
        ));

        elemento.put("properties", propiedades);

        elemento.put("required", List.of(
                "ocid",
                "aprobado",
                "categoria",
                "justificacion"
        ));

        esquema.put("items", elemento);

        return esquema;
    }

    private List<ResultadoAnalisisGranite> convertirRespuesta(String contenido) {

        try {
            JavaType tipoLista = objectMapper.getTypeFactory().constructCollectionType(List.class, ResultadoAnalisisGranite.class);

            return objectMapper.readValue(contenido, tipoLista);
        } catch (JacksonException e) {
            throw new IllegalStateException("Granite devolvió un JSON inválido: " + contenido, e);
        }
    }

    private String valor(String valor) {
        return valor == null ? "" : valor;
    }
}