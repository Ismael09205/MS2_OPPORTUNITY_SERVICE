package com.ibm.opportunity_analysis_service.adapter.out.granite;

import com.ibm.opportunity_analysis_service.application.port.out.EvaluadorOportunidadLlmPort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.application.util.FirmaInfoRevision;
import com.ibm.opportunity_analysis_service.domain.entity.EstadoRecomendacion;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;
import com.ibm.opportunity_analysis_service.domain.entity.RecomendacionOportunidad;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Evaluador LLM con la CONFIGURACIÓN OFICIAL medida en la corrida 6 (experimentos:
 * 82.6% de acierto exacto, 0 falsos positivos, reproducible 46/46 con seed 42):
 *
 *  1. Prompt C4 EXACTO (system + bloque de proceso idénticos a fewshot_eval_v6.py, incluidos
 *     el diagnóstico de señal vectorial y la alerta de ambigüedad).
 *  2. Few-shot de los 11 ejemplos del gold set v2 embebidos como recurso estático
 *     (src/main/resources/prompts/ejemplos_fewshot_c4.json, generado por
 *     experimentos/generar_recurso_fewshot.py) — decisión del usuario 2026-10-23.
 *  3. Salida JSON de 3 vías: {"decision": "SI"|"NO"|"REVISAR", "nivel": "ALTO"|"MEDIO"|"NA",
 *     "producto_recomendado", "justificacion"} con las reglas de coherencia del prompt C4.
 *  4. Opciones: temperature 0.1, num_predict 350, num_ctx 16384 y SEED 42 (la semilla fija es
 *     lo que hizo el experimento reproducible: sin ella ~12% de casos cambian entre corridas).
 *
 * Mapeo de estados (plan REVISAR aprobado 2026-10-23):
 *  - SI (con nivel ALTO/MEDIO) → EVALUADA, esOportunidadIbm=true.
 *  - NO → DESCARTADA_ALCANCE (decisión del usuario: el descarte del LLM es alcance).
 *  - REVISAR → REQUIERE_REVISION con la causa del modelo en justificacion y la firma de
 *    información para la Opción C (reintento solo si cambia el proceso).
 *  - SI sin nivel válido / JSON ilegible / timeout → ERROR_EVALUACION (no definitivo, reintentable).
 *
 * El filtro de reglas duras (R1-R5) NO vive aquí: se ejecuta antes, en
 * {@code RecomendarOportunidadesService}, evitando la llamada al modelo.
 */
@Slf4j
@Component
public class OllamaEvaluadorOportunidadAdapter implements EvaluadorOportunidadLlmPort {

    /** Top-3 de candidatos, idéntico al split medido de la corrida 6 (buscarSimilares(..., 3)). */
    private static final int MAX_CANDIDATOS_EVALUACION = 3;

    /** Prefijo del mensaje de usuario por caso, idéntico al del script de la corrida 6. */
    private static final String PREFIJO_CASO = "Evalúa el siguiente proceso SERCOP.\n\n";

    /** Recurso few-shot estático (11 ejemplos en el orden medido: ALTO, MEDIO, REVISAR, NO). */
    private static final String RECURSO_FEW_SHOT = "prompts/ejemplos_fewshot_c4.json";

    /**
     * System prompt C4 EXACTO (fewshot_eval_v6.py::SYSTEM_PROMPT). No editar sin re-medir:
     * el 82.6% de la corrida 6 se midió con este texto.
     */
    static final String SYSTEM_PROMPT_C4 = """
            Eres un arquitecto de soluciones y líder comercial de IBM para compras públicas y licitaciones del SERCOP. Tu misión es evaluar con estricto rigor técnico y comercial si un proceso de contratación representa una oportunidad real y viable para el portafolio corporativo de IBM o sus Business Partners. Debes rechazar falsos positivos derivados de coincidencias léxicas genéricas (ej: si piden internet básico o correo y dice 'nube', no corresponde a IBM Cloud o watsonx).

            CRITERIOS DE ALCANCE Y CONTROL DE OPORTUNIDADES IBM:
            - ALCANCE VÁLIDO IBM:
              * Nube Híbrida y Plataforma: Red Hat OpenShift, modernización de aplicaciones legadas, nube privada/híbrida empresarial.
              * Datos e IA: watsonx, plataformas de analítica empresarial, Db2, gobierno y linaje de datos.
              * Ciberseguridad Enterprise: QRadar (SIEM/SOAR), Guardium (seguridad de datos), Verify (IAM), MaaS360.
              * Automatización y Observabilidad: Instana (APM), Turbonomic (ARM), IBM MQ, App Connect.
              * Gestión de Activos: IBM Maximo (EAM para activos físicos/infraestructura crítica).
              * Infraestructura Corporativa: Servidores Power, almacenamiento corporativo Storage, mainframes.

            - FUERA DE ALCANCE IBM (decision NO):
              * Enlaces de internet puro / ISP, cableado estructurado, fibra óptica física.
              * Computadoras, laptops, periféricos, fotocopiadoras, impresoras o tóner.
              * Paquetes básicos de correo o licencias de usuario final estándar sin arquitectura empresarial.
              * Ofimática y productividad de usuario (commodity), incluidas renovaciones grandes.
              * Renovación monomarca exclusiva y cerrada de licencias de terceros sin alcance de migración o plataforma.
              * CAD/BIM y software de diseño de terceros sin equivalente IBM.
              * Contenido propietario de editores/base de datos de terceros (SciELO, WoS, Scopus, Wolfram, etc.).
              * Software vertical de un solo giro (educativo, tránsito, bibliotecas, GIS) sin componente de plataforma IBM.
              * Soporte, garantía, mantenimiento o actualización de una plataforma existente.
              * Consultoría, auditoría, digitalización, desarrollo a medida, operación de servicios o capacitación: si el objeto central es el SERVICIO PROFESIONAL, la decisión es NO aunque en el texto se citen productos IBM como referencia. El objeto central manda.
              * Contenido propietario de terceros claramente identificable (bases de datos de citas/revistas, geodatos, documentación técnica de fabricante, catálogos editoriales): NO seco, nunca REVISAR.

            REGLAS DE NIVEL:
            - ALTO: requerimientos técnicos del pliego que corresponden directamente a capacidades centrales de IBM Y licitación abierta y competitiva.
            - MEDIO: alcance mixto donde IBM/Partners pueden ofertar parte (nube, datos, seguridad, plataforma) aunque otra parte sea de terceros; modernización/migración; afinidad plausible con descripción general.

            REGLAS DE ABSTENCIÓN — REVISAR es EXCEPCIONAL, úsalo solo cuando:
              a) Alcance mixto real y no discernible: el objeto central es parcialmente IBM y parcialmente de terceros, y el hecho faltante cambiaría la decisión.
              b) Renovación de nube/servicio sin indicar de qué proveedor se renueva (podría ser monomarca ajena).
              c) Seguridad perimetral/hardware sin indicar la marca del producto (podría ser appliance de competidor).
              d) Un proveedor ya instalado podría vetar la oportunidad Y el pliego no revela quién es.
              NO uses REVISAR por descripción corta o vaga si el objeto central ya se identifica: si cae en una categoría de descarte, descarta (NO); si encaja de lleno en IBM, decide.
              REVISAR NO es un descarte: es "con esta información no puedo decidir responsablemente". Si la respuesta cae claramente en ALTO/MEDIO o en NO, decídete.

            Responde estrictamente en formato JSON: {"decision": "SI"|"NO"|"REVISAR", "nivel": "ALTO"|"MEDIO"|"NA", "producto_recomendado": string|null, "justificacion": string}

            Reglas de coherencia: decision=SI exige nivel ALTO o MEDIO — NUNCA respondas SI con nivel NA; si es oportunidad pero no decides entre ALTO y MEDIO, elige MEDIO. decision=NO o REVISAR exige nivel="NA". producto_recomendado solo si decision=SI.""";

    /** Un ejemplo few-shot (prompt y respuesta JSON correcta, en el formato medido). */
    record EjemploFewShot(String prompt, JsonNode respuesta) {
    }

    // Control de concurrencia para evitar saturar el modelo LLM en Ollama
    private final Semaphore semaforoLlm = new Semaphore(1, true);

    private final RestClient restClient;
    private final ConfiguracionGranite configuracionGranite;
    private final JsonMapper jsonMapper;
    private final List<EjemploFewShot> ejemplosFewShot;

    public OllamaEvaluadorOportunidadAdapter(ConfiguracionGranite configuracionGranite, JsonMapper jsonMapper) {
        this.configuracionGranite = configuracionGranite;
        this.jsonMapper = jsonMapper;
        this.ejemplosFewShot = cargarEjemplosFewShot();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        // 180 s (antes 90): el contexto oficial es mucho mayor (system + 11 ejemplos ~6k tokens).
        requestFactory.setReadTimeout(Duration.ofSeconds(180));

        this.restClient = RestClient.builder()
                .baseUrl(configuracionGranite.getUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Carga los 11 ejemplos few-shot del recurso estático generado por
     * {@code experimentos/generar_recurso_fewshot.py} a partir del gold set v2 congelado.
     * Si el recurso falta o está malformado se evalúa sin ejemplos (el sistema sigue siendo
     * correcto, pero deja de reproducir la configuración medida) y se avisa por log.
     */
    private List<EjemploFewShot> cargarEjemplosFewShot() {
        try (InputStream in = new ClassPathResource(RECURSO_FEW_SHOT).getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode arr = jsonMapper.readTree(json);
            List<EjemploFewShot> ejemplos = new ArrayList<>();
            for (JsonNode nodo : arr) {
                ejemplos.add(new EjemploFewShot(nodo.path("prompt").asText(""), nodo.path("respuesta")));
            }
            log.info("Few-shot C4 cargado: {} ejemplos desde {}", ejemplos.size(), RECURSO_FEW_SHOT);
            return List.copyOf(ejemplos);
        } catch (Exception e) {
            log.warn("No se pudo cargar el few-shot C4 ({}): se evaluará sin ejemplos. Causa: {}",
                    RECURSO_FEW_SHOT, e.getMessage());
            return List.of();
        }
    }

    @Override
    public RecomendacionOportunidad evaluarOportunidad(
            ProcesoSercop proceso,
            List<ProductoIbm> candidatosTop,
            List<Double> scoresVectoriales) {

        String modelo = (configuracionGranite.getModeloLlm() != null && !configuracionGranite.getModeloLlm().isBlank())
                ? configuracionGranite.getModeloLlm()
                : "granite3.1-dense:8b";

        // Top-3, como en la corrida 6
        List<ProductoIbm> candidatosAcotados = candidatosTop != null && candidatosTop.size() > MAX_CANDIDATOS_EVALUACION
                ? candidatosTop.subList(0, MAX_CANDIDATOS_EVALUACION)
                : (candidatosTop != null ? candidatosTop : List.of());

        List<Double> scoresAcotados = scoresVectoriales != null && scoresVectoriales.size() > MAX_CANDIDATOS_EVALUACION
                ? scoresVectoriales.subList(0, MAX_CANDIDATOS_EVALUACION)
                : scoresVectoriales;

        // Secuencia de mensajes idéntica a la corrida 6: system C4 + 11 ejemplos
        // (user/assistant) + el caso actual.
        List<Map<String, String>> mensajes = new ArrayList<>();
        mensajes.add(Map.of("role", "system", "content", SYSTEM_PROMPT_C4));
        for (EjemploFewShot ejemplo : ejemplosFewShot) {
            mensajes.add(Map.of("role", "user", "content", PREFIJO_CASO + ejemplo.prompt()));
            mensajes.add(Map.of("role", "assistant", "content", ejemplo.respuesta().toString()));
        }
        mensajes.add(Map.of("role", "user", "content", PREFIJO_CASO + construirPromptProceso(proceso, candidatosAcotados, scoresAcotados)));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelo);
        requestBody.put("stream", false);
        requestBody.put("format", construirSchemaFormato());
        requestBody.put("options", Map.of(
                "temperature", 0.1,
                "num_predict", 350,
                "num_ctx", 16384,
                "seed", 42        // semilla fija: condición de reproducibilidad medida en la corrida 6
        ));
        requestBody.put("messages", mensajes);

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

        // Fallback si el LLM no responde o da timeout: NUNCA se fabrica un veredicto.
        // Tarea 1.5 (H3): el adapter reporta ERROR_EVALUACION para que el servicio persista
        // esOportunidadIbm=false con estado de error, reintentable, en vez de un falso positivo.
        return fallbackEvaluacion(proceso, candidatosAcotados, scoresAcotados);
    }

    /**
     * Bloque de datos del proceso, réplica de {@code construir_prompt_proceso} de la corrida 6
     * (mismas etiquetas, mismas alertas, mismo orden y el mismo cierre de frase).
     */
    private String construirPromptProceso(
            ProcesoSercop proceso,
            List<ProductoIbm> candidatos,
            List<Double> scoresVectoriales) {

        StringBuilder sb = new StringBuilder();
        sb.append("=== DATOS DE LA CONTRATACIÓN PÚBLICA (SERCOP) ===\n");
        sb.append("OCID: ").append(proceso.getOcid()).append("\n");
        sb.append("Título: ").append(proceso.getTitulo()).append("\n");

        String descripcion = proceso.getDescripcion() != null && !proceso.getDescripcion().isBlank()
                ? proceso.getDescripcion()
                : "Descripción no detallada en el pliego.";
        sb.append("Descripción: ").append(descripcion).append("\n");
        sb.append("Tipo/Categoría: ")
                .append(proceso.getTipoInterno() != null && !proceso.getTipoInterno().isBlank()
                        ? proceso.getTipoInterno() : "No especificado")
                .append("\n");
        sb.append("Comprador: ").append(proceso.getComprador() != null ? proceso.getComprador() : "No especificado").append("\n");
        sb.append("Método de Contratación: ").append(proceso.getMetodo() != null ? proceso.getMetodo() : "No especificado").append("\n");
        java.math.BigDecimal monto = proceso.getMonto() != null ? proceso.getMonto() : proceso.getPresupuesto();
        if (monto != null) {
            sb.append("Monto Referencial: ").append(monto).append(" USD\n");
        }
        sb.append("\n");

        double maxScore = 0.0;
        double minScore = 1.0;
        if (!candidatos.isEmpty()) {
            sb.append("=== CANDIDATOS IBM Y AFINIDAD VECTORIAL ===\n");
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
                if (p.getDescripcion() != null && !p.getDescripcion().isBlank()) {
                    sb.append("   Descripción: ").append(p.getDescripcion()).append("\n");
                }
                if (p.getCapacidades() != null && !p.getCapacidades().isEmpty()) {
                    sb.append("   Capacidades: ").append(String.join(", ", p.getCapacidades())).append("\n");
                }
                sb.append("\n");
            }

            double delta = (candidatos.size() > 1 && maxScore > 0) ? (maxScore - minScore) : 0.0;
            sb.append("=== DIAGNÓSTICO DE SEÑAL VECTORIAL ===\n");
            sb.append(String.format("- Puntaje Máximo: %.4f | Dispersión (Delta Top1 - Top%d): %.4f\n",
                    maxScore, candidatos.size(), delta));
            if (delta < 0.035 && maxScore < 0.72) {
                sb.append("- ALERTA DE RUIDO SEMÁNTICO: Los puntajes están muy apretados (< 0.035 de diferencia) y por "
                        + "debajo de 0.72. NO califiques 'ALTO' a menos que el pliego mencione requerimientos unívocos.\n");
            }
            sb.append("\n");
        }

        if (proceso.getDescripcion() == null || proceso.getDescripcion().length() < 40) {
            sb.append("- ALERTA DE AMBIGÜEDAD: La descripción del proceso es muy corta o no detalla el alcance técnico.\n\n");
        }

        sb.append("Analiza esta contratación pública y responde con el JSON del veredicto.");
        return sb.toString();
    }

    /** Esquema JSON de 3 vías de la corrida 6 (decision/nivel/producto_recomendado/justificacion). */
    private Map<String, Object> construirSchemaFormato() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "decision", Map.of("type", "string", "enum", List.of("SI", "NO", "REVISAR")),
                        "nivel", Map.of("type", "string", "enum", List.of("ALTO", "MEDIO", "NA")),
                        "producto_recomendado", Map.of("type", "string"),
                        "justificacion", Map.of("type", "string")
                ),
                "required", List.of("decision", "justificacion")
        );
    }

    /** Visible para test: los ejemplos few-shot cargados del recurso estático. */
    List<EjemploFewShot> ejemplosFewShot() {
        return ejemplosFewShot;
    }

    /**
     * Mapea la respuesta 3 vías del modelo al estado correspondiente:
     * SI→EVALUADA · NO→DESCARTADA_ALCANCE · REVISAR→REQUIERE_REVISION(con causa y firma, Opción C).
     * "SI" sin nivel ALTO/MEDIO viola las reglas de coherencia del prompt C4 (fallo medido en C3):
     * se trata como respuesta ilegible (ERROR_EVALUACION), no como veredicto.
     * Visible para test (misma paquete): el mapeo de estados es el contrato de la Fase REVISAR.
     */
    RecomendacionOportunidad parsearRespuesta(ProcesoSercop proceso, String contentJson) {
        RecomendacionOportunidad rec = new RecomendacionOportunidad();
        rec.setOcid(proceso.getOcid());
        rec.setTitulo(proceso.getTitulo());
        rec.setDescripcion(proceso.getDescripcion());

        try {
            JsonNode root = jsonMapper.readTree(contentJson);
            String decision = root.path("decision").asText("").trim().toUpperCase(Locale.ROOT);
            String justificacion = root.path("justificacion").asText("");
            String producto = root.path("producto_recomendado").isTextual()
                    ? root.path("producto_recomendado").asText()
                    : null;

            switch (decision) {
                case "SI" -> {
                    String nivel = root.path("nivel").asText("").trim().toUpperCase(Locale.ROOT);
                    if (!"ALTO".equals(nivel) && !"MEDIO".equals(nivel)) {
                        log.warn("Respuesta SI sin nivel ALTO/MEDIO para OCID {} (violación de coherencia C4): tratada como ilegible.",
                                proceso.getOcid());
                        return fallbackEvaluacion(proceso, List.of(), List.of());
                    }
                    rec.setEsOportunidadIbm(true);
                    rec.setNivelConfianza(nivel);
                    rec.setProductoRecomendado(producto);
                    rec.setJustificacion(justificacion);
                    rec.setEstrategiaComercial(null);
                    rec.setEstado(EstadoRecomendacion.EVALUADA);
                    rec.setFechaEvaluacion(OffsetDateTime.now());
                }
                case "NO" -> {
                    // Decisión del usuario 2026-10-23: el NO del LLM se persiste como DESCARTADA_ALCANCE.
                    rec.setEsOportunidadIbm(false);
                    rec.setNivelConfianza("N/A");
                    rec.setProductoRecomendado(null);
                    rec.setJustificacion(justificacion);
                    rec.setEstrategiaComercial(null);
                    rec.setEstado(EstadoRecomendacion.DESCARTADA_ALCANCE);
                    rec.setFechaEvaluacion(OffsetDateTime.now());
                }
                case "REVISAR" -> {
                    // Cola humana: la causa del modelo es la justificación visible; la firma habilita
                    // el reintento automático solo si cambia la información (Opción C).
                    rec.setEsOportunidadIbm(false);
                    rec.setNivelConfianza("N/A");
                    rec.setProductoRecomendado(null);
                    rec.setJustificacion(justificacion);
                    rec.setEstrategiaComercial("Pendiente de revisión humana (cola de Bob).");
                    rec.setEstado(EstadoRecomendacion.REQUIERE_REVISION);
                    rec.setFirmaInfoRevision(FirmaInfoRevision.calcular(proceso));
                    rec.setFechaEvaluacion(OffsetDateTime.now());
                }
                default -> {
                    log.warn("Respuesta del LLM con decisión desconocida '{}' para OCID {}: tratada como ilegible.",
                            decision, proceso.getOcid());
                    return fallbackEvaluacion(proceso, List.of(), List.of());
                }
            }
        } catch (Exception e) {
            // Respuesta ilegible: es un error de evaluación, no un veredicto (tarea 1.5).
            log.warn("Respuesta ilegible del LLM para OCID {}: {}", proceso.getOcid(), e.getMessage());
            return fallbackEvaluacion(proceso, List.of(), List.of());
        }
        return rec;
    }

    /**
     * Tarea 1.5 (H3): cuando el LLM no responde, da timeout o devuelve JSON ilegible, el sistema
     * NO fabrica una oportunidad. Devuelve un estado de error explícito con esOportunidadIbm=false,
     * que el servicio persiste como ERROR_EVALUACION (no definitivo, permite reintento).
     */
    private RecomendacionOportunidad fallbackEvaluacion(
            ProcesoSercop proceso,
            List<ProductoIbm> candidatosTop,
            List<Double> scoresVectoriales) {

        RecomendacionOportunidad rec = new RecomendacionOportunidad();
        rec.setOcid(proceso.getOcid());
        rec.setTitulo(proceso.getTitulo());
        rec.setDescripcion(proceso.getDescripcion());
        rec.setEsOportunidadIbm(false);
        rec.setProductoRecomendado(null);
        rec.setNivelConfianza("N/A");
        rec.setJustificacion("Evaluación no disponible: el LLM no respondió o devolvió una respuesta ilegible."
                + " No es un descarte definitivo; se reintentará."
                + (candidatosTop.isEmpty() ? "" : " Candidatos vectoriales disponibles: " + candidatosTop.size() + "."));
        rec.setEstrategiaComercial("Evaluación pendiente de reintento.");
        rec.setEstado(EstadoRecomendacion.ERROR_EVALUACION);
        return rec;
    }
}
