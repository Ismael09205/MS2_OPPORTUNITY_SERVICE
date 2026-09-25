package com.ibm.opportunity_analysis_service.adapter.out.granite;

import com.ibm.opportunity_analysis_service.adapter.out.granite.dtoGranite.MensajeOllama;
import com.ibm.opportunity_analysis_service.adapter.out.granite.dtoGranite.SolicitudOllama;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SolicitudOllamaBuilder {

    private final ConfiguracionGranite configuracionGranite;

    public SolicitudOllamaBuilder(ConfiguracionGranite configuracionGranite) {
        this.configuracionGranite = configuracionGranite;
    }

    public SolicitudOllama construir(List<ProcesoSercop> procesos) {

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
        Clasifica cada proceso según si representa una oportunidad tecnológica alineable con IBM.
                
        APROBAR:
        - Software especializado o sistemas informáticos.
        - Cloud.
        - Inteligencia artificial.
        - Redes/telecomunicaciones alineadas con IBM.
        - Datos, integración o automatización especializada.
                
        RECHAZAR:
        - Office, Windows y software general.
        - Hardware general.
        - Tecnología incidental.
        - Objetos no tecnológicos.
        - Necesidades no alineables con IBM.
                
        No uses palabras clave aisladamente. Evalúa el objeto principal.
                
        Categorías: software, cloud, inteligencia-artificial, redes.
        Si se rechaza: categoria=null.
        Un resultado por proceso.
        Justificación: máximo 10 palabras.
        Solo JSON.
                
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
                                        "redes"
                                )
                        ),
                        Map.of("type", "null"))
        ));

        propiedades.put("justificacion", Map.of("type", "string"));

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

    private String valor(String valor) {
        return valor == null ? "" : valor;
    }
}