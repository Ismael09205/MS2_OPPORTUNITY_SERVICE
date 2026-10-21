package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.ProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class CatalogoIbmBootstrapService {

    private final ProductoIbmPersistencePort productoIbmPersistencePort;
    private final EmbeddingProductoIbmPersistencePort embeddingProductoIbmPersistencePort;
    private final CargarCatalogoIbmService cargarCatalogoIbmService;
    private final GenerarEmbeddingProductosIbmService generarEmbeddingProductosIbmService;

    public CatalogoIbmBootstrapService(
            ProductoIbmPersistencePort productoIbmPersistencePort,
            EmbeddingProductoIbmPersistencePort embeddingProductoIbmPersistencePort,
            CargarCatalogoIbmService cargarCatalogoIbmService,
            GenerarEmbeddingProductosIbmService generarEmbeddingProductosIbmService) {
        this.productoIbmPersistencePort = productoIbmPersistencePort;
        this.embeddingProductoIbmPersistencePort = embeddingProductoIbmPersistencePort;
        this.cargarCatalogoIbmService = cargarCatalogoIbmService;
        this.generarEmbeddingProductosIbmService = generarEmbeddingProductosIbmService;
    }

    /**
     * Verifica que el catálogo de productos IBM y sus correspondientes embeddings estén
     * cargados y persistidos en la base de datos. Si no lo están, los carga e inicializa
     * de forma automática e idempotente.
     */
    public synchronized boolean asegurarCatalogoIbmListo() {
        try {
            List<ProductoIbm> productos = productoIbmPersistencePort.buscarTodos();

            if (productos.isEmpty()) {
                log.info("Catálogo IBM no encontrado en base de datos. Cargando catálogo inicial desde JSON...");
                productos = cargarCatalogoIbmService.cargar();
                log.info("Catálogo IBM cargado exitosamente ({} productos).", productos.size());
            }

            // Generar embeddings para productos IBM si aún no existen
            try {
                // Si buscarSimilares con un vector ficticio no arroja o si los embeddings no existen
                // simplemente generamos los que falten
                log.info("Asegurando embeddings vectoriales del catálogo IBM...");
                generarEmbeddingProductosIbmService.generarTodos();
                log.info("Embeddings del catálogo IBM verificados y listos.");
            } catch (Exception e) {
                log.warn("Verificación de embeddings IBM: {}", e.getMessage());
            }

            return true;
        } catch (Exception e) {
            log.error("Error asegurando disponibilidad del catálogo IBM: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean estaListo() {
        List<ProductoIbm> productos = productoIbmPersistencePort.buscarTodos();
        return !productos.isEmpty();
    }
}
