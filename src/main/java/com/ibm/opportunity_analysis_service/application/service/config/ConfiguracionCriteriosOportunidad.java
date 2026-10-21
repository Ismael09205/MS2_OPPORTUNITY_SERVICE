package com.ibm.opportunity_analysis_service.application.service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "opportunity-analysis.qualification")
public class ConfiguracionCriteriosOportunidad {

    /**
     * Habilitar o deshabilitar el filtrado estricto por alcance.
     */
    private boolean habilitado = true;

    /**
     * Monto mínimo referencial en USD para calificar como oportunidad empresarial IBM.
     * Procesos por debajo de este monto suelen ser compras menores/commodities de micro-escala.
     */
    private BigDecimal montoMinimoUsd = new BigDecimal("15000");

    /**
     * Métodos o tipos de contratación que habilitan competencia / servicios corporativos.
     * Si está vacío, no se restringe por método.
     */
    private List<String> metodosPermitidos = new ArrayList<>(List.of(
            "open",
            "licitacion",
            "licitación",
            "cotizacion",
            "cotización",
            "subasta inversa",
            "subasta_inversa",
            "consultoria",
            "consultoría",
            "concurso publico",
            "concurso_publico"
    ));

    /**
     * Métodos expresamente descartados (ej. ínfima cuantía para suministros de micro-escala).
     */
    private List<String> metodosDescartados = new ArrayList<>(List.of(
            "infima_cuantia",
            "infima cuantia",
            "ínfima cuantía"
    ));

    /**
     * Palabras y frases clave que indican compras no enterprise o fuera de alcance IBM
     * (hardware commodity, consumibles, servicios físicos, conectividad pura).
     */
    private List<String> palabrasClaveExcluidas = new ArrayList<>(List.of(
            "cableado estructurado",
            "enlace de internet",
            "servicio de internet dedicado",
            "suministro de toner",
            "suministro de tóner",
            "recarga de toner",
            "recarga de cartuchos",
            "mantenimiento de impresoras",
            "arrendamiento de impresoras",
            "fotocopiadoras",
            "material de oficina",
            "suministros de oficina",
            "papel bond",
            "mobiliario",
            "mantenimiento de aire acondicionado",
            "limpieza y desinfeccion",
            "limpieza y desinfección",
            "guardias de seguridad",
            "seguridad fisica",
            "seguridad física",
            "catering",
            "alimentos y bebidas",
            "adquisicion de vehiculos",
            "adquisición de vehículos"
    ));

    /**
     * Palabras clave que confirman requerimientos de tecnología / software enterprise
     * (Nube Híbrida, IA, Datos, Ciberseguridad, Middleware, Observabilidad, Gestión de Activos).
     */
    private List<String> palabrasClaveEnterprise = new ArrayList<>(List.of(
            "software", "plataforma", "nube", "cloud", "inteligencia artificial", "ia",
            "analitica", "analítica", "base de datos", "ciberseguridad", "seguridad informatica",
            "seguridad informática", "siem", "soc", "observabilidad", "monitoreo", "middleware",
            "integracion", "integración", "api", "devops", "contenedores", "openshift", "red hat",
            "gestion de activos", "gestión de activos", "migracion", "migración", "infraestructura tecnologica",
            "infraestructura tecnológica", "servidores", "almacenamiento empresarial", "storage",
            "backup", "recuperacion ante desastres", "disaster recovery", "gobierno de datos",
            "arquitectura empresarial", "modernizacion", "modernización", "mainframe", "watson"
    ));
}
