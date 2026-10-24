#!/usr/bin/env python3
"""
Corrida 6 — Gold set v2 CONGELADO, split 20/80 — REGLAS + LLM (prompt C4) + SEMILLA FIJA.

Identica a la corrida 5 salvo seed=42 en la llamada al modelo, para hacer el
experimento REPRODUCIBLE (mismo prompt + misma entrada -> misma respuesta).
Motivo: entre C4 y C5 el LLM cambio de respuesta en 4/34 casos (varianza con
temperature 0.1 y seed aleatoria).

Protocolo de reproducibilidad:
  run1 -> renombrar resultados_v6.jsonl a resultados_v6_run1.jsonl
  run2 -> re-correr el mismo script y comparar predicciones caso por caso.

Endpoint: tunel del usuario OLLAMA_URL=http://localhost:11435/api/chat (exigido).

Uso:
  python3 experimentos/fewshot_eval_v6.py --dry-run
  python3 experimentos/fewshot_eval_v6.py
"""

import argparse
import csv
import json
import os
import re
import subprocess
import sys
import time
import unicodedata
import urllib.request
from collections import Counter
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
GOLD_V2 = Path(__file__).resolve().parent / "goldset_v2_CONGELADO.csv"
OUT_DIR = Path(__file__).resolve().parent
RESULTADOS = OUT_DIR / "resultados_v6.jsonl"
REPORTE = OUT_DIR / "reporte_v6.txt"

OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://localhost:11435/api/chat")
DB = {"host": "localhost", "user": "ismaeln", "dbname": "sercop_db", "password": "123456"}

# --- Ejemplos few-shot (los "justos": 20% del v2) ---
EJEMPLOS = {
    "ALTO": ["ocds-5wno2w-SIE-EMASEO-EP-2026-014-276265"],  # EDR/XDR/NDR enterprise
    "MEDIO": [
        "ocds-5wno2w-RE-CEP-CNE-2026-005-2546",       # seguridad + commodity, direct
        "ocds-5wno2w-CPC-ESPOCH-2026-02-53390",       # consultoria BPMS mixto
        "ocds-5wno2w-RE-CEP-MDMQ-2026-46006-41357",   # SOC servicios gestionados
    ],
    "REVISAR": ["ocds-5wno2w-RE-CEP-SB-2026-002-2421"],  # renovacion nube sin saber que nube
    "NO": [
        "ocds-5wno2w-SIE-UEAEP-2026-0001-1041295",    # plataforma estudios tercero
        "ocds-5wno2w-SIE-CPEC-2026-003-3264",         # renovacion M365+Adobe
        "ocds-5wno2w-SIE-GADMAZ-2026-019-2422",       # BIM/CAD competidor
        "ocds-5wno2w-RE-PU-UTMACH-2026-002-60177",    # Web of Science contenido
        "ocds-5wno2w-SIE-GADMQ-2026-009-2453",        # firewall+antivirus Sophos
        "ocds-5wno2w-CPC-GADMC--2026-003-35903",      # digitalizacion servicio
    ],
}

SYSTEM_PROMPT = """Eres un arquitecto de soluciones y líder comercial de IBM para compras públicas y licitaciones del SERCOP. Tu misión es evaluar con estricto rigor técnico y comercial si un proceso de contratación representa una oportunidad real y viable para el portafolio corporativo de IBM o sus Business Partners. Debes rechazar falsos positivos derivados de coincidencias léxicas genéricas (ej: si piden internet básico o correo y dice 'nube', no corresponde a IBM Cloud o watsonx).

CRITERIOS DE ALCANCE Y CONTROL DE OPORTUNIDADES IBM:
- ALCANCE VÁLIDO IBM:
  * Nube Híbrida y Plataforma: Red Hat OpenShift, modernización de aplicaciones legadas, nube privada/híbrida empresarial.
  * Datos e IA: watsonx, plataformas de analítica empresarial, Db2, gobierno y linaje de datos.
  * Ciberseguridad Enterprise: QRadar (SIEM/SOAR), Guardium (seguridad de datos), Verify (IAM), MaaS360.
  * Automatización y Observabilidad: Instana (APM), Turbonomic (ARM), IBM MQ, App Connect.
  * Gestión de Activos: IBM Maximo (EAM para activos físicos/infraestructura crítica).
  * Infraestructura Corporativa: Servidores Power, almacenamiento corporativo Storage, mainframes.

- FUERA DE ALCANCE IBM (decision NO):
  * Enlaces de internet puro / ISP, cableado estructurado, fibra óptica física.
  * Computadoras, laptops, periféricos, fotocopiadoras, impresoras o tóner.
  * Paquetes básicos de correo o licencias de usuario final estándar sin arquitectura empresarial.
  * Ofimática y productividad de usuario (commodity), incluidas renovaciones grandes.
  * Renovación monomarca exclusiva y cerrada de licencias de terceros sin alcance de migración o plataforma.
  * CAD/BIM y software de diseño de terceros sin equivalente IBM.
  * Contenido propietario de editores/base de datos de terceros (SciELO, WoS, Scopus, Wolfram, etc.).
  * Software vertical de un solo giro (educativo, tránsito, bibliotecas, GIS) sin componente de plataforma IBM.
  * Soporte, garantía, mantenimiento o actualización de una plataforma existente.
  * Consultoría, auditoría, digitalización, desarrollo a medida, operación de servicios o capacitación: si el objeto central es el SERVICIO PROFESIONAL, la decisión es NO aunque en el texto se citen productos IBM como referencia. El objeto central manda.
  * Contenido propietario de terceros claramente identificable (bases de datos de citas/revistas, geodatos, documentación técnica de fabricante, catálogos editoriales): NO seco, nunca REVISAR.

REGLAS DE NIVEL:
- ALTO: requerimientos técnicos del pliego que corresponden directamente a capacidades centrales de IBM Y licitación abierta y competitiva.
- MEDIO: alcance mixto donde IBM/Partners pueden ofertar parte (nube, datos, seguridad, plataforma) aunque otra parte sea de terceros; modernización/migración; afinidad plausible con descripción general.

REGLAS DE ABSTENCIÓN — REVISAR es EXCEPCIONAL, úsalo solo cuando:
  a) Alcance mixto real y no discernible: el objeto central es parcialmente IBM y parcialmente de terceros, y el hecho faltante cambiaría la decisión.
  b) Renovación de nube/servicio sin indicar de qué proveedor se renueva (podría ser monomarca ajena).
  c) Seguridad perimetral/hardware sin indicar la marca del producto (podría ser appliance de competidor).
  d) Un proveedor ya instalado podría vetar la oportunidad Y el pliego no revela quién es.
  NO uses REVISAR por descripción corta o vaga si el objeto central ya se identifica: si cae en una categoría de descarte, descarta (NO); si encaja de lleno en IBM, decide.
  REVISAR NO es un descarte: es "con esta información no puedo decidir responsablemente". Si la respuesta cae claramente en ALTO/MEDIO o en NO, decídete.

Responde estrictamente en formato JSON: {"decision": "SI"|"NO"|"REVISAR", "nivel": "ALTO"|"MEDIO"|"NA", "producto_recomendado": string|null, "justificacion": string}

Reglas de coherencia: decision=SI exige nivel ALTO o MEDIO — NUNCA respondas SI con nivel NA; si es oportunidad pero no decides entre ALTO y MEDIO, elige MEDIO. decision=NO o REVISAR exige nivel="NA". producto_recomendado solo si decision=SI."""

JSON_SCHEMA = {
    "type": "object",
    "properties": {
        "decision": {"type": "string", "enum": ["SI", "NO", "REVISAR"]},
        "nivel": {"type": "string", "enum": ["ALTO", "MEDIO", "NA"]},
        "producto_recomendado": {"type": "string"},
        "justificacion": {"type": "string"},
    },
    "required": ["decision", "justificacion"],
}

# ---------------- Filtro determinista (Paso A validado: 33/33 NO, 0 fugas) ----------------
TERCEROS = ["sophos", "fortinet", "palo alto", "cisco", "oracle", "microsoft", "adobe",
            "eset", "kaspersky", "trend micro", "mcafee", "symantec", "broadcom",
            "vmware", "citrix", "esri", "autocad", "veeam", "docuware", "olymp",
            "zimbra", "solarwinds", "laserfiche", "scopus"]
IBM_MARCAS = ["ibm", "qradar", "guardium", "verify", "maas360", "instana", "turbonomic",
              "maximo", "db2", "watsonx", "openshift", "cloud pak", "sterling",
              "app connect", "storage defender", "bigfix", "cognos", "spss",
              "planning analytics", "aix", "power", "mq"]


def _norm(s: str) -> str:
    s = unicodedata.normalize("NFKD", s or "")
    s = "".join(c for c in s if not unicodedata.combining(c))
    return s.lower()


def aplicar_reglas(titulo: str, descripcion: str, categoria: str) -> list:
    t, d, cat = _norm(titulo), _norm(descripcion), _norm(categoria)
    texto = t + " " + d
    hits = []
    if cat.strip() == "desarrollo_consultoria_ti":
        hits.append("R1_CAT_CONSULTORIA")
    if "antivirus" in texto:
        hits.append("R2_ANTIVIRUS")
    if re.search(r"\b(cad|bim)\b", d):          # solo descripción (excepción del usuario)
        hits.append("R3_CADDIB")
    if any(k in texto for k in ("web of science", "scielo", "scopus", "wolfram")):
        hits.append("R4_CONTENIDO")
    if "renovacion" in texto and any(m in texto for m in TERCEROS) \
            and not any(m in texto for m in IBM_MARCAS):
        hits.append("R5_RENOV_MONOMARCA")
    return hits


def psql_json(sql: str) -> list:
    env = dict(os.environ, PGPASSWORD=DB["password"])
    res = subprocess.run(
        ["psql", "-h", DB["host"], "-U", DB["user"], "-d", DB["dbname"], "-t", "-A", "-c", sql],
        capture_output=True, text=True, env=env, timeout=60,
    )
    if res.returncode != 0:
        raise RuntimeError(f"psql fallo: {res.stderr.strip()}")
    out = res.stdout.strip()
    return json.loads(out) if out and out != "{}" else []


def cargar_datos():
    with open(GOLD_V2, newline="") as f:
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
        "SELECT json_agg(row_to_json(p)) FROM (SELECT nombre, descripcion, capacidades FROM producto_ibm) p;"
    )
    return gold, {p["ocid"]: p for p in procesos}, {p["nombre"].strip().lower(): p for p in productos}


def parsear_candidatos(campo: str):
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
                      "debajo de 0.72. NO califiques 'ALTO' a menos que el pliego mencione requerimientos unívocos.")
        sb.append("")

    if not proc.get("descripcion") or len(proc.get("descripcion") or "") < 40:
        sb.append("- ALERTA DE AMBIGÜEDAD: La descripción del proceso es muy corta o no detalla el alcance técnico.\n")

    sb.append("Analiza esta contratación pública y responde con el JSON del veredicto.")
    return "\n".join(sb)


def mensaje_ejemplo(gold_row, proc, catalogo, etiqueta):
    prompt = "Evalúa el siguiente proceso SERCOP.\n\n" + construir_prompt_proceso(gold_row, proc, catalogo)
    if etiqueta == "NO":
        respuesta = {"decision": "NO", "nivel": "NA", "producto_recomendado": None,
                     "justificacion": gold_row.get("nota") or "Fuera del alcance del catálogo IBM."}
    elif etiqueta == "REVISAR":
        respuesta = {"decision": "REVISAR", "nivel": "NA", "producto_recomendado": None,
                     "justificacion": gold_row.get("nota") or "Información insuficiente para decidir sin el pliego."}
    else:  # ALTO / MEDIO
        respuesta = {"decision": "SI", "nivel": etiqueta,
                     "producto_recomendado": gold_row.get("producto_esperado") or None,
                     "justificacion": gold_row.get("nota") or f"Encaje con la familia esperada {gold_row.get('familia_esperada')}."}
    return [
        {"role": "user", "content": prompt},
        {"role": "assistant", "content": json.dumps(respuesta, ensure_ascii=False)},
    ]


def predecir(modelo: str, mensajes: list, reintentos: int = 2) -> dict:
    body = json.dumps({
        "model": modelo,
        "stream": False,
        "format": JSON_SCHEMA,
        "options": {"temperature": 0.1, "num_predict": 350, "num_ctx": 16384, "seed": 42},
        "messages": mensajes,
    }).encode()
    ultimo_error = None
    for intento in range(reintentos):
        try:
            req = urllib.request.Request(OLLAMA_URL, data=body, headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(req, timeout=300) as resp:
                data = json.loads(resp.read())
            content = data.get("message", {}).get("content", "")
            try:
                return json.loads(content)
            except json.JSONDecodeError:
                m = re.search(r"\{.*\}", content, re.S)
                if m:
                    return json.loads(m.group(0))
                ultimo_error = "respuesta ilegible"
        except Exception as e:
            ultimo_error = str(e)
            time.sleep(2)
    return {"_error": ultimo_error or "error desconocido"}


def prediccion_a_clase(resp: dict) -> str:
    """Mapea la respuesta a la clase comparable con el gold: ALTO | MEDIO | NO | REVISAR."""
    if resp.get("_error"):
        return "ERROR"
    dec = (resp.get("decision") or "").strip().upper()
    if dec == "REVISAR":
        return "REVISAR"
    if dec == "NO":
        return "NO"
    if dec == "SI":
        nivel = (resp.get("nivel") or "").strip().upper()
        return nivel if nivel in ("ALTO", "MEDIO") else "SI_SIN_NIVEL"
    return "INVALIDA"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--model", default="granite3.1-dense:8b")
    args = ap.parse_args()

    print("Cargando gold set v2 CONGELADO + BD ...")
    gold, procesos, catalogo = cargar_datos()

    ocids_ejemplo = [o for nivel in EJEMPLOS for o in EJEMPLOS[nivel]]
    assert len(ocids_ejemplo) == 11, f"esperaban 11 ejemplos, hay {len(ocids_ejemplo)}"
    assert len(set(ocids_ejemplo)) == 11, "OCIDs de ejemplo duplicados"
    for o in ocids_ejemplo:
        assert o in gold and o in procesos, f"ejemplo {o} no esta en gold v2 o BD"

    mensajes_base = [{"role": "system", "content": SYSTEM_PROMPT}]
    for nivel in ("ALTO", "MEDIO", "REVISAR", "NO"):
        for o in EJEMPLOS[nivel]:
            mensajes_base.extend(mensaje_ejemplo(gold[o], procesos[o], catalogo, nivel))

    prueba = [o for o in gold if o not in set(ocids_ejemplo)]
    if args.limit:
        prueba = prueba[:args.limit]

    dist_ej = Counter()
    for nivel in EJEMPLOS:
        dist_ej[nivel] = len(EJEMPLOS[nivel])
    dist_pr = Counter(gold[o]["nivel_esperado"] for o in prueba)
    print(f"Ejemplos: 11/57 (~20%) -> {dict(dist_ej)}")
    print(f"Prueba: {len(prueba)} casos SIN etiqueta (~80%) -> {dict(dist_pr)}")
    print(f"Modelo: {args.model} | endpoint: {OLLAMA_URL}")

    if args.dry_run:
        chars = sum(len(m["content"]) for m in mensajes_base)
        print(f"[dry-run] contexto base: {chars} chars ~ {chars//4} tokens")
        from collections import Counter as _C
        kills = _C()
        for ocid in prueba:
            p = procesos[ocid]
            for h in aplicar_reglas(p.get("titulo"), p.get("descripcion"), p.get("categoria_cpc")):
                kills[h] += 1
        total_regla = sum(1 for ocid in prueba
                          if aplicar_reglas(procesos[ocid].get("titulo"), procesos[ocid].get("descripcion"),
                                            procesos[ocid].get("categoria_cpc")))
        print(f"[dry-run] reglas matarian {total_regla}/{len(prueba)} casos de prueba -> {dict(kills)}")
        print(f"[dry-run] el LLM veria {len(prueba)-total_regla} casos")
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
            print(f"Reanudando: {len(hechos)} resultados previos.")

    resultados = []
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
        proc = procesos[ocid]
        hits = aplicar_reglas(proc.get("titulo"), proc.get("descripcion"), proc.get("categoria_cpc"))
        if hits:
            # descarte determinista: sin llamada al modelo
            resp = {"decision": "NO", "nivel": "NA", "producto_recomendado": None,
                    "justificacion": f"Filtro determinista: {','.join(hits)} (descarte sin llamada al modelo)"}
            regla = True
        else:
            mensajes = mensajes_base + [{"role": "user", "content":
                "Evalúa el siguiente proceso SERCOP.\n\n" + construir_prompt_proceso(g, proc, catalogo)}]
            resp = predecir(args.model, mensajes)
            regla = False
        clase = prediccion_a_clase(resp)
        esperado = g["nivel_esperado"]
        reg = {
            "ocid": ocid,
            "esperado": esperado,
            "predicho": clase,
            "regla": regla,
            "respuesta": resp,
            "titulo": g["titulo"],
            "nota_gold": g.get("nota", ""),
        }
        resultados.append(reg)
        with open(RESULTADOS, "a") as f:
            f.write(json.dumps(reg, ensure_ascii=False) + "\n")
        marca = "OK " if clase == esperado else "DIF"
        origen = f"REG[{','.join(hits)}]" if regla else "LLM"
        print(f"  [{i}/{len(prueba)}] {marca} {ocid} esperado={esperado} predicho={clase} via={origen}")
        if not regla:
            time.sleep(0.3)

    # ---------------- Reporte ----------------
    n = len(resultados)
    m = Counter((r["esperado"], r["predicho"]) for r in resultados)
    clases_pred = ["ALTO", "MEDIO", "REVISAR", "NO", "SI_SIN_NIVEL", "ERROR", "INVALIDA"]
    clases_esp = ["ALTO", "MEDIO", "REVISAR", "NO"]

    # Binario: positivo real = ALTO/MEDIO; prediccion positiva = ALTO/MEDIO
    tp = sum(v for (e, p), v in m.items() if e in ("ALTO", "MEDIO") and p in ("ALTO", "MEDIO"))
    fp = sum(v for (e, p), v in m.items() if e == "NO" and p in ("ALTO", "MEDIO"))
    fn_fuga = sum(v for (e, p), v in m.items() if e in ("ALTO", "MEDIO") and p == "NO")
    fn_revision = sum(v for (e, p), v in m.items() if e in ("ALTO", "MEDIO") and p == "REVISAR")
    tn = sum(v for (e, p), v in m.items() if e == "NO" and p == "NO")
    costo_rev = sum(v for (e, p), v in m.items() if e == "NO" and p == "REVISAR")
    rev_ok = sum(v for (e, p), v in m.items() if e == "REVISAR" and p == "REVISAR")
    rev_gold_total = sum(1 for r in resultados if r["esperado"] == "REVISAR")
    total_rev_pred = sum(v for (e, p), v in m.items() if p == "REVISAR")
    prec = tp / (tp + fp) if tp + fp else 0.0
    acierto_nivel = sum(1 for r in resultados if r["predicho"] == r["esperado"])
    resueltos_regla = sum(1 for r in resultados if r.get("regla"))
    aciertos_regla = sum(1 for r in resultados if r.get("regla") and r["esperado"] == "NO")
    fugas_regla = sum(1 for r in resultados if r.get("regla") and r["esperado"] != "NO")
    tn_llm = sum(1 for r in resultados if r["esperado"] == "NO" and r["predicho"] == "NO" and not r.get("regla"))
    no_llm = sum(1 for r in resultados if r["esperado"] == "NO" and not r.get("regla"))

    lineas = []
    lineas.append("=" * 78)
    lineas.append("REPORTE CORRIDA 6 — gold set v2 CONGELADO | reglas + LLM (prompt C4) | split 20/80 | seed=42")
    lineas.append(f"Fecha: {time.strftime('%Y-%m-%d %H:%M:%S')} | prueba: {n} casos | {time.time()-t0:.0f}s")
    lineas.append("=" * 78)
    lineas.append("")
    lineas.append(f"ACIERTO DE CLASE EXACTA (incluye REVISAR): {acierto_nivel}/{n} = {100*acierto_nivel/n:.1f}%")
    lineas.append("")
    lineas.append("METRICAS DE PRODUCTO:")
    lineas.append(f"  Precision de SI (dice oportunidad -> lo es)      : {prec:.3f}  (TP={tp}, FP={fp})")
    lineas.append(f"  Fuga de positivos (dice NO, es oportunidad)      : {fn_fuga}   <- error grave")
    lineas.append(f"  Captura por cola (positivo -> REVISAR, aceptable): {fn_revision}")
    lineas.append(f"  Costo de revision (NO -> REVISAR, cola inutil)   : {costo_rev}")
    lineas.append(f"  Revisar bien capturado (gold REVISAR -> REVISAR) : {rev_ok}/{rev_gold_total}")
    lineas.append(f"  Acierto de NO (descartes correctos)              : {tn}/{sum(1 for r in resultados if r['esperado']=='NO')}")
    lineas.append(f"    - por regla (sin LLM)                          : {aciertos_regla}/{resueltos_regla} (fugas por regla: {fugas_regla})")
    lineas.append(f"    - por LLM                                      : {tn_llm}/{no_llm}")
    lineas.append(f"  Tasa total de REVISAR predecida                  : {total_rev_pred}/{n} = {100*total_rev_pred/n:.1f}%")
    lineas.append("")
    lineas.append("MATRIZ (fila=gold, columna=prediccion):")
    lineas.append("            " + "".join(f"{p:>11}" for p in clases_pred))
    for e in clases_esp:
        lineas.append(f"  {e:>8}  " + "".join(f"{m.get((e,p),0):>11}" for p in clases_pred))
    lineas.append("")

    def listar(titulo, filtro):
        items = [r for r in resultados if filtro(r)]
        lineas.append(f"{titulo} ({len(items)}):")
        for r in items:
            just = (r["respuesta"].get("justificacion") or "")[:150]
            lineas.append(f"  - {r['ocid']} | gold={r['esperado']} pred={r['predicho']} | {r['titulo'][:50]}")
            lineas.append(f"      modelo: {just}")
            lineas.append(f"      gold  : {r['nota_gold'][:150]}")
        lineas.append("")

    listar("FALSOS POSITIVOS (NO -> ALTO/MEDIO)",
           lambda r: r["esperado"] == "NO" and r["predicho"] in ("ALTO", "MEDIO"))
    listar("FUGAS (positivo -> NO)",
           lambda r: r["esperado"] in ("ALTO", "MEDIO") and r["predicho"] == "NO")
    listar("POSITIVOS A REVISAR (captura por cola)",
           lambda r: r["esperado"] in ("ALTO", "MEDIO") and r["predicho"] == "REVISAR")
    listar("COSTO DE REVISION (NO -> REVISAR)",
           lambda r: r["esperado"] == "NO" and r["predicho"] == "REVISAR")
    listar("DIFERENTES DE NIVEL (ALTO<->MEDIO)",
           lambda r: {r["esperado"], r["predicho"]} <= {"ALTO", "MEDIO"} and r["esperado"] != r["predicho"])

    lineas.append("Comparativa: C5 (reglas+LLM, sin seed) costo_rev=8 tasa_rev=23.9% | C4 (solo LLM) costo_rev=6 tasa_rev=19.6% acierto_NO=85%")
    lineas.append("=" * 78)

    reporte = "\n".join(lineas)
    print("\n" + reporte)
    with open(REPORTE, "w") as f:
        f.write(reporte + "\n")
    print(f"\nReporte: {REPORTE} | resultados: {RESULTADOS}")


if __name__ == "__main__":
    main()
