package com.ibm.opportunity_analysis_service.adapter.out.persistence.adapter;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.RecomendacionEntity;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.RecomendacionJpaRepository;
import com.ibm.opportunity_analysis_service.application.port.out.RecomendacionPersistencePort;
import com.ibm.opportunity_analysis_service.domain.entity.EstadoRecomendacion;
import com.ibm.opportunity_analysis_service.domain.entity.RecomendacionOportunidad;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class RecomendacionPersistenceAdapter implements RecomendacionPersistencePort {

    private final RecomendacionJpaRepository repository;

    public RecomendacionPersistenceAdapter(RecomendacionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public RecomendacionOportunidad guardarOActualizar(RecomendacionOportunidad recomendacion, Double scoreCompuesto) {
        RecomendacionEntity entity = repository.findByOcid(recomendacion.getOcid())
                .orElse(new RecomendacionEntity());

        entity.setOcid(recomendacion.getOcid());
        entity.setTitulo(recomendacion.getTitulo());
        entity.setDescripcion(recomendacion.getDescripcion());
        entity.setComprador(recomendacion.getComprador());
        entity.setMontoReferencial(recomendacion.getMontoReferencial());
        entity.setEsOportunidadIbm(recomendacion.isEsOportunidadIbm());
        entity.setProductoRecomendado(recomendacion.getProductoRecomendado());
        entity.setNivelConfianza(recomendacion.getNivelConfianza());
        entity.setJustificacion(recomendacion.getJustificacion());
        entity.setEstrategiaComercial(recomendacion.getEstrategiaComercial());
        // null = conservar el score existente (resolución humana de la cola REQUIERE_REVISION).
        entity.setScoreCompuesto(scoreCompuesto != null
                ? scoreCompuesto
                : (entity.getScoreCompuesto() != null ? entity.getScoreCompuesto() : 0.0));
        entity.setFechaGeneracion(OffsetDateTime.now());
        entity.setEstado(recomendacion.getEstado() != null ? recomendacion.getEstado().name() : null);
        entity.setFechaEvaluacion(recomendacion.getFechaEvaluacion());
        entity.setFirmaInfoRevision(recomendacion.getFirmaInfoRevision());
        // Política de reintento (2026-10-24): null en dominio = 0 (fila nueva o veredicto limpio).
        entity.setIntentosReintento(recomendacion.getIntentosReintento());
        entity.setProximoIntentoAt(recomendacion.getProximoIntentoAt());

        normalizarConsistencia(entity);

        repository.save(entity);
        return toDomain(entity);
    }

    /**
     * Tarea 1.9 (H14): ninguna fila puede quedar como oportunidad sin una confianza que la
     * sustente. Hoy existía al menos una fila {@code esOportunidadIbm=true} con
     * {@code nivelConfianza='N/A'}, que las reglas del prompt definen como descarte.
     *
     * Ante esa combinación inválida se persiste como {@code ERROR_EVALUACION} con
     * {@code esOportunidadIbm=false}: no es un descarte definitivo (permite reevaluación) pero
     * tampoco puede publicarse como oportunidad. El mismo invariante lo refuerza el CHECK
     * {@code chk_recomendacion_confianza} de la migración DDL.
     */
    private void normalizarConsistencia(RecomendacionEntity entity) {
        boolean confianzaNoAcredita = entity.getNivelConfianza() == null
                || entity.getNivelConfianza().isBlank()
                || "N/A".equalsIgnoreCase(entity.getNivelConfianza().trim());

        if (entity.isEsOportunidadIbm() && confianzaNoAcredita) {
            log.warn("Inconsistencia detectada para OCID {}: esOportunidadIbm=true con confianza '{}'. "
                            + "Se persiste como ERROR_EVALUACION sin publicar como oportunidad (H14).",
                    entity.getOcid(), entity.getNivelConfianza());
            entity.setEsOportunidadIbm(false);
            entity.setEstado(EstadoRecomendacion.ERROR_EVALUACION.name());
            entity.setFechaEvaluacion(OffsetDateTime.now());
            String motivo = "Evaluación inconsistente: se marcó como oportunidad sin nivel de confianza válido. "
                    + "Requiere reevaluación.";
            entity.setJustificacion(entity.getJustificacion() == null || entity.getJustificacion().isBlank()
                    ? motivo
                    : entity.getJustificacion() + " [" + motivo + "]");
        }
    }

    @Override
    public Optional<RecomendacionOportunidad> buscarPorOcid(String ocid) {
        return repository.findByOcid(ocid).map(this::toDomain);
    }

    @Override
    public List<RecomendacionOportunidad> obtenerOportunidadesIbm(int limit, int offset) {
        return repository.findOportunidadesEvaluadas(limit, offset)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<RecomendacionOportunidad> obtenerPendientes(int limit, int offset) {
        return repository.findPendientes(limit, offset)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<RecomendacionOportunidad> obtenerColaRevision(int limit, int offset) {
        return repository.findColaRevision(limit, offset)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void eliminarTodas() {
        repository.deleteAllInBatch();
    }

    private RecomendacionOportunidad toDomain(RecomendacionEntity entity) {
        RecomendacionOportunidad r = new RecomendacionOportunidad();
        r.setOcid(entity.getOcid());
        r.setTitulo(entity.getTitulo());
        r.setDescripcion(entity.getDescripcion());
        r.setComprador(entity.getComprador());
        r.setMontoReferencial(entity.getMontoReferencial());
        r.setEsOportunidadIbm(entity.isEsOportunidadIbm());
        r.setProductoRecomendado(entity.getProductoRecomendado());
        r.setNivelConfianza(entity.getNivelConfianza());
        r.setJustificacion(entity.getJustificacion());
        r.setEstrategiaComercial(entity.getEstrategiaComercial());
        r.setEstado(entity.getEstado() != null
                ? com.ibm.opportunity_analysis_service.domain.entity.EstadoRecomendacion.valueOf(entity.getEstado())
                : null);
        r.setFechaEvaluacion(entity.getFechaEvaluacion());
        r.setFirmaInfoRevision(entity.getFirmaInfoRevision());
        r.setIntentosReintento(entity.getIntentosReintento() != null ? entity.getIntentosReintento() : 0);
        r.setProximoIntentoAt(entity.getProximoIntentoAt());
        return r;
    }
}
