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

    // Tarea 1.10 (H7 ampliado, decisión del usuario 2026-10-22): aquí existía un campo
    // `metodosPermitidos` declarado pero NUNCA leído por el código, y se eliminó deliberadamente:
    //  (1) no estaba en uso — `AnalizarSimilitudService.calcularBonusMetodo` usa un switch
    //      hardcodeado, no esa lista;
    //  (2) si alguien lo activara como filtro duro excluiría `direct`, y en el gold set 7 de las
    //      11 oportunidades reales (incluidas las 3 ALTO) son `direct`.
    // Decisión: la contratación `direct` NO es cortante; solo penaliza en el score (tarea 2.2).
    // Si en el futuro se quiere restringir por método, esa regla debe decidirse primero.

    /**
     * Métodos expresamente descartados (ej. ínfima cuantía para suministros de micro-escala).
     * Único filtro de método que SÍ es cortante. Con la muestra actual (solo `open` y `direct`)
     * no descartó ningún proceso — medido 2026-10-21, 0 de 381 — pero se conserva porque
     * `infima_cuantia` existe en otros feeds de SERCOP.
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
