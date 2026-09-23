package com.ibm.opportunity_analysis_service.FiltrarOportunidadesServiceTest;

import com.ibm.opportunity_analysis_service.application.service.FiltrarOportunidadesService;
import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionFiltroOportunidades;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FiltrarOportunidadesServiceTest {

    @Test
    void debeDetectarCategorias() {

        ConfiguracionFiltroOportunidades configuracion = new ConfiguracionFiltroOportunidades();

        ConfiguracionFiltroOportunidades.Categoria software = new ConfiguracionFiltroOportunidades.Categoria();
        software.setTerminos(List.of("software", "aplicacion", "sistema informatico"));

        ConfiguracionFiltroOportunidades.Categoria cloud = new ConfiguracionFiltroOportunidades.Categoria();
        cloud.setTerminos(List.of("cloud", "nube", "saas"));

        configuracion.setCategorias(Map.of(
                "software", software,
                "cloud", cloud
        ));

        FiltrarOportunidadesService service = new FiltrarOportunidadesService(configuracion);

        ProcesoSercop proceso = new ProcesoSercop();
        proceso.setTitulo("Implementacion de plataforma SaaS");
        proceso.setDescripcion("Desarrollo de software para empresa");
        proceso.setTipoInterno("");

        String textoNormalizado = service.normalizarTexto(proceso);

        Set<String> categorias = service.detectarCategorias(textoNormalizado);

        System.out.println("Texto normalizado: " + textoNormalizado);
        System.out.println("Categorias detectadas: " + categorias);

        assertTrue(categorias.contains("software"));
        assertTrue(categorias.contains("cloud"));
    }
}