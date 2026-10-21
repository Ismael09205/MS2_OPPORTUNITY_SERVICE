# Plan para dar buenas recomendaciones al usuario

**Alcance acordado:** pipeline (calidad de la señal) + presentación (qué ve y cómo responde el usuario).
**Modo de decisión:** Bob (asistente externo, `EVALUADOR=bob`). Granite sigue funcionando, pero el plan está pensado para que el contrato de datos sirva a ambos.

**Dependencia externa:** `sercop_ingestion_service` (MS1) ingiere el `.zip` en la tabla compartida `proceso_contratacion`. Este servicio no toca la ingesta; solo lee.

---

## Diagnóstico (qué encontré al leer el código)

Hallazgos que justifican las fases. Todos verificados en el código actual, no en suposiciones.

| # | Hallazgo | Dónde | Impacto |
|---|---|---|---|
| H1 | La "preselección vectorial" se persiste con `esOportunidadIbm=true` y confianza `ALTO/MEDIO/BAJO` calculada con umbrales hardcodeados (0.75 / 0.68 / delta 0.04 / 0.02), sin pasar por validador ni LLM. En `GET /recomendaciones` es indistinguible de una recomendación evaluada. | `RecomendarOportunidadesService.generarRecomendacionRapidaVectorial` (líneas 94-142) | El usuario recibe preselecciones presentadas como recomendaciones firmes. La semántica de `nivelConfianza` significa dos cosas distintas. |
| H2 | Lo único que distingue una preselección de una evaluada es `justificacion.startsWith("Preselección por afinidad vectorial")` | `RecomendarOportunidadesService` línea 188 | Acoplamiento frágil; cambiar el texto rompe la reevaluación. |
| H3 | Si el LLM falla, el fallback escribe `esOportunidadIbm = !candidatosTop.isEmpty()` → un error de Ollama se convierte en "sí es oportunidad" y se cachea. | `RecomendarOportunidadesService` líneas 269-276 | Falsos positivos permanentes; sin reintento. |
| H4 | Si un proceso deja de ser elegible tras un cambio de contenido, `AnalizarSimilitudService` hace `continue` pero **no borra** las `similitud_proceso_producto` huérfanas; la preselección sigue marcándolo como oportunidad. | `AnalizarSimilitudService.analizar()` líneas 64-69 | Descartes que nunca se materializan. |
| H5 | `generarRecomendacionRapidaVectorial`, `generarYPersistirRecomendacion` y `obtenerCandidatosCrudos` cargan **todos** los procesos y filtran por OCID en memoria (`obtenerTodos()` + `stream().filter`). No existe `buscarPorOcid` para procesos. | `RecomendarOportunidadesService` líneas 95-98, 199-202, 301-304 | Latencia O(n) por request; rompe la promesa de respuesta "on-demand en 1-2 s" con miles de procesos. |
| H6 | Fórmula real del código: `0.70·coseno + bonusMonto + bonusMétodo + bonusAlcance`. `README_SIMILITUD` §3 documenta `0.75·coseno + bonusMonto + bonusMétodo` (sin alcance). | `AnalizarSimilitudService.calcularScoreCompuesto` (línea 147) vs `README_SIMILITUD.md` | Documentos y código divergentes; confunde cualquier ajuste posterior. |
| H7 | `calcularBonusMetodo` usa un `switch` hardcodeado `open/direct/default=0.05`, mientras `ConfiguracionCriteriosOportunidad.metodosPermitidos` está declarado y **nunca se usa**. Cualquier método SERCOP que no sea `open`/`direct` recibe 0.10 o 0.03/0.05 por azar de nombres. | `AnalizarSimilitudService` líneas 134-141; `ConfiguracionCriteriosOportunidad` línea 31 | Ranking afectado por vocabulario no normalizado. |
| H8 | Procesos con `monto`/`presupuesto` nulos **no** se descartan por monto (el filtro solo actúa si hay monto > 0) y tampoco reportan que "no tienen monto". | `ValidadorAlcanceIbmService` líneas 79-90 | Candidatos sin valorar entran con bonus de monto 0 sin señalar la incertidumbre. |
| H9 | `findOportunidadesIbm` ordena solo por `score_compuesto DESC`; `nivelConfianza` y el estado de evaluación no influyen. | `RecomendacionJpaRepository` líneas 19-25 | Un BAJO o una preselección sin evaluar encabezan la lista. |
| H10 | `/candidatos/{ocid}` (la entrada de Bob) no ejecuta el validador ni envía `scoreCompuesto`, método, fecha, ni el resultado del validador. Bob desconoce que ya se filtró y cuál es el monto/método reales. | `RecomendarOportunidadesService.obtenerCandidatosCrudos` (300-338) + prompt en `MCP_SERVER/src/index.ts` (61-92) | El razonamiento de Bob parte de información incompleta. |
| H11 | El resumen de lista del MCP devuelve JSON crudo con `justificacion.slice(0,200)`, sin título, fecha, región ni estado, y con textos que mezclan modos ("Granite solo trabaja cuando..."). | `MCP_SERVER/src/index.ts` (107-162) | La primera impresión del usuario es poco accionable. |
| H12 | No existen filtros (producto, tema, monto, región, confianza, estado): solo paginación en 6. | `AnalysisController` + MCP | El usuario no puede acotar; solo "dame más". |
| H13 | Si cambia el catálogo IBM, el hash no cambia y nada se recalcula (documentado, no corregido). | `README_SIMILITUD` §3 | Recomendaciones congeladas con un catálogo viejo. |

---

## Fase 0 — Línea base medible (primero, porque sin esto "mejor" es subjetivo)

**Objetivo:** poder decir antes/después si las recomendaciones mejoraron.

| # | Tarea | Archivos |
|---|---|---|
| 0.1 | Crear un gold set: muestrear entre 40 y 60 procesos reales de la BD y etiquetarlos a mano: `ocid, es_oportunidad_esperado, producto_esperado, nota`. Incluir deliberadamente: claros commodity (deben salir `false`), claros enterprise (deben salir `true`), mixtos (deben salir `MEDIO`) y casos borde (monto nuloso, método raro). | `src/test/resources/goldset_oportunidades.csv` |
| 0.2 | Test de calidad que corre el pipeline sobre el gold set y reporta: precisión, recall, acierto del producto, distribución de confianza y lista de falsos positivos/negativos con su motivo. | `src/test/java/.../CalidadRecomendacionesIT.java` |
| 0.3 | Registrar el baseline actual (números de la corrida de 0.2) en este documento. | este archivo, sección "Baseline" |

**Criterio de aceptación:** `./gradlew test` ejecuta el gold set y publica las métricas. Baseline guardado.

---

## Fase 1 — Integridad de la señal: que `es_oportunidad_ibm=true` signifique algo

**Objetivo:** que ninguna fila se presente como oportunidad sin criterio, y que los errores no se disfracen de éxito.

| # | Tarea | Archivos | Relación |
|---|---|---|---|
| 1.1 | Añadir campo de **estado/origen** a la recomendación: `PRESELECCION_VECTORIAL` \| `EVALUADA` \| `DESCARTADA_ALCANCE` \| `ERROR_EVALUACION`, más `fecha_evaluacion`. | `RecomendacionEntity`, `RecomendacionOportunidad`, `RecomendacionPersistenceAdapter`, migración DDL en `src/main/resources` | H1, H2 |
| 1.2 | Reemplazar el `justificacion.startsWith("Preselección...")` por consulta sobre `estado`. | `RecomendarOportunidadesService.recomendarPorOcid` | H2 |
| 1.3 | Aplicar `ValidadorAlcanceIbmService.validar()` dentro de `generarRecomendacionRapidaVectorial` antes de escribir `esOportunidadIbm=true`. | `RecomendarOportunidadesService` | H1, H4 |
| 1.4 | En `AnalizarSimilitudService.analizar()`: si el proceso **no** es elegible y tenía similitudes persistidas, eliminarlas y marcar la recomendación como `DESCARTADA_ALCANCE` con el motivo (hoy solo `continue`). | `AnalizarSimilitudService`, `SimilitudPersistencePort` (nuevo `eliminarPorOcid`) | H4 |
| 1.5 | Fallback del LLM: en vez de `esOportunidadIbm = !candidatosTop.isEmpty()`, persistir `estado=ERROR_EVALUACION`, `esOportunidadIbm=false` y **no** tratarlo como definitivo (permitir reintento en `recomendarPorOcid` y en el scheduler). | `RecomendarOportunidadesService` líneas 266-276 | H3 |
| 1.6 | Lookup por OCID: añadir `ProcesoSercopPersistencePort.buscarPorOcid(String)` (+ JPQL/`findByOcid`) y sustituir los tres `obtenerTodos()+filter`. | `ProcesoSercopPersistencePort`, `ProcesoSercopJpaRepository`, `ProcesoSercopPersistenceAdapter`, `RecomendarOportunidadesService` | H5 |
| 1.7 | Decisión de producto (ver pregunta abierta A): el endpoint `GET /recomendaciones` debe distinguir preselecciones de evaluadas (filtro por `estado` y/o campo nuevo en la respuesta). | `AnalysisController`, `RecomendacionJpaRepository.findOportunidadesIbm`, `MCP_SERVER/src/index.ts` | H1 |

**Criterio de aceptación:** test nuevo que (a) una preselección nunca sale sin pasar el validador, (b) un proceso hoy-elegible que deja de serlo pierde sus similitudes, (c) un error del LLM no produce `esOportunidadIbm=true`, (d) la latencia de `/recomendaciones/{ocid}` no escala con el total de procesos. Medir con el gold set: sin regresión de recall.

---

## Fase 2 — Scoring y ranking coherente

**Objetivo:** que el orden de la lista refleje "qué atacar primero".

| # | Tarea | Archivos | Relación |
|---|---|---|---|
| 2.1 | Unificar la fórmula. Decidir si el peso es 0.70 + `bonusAlcance` (código) o 0.75 sin alcance (README), dejarlo **configurable** y corregir el documento que quede desalineado. Si cambia el peso, recalcular `score_compuesto` (hay precedente en `docs/migracion_escala_coseno.sql`). | `AnalizarSimilitudService` (mover constantes a `ConfiguracionCriteriosOportunidad`), `README_SIMILITUD.md` §3 | H6 |
| 2.2 | Normalizar el bonus de método con una tabla configurable mapeando los valores reales de SERCOP → bonus, y poner `metodosPermitidos` a uso real (o eliminarlo). | `ConfiguracionCriteriosOportunidad`, `AnalizarSimilitudService.calcularBonusMetodo`, `application.yaml` | H7 |
| 2.3 | Política para monto nulo: no descartar en automático, pero sí marcar `montoConocido=false` y hacer que el bonus/puntuación lo refleje; exponerlo en la recomendación. | `ValidadorAlcanceIbmService`, `RecomendacionOportunidad` | H8 |
| 2.4 | Ranking compuesto en la lista: priorizar `estado=EVALUADA` sobre `PRESELECCION` y ponderar por confianza (factor configurable: ALTO 1.0 / MEDIO 0.85 / BAJO 0.6). Alternativa mínima: dos bloques en la respuesta (evaluadas primero). | `RecomendacionJpaRepository.findOportunidadesIbm` | H9 |
| 2.5 | Mover los umbrales de la preselección (0.75/0.68/delta) a configuración y calibrarlos con el gold set de la Fase 0. | `ConfiguracionCriteriosOportunidad`, `RecomendarOportunidadesService` líneas 119-126 | H1 |
| 2.6 | Invalidación por catálogo: incluir una `version_catalogo` (hash o `count + max(fecha)` de `producto_ibm`) en el hash de similitud y limpiar `recomendacion` cuando cambie. | `AnalizarSimilitudService.calcularHash`, `CatalogoIbmBootstrapService`, `README_SIMILITUD.md` §3 | H13 |
| 2.7 | *(Solo si el gold set lo pide)* diversificar el top-5 para que no sean 5 productos de la misma familia (MMR simple sobre `score_vectorial`). | `AnalizarSimilitudService` | opcional |

**Criterio de aceptación:** `./gradlew test` verde; corrida del gold set con precisión ≥ baseline; el top-6 de `GET /recomendaciones` no contiene preselecciones sin evaluar por encima de evaluadas con confianza ALTO.

---

## Fase 3 — Contrato enriquecido: qué le llega a Bob

**Objetivo:** que Bob razone con todos los hechos, no con un recorte.

| # | Tarea | Archivos | Relación |
|---|---|---|---|
| 3.1 | Ampliar `CandidatosRecomendacionDto` con: `metodo`, `fecha` + `diasDesdePublicacion` (existe `ProcesoSercop.fecha`), `scoreCompuesto` por candidato, `bonusAplicados`, `resultadoValidacion` (elegible, motivo, bonusAlcance) y `recomendacionPrevia` (estado + confianza + fecha). | `CandidatosRecomendacionDto`, `RecomendarOportunidadesService.obtenerCandidatosCrudos` | H10 |
| 3.2 | Ejecutar el validador también en `/candidatos/{ocid}` y devolver `elegible:false` + motivo, para que Bob comunique el descarte en lugar de inventarlo. | `RecomendarOportunidadesService.obtenerCandidatosCrudos` | H10 |
| 3.3 | Devolver los 5 candidatos con su `scoreCompuesto`, no solo la similitud vectorial. | `CandidatosRecomendacionDto.CandidatoConDetalle` | H10 |
| 3.4 | Exponer el descarte en la lista: añadir `motivoDescarte`/`estado` a la respuesta de `GET /recomendaciones` (transparencia: por qué algo no es oportunidad). | `RecomendacionOportunidad`, `AnalysisController` | H11 |

**Criterio de aceptación:** `curl /candidatos/{ocid}` devuelve proceso + validador + top-5 con score compuesto; un OCID descartado explica el motivo.

---

## Fase 4 — Presentación: cómo responde Bob (MCP)

**Objetivo:** que la respuesta final sea accionable y honesta sobre su nivel de certeza.

| # | Tarea | Archivos | Relación |
|---|---|---|---|
| 4.1 | Reescribir el prompt de `obtener_recomendacion_por_ocid` con plantilla ejecutiva fija: **Veredicto** (sí/no + confianza) · **Por qué encaja** (2-3 bullets citando el texto del pliego) · **Producto IBM recomendado** · **Estrategia comercial** (venta directa / consorcio con partners / largo plazo) · **Condiciones y riesgos** (monto, método, región, competencia, plazos) · **Siguiente paso** concreto. | `MCP_SERVER/src/index.ts` (líneas 61-92) | H10, H11 |
| 4.2 | Reglas de honestidad en el prompt: si `estado=PRESELECCION_VECTORIAL` → "evaluación profunda pendiente"; si `ERROR_EVALUACION` → reportar el fallo y no inventar; nunca presentar una preselección como veredicto. | `MCP_SERVER/src/index.ts` | H1 |
| 4.3 | Enriquecer `obtener_todas_las_recomendaciones`: incluir `titulo`, `region`, `fecha`/días restantes, `score_compuesto`, `estado` y confianza, en vez de `justificacion.slice(0,200)`. Arreglar los textos que asumen Granite en modo Bob. | `MCP_SERVER/src/index.ts` (107-162) | H11 |
| 4.4 | Nueva herramienta MCP + endpoint REST con **filtros**: producto/tema, rango de monto, región, método, confianza y estado (evaluadas / pendientes). | `AnalysisController` (GET `/recomendaciones?...`), `RecomendacionJpaRepository`, `MCP_SERVER/src/index.ts` | H12 |
| 4.5 | Verificar que `EVALUADOR=granite` sigue leyendo `/recomendaciones/{ocid}` con los campos nuevos y no se rompe. | `MCP_SERVER/src/index.ts` (49-55) | coherencia |

**Criterio de aceptación:** conversación de prueba en Bob: (1) resumen de la lista legible y accionable, (2) profundizar un OCID da la plantilla completa, (3) un OCID descartado lo explica con motivo, (4) una preselección se declara pendiente.

---

## Fase 5 — Verificación continua (corre en paralelo con todo)

| # | Tarea | Detalle |
|---|---|---|
| 5.1 | Ampliar unitarios | `ValidadorAlcanceIbmServiceTest` (monto nulo, exclusiones nuevas), `ProcesoSercopPipelineServiceTest` (idempotencia), nuevo `RecomendendarOportunidadesServiceTest` (preselección validada, fallback, reintento). |
| 5.2 | Suite de calidad | Ejecutar el gold set de la Fase 0 en cada fase y comparar con el baseline. |
| 5.3 | Verificación manual | Curls de `README_FUNCIONALIDAD.md` + `GET /procesos/estado-pipeline` tras cada fase. |
| 5.4 | Build completo | `./gradlew test` y `cd MCP_SERVER && npm run build`. |
| 5.5 | Actualizar docs | `README_SIMILITUD.md` (fórmula y estado de recomendación), `README_ARQUITECTURA_EVALUACION.md` (preselección vs evaluación), este plan. |

---

## Orden sugerido y dependencias

```
Fase 0 (baseline)  ──►  Fase 1 (integridad)  ──►  Fase 2 (scoring/ranking)
                                                        │
                              Fase 3 (contrato) ◄───────┘
                                    │
                                    ▼
                              Fase 4 (MCP/Bob)
Fase 5 corre desde el día 1
```

Fases 3 y 4 pueden avanzar en paralelo con la Fase 2 si se entrega primero el DTO de la Fase 3.

---

## Preguntas abiertas (a decidir antes de codificar la Fase 1)

**A. ¿Qué ve el usuario en `GET /recomendaciones` mientras el pipeline aún no ha evaluado?**
1. Solo recomendaciones evaluadas (`estado=EVALUADA`) y las preselecciones en un bloque/parámetro aparte → más honesto, pero la lista puede quedar corta al principio.
2. Todo mezclado pero con `estado` visible en cada item → más simple, el usuario aprende a leerlo.
3. Solo evaluadas y, si no hay, responder "procesando" con el avance → mejor experiencia, más trabajo.

**B. ¿El orden de la lista prioriza confianza o score compuesto?** (ver 2.4: hoy solo score, y un BAJO puede encabezar).

**C. ¿Fórmula de score definitiva?** 0.70 + `bonusAlcance` (actual en código) u 0.75 sin alcance (README). Si se cambia, hay que recalcular los `score_compuesto` guardados.

**D. ¿Se exponen los descartes al usuario?** (transparencia: "por qué no es oportunidad") o solo las oportunidades.
