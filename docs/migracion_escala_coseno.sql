-- ============================================================================
-- MIGRACION ya aplicada: escala de score_vectorial / score_compuesto
-- ============================================================================
-- NO VOLVER A EJECUTAR sobre una base que ya esta en la escala nueva: el
-- resultado seria incorrecto (score_vectorial pasaria de 1).
--
-- Contexto
--   La busqueda hibrida (0.65 * coseno + 0.35 * texto) fue reemplazada por
--   coseno puro, porque la parte lexica valia 0 en todos los casos medidos
--   (ver README_SIMILITUD.md). Como la parte lexica era 0, los scores
--   guardados valian exactamente 0.65 * coseno.
--
-- Cuando usarlo
--   Solo para llevar a la escala nueva una base de datos con datos calculados
--   ANTES de ese cambio. Comprobacion previa: si
--       SELECT max(score_vectorial) FROM similitud_proceso_producto;
--   devuelve un valor <= 0.65, la base sigue en la escala vieja.
--
-- Que hace
--   1. score_vectorial: de 0.65 * coseno a coseno.
--   2. score_compuesto: se conserva el bonus de monto y de metodo y se
--      corrige solo la parte del coseno (0.75 * coseno).
--   3. recomendacion.score_compuesto: maximo score_compuesto de su proceso.
--
-- El orden del top 5 dentro de cada proceso no cambia.
-- ============================================================================

BEGIN;

UPDATE similitud_proceso_producto
SET score_compuesto = score_compuesto + 0.75 * score_vectorial * (1 / 0.65 - 1),
    score_vectorial = score_vectorial / 0.65;

UPDATE recomendacion r
SET score_compuesto = m.mx
FROM (SELECT ocid, max(score_compuesto) AS mx
      FROM similitud_proceso_producto
      GROUP BY ocid) m
WHERE m.ocid = r.ocid;

COMMIT;
