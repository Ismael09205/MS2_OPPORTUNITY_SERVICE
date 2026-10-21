#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import axios from "axios";

// Configuracion de conexion al microservicio
const API_BASE_URL = process.env.API_BASE_URL || "http://localhost:8080";
const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 120000,
});

// Capa de inteligencia activa:
//   "bob"     → el MCP devuelve datos crudos para que Bob (el LLM) razone
//   "granite" → el Java llama a Granite/Ollama y devuelve la recomendacion ya evaluada
const EVALUADOR = (process.env.EVALUADOR || "bob").toLowerCase();

const server = new McpServer({
  name: "opportunity-analysis-mcp",
  version: "1.0.0",
});

// ==========================================
// HERRAMIENTAS DE USUARIO (ALTO NIVEL / AUTOMATICAS)
// ==========================================

// Herramienta de Usuario: Obtener recomendacion para un proceso especifico por OCID
server.registerTool(
  "obtener_recomendacion_por_ocid",
  {
    description: [
      "Obtiene los datos completos de un proceso SERCOP especifico para que el asistente genere un analisis profundo.",
      "Usar cuando el usuario exprese interes en una oportunidad concreta, por ejemplo:",
      "- 'dame mas informacion del caso 1'",
      "- 'me interesa el SENAE'",
      "- 'profundiza en BanEcuador'",
      "- 'quiero saber mas de esa oportunidad'",
      "- cualquier referencia a un comprador, numero de caso u OCID de la lista previa.",
      "IMPORTANTE: siempre llamar a esta herramienta para obtener los datos antes de presentar el analisis.",
      "Nunca generar el analisis sin haber consultado esta herramienta primero.",
    ].join(" "),
    inputSchema: z.object({
      ocid: z.string().describe("Identificador unico OCID del proceso. Obtenerlo de la lista devuelta por obtener_todas_las_recomendaciones."),
    }),
  },
  async ({ ocid }) => {
    try {
      if (EVALUADOR === "granite") {
        // Granite evalúa internamente → devolver resultado ya cocinado
        const response = await client.get(`/recomendaciones/${encodeURIComponent(ocid)}`);
        return {
          content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }],
        };
      }

      // EVALUADOR === "bob": datos crudos para que Bob razone
      const response = await client.get(`/candidatos/${encodeURIComponent(ocid)}`);
      const data = response.data;

      const texto = `
Eres un arquitecto de soluciones IBM experto en licitaciones públicas de Ecuador (SERCOP).
Analiza los siguientes datos crudos de un proceso de contratación y los candidatos IBM preseleccionados por similitud vectorial, y emite tu recomendación.

=== PROCESO SERCOP ===
OCID: ${data.proceso.ocid}
Título: ${data.proceso.titulo}
Descripción: ${data.proceso.descripcion}
Tipo: ${data.proceso.tipoInterno}
Comprador: ${data.proceso.comprador}
Monto referencial: ${data.proceso.presupuesto ?? data.proceso.monto ?? 'No especificado'} USD
Región: ${data.proceso.region ?? 'No especificada'}

=== TOP CANDIDATOS IBM (por similitud vectorial) ===
${data.candidatos.map((c: any, i: number) => `
${i + 1}. ${c.nombre} (score vectorial: ${c.similitud.toFixed(4)})
   Descripción: ${c.descripcion}
   Capacidades: ${(c.capacidades ?? []).join(', ') || 'N/A'}
   Tecnologías: ${(c.tecnologias ?? []).join(', ') || 'N/A'}
`).join('')}

=== TU TAREA COMO ARQUITECTO DE SOLUCIONES (GUÍA COMERCIAL GRADUADA) ===
Con base en la descripción del proceso y los candidatos IBM:
1. ¿Es una oportunidad real para IBM o sus Business Partners? (true/false). No descartes oportunidades mixtas o de consorcio por la simple mención de marcas legadas o de terceros.
2. Nivel de confianza (Guía graduada de abordaje comercial):
   - ALTO: El objeto central coincide directamente con soluciones IBM (watsonx, Instana, Turbonomic, Maximo, Cloud Pak, MQ, QRadar, etc.) sin ataduras de marcas competidoras exclusivas.
   - MEDIO: Licitaciones mixtas o multi-rubro (ej: piden una solución privativa de terceros como Palantir/Salesforce pero también componentes de nube IaaS, integración, seguridad o conectividad donde IBM/Partners pueden ofertar) o proyectos de modernización/migración.
   - BAJO: Renovación cerrada o suscripción exclusiva de software privativo de un competidor donde el margen para IBM es marginal.
   - N/A: Compras no tecnológicas (obras, papelería, mobiliario) o commodities genéricos de usuario final.
3. Producto IBM recomendado y justificación técnica realista.
4. Estrategia comercial concreta (ej: venta directa de solución, armado de consorcio con partners para cubrir componentes accesorios de nube/IaaS, o enfoque a largo plazo).
`;

      return {
        content: [{ type: "text", text: texto }],
      };
    } catch (error: any) {
      const statusText = error.response?.status === 404 ? "No se encontro el OCID especificado" : error.message;
      return {
        content: [{ type: "text", text: `Error consultando recomendacion para OCID ${ocid}: ${statusText}` }],
        isError: true,
      };
    }
  }
);

// Herramienta de Usuario: Obtener recomendaciones pre-calculadas (solo lectura de cache)
server.registerTool(
  "obtener_todas_las_recomendaciones",
  {
    description: [
      "Devuelve el resumen ejecutivo de las oportunidades IBM mas relevantes detectadas en los procesos SERCOP.",
      "Las oportunidades se generan bajo demanda — Granite solo trabaja cuando el usuario pide mas resultados.",
      "Usar como punto de entrada cuando el usuario pregunte por oportunidades, licitaciones o procesos relevantes para IBM.",
      "El resultado incluye OCID, comprador, monto, producto IBM recomendado y nivel de confianza.",
      "Tras mostrar el resumen, invitar al usuario a seleccionar un caso para profundizar con obtener_recomendacion_por_ocid.",
      "Cuando el usuario pida 'mas oportunidades' o 'ver mas casos', incrementar offset en 6 y llamar de nuevo.",
      "offset=0 primera pagina, offset=6 segunda pagina, offset=12 tercera pagina, y asi sucesivamente.",
    ].join(" "),
    inputSchema: z.object({
      offset: z.number().optional().describe("Posicion de inicio para paginacion (default 0). Incrementar en 6 para ver mas oportunidades."),
    }),
  },
  async ({ offset = 0 }) => {
    try {
      const response = await client.get("/recomendaciones", { params: { limit: 6, offset } });
      const todas: any[] = response.data;

      // Proyección compacta: solo campos esenciales para evitar consumo excesivo de tokens
      const resumen = todas
        .filter((r: any) => r.esOportunidadIbm)
        .slice(0, 6)
        .map((r: any) => ({
          ocid: r.ocid,
          comprador: r.comprador ?? "N/A",
          monto_usd: r.montoReferencial ?? "N/A",
          producto_ibm: r.productoRecomendado,
          confianza: r.nivelConfianza,
          justificacion: r.justificacion?.slice(0, 200) ?? "",
        }));

      const paginaActual = Math.floor(offset / 6) + 1;
      const texto = resumen.length > 0
        ? `Oportunidades IBM relevantes (pagina ${paginaActual}):\n\n` +
          JSON.stringify(resumen, null, 2) +
          `\n\n---\nPara un analisis profundo de cualquiera, usa obtener_recomendacion_por_ocid con el OCID.` +
          `\nSi el usuario quiere ver mas oportunidades, usar offset=${offset + 6}.`
        : offset === 0
          ? `No hay oportunidades IBM identificadas aun. Si existen similitudes calculadas, espera mientras Granite evalua los primeros procesos.`
          : `No hay mas oportunidades IBM disponibles a partir del offset ${offset}. Se han agotado los candidatos relevantes.`;

      return {
        content: [{ type: "text", text: texto }],
      };
    } catch (error: any) {
      return {
        content: [{ type: "text", text: `Error al obtener recomendaciones: ${error.message}` }],
        isError: true,
      };
    }
  }
);

// Herramienta Técnica: Precalcular y cachear recomendaciones LLM para los top N procesos
server.registerTool(
  "precalcular_recomendaciones",
  {
    description: "[Tecnico] Genera y persiste las recomendaciones LLM para los top N procesos por score de relevancia. Ejecutar tras analizar_similitud cuando lleguen nuevos procesos. Puede tardar varios minutos.",
    inputSchema: z.object({
      topN: z.number().optional().describe("Número de procesos top a precalcular (por defecto 50)"),
    }),
  },
  async ({ topN = 50 }) => {
    try {
      const response = await client.post("/recomendaciones/precalcular", null, { params: { topN } });
      return {
        content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }],
      };
    } catch (error: any) {
      return {
        content: [{ type: "text", text: `Error al precalcular recomendaciones: ${error.message}` }],
        isError: true,
      };
    }
  }
);

// Herramienta Técnica: Limpiar la caché de recomendaciones para re-evaluar con las nuevas reglas
server.registerTool(
  "limpiar_cache_recomendaciones",
  {
    description: "Elimina las recomendaciones antiguas guardadas en la base de datos para forzar una re-evaluación completa bajo las nuevas reglas de rigor técnico.",
    inputSchema: z.object({}),
  },
  async () => {
    try {
      const response = await client.post("/recomendaciones/limpiar-cache");
      return {
        content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }],
      };
    } catch (error: any) {
      return {
        content: [{ type: "text", text: `Error al limpiar caché de recomendaciones: ${error.message}` }],
        isError: true,
      };
    }
  }
);

// Herramienta de Usuario: Listar todos los procesos SERCOP candidatos cargados
server.registerTool(
  "listar_procesos_sercop",
  {
    description: "Lista todos los procesos de contratacion SERCOP disponibles en la base de datos para consultar sus OCIDs y detalles.",
    inputSchema: z.object({}),
  },
  async () => {
    try {
      const response = await client.get("/procesos");
      return {
        content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }],
      };
    } catch (error: any) {
      return {
        content: [{ type: "text", text: `Error al listar procesos SERCOP: ${error.message}` }],
        isError: true,
      };
    }
  }
);

// Herramienta de Usuario: Sincronizar y procesar de forma automática todos los procesos SERCOP pendientes
server.registerTool(
  "sincronizar_procesos_sercop",
  {
    description: "Sincroniza y procesa de forma automática todos los procesos SERCOP en la BD: asegura catálogo IBM, calcula embeddings con Granite y similitudes vectoriales.",
    inputSchema: z.object({}),
  },
  async () => {
    try {
      const response = await client.post("/procesos/sincronizar");
      return {
        content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }],
      };
    } catch (error: any) {
      return {
        content: [{ type: "text", text: `Error al sincronizar procesos SERCOP: ${error.message}` }],
        isError: true,
      };
    }
  }
);

// Herramienta de Usuario: Consultar estado y métricas del pipeline
server.registerTool(
  "obtener_estado_pipeline",
  {
    description: "Consulta las métricas y estado del pipeline (total procesos SERCOP, embeddings, similitudes calculadas, estado del catálogo IBM).",
    inputSchema: z.object({}),
  },
  async () => {
    try {
      const response = await client.get("/procesos/estado-pipeline");
      return {
        content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }],
      };
    } catch (error: any) {
      return {
        content: [{ type: "text", text: `Error al consultar estado del pipeline: ${error.message}` }],
        isError: true,
      };
    }
  }
);

// ==========================================
// HERRAMIENTAS TECNICAS (PRUEBAS Y MANTENIMIENTO)
// ==========================================

// Herramienta Tecnica: Cargar catalogo oficial de productos IBM
server.registerTool(
  "cargar_catalogo_ibm",
  {
    description: "[Tecnico] Carga el catalogo de productos IBM desde el archivo local a la base de datos.",
    inputSchema: z.object({}),
  },
  async () => {
    try {
      const response = await client.get("/cargar-catalogo-ibm");
      return {
        content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }],
      };
    } catch (error: any) {
      return {
        content: [{ type: "text", text: `Error al cargar catalogo IBM: ${error.message}` }],
        isError: true,
      };
    }
  }
);

// Herramienta Tecnica: Generar embeddings para productos IBM
server.registerTool(
  "generar_embeddings_productos_ibm",
  {
    description: "[Tecnico] Genera y almacena los embeddings vectoriales para todos los productos del catalogo IBM usando Granite.",
    inputSchema: z.object({}),
  },
  async () => {
    try {
      const response = await client.post("/embeddings/productos-ibm");
      return {
        content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }],
      };
    } catch (error: any) {
      return {
        content: [{ type: "text", text: `Error al generar embeddings de productos IBM: ${error.message}` }],
        isError: true,
      };
    }
  }
);

// Herramienta Tecnica: Generar embeddings masivos para candidatos SERCOP
server.registerTool(
  "generar_embeddings_candidatos",
  {
    description: "[Tecnico] Fuerza la generacion masiva de embeddings para todos los procesos candidatos de SERCOP.",
    inputSchema: z.object({}),
  },
  async () => {
    try {
      const response = await client.post("/embeddings/candidatos");
      return {
        content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }],
      };
    } catch (error: any) {
      return {
        content: [{ type: "text", text: `Error al generar embeddings de candidatos SERCOP: ${error.message}` }],
        isError: true,
      };
    }
  }
);

// Herramienta Tecnica: Ejecutar calculo de similitud vectorial
server.registerTool(
  "analizar_similitud",
  {
    description: "[Tecnico] Calcula y persiste la matriz de similitudes vectoriales entre candidatos SERCOP y productos IBM.",
    inputSchema: z.object({}),
  },
  async () => {
    try {
      const response = await client.post("/similitud");
      return {
        content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }],
      };
    } catch (error: any) {
      return {
        content: [{ type: "text", text: `Error al analizar similitud: ${error.message}` }],
        isError: true,
      };
    }
  }
);

// Herramienta Tecnica: Probar vectorizacion de texto arbitrario
server.registerTool(
  "probar_embedding_texto",
  {
    description: "[Tecnico] Genera y verifica el vector embedding para un texto dado usando Granite.",
    inputSchema: z.object({
      texto: z.string().describe("Texto de entrada a vectorizar"),
    }),
  },
  async ({ texto }) => {
    try {
      const response = await client.get("/prueba-embedding", {
        params: { texto },
      });
      return {
        content: [{ type: "text", text: JSON.stringify(response.data, null, 2) }],
      };
    } catch (error: any) {
      return {
        content: [{ type: "text", text: `Error probando embedding: ${error.message}` }],
        isError: true,
      };
    }
  }
);

// Inicializacion del transporte stdio
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("Opportunity Analysis MCP Server iniciado en stdio");
}

main().catch((error) => {
  console.error("Error fatal en el servidor MCP:", error);
  process.exit(1);
});
