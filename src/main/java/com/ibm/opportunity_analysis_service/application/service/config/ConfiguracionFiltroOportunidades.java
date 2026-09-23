package com.ibm.opportunity_analysis_service.application.service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@Setter
@Getter
@ConfigurationProperties(prefix = "filtro-oportunidades")
public class ConfiguracionFiltroOportunidades {

    private Map<String, Categoria> categorias;
    private List<String> exclusiones;

    @Getter
    @Setter
    public static class Categoria {

        private List<String> terminos;
    }
}