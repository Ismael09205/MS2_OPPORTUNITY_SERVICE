package com.ibm.opportunity_analysis_service.adapter.out.persistence.adapter;

import com.ibm.opportunity_analysis_service.application.mapper.ProductoIbmMapper;
import com.ibm.opportunity_analysis_service.application.port.out.ProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.entities.ProductoIbmEntity;
import com.ibm.opportunity_analysis_service.adapter.out.persistence.repository.ProductoIbmJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductoIbmPersistenceAdapter implements ProductoIbmPersistencePort {

    private final ProductoIbmJpaRepository productoIbmJpaRepository;
    private final ProductoIbmMapper productoIbmMapper;

    public ProductoIbmPersistenceAdapter(ProductoIbmJpaRepository productoIbmJpaRepository, ProductoIbmMapper productoIbmMapper) {
        this.productoIbmJpaRepository = productoIbmJpaRepository;
        this.productoIbmMapper = productoIbmMapper;
    }

    @Override
    public ProductoIbm guardar(ProductoIbm productoIbm) {
        ProductoIbmEntity entity = productoIbmMapper.toEntity(productoIbm);
        ProductoIbmEntity guardado = productoIbmJpaRepository.save(entity);

        return productoIbmMapper.toDomain(guardado);
    }

    @Override
    public List<ProductoIbm> guardarTodos(List<ProductoIbm> productosIbm) {
        List<ProductoIbmEntity> entities = productosIbm.stream()
                .map(productoIbmMapper::toEntity)
                .toList();

        List<ProductoIbmEntity> guardados = productoIbmJpaRepository.saveAll(entities);

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
}