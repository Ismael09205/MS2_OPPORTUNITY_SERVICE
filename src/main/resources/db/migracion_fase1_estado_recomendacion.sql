-- =============================================================================
-- Fase 1 — Integridad de la señal (tareas 1.1 y 1.9)
-- Migración DDL de la tabla `recomendacion`.
--
-- Contexto: hasta la Fase 1 no existía distinción entre una preselección vectorial sin
-- evaluar y una recomendación evaluada (H1), y había filas es_oportunidad_ibm=true con
-- nivel_confianza='N/A' (H14), que las reglas del prompt definen como descarte.
--
-- Nota sobre Hibernate: application.yaml usa `ddl-auto: update`, que CREÓ las columnas
-- `estado` y `fecha_evaluacion` al arrancar, pero NO crea restricciones CHECK. Este script
-- aporta los CHECK y debe ejecutarse una vez contra la base (psql -f ...).
-- Idempotente: se puede re-ejecutar sin efecto.
-- =============================================================================

-- 1) Columnas de estado/origen (por si la migración se aplica a una BD que aún no arrancó
--    con la Fase 1; `ddl-auto: update` también las crea, por eso IF NOT EXISTS).
ALTER TABLE recomendacion ADD COLUMN IF NOT EXISTS estado          varchar(32);
ALTER TABLE recomendacion ADD COLUMN IF NOT EXISTS fecha_evaluacion timestamptz;
-- Control REVISAR (2026-10-23): firma de información del proceso al abstener el LLM, para la
-- Opción C (reintento automático SOLO si cambió la información: hash de título+descripción).
ALTER TABLE recomendacion ADD COLUMN IF NOT EXISTS firma_info_revision varchar(64);

-- 2) Restricción de valores de estado (tarea 1.1): los cinco estados del enum.
--    REQUIERE_REVISION = el LLM se abstuvo (3 vías SI/NO/REVISAR) con causa en justificacion;
--    va a la cola humana (herramienta MCP en Bob) y no es un juicio definitivo.
ALTER TABLE recomendacion DROP CONSTRAINT IF EXISTS chk_recomendacion_estado;
ALTER TABLE recomendacion ADD CONSTRAINT chk_recomendacion_estado
    CHECK (estado IS NULL
           OR estado IN ('PRESELECCION_VECTORIAL', 'EVALUADA', 'DESCARTADA_ALCANCE',
                         'ERROR_EVALUACION', 'REQUIERE_REVISION'));

-- 3) Restricción de consistencia (tarea 1.9, H14): si es oportunidad, la confianza debe
--    acreditarla. 'N/A' o vacío con es_oportunidad_ibm=true es una contradicción y no puede
--    persistirse. `estado IS NULL` permite filas legadas anteriores a la Fase 1 mientras no
--    se migren (la Fase 1 limpia la caché y recalcula, ver decisión del usuario 2026-10-22).
ALTER TABLE recomendacion DROP CONSTRAINT IF EXISTS chk_recomendacion_confianza;
ALTER TABLE recomendacion ADD CONSTRAINT chk_recomendacion_confianza
    CHECK (es_oportunidad_ibm = false
           OR (nivel_confianza IS NOT NULL
               AND btrim(nivel_confianza) <> ''
               AND upper(btrim(nivel_confianza)) IN ('ALTO', 'MEDIO', 'BAJO')));

-- 4) Índice para la nueva query de pendientes (tareas 1.5, 1.7 y 1.8).
CREATE INDEX IF NOT EXISTS idx_recomendacion_estado
    ON recomendacion (estado);

-- =============================================================================
-- Verificación:
--   SELECT estado, count(*) FROM recomendacion GROUP BY estado ORDER BY 2 DESC;
--   SELECT count(*) FROM recomendacion
--    WHERE es_oportunidad_ibm = true
--      AND (nivel_confianza IS NULL OR upper(btrim(nivel_confianza)) NOT IN ('ALTO','MEDIO','BAJO'));
--   → el segundo debe devolver 0 (H14 resuelto).
-- =============================================================================
