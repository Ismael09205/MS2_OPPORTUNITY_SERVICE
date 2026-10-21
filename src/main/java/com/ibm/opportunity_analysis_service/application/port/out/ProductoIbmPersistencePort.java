package com.ibm.opportunity_analysis_service.application.port.out;

import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;

import java.util.List;

public interface ProductoIbmPersistencePort {

    List<ProductoIbm> guardarTodos(List<ProductoIbm> productosIbm);

    List<ProductoIbm> buscarTodos();

    java.util.Optional<ProductoIbm> buscarPorId(Long id);
}
