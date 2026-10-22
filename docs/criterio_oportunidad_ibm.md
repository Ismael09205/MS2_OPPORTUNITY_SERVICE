# Criterio de "Oportunidad de Negocio para IBM"

> **Versión:** 1.3 (2026-10-21) — marco corregido: **catálogo de software IBM**, no "¿es TI?".
> v1.1 añadió las reglas duras (bloque 1), v1.2 la regla estricta de desarrollo a medida (bloque 5), **v1.3 resuelve el cloud/IaaS (la espiga S7)**.
> **Alcance:** IBM + Business Partners (consorcios y co-oferta incluidos).
> **Uso:** (1) verdad de referencia para medir la calidad de las recomendaciones (gold set Fase 0); (2) contrato que deben cumplir `ValidadorAlcanceIbmService`, el ranking y el prompt de Bob.
> **Decisiones:** todas las del §7.

---

## 1. Definición operativa

Un proceso SERCOP es **oportunidad de negocio para IBM** cuando:

1. El **objeto central** de la contratación está cubierto por el **catálogo de software IBM** que tenemos cargado (104 productos en `producto_ibm`).
2. **IBM o una Partner puede vender** ese producto: no hay una atadura exclusiva a marca competidor que lo cierre (si la hay, depende del espacio real — ver Q4).
3. Supera los **cortes de negocio** (monto, método, exclusiones).
4. Si es un **servicio**, cuenta solo si el **pliego exige explícitamente tecnología IBM** (→ MEDIO/BAJO). Ver reglas duras.
5. **Servicio de nube / IaaS sin marca nombrada → sí cuenta** (decisión 2026-10-21): el cloud es un producto IBM propio que IBM vende directamente (IBM Cloud, Object Storage, Virtual Servers, GPUs). Es la **única excepción** a "infraestructura solo en mixtos".
6. **Hardware/appliance puro** (almacenamiento, servidores, firewalls, equipos de marca ajena o sin componente de software) → **NO**, aunque sea IBM.

**Reglas duras de NO (todas decididas por el usuario el 2026-10-21):**

- Tóner, cableado, mobiliario, enlace de internet, limpieza, catering, guardias, **obras civiles** → **NO**.
- **Desarrollo a medida → NO**, aunque el dominio tenga producto IBM de plataforma (gestión documental, automatización, integración), **salvo que el pliego exija tecnología IBM explícitamente** (regla bloque 5).
- **Soporte, mantenimiento, actualización o renovación de plataforma existente** sin producto IBM → **NO** (incluye actualización de versión de la misma marca).
- Renovación cerrada de competidor **sin espacio** real → **NO**; la migración **sí** abre espacio (Oracle → MEDIO; SharePoint/OlymPO → NO porque el destino ya es la misma marca).
- **Suscripción a base de datos o contenido propietario de un editor tercero sin componente de plataforma IBM** (Scopus/Elsevier, Web of Science/Clarivate, IEEE Xplore, LexisNexis, Rosetta Stone…) → **NO**. Aunque el pliego lo pinte con IA: el objeto central es el *contenido*.
- **Servicio operativo puro** (digitalización de archivo, escaneo/indexación, operación de plataforma de terceros) → **NO**.
- **Conectividad/correo electrónico/ofimática** → **NO** (commodity explícito).
- **Software vertical sin equivalente IBM** (turnos, ingeniería civil, educación, TPRM/GRC, ICR/OCR, verificación de plagio) → **NO**.

---

## 2. Familias del portafolio (para la etiqueta)

Los 104 productos agrupados. La etiqueta registra **qué familia cubre el proceso**.

| Familia | Productos representativos |
|---|---|
| **F1 · IA y ciencia de datos** | watsonx, watsonx.data/governance/orchestrate/Assistant for Z, watsonx Code Assistant for Z, SPSS, ILOG CPLEX, AI coding agent |
| **F2 · Datos y analítica** | Db2, Db2 for z/OS, Cloudant, DataStage, Cognos Analytics, Planning Analytics, StreamSets, Cloud Pak for Data, Cloud Databases for Elasticsearch |
| **F3 · Integración, automatización y B2B** | Cloud Pak for Integration, Cloud Pak for Business Automation, MQ, API Connect, App Connect, DataPower Gateway, Sterling (B2B/SFT), webMethods, Event Automation, Targetprocess |
| **F4 · Seguridad** | QRadar, Guardium (5 variantes), Trusteer, Verify, Vault, Secrets Manager, Cloud App ID, Cloud Cyber Recovery, Storage Defender |
| **F5 · Observabilidad, FinOps y operaciones** | Instana Observability, Turbonomic, SevOne, OMEGAMON, Kubecost, Cloudability, Apptio, Cloud Pak for AIOps, Z IntelliMagic Vision |
| **F6 · Nube híbrida y plataforma** | Red Hat OpenShift, Cloud Paks, Cloud Satellite, Terraform, Fusion, Concert, **IBM Cloud** (Virtual Servers for VPC, Bare Metal, Object Storage, Kubernetes Service, Satellite, Code Engine, Container Registry, **GPUs and AI Accelerators**, Cloud Internet Services) |
| **F7 · Mainframe y almacenamiento** | z/OS, CICS, AIX, IBM i, Power, LinuxOne, z16/z17, FlashSystem, Storage (DS8000, Ceph, Scale, Tape, SAN, Insights) — **solo como componente en mixtos**; puro → NO |
| **F8 · DevOps e ingeniería** | DevOps Code ClearCase, DevOps Test, Engineering Lifecycle Management, Developer for z/OS |

---

## 3. Niveles de la etiqueta

Cualitativa en 4 niveles (decisión 2026-10-21).

| Nivel | Significado | `esOportunidadIbm` |
|---|---|---|
| **ALTO** | El objeto central coincide directamente con un producto IBM del catálogo, sin atadura exclusiva de competidor. Incluye **IaaS/cloud puro sin marca**. | `true` |
| **MEDIO** | Hay producto IBM que participa pero no domina: mixto/multi-rubro con espacio real (IaaS dentro de un mixto, integración, seguridad, colaboración no IBM, migración). | `true` |
| **BAJO** | Es oportunidad (hay producto IBM aplicable) pero con margen reducido o certeza baja. | `true` (decisión 2026-10-21) |
| **NO** | Sin producto IBM razonable: todas las reglas duras de §1. | `false` |
| **N/A** | Compra de usuario final sin componente tecnológico (equivale a NO; se conserva por compatibilidad con `nivelConfianza`). | `false` |

**Métricas:** `precisión` = sobre lo recomendado como `true`, cuánto es ALTO/MEDIO/BAJO real. `recall` = sobre los ALTO+MEDIO+BAJO reales, cuántos detectó el sistema. **Cobertura** se reporta aparte (¿cuántos tienen recomendación?).

---

## 4. Checklist de decisión (5 preguntas, en orden)

La primera que responda NO corta el juicio.

**Q1 — ¿El objeto central está cubierto por el catálogo IBM?**
Si es commodity/oficina/conectividad/correo, **obras**, **contenido propietario de un editor tercero**, **servicio operativo puro**, **hardware/appliance puro** o algo que IBM no vende → **NO**.

**Q2 — ¿Supera los cortes de negocio?**
- Monto ≥ USD 15.000 **o** ≥ 2 keywords enterprise presentes (un pliego de $8k con OpenShift/SIEM **sí** es oportunidad).
- Si el monto **no está informado**, no se descarta: se evalúa igual y se marca `montoConocido=false`.
- Método no excluido (hoy `infima_cuantia`; **nota:** en los 381 procesos actuales no existe ninguno).
- ⚠️ La excepción de keywords está **inflada** por H18: la keyword `"ia"` casa por subcadena dentro de *licen**cia**s / ofimá**tica*** → cualquier proceso de licencias suma +1.

**Q3 — ¿Hay un producto IBM aplicable al núcleo?** (registrar familia §2)
Si no lo hay de forma razonable → **NO**.

**Q4 — ¿Hay atadura exclusiva a marca competidor?**
Pura renovación/actualización **sin espacio** → **NO**. Si pide componentes donde IBM/Partner puede participar (IaaS, integración, seguridad, nube, **migración**) → **MEDIO**.

**Q5 — ¿Qué tipo de entrada es?**
- Compra directa de software IBM **o** servicio de nube/IaaS sin marca → **ALTO**.
- Mixto con infraestructura/hardware IBM **como componente** → **MEDIO**.
- Servicio: **desarrollo a medida → NO** salvo pliego que exija tecnología IBM; soporte/actualización de plataforma existente → **NO**.

---

## 5. Casos borde — guía de etiquetado

| Caso | Etiqueta | Estado |
|---|---|---|
| Objeto = producto IBM sin competidor presente (ej. seguridad de red sin marca) | ALTO + familia | definido |
| **Servicio de nube/IaaS sin marca** (GPU para IA, cloud computing genérico) | **ALTO**, F6 | **decidido 2026-10-21 (espiga S7)** |
| Objeto no TI con mención TI accesoria | NO | definido (Q1) |
| Mixto con marca competidora central + componentes IBM (nube, seguridad, conectividad, integración) | MEDIO | definido (Q4) — caso SENAE (Palantir + IaaS) |
| Mixto no competidor: colaboración (no IBM) + IA + cloud | MEDIO | definido (espiga S7) — Min. Gobierno |
| Renovación cerrada de licencia competidor sin espacio | NO | definido 2026-10-21 |
| Renovación/mixto con espacio real para IBM (incluye migración de plataforma) | MEDIO | definido 2026-10-21 — EEPGSA (Oracle) y Balzar |
| Migración **hacia la misma marca** (SharePoint, OlymPO v16) | NO | decidido 2026-10-21 (bloques 3 y 4) |
| Licencia/SaaS sin marca y sin producto IBM claro (Adobe, Autodesk, Office, ofimática, correo) | NO | definido |
| **Plataforma vertical sin equivalente IBM** (turnos, TPRM/GRC, ICR, plagio, ingeniería civil, educación) | **NO** | decidido 2026-10-21 |
| Suscripción de contenido propietario de editor tercero (Scopus, Web of Science, Rosetta Stone…) — **incluso con IA** | **NO** | decidido 2026-10-21 (bloques 1, 4 y 7) |
| Servicio operativo puro: digitalización, escaneo, operación de plataforma | **NO** | decidido 2026-10-21 (bloque 1) |
| **Hardware/appliance/storage puro** (PACS, firewall Palo Alto, z16, FlashSystem) | **NO** | decidido 2026-10-21 (bloque 1) |
| Soporte/mantenimiento/actualización de plataforma existente | **NO** | decidido 2026-10-21 (bloques 3, 5 y 7) |
| **Desarrollo a medida** (aunque el dominio tenga producto IBM de plataforma) | **NO** — salvo pliego que exija tecnología IBM | decidido 2026-10-21 (bloque 5) |
| Consultoría de implementación (ej. agente de IA + BI de software libre) | **NO** | decidido 2026-10-21 (espiga S7) |
| Obras civiles clasificadas erróneamente como TI por SERCOP | NO | decidido 2026-10-21 (bloque 5) |
| Contratación `direct` con objeto cubierto por el catálogo | **[PENDIENTE]** | ~15 casos direct ya etiquetados sin usarlo como corte; aún sin regla fija |

---

## 6. Gold set: formato y muestra

**Archivo:** `src/test/resources/goldset_oportunidades.csv` — **63 procesos** etiquetados.
**Regeneración:** `psql -h localhost -U ismaeln -d sercop_db -f src/test/resources/generar_goldset.sql` (**no regenerar** una vez etiquetado: cambiaría la muestra y perdería la espiga S7, que se añadió aparte).

Columnas de **hechos** (las llena la extracción):
`ocid, strato, titulo, comprador, categoria_cpc, monto_usd, metodo, tipo_interno, region, menciona_competidor, candidatos_top3_sistema, descripcion_corta`

Columnas de **verdad** (las llena el usuario): `nivel_esperado` (ALTO/MEDIO/BAJO/NO), `familia_esperada` (F1..F8 o vacío), `producto_esperado` (nombre exacto de `producto_ibm` o vacío), `nota`.

**Flujo de etiquetado:** bloques interactivos en chat — el asistente presenta un bloque con hechos + etiqueta propuesta razonada, el usuario confirma/corrige, el asistente escribe el CSV.

**Estratificación (55) + espiga (8):**

| # | Estrato | n | Por qué |
|---|---|---|---|
| S1 | `CLOUD_COMPUTING` / `BASES_DATOS_PLATAFORMAS`, ≥15k, sin marca competidora | 12 | Candidatos naturales ALTO (F2/F6) |
| S2 | `LICENCIAS_SOFTWARE` / `SUSCRIPCIONES_SAAS`, ≥15k, **con** marca competidora | 12 | La zona donde se decide la calidad (Q4) |
| S3 | `LICENCIAS_SOFTWARE` / `SUSCRIPCIONES_SAAS`, ≥15k, sin marca | 8 | Software genérico: ¿encaja con catálogo? |
| S4 | `DESARROLLO_CONSULTORIA_TI` | 8 | Servicios → regla bloque 5: todos NO |
| S5 | Sin monto informado | 8 | Caso borde de corte |
| S6 | Monto < 15k | 7 | Caso borde de corte |
| **S7** | **Espiga: fuera de la muestra, filtrados por keywords fuertes, ≥15-20k, sin marca competidora** | **8** | **Calibrar la frontera NO↔MEDIO↔ALTO** |

**Estado del etiquetado: 63/63 ✅**
Distribución: **ALTO 3 · MEDIO 8 · BAJO 0 · NO 52** → 11 oportunidades reales de 63.

---

## 7. Registro de decisiones

| Fecha | Decisión | Elegido por |
|---|---|---|
| 2026-10-21 | Alcance = IBM + Business Partners (consorcios y co-oferta) | usuario |
| 2026-10-21 | Primero criterio escrito, después etiquetar | usuario |
| 2026-10-21 | Fase 0 antes que la Fase 1 | usuario |
| 2026-10-21 | **Marco = catálogo de software IBM, no "¿es TI?"** (corrección de enfoque) | usuario |
| 2026-10-21 | Etiqueta cualitativa 4 niveles: ALTO / MEDIO / BAJO / NO | usuario |
| 2026-10-21 | Renovación de competidor: depende del espacio real (pura → NO; mixta → MEDIO) | usuario |
| 2026-10-21 | Monto < $15k con ≥ 2 keywords enterprise → sí es oportunidad | usuario |
| 2026-10-21 | Sin monto informado → oportunidad si el objeto está cubierto | usuario |
| 2026-10-21 | Frontera ALTO/MEDIO → la decide el evaluador; no se mide con regla | usuario |
| 2026-10-21 | BAJO cuenta como oportunidad (`esOportunidadIbm=true`) | usuario |
| 2026-10-21 | Muestra: 55 procesos, estratificación balanceada | usuario |
| 2026-10-21 | Alcance del catálogo: **software**; infra/hardware IBM solo como componente en mixtos | usuario |
| 2026-10-21 | La etiqueta registra **familia de portafolio** cubierta (F1-F8) | usuario |
| 2026-10-21 | Test de calidad: JUnit `@Tag("local")` dentro de `./gradlew test`, contra lo guardado en BD | usuario |
| 2026-10-21 | Métricas: cobertura y precisión por separado (sin recomendación = no detectado solo en cobertura) | usuario |
| 2026-10-21 | **Flujo de etiquetado: bloques interactivos en chat** (asistente propone, usuario confirma) | usuario |
| 2026-10-21 | **Infra/hardware puro sin componente de software → NO siempre** (bloque 1, caso PACS) | usuario |
| 2026-10-21 | **Suscripción de contenido propietario de terceros → NO siempre**, incluso con IA (bloques 1, 4 y 7) | usuario |
| 2026-10-21 | **Servicio operativo puro (digitalización) → NO** (bloque 1, caso Capitanía de Puerto) | usuario |
| 2026-10-21 | Balzar (plataforma de recaudación + implementación, $119.950) → **MEDIO**; no lo alcanza la regla de desarrollo (es adquisición de software) | usuario |
| 2026-10-21 | EEPGSA (licenciamiento y migración Oracle del SIG) → **MEDIO**: la migración abre espacio | usuario |
| 2026-10-21 | CNE (soporte del gestor Oracle, $397k) → **NO**: el soporte no abre espacio | usuario |
| 2026-10-21 | ESPE (solución NDR de red) → **MEDIO** conservador sin ver el pliego; producto QRadar/F4 | usuario |
| 2026-10-21 | **Desarrollo a medida → NO siempre**, salvo pliego que exija tecnología IBM (bloque 5, S4) | usuario |
| 2026-10-21 | ISSPOL (actualización de plataforma existente) → **NO** | usuario |
| 2026-10-21 | ARH (mantenimiento de BD con migración sin destino, $12k) → **NO** | usuario |
| 2026-10-21 | **Cloud/IaaS puro sin marca → SÍ cuenta** (ALTO): es producto IBM propio; el hardware puro sigue en NO (§1.5-1.6) | usuario |
| 2026-10-21 | **Ampliación del gold set: +8 casos "espiga"** buscando ALTO (estrato S7) → 63 procesos | usuario |
| _pendiente_ | Contratación `direct`: ¿resta un nivel o no es cortante? | usuario |
