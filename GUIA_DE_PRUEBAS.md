# Guía de Pruebas Paso a Paso

Esta guía describe cómo verificar de extremo a extremo el microservicio `opportunity_analysis_service` y su servidor MCP.

---

## 1. Requisitos Previos

1. **Base de Datos PostgreSQL**:
   - PostgreSQL en ejecución en el puerto `5432` con la base de datos `sercop_db`.
   - Extensión `pgvector` habilitada (`CREATE EXTENSION IF NOT EXISTS vector;`).
2. **Servidor Ollama**:
   - Ollama corriendo en `http://localhost:11434`.
   - Modelos descargados:
     ```bash
     ollama pull granite-embedding:278m
     ollama pull granite3.1-dense:8b
     ```
3. **Java y Node.js**:
   - Java 21 instalado.
   - Node.js versión 18 o superior.

---

## 2. Iniciar el Microservicio Backend

En la raíz del proyecto, ejecuta:

```bash
./gradlew bootRun
```

### Qué verificar en los logs de arranque:
- Verás que el sistema arranca e inmediatamente el componente `ProcesosStartupRunner` ejecuta en segundo plano:
  - Carga automática de productos IBM (`catalogo_ibm.json`) y generación de sus vectores (si la base estaba vacía).
  - Detección y cálculo automático de cualquier proceso SERCOP que estuviera pendiente en la tabla `proceso_contratacion`.

---

## 3. Pruebas de Endpoints REST

### Paso 3.1: Consultar el Estado del Pipeline
Abre una terminal o tu navegador y realiza una petición GET:

```bash
curl -X GET http://localhost:8080/procesos/estado-pipeline
```

**Resultado esperado**:
Un JSON que muestra:
- `enEjecucion`: `false` (o `true` si está procesando en ese instante).
- `totalProcesosSercop`: Número de procesos encontrados en `proceso_contratacion`.
- `totalEmbeddingsSercop`: Cantidad de embeddings generados para SERCOP.
- `totalProcesosConSimilitud`: Cantidad de procesos con similitudes calculadas.
- `catalogoIbmListo`: `true`.
- `totalProductosIbm`: Número de productos del catálogo IBM.

---

### Paso 3.2: Simular Subida de Procesos o Sincronización Manual
Cuando `sercop_ingestion_service` sube un archivo ZIP con JSON y los inserta en `proceso_contratacion`, puedes probar el disparo inmediato:

```bash
curl -X POST http://localhost:8080/procesos/sincronizar
```

**Resultado esperado**:
Un resumen de ejecución indicando:
- `totalProcesosSercop`: Total evaluado.
- `procesadosExitosos`: Procesos nuevos o modificados que fueron vectorizados y analizados.
- `alDiaSinCambios`: Procesos que se omitieron porque su hash SHA-256 no cambió (idempotencia).
- `fallidos`: 0 (o la lista de errores si ocurrió un fallo puntual).
- `duracionSegundos`: Tiempo total que tomó el lote.

---

### Paso 3.3: Consultar las Oportunidades Recomendadas
Consulta la lista paginada de oportunidades con mayor afinidad:

```bash
curl -X GET "http://localhost:8080/recomendaciones?limit=6&offset=0"
```

**Resultado esperado**:
Lista de oportunidades filtradas que tienen encaje real con IBM, ordenadas por puntuación compuesta de negocio y semántica.

---

### Paso 3.4: Consultar Detalle por OCID
Toma un `ocid` del paso anterior y consulta su detalle:

```bash
curl -X GET http://localhost:8080/candidatos/TU-OCID-AQUI
```

---

## 4. Pruebas de Tolerancia a Fallos

1. **Prueba de Idempotencia**:
   - Vuelve a ejecutar `curl -X POST http://localhost:8080/procesos/sincronizar`.
   - Verifica que el campo `alDiaSinCambios` coincida con el total de procesos y que `duracionSegundos` sea casi instantáneo (no se vuelve a llamar a Granite innecesariamente).

2. **Prueba de Aislamiento de Errores**:
   - Si se simula un proceso con datos atípicos o si Ollama tiene un corte temporal, el servicio registra la advertencia, reintenta hasta 3 veces con pausa progresiva y continúa con los siguientes procesos sin abortar el lote.

---

## 5. Pruebas con el Servidor MCP

1. **Compilar el servidor MCP**:
   ```bash
   cd MCP_SERVER
   npm install
   npm run build
   ```

2. **Interactuar con Bob en el chat**:
   - Puedes escribirle a Bob:
     - *"¿Cuál es el estado actual del pipeline de procesos?"* (Bob llamará a `obtener_estado_pipeline`).
     - *"Sincroniza los nuevos procesos que llegaron a la base de datos"* (Bob llamará a `sincronizar_procesos_sercop`).
     - *"Muéstrame las oportunidades IBM más relevantes"* (Bob llamará a `obtener_todas_las_recomendaciones`).
     - *"Dame más detalles del caso [OCID]"* (Bob llamará a `obtener_recomendacion_por_ocid`).
