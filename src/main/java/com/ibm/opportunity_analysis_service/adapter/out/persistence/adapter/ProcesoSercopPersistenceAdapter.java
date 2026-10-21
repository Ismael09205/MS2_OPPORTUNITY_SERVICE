package com.ibm.opportunity_analysis_service.adapter.out.persistence.adapter;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.ProcesoSercopJpaRepository;
import com.ibm.opportunity_analysis_service.application.mapper.ProcesoSercopMapper;
import com.ibm.opportunity_analysis_service.application.port.out.ProcesoSercopPersistencePort;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProcesoSercopPersistenceAdapter implements ProcesoSercopPersistencePort {

    private final ProcesoSercopJpaRepository repository;
    private final ProcesoSercopMapper mapper;

    public ProcesoSercopPersistenceAdapter(ProcesoSercopJpaRepository repository, ProcesoSercopMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<ProcesoSercop> obtenerTodos() {
        return repository.findAll()
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
