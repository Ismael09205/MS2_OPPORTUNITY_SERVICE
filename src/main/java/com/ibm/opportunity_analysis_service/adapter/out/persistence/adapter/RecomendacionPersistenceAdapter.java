package com.ibm.opportunity_analysis_service.adapter.out.persistence.adapter;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.RecomendacionEntity;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.RecomendacionJpaRepository;
import com.ibm.opportunity_analysis_service.application.port.out.RecomendacionPersistencePort;
import com.ibm.opportunity_analysis_service.domain.entity.RecomendacionOportunidad;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

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
        entity.setScoreCompuesto(scoreCompuesto);
        entity.setFechaGeneracion(OffsetDateTime.now());

        repository.save(entity);
        return recomendacion;
    }

    @Override
    public Optional<RecomendacionOportunidad> buscarPorOcid(String ocid) {
        return repository.findByOcid(ocid).map(this::toDomain);
    }

    @Override
    public List<RecomendacionOportunidad> obtenerOportunidadesIbm(int limit, int offset) {
        return repository.findOportunidadesIbm(limit, offset)
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
        return r;
    }
}
