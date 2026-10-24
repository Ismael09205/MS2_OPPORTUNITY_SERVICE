# Gold Sets SERCOP — Referencia de verdad para evaluación de oportunidades IBM

Documentación de los dos conjuntos de referencia etiquetados, el proceso de etiquetado,
los criterios de decisión derivados y las mediciones de población asociadas.

> **Fecha de corte:** 2026-10-23 · **Origen de datos:** BD `sercop_db` (localhost:5432), tabla `proceso_contratacion` (381 procesos).

---

## 1. Reglas de uso (inviolables)

| Gold set | Archivo | Rol | Estado |
|---|---|---|---|
| **v1** | `src/test/resources/goldset_oportunidades.csv` | **Ejemplos few-shot** de los prompts + tests Fase 0 | Activo como ejemplos — NO usar para reportar evaluación |
| **v2** | `experimentos/goldset_v2_CONGELADO.csv` | **Evaluación congelada** (pruebas) | 57/57 etiquetado — NO tocar ni retocar etiquetas |

Regla de oro: **se itera con v1, se reporta con v2.** Cualquier tuning hecho mirando resultados de v2 se declara explícitamente como "tuning visto en v2"; el reporte limpio final requiere cross-validation 5-fold sobre los 57.

---

## 2. Gold set v1 — composición

Construido en Fase 0 (criterio v1.3). 63 casos, todos etiquetados.

| Etiqueta | Casos | % |
|---|---|---|
| ALTO | 3 | 4,8 % |
| MEDIO | 8 | 12,7 % |
| NO | 52 | 82,5 % |
| **Positivos (ALTO+MEDIO)** | **11** | **17,5 %** |

Baseline Fase 0 (pipeline publicado sin evaluar): **precisión 0,0 % · recall 0,0 %**.

---

## 3. Gold set v2 — construcción y composición

### 3.1 Muestra estratificada

Muestra de **57 procesos** extraídos de los ~318 procesos sin etiquetar de la BD, estratificados por probabilidad de oportunidad (P1 = tecnológico con monto alto y candidatos IBM plausibles → P5 = commodity puro):

| Estrato | Casos | Composición final |
|---|---|---|
| P1 (tecnológico, ≥15k USD, sin competidor obvio) | 25 | ALTO 2 · MEDIO 4 · NO 16 · REVISAR 3 |
| P2 (tecnológico medio) | 7 | MEDIO 1 · NO 6 |
| P3 (general/servicios) | 8 | NO 8 |
| P4 (mixto/commodity selectivo) | 12 | NO 11 · REVISAR 1 |
| P5 (commodity: antivirus, CAD, renovaciones) | 5 | NO 5 |

### 3.2 Distribución final (congelada)

| Etiqueta | v1 (63) | **v2 (57)** | Combinado (120) |
|---|---|---|---|
| ALTO | 3 | **2** | 5 |
| MEDIO | 8 | **5** | 13 |
| REVISAR (etiqueta nueva) | — | **4** | 4 |
| NO | 52 | **46** | 98 |
| Positivos (ALTO+MEDIO) | 11 (17,5 %) | **7 (12,3 %)** | 18 (15 %) |

**Lectura:** la tasa real de oportunidades en el universo SERCOP es ~12-17 %; el universo es mayormente NO. La estratificación P1 fue la más fértil (6 no-NO de 25), pero confirma que no existe un "código trampa" que multiplique positivos: hay que filtrar mucho grano.

### 3.3 Proceso de etiquetado

Etiquetado **interactivo por bloques** (~7 bloques): el asistente presentaba cada proceso con la información de producción (pliego resumido de BD + top-3 candidatos con score coseno) y su lectura razonada; el usuario confirmaba o ajustaba la etiqueta final. Toda discusión de frontera quedó registrada en el campo `nota` del CSV.

---

## 4. Criterios de decisión nacidos del etiquetado (aplicados en la corrida 4)

### 4.1 Reglas que decidiste tú (no estaban escritas antes)

| Regla | Caso origen | Efecto |
|---|---|---|
| **"Objeto central ≠ servicio/commodity"** | Casos 2 y 4 bajaron de ALTO a MEDIO | El objeto manda sobre los productos citados |
| **"¿Ya tienen competidor instalado?" → REVISAR** | Samborondón (chatbot institucional) | Define REVISAR = dependencia de proveedor ajeno |
| **"¿De qué proveedor se renueva?" → REVISAR** | SB (renovación nube), BANEC (servicio cloud) | Renovación sin marca = frontera |
| **Antivirus institucional → NO**, aunque traiga "XDR" o "IA" | Casos 36, 40, 44 (Judicatura $374k), 53, 54 | Commodity tajante |
| **Consultoría de auditoría/certificación → NO** | IESS ($1,6 M) — el monto alto no cambia el objeto | Regla bloque 5 |
| **Contenido propietario de tercero → NO** | WoS, Wolfram, documentación naval, geodatos | Sin equivalente IBM |

### 4.2 Los 4 REVISAR del v2 = definición operativa de la abstención

| Caso | Pregunta que lo hizo REVISAR |
|---|---|
| SB | ¿De qué proveedor de nube se renueva? |
| BANEC | "Servicio cloud" genérico sin vendor |
| CBDMQ-024 | Marca/tipo del servicio de comunicaciones |
| Samborondón | ¿Existe chatbot/competidor ya instalado? |

**Tasa de duda humana real: 4/57 ≈ 7 %** — calza con la estimación de "5-10 % llegaría a humano" del diseño de cascada.

### 4.3 Patrones NO repetidos sin excepción (120 casos)

antivirus/commodity · CAD/BIM · digitalización · vertical educativa · monomarca renovación · contenido tercero (WoS, SciELO, Wolfram) · desarrollo/consultoría a medida.

---

## 5. Análisis de familias — ¿dónde vive el SÍ? (120 etiquetados)

| Familia (CPC) | Casos | SÍ (ALTO+MEDIO) | Tasa |
|---|---|---|---|
| **BASES_DATOS_PLATAFORMAS** | 19 | 5 | **26,3 %** |
| **CLOUD_COMPUTING** | 12 | 3 | **25,0 %** |
| SUSCRIPCIONES_SAAS | 38 | 7 | 18,4 % |
| LICENCIAS_SOFTWARE | 40 | 3 | 7,5 % |
| **DESARROLLO_CONSULTORIA_TI** | 11 | **0** | **0,0 %** |

Por tipo de contrato:

| Tipo interno | Casos | SÍ | Tasa |
|---|---|---|---|
| **Contratos entre Entidades Públicas** | 12 | 7 | **58,3 %** |
| Concurso público | 6 | 1 | 16,7 % |
| Subasta Inversa Electrónica | 32 | 3 | 9,4 % |
| Licitación | 3 | 0 | 0 % |
| Bienes y Servicios únicos | 9 | 0 | 0 % |

**Tendencias:**
1. **Plataformas/Bases de datos y Nube son las familias cálidas** (25-26 %) — no conviene descartar temprano ahí.
2. **DESARROLLO_CONSULTORIA_TI = 0/11**: regla dura candidate con respaldo de dato, no de intuición.
3. **Contratos entre Entidades Públicas = veta principal** (58 %) — muestra pequeña (12) pero llamativa.
4. Licenciar software (la familia más numerosa) es mayormente commodity: 7,5 %.

---

## 6. Análisis de información disponible (381 procesos de BD)

| Señal | Casos | % |
|---|---|---|
| Descripción vacía | 0 | 0 % |
| Descripción < 50 caracteres | 20 | 5,2 % |
| Descripción < 80 caracteres | 85 | 22,3 % |
| Descripción 80-199 caracteres | 233 | 61,2 % |
| Descripción ≥ 200 caracteres | 63 | 16,5 % |
| **Contexto total (título+desc) < 80 chars** | **29** | **7,6 %** |
| Contexto total < 120 chars | 114 | 29,9 % |
| Sin monto ni presupuesto | 34 | 8,9 % |
| Poco contexto **y** sin monto | 3 | 0,8 % |

Percentiles de longitud de descripción: p10 = 61 · p25 = 85 · p50 = 125 caracteres.

### 6.1 Gate "datos insuficientes" por longitud — DESCARTADO (2026-10-23)

Se propuso un `if (contexto < 80 chars) → DATOS_INSUFICIENTES`. Se midieron los 14 casos más cortos de la BD: **10 de 14 se deciden con el título** (correo, Office 365, CAD, SPSS, VEEAM, impresión → NO claros). Los genuinamente insuficientes comparten otro patrón: *familia de interés IBM + sin marca/producto nombrado* (BANEC "servicio cloud", SENADI "seguridad perimetral") — y **el prompt de 3 vías ya los manda a REVISAR correctamente** (BANEC capturado 2/2 corridas).

**Decisión:** gate de longitud descartado (solo cubriría ~1-2 % real y dañaría casos decidibles). Único residuo adoptado: **guardar la causa del REVISAR** (justificación del modelo) en la cola humana para que la revisión sea "busque el pliego y mire la marca", no un juicio de negocio.
