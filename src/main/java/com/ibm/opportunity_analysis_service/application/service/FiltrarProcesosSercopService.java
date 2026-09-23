package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.domain.entity.MuestraCategoriaSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoFiltroSercop;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FiltrarProcesosSercopService {

    private final ObtenerProcesosSercopService obtenerProcesosSercopService;
    private final FiltrarOportunidadesService filtrarOportunidadesService;

    public FiltrarProcesosSercopService(ObtenerProcesosSercopService obtenerProcesosSercopService, FiltrarOportunidadesService filtrarOportunidadesService) {
        this.obtenerProcesosSercopService = obtenerProcesosSercopService;
        this.filtrarOportunidadesService = filtrarOportunidadesService;
    }

    public List<ProcesoSercop> filtrarProcesos() {

        List<ProcesoSercop> procesos = obtenerProcesosSercopService.obtenerTodos();

        return procesos.stream()
                .filter(filtrarOportunidadesService::esOportunidadTecnologica)
                .toList();
    }
    public List<ResultadoFiltroSercop> obtenerMuestraFiltrada(int cantidad) {

        List<ProcesoSercop> procesosFiltrados = filtrarProcesos();

        return procesosFiltrados.stream()
                .limit(cantidad)
                .map(proceso -> {
                    ResultadoFiltroSercop resultado = new ResultadoFiltroSercop();

                    resultado.setOcid(proceso.getOcid());
                    resultado.setTitulo(proceso.getTitulo());
                    resultado.setDescripcion(proceso.getDescripcion());
                    resultado.setTipoInterno(proceso.getTipoInterno());

                    String textoNormalizado = filtrarOportunidadesService.normalizarTexto(proceso);

                    resultado.setCategorias(filtrarOportunidadesService.detectarCategorias(textoNormalizado));

                    return resultado;
                })
                .toList();
    }
    public Map<String, Integer> contarCategorias() {

        List<ProcesoSercop> procesosFiltrados = filtrarProcesos();

        Map<String, Integer> cantidades = new HashMap<>();

        for (ProcesoSercop proceso : procesosFiltrados) {

            String textoNormalizado = filtrarOportunidadesService.normalizarTexto(proceso);

            Set<String> categorias = filtrarOportunidadesService.detectarCategorias(textoNormalizado);

            for (String categoria : categorias) {
                cantidades.merge(categoria, 1, Integer::sum);
            }
        }

        return cantidades;
    }
    public Map<String, List<MuestraCategoriaSercop>> obtenerMuestrasPorCategoria(int cantidad) {

        List<ProcesoSercop> procesosFiltrados = filtrarProcesos();

        Map<String, List<MuestraCategoriaSercop>> muestras = new HashMap<>();

        for (ProcesoSercop proceso : procesosFiltrados) {

            String textoNormalizado = filtrarOportunidadesService.normalizarTexto(proceso);

            Set<String> categorias = filtrarOportunidadesService.detectarCategorias(textoNormalizado);

            for (String categoria : categorias) {

                List<MuestraCategoriaSercop> muestra = muestras.computeIfAbsent(categoria, key -> new java.util.ArrayList<>());

                if (muestra.size() < cantidad) {

                    MuestraCategoriaSercop resultado = new MuestraCategoriaSercop();

                    resultado.setOcid(proceso.getOcid());
                    resultado.setTitulo(proceso.getTitulo());
                    resultado.setDescripcion(proceso.getDescripcion());
                    resultado.setCategoria(categoria);

                    muestra.add(resultado);
                }
            }
        }

        return muestras;
    }
}