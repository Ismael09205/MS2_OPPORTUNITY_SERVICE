-- =============================================================================
-- Control REVISAR — Reintentos con backoff de ERROR_EVALUACION (2026-10-24)
--
-- Contexto: `ProcesosScheduler` reintenta los casos ERROR_EVALUACION cada 5 min
-- para siempre. Si el servicio LLM (túnel Ollama) está caído, eso genera llamadas
-- fallidas sin fin y no distingue un fallo transitorio de un caso estancado.
--
-- Política aprobada por el usuario (2026-10-24):
--   - Backoff exponencial: 5m → 15m → 1h → 4h → 8h tras cada intento.
--   - Tope: 6 intentos (≈ 13h20m de fallos seguidos ≈ 1 día). A partir del 6º
--     fallo el caso queda ESTANCADO: no se reprograma (proximo_intento_at = null
--     e intentos_reintento >= 6 lo excluye de la cola) y se avisa con WARN.
--   - El contador vuelve a 0 en cuanto un intento produce veredicto, o si cambia
--     la información del proceso y se re-evalúa.
--   - Una fila REQUIERE_REVISION no entra en esta política: está en la cola
--     humana y solo se re-evalúa si cambió la firma (Opción C).
--
-- Nota sobre Hibernate: application.yaml usa `ddl-auto: update`, que CREARÁ las
-- columnas al arrancar; este script aporta el DEFAULT y la semántica documentada,
-- como en la migración Fase 1. Idempotente: se puede re-ejecutar sin efecto.
-- =============================================================================

ALTER TABLE recomendacion
    ADD COLUMN IF NOT EXISTS intentos_reintento integer NOT NULL DEFAULT 0;

ALTER TABLE recomendacion
    ADD COLUMN IF NOT EXISTS proximo_intento_at timestamptz;

-- =============================================================================
-- Verificación:
--   SELECT count(*) FROM recomendacion WHERE intentos_reintento IS NULL;      → 0
--   SELECT estado, intentos_reintento, proximo_intento_at
--     FROM recomendacion WHERE estado = 'ERROR_EVALUACION' ORDER BY ocid;
-- =============================================================================
