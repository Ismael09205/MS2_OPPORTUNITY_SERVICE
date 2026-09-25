package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.domain.entity.MuestraCategoriaSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoFiltroSercop;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    public Slice<ProcesoSercop> filtrarPagina(int numeroPagina, int tamanoPagina) {

        Slice<ProcesoSercop> pagina = obtenerProcesosSercopService.obtenerPagina(numeroPagina, tamanoPagina);

        List<ProcesoSercop> procesosFiltrados = pagina.getContent().stream()
                .filter(filtrarOportunidadesService::esOportunidadTecnologica)
                .toList();

        return new SliceImpl<>(procesosFiltrados, pagina.getPageable(), pagina.hasNext());
    }

    public List<ResultadoFiltroSercop> obtenerMuestraFiltrada(int cantidad, int tamanoPagina) {

        List<ResultadoFiltroSercop> resultados = new ArrayList<>();

        int numeroPagina = 0;

        while (resultados.size() < cantidad) {

            Slice<ProcesoSercop> pagina = filtrarPagina(numeroPagina, tamanoPagina);

            for (ProcesoSercop proceso : pagina.getContent()) {

                if (resultados.size() >= cantidad) {
                    break;
                }

                ResultadoFiltroSercop resultado = new ResultadoFiltroSercop();

                resultado.setOcid(proceso.getOcid());
                resultado.setTitulo(proceso.getTitulo());
                resultado.setDescripcion(proceso.getDescripcion());
                resultado.setTipoInterno(proceso.getTipoInterno());

                String textoNormalizado = filtrarOportunidadesService.normalizarTexto(proceso);

                resultado.setCategorias(filtrarOportunidadesService.detectarCategorias(textoNormalizado));

                resultados.add(resultado);
            }

            if (!pagina.hasNext()) {
                break;
            }

            numeroPagina++;
        }

        return resultados;
    }

    public Map<String, Integer> contarCategorias(int tamanoPagina) {

        Map<String, Integer> cantidades = new HashMap<>();

        int numeroPagina = 0;

        while (true) {

            Slice<ProcesoSercop> pagina = filtrarPagina(numeroPagina, tamanoPagina);

            for (ProcesoSercop proceso : pagina.getContent()) {

                String textoNormalizado = filtrarOportunidadesService.normalizarTexto(proceso);

                Set<String> categorias = filtrarOportunidadesService.detectarCategorias(textoNormalizado);

                for (String categoria : categorias) {
                    cantidades.merge(categoria, 1, Integer::sum);
                }
            }

            if (!pagina.hasNext()) {
                break;
            }

            numeroPagina++;
        }

        return cantidades;
    }

    public Map<String, List<MuestraCategoriaSercop>> obtenerMuestrasPorCategoria(int cantidad, int tamanoPagina) {

        Map<String, List<MuestraCategoriaSercop>> muestras = new HashMap<>();

        int numeroPagina = 0;

        while (true) {

            Slice<ProcesoSercop> pagina = filtrarPagina(numeroPagina, tamanoPagina);

            for (ProcesoSercop proceso : pagina.getContent()) {

                String textoNormalizado = filtrarOportunidadesService.normalizarTexto(proceso);

                Set<String> categorias = filtrarOportunidadesService.detectarCategorias(textoNormalizado);

                for (String categoria : categorias) {

                    List<MuestraCategoriaSercop> muestra = muestras.computeIfAbsent(categoria, key -> new ArrayList<>());

                    if (muestra.size() >= cantidad) {
                        continue;
                    }

                    MuestraCategoriaSercop resultado = new MuestraCategoriaSercop();

                    resultado.setOcid(proceso.getOcid());
                    resultado.setTitulo(proceso.getTitulo());
                    resultado.setDescripcion(proceso.getDescripcion());
                    resultado.setCategoria(categoria);

                    muestra.add(resultado);
                }
            }

            if (!pagina.hasNext()) {
                break;
            }

            numeroPagina++;
        }

        return muestras;
    }

    public Map<String, Object> probarFiltroDeterministico() {

        List<ProcesoSercop> procesos = obtenerProcesosSercopService.obtenerTodos();

        List<ProcesoSercop> filtrados = procesos.stream()
                .filter(filtrarOportunidadesService::esOportunidadTecnologica)
                .toList();

        return Map.of(
                "totalProcesos", procesos.size(),
                "totalProcesosFiltrados", filtrados.size(),
                "primeros10", filtrados.stream().limit(10).toList()
        );
    }
}