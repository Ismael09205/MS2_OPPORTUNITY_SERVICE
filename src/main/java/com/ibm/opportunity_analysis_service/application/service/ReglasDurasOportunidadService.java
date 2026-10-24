package com.ibm.opportunity_analysis_service.application.service;

import com.ibm.opportunity_analysis_service.domain.entity.ProcesoSercop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Filtro determinista de descarte que se ejecuta ANTES del LLM en la cascada de evaluación
 * (configuración oficial de la corrida 6: reglas duras → LLM 3 vías con seed 42).
 *
 * Réplica en Java de {@code experimentos/validacion_reglas.py}, con la MISMA semántica validada
 * en el Paso A sobre los 120 casos etiquetados del gold set: 33 casos muertos por regla,
 * 33/33 predicciones NO correctas, 0 fugas y 0 casos REVISAR muertos. Si una regla dispara, el
 * proceso se descarta como NO sin llamar al modelo: certeza gratis, 0 tokens, instantáneo
 * (resolvió el 27,5 % del split de prueba de la corrida 6, 12/12 aciertos).
 *
 * Las listas de marcas viven aquí como constantes (y no en application.yaml) a propósito:
 * son parte de un criterio MEDIDO; moverlas rompe la garantía 33/33 y debe pasar por una
 * re-validación con {@code validacion_reglas.py} antes de cambiarse.
 *
 * Semántica exacta (idéntica al script Python, con normalización NFKD sin diacríticos):
 *  - R1_CAT_CONSULTORIA: la categoría CPC del proceso es DESARROLLO_CONSULTORIA_TI.
 *  - R2_ANTIVIRUS: "antivirus" aparece en título o descripción.
 *  - R3_CADDIB: CAD o BIM como palabra completa SOLO en la descripción (excepción del usuario:
 *    el título no descarta, puede mencionar CAD/BIM como referencia sin ser el objeto).
 *  - R4_CONTENIDO: Web of Science / SciELO / Scopus / Wolfram (contenido propietario de terceros).
 *  - R5_RENOV_MONOMARCA: hay "renovación" + se nombra una marca de tercero + NO se nombra
 *    ninguna marca IBM (renovación monomarca ajena cerrada).
 */
@Slf4j
@Service
public class ReglasDurasOportunidadService {

    /** Categoría CPC de consultoría/desarrollo: el objeto central es servicio profesional. */
    static final String CAT_CONSULTORIA = "desarrollo_consultoria_ti";

    /** Marcas cuya mención en contexto de renovación cierra la puerta a IBM. Sincronizar con validacion_reglas.py. */
    static final List<String> TERCEROS = List.of(
            "sophos", "fortinet", "palo alto", "cisco", "oracle", "microsoft", "adobe",
            "eset", "kaspersky", "trend micro", "mcafee", "symantec", "broadcom",
            "vmware", "citrix", "esri", "autocad", "veeam", "docuware", "olymp",
            "zimbra", "solarwinds", "laserfiche", "scopus");

    /** Marcas IBM: si el pliego las nombra, una renovación deja de ser monomarca ajena (R5). */
    static final List<String> IBM_MARCAS = List.of(
            "ibm", "qradar", "guardium", "verify", "maas360", "instana", "turbonomic",
            "maximo", "db2", "watsonx", "openshift", "cloud pak", "sterling",
            "app connect", "storage defender", "bigfix", "cognos", "spss",
            "planning analytics", "aix", "power", "mq");

    static final List<String> CONTENIDO_TERCEROS = List.of("web of science", "scielo", "scopus", "wolfram");

    /** Palabras completas CAD/BIM sobre texto normalizado (bordes \w como el \b de Python). */
    private static final Pattern PATRON_CAD_BIM =
            Pattern.compile("\\b(cad|bim)\\b", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Aplica las 5 reglas al proceso.
     *
     * @return vacío si ninguna regla dispara (el proceso sigue hacia el LLM), o el código de la
     *         regla (o reglas) disparada(s), p.ej. "R2_ANTIVIRUS", para usarlo como causa del descarte.
     */
    public Optional<String> aplicar(ProcesoSercop proceso) {
        String titulo = normalizar(proceso.getTitulo());
        String descripcion = normalizar(proceso.getDescripcion());
        String categoria = normalizar(proceso.getCategoriaCpc());
        String texto = titulo + " " + descripcion;

        List<String> hits = new java.util.ArrayList<>();

        if (CAT_CONSULTORIA.equals(categoria.strip())) {
            hits.add("R1_CAT_CONSULTORIA");
        }
        if (texto.contains("antivirus")) {
            hits.add("R2_ANTIVIRUS");
        }
        // Excepción del usuario: CAD/BIM en el título NO descarta, solo en la descripción.
        if (PATRON_CAD_BIM.matcher(descripcion).find()) {
            hits.add("R3_CADDIB");
        }
        if (CONTENIDO_TERCEROS.stream().anyMatch(texto::contains)) {
            hits.add("R4_CONTENIDO");
        }
        boolean tieneRenovacion = texto.contains("renovacion");
        boolean tieneMarcaTercero = TERCEROS.stream().anyMatch(texto::contains);
        boolean tieneMarcaIbm = IBM_MARCAS.stream().anyMatch(texto::contains);
        if (tieneRenovacion && tieneMarcaTercero && !tieneMarcaIbm) {
            hits.add("R5_RENOV_MONOMARCA");
        }

        if (hits.isEmpty()) {
            return Optional.empty();
        }
        String reglas = String.join(" + ", hits);
        log.debug("Regla dura disparada para OCID {}: {}", proceso.getOcid(), reglas);
        return Optional.of(reglas);
    }

    /**
     * Normalización idéntica a {@code norm()} de validacion_reglas.py: NFKD, sin marcas
     * combinantes (diacríticos) y en minúsculas. "Renovación" → "renovacion", por ejemplo.
     */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String descompuesto = Normalizer.normalize(texto, Normalizer.Form.NFKD);
        String sinDiacriticos = descompuesto.replaceAll("\\p{M}+", "");
        return sinDiacriticos.toLowerCase(Locale.ROOT);
    }
}
