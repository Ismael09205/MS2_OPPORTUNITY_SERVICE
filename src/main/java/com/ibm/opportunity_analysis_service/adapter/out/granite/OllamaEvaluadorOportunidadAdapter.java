package com.ibm.opportunity_analysis_service.adapter.out.granite;

import com.ibm.opportunity_analysis_service.application.port.out.EvaluadorOportunidadLlmPort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;
import com.ibm.opportunity_analysis_service.domain.entity.RecomendacionOportunidad;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
public class OllamaEvaluadorOportunidadAdapter implements EvaluadorOportunidadLlmPort {

    private static final int MAX_CANDIDATOS_EVALUACION = 5;

    // Control de concurrencia para evitar saturar el modelo LLM en Ollama
    private final Semaphore semaforoLlm = new Semaphore(1, true);

    private final RestClient restClient;
    private final ConfiguracionGranite configuracionGranite;
    private final JsonMapper jsonMapper;

    public OllamaEvaluadorOportunidadAdapter(ConfiguracionGranite configuracionGranite, JsonMapper jsonMapper) {
        this.configuracionGranite = configuracionGranite;
        this.jsonMapper = jsonMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));

        this.restClient = RestClient.builder()
                .baseUrl(configuracionGranite.getUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public RecomendacionOportunidad evaluarOportunidad(
            ProcesoSercop proceso,
            List<ProductoIbm> candidatosTop,
            List<Double> scoresVectoriales) {

        String modelo = (configuracionGranite.getModeloLlm() != null && !configuracionGranite.getModeloLlm().isBlank())
                ? configuracionGranite.getModeloLlm()
                : "granite3.1-dense:8b";

        // Limitar a los mejores candidatos para optimizar el contexto y acelerar la respuesta de Granite
        List<ProductoIbm> candidatosAcotados = candidatosTop != null && candidatosTop.size() > MAX_CANDIDATOS_EVALUACION
                ? candidatosTop.subList(0, MAX_CANDIDATOS_EVALUACION)
                : (candidatosTop != null ? candidatosTop : List.of());

        List<Double> scoresAcotados = scoresVectoriales != null && scoresVectoriales.size() > MAX_CANDIDATOS_EVALUACION
                ? scoresVectoriales.subList(0, MAX_CANDIDATOS_EVALUACION)
                : scoresVectoriales;

        String prompt = construirPrompt(proceso, candidatosAcotados, scoresAcotados);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelo);
        requestBody.put("stream", false);
        requestBody.put("format", construirSchemaFormato());
        requestBody.put("options", Map.of(
                "temperature", 0.1,
                "num_predict", 350,   // Acota el tamaño de la respuesta para evitar tiempos excesivos
                "num_ctx", 4096
        ));
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "Eres un arquitecto de soluciones y líder comercial de IBM para compras públicas y licitaciones del SERCOP. Tu misión es evaluar con estricto rigor técnico y comercial si un proceso de contratación representa una oportunidad real y viable para el portafolio corporativo de IBM o sus Business Partners. Debes rechazar falsos positivos derivados de coincidencias léxicas genéricas (ej: si piden internet básico o correo y dice 'nube', no corresponde a IBM Cloud o watsonx)."),
                Map.of("role", "user", "content", prompt)
        ));

        boolean adquirioPermiso = false;
        try {
            // Adquirir permiso para no sobrecargar Ollama con múltiples llamadas en paralelo
            semaforoLlm.acquire();
            adquirioPermiso = true;

            long inicio = System.currentTimeMillis();
            Map<?, ?> respuesta = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            long duracion = System.currentTimeMillis() - inicio;
            log.debug("Evaluación Granite para OCID {} completada en {} ms", proceso.getOcid(), duracion);

            if (respuesta != null && respuesta.containsKey("message")) {
                Map<?, ?> message = (Map<?, ?>) respuesta.get("message");
                String content = (String) message.get("content");
                return parsearRespuesta(proceso, content);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Interrupción esperando turno para evaluar con Granite OCID: {}", proceso.getOcid());
        } catch (Exception e) {
            log.error("Error consultando LLM Ollama para OCID {}: {}", proceso.getOcid(), e.getMessage());
        } finally {
            if (adquirioPermiso) {
                semaforoLlm.release();
            }
        }

        // Fallback básico si el LLM no responde o da timeout
        return fallbackEvaluacion(proceso, candidatosAcotados, scoresAcotados);
    }

    private String construirPrompt(
            ProcesoSercop proceso,
            List<ProductoIbm> candidatos,
            List<Double> scoresVectoriales) {

        StringBuilder sb = new StringBuilder();
        sb.append("Analiza la siguiente contratación pública de SERCOP y los candidatos IBM preseleccionados.\n\n");
        sb.append("=== DATOS DE LA CONTRATACIÓN PÚBLICA (SERCOP) ===\n");
        sb.append("OCID: ").append(proceso.getOcid()).append("\n");
        sb.append("Título: ").append(proceso.getTitulo()).append("\n");

        String descripcion = proceso.getDescripcion() != null && !proceso.getDescripcion().isBlank()
                ? proceso.getDescripcion()
                : "Descripción no detallada en el pliego.";
        sb.append("Descripción: ").append(descripcion).append("\n");
        sb.append("Tipo/Categoría: ").append(proceso.getTipoInterno()).append("\n");
        sb.append("Comprador: ").append(proceso.getComprador() != null ? proceso.getComprador() : "No especificado").append("\n");
        sb.append("Método de Contratación: ").append(proceso.getMetodo() != null ? proceso.getMetodo() : "No especificado").append("\n");
        if (proceso.getMonto() != null || proceso.getPresupuesto() != null) {
            sb.append("Monto Referencial: ").append(proceso.getMonto() != null ? proceso.getMonto() : proceso.getPresupuesto()).append(" USD\n");
        }
        sb.append("\n");

        sb.append("=== CANDIDATOS IBM Y AFINIDAD VECTORIAL ===\n");
        double maxScore = 0.0;
        double minScore = 1.0;

        for (int i = 0; i < candidatos.size(); i++) {
            ProductoIbm p = candidatos.get(i);
            Double score = (scoresVectoriales != null && i < scoresVectoriales.size()) ? scoresVectoriales.get(i) : null;
            if (score != null) {
                maxScore = Math.max(maxScore, score);
                minScore = Math.min(minScore, score);
            }

            sb.append(i + 1).append(". Producto: ").append(p.getNombre());
            if (score != null) {
                sb.append(String.format(" (Similitud Vectorial: %.4f)", score));
            }
            sb.append("\n");
            sb.append("   Descripción: ").append(p.getDescripcion()).append("\n");
            if (p.getCapacidades() != null && !p.getCapacidades().isEmpty()) {
                sb.append("   Capacidades: ").append(String.join(", ", p.getCapacidades())).append("\n");
            }
            sb.append("\n");
        }

        double delta = (candidatos.size() > 1 && maxScore > 0) ? (maxScore - minScore) : 0.0;

        sb.append("=== DIAGNÓSTICO DE SEÑAL VECTORIAL ===\n");
        if (maxScore > 0) {
            sb.append(String.format("- Puntaje Máximo: %.4f | Dispersión (Delta Top1 - Top%d): %.4f\n", maxScore, candidatos.size(), delta));
            if (delta < 0.035 && maxScore < 0.72) {
                sb.append("- ALERTA DE RUIDO SEMÁNTICO: Los puntajes están muy apretados (< 0.035 de diferencia) y por debajo de 0.72. Esto indica coincidencia léxica/genérica sin un producto IBM dominante. NO califiques 'ALTO' a menos que el texto del pliego mencione explícitamente requerimientos unívocos.\n");
            }
        }
        if (proceso.getDescripcion() == null || proceso.getDescripcion().length() < 40) {
            sb.append("- ALERTA DE AMBIGÜEDAD: La descripción del proceso es muy corta o no detalla el alcance técnico.\n");
        }
        sb.append("\n");

        sb.append("""
        CRITERIOS DE ALCANCE Y CONTROL DE OPORTUNIDADES IBM:
        - ALCANCE VÁLIDO IBM:
          * Nube Híbrida y Plataforma: Red Hat OpenShift, modernización de aplicaciones legadas, nube privada/híbrida empresarial.
          * Datos e IA: watsonx, plataformas de analítica empresarial, Db2, gobierno y linaje de datos.
          * Ciberseguridad Enterprise: QRadar (SIEM/SOAR), Guardium (seguridad de datos), Verify (IAM), MaaS360.
          * Automatización y Observabilidad: Instana (APM), Turbonomic (ARM), IBM MQ, App Connect.
          * Gestión de Activos: IBM Maximo (EAM para activos físicos/infraestructura crítica).
          * Infraestructura Corporativa: Servidores Power, almacenamiento corporativo Storage, mainframes.

        - FUERA DE ALCANCE IBM (DESCARTAR INMEDIATAMENTE COMO es_oportunidad_ibm = false / N/A):
          * Enlaces de internet puro / ISP, cableado estructurado, fibra óptica física.
          * Adquisición de computadoras de escritorio, laptops genéricas de oficina, periféricos.
          * Suministro o arrendamiento de fotocopiadoras, impresoras o tóner.
          * Paquetes básicos de correo o licencias de usuario final estándar sin arquitectura empresarial.
          * Renovación monomarca exclusiva y cerrada de licencias de terceros sin alcance de migración o plataforma.

        REGLAS DE ASIGNACIÓN DE NIVEL DE CONFIANZA:
        1. ALTO:
           - Requiere: (a) El texto del pliego describe con claridad requerimientos técnicos que corresponden directamente a capacidades centrales de IBM, Y (b) La licitación es abierta y competitiva.
           - PROHIBIDO asignar ALTO si los puntajes vectoriales están planos (delta < 0.035) o si es un requerimiento genérico.

        2. MEDIO:
           - Casos válidos: (a) Licitaciones multi-rubro o mixtas donde IBM/Partners pueden ofertar componentes de software/plataforma/seguridad, (b) Proyectos de modernización o migración de infraestructura, o (c) Afinidad plausible pero pliego con descripción general.
           - En 'estrategia_comercial' explicar el abordaje (consorcio con business partners, capa de infraestructura/seguridad).

        3. BAJO:
           - Casos válidos: (a) Procesos con margen competitivo estrecho, (b) Especificación técnica sesgada a marcas de terceros donde solo cabría oferta alternativa, (c) Requerimiento muy preliminar.
           - En 'justificacion' señalar claramente por qué el encaje es marginal o de baja probabilidad.

        4. N/A (Descartado):
           - Procesos fuera del alcance tecnológico corporativo de IBM (commodities, insumos, internet doméstico, soporte micro).
           - Marca: es_oportunidad_ibm = false, producto_recomendado = null, nivel_confianza = "N/A".

        Responde estrictamente en formato JSON según el esquema especificado.
        """);

        return sb.toString();
    }

    private Map<String, Object> construirSchemaFormato() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "es_oportunidad_ibm", Map.of("type", "boolean"),
                        "producto_recomendado", Map.of("type", "string"),
                        "nivel_confianza", Map.of("type", "string", "enum", List.of("ALTO", "MEDIO", "BAJO", "N/A")),
                        "justificacion", Map.of("type", "string"),
                        "estrategia_comercial", Map.of("type", "string")
                ),
                "required", List.of("es_oportunidad_ibm", "justificacion")
        );
    }

    private RecomendacionOportunidad parsearRespuesta(ProcesoSercop proceso, String contentJson) {
        RecomendacionOportunidad rec = new RecomendacionOportunidad();
        rec.setOcid(proceso.getOcid());
        rec.setTitulo(proceso.getTitulo());
        rec.setDescripcion(proceso.getDescripcion());

        try {
            JsonNode root = jsonMapper.readTree(contentJson);
            rec.setEsOportunidadIbm(root.path("es_oportunidad_ibm").asBoolean(false));
            rec.setProductoRecomendado(root.path("producto_recomendado").isTextual() ? root.path("producto_recomendado").asText() : null);
            rec.setNivelConfianza(root.path("nivel_confianza").asText("N/A"));
            rec.setJustificacion(root.path("justificacion").asText(""));
            rec.setEstrategiaComercial(root.path("estrategia_comercial").asText(""));
        } catch (Exception e) {
            rec.setJustificacion(contentJson);
        }
        return rec;
    }

    private RecomendacionOportunidad fallbackEvaluacion(
            ProcesoSercop proceso,
            List<ProductoIbm> candidatosTop,
            List<Double> scoresVectoriales) {

        RecomendacionOportunidad rec = new RecomendacionOportunidad();
        rec.setOcid(proceso.getOcid());
        rec.setTitulo(proceso.getTitulo());
        rec.setDescripcion(proceso.getDescripcion());

        if (!candidatosTop.isEmpty()) {
            double topScore = (scoresVectoriales != null && !scoresVectoriales.isEmpty())
                    ? scoresVectoriales.get(0) : 0.65;

            rec.setEsOportunidadIbm(true);
            rec.setProductoRecomendado(candidatosTop.get(0).getNombre());
            rec.setNivelConfianza(topScore >= 0.75 ? "MEDIO" : "BAJO");
            rec.setJustificacion(String.format("Preselección técnica preliminar (Score: %.4f). Requiere validación de pliego.", topScore));
            rec.setEstrategiaComercial("Evaluar alcance de infraestructura y servicios para " + candidatosTop.get(0).getNombre());
        } else {
            rec.setEsOportunidadIbm(false);
            rec.setNivelConfianza("N/A");
            rec.setJustificacion("No se encontraron productos IBM con coincidencia semántica relevante.");
        }
        return rec;
    }
}
