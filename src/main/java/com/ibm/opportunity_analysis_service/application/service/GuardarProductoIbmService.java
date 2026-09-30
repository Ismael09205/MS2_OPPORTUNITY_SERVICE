package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.ProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class GuardarProductoIbmService {

    private final ProductoIbmPersistencePort productoIbmPersistencePort;

    public GuardarProductoIbmService(ProductoIbmPersistencePort productoIbmPersistencePort) {
        this.productoIbmPersistencePort = productoIbmPersistencePort;
    }

    public List<ProductoIbm> guardarTodos(List<ProductoIbm> productosIbm) {

        if (productosIbm == null || productosIbm.isEmpty()) {
            throw new IllegalArgumentException("La lista de productos IBM no puede estar vacia");
        }

        OffsetDateTime ahora = OffsetDateTime.now();

        productosIbm.forEach(producto -> {
            producto.setFechaCreacion(ahora);
            producto.setFechaActualizacion(ahora);
        });

        return productoIbmPersistencePort.guardarTodos(productosIbm);
    }
}