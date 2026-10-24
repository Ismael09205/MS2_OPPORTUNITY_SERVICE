package com.ibm.opportunity_analysis_service.adapter.in.dto;

import com.ibm.opportunity_analysis_service.domain.entity.RecomendacionOportunidad;

import java.util.List;

/**
 * Respuesta paginada de {@code GET /recomendaciones} (Fase 1, tareas 1.7 y 1.8).
 *
 * Decisión A del plan: la lista contiene solo recomendaciones {@code estado=EVALUADA}.
 * Las preselecciones vectoriales se exponen en {@code GET /recomendaciones/pendientes}.
 *
 * {@code pendientesCount} existe porque al aplicar la decisión A el endpoint puede devolver
 * menos de {@code limit} filas aunque queden procesos por evaluar: sin este contador el usuario
 * vería una lista corta sin entender por qué (tarea 1.8).
 */
public record PaginaRecomendacionesDto(
        List<RecomendacionOportunidad> recomendaciones,
        int limit,
        int offset,
        long pendientesCount) {
}
