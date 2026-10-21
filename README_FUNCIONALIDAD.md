# Guía de Pruebas de la Funcionalidad

Esta guía detalla los pasos para probar los filtros de alcance, el relacionador vectorial y la evaluación asíncrona/on-demand con Granite.

---

## 1. Verificación Inicial

Asegúrate de que PostgreSQL y Ollama estén corriendo:
```bash
ollama run granite3.1-dense:8b
ollama run granite-embedding:278m
```

Compilar y ejecutar la aplicación:
```bash
./gradlew bootRun
```

---

## 2. Pruebas de Endpoints

### A. Sincronizar Procesos y Disparo Asíncrono de Granite
Ejecuta la sincronización completa:
```bash
curl -X POST http://localhost:8080/procesos/sincronizar
```
* **Qué verificar:**
  * La respuesta HTTP devuelve inmediatamente el resumen de procesos sincronizados.
  * En los logs de la consola verás:
    `Disparando evaluación asíncrona priorizada de Granite para los nuevos candidatos...`
    `Iniciando evaluación asíncrona priorizada de top 5 oportunidades con Granite...`

---

### B. Consultar Oportunidades Listas (Instantáneo)
Consulta las recomendaciones generadas:
```bash
curl -X GET "http://localhost:8080/recomendaciones?limit=6&offset=0"
```
* **Qué verificar:**
  * Responde en menos de **50 ms**.
  * No aparecen licitaciones de compras excluidas (como tóner, suministros de oficina o enlaces de internet puro).
  * Cada oportunidad incluye: `ocid`, `titulo`, `productoRecomendado`, `nivelConfianza`, `justificacion` y `estrategiaComercial`.

---

### C. Evaluación On-Demand de un Caso Puntual (Just-In-Time)
Para forzar la evaluación profunda con Granite de un OCID específico:
```bash
curl -X GET "http://localhost:8080/recomendaciones/{OCID_DE_PRUEBA}"
```
* **Qué verificar:**
  * Si el proceso es fuera de alcance (ej. monto muy bajo o término excluido), responde inmediatamente con `esOportunidadIbm: false` y el motivo de descarte.
  * Si es una oportunidad tecnológica válida, Granite analiza los productos relacionados y devuelve el veredicto en ~1-2 segundos.

---

### D. Consultar Estado y Métricas del Pipeline
Para auditar cuántos procesos han sido filtrados y evaluados:
```bash
curl -X GET http://localhost:8080/procesos/estado-pipeline
```
* **Qué verificar:**
  * Retorna el conteo de `totalProcesosSercop`, `totalEmbeddingsSercop`, `totalProcesosConSimilitud` y `totalRecomendaciones`.

---

## 3. Pruebas Automatizadas Unitarias y de Integración

Ejecuta la suite de pruebas automatizadas:
```bash
./gradlew test
```
* Incluye pruebas para:
  1. [`ValidadorAlcanceIbmServiceTest.java`](src/test/java/com/ibm/opportunity_analysis_service/ValidadorAlcanceIbmServiceTest.java): Descarte de términos no enterprise, validación de presupuestos y aprobación con bonificación de alcance.
  2. [`ProcesoSercopPipelineServiceTest.java`](src/test/java/com/ibm/opportunity_analysis_service/ProcesoSercopPipelineServiceTest.java): Idempotencia, tolerancia a fallos y disparo asíncrono.
