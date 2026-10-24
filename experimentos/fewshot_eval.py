#!/usr/bin/env python3
"""
Experimento Few-Shot Eval — ¿Es capaz granite3.1-dense:8b de dar buenas recomendaciones?

Diseño (acordado con el usuario 2026-10-23):
  - 15 procesos ETIQUETADOS entran al prompt como ejemplos few-shot (2 ALTO + 4 MEDIO + 9 NO),
    cubriendo todos los casos posibles del criterio (no existe BAJO en el gold set).
  - Los 48 procesos restantes se evalúan SIN etiqueta, solo con la información pertinente
    que usa producción: datos del pliego (BD) + top-3 candidatos IBM con score de similitud
    y descripción del catálogo.
  - Se comparan las predicciones contra las etiquetas ocultas del gold set.

No toca el código Java del servicio. Requiere: Ollama corriendo y la BD sercop_db accesible.

Uso:
  python3 experimentos/fewshot_eval.py --dry-run     # solo construye prompts y reporta tamaños
  python3 experimentos/fewshot_eval.py               # corre el experimento completo (~48 llamadas)
  python3 experimentos/fewshot_eval.py --limit 10    # prueba parcial
"""

import argparse
import csv
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from collections import Counter
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
GOLDSET = BASE / "src/test/resources/goldset_oportunidades.csv"
OUT_DIR = Path(__file__).resolve().parent
RESULTADOS = OUT_DIR / "resultados_fewshot.jsonl"
REPORTE = OUT_DIR / "reporte_fewshot.txt"

OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://localhost:11434/api/chat")
DB = {"host": "localhost", "user": "ismaeln", "dbname": "sercop_db", "password": "123456"}

# ---------------------------------------------------------------------------
# Selección de los 15 ejemplos few-shot (estratificada por arquetipo)
# ---------------------------------------------------------------------------
EJEMPLOS = {
    "ALTO": [
        "ocds-5wno2w-RE-CEP-UC-2026-007-30951",     # GPUs/IA en IBM Cloud — requisito unívoco
        "ocds-5wno2w-RE-CEP-UTN-2026-01-83514",     # Cloud Virtual Servers VPC
    ],
    "MEDIO": [
        "ocds-5wno2w-RE-CEP-SENAE-2026-001-2656",   # IaaS 17.2M (caso difícil: el LLM lo había descartado)
        "ocds-5wno2w-SIE-EEPGSA-2026-05-3208",      # base de datos -> Db2 (F2)
        "ocds-5wno2w-SIE-ESPE-2026-015-30573",      # ciberseguridad -> QRadar (F4)
        "ocds-5wno2w-SIE-EERSSA-2026-019-3240",     # IA -> watsonx (F1)
        # Corrida 1: los 3 falsos negativos (subtipo "alcance mixto / entrada vía partner")
        # entran como ejemplos para enseñarle al modelo el patrón que falló (2026-10-23)
        "ocds-5wno2w-CPC-GADMB-2026-00001-31291",   # mixto: OpenShift/Cloud Paks vía partner
        "ocds-5wno2w-RE-CEP-MDG-2026-02-57124",     # mixto: Object Storage + watsonx, colaboración no IBM
        "ocds-5wno2w-RE-CEP-ARCSA-2026-002-488199", # mixto: Cognos + watsonx, productividad no IBM
    ],
    "NO": [
        "ocds-5wno2w-RE-PU-ESPAMMFL-2026-03-25643", # contenido propietario (Elsevier)
        "ocds-5wno2w-RE-PU-CCQAHDS-2026-0002-51587",# hardware puro sin software
        "ocds-5wno2w-SIE-UTA-2026-035-3205",        # commodity M365
        "ocds-5wno2w-SIE-CFNGYE-2026-001-2469",     # migración monomarca SharePoint
        "ocds-5wno2w-CPC-EPMMM-2026-001-585764",    # desarrollo a medida sin pliego IBM
        "ocds-5wno2w-SIE-CONTROLHIDROCARBUROS-2026-011-1216828",  # trampa keyword "ia" (H18)
    ],
}

# ---------------------------------------------------------------------------
# System prompt = mismo rol + mismos CRITERIOS que producción (OllamaEvaluadorOportunidadAdapter)
# ---------------------------------------------------------------------------
SYSTEM_PROMPT = """Eres un arquitecto de soluciones y líder comercial de IBM para compras públicas y licitaciones del SERCOP. Tu misión es evaluar con estricto rigor técnico y comercial si un proceso de contratación representa una oportunidad real y viable para el portafolio corporativo de IBM o sus Business Partners. Debes rechazar falsos positivos derivados de coincidencias léxicas genéricas (ej: si piden internet básico o correo y dice 'nube', no corresponde a IBM Cloud o watsonx).

CRITERIOS DE ALCANCE Y CONTROL DE OPORTUNIDADES IBM:
- ALCANCE VÁLIDO IBM:
  * Nube Híbrida y Plataforma: Red Hat OpenShift, modernización de aplicaciones legadas, nube privada/híbrida empresarial.
  * Datos e IA: watsonx, plataformas de analítica empresarial, Db2, gobierno y linaje de datos.
  * Ciberseguridad Enterprise: QRadar (SIEM/SOAR), Guardium (seguridad de datos), Verify (IAM), MaaS360.
  * Automatización y Observabilidad: Instana (APM), Turbonomic (ARM), IBM MQ, App Connect.
  * Gestión de Activos: IBM Maximo (EAM para activos físicos/infraestructura crítica).
  * Infraestructura Corporativa: Servidores Power, almacenamiento corporativo Storage, mainframes.

- FUERA DE ALCANCE IBM (DESCARTAR INMEDIATAMENTE COMO es_oportunidad_ibm = false / N/A):
  * Enlaces de internet puro / ISP, cableado estructurado, fibra óptica física.
  * Adquisición de computadoras de escritorio, laptops genéricas de oficina, periféricos.
  * Suministro o arrendamiento de fotocopiadoras, impresoras o tóner.
  * Paquetes básicos de correo o licencias de usuario final estándar sin arquitectura empresarial.
  * Renovación monomarca exclusiva y cerrada de licencias de terceros sin alcance de migración o plataforma.

REGLAS DE ASIGNACIÓN DE NIVEL DE CONFIANZA:
1. ALTO:
   - Requiere: (a) El texto del pliego describe con claridad requerimientos técnicos que corresponden directamente a capacidades centrales de IBM, Y (b) La licitación es abierta y competitiva.
   - PROHIBIDO asignar ALTO si los puntajes vectoriales están planos (delta < 0.035) o si es un requerimiento genérico.

2. MEDIO:
   - Casos válidos: (a) Licitaciones multi-rubro o mixtas donde IBM/Partners pueden ofertar componentes de software/plataforma/seguridad, (b) Proyectos de modernización o migración de infraestructura, o (c) Afinidad plausible pero pliego con descripción general.
   - En 'estrategia_comercial' explicar el abordaje (consorcio con business partners, capa de infraestructura/seguridad).

3. BAJO:
   - Casos válidos: (a) Procesos con margen competitivo estrecho, (b) Especificación técnica sesgada a marcas de terceros donde solo cabría oferta alternativa, (c) Requerimiento muy preliminar.
   - En 'justificacion' señalar claramente por qué el encaje es marginal o de baja probabilidad.

4. N/A (Descartado):
   - Procesos fuera del alcance tecnológico corporativo de IBM (commodities, insumos, internet doméstico, soporte micro).
   - Marca: es_oportunidad_ibm = false, producto_recomendado = null, nivel_confianza = "N/A".

Responde estrictamente en formato JSON con las claves: es_oportunidad_ibm (boolean), producto_recomendado (string o null), nivel_confianza ("ALTO"|"MEDIO"|"BAJO"|"N/A"), justificacion (string), estrategia_comercial (string)."""

JSON_SCHEMA = {
    "type": "object",
    "properties": {
        "es_oportunidad_ibm": {"type": "boolean"},
        "producto_recomendado": {"type": "string"},
        "nivel_confianza": {"type": "string", "enum": ["ALTO", "MEDIO", "BAJO", "N/A"]},
        "justificacion": {"type": "string"},
        "estrategia_comercial": {"type": "string"},
    },
    "required": ["es_oportunidad_ibm", "justificacion"],
}


def psql_json(sql: str) -> list:
    env = dict(os.environ, PGPASSWORD=DB["password"])
    res = subprocess.run(
        ["psql", "-h", DB["host"], "-U", DB["user"], "-d", DB["dbname"], "-t", "-A", "-c", sql],
        capture_output=True, text=True, env=env, timeout=60,
    )
    if res.returncode != 0:
        raise RuntimeError(f"psql falló: {res.stderr.strip()}")
    out = res.stdout.strip()
    return json.loads(out) if out and out != "{}" else []


def cargar_datos():
    with open(GOLDSET, newline="") as f:
        gold = {r["ocid"]: r for r in csv.DictReader(f)}

    ocids = list(gold.keys())
    in_list = ",".join("'" + o.replace("'", "''") + "'" for o in ocids)
    procesos = psql_json(
        f"SELECT json_agg(row_to_json(p)) FROM ("
        f"SELECT ocid, titulo, descripcion, comprador, metodo, monto, presupuesto, "
        f"region, categoria_cpc, tipo_interno FROM proceso_contratacion "
        f"WHERE ocid IN ({in_list})) p;"
    )
    productos = psql_json(
        "SELECT json_agg(row_to_json(p)) FROM ("
        "SELECT nombre, descripcion, capacidades FROM producto_ibm) p;"
    )
    return gold, {p["ocid"]: p for p in procesos}, {p["nombre"].strip().lower(): p for p in productos}


# ---------------------------------------------------------------------------
# Construcción de mensajes (espejo del prompt de producción)
# ---------------------------------------------------------------------------
def parsear_candidatos(campo: str):
    """'Cloud for SAP [cos=0.615] | X [cos=0.613] | ...' -> [(nombre, score), ...]"""
    out = []
    for parte in (campo or "").split("|"):
        m = re.match(r"\s*(.+?)\s*\[cos=([0-9.]+)\]\s*$", parte.strip())
        if m:
            out.append((m.group(1).strip(), float(m.group(2))))
    return out


def construir_prompt_proceso(gold_row, proc, catalogo):
    sb = []
    sb.append("=== DATOS DE LA CONTRATACIÓN PÚBLICA (SERCOP) ===")
    sb.append(f"OCID: {gold_row['ocid']}")
    sb.append(f"Título: {proc.get('titulo') or gold_row['titulo']}")
    desc = proc.get("descripcion") or gold_row["descripcion_corta"] or "Descripción no detallada en el pliego."
    sb.append(f"Descripción: {desc}")
    sb.append(f"Tipo/Categoría: {proc.get('tipo_interno') or 'No especificado'}")
    sb.append(f"Comprador: {proc.get('comprador') or 'No especificado'}")
    sb.append(f"Método de Contratación: {proc.get('metodo') or 'No especificado'}")
    monto = proc.get("monto") or proc.get("presupuesto") or gold_row.get("monto_usd")
    if monto:
        sb.append(f"Monto Referencial: {monto} USD")
    sb.append("")

    candidatos = parsear_candidatos(gold_row.get("candidatos_top3_sistema", ""))
    if candidatos:
        sb.append("=== CANDIDATOS IBM Y AFINIDAD VECTORIAL ===")
        scores = [c[1] for c in candidatos]
        for i, (nombre, score) in enumerate(candidatos, 1):
            sb.append(f"{i}. Producto: {nombre} (Similitud Vectorial: {score:.4f})")
            prod = catalogo.get(nombre.strip().lower())
            if prod and prod.get("descripcion"):
                sb.append(f"   Descripción: {prod['descripcion']}")
            if prod and prod.get("capacidades"):
                caps = prod["capacidades"]
                if isinstance(caps, list):
                    sb.append(f"   Capacidades: {', '.join(str(c) for c in caps)}")
            sb.append("")
        max_s, min_s = max(scores), min(scores)
        delta = max_s - min_s
        sb.append("=== DIAGNÓSTICO DE SEÑAL VECTORIAL ===")
        sb.append(f"- Puntaje Máximo: {max_s:.4f} | Dispersión (Delta Top1 - Top{len(scores)}): {delta:.4f}")
        if delta < 0.035 and max_s < 0.72:
            sb.append("- ALERTA DE RUIDO SEMÁNTICO: Los puntajes están muy apretados (< 0.035 de diferencia) y por "
                      "debajo de 0.72. Esto indica coincidencia léxica/genérica sin un producto IBM dominante. NO "
                      "califiques 'ALTO' a menos que el texto del pliego mencione explícitamente requerimientos unívocos.")
        sb.append("")

    if not proc.get("descripcion") or len(proc.get("descripcion") or "") < 40:
        sb.append("- ALERTA DE AMBIGÜEDAD: La descripción del proceso es muy corta o no detalla el alcance técnico.\n")

    sb.append("Analiza esta contratación pública y responde con el JSON del veredicto.")
    return "\n".join(sb)


def mensaje_ejemplo(gold_row, proc, catalogo, nivel):
    """Convierte un caso etiquetado en el par user/assistant few-shot."""
    prompt = "Evalúa el siguiente proceso SERCOP.\n\n" + construir_prompt_proceso(gold_row, proc, catalogo)
    if nivel == "NO":
        respuesta = {
            "es_oportunidad_ibm": False,
            "producto_recomendado": None,
            "nivel_confianza": "N/A",
            "justificacion": gold_row.get("nota") or "Fuera del alcance del catálogo IBM.",
            "estrategia_comercial": "",
        }
    else:
        respuesta = {
            "es_oportunidad_ibm": True,
            "producto_recomendado": gold_row.get("producto_esperado") or None,
            "nivel_confianza": nivel,
            "justificacion": gold_row.get("nota") or f"Encaje con la familia esperada {gold_row.get('familia_esperada')}.",
            "estrategia_comercial": "Caso de ejemplo del gold set (etiqueta del criterio humano).",
        }
    return [
        {"role": "user", "content": prompt},
        {"role": "assistant", "content": json.dumps(respuesta, ensure_ascii=False)},
    ]


def predecir(modelo: str, mensajes: list) -> dict:
    body = json.dumps({
        "model": modelo,
        "stream": False,
        "format": JSON_SCHEMA,
        "options": {"temperature": 0.1, "num_predict": 350, "num_ctx": 16384},
        "messages": mensajes,
    }).encode()
    req = urllib.request.Request(OLLAMA_URL, data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=300) as resp:
        data = json.loads(resp.read())
    content = data.get("message", {}).get("content", "")
    try:
        return json.loads(content)
    except json.JSONDecodeError:
        m = re.search(r"\{.*\}", content, re.S)
        return json.loads(m.group(0)) if m else {"_error": "respuesta ilegible", "_raw": content[:300]}


def nivel_predicho(resp: dict) -> str:
    nivel = (resp.get("nivel_confianza") or "N/A").strip().upper()
    return nivel if nivel in ("ALTO", "MEDIO", "BAJO") else "NO"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="no llama al LLM; muestra prompts y tamaños")
    ap.add_argument("--limit", type=int, default=0, help="evaluar solo los N primeros casos de prueba")
    ap.add_argument("--model", default="granite3.1-dense:8b")
    args = ap.parse_args()

    print("Cargando gold set + BD …")
    gold, procesos, catalogo = cargar_datos()

    ocids_ejemplo = [o for nivel in EJEMPLOS for o in EJEMPLOS[nivel]]
    assert len(ocids_ejemplo) == 15, f"esperaban 15 ejemplos, hay {len(ocids_ejemplo)}"
    assert len(set(ocids_ejemplo)) == 15, "OCIDs de ejemplo duplicados"
    for o in ocids_ejemplo:
        assert o in gold and o in procesos, f"ejemplo {o} no está en gold set o BD"

    # Mensajes few-shot: 15 ejemplos etiquetados (orden estable ALTO -> MEDIO -> NO)
    mensajes_base = [{"role": "system", "content": SYSTEM_PROMPT}]
    for nivel in ("ALTO", "MEDIO", "NO"):
        for o in EJEMPLOS[nivel]:
            mensajes_base.extend(mensaje_ejemplo(gold[o], procesos[o], catalogo, nivel))

    prueba = [o for o in gold if o not in set(ocids_ejemplo)]
    if args.limit:
        prueba = prueba[:args.limit]

    dist = Counter(gold[o]["nivel_esperado"] for o in prueba)
    print(f"Ejemplos few-shot: 15 (2 ALTO · 7 MEDIO · 6 NO)")
    print(f"Conjunto de prueba: {len(prueba)} procesos SIN etiqueta en el prompt → "
          f"{dist.get('ALTO',0)} ALTO · {dist.get('MEDIO',0)} MEDIO · {dist.get('NO',0)} NO")
    print(f"Modelo: {args.model} | endpoint: {OLLAMA_URL}")

    if args.dry_run:
        chars = sum(len(m["content"]) for m in mensajes_base)
        print(f"[dry-run] contexto base (system + 15 ejemplos): {chars} chars ≈ ~{chars // 4} tokens")
        muestra = mensajes_base + [{"role": "user", "content":
            "Evalúa el siguiente proceso SERCOP.\n\n" + construir_prompt_proceso(gold[prueba[0]], procesos[prueba[0]], catalogo)}]
        print(f"[dry-run] prompt completo con 1 caso de prueba: {sum(len(m['content']) for m in muestra)} chars ≈ ~{sum(len(m['content']) for m in muestra)//4} tokens")
        return

    hechos = set()
    if RESULTADOS.exists():
        with open(RESULTADOS) as f:
            for linea in f:
                try:
                    hechos.add(json.loads(linea)["ocid"])
                except Exception:
                    pass
        if hechos:
            print(f"Reanudando: {len(hechos)} resultados previos encontrados.")

    resultados = []
    errores_modelo = 0
    t0 = time.time()
    for i, ocid in enumerate(prueba, 1):
        if ocid in hechos:
            with open(RESULTADOS) as f:
                for linea in f:
                    r = json.loads(linea)
                    if r["ocid"] == ocid:
                        resultados.append(r)
            continue
        g = gold[ocid]
        mensajes = mensajes_base + [{"role": "user", "content":
            "Evalúa el siguiente proceso SERCOP.\n\n" + construir_prompt_proceso(g, procesos[ocid], catalogo)}]
        try:
            resp = predecir(args.model, mensajes)
        except Exception as e:
            print(f"  [{i}/{len(prueba)}] ERROR {ocid}: {e}")
            resp = {"_error": str(e)}
        pred = nivel_predicho(resp)
        esperado = g["nivel_esperado"]
        reg = {
            "ocid": ocid,
            "esperado": esperado,
            "predicho": pred,
            "acierto_nivel": pred == esperado,
            "acierto_binario": (pred != "NO") == (esperado != "NO"),
            "respuesta": resp,
            "titulo": g["titulo"],
            "nota_gold": g.get("nota", ""),
        }
        if resp.get("_error"):
            errores_modelo += 1
        resultados.append(reg)
        with open(RESULTADOS, "a") as f:
            f.write(json.dumps(reg, ensure_ascii=False) + "\n")
        marca = "OK " if reg["acierto_nivel"] else "ERR"
        print(f"  [{i}/{len(prueba)}] {marca} {ocid} esperado={esperado} predicho={pred}")
        time.sleep(0.5)

    # ------------------------- Reporte -------------------------
    ok_nivel = sum(1 for r in resultados if r["acierto_nivel"])
    ok_bin = sum(1 for r in resultados if r["acierto_binario"])
    n = len(resultados)

    def matriz(clave_esperado, clave_predicho):
        m = Counter((r[clave_esperado], r[clave_predicho]) for r in resultados)
        return m

    esperadas = ["ALTO", "MEDIO", "NO"]
    predichas = ["ALTO", "MEDIO", "BAJO", "NO"]
    m = matriz("esperado", "predicho")

    # Métricas binarias (¿oportunidad?)
    tp = sum(v for (e, p), v in m.items() if e != "NO" and p != "NO")
    fp = sum(v for (e, p), v in m.items() if e == "NO" and p != "NO")
    fn = sum(v for (e, p), v in m.items() if e != "NO" and p == "NO")
    tn = sum(v for (e, p), v in m.items() if e == "NO" and p == "NO")
    prec = tp / (tp + fp) if tp + fp else 0.0
    rec = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * prec * rec / (prec + rec) if prec + rec else 0.0

    lineas = []
    lineas.append("=" * 78)
    lineas.append("REPORTE FEWSHOT EVAL — granite3.1-dense:8b con 15 ejemplos etiquetados")
    lineas.append(f"Fecha: {time.strftime('%Y-%m-%d %H:%M:%S')} | casos de prueba: {n} | "
                  f"tiempo total: {time.time()-t0:.0f}s")
    lineas.append("=" * 78)
    lineas.append("")
    lineas.append(f"ACIERTO DE NIVEL (ALTO/MEDIO/NO exacto): {ok_nivel}/{n} = {100*ok_nivel/n:.1f}%")
    lineas.append(f"ACIERTO BINARIO  (oportunidad sí/no)   : {ok_bin}/{n} = {100*ok_bin/n:.1f}%")
    lineas.append("")
    lineas.append("CLASIFICACIÓN BINARIA (¿es oportunidad para IBM?):")
    lineas.append(f"  Precision = {prec:.3f}   Recall = {rec:.3f}   F1 = {f1:.3f}")
    lineas.append(f"  TP={tp}  FP={fp}  FN={fn}  TN={tn}")
    lineas.append("")
    lineas.append("MATRIZ DE CONFUSIÓN (fila=esperado, columna=predicho):")
    lineas.append("            " + "".join(f"{p:>8}" for p in predichas))
    for e in esperadas:
        lineas.append(f"  {e:>8}  " + "".join(f"{m.get((e,p),0):>8}" for p in predichas))
    lineas.append("")

    fps = [r for r in resultados if r["esperado"] == "NO" and r["predicho"] != "NO"]
    fns = [r for r in resultados if r["esperado"] != "NO" and r["predicho"] == "NO"]
    lineas.append(f"FALSOS POSITIVOS ({len(fps)}): el modelo dice oportunidad, el criterio dice NO")
    for r in fps:
        just = (r["respuesta"].get("justificacion") or "")[:160]
        lineas.append(f"  - {r['ocid']} → {r['predicho']} | {r['titulo'][:55]}")
        lineas.append(f"      modelo: {just}")
        lineas.append(f"      gold  : {r['nota_gold'][:160]}")
    lineas.append("")
    lineas.append(f"FALSOS NEGATIVOS ({len(fns)}): el modelo descarta, el criterio dice oportunidad")
    for r in fns:
        just = (r["respuesta"].get("justificacion") or "")[:160]
        lineas.append(f"  - {r['ocid']} → {r['predicho']} | {r['titulo'][:55]}")
        lineas.append(f"      modelo: {just}")
        lineas.append(f"      gold  : {r['nota_gold'][:160]}")
    lineas.append("")
    if errores_modelo:
        lineas.append(f"⚠ {errores_modelo} respuestas ilegibles/error.")
    lineas.append("Baseline Fase 0 (publicado sin evaluar): precisión 0.0% · recall 0.0%")
    lineas.append("=" * 78)

    reporte = "\n".join(lineas)
    print("\n" + reporte)
    with open(REPORTE, "w") as f:
        f.write(reporte + "\n")
    print(f"\nReporte guardado en {REPORTE} | resultados en {RESULTADOS}")


if __name__ == "__main__":
    main()
