# Flujo actual del sistema (estado ANTES de los cambios)

Descripción paso a paso de cómo entra un proceso SERCOP, cómo se clasifica como oportunidad y qué se envía a Granite.

---

## 0. Componentes

- **sercop_ingestion_service**: externo. Inserta los procesos en la tabla de procesos SERCOP (PostgreSQL).
- **opportunity_analysis_service** (este repo): Spring Boot. Calcula embeddings, similitudes y recomendaciones.
- **Ollama / Granite**: dos modelos.
  - `granite-embedding:278m` genera embeddings (vectores).
  - `granite3.1-dense:8b` evalúa oportunidades (LLM).
- **MCP_SERVER** (`MCP_SERVER/src/index.ts`): expone herramientas a Bob. Llama por HTTP a este servicio.

---

## 1. Arranque y disparadores

| Disparador | Qué hace | Frecuencia |
|---|---|---|
| `ProcesosStartupRunner` | Ejecuta la sincronización al iniciar la app. | Una vez |
| `ProcesosScheduler.ejecutarSincronizacionPeriodica` | Llama a `sincronizarProcesos()`. | Cada 60 s (`pipeline.scheduler.delay-ms`) |
| `ProcesosScheduler.ejecutarPrecalculoPeriodico` | Llama a `precalcularRecomendaciones(3)`. | Cada 300 s |
| `POST /procesos/sincronizar` | Sincronización manual. | Bajo demanda |
| `GET /recomendaciones` | Lista oportunidades (paginado). | Bajo demanda (lo usa el MCP) |
| `GET /recomendaciones/{ocid}` | Evaluación con Granite de un caso. | Bajo demanda |
| `GET /candidatos/{ocid}` | Datos crudos + top 5 productos IBM para que Bob razone. | Bajo demanda |

---

## 2. Etapa A: Sincronización (`ProcesoSercopPipelineService.sincronizarProcesos`)

Solo corre un hilo a la vez (`AtomicBoolean`). Si ya hay una ejecución, se descarta.

1. **Asegura el catálogo IBM** (`CatalogoIbmBootstrapService`): carga productos y sus embeddings si faltan.
2. **Lee todos los procesos** de la BD (`obtenerTodos()`).
3. Para **cada proceso**, dentro de un try/catch aislado:
   1. **Validación de alcance** (`ValidadorAlcanceIbmService.validar`):
      1. Si `qualification.habilitado = false`, todo es elegible.
      2. Se une título + descripción + tipo interno, se normaliza (minúsculas, sin tildes).
      3. **Palabras excluidas**: si el texto *contiene* alguna (cableado, tóner, mobiliario, etc.) → descartado.
      4. **Método descartado**: `infima_cuantia` → descartado.
      5. **Palabras enterprise**: cuenta cuántas de la lista aparecen como subcadena (`software`, `nube`, `ia`, `api`, `soc`…).
      6. **Monto mínimo** (15 000 USD): si el monto es menor y hay menos de 2 coincidencias enterprise → descartado.
      7. Si pasa, devuelve `bonusAlcance = min(0.10, coincidencias × 0.02)`.
   2. Si **no es elegible**: se cuenta como "al día" y se salta. No se guarda nada.
   3. **Hash SHA-256** de título|descripción|tipo|monto|método. Si coincide con el hash guardado en la tabla de similitudes → se salta (idempotencia).
   4. **Embedding del proceso** (`GenerarEmbeddingCandidatoService`):
      - Texto: `Licitación: {título}. Requerimiento: {descripción}. Categoria: {tipo}.`
      - Se envía a Ollama (`granite-embedding:278m`) y se guarda el vector.
   5. **Búsqueda vectorial**: top **5** productos IBM por similitud coseno (`buscarSimilares`).
   6. **Score compuesto** por cada uno de los 5 productos:
      ```
      scoreCompuesto = 0.70 × scoreVectorial
                     + bonusMonto    (monto/10M, máx 0.15)
                     + bonusMetodo   (open 0.10, direct 0.03, otro 0.05)
                     + bonusAlcance  (0 a 0.10)
      ```
   7. Se guardan 5 filas en `similitud_proceso_producto` (ocid, producto, scoreVectorial, scoreCompuesto, hash).
4. Si se procesó al menos un proceso nuevo, lanza `precalcularRecomendacionesAsync(5)` (hilo aparte).

Resultado: cada proceso elegible tiene 5 filas de similitud. Aún no hay ninguna "recomendación".

---

## 3. Etapa B: Generación de recomendaciones

Hay tres caminos que escriben en la tabla `recomendacion`.

### B1. Preselección vectorial (camino de `GET /recomendaciones`)

`RecomendarOportunidadesService.obtenerOportunidadesIbm(limit, offset)`:

1. Lee de `recomendacion` las filas con `es_oportunidad_ibm = true`, ordenadas por `score_compuesto DESC`, con `LIMIT/OFFSET`.
2. Si hay al menos `limit` filas, las devuelve.
3. Si no, toma hasta `limit × 3` OCIDs de `similitud_proceso_producto` que **aún no estén en `recomendacion`** (`findSiguientesAEvaluar`, ordenados por `MAX(score_compuesto)`).
4. Para cada uno, `generarRecomendacionRapidaVectorial` (**sin LLM**):
   - Toma el producto con mayor `scoreCompuesto`.
   - Calcula `maxScore`, `minScore`, `delta` sobre los `scoreVectorial` de los 5.
   - Confianza:
     - `maxScore ≥ 0.75` y `delta ≥ 0.04` → **ALTO**
     - `maxScore ≥ 0.68` o `delta ≥ 0.02` → **MEDIO**
     - si no → **BAJO**
   - Guarda con **`esOportunidadIbm = true`** y justificación `"Preselección por afinidad vectorial (Score…, Delta…)"`.
5. Vuelve a leer de la BD y devuelve.

### B2. Evaluación con Granite en segundo plano

`precalcularRecomendaciones(topN)` (la llama el scheduler con 3, y el pipeline con 5):

1. `findSiguientesAEvaluar(topN)`: los OCIDs con mejor score compuesto que no tienen recomendación.
2. Para cada OCID → `generarYPersistirRecomendacion` (ver sección 4).

### B3. Evaluación on-demand

`GET /recomendaciones/{ocid}` → `recomendarPorOcid`:

1. Si ya existe una recomendación cuya justificación **no** empieza con `"Preselección por afinidad vectorial"`, la devuelve (cache).
2. Si no existe o es solo preselección → `generarYPersistirRecomendacion` (sección 4).

---

## 4. Etapa C: Evaluación completa (`generarYPersistirRecomendacion`)

1. Busca el proceso por OCID.
2. **Vuelve a validar el alcance**. Si no es elegible, guarda `esOportunidadIbm = false`, confianza `N/A`, con el motivo de descarte, y termina.
3. Obtiene los candidatos IBM:
   - Si hay similitudes guardadas, usa esos 5 productos y sus `scoreVectorial`.
   - Si no, genera el embedding en el momento y busca los **3** más similares.
4. Carga los objetos `ProductoIbm` de esos candidatos.
5. Llama a `evaluadorOportunidadLlmPort.evaluarOportunidad(proceso, candidatos, scores)` (sección 5).
6. **Si lanza excepción**: fallback con `esOportunidadIbm = !candidatos.isEmpty()`, producto = el primero, confianza `BAJO`.
7. Completa título, descripción, comprador y monto del proceso.
8. Guarda en `recomendacion` junto con el mejor `scoreCompuesto` (se usa para ordenar el listado).

---

## 5. Qué se envía a Granite (`OllamaEvaluadorOportunidadAdapter`)

Endpoint: `POST {granite.url}/api/chat` en Ollama, modelo `granite3.1-dense:8b`.

**Parámetros**: `stream=false`, `temperature=0.1`, `num_predict=350`, `num_ctx=4096`, timeout de lectura 90 s. Un `Semaphore(1)` hace que solo se evalúe un caso a la vez. Se usa un esquema JSON de salida (`format`).

**Mensaje `system`**: rol de arquitecto de soluciones y líder comercial de IBM para compras públicas; debe rechazar falsos positivos por coincidencias léxicas genéricas.

**Mensaje `user`** (construido en `construirPrompt`):

1. **Datos del proceso**: OCID, título, descripción (o "no detallada"), tipo/categoría, comprador, método, monto referencial.
2. **Hasta 5 candidatos IBM**: nombre, similitud vectorial, descripción y capacidades.
3. **Diagnóstico de señal vectorial**:
   - Puntaje máximo y delta.
   - Alerta de "ruido semántico" si `delta < 0.035` y `max < 0.72`.
   - Alerta de ambigüedad si la descripción tiene menos de 40 caracteres.
4. **Criterios de alcance**: qué entra en el portafolio IBM y qué se descarta (internet, cableado, PCs, impresoras, correo básico, renovación monomarca de terceros).
5. **Reglas de confianza**: ALTO / MEDIO / BAJO / N/A (con `es_oportunidad_ibm=false` en N/A).

**Respuesta esperada** (JSON):

```json
{
  "es_oportunidad_ibm": true,
  "producto_recomendado": "…",
  "nivel_confianza": "ALTO | MEDIO | BAJO | N/A",
  "justificacion": "…",
  "estrategia_comercial": "…"
}
```

`parsearRespuesta` la convierte en `RecomendacionOportunidad`. Si el LLM no responde, da timeout o el JSON es inválido, `fallbackEvaluacion` marca `esOportunidadIbm = true` (con confianza MEDIO o BAJO según el score) siempre que haya candidatos.

---

## 6. Etapa D: Consumo desde Bob (MCP)

Variable `EVALUADOR` del MCP (en `application.yaml`: `granite.evaluador: bob`).

- **`obtener_todas_las_recomendaciones(offset)`**:
  - Llama a `GET /recomendaciones?limit=6&offset=N`.
  - Filtra `esOportunidadIbm`, y muestra ocid, comprador, monto, producto, confianza y los primeros 200 caracteres de la justificación.
  - Si está vacío: "No hay oportunidades IBM identificadas aún".
- **`obtener_recomendacion_por_ocid(ocid)`**:
  - `EVALUADOR = granite`: llama a `GET /recomendaciones/{ocid}` y devuelve el resultado de Granite.
  - `EVALUADOR = bob`: llama a `GET /candidatos/{ocid}` (proceso + top 5 productos con scores, sin evaluación) y arma un prompt para que **Bob** emita el veredicto. Ese veredicto **no se guarda**.
- Otras herramientas: sincronizar, estado del pipeline, precalcular, limpiar caché, cargar catálogo, embeddings, similitud.

---

## 7. Tablas implicadas

| Tabla | Contenido |
|---|---|
| procesos SERCOP | Datos de la licitación (los escribe la ingesta). |
| embedding candidato | Vector del proceso + texto usado + modelo. |
| producto IBM / embedding producto IBM | Catálogo y sus vectores. |
| `similitud_proceso_producto` | 5 filas por proceso: producto, `scoreVectorial`, `scoreCompuesto`, hash. |
| `recomendacion` | Veredicto por OCID: `es_oportunidad_ibm`, producto, confianza, justificación, estrategia, `score_compuesto`. |

---

## 8. Resumen del recorrido de un proceso

```
Ingesta inserta proceso
  → (cada 60 s) sincronizarProcesos
      → validarAlcance (palabras / método / monto)      ── no elegible → se ignora
      → hash sin cambios                                ── se ignora
      → embedding del proceso (granite-embedding)
      → top 5 productos IBM por coseno
      → 5 filas de similitud con scoreCompuesto
  → usuario pide /recomendaciones
      → si faltan: preselección vectorial, es_oportunidad_ibm = true  (sin LLM)
  → scheduler (cada 5 min, 3 casos) / pipeline (5 casos)
      → evaluación con Granite → veredicto guardado (puede sobrescribir la preselección)
  → usuario pide detalle de un OCID
      → Granite: evaluación persistida
      → Bob: datos crudos, veredicto no persistido
```
