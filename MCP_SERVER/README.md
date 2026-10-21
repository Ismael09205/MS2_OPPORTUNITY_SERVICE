# Opportunity Analysis MCP Server

Servidor basado en el estándar Model Context Protocol (MCP) que actúa como puente de comunicación entre el asistente de inteligencia artificial (Bob) y el microservicio backend de análisis de oportunidades (`opportunity_analysis_service`).

---

## Propósito del Servidor MCP

El servidor MCP permite que el asistente inteligente:
1. Consulte el resumen ejecutivo de licitaciones públicas de SERCOP priorizadas por afinidad con soluciones IBM.
2. Profundice en el análisis técnico y comercial de procesos específicos bajo demanda.
3. Supervise y dispare la sincronización de procesos y el estado de salud del pipeline.
4. Ejecute tareas de mantenimiento y verificación técnica cuando sea necesario.

---

## Catálogo de Herramientas MCP

### 1. Herramientas de Negocio y Usuario
- **`obtener_todas_las_recomendaciones`**:
  Devuelve el resumen ejecutivo paginado de las mejores oportunidades de negocio detectadas para IBM. Muestra el identificador (OCID), entidad compradora, monto referencial, producto IBM recomendado y nivel de confianza.
- **`obtener_recomendacion_por_ocid`**:
  Obtiene la información técnica y comercial profunda de un proceso SERCOP específico para formular la estrategia de propuesta.
- **`sincronizar_procesos_sercop`**:
  Dispara la sincronización y cálculo vectorial de todos los procesos SERCOP pendientes en la base de datos de forma automática y tolerante a fallos.
- **`obtener_estado_pipeline`**:
  Informa sobre la cantidad total de procesos cargados, embeddings generados, similitudes calculadas y estado de inicialización del catálogo IBM.
- **`listar_procesos_sercop`**:
  Permite consultar la lista cruda de licitaciones registradas en la base de datos.

### 2. Herramientas Técnicas y de Mantenimiento
- **`cargar_catalogo_ibm`**: Carga el archivo local de productos oficiales IBM a la base de datos.
- **`generar_embeddings_productos_ibm`**: Fuerza la vectorización de todo el catálogo IBM mediante Granite.
- **`generar_embeddings_candidatos`**: Genera los embeddings de los procesos SERCOP.
- **`analizar_similitud`**: Ejecuta el cálculo de similitudes coseno y score compuesto.
- **`probar_embedding_texto`**: Vectoriza un texto libre de prueba para verificar la conectividad con Ollama.

---

## Modos de Evaluación Inteligente (Variable `EVALUADOR`)

El servidor MCP soporta dos modalidades de razonamiento comercial:

1. **Modo Bob (`EVALUADOR=bob`, predeterminado)**:
   - El microservicio backend devuelve los datos crudos del proceso y los productos IBM con mayor afinidad vectorial.
   - El asistente Bob razona directamente en la conversación como arquitecto de soluciones de IBM, aplicando su propio criterio para recomendar el producto y definir la estrategia.

2. **Modo Granite (`EVALUADOR=granite`)**:
   - El microservicio backend consulta directamente al modelo Granite LLM vía Ollama para que devuelva la evaluación y justificación técnica ya estructurada.
   - El servidor MCP entrega la respuesta generada por Granite directamente al usuario.

---

## Instalación y Configuración

### 1. Compilación del Servidor MCP
```bash
cd MCP_SERVER
npm install
npm run build
```

### 2. Configuración en Clientes MCP (ej. `mcp.json`)
```json
{
  "mcpServers": {
    "opportunity-analysis": {
      "command": "node",
      "args": ["/ruta/absoluta/a/MCP_SERVER/build/index.js"],
      "env": {
        "API_BASE_URL": "http://localhost:8080",
        "EVALUADOR": "bob"
      }
    }
  }
}
```
