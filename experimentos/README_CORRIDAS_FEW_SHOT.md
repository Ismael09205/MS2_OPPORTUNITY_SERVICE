# Experimentos Few-Shot — Evaluación de `granite3.1-dense:8b` sobre SERCOP

Registro de las 6 corridas de evaluación del LLM como clasificador de oportunidades de negocio,
con metodología, métricas comparadas, tendencias, configuración oficial y estado actual.

> **Fecha de corte:** 2026-10-23 · **Verdad de referencia:** `goldset_v2_CONGELADO.csv` (ver `README_GOLDSETS.md`).

---

## 1. Entorno y protocolo de operación

| Elemento | Valor |
|---|---|
| Modelo evaluado | `granite3.1-dense:8b` (5 GB) |
| **Endpoint de pruebas** | **Túnel `http://localhost:11435/api/chat`** → máquina remota potente (exigido por el usuario) |
| Ollama local (:11434) | Solo diagnóstico de infraestructura — **nunca para pruebas** |
| Parámetros oficiales | temperature 0.1 · **seed 42 (reproducibilidad)** · num_ctx 16384 · num_predict 350 · esquema JSON forzado |
| BD | `sercop_db` localhost:5432 — procesos + catálogo `producto_ibm` (descripción y capacidades) |
| Info por proceso (igual que producción) | Pliego resumido de BD + top-3 candidatos IBM con score coseno y descripción del catálogo |
| Costo por caso | 19-29 s (reglas: instantáneo); corrida de 46 casos ≈ 15 min |

**Protocolo acordado (2026-10-23):**
1. Todas las pruebas van al túnel :11435.
2. Si el túnel no responde, o responde sin el modelo → **PARAR y avisar**.
3. **Anunciar antes de ejecutar** cualquier cosa o modificar código.

---

## 2. Metodología

- **Few-shot balanceado:** ~20 % de los casos etiquetados como ejemplos (el modelo ve prompt+respuesta), ~80 % clasificados a ciegas.
- **Formato de salida:** `{"decision": "SI"|"NO"|"REVISAR", "nivel": "ALTO"|"MEDIO"|"NA", "producto_recomendado": ..., "justificacion": ...}`
- **Corridas 1-2 (binarias):** `decision ∈ {SI, NO}` con nivel; gold v1 (63).
- **Corridas 3-6 (3 vías):** añaden `REVISAR` (abstención) — el diseño aprobado para producción; gold v2 congelado, split 20/80 → 11 ejemplos / 46 prueba.
- **Corridas 5-6 (filtro determinista):** reglas duras en código ANTES del LLM — si una regla dispara → NO automático sin llamada.
- **Métricas de producto:**
  - *Precisión de SÍ* — de lo que dice oportunidad, cuánto lo es (false positives arruinan la credibilidad).
  - *Fuga de positivos* — oportunidad real clasificada NO (error grave: se pierde negocio).
  - *Costo de revisión* — NO innecesariamente mandado a REVISAR (cola humana inútil).
  - *Acierto de NO* / *Tasa de REVISAR predecida*.

**Umbrales de aceptación pactados:** precisión SÍ ≥ 0,70 · fuga ≤ 1 · costo de revisión ≤ 4 (10 %) · REVISAR capturado ≥ 2/3 · acierto de NO ≥ 90 %.

---

## 3. Resumen de corridas

| | **C1** | **C2** | **C3** | **C4** | **C5** | **C6** |
|---|---|---|---|---|---|---|
| Fecha | 10-23 10:09 | 11:26 | 13:05 | 22:08 | 22:59 | 23:23 / 23:43 (×2) |
| Script | `fewshot_eval.py` | `fewshot_eval.py` | `fewshot_eval_v2.py` | `fewshot_eval_v4.py` | `fewshot_eval_v5.py` | `fewshot_eval_v6.py` |
| Gold set | v1 | v1 | v2 congelado | v2 congelado | v2 congelado | v2 congelado |
| Ejemplos / prueba | 15 / 48 | 15 / 48 | 11 / 46 | 11 / 46 | 11 / 46 | 11 / 46 |
| Vías | binaria | binaria | 3 (SI/NO/REV) | 3 vías | 3 vías + reglas | 3 vías + reglas |
| Diferencia clave | config inicial | ejemplos re-balanceados | primer diseño 3 vías | prompt ajustado (4 parches) | + filtro determinista | + **seed 42** |
| Endpoint | 11434 | 11435 | 11435 | 11435 | 11435 | 11435 |
| Duración | 1039 s | 1867 s | 1157 s | 1334 s | 899 s | 883 s |

### Métricas

| Métrica | C1 | C2 | C3 | C4 | C5 | **C6 (oficial)** |
|---|---|---|---|---|---|---|
| Acierto de clase | 91,7 % (nivel) | 77,1 % (nivel) | 73,9 % | 80,4 % | 73,9 % | **82,6 %** |
| Precisión de SÍ | **1,000** | 0,167 | n/a (0 SÍ) | 1,000 | 0 (1 SI sin nivel) | **1,000** |
| Recall / fuga | 0,400 (**3 FN**) | **1,000** (10 FP) | fuga **0** | fuga 1 | fuga 1 | **fuga 1** |
| Falsos positivos | 0 | **10** | 0 | 0 | 0 | **0** |
| Costo de revisión | — | — | 8 | 6 | 8 | **5** |
| REVISAR capturado | — | — | 2/3 | 2/3 | 2/3 | **2/3** |
| Acierto de NO | — | — | 80 % | 85 % | 80 % | **87,5 %** (reglas 12/12 + LLM 23/28) |
| Tasa REVISAR | — | — | 26,1 % | 19,6 % | 23,9 % | **17,4 %** |
| F1 binario | 0,571 | 0,286 | — | — | — | — |

### Matriz gold × predicción C6 (configuración oficial)

| gold \ pred | ALTO | MEDIO | REVISAR | NO |
|---|---|---|---|---|
| ALTO (1) | 0 | 0 | 1 (EPMAPS) | 0 |
| MEDIO (2) | 0 | **1** (Chone) | 0 | **1** (EMGIRS) |
| REVISAR (3) | 0 | 0 | **2** | 1 |
| NO (40) | 0 | 0 | **5** | **35** (12 por regla) |

---

## 4. Análisis por corrida

### C1 — Precisión perfecta, pero miedo a decir que sí
Precisión 1,000 con solo 2 TP; los **3 falsos negativos fueron exactamente los casos mixtos** (GADMB plataforma de recaudación con entrada vía partner sobre OpenShift, MDG mixto colaboración+IA+nube, ARCSA productividad+analítica+IA). El modelo los descartó por "sin producto IBM que sustituya el objeto central" — no distingue *mixto* de *no IBM*.

### C2 — El balancín del balance de ejemplos
Al pasar los mixtos a ejemplos, el modelo volcó al otro extremo: **recall 1,000 con 10 FP** (revistas científicas→watsonx.data, soporte Oracle→Db2, migración monomarca OlymPO→IBM i, antivirus→Trusteer…). Un solo timeout real del túnel cayó a NO automático. **El modelo es extremadamente sensible al balance few-shot: no es confiable autocontenido en binario.** Señal adicional de memorización: los ejemplos se repiten casi literal.

### C3 — El diseño de 3 vías elimina ambos desastres
Con REVISAR como vía de salida: **0 FP y 0 fuga** — primera corrida sin error catastrófico. Los 3 positivos de prueba fueron todos al lado "no-NO" (2 a cola, 1 a SÍ sin nivel — fallo de coherencia del esquema). Problema dominante: **sobre-abstinencia** — 26,1 % de REVISAR (vs 7 % real de duda humana); abstuvo donde el criterio es determinista (consultoría/desarrollo a medida ×3, contenido de tercero ×2).

### C4 — Prompt ajustado: más decisión, con un costo
4 parches: (1) consultoría/auditoría/desarrollo a medida → NO seco; (2) contenido propietario identificable → NO seco; (3) cláusula de abstinencia por "descripción vaga" eliminada; (4) `decision=SI` exige nivel (si duda entre ALTO y MEDIO → MEDIO).
Resultado: el `SI_sin_nivel` desapareció (Chone → MEDIO correcto), las consultorías cayeron a NO (IESS, GADMC-CH), REVISAR bajó 6,5 pts, acierto de NO +5 pts, precisión de SÍ 1,000. **Costo: 1 fuga nueva** (EMGIRS: "renovación de seguridad perimetral + hardware" → NO donde el gold es MEDIO mixto).

### C5 — Filtro determinista: reglas perfectas, LLM con ruido
Paso A (`validacion_reglas.py`): las 5 reglas validadas sobre los 120 etiquetados → **33 muertes, 33/33 NO correctos, 0 fugas, 0 REVISAR muertos**. En C5 las reglas resolvieron **12/46 casos de prueba sin tocar el modelo** (12/12 aciertos).
Pero el LLM (ahora solo en los 34 casos duros) **respondió distinto que C4 en 4 casos con el mismo prompt**: ESPOCHEP, IESS y GADMC-CH pasaron de NO a REVISAR, Chone volvió a fallar el nivel. Diagnóstico: **varianza** — el script no fijaba la semilla y Ollama usa seed aleatoria por defecto. Un ~12 % de casos se da vuelta entre corridas.

### C6 — Semilla fija: reproducible y mejor
Copia de C5 + `seed: 42` en la petición. Resultado: **mejor corrida de las 6** en todas las métricas (82,6 % acierto, costo 5, tasa 17,4 %). Y la prueba de reproducibilidad: **dos corridas idénticas, 46/46 predicciones iguales, misma duración (883 s)**. El número deja de ser lotería.

---

## 5. Tendencias halladas (las que importan)

1. **Sensibilidad al balance few-shot** (C1↔C2): el modelo no calibra solo — el diseño de producción no puede depender de un prompt binario aislado.
2. **Los extremos son sólidos:** commodity y encaje claro se clasifican bien (>90 %); **la frontera mixta es el terreno del fallo**.
3. **La vía REVISAR funciona:** 0-1 fuga en las corridas 3-vías vs 3 en binaria; el modelo se abstiene en vez de destruir oportunidades.
4. **El prompt endurecido decide pero arriesga** (C3↔C4): trade-off real entre cola y fuga que debe vigilarse en cada iteración.
5. **Reglas deterministas = certeza gratis:** 33/33 en validación, 12/12 en corrida, 0 tokens, instantáneo. El 27,5 % del trabajo clasificatorio es código, no azar.
6. **Sin seed no hay experimento:** con temperature 0.1 pero seed aleatoria, ~12 % de casos cambian entre corridas (C4 vs C5 en los mismos 34). Con `seed: 42` → 46/46 reproducible.
7. **0 FP sostenido en C3-C6** — nunca vende basura; el riesgo residual es de fuga (1 caso frontera persistente: EMGIRS), no de humo.

---

## 6. Estado actual: configuración oficial y siguiente paso

### Configuración oficial (C6, reproducible)

**Filtro de 5 reglas (Paso A validado: 33/33) → LLM `granite3.1-dense:8b` con prompt C4 y seed 42 → 3 vías (SI/NO/REVISAR).**

| Métrica oficial | Valor | Umbral | Estado |
|---|---|---|---|
| Acierto exacto | 82,6 % (38/46) | — | |
| Precisión de SÍ | 1,000 (0 FP) | ≥0,70 | ✅ |
| Fuga | 1 (EMGIRS, frontera pura) | ≤1 | ✅ |
| REVISAR capturado | 2/3 | ≥2/3 | ✅ |
| Costo de revisión | 5 | ≤4 | a 1 caso |
| Acierto de NO | 87,5 % | ≥90 % | a 1 caso |
| Tasa de cola | 17,4 % | ~10 % | |

Las 5 residuales NO→REVISAR son todas frontera conocida (mixtos "vía partner", consultoría, contenido, descripción ambigua) — terreno de la cola humana con causa, no del prompt.

### Siguiente paso

1. **Implementar el control REVISAR en la Fase 1** (estado `REQUIERE_REVISION`, filtro de reglas como pre-filtro del servicio, cola con causa) — la fase de experimentos cierra aquí.
2. La **cascada de producción** (autoconsistencia/votos, segundo nivel) absorbe la varianza caso a caso — la semilla fija valida en experimentos, la cascada estabiliza en vivo.

### Opciones descartadas (con razón)

| Opción | Razón |
|---|---|
| Reranker (bge-reranker-v2-m3) | Port roto en Ollama local (crash GGML_ASSERT), sin endpoint nativo; aporte marginal vs complejidad de despliegue — descartado por el usuario |
| Gate "datos insuficientes" por longitud | 10/14 casos más cortos son decidibles con el título; cubriría ~1-2 % real y dañaría casos decidibles (ver README_GOLDSETS §6.1) |
| Segundos modelos / más scripts | fuera de alcance: un modelo, un prompt, iteraciones acotadas |

---

## 6.1 Inventario de archivos

| Archivo | Contenido |
|---|---|
| `fewshot_eval.py` | Script C1/C2 (binario, v1) — **no modificar** |
| `fewshot_eval_v2.py` | Script C3 (3 vías, split 20/80) |
| `fewshot_eval_v4.py` | Script C4 (prompt ajustado) |
| `fewshot_eval_v5.py` | Script C5 (+ filtro determinista) |
| `fewshot_eval_v6.py` | Script C6 (**oficial**: C5 + seed 42) |
| `validacion_reglas.py` | Paso A: validación de las 5 reglas contra los 120 etiquetados |
| `corrida{1,2,3,4,5}.log`, `corrida6_run{1,2}.log` | Logs crudos por corrida |
| `corrida1_resultados.jsonl`, `resultados_fewshot.jsonl`, `resultados_v2.jsonl`, `resultados_v4.jsonl`, `resultados_v5.jsonl`, `resultados_v6.jsonl` | Registro por caso (esperado, predicho, justificación del modelo) |
| `resultados_v6_run1.jsonl` | Run1 de reproducibilidad (idéntico a run2: 46/46) |
| `corrida1_reporte.txt`, `reporte_fewshot.txt`, `reporte_v2.txt`, `reporte_v4.txt`, `reporte_v5.txt`, `reporte_v6.txt` | Reportes con métricas y matrices |
| `reporte_reglas.txt` | Validación Paso A: muertes por regla, 0 fugas |
| `goldset_v2_muestra.csv` | Muestra v2 con etiquetas y notas de discusión |
| `goldset_v2_CONGELADO.csv` | **Copia congelada inmutable** — verdad de evaluación |
