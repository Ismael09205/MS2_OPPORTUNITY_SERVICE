package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingCandidatoPort;
import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.RecomendacionPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.SimilitudPersistencePort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingCandidatoTecnologico;
import com.ibm.opportunity_analysis_service.domain.entity.EstadoRecomendacion;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.RecomendacionOportunidad;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoSimilitudIbm;
import com.ibm.opportunity_analysis_service.domain.entity.SimilitudProcesoProducto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AnalizarSimilitudService {

    private final ObtenerProcesosSercopService obtenerProcesosSercopService;
    private final EmbeddingCandidatoPort embeddingCandidatoPort;
    private final EmbeddingProductoIbmPersistencePort embeddingProductoIbmPersistencePort;
    private final SimilitudPersistencePort similitudPersistencePort;
    private final RecomendacionPersistencePort recomendacionPersistencePort;
    private final ValidadorAlcanceIbmService validadorAlcanceIbmService;
    private final ConfiguracionGranite configuracionGranite;

    // Monto de referencia para normalizar el bonus de monto (10M USD)
    private static final double MONTO_NORMALIZACION = 10_000_000.0;

    public AnalizarSimilitudService(
            ObtenerProcesosSercopService obtenerProcesosSercopService,
            EmbeddingCandidatoPort embeddingCandidatoPort,
            EmbeddingProductoIbmPersistencePort embeddingProductoIbmPersistencePort,
            SimilitudPersistencePort similitudPersistencePort,
            RecomendacionPersistencePort recomendacionPersistencePort,
            ValidadorAlcanceIbmService validadorAlcanceIbmService,
            ConfiguracionGranite configuracionGranite) {
        this.obtenerProcesosSercopService = obtenerProcesosSercopService;
        this.embeddingCandidatoPort = embeddingCandidatoPort;
        this.embeddingProductoIbmPersistencePort = embeddingProductoIbmPersistencePort;
        this.similitudPersistencePort = similitudPersistencePort;
        this.recomendacionPersistencePort = recomendacionPersistencePort;
        this.validadorAlcanceIbmService = validadorAlcanceIbmService;
        this.configuracionGranite = configuracionGranite;
    }

    public void analizar() {

        List<ProcesoSercop> procesos = obtenerProcesosSercopService.obtenerTodos();

        if (procesos.isEmpty()) {
            throw new IllegalStateException("No existen procesos SERCOP para analizar");
        }

        String modelo = configuracionGranite.getModelo();
        int procesados = 0;

        for (ProcesoSercop proceso : procesos) {

            // Validación de alcance y elegibilidad tecnológica IBM
            var validacion = validadorAlcanceIbmService.validar(proceso);
            if (!validacion.isEsElegible()) {
                log.debug("Proceso {} no elegible: {}", proceso.getOcid(), validacion.getMotivoDescarte());
                // Tarea 1.4 (H4): antes solo hacía `continue` y las similitudes huérfanas seguían
                // sosteniendo la preselección. Ahora se materializa el descarte: se borran las
                // similitudes y la recomendación pasa a DESCARTADA_ALCANCE con su motivo real.
                descartarSiHabiaRastro(proceso, validacion.getMotivoDescarte());
                continue;
            }

            // Ejecución incremental: skip si el contenido relevante no cambió
            String hashActual = calcularHash(proceso);
            Optional<String> hashGuardado = similitudPersistencePort.obtenerHashPorOcid(proceso.getOcid());
            if (hashGuardado.isPresent() && hashGuardado.get().equals(hashActual)) {
                continue;
            }

            Optional<EmbeddingCandidatoTecnologico> embedding =
                    embeddingCandidatoPort.buscarPorOcidYModelo(proceso.getOcid(), modelo);

            if (embedding.isEmpty()) {
                log.warn("Sin embedding disponible para proceso OCID: {}", proceso.getOcid());
                continue;
            }

            List<ResultadoSimilitudIbm> resultados =
                    embeddingProductoIbmPersistencePort.buscarSimilares(
                            embedding.get().getEmbedding(),
                            5
                    );

            double bonusMonto = calcularBonusMonto(proceso);
            double bonusMetodo = calcularBonusMetodo(proceso);
            double bonusAlcance = validacion.getBonusAlcance();

            for (ResultadoSimilitudIbm resultado : resultados) {
                double scoreCompuesto = calcularScoreCompuesto(
                        resultado.getSimilitud(), bonusMonto, bonusMetodo, bonusAlcance);

                SimilitudProcesoProducto similitud = new SimilitudProcesoProducto();
                similitud.setOcid(proceso.getOcid());
                similitud.setProductoIbmId(resultado.getProductoIbmId());
                similitud.setNombreProducto(resultado.getNombreProducto());
                similitud.setScoreVectorial(resultado.getSimilitud());
                similitud.setScoreCompuesto(scoreCompuesto);
                similitud.setHashContenido(hashActual);

                similitudPersistencePort.guardarOActualizar(similitud);
            }

            procesados++;
        }

        log.info("Similitudes persistidas para {} procesos.", procesados);
    }

    /**
     * Tarea 1.4 (H4): materializa el descarte de un proceso que dejó de ser elegible.
     * Borra sus similitudes huérfanas y, si tenía recomendación, la marca DESCARTADA_ALCANCE
     * con el motivo real del validador. Si no tenía ni similitudes ni recomendación, no hace nada
     * (no conviene crear filas de descarte para los ~300 procesos que nunca fueron candidatos).
     */
    private void descartarSiHabiaRastro(ProcesoSercop proceso, String motivo) {
        boolean teniaSimilitudes = !similitudPersistencePort.buscarPorOcid(proceso.getOcid()).isEmpty();
        boolean teniaRecomendacion = recomendacionPersistencePort.buscarPorOcid(proceso.getOcid()).isPresent();

        if (!teniaSimilitudes && !teniaRecomendacion) {
            return;
        }

        if (teniaSimilitudes) {
            int borradas = similitudPersistencePort.eliminarPorOcid(proceso.getOcid());
            log.info("Similitudes huérfanas eliminadas para OCID {} ({} filas): proceso no elegible.",
                    proceso.getOcid(), borradas);
        }

        RecomendacionOportunidad descartada = new RecomendacionOportunidad();
        descartada.setOcid(proceso.getOcid());
        descartada.setTitulo(proceso.getTitulo() != null ? proceso.getTitulo() : "Sin título");
        descartada.setDescripcion(proceso.getDescripcion() != null ? proceso.getDescripcion() : "");
        descartada.setComprador(proceso.getComprador() != null ? proceso.getComprador() : "Entidad no especificada");
        descartada.setMontoReferencial(proceso.getMonto() != null ? proceso.getMonto() : proceso.getPresupuesto());
        descartada.setEsOportunidadIbm(false);
        descartada.setProductoRecomendado(null);
        descartada.setNivelConfianza("N/A");
        descartada.setJustificacion(motivo);
        descartada.setEstrategiaComercial("Descartado: El requerimiento se encuentra fuera del alcance del portafolio tecnológico corporativo de IBM.");
        descartada.setEstado(EstadoRecomendacion.DESCARTADA_ALCANCE);
        descartada.setFechaEvaluacion(java.time.OffsetDateTime.now());
        recomendacionPersistencePort.guardarOActualizar(descartada, 0.0);
        log.info("Recomendación OCID {} marcada DESCARTADA_ALCANCE: {}", proceso.getOcid(), motivo);
    }

    /**
     * Normaliza el monto referencial del proceso a un bonus entre 0 y 0.15.
     * Un proceso de 10M USD o más obtiene el bonus máximo.
     */
    private double calcularBonusMonto(ProcesoSercop proceso) {
        BigDecimal monto = proceso.getMonto() != null ? proceso.getMonto() : proceso.getPresupuesto();
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        double ratio = monto.doubleValue() / MONTO_NORMALIZACION;
        return Math.min(ratio, 1.0) * 0.15;
    }

    /**
     * Bonus por método de contratación: licitación abierta > contrato directo.
     * Las licitaciones abiertas son más competitivas y relevantes para IBM.
     *
     * Tarea 1.10 (decisión del usuario 2026-10-22): la contratación `direct` <b>no es cortante</b>
     * —no descarta la oportunidad ni resta nivel de confianza—, solo baja su posición en el
     * ranking. Es coherente con el gold set: 7 de las 11 oportunidades reales son `direct`,
     * incluidas las 3 ALTO (U. Cuenca, UTN, UEMPRENDE).
     *
     * Medido 2026-10-21 sobre los 381 procesos: solo existen `open` (290) y `direct` (91), así
     * que la rama `default -> 0.05` jamás se ejecuta con este feed. La tabla configurable que
     * sustituya este switch es la tarea 2.2 (Fase 2); aquí no se mueven los valores.
     */
    private double calcularBonusMetodo(ProcesoSercop proceso) {
        if (proceso.getMetodo() == null) return 0.0;
        return switch (proceso.getMetodo().toLowerCase()) {
            case "open" -> 0.10;
            case "direct" -> 0.03;
            default -> 0.05;
        };
    }

    /**
     * Score compuesto = 70% similitud coseno + 10% bonus monto + 10% bonus método + 10% bonus alcance.
     */
    private double calcularScoreCompuesto(double scoreVectorial, double bonusMonto, double bonusMetodo, double bonusAlcance) {
        return (scoreVectorial * 0.70) + bonusMonto + bonusMetodo + bonusAlcance;
    }

    /**
     * SHA-256 de los campos que afectan la similitud vectorial y el score compuesto:
     * titulo, descripcion, tipoInterno, monto/presupuesto, metodo.
     * Si ninguno de estos cambió, el resultado sería idéntico → skip.
     */
    private String calcularHash(ProcesoSercop proceso) {
        BigDecimal monto = proceso.getMonto() != null ? proceso.getMonto() : proceso.getPresupuesto();
        String contenido = String.join("|",
                nvl(proceso.getTitulo()),
                nvl(proceso.getDescripcion()),
                nvl(proceso.getTipoInterno()),
                monto != null ? monto.toPlainString() : "",
                nvl(proceso.getMetodo())
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(contenido.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 siempre está disponible en la JVM estándar
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}
