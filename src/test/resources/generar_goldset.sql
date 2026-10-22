-- Genera el gold set de la Fase 0 (55 procesos estratificados) con hechos para etiquetar.
-- Las columnas de verdad (nivel_esperado, familia_esperada, producto_esperado, nota) salen vacías.
-- Uso: psql -h localhost -U ismaeln -d sercop_db -f src/test/resources/generar_goldset.sql
-- Muestreo determinista (md5(ocid)) para que la muestra sea reproducible.
-- Nota: COPY del lado del servidor → requiere superusuario y ruta absoluta.
-- (Si no se puede, usar \copy en una sola línea con la misma consulta.)

COPY (
WITH base AS (
  SELECT ocid, titulo, left(coalesce(descripcion,''), 700) AS descripcion_corta,
         comprador, categoria_cpc,
         coalesce(monto, presupuesto, 0) AS monto_usd, metodo, tipo_interno, region,
         lower(coalesce(titulo,'') || ' ' || coalesce(descripcion,'')) AS txt
  FROM proceso_contratacion
),
f AS (
  SELECT *,
    (monto_usd = 0) AS sin_monto,
    (monto_usd > 0 AND monto_usd < 15000) AS menor15k,
    (monto_usd >= 15000) AS ok15k,
    (txt ~ '(oracle|microsoft|sql server|salesforce|sap|cisco|fortinet|palo alto|vmware|adobe|eset|kaspersky)') AS comp
  FROM base
),
s AS (
  SELECT *, CASE
      WHEN categoria_cpc IN ('CLOUD_COMPUTING','BASES_DATOS_PLATAFORMAS') AND ok15k AND NOT comp THEN 'S1'
      WHEN categoria_cpc IN ('LICENCIAS_SOFTWARE','SUSCRIPCIONES_SAAS') AND ok15k AND comp THEN 'S2'
      WHEN categoria_cpc IN ('LICENCIAS_SOFTWARE','SUSCRIPCIONES_SAAS') AND ok15k THEN 'S3'
      WHEN sin_monto THEN 'S5'
      WHEN menor15k THEN 'S6'
      WHEN categoria_cpc = 'DESARROLLO_CONSULTORIA_TI' THEN 'S4'
      ELSE 'otro' END AS strato
  FROM f
),
rn AS (
  SELECT *, row_number() OVER (PARTITION BY strato ORDER BY md5(ocid)) AS fila
  FROM s
),
eleg AS (
  SELECT * FROM rn WHERE
       (strato = 'S1' AND fila <= 12)
    OR (strato = 'S2' AND fila <= 12)
    OR (strato = 'S3' AND fila <=  8)
    OR (strato = 'S4' AND fila <=  8)
    OR (strato = 'S5' AND fila <=  8)
    OR (strato = 'S6' AND fila <=  7)
)
SELECT e.ocid, e.strato, e.titulo, e.comprador, e.categoria_cpc,
       (CASE WHEN e.monto_usd > 0 THEN e.monto_usd ELSE NULL END) AS monto_usd,
       e.metodo, e.tipo_interno, e.region, e.comp AS menciona_competidor,
       (SELECT string_agg(z.nombre_producto || ' [cos=' || round(z.cos::numeric, 3) || ']', ' | '
                          ORDER BY z.cos DESC)
          FROM (SELECT nombre_producto, score_vectorial AS cos
                  FROM similitud_proceso_producto
                 WHERE ocid = e.ocid
                 ORDER BY score_vectorial DESC
                 LIMIT 3) z) AS candidatos_top3_sistema,
       e.descripcion_corta,
       '' AS nivel_esperado, '' AS familia_esperada, '' AS producto_esperado, '' AS nota
FROM eleg e
ORDER BY e.strato, md5(e.ocid)
) TO '/Users/ismaeln/Desktop/Proyecto_IBM/opportunity_analysis_service/src/test/resources/goldset_oportunidades.csv' WITH (FORMAT CSV, HEADER);
