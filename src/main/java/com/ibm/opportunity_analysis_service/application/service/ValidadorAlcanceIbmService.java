package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.application.service.config.ConfiguracionCriteriosOportunidad;
import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import com.ibm.opportunity_analysis_service.domain.entity.ResultadoValidacionAlcance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ValidadorAlcanceIbmService {

    private final ConfiguracionCriteriosOportunidad configuracion;
    private static final Pattern PATRON_DIACRITICOS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    /**
     * Patrones de keyword enterprise ya compilados, por keyword normalizada (tarea 1.11).
     * Se compilan una sola vez: son ~40 keywords y se evalúan sobre cada proceso.
     */
    private static final java.util.Map<String, Pattern> PATRONES_KEYWORD =
            new java.util.concurrent.ConcurrentHashMap<>();

    public ValidadorAlcanceIbmService(ConfiguracionCriteriosOportunidad configuracion) {
        this.configuracion = configuracion;
    }

    /**
     * Evalúa si un proceso SERCOP califica como oportunidad válida dentro del alcance tecnológico corporativo de IBM.
     */
    public ResultadoValidacionAlcance validar(ProcesoSercop proceso) {
        if (!configuracion.isHabilitado()) {
            return ResultadoValidacionAlcance.elegible(true, 0.05);
        }

        if (proceso == null) {
            return ResultadoValidacionAlcance.descartado("Proceso nulo");
        }

        String textoCompletoNormalizado = normalizarTexto(
                String.join(" ",
                        nvl(proceso.getTitulo()),
                        nvl(proceso.getDescripcion()),
                        nvl(proceso.getTipoInterno())
                )
        );

        // 1. Filtro de exclusión por palabras clave negativas (commodities, insumos de oficina, servicios no TI)
        for (String palabraExcluida : configuracion.getPalabrasClaveExcluidas()) {
            String palabraNormalizada = normalizarTexto(palabraExcluida);
            if (!palabraNormalizada.isBlank() && textoCompletoNormalizado.contains(palabraNormalizada)) {
                log.debug("Proceso OCID {} descartado por coincidencia con término excluido: '{}'",
                        proceso.getOcid(), palabraExcluida);
                return ResultadoValidacionAlcance.descartado(
                        "Descartado por término excluido fuera del portafolio IBM: " + palabraExcluida);
            }
        }

        // 2. Filtro por método de contratación expresamente descartado
        if (proceso.getMetodo() != null && !proceso.getMetodo().isBlank()) {
            String metodoNormalizado = normalizarTexto(proceso.getMetodo());
            for (String descartado : configuracion.getMetodosDescartados()) {
                if (metodoNormalizado.contains(normalizarTexto(descartado))) {
                    return ResultadoValidacionAlcance.descartado(
                            "Descartado por método de contratación no corporativo: " + proceso.getMetodo());
                }
            }
        }

        // 3. Verificación de presencia de palabras clave de tecnología enterprise
        boolean tieneEnterpriseKeywords = false;
        int conteoCoincidenciasEnterprise = 0;
        for (String kwNorm : keywordsEnterpriseNormalizadas()) {
            if (coincidePorLimiteDePalabra(textoCompletoNormalizado, kwNorm)) {
                tieneEnterpriseKeywords = true;
                conteoCoincidenciasEnterprise++;
            }
        }

        // 4. Filtro por presupuesto mínimo (si está especificado y no supera el umbral)
        BigDecimal monto = proceso.getMonto() != null ? proceso.getMonto() : proceso.getPresupuesto();
        if (monto != null && monto.compareTo(BigDecimal.ZERO) > 0) {
            if (monto.compareTo(configuracion.getMontoMinimoUsd()) < 0) {
                // Si el monto es muy bajo y no tiene palabras enterprise contundentes, se descarta
                if (conteoCoincidenciasEnterprise < 2) {
                    return ResultadoValidacionAlcance.descartado(
                            String.format("Presupuesto de $%.2f USD inferior al umbral mínimo empresarial ($%.2f USD)",
                                    monto.doubleValue(), configuracion.getMontoMinimoUsd().doubleValue()));
                }
            }
        }

        // Calcular bonificación de alcance: mayor si tiene términos enterprise explícitos
        double bonusAlcance = tieneEnterpriseKeywords ? Math.min(0.10, conteoCoincidenciasEnterprise * 0.02) : 0.0;

        return ResultadoValidacionAlcance.elegible(tieneEnterpriseKeywords, bonusAlcance);
    }

    private String normalizarTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String textoDescompuesto = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return PATRON_DIACRITICOS.matcher(textoDescompuesto)
                .replaceAll("")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    /**
     * Tarea 1.11 (H18): las keywords enterprise se comparan por <b>límite de palabra</b>, no por
     * subcadena. Antes {@code texto.contains("ia")} casaba dentro de palabras que no tienen nada
     * que ver con IA. Medido el 2026-10-22 sobre los 381 procesos, los mayores falsos positivos de
     * "ia" eran: <i>licen<b>cia</b>s</i> (84), <i>licen<b>cia</b>miento</i> (40),
     * <i>sub<i>sidia</i>rias</i> (38), <i>con<b>sultor</b>ía</i> (16), <i>e<b>specializada</b></i>
     * (14), <i>me<b>diante</b></i> (10), <i>social</i> (6), <i>prov<b>incia</b>l</i> (6):
     * en total <b>252 de 381 procesos (66 %)</b> ganaban la keyword "ia" sin mencionar IA, y Q2
     * (monto &lt;15k con ≥2 keywords) pasaba de <b>16</b> procesos reales a <b>2</b>. También
     * inflaba el {@code bonusAlcance} del score, y por tanto el ranking.
     *
     * El texto y las keywords llegan ya normalizados (sin acentos, minúsculas), de modo que
     * "analítica" y "analitica" son la misma keyword: se deduplican para no contar dos veces el
     * mismo concepto en {@code bonusAlcance}.
     */
    private boolean coincidePorLimiteDePalabra(String textoNormalizado, String kwNormalizada) {
        Pattern patron = PATRONES_KEYWORD.computeIfAbsent(
                kwNormalizada, kw -> Pattern.compile("(?<!\\w)" + Pattern.quote(kw) + "(?!\\w)"));
        return patron.matcher(textoNormalizado).find();
    }

    /** Keywords enterprise ya normalizadas y deduplicadas (variantes acentuadas cuentan una vez). */
    private Set<String> keywordsEnterpriseNormalizadas() {
        Set<String> normalizadas = new LinkedHashSet<>();
        for (String kw : configuracion.getPalabrasClaveEnterprise()) {
            String kwNorm = normalizarTexto(kw);
            if (!kwNorm.isBlank()) {
                normalizadas.add(kwNorm);
            }
        }
        return normalizadas;
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
