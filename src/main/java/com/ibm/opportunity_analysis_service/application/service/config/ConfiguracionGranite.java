package com.ibm.opportunity_analysis_service.application.service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "granite")
public class ConfiguracionGranite {

    private String url;
    private String modelo;
    private int tamanoLote;
}