# Plan para dar buenas recomendaciones al usuario

**Alcance acordado:** pipeline (calidad de la señal) + presentación (qué ve y cómo responde el usuario).
**Modo de decisión:** Bob (asistente externo, `EVALUADOR=bob`). Granite sigue funcionando, pero el plan está pensado para que el contrato de datos sirva a ambos.

**Dependencia externa:** `sercop_ingestion_service` (MS1) ingiere el `.zip` en la tabla compartida `proceso_contratacion`. Este servicio no toca la ingesta; solo lee.

---

## Decisiones tomadas

| ID | Decisión |
|---|---|
| **A** | `GET /recomendaciones` devuelve **solo** recomendaciones con `estado=EVALUADA`. Las preselecciones se exponen aparte (`?estado=preseleccion` o endpoint `/recomendaciones/pendientes`). |
| **B** | El ranking combina **estado + confianza + score compuesto**: evaluadas primero, y dentro de cada bloque factor configurable por confianza (ALTO 1.0 / MEDIO 0.85 / BAJO 0.6) sobre `score_compuesto`. |
| **C** | Fórmula definitiva: **`0.70·coseno + bonusMonto + bonusMétodo + bonusAlcance`** (la que ya está en el código y persistida). Se corrige `README_SIMILITUD.md`; no hay que recalcular scores. |
| **D** | **No** se exponen descartes en los listados: solo oportunidades (`esOportunidadIbm=true`). *Matiz:* el detalle on-demand de un OCID concreto sí conserva el motivo (`/recomendaciones/{ocid}` en modo Granite), para que Bob no invente razones — pero nunca aparece en listas ni en el resumen del MCP. |

---

## Protocolo de trabajo (aplica a cada una de las 6 fases)

Para cada fase se ejecutan tres pasos obligatorios:

1. **Antes de tocar código:** presentar la situación (qué problema hay, por qué afecta a la calidad de la recomendación cuando un proceso SERCOP es o no oportunidad real de IBM) y las opciones disponibles para resolverla, con pros/contras de cada una.
2. **Preguntar todo lo que no esté claro.** Si una decisión cambia el comportamiento visible (qué ve el usuario, qué significa `esOportunidadIbm`, cómo se ordena, qué se muestra de los descartes, criterios de elegibilidad), **no se decide solo**: se explica y se pregunta antes de implementar.
3. **Al terminar la fase:** resumen de lo implementado — archivos modificados/creados, decisiones tomadas durante la ejecución, cómo verificarlo (curl/test) y qué queda pendiente o incompleto.

Criterio rector de todas las fases: **mejorar la acierto de "¿este proceso SERCOP es realmente una oportunidad de negocio para IBM?"**, medido contra el gold set de la Fase 0.

---

## Diagnóstico medido (2026-10-21, `sercop_db`)

Cifras reales que cuantifican los hallazgos:

| Medida | Valor | Lectura |
|---|---|---|
| Procesos en `proceso_contratacion` | **381** | base de trabajo |
| Recomendaciones en `recomendacion` | **46** | 24 `true` / 22 `false` |
| De las 24 `true`, **cuántas son preselección heurística** | **23 (96 %)** | **H1 confirmado y cuantificado:** casi todo lo que hoy se muestra como "oportunidad para IBM" no ha pasado por LLM ni por Bob. Solo 1 está realmente evaluada. |
| Filas `esOportunidadIbm=true` con `nivelConfianza='N/A'` | **1** (`ocds-5wno2w-SIE-UITEY-2026-034-541976`) | Inconsistencia: las reglas del prompt dicen `N/A ⇒ false`. Hallazgo **H14**. |
| Distribución de `metodo` | `open` 290, `direct` 91 | **H7 agravado:** no existe ningún `infima_cuantia`, así que `metodosDescartados` **no descarta ni un proceso**; y el `default → 0.05` del switch **nunca se ejecuta**. |
| Montos | ≥15k: 295 · <15k: 86 · **sin monto: 34** | 34 procesos (9 %) no pasan ni el filtro de monto ni lo marcan como desconocido → H8. |
| Recomendaciones `false` evaluadas | 22 | Son descartes reales del LLM/Bob, buen material de contraste para el gold set. |
| **Base 100 % TI** | `categoria_cpc` ∈ {LICENCIAS_SOFTWARE 127, SUSCRIPCIONES_SAAS 107, BASES_DATOS_PLATAFORMAS 64, DESARROLLO_CONSULTORIA_TI 44, CLOUD_COMPUTING 39} — CHECK constraint | **H15:** MS1 ya filtra por categoría en la ingesta. |
| Procesos con palabras clave excluidas | **0 de 381** | **H15:** `palabrasClaveExcluidas` no descarta **nada**. |
| Procesos con método excluido (`infima_cuantia`) | **0 de 381** | **H15:** `metodosDescartados` tampoco. Los "filtros duros" del README no están probados por ningún dato. |
| Procesos que mencionan un producto IBM por nombre | **2 de 381** | Cero ventas fáciles: la recomendación depende de la similitud semántica + el juicio del evaluador. |
| Procesos que mencionan competidor **sin** mencionar IBM | **40 (10 %)** | La regla "depende del espacio real" (Q4 del criterio) es la frontera de mayor impacto. |
| `tipo_interno` contiene el **método** de contratación en español ("Concurso público", "Subasta Inversa Electrónica"), no una categoría | 381 | **H16:** `generarTextoEmbedding` lo etiqueta como `Categoria: {tipoInterno}` → el texto que se vectoriza miente sobre un campo. Afecta a la calidad del embedding. |
| Mojibake en nombres de producto del catálogo | ej. `Sterling B2B Integration SaaSâ​` | **H17:** texto corrupto que se guarda, se vectoriza y se le entrega a Bob. |
| **Keyword `"ia"` de `palabrasClaveEnterprise` buscada por subcadena** | casa dentro de `licen**cia**s`, `licen**cia**miento`, `ofimá**tica**` → cualquier proceso de licencias suma +1 keyword "enterprise" automáticamente | **H18 (medido 2026-10-21 sobre los 42 keywords, bloques S6 del gold set):** la excepción "monto <15k con ≥2 keywords enterprise" está inflada; dos procesos de 12k y 14,8k "pasan" Q2 solo por esa coincidencia. |
| Encaje real del top-3 sobre casos del gold set | ej. "Renovación de licencias Microsoft 365" → MaaS360 / Trusteer / Sterling; "licencia Office 365" → Sterling / OpenShift / AIX | Señal de que el ranking actual falla exactamente donde el criterio exige decidir (estrato S2/S3). Es lo que el baseline de la Fase 0 va a cuantificar. |

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

| # | Tarea | Archivos | Estado |
|---|---|---|---|
| 0.0 | **Criterio escrito de oportunidad IBM** (marco de catálogo de software, no "¿es TI?"), con las decisiones del usuario, familias F1-F8 y casos borde. | `docs/criterio_oportunidad_ibm.md` | ✅ 2026-10-21 — **v1.3**, 30 decisiones registradas en §7; queda **1 pendiente** (contratación `direct`) |
| 0.1 | Gold set: **63** procesos reales etiquetados por el usuario en bloques interactivos — muestra base 55 estratificada (S1 12 / S2 12 / S3 8 / S4 8 / S5 8 / S6 7) + espiga S7 (8 candidatos a ALTO buscados fuera de la muestra). | `src/test/resources/goldset_oportunidades.csv`, `src/test/resources/generar_goldset.sql` | ✅ 2026-10-21 — **63/63 etiquetados**: ALTO 3 · MEDIO 8 · BAJO 0 · NO 52 |
| 0.2 | Test de calidad que compara el gold set contra `recomendacion` en la BD: cobertura, precisión sobre evaluados, recall de cobertura, matriz etiqueta vs confianza y listado de falsos positivos/negativos. `@Tag("local")`, se omite si la BD no está disponible. | `src/test/java/.../CalidadRecomendacionesTest.java` | ✅ 2026-10-21 — verificado; ajustado para validar base=55 y espiga S7 por separado |
| 0.3 | Registrar el baseline en este documento. | este archivo, sección "Baseline" | ✅ 2026-10-21 — baseline medido abajo |

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
| 1.7 | Aplicar **decisión A**: `GET /recomendaciones` pasa a devolver solo `estado=EVALUADA`; añadir `?estado=preseleccion` (o endpoint `/recomendaciones/pendientes`) para las preselecciones, y que el MCP lo distinga. | `AnalysisController`, `RecomendacionJpaRepository.findOportunidadesIbm`, `MCP_SERVER/src/index.ts` | H1 |
| 1.8 | Rehacer el relleno perezoso de `obtenerOportunidadesIbm`: hoy, ante un cache miss, genera preselecciones y vuelve a leer — con la decisión A esas filas quedarían filtradas y el endpoint devolvería una lista corta o vacía sin explicación. En su lugar: disparar evaluación LLM **asíncrona** de los pendientes (mismo patrón que `precalcularRecomendacionesAsync`) y devolver lo disponible + `pendientesCount` para que el usuario sepa que se está procesando. | `RecomendarOportunidadesService.obtenerOportunidadesIbm`, `RecomendacionJpaRepository` (nuevo conteo de pendientes) | H1, sin lista corta sin explicar |
| 1.9 | Consistencia de la etiqueta: rechazar/persistir como `ERROR_EVALUACION` cualquier combinación inválida (`esOportunidadIbm=true` con `nivelConfianza='N/A'`, que hoy existe — H14) y añadir una **restricción** que lo impida (CHECK o validación en `RecomendacionPersistenceAdapter`). | `RecomendacionOportunidad`, `RecomendacionPersistenceAdapter`, migración DDL | H14 |
| 1.10 | Revisión de `metodosDescartados` y `calcularBonusMetodo` con los valores reales: la muestra solo tiene `open` (290) y `direct` (91), ningún `infima_cuantia` — verificar si el filtro de método existe para otro feed o está muerto, y si `metodosPermitidos`/`default 0.05` deben existir. *Decisión del usuario al llegar la fase.* | `ConfiguracionCriteriosOportunidad`, `AnalizarSimilitudService` | H7 ampliado |
| 1.11 | **H18:** matchear `palabrasClaveEnterprise` por **límites de palabra** (y normalizando acentos) en vez de `String.contains`, empezando por la keyword `"ia"` que casa dentro de *licen**cia**s / licen**cia**miento / ofimá**tica***. Recontar cuántos procesos superan realmente Q2 con ≥2 keywords y comparar con el conteo actual (medir con el gold set antes/después). | `ConfiguracionCriteriosOportunidad`, método que evalúa `palabrasClaveEnterprise` | H18, corte de monto |

**Criterio de aceptación:** test nuevo que (a) una preselección nunca sale sin pasar el validador, (b) un proceso hoy-elegible que deja de serlo pierde sus similitudes, (c) un error del LLM no produce `esOportunidadIbm=true`, (d) la latencia de `/recomendaciones/{ocid}` no escala con el total de procesos, (e) `GET /recomendaciones` sin parámetros nunca devuelve filas `PRESELECCION_VECTORIAL` (esas solo vía `?estado=preseleccion`) y, cuando devuelve menos de `limit` por falta de evaluadas, lo indica con `pendientesCount` en lugar de devolver una lista corta sin explicación. Medir con el gold set: sin regresión de recall.

---

## Fase 2 — Scoring y ranking coherente

**Objetivo:** que el orden de la lista refleje "qué atacar primero".

| # | Tarea | Archivos | Relación |
|---|---|---|---|
| 2.1 | Aplicar **decisión C**: mantener `0.70·coseno + bonusMonto + bonusMétodo + bonusAlcance`, sacar los pesos a constantes/configurables y **corregir `README_SIMILITUD.md`** (§3 fórmula, §3 ejemplo numérico con 0.75 y cualquier mención de 0.65/0.75). No hace falta recalcular `score_compuesto` persistido: ya está con 0.70. **Ver igualmente con SQL** que las filas guardadas realmente cumplan 0.70 (las creadas antes de los cambios de fórmula guardan 0.65·coseno o la versión 0.75): si hay filas con escala vieja, corregirlas con un `UPDATE` puntual y documentarlo en `docs/`. | `AnalizarSimilitudService` (mover constantes a `ConfiguracionCriteriosOportunidad`), `README_SIMILITUD.md` §3 | H6 |
| 2.2 | Normalizar el bonus de método con una tabla configurable mapeando los valores reales de SERCOP → bonus, y poner `metodosPermitidos` a uso real (o eliminarlo). | `ConfiguracionCriteriosOportunidad`, `AnalizarSimilitudService.calcularBonusMetodo`, `application.yaml` | H7 |
| 2.3 | Política para monto nulo: no descartar en automático, pero sí marcar `montoConocido=false` y hacer que el bonus/puntuación lo refleje; exponerlo en la recomendación. | `ValidadorAlcanceIbmService`, `RecomendacionOportunidad` | H8 |
| 2.4 | Aplicar **decisión B** en `findOportunidadesIbm`: (1) primero `estado=EVALUADA` (obligatorio por decisión A), (2) dentro del bloque, ordenar por `score_compuesto × factor_confianza` con factores configurables (ALTO 1.0 / MEDIO 0.85 / BAJO 0.6). Mantener el SQL como consulta nativa y exponer el `score_orden` calculado en la respuesta. | `RecomendacionJpaRepository.findOportunidadesIbm`, `ConfiguracionCriteriosOportunidad` | H9 |
| 2.5 | Mover los umbrales de la preselección (0.75/0.68/delta) a configuración y calibrarlos con el gold set de la Fase 0. | `ConfiguracionCriteriosOportunidad`, `RecomendarOportunidadesService` líneas 119-126 | H1 |
| 2.6 | Invalidación por catálogo: incluir una `version_catalogo` (hash o `count + max(fecha)` de `producto_ibm`) en el hash de similitud y limpiar `recomendacion` cuando cambie. | `AnalizarSimilitudService.calcularHash`, `CatalogoIbmBootstrapService`, `README_SIMILITUD.md` §3 | H13 |
| 2.7 | *(Solo si el gold set lo pide)* diversificar el top-5 para que no sean 5 productos de la misma familia (MMR simple sobre `score_vectorial`). | `AnalizarSimilitudService` | opcional |
| 2.8 | Corregir `generarTextoEmbedding`: hoy escribe `Categoria: {tipoInterno}` pero `tipo_interno` es el **método** de contratación → cambiar a `Metodo: {tipoInterno}` (o eliminarlo, ya que `metodo` open/direct ya se usa en el bonus). **[DECIDIR: al cambiar el texto hay que re-vectorizar los 381 procesos y invalidar hashes — decisión del usuario al llegar la fase.]** | `GenerarEmbeddingCandidatoService.generarTextoEmbedding` (línea ~109), `README_SIMILITUD.md` §1 | H16 |

**Criterio de aceptación:** `./gradlew test` verde; corrida del gold set con precisión ≥ baseline; el top-6 de `GET /recomendaciones` contiene solo `estado=EVALUADA` y su orden coincide con `score_compuesto × factor_confianza`.

---

## Fase 3 — Contrato enriquecido: qué le llega a Bob

**Objetivo:** que Bob razone con todos los hechos, no con un recorte.

| # | Tarea | Archivos | Relación |
|---|---|---|---|
| 3.1 | Ampliar `CandidatosRecomendacionDto` con: `metodo`, `fecha` + `diasDesdePublicacion` (existe `ProcesoSercop.fecha`), `scoreCompuesto` por candidato, `bonusAplicados`, `resultadoValidacion` (elegible, motivo, bonusAlcance) y `recomendacionPrevia` (estado + confianza + fecha). | `CandidatosRecomendacionDto`, `RecomendarOportunidadesService.obtenerCandidatosCrudos` | H10 |
| 3.2 | Ejecutar el validador también en `/candidatos/{ocid}` y devolver `elegible:false` + motivo, para que Bob comunique el descarte en lugar de inventarlo. | `RecomendarOportunidadesService.obtenerCandidatosCrudos` | H10 |
| 3.3 | Devolver los 5 candidatos con su `scoreCompuesto`, no solo la similitud vectorial. | `CandidatosRecomendacionDto.CandidatoConDetalle` | H10 |
| 3.4 | **Decisión D (no exponer descartes en listados):** no se agrega `motivoDescarte` a `GET /recomendaciones`; la lista sigue siendo solo oportunidades. El motivo queda reservado al detalle on-demand de un OCID concreto (`GET /recomendaciones/{ocid}` en modo Granite y `resultadoValidacion` en `/candidatos/{ocid}`) para que Bob no invente razones cuando el usuario insiste en un caso. | `CandidatosRecomendacionDto` (3.1-3.2), `RecomendarOportunidadesService` | H10 |
| 3.5 | Saneamiento de caracteres en los nombres del catálogo (`Sterling B2B Integration SaaSâ​`, `Sterling Secure File Transferâ​`): normalizar al cargar (`CargarCatalogoIbmService`) **y** corregir las filas ya persistidas, porque ese texto llega al prompt de Bob. | `CargarCatalogoIbmService`, SQL de corrección en `docs/` | H17 |

**Criterio de aceptación:** `curl /candidatos/{ocid}` devuelve proceso + validador + top-5 con score compuesto; un OCID descartado explica el motivo.

---

## Fase 4 — Presentación: cómo responde Bob (MCP)

**Objetivo:** que la respuesta final sea accionable y honesta sobre su nivel de certeza.

| # | Tarea | Archivos | Relación |
|---|---|---|---|
| 4.1 | Reescribir el prompt de `obtener_recomendacion_por_ocid` con plantilla ejecutiva fija: **Veredicto** (sí/no + confianza) · **Por qué encaja** (2-3 bullets citando el texto del pliego) · **Producto IBM recomendado** · **Estrategia comercial** (venta directa / consorcio con partners / largo plazo) · **Condiciones y riesgos** (monto, método, región, competencia, plazos) · **Siguiente paso** concreto. | `MCP_SERVER/src/index.ts` (líneas 61-92) | H10, H11 |
| 4.2 | Reglas de honestidad en el prompt: si `estado=PRESELECCION_VECTORIAL` → "evaluación profunda pendiente"; si `ERROR_EVALUACION` → reportar el fallo y no inventar; nunca presentar una preselección como veredicto. | `MCP_SERVER/src/index.ts` | H1 |
| 4.3 | Enriquezar `obtener_todas_las_recomendaciones`: incluir `titulo`, `region`, `fecha`/días restantes, `score_compuesto` calculado con el factor de confianza y `estado=EVALUADA` (por decisión A la herramienta ya no debe filtrar `r.esOportunidadIbm` en cliente, el backend lo garantiza), en vez de `justificacion.slice(0,200)`. Arreglar los textos que asumen Granite en modo Bob y añadir `pendientesCount` cuando la lista venga corta. | `MCP_SERVER/src/index.ts` (107-162) | H11 |
| 4.4 | Nueva herramienta MCP + endpoint REST con **filtros**: producto/tema, rango de monto, región, método, confianza y estado (evaluadas / preselecciones pendientes — el parámetro `estado` de la decisión A). | `AnalysisController` (GET `/recomendaciones?...`), `RecomendacionJpaRepository`, `MCP_SERVER/src/index.ts` | H12 |
| 4.5 | Verificar que `EVALUADOR=granite` sigue leyendo `/recomendaciones/{ocid}` con los campos nuevos y no se rompe. | `MCP_SERVER/src/index.ts` (49-55) | coherencia |

**Criterio de aceptación:** conversación de prueba en Bob: (1) resumen de la lista legible y accionable (solo evaluadas, con `pendientesCount` si falta), (2) profundizar un OCID da la plantilla completa, (3) si el usuario pregunta por un OCID puntual descartado, Bob lo explica con el motivo real sin inventarlo (no aparece en listas), (4) una preselección se declara pendiente de evaluación.

---

## Fase 5 — Verificación continua (corre en paralelo con todo)

| # | Tarea | Detalle |
|---|---|---|
| 5.1 | Ampliar unitarios | `ValidadorAlcanceIbmServiceTest` (monto nulo, exclusiones nuevas), `ProcesoSercopPipelineServiceTest` (idempotencia), nuevo `RecomendarOportunidadesServiceTest` (preselección validada, fallback, reintento). |
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

## Decisiones A-D

Ya resueltas y aplicadas al plan: ver sección **Decisiones tomadas** al inicio del documento.

---

## Baseline (Fase 0 — medido 2026-10-21)

Reproducir: `./gradlew test --tests 'com.ibm.opportunity_analysis_service.CalidadRecomendacionesTest'`
Gold set: **63 procesos** (base 55 + espiga 8), **63/63 etiquetados** → **11 oportunidades reales** (3 ALTO + 8 MEDIO) y **52 NO**.

| Métrica | Valor inicial (baseline) |
|---|---|
| Cobertura (¿cuántos tienen recomendación en BD?) | **11,1 %** — 7 de 63 |
| Recomendaciones marcadas `esOportunidadIbm=true` | 5 |
| **Precisión sobre evaluados** (de las que el sistema dice que son oportunidad, cuántas lo son) | **0,0 %** — 0 verdaderos / **5 falsos positivos** |
| **Recall de cobertura** (de las 11 oportunidades reales, cuántas detecta) | **0,0 %** — 0 de 11; 10 de ellas **aún sin evaluar** |
| Acierto del producto IBM recomendado | sin dato: no hay ni un verdadero positivo |
| Distribución real ALTO / MEDIO / BAJO | 3 / 8 / 0 |
| Falsos positivos (los 5, todos `Preselección por afinidad vectorial`, score ≈0,653-0,657, delta 0,014-0,041) | ANDEC (M365) · UNACH (Adobe) · GADPI (ingeniería civil) · GADMLM (consultoría ciberseguridad) · RPM (agente IA + BI libre) |
| Falsos negativos (11 oportunidades reales sin detectar) | SENAE (Palantir+IaaS, 17,2M, dice `N/A`) · Parlamento Andino · Balzar · EEPGSA (Oracle) · ESPE (NDR) · EERSSA (chatbot) · Min. Gobierno · ARCSA · **U. Cuenca (ALTO)** · **UTN (ALTO)** · **UEMPRENDE (ALTO)** |
| Matriz etiqueta humana vs `nivelConfianza` | ALTO → `SIN_RECOMENDACION`=3 · MEDIO → `N/A`=1, `SIN_RECOMENDACION`=7 · NO → `BAJO`=1, `MEDIO`=4, `N/A`=1, `SIN_RECOMENDACION`=46 |
| Latencia `GET /recomendaciones/{ocid}` | pendiente de medir (tarea 1.6/H5) |

**Lectura del baseline:** el sistema no acierta ni una. Las 5 que publica como oportunidad son preselecciones vectoriales sin evaluar (5/5 falsas), y las 11 oportunidades reales no las detecta ninguna (10 porque nunca llegaron al LLM, 1 — SENAE — porque el LLM las descartó). El error no está en "apuntar mal", sino en **publicar la preselección sin pasar por el criterio** (H1), que es exactamente lo que ataca la Fase 1.

---

## Punto de partida

Estado: **Fase 0 completada** (criterio v1.3 + gold set 63/63 + baseline medido, tarea 0.3). Siguiente: **Fase 1** (integridad de la señal: que `es_oportunidad_ibm=true` signifique algo) — corrige H1-H5, H14 y H18, y habilita las decisiones A y B. Antes de tocar código, cada tarea de la Fase 1 se presenta con sus opciones y se deciden con el usuario.
