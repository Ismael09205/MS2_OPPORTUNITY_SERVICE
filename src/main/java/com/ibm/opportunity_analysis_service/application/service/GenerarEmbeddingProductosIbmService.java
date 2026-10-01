package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.port.out.EmbeddingProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.port.out.ProductoIbmPersistencePort;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionGranite;
import com.ibm.opportunity_analysis_service.domain.entity.EmbeddingProductoIbm;
import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class GenerarEmbeddingProductosIbmService {

    private final ProductoIbmPersistencePort productoIbmPersistencePort;
    private final EmbeddingProductoIbmPersistencePort embeddingProductoIbmPersistencePort;
    private final GenerarEmbeddingService generarEmbeddingService;
    private final ConfiguracionGranite configuracionGranite;

    public GenerarEmbeddingProductosIbmService(ProductoIbmPersistencePort productoIbmPersistencePort, EmbeddingProductoIbmPersistencePort embeddingProductoIbmPersistencePort, GenerarEmbeddingService generarEmbeddingService, ConfiguracionGranite configuracionGranite) {
        this.productoIbmPersistencePort = productoIbmPersistencePort;
        this.embeddingProductoIbmPersistencePort = embeddingProductoIbmPersistencePort;
        this.generarEmbeddingService = generarEmbeddingService;
        this.configuracionGranite = configuracionGranite;
    }

    public List<EmbeddingProductoIbm> generarTodos() {

        List<ProductoIbm> productos = productoIbmPersistencePort.buscarTodos();

        if (productos.isEmpty()) {
            throw new IllegalStateException("No existen productos IBM para generar embeddings");
        }

        List<EmbeddingProductoIbm> embeddings = new ArrayList<>();

        for (ProductoIbm producto : productos) {

            String texto = construirTextoEmbedding(producto);

            List<Float> valores = generarEmbeddingService.generar(texto);

            float[] vector = convertirVector(valores);

            EmbeddingProductoIbm embedding = new EmbeddingProductoIbm();

            embedding.setProductoIbmId(producto.getId());
            embedding.setModelo(configuracionGranite.getModelo());
            embedding.setTextoEmbedding(texto);
            embedding.setEmbedding(vector);
            embedding.setFechaGeneracion(OffsetDateTime.now());

            embeddings.add(embedding);
        }

        return embeddingProductoIbmPersistencePort.guardarTodos(embeddings);
    }

    private String construirTextoEmbedding(ProductoIbm producto) {

        return """
                Producto: %s
                Descripcion: %s
                Categorias: %s
                Capacidades: %s
                Casos de uso: %s
                Tecnologias: %s
                Integraciones: %s
                Tags: %s
                Keywords: %s
                """.formatted(
                producto.getNombre(),
                producto.getDescripcion(),
                unir(producto.getCategorias()),
                unir(producto.getCapacidades()),
                unir(producto.getCasosUso()),
                unir(producto.getTecnologias()),
                unir(producto.getIntegraciones()),
                unir(producto.getTags()),
                unir(producto.getKeywords())
        );
    }

    private String unir(List<String> valores) {

        if (valores == null || valores.isEmpty()) {
            return "";
        }

        return String.join(", ", valores);
    }

    private float[] convertirVector(List<Float> valores) {

        if (valores == null || valores.isEmpty()) {
            throw new IllegalStateException("Granite no devolvio valores para el embedding");
        }

        float[] vector = new float[valores.size()];

        for (int i = 0; i < valores.size(); i++) {
            vector[i] = valores.get(i);
        }

        return vector;
    }
}