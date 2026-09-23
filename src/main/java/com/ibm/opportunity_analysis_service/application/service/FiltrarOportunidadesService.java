package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionFiltroOportunidades;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class FiltrarOportunidadesService {

    private final ConfiguracionFiltroOportunidades obtenerFiltroOportunidades;

    private static final Pattern PATRON_DIACRITICO = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    public FiltrarOportunidadesService(ConfiguracionFiltroOportunidades obtenerFiltroOportunidades) {
        this.obtenerFiltroOportunidades = obtenerFiltroOportunidades;
    }

    public String normalizarTexto(ProcesoSercop sercopProceso) {

        if (sercopProceso == null) {
            throw new IllegalArgumentException("El proceso no puede ser null");
        }

        String titulo = sercopProceso.getTitulo();
        String descripcion = sercopProceso.getDescripcion();
        String tipoInterno = sercopProceso.getTipoInterno();

        titulo = titulo != null ? titulo : "";
        descripcion = descripcion != null ? descripcion : "";
        tipoInterno = tipoInterno != null ? tipoInterno : "";

        String oportunidadAnalisis = titulo + " " + descripcion + " " + tipoInterno;

        String nfdNormalizador = Normalizer.normalize(oportunidadAnalisis, Normalizer.Form.NFD);

        String sinTildes = PATRON_DIACRITICO.matcher(nfdNormalizador).replaceAll("");

        return sinTildes.toLowerCase().trim();
    }

    public boolean tieneExclusion(String textoNormalizado) {

        List<String> exclusiones = obtenerFiltroOportunidades.getExclusiones();

        for (String exclusion : exclusiones) {

            String exclusionNormalizada = normalizarTextoExclusion(exclusion);

            if (textoNormalizado.contains(exclusionNormalizada)) {
                return true;
            }
        }

        return false;
    }

    public Set<String> detectarCategorias(String textoNormalizado) {

        Set<String> categoriasDetectadas = new HashSet<>();

        for (Map.Entry<String, ConfiguracionFiltroOportunidades.Categoria> categoria : obtenerFiltroOportunidades.getCategorias().entrySet()) {

            for (String termino : categoria.getValue().getTerminos()) {

                String terminoNormalizado = normalizarTextoExclusion(termino);

                if (textoNormalizado.contains(terminoNormalizado)) {
                    categoriasDetectadas.add(categoria.getKey());
                    break;
                }
            }
        }

        return categoriasDetectadas;
    }

    private String normalizarTextoExclusion(String texto) {

        if (texto == null) {
            return "";
        }

        String nfdNormalizador = Normalizer.normalize(texto, Normalizer.Form.NFD);

        String sinTildes = PATRON_DIACRITICO.matcher(nfdNormalizador).replaceAll("");

        return sinTildes.toLowerCase().trim();
    }
    public boolean esOportunidadTecnologica(ProcesoSercop sercopProceso) {

        String textoNormalizado = normalizarTexto(sercopProceso);

        if (tieneExclusion(textoNormalizado)) {
            return false;
        }

        Set<String> categoriasDetectadas = detectarCategorias(textoNormalizado);

        return !categoriasDetectadas.isEmpty();
    }
}