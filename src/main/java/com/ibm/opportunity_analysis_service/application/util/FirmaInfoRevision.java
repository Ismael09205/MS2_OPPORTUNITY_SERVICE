package com.ibm.opportunity_analysis_service.application.util;

import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Firma de la información de un proceso SERCOP para el control REVISAR (Opción C, aprobada por
 * el usuario 2026-10-23): cuando el LLM se abstiene (REQUIERE_REVISION) se guarda esta firma
 * (hash SHA-256, primeros 16 hex, de título+descripción normalizados sin diacríticos). Al volver
 * a evaluar el caso, si la firma actual coincide con la guardada la información NO cambió y no se
 * gasta una llamada al modelo; si difiere (p.ej. se re-ingestó el pliego completo), el caso se
 * re-evalúa automáticamente y sale de la cola humana por sí solo.
 */
public final class FirmaInfoRevision {

    private FirmaInfoRevision() {
    }

    public static String calcular(ProcesoSercop proceso) {
        String base = normalizarSinDiacriticos(proceso.getTitulo())
                + "|"
                + normalizarSinDiacriticos(proceso.getDescripcion());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    private static String normalizarSinDiacriticos(String texto) {
        if (texto == null) {
            return "";
        }
        String descompuesto = Normalizer.normalize(texto, Normalizer.Form.NFKD);
        return descompuesto.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).strip();
    }
}
