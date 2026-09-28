package com.ibm.opportunity_analysis_service.adapter.out.persistence.adapter;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.CandidatoTecnologicoEntity;
import com.ibm.opportunity_analysis_service.application.mapper.CandidatoTecnologicoMapper;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.CandidatoTecnologicoJpaRepository;
import com.ibm.opportunity_analysis_service.application.port.out.CandidatoTecnologicoPersistencePort;
import com.ibm.opportunity_analysis_service.domain.entity.CandidatoTecnologico;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CandidatoTecnologicoPersistenceAdapter implements CandidatoTecnologicoPersistencePort {

    private final CandidatoTecnologicoJpaRepository repository;
    private final CandidatoTecnologicoMapper mapper;

    public CandidatoTecnologicoPersistenceAdapter(CandidatoTecnologicoJpaRepository repository, CandidatoTecnologicoMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public CandidatoTecnologico guardar(CandidatoTecnologico candidatoTecnologico) {
        CandidatoTecnologicoEntity entity = mapper.toEntity(candidatoTecnologico);
        CandidatoTecnologicoEntity entityGuardada = repository.save(entity);
        return mapper.toDomain(entityGuardada);
    }

    @Override
    public Optional<CandidatoTecnologico> buscarPorOcid(String ocid) {
        return repository.findByOcid(ocid)
                .map(mapper::toDomain);
    }
}