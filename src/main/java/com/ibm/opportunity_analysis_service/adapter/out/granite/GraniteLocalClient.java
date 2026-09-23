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

        MensajeOllama mensaje = new MensajeOllama();
        mensaje.setRole("user");
        mensaje.setContent(construirPrompt(procesos));

        solicitud.setMessages(List.of(mensaje));

        return solicitud;
    }

    private String construirPrompt(List<ProcesoSercop> procesos) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
                Analiza los siguientes procesos de contratación pública y determina cuáles representan
                una oportunidad tecnológica real.

                Tu criterio debe centrarse en el OBJETO PRINCIPAL de la contratación.

                Considera como oportunidad tecnológica cuando el objeto principal requiere directamente
                tecnología, software, infraestructura tecnológica, servicios cloud, inteligencia artificial,
                redes o telecomunicaciones.

                NO consideres una oportunidad tecnológica cuando la tecnología solamente sea utilizada
                por la institución, por los proveedores o como apoyo para realizar otra actividad.

                Ejemplos:

                - "Adquisición de servidores informáticos" → aprobado.
                - "Desarrollo de una aplicación web" → aprobado.
                - "Implementación de infraestructura cloud" → aprobado.
                - "Contratación de servidores públicos" → rechazado.
                - "Compra de escritorios para oficinas" → rechazado.
                - "Compra de lavavajillas" → rechazado.
                - "Mantenimiento del Parque Virgen de la Nube" → rechazado.

                No debes limitarte únicamente a buscar palabras clave. Debes interpretar el significado
                y el objeto principal de cada contratación.

                Para cada proceso devuelve:

                - ocid: identificador del proceso.
                - aprobado: true si el objeto principal representa una oportunidad tecnológica real,
                  false en caso contrario.
                - categoria: una de estas categorías: software, cloud, inteligencia-artificial,
                  infraestructura, redes. Si no corresponde, utiliza null.
                - justificacion: explicación breve de la decisión.

                Debes devolver exactamente un resultado por cada proceso recibido.

                Procesos a analizar:

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