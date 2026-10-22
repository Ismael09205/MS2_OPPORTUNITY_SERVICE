package com.ibm.opportunity_analysis_service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fase 0, tarea 0.2 — Métricas de calidad de las recomendaciones contra el gold set.
 *
 * Compara la verdad humana (goldset_oportunidades.csv, etiquetado a mano) con lo que el
 * sistema tiene hoy persistido en la tabla `recomendacion`. No invoca Ollama ni el pipeline.
 *
 * Decide el usuario (2026-10-21):
 *  - Vehículo: test JUnit con @Tag("local") que corre dentro de `./gradlew test`.
 *  - Se salta automáticamente si la base de datos no está disponible.
 *  - Métricas: cobertura y precisión por separado. Un proceso sin recomendación en la BD
 *    cuenta como NO detectado en la cobertura, pero no penaliza la precisión sobre evaluados.
 *
 * Conexión: variables de entorno SERCOP_DB_URL / SERCOP_DB_USER / SERCOP_DB_PASSWORD
 * (por defecto las mismas de application.yaml).
 */
@Tag("local")
class CalidadRecomendacionesTest {

    /** Niveles que cuentan como oportunidad de negocio para IBM (decisión 2026-10-21: BAJO incluido). */
    private static final Set<String> OPORTUNIDAD_REAL = Set.of("ALTO", "MEDIO", "BAJO");

    private static final String RECURSO = "goldset_oportunidades.csv";

    // ------------------------------------------------------------------ datos

    record Etiqueta(String ocid, String strato, String nivel, String familia, String producto, String nota) {

        boolean oportunidadReal() {
            return OPORTUNIDAD_REAL.contains(nivel);
        }
    }

    record Recomendacion(boolean esOportunidad, String confianza, String producto, String justificacion) {
    }

    // ------------------------------------------------------------------ tests

    @Test
    void elGoldSetEsConsistente() throws IOException {
        Map<String, Etiqueta> etiquetas = cargarGoldSet();

        assertTrue(etiquetas.size() >= 40,
                "Se esperaban al menos 40 procesos en el gold set y hay " + etiquetas.size());

        long conNivelValido = etiquetas.values().stream()
                .filter(e -> !e.nivel().isBlank())
                .count();

        // La muestra base (S1..S6) es determinista (md5(ocid)) y debe seguir en 55; la espiga S7
        // se añadió aparte para calibrar la frontera NO/MEDIO/ALTO (decisión 2026-10-21).
        long base = etiquetas.values().stream().filter(e -> !"S7".equals(e.strato())).count();
        long espiga = etiquetas.values().stream().filter(e -> "S7".equals(e.strato())).count();

        assertEquals(55, base,
                "La muestra base del gold set (S1..S6) debería tener 55 procesos; hay " + base);
        assertTrue(espiga > 0 && espiga <= 10,
                "La espiga S7 debería tener entre 1 y 10 procesos; hay " + espiga);
        assertTrue(conNivelValido <= etiquetas.size());

        System.out.printf(Locale.ROOT, "%n[GOLD SET] %d procesos, %d etiquetados, %d pendientes de etiquetar%n",
                etiquetas.size(), conNivelValido, etiquetas.size() - conNivelValido);
    }

    @Test
    void reporteDeCalidadContraLaBaseDeDatos() throws IOException {
        Map<String, Etiqueta> etiquetas = cargarGoldSet();
        Map<String, Etiqueta> etiquetadas = new LinkedHashMap<>();
        etiquetas.forEach((ocid, e) -> {
            if (!e.nivel().isBlank()) {
                etiquetadas.put(ocid, e);
            }
        });

        Assumptions.assumeTrue(!etiquetadas.isEmpty(),
                "Gold set sin etiquetas todavía: el reporte de calidad queda pendiente de etiquetado.");

        Map<String, Recomendacion> recomendaciones = consultarRecomendaciones(etiquetadas.keySet());
        System.out.println(imprimirReporte(etiquetas, etiquetadas, recomendaciones));
    }

    // ------------------------------------------------------------------ reporte

    private String imprimirReporte(
            Map<String, Etiqueta> todas,
            Map<String, Etiqueta> etiquetadas,
            Map<String, Recomendacion> recomendaciones) {

        long oportunidadesReales = etiquetadas.values().stream().filter(Etiqueta::oportunidadReal).count();
        long evaluados = recomendaciones.size();
        long sistemaDiceSi = recomendaciones.values().stream().filter(Recomendacion::esOportunidad).count();

        long tp = 0, fp = 0, fn = 0, oportunidadSinEvaluar = 0;
        List<String> falsosPositivos = new ArrayList<>();
        List<String> falsosNegativos = new ArrayList<>();

        for (Etiqueta e : etiquetadas.values()) {
            Recomendacion r = recomendaciones.get(e.ocid());
            boolean real = e.oportunidadReal();
            boolean diceSi = r != null && r.esOportunidad();

            if (diceSi && real) {
                tp++;
            } else if (diceSi) {
                fp++;
                falsosPositivos.add(formatearCaso(e, r, "dice SÍ, etiqueta " + e.nivel()));
            } else if (real) {
                fn++;
                if (r == null) {
                    oportunidadSinEvaluar++;
                    falsosNegativos.add(formatearCaso(e, null, "OPORTUNIDAD REAL sin recomendación en BD"));
                } else {
                    falsosNegativos.add(formatearCaso(e, r, "dice NO, etiqueta " + e.nivel()));
                }
            }
        }

        double precision = (tp + fp) == 0 ? 0.0 : (double) tp / (tp + fp);
        double recallCobertura = oportunidadesReales == 0 ? 0.0 : (double) tp / oportunidadesReales;
        double cobertura = etiquetadas.isEmpty() ? 0.0 : (double) evaluados / etiquetadas.size();

        StringBuilder sb = new StringBuilder();
        sb.append("\n================ REPORTE DE CALIDAD (baseline Fase 0) ================\n");
        sb.append(String.format(Locale.ROOT, "Gold set            : %d procesos | etiquetados %d | pendientes %d%n",
                todas.size(), etiquetadas.size(), todas.size() - etiquetadas.size()));
        sb.append(String.format(Locale.ROOT, "Etiquetas reales    : ALTO+MEDIO+BAJO = %d oportunidades | NO = %d%n",
                oportunidadesReales, etiquetadas.size() - oportunidadesReales));
        sb.append(String.format(Locale.ROOT, "Sistema (BD)        : %d con recomendación | %d marcados esOportunidadIbm%n",
                evaluados, sistemaDiceSi));
        sb.append(String.format(Locale.ROOT, "Cobertura           : %.1f%% de los etiquetados tienen recomendación%n",
                cobertura * 100));
        sb.append(String.format(Locale.ROOT, "Precisión (evaluados): %.1f%%  [verdaderos %d / falsos positivos %d]%n",
                precision * 100, tp, fp));
        sb.append(String.format(Locale.ROOT, "Recall cobertura    : %.1f%%  [de %d oportunidades reales, %d detectadas]%n",
                recallCobertura * 100, oportunidadesReales, tp));
        sb.append(String.format(Locale.ROOT, "  └ de las falladas, %d son oportunidades reales aún sin evaluar%n",
                oportunidadSinEvaluar));
        sb.append("\n--- Matriz etiqueta (humano) vs nivelConfianza (sistema) ---\n");
        for (String linea : matriz(etiquetadas, recomendaciones)) {
            sb.append(linea);
        }

        anexarCasos(sb, "FALSOS POSITIVOS (el sistema dice oportunidad, no lo es)", falsosPositivos);
        anexarCasos(sb, "FALSOS NEGATIVOS (es oportunidad, el sistema no la detecta)", falsosNegativos);
        sb.append("======================================================================\n");
        return sb.toString();
    }

    private List<String> matriz(
            Map<String, Etiqueta> etiquetadas, Map<String, Recomendacion> recomendaciones) {

        Map<String, Map<String, Integer>> m = new LinkedHashMap<>();
        for (Etiqueta e : etiquetadas.values()) {
            Recomendacion r = recomendaciones.get(e.ocid());
            String confianza = r == null ? "SIN_RECOMENDACION" : String.valueOf(r.confianza());
            m.computeIfAbsent(e.nivel(), k -> new LinkedHashMap<>())
                    .merge(confianza, 1, Integer::sum);
        }

        List<String> orden = new ArrayList<>(List.of("ALTO", "MEDIO", "BAJO", "NO"));
        m.keySet().stream().sorted().forEach(k -> {
            if (!orden.contains(k)) {
                orden.add(k);
            }
        });

        List<String> lineas = new ArrayList<>();
        for (String nivel : orden) {
            Map<String, Integer> conteos = m.get(nivel);
            if (conteos == null) {
                continue;
            }
            StringBuilder linea = new StringBuilder(String.format(Locale.ROOT, "  %-6s -> ", nivel));
            new java.util.TreeMap<>(conteos).forEach((conf, n) -> linea.append(conf).append("=").append(n).append("  "));
            lineas.add(linea.append('\n').toString());
        }
        if (lineas.isEmpty()) {
            lineas.add("  sin datos\n");
        }
        return lineas;
    }

    private void anexarCasos(StringBuilder sb, String titulo, List<String> casos) {
        sb.append("\n--- ").append(titulo).append(" (").append(casos.size()).append(") ---\n");
        if (casos.isEmpty()) {
            sb.append("  ninguno\n");
            return;
        }
        casos.stream().limit(10).forEach(c -> sb.append("  ").append(c).append('\n'));
        if (casos.size() > 10) {
            sb.append("  ... y ").append(casos.size() - 10).append(" más\n");
        }
    }

    private String formatearCaso(Etiqueta e, Recomendacion r, String motivo) {
        String conf = r == null ? "sin recomendación" : r.confianza() + "/" + r.producto();
        String just = r == null || r.justificacion() == null ? "" : r.justificacion();
        if (just.length() > 120) {
            just = just.substring(0, 120) + "…";
        }
        return String.format(Locale.ROOT, "[%s] %s | etiqueta=%s | sistema=%s | %s",
                e.strato(), e.ocid(), e.nivel(), conf, motivo + (just.isEmpty() ? "" : " :: " + just));
    }

    // ------------------------------------------------------------------ acceso a datos

    private Map<String, Etiqueta> cargarGoldSet() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RECURSO)) {
            assertNotNull(in, "No se encontró " + RECURSO + " en el classpath de tests");
            String contenido = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<List<String>> filas = parseCsv(contenido);
            assertTrue(filas.size() > 1, "El CSV solo tiene encabezado");

            List<String> cabecera = filas.get(0);
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < cabecera.size(); i++) {
                idx.put(cabecera.get(i), i);
            }
            for (String columna : List.of("ocid", "strato", "nivel_esperado", "familia_esperada",
                    "producto_esperado", "nota")) {
                assertTrue(idx.containsKey(columna), "Falta la columna " + columna + " en el CSV");
            }

            Map<String, Etiqueta> etiquetas = new LinkedHashMap<>();
            for (int i = 1; i < filas.size(); i++) {
                List<String> fila = filas.get(i);
                String ocid = valor(fila, idx, "ocid");
                if (ocid.isBlank()) {
                    continue;
                }
                assertTrue(!etiquetas.containsKey(ocid), "OCID duplicado en el gold set: " + ocid);
                etiquetas.put(ocid, new Etiqueta(
                        ocid,
                        valor(fila, idx, "strato"),
                        valor(fila, idx, "nivel_esperado").toUpperCase(Locale.ROOT).trim(),
                        valor(fila, idx, "familia_esperada").trim(),
                        valor(fila, idx, "producto_esperado").trim(),
                        valor(fila, idx, "nota").trim()));
            }
            return etiquetas;
        }
    }

    private Map<String, Recomendacion> consultarRecomendaciones(Set<String> ocids) {
        String url = entorno("SERCOP_DB_URL", "jdbc:postgresql://localhost:5432/sercop_db");
        String usuario = entorno("SERCOP_DB_USER", "ismaeln");
        String password = entorno("SERCOP_DB_PASSWORD", "123456");

        try (Connection conexion = DriverManager.getConnection(url, usuario, password)) {
            StringBuilder sql = new StringBuilder(
                    "SELECT ocid, es_oportunidad_ibm, nivel_confianza, producto_recomendado, justificacion "
                            + "FROM recomendacion WHERE ocid IN (");
            List<String> lista = new ArrayList<>(ocids);
            for (int i = 0; i < lista.size(); i++) {
                sql.append(i == 0 ? "?" : ", ?");
            }
            sql.append(")");

            Map<String, Recomendacion> resultado = new HashMap<>();
            try (PreparedStatement ps = conexion.prepareStatement(sql.toString())) {
                for (int i = 0; i < lista.size(); i++) {
                    ps.setString(i + 1, lista.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        resultado.put(rs.getString("ocid"), new Recomendacion(
                                rs.getBoolean("es_oportunidad_ibm"),
                                rs.getString("nivel_confianza"),
                                rs.getString("producto_recomendado"),
                                rs.getString("justificacion")));
                    }
                }
            }
            return resultado;
        } catch (SQLException e) {
            Assumptions.assumeTrue(false,
                    "Base de datos no disponible, test de calidad omitido: " + e.getMessage());
            return Map.of();
        }
    }

    private String entorno(String variable, String valorPorDefecto) {
        String valor = System.getenv(variable);
        return valor == null || valor.isBlank() ? valorPorDefecto : valor;
    }

    private String valor(List<String> fila, Map<String, Integer> idx, String columna) {
        int i = idx.get(columna);
        return i < fila.size() && fila.get(i) != null ? fila.get(i).trim() : "";
    }

    /** Parser CSV mínimo compatible con RFC 4180 (comillas, comillas escapadas, saltos de línea). */
    private List<List<String>> parseCsv(String contenido) {
        List<List<String>> filas = new ArrayList<>();
        List<String> fila = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreComillas = false;

        for (int i = 0; i < contenido.length(); i++) {
            char c = contenido.charAt(i);
            if (entreComillas) {
                if (c == '"') {
                    if (i + 1 < contenido.length() && contenido.charAt(i + 1) == '"') {
                        campo.append('"');
                        i++;
                    } else {
                        entreComillas = false;
                    }
                } else {
                    campo.append(c);
                }
            } else if (c == '"') {
                entreComillas = true;
            } else if (c == ',') {
                fila.add(campo.toString());
                campo.setLength(0);
            } else if (c == '\n') {
                fila.add(campo.toString());
                campo.setLength(0);
                filas.add(fila);
                fila = new ArrayList<>();
            } else if (c != '\r') {
                campo.append(c);
            }
        }
        if (!campo.isEmpty() || !fila.isEmpty()) {
            fila.add(campo.toString());
            filas.add(fila);
        }
        return filas;
    }
}
