package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.domain.entity.CandidatoTecnologico;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
public class FiltrarProcesosSercopService {

    private final ObtenerProcesosSercopService obtenerProcesosSercopService;
    private final FiltrarOportunidadesService filtrarOportunidadesService;
    private final GuardarCandidatoTecnologicoService guardarCandidatoTecnologicoService;

    public FiltrarProcesosSercopService(ObtenerProcesosSercopService obtenerProcesosSercopService, FiltrarOportunidadesService filtrarOportunidadesService, GuardarCandidatoTecnologicoService guardarCandidatoTecnologicoService) {
        this.obtenerProcesosSercopService = obtenerProcesosSercopService;
        this.filtrarOportunidadesService = filtrarOportunidadesService;
        this.guardarCandidatoTecnologicoService = guardarCandidatoTecnologicoService;
    }

    public Slice<ProcesoSercop> filtrarPagina(int numeroPagina, int tamanoPagina) {

        Slice<ProcesoSercop> pagina = obtenerProcesosSercopService.obtenerPagina(numeroPagina, tamanoPagina);

        List<ProcesoSercop> procesosFiltrados = pagina.getContent().stream()
                .filter(filtrarOportunidadesService::esOportunidadTecnologica)
                .toList();

        return new SliceImpl<>(procesosFiltrados, pagina.getPageable(), pagina.hasNext());
    }


    public int filtrarYPersistirCandidatos(int tamanoPagina) {

        int totalCandidatos = 0;
        int numeroPagina = 0;

        while (true) {

            Slice<ProcesoSercop> pagina = filtrarPagina(numeroPagina, tamanoPagina);

            for (ProcesoSercop proceso : pagina.getContent()) {

                CandidatoTecnologico candidato = new CandidatoTecnologico();

                candidato.setOcid(proceso.getOcid());
                candidato.setTitulo(proceso.getTitulo());
                candidato.setDescripcion(proceso.getDescripcion());
                candidato.setFechaDeteccion(java.time.OffsetDateTime.now());
                candidato.setEstado("PENDIENTE");

                guardarCandidatoTecnologicoService.guardar(candidato);

                totalCandidatos++;
            }

            if (!pagina.hasNext()) {
                break;
            }

            numeroPagina++;
        }

        return totalCandidatos;
    }
}