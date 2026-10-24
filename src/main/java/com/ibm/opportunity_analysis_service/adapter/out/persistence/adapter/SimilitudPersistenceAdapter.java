package com.ibm.opportunity_analysis_service.adapter.out.persistence.adapter;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.SimilitudProcesoProductoEntity;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.SimilitudProcesoProductoJpaRepository;
import com.ibm.opportunity_analysis_service.application.port.out.SimilitudPersistencePort;
import com.ibm.opportunity_analysis_service.domain.entity.SimilitudProcesoProducto;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class SimilitudPersistenceAdapter implements SimilitudPersistencePort {

    private final SimilitudProcesoProductoJpaRepository repository;

    public SimilitudPersistenceAdapter(SimilitudProcesoProductoJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public SimilitudProcesoProducto guardarOActualizar(SimilitudProcesoProducto similitud) {
        SimilitudProcesoProductoEntity entity = repository
                .findByOcidAndProductoIbmId(similitud.getOcid(), similitud.getProductoIbmId())
                .orElse(new SimilitudProcesoProductoEntity());

        entity.setOcid(similitud.getOcid());
        entity.setProductoIbmId(similitud.getProductoIbmId());
        entity.setNombreProducto(similitud.getNombreProducto());
        entity.setScoreVectorial(similitud.getScoreVectorial());
        entity.setScoreCompuesto(similitud.getScoreCompuesto());
        entity.setHashContenido(similitud.getHashContenido());
        entity.setFechaCalculo(OffsetDateTime.now());

        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<String> obtenerHashPorOcid(String ocid) {
        return repository.findHashContenidoByOcid(ocid);
    }

    @Override
    public List<SimilitudProcesoProducto> buscarPorOcid(String ocid) {
        return repository.findByOcid(ocid)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public int eliminarPorOcid(String ocid) {
        int eliminadas = repository.findByOcid(ocid).size();
        repository.deleteByOcid(ocid);
        return eliminadas;
    }

    @Override
    public List<String> obtenerSiguientesAEvaluar(int limit) {
        return repository.findSiguientesAEvaluar(limit);
    }

    @Override
    public List<String> obtenerPendientesDeEvaluacion(int limit) {
        return repository.findPendientesDeEvaluacion(limit);
    }

    @Override
    public long contarPendientesDeEvaluacion() {
        return repository.countPendientesDeEvaluacion();
    }

    @Override
    public long contarProcesosConSimilitud() {
        return repository.countDistinctOcid();
    }

    private SimilitudProcesoProducto toDomain(SimilitudProcesoProductoEntity entity) {
        return new SimilitudProcesoProducto(
                entity.getId(),
                entity.getOcid(),
                entity.getProductoIbmId(),
                entity.getNombreProducto(),
                entity.getScoreVectorial(),
                entity.getScoreCompuesto(),
                entity.getHashContenido(),
                entity.getFechaCalculo()
        );
    }
}
