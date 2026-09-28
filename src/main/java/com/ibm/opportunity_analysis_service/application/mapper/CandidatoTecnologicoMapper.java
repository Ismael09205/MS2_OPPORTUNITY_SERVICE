package com.ibm.opportunity_analysis_service.application.mapper;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.CandidatoTecnologicoEntity;
import com.ibm.opportunity_analysis_service.domain.entity.CandidatoTecnologico;
import org.springframework.stereotype.Component;

@Component
public class CandidatoTecnologicoMapper {

    public CandidatoTecnologicoEntity toEntity(CandidatoTecnologico candidatoTecnologico) {
        CandidatoTecnologicoEntity entity = new CandidatoTecnologicoEntity();
        entity.setId(candidatoTecnologico.getId());
        entity.setOcid(candidatoTecnologico.getOcid());
        entity.setTitulo(candidatoTecnologico.getTitulo());
        entity.setDescripcion(candidatoTecnologico.getDescripcion());
        entity.setFechaDeteccion(candidatoTecnologico.getFechaDeteccion());
        entity.setEstado(candidatoTecnologico.getEstado());
        return entity;
    }

    public CandidatoTecnologico toDomain(CandidatoTecnologicoEntity entity) {
        CandidatoTecnologico candidatoTecnologico = new CandidatoTecnologico();
        candidatoTecnologico.setId(entity.getId());
        candidatoTecnologico.setOcid(entity.getOcid());
        candidatoTecnologico.setTitulo(entity.getTitulo());
        candidatoTecnologico.setDescripcion(entity.getDescripcion());
        candidatoTecnologico.setFechaDeteccion(entity.getFechaDeteccion());
        candidatoTecnologico.setEstado(entity.getEstado());
        return candidatoTecnologico;
    }
}