package com.ibm.opportunity_analysis_service.application.mapper;

import com.ibm.opportunity_analysis_service.adapter.in.dto.ProductoIbmDto;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.ProductoIbmEntity;
import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;
import org.springframework.stereotype.Component;

@Component
public class ProductoIbmMapper {

    public ProductoIbmEntity toEntity(ProductoIbm productoIbm) {
        ProductoIbmEntity entity = new ProductoIbmEntity();

        entity.setId(productoIbm.getId());
        entity.setNombre(productoIbm.getNombre());
        entity.setDescripcion(productoIbm.getDescripcion());
        entity.setCategorias(productoIbm.getCategorias());
        entity.setCapacidades(productoIbm.getCapacidades());
        entity.setCasosUso(productoIbm.getCasosUso());
        entity.setTecnologias(productoIbm.getTecnologias());
        entity.setIntegraciones(productoIbm.getIntegraciones());
        entity.setTags(productoIbm.getTags());
        entity.setKeywords(productoIbm.getKeywords());
        entity.setSourceUrl(productoIbm.getSourceUrl());
        entity.setFechaCreacion(productoIbm.getFechaCreacion());
        entity.setFechaActualizacion(productoIbm.getFechaActualizacion());

        return entity;
    }

    public ProductoIbm toDomain(ProductoIbmDto dto) {
        ProductoIbm productoIbm = new ProductoIbm();

        productoIbm.setNombre(dto.getProduct_name());
        productoIbm.setDescripcion(dto.getDescription());
        productoIbm.setCategorias(dto.getCategory());
        productoIbm.setCapacidades(dto.getCapabilities());
        productoIbm.setCasosUso(dto.getUse_cases());
        productoIbm.setTecnologias(dto.getTechnologies());
        productoIbm.setIntegraciones(dto.getIntegrations());
        productoIbm.setTags(dto.getTags());
        productoIbm.setKeywords(dto.getKeywords());
        productoIbm.setSourceUrl(dto.getSource_url());

        return productoIbm;
    }

    public ProductoIbm toDomain(ProductoIbmEntity entity) {
        ProductoIbm productoIbm = new ProductoIbm();

        productoIbm.setId(entity.getId());
        productoIbm.setNombre(entity.getNombre());
        productoIbm.setDescripcion(entity.getDescripcion());
        productoIbm.setCategorias(entity.getCategorias());
        productoIbm.setCapacidades(entity.getCapacidades());
        productoIbm.setCasosUso(entity.getCasosUso());
        productoIbm.setTecnologias(entity.getTecnologias());
        productoIbm.setIntegraciones(entity.getIntegraciones());
        productoIbm.setTags(entity.getTags());
        productoIbm.setKeywords(entity.getKeywords());
        productoIbm.setSourceUrl(entity.getSourceUrl());
        productoIbm.setFechaCreacion(entity.getFechaCreacion());
        productoIbm.setFechaActualizacion(entity.getFechaActualizacion());

        return productoIbm;
    }
}