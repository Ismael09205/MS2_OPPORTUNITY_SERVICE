package com.ibm.opportunity_analysis_service.adapter.out.persistence.adapter;

import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.ProductoIbmEntity;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.EmbeddingProductoIbmJpaRepository;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.ProductoIbmJpaRepository;
import com.ibm.opportunity_analysis_service.application.mapper.ProductoIbmMapper;
import com.ibm.opportunity_analysis_service.application.port.out.ProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class ProductoIbmPersistenceAdapter implements ProductoIbmPersistencePort {

    private final ProductoIbmJpaRepository productoIbmJpaRepository;
    private final EmbeddingProductoIbmJpaRepository embeddingProductoIbmJpaRepository;
    private final ProductoIbmMapper productoIbmMapper;

    public ProductoIbmPersistenceAdapter(
            ProductoIbmJpaRepository productoIbmJpaRepository,
            EmbeddingProductoIbmJpaRepository embeddingProductoIbmJpaRepository,
            ProductoIbmMapper productoIbmMapper) {
        this.productoIbmJpaRepository = productoIbmJpaRepository;
        this.embeddingProductoIbmJpaRepository = embeddingProductoIbmJpaRepository;
        this.productoIbmMapper = productoIbmMapper;
    }

    @Override
    @Transactional
    public List<ProductoIbm> guardarTodos(List<ProductoIbm> productosIbm) {
        List<ProductoIbmEntity> aGuardar = productosIbm.stream()
                .map(p -> {
                    List<ProductoIbmEntity> existentes = productoIbmJpaRepository.findByNombre(p.getNombre());
                    ProductoIbmEntity entity;

                    if (!existentes.isEmpty()) {
                        entity = existentes.get(0);
                        if (existentes.size() > 1) {
                            List<ProductoIbmEntity> sobrantes = existentes.subList(1, existentes.size());
                            List<Long> idsSobrantes = sobrantes.stream().map(ProductoIbmEntity::getId).toList();
                            embeddingProductoIbmJpaRepository.deleteByProductoIbmIdIn(idsSobrantes);
                            productoIbmJpaRepository.deleteAll(sobrantes);
                        }
                    } else {
                        entity = productoIbmMapper.toEntity(p);
                    }

                    entity.setNombre(p.getNombre());
                    entity.setDescripcion(p.getDescripcion());
                    entity.setCategorias(p.getCategorias());
                    entity.setCapacidades(p.getCapacidades());
                    entity.setCasosUso(p.getCasosUso());
                    entity.setTecnologias(p.getTecnologias());
                    entity.setIntegraciones(p.getIntegraciones());
                    entity.setTags(p.getTags());
                    entity.setKeywords(p.getKeywords());
                    entity.setSourceUrl(p.getSourceUrl());
                    if (entity.getFechaCreacion() == null) {
                        entity.setFechaCreacion(p.getFechaCreacion());
                    }
                    entity.setFechaActualizacion(p.getFechaActualizacion());
                    return entity;
                })
                .toList();

        List<ProductoIbmEntity> guardados = productoIbmJpaRepository.saveAll(aGuardar);

        return guardados.stream()
                .map(productoIbmMapper::toDomain)
                .toList();
    }

    @Override
    public List<ProductoIbm> buscarTodos() {
        return productoIbmJpaRepository.findAll()
                .stream()
                .map(productoIbmMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ProductoIbm> buscarPorId(Long id) {
        return productoIbmJpaRepository.findById(id)
                .map(productoIbmMapper::toDomain);
    }
}