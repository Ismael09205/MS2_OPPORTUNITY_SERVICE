# Opportunity Analysis Service

Microservicio de análisis de licitaciones públicas de SERCOP (Ecuador) y correspondencia con el catálogo de soluciones y productos IBM.

El objetivo central de este microservicio es operar de forma **100% autónoma y tolerante a fallos**: el usuario solo sube los procesos de contratación pública a través del microservicio de ingesta (`sercop_ingestion_service`), y este servicio se encarga automáticamente de vectorizar los procesos, calcular las similitudes contra el catálogo IBM y preparar las recomendaciones comerciales.

---

## Flujo Automatizado de Extremo a Extremo

El flujo de trabajo general opera de manera transparente en cuatro etapas simples:

1. **Ingesta de Licitaciones (Microservicio Externo)**:
   - El usuario sube el archivo ZIP con los procesos de contratación en formato JSON al microservicio `sercop_ingestion_service`.
   - Dicho microservicio guarda los registros en la tabla compartida de la base de datos PostgreSQL.

2. **Detección y Sincronización Automática**:
   - Este microservicio detecta los nuevos procesos de contratación pública en la base de datos mediante tres mecanismos complementarios:
     - **Verificación al iniciar**: Revisa pendientes en segundo plano al arrancar la aplicación.
     - **Tarea programada periódica**: Un programador en background revisa cada minuto si existen procesos pendientes.
     - **Disparador inmediato (Webhook)**: El microservicio de ingesta puede notificar por HTTP que finalizó la carga.

3. **Procesamiento Resiliente y Cálculo Vectorial**:
   - **Auto-verificación del Catálogo IBM**: Asegura que los productos IBM y sus vectores estén listos en la base de datos sin requerir intervención manual.
   - **Detección de cambios por Hash**: Si un proceso ya fue analizado y su contenido no cambió, se omite para no gastar recursos.
   - **Vectorización con Granite**: Genera los embeddings del proceso en Ollama con reintentos automáticos y tolerancia a fallos.
   - **Similitud y Puntuación**: Calcula la similitud semántica contra los productos IBM y aplica bonificaciones de negocio según el monto y el método de contratación.
   - **Persistencia**: Guarda los 5 mejores productos candidatos para cada licitación.

4. **Generación de Recomendaciones**:
   - Las recomendaciones ejecutivas se generan bajo demanda a medida que el usuario o el asistente consultan las mejores oportunidades, o bien mediante evaluación externa con Bob.

---

## Resumen de Cambios y Mejoras Implementadas

### 1. Automatización y Orquestación del Pipeline
- Se creó un servicio orquestador que centraliza todo el ciclo de vida de los procesos.
- Se implementó un control de concurrencia que impide que dos tareas de sincronización se ejecuten al mismo tiempo.
- Se agregó soporte para omitir procesos idénticos mediante cálculo de hash de contenido.

### 2. Tolerancia a Fallos y Resiliencia (Fault Tolerance)
- **Aislamiento de fallos individuales**: Si un proceso particular genera un error o tiempo de espera en Granite, el error se captura y se registra, permitiendo que todos los demás procesos del lote continúen procesándose con éxito.
- **Reintentos con pausa progresiva**: El cliente de embeddings de Granite reintenta automáticamente hasta 3 veces ante micro-cortes o demoras de Ollama.
- **Tiempos de espera definidos**: Se configuraron tiempos límite de conexión y lectura para evitar que hilos queden bloqueados indefinidamente.
- **Logging estructurado**: Se reemplazaron todas las impresiones por consola por un sistema de registro profesional con diferentes niveles de severidad.

### 3. Carga Automática del Catálogo IBM
- Se implementó un componente de auto-inicialización que verifica si el catálogo oficial de IBM está cargado en la base de datos y vectorizado. Si falta, lo carga automáticamente desde el archivo local empaquetado.

### 4. Automatización en Segundo Plano
- **Startup Runner**: Al iniciar el microservicio, se lanza una verificación asíncrona en segundo plano para no demorar el arranque del servidor.
- **Scheduler**: Tarea programada en segundo plano que busca procesos pendientes periódicamente.

### 5. Nuevos Endpoints y Métricas
- Endpoint para solicitar la sincronización y procesamiento de procesos pendientes.
- Endpoint de estado y métricas que informa la cantidad de procesos registrados, embeddings generados, similitudes calculadas y el estado de salud de los componentes.

### 6. Integración y Nuevas Herramientas en el Servidor MCP
- Se añadieron herramientas en el servidor MCP para permitir al asistente sincronizar procesos y consultar el estado del pipeline en tiempo real.

---

## Documentación Complementaria

- [Guía de Pruebas Paso a Paso](GUIA_DE_PRUEBAS.md): Instrucciones detalladas para probar todos los flujos implementados.
- [Documentación del Servidor MCP](MCP_SERVER/README.md): Detalle de todas las herramientas MCP disponibles y sus modos de evaluación.
- [Análisis de Similitud y Mediciones](README_SIMILITUD.md): Justificación técnica sobre la similitud coseno y el score compuesto.
