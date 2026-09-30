package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.adapter.in.dto.ProductoIbmDto;
import com.ibm.opportunity_analysis_service.application.mapper.ProductoIbmMapper;
import com.ibm.opportunity_analysis_service.domain.entity.ProductoIbm;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class CargarCatalogoIbmService {

    private final JsonMapper jsonMapper;
    private final ProductoIbmMapper productoIbmMapper;
    private final GuardarProductoIbmService guardarProductoIbmService;

    public CargarCatalogoIbmService(JsonMapper jsonMapper, ProductoIbmMapper productoIbmMapper, GuardarProductoIbmService guardarProductoIbmService) {
        this.jsonMapper = jsonMapper;
        this.productoIbmMapper = productoIbmMapper;
        this.guardarProductoIbmService = guardarProductoIbmService;
    }

    public List<ProductoIbm> cargar() throws IOException {

        InputStream archivo = getClass()
                .getClassLoader()
                .getResourceAsStream("ibm_products_enriched_es.json");

        if (archivo == null) {
            throw new IllegalStateException("No se encontro el archivo del catalogo de IBM");
        }

        List<ProductoIbmDto> productosDto = jsonMapper.readValue(archivo, new TypeReference<List<ProductoIbmDto>>() {});

        List<ProductoIbm> productos = productosDto.stream()
                .map(productoIbmMapper::toDomain)
                .toList();

        return guardarProductoIbmService.guardarTodos(productos);
    }
}