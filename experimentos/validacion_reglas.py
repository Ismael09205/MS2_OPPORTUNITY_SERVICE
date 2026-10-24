#!/usr/bin/env python3
"""
Paso A — Validación del filtro determinista (reglas duras) contra los 120 casos
etiquetados de los gold sets v1 + v2. SOLO LECTURA — sin LLM, sin túnel.

Pregunta que responde: ¿alguna regla mataría un positivo (ALTO/MEDIO) o un REVISAR?

Reglas acordadas con el usuario (2026-10-23):
  R1 CAT_CONSULTORIA : categoria_cpc = DESARROLLO_CONSULTORIA_TI
  R2 ANTIVIRUS       : "antivirus" en título o descripción
  R3 CADDIB          : \bCAD\b o \bBIM\b SOLO en descripción (nunca solo por título)
  R4 CONTENIDO       : Web of Science / SciELO / Scopus / Wolfram
  R5 RENOV_MONOMARCA : "renovación" + marca de tercero nombrada + sin marca IBM nombrada

Salida: experimentos/reporte_reglas.txt
"""

import csv
import json
import os
import re
import subprocess
import unicodedata
from collections import Counter
from pathlib import Path

OUT = Path(__file__).resolve().parent / "reporte_reglas.txt"
DB = {"host": "localhost", "user": "ismaeln", "dbname": "sercop_db", "password": "123456"}

TERCEROS = ["sophos", "fortinet", "palo alto", "cisco", "oracle", "microsoft", "adobe",
            "eset", "kaspersky", "trend micro", "mcafee", "symantec", "broadcom",
            "vmware", "citrix", "esri", "autocad", "veeam", "docuware", "olymp",
            "zimbra", "solarwinds", "laserfiche", "scopus"]
IBM_MARCAS = ["ibm", "qradar", "guardium", "verify", "maas360", "instana", "turbonomic",
              "maximo", "db2", "watsonx", "openshift", "cloud pak", "sterling",
              "app connect", "storage defender", "bigfix", "cognos", "spss",
              "planning analytics", "aix", "power", "mq"]

REGLAS = ["R1_CAT_CONSULTORIA", "R2_ANTIVIRUS", "R3_CADDIB", "R4_CONTENIDO", "R5_RENOV_MONOMARCA"]


def norm(s: str) -> str:
    s = unicodedata.normalize("NFKD", s or "")
    s = "".join(c for c in s if not unicodedata.combining(c))
    return s.lower()


def aplicar_reglas(titulo: str, descripcion: str, categoria: str):
    """Devuelve lista de reglas que disparan."""
    t, d, cat = norm(titulo), norm(descripcion), norm(categoria)
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
    tiene_renov = "renovacion" in texto
    tiene_tercero = any(m in texto for m in TERCEROS)
    tiene_ibm = any(m in texto for m in IBM_MARCAS)
    if tiene_renov and tiene_tercero and not tiene_ibm:
        hits.append("R5_RENOV_MONOMARCA")
    return hits, {"renov": tiene_renov, "tercero": tiene_tercero, "ibm": tiene_ibm}


def main():
    # ---- gold sets combinados (120) ----
    def load(p):
        with open(p, newline="") as f:
            return list(csv.DictReader(f))
    gold = {}
    for r in load("src/test/resources/goldset_oportunidades.csv") + load("experimentos/goldset_v2_CONGELADO.csv"):
        gold[r["ocid"]] = r
    casos = [r for r in gold.values() if r.get("nivel_esperado")]
    ocids = [r["ocid"] for r in casos]
    in_list = ",".join("'" + o.replace("'", "''") + "'" for o in ocids)

    env = dict(os.environ, PGPASSWORD=DB["password"])
    res = subprocess.run(
        ["psql", "-h", DB["host"], "-U", DB["user"], "-d", DB["dbname"], "-t", "-A", "-c",
         f"SELECT json_agg(row_to_json(p)) FROM (SELECT ocid, titulo, descripcion, categoria_cpc "
         f"FROM proceso_contratacion WHERE ocid IN ({in_list})) p;"],
        capture_output=True, text=True, env= env, timeout=60)
    if res.returncode != 0:
        raise RuntimeError(res.stderr)
    procs = {p["ocid"]: p for p in json.loads(res.stdout.strip() or "[]")}
    faltan = [o for o in ocids if o not in procs]

    L = []
    L.append("=" * 92)
    L.append("VALIDACIÓN DE REGLAS DURAS — 120 casos etiquetados (gold v1 + v2) — solo lectura")
    L.append("=" * 92)
    L.append(f"Casos evaluados: {len(casos)} | sin fila en BD (no evaluables): {len(faltan)}")
    L.append("")

    muertes_por_regla = {r: [] for r in REGLAS}
    fuga, revisar_cazado, no_ok = [], [], []
    positivos_salvados = []   # contienen palabra clave pero ninguna regla disparó
    kd = []

    for g in casos:
        o = g["ocid"]
        if o not in procs:
            continue
        p = procs[o]
        hits, flags = aplicar_reglas(p.get("titulo"), p.get("descripcion"), p.get("categoria_cpc"))
        esp = g["nivel_esperado"]
        for h in hits:
            muertes_por_regla[h].append((o, esp, (g.get("titulo") or "")[:58]))
        if hits:
            if esp in ("ALTO", "MEDIO"):
                fuga.append((o, esp, hits, (p.get("titulo") or "")[:70]))
            elif esp == "REVISAR":
                revisar_cazado.append((o, hits, (p.get("titulo") or "")[:70]))
            else:
                no_ok.append(o)
        else:
            # positivos que contienen palabras clave pero fueron salvados (efecto de las excepciones)
            if esp in ("ALTO", "MEDIO"):
                d = norm(p.get("descripcion"))
                t = norm(p.get("titulo"))
                kw = []
                if re.search(r"\b(cad|bim)\b", t) and not re.search(r"\b(cad|bim)\b", d):
                    kw.append("CAD/BIM solo en título (salvado por excepción)")
                if "renovacion" in (t + d) and flags["ibm"]:
                    kw.append("renovación con marca IBM nombrada (salvado por R5)")
                if "renovacion" in (t + d) and flags["tercero"] and not flags["ibm"]:
                    kw.append("!!! renovación+tercero sin IBM y aun así no murió (revisar listas)")
                if kw:
                    positivos_salvados.append((o, esp, kw, (p.get("titulo") or "")[:70]))
            kd.append(o)

    # ---- tabla por regla ----
    L.append("MUERTES POR REGLA (deberían ser todas gold=NO):")
    L.append(f"  {'regla':22} {'muertes':>7} {'NO':>4} {'ALTO/MEDIO':>11} {'REVISAR':>8}")
    for r in REGLAS:
        v = muertes_por_regla[r]
        c = Counter(e for _, e, _ in v)
        L.append(f"  {r:22} {len(v):7} {c.get('NO',0):4} {c.get('ALTO',0)+c.get('MEDIO',0):11} {c.get('REVISAR',0):8}")
    L.append("")

    # ---- fugas ----
    if fuga:
        L.append(f"!!! FUGAS — positivos que las reglas matarían ({len(fuga)}):")
        for o, e, h, tit in fuga:
            L.append(f"  - {o} | gold={e} | reglas={h}")
            L.append(f"      {tit}")
    else:
        L.append("FUGAS: 0 — ninguna regla mata un ALTO/MEDIO. ✓")
    if revisar_cazado:
        L.append(f"REVISAR que las reglas matarían ({len(revisar_cazado)}):")
        for o, h, tit in revisar_cazado:
            L.append(f"  - {o} | reglas={h} | {tit}")
    L.append("")

    # ---- efecto total ----
    matados = len(no_ok) + len(fuga) + len(revisar_cazado)
    L.append(f"EFECTO TOTAL: reglas matan {matados} de {len(casos)} casos etiquetados")
    L.append(f"  - correctos (gold=NO) : {len(no_ok)}")
    L.append(f"  - fugas (ALTO/MEDIO)  : {len(fuga)}")
    L.append(f"  - REVISAR matados     : {len(revisar_cazado)}")
    restantes = [g["nivel_esperado"] for g in casos if g["ocid"] in kd or (g["ocid"] not in procs)]
    L.append(f"  - queda para el LLM   : {len(restantes)} -> {dict(Counter(restantes))}")
    L.append("")

    # ---- positivos salvados ----
    L.append("POSITIVOS QUE CONTIENEN PALABRAS CLAVE PERO LAS REGLAS LOS SALVARON:")
    if positivos_salvados:
        for o, e, kw, tit in positivos_salvados:
            L.append(f"  - {o} | gold={e} | {'; '.join(kw)}")
            L.append(f"      {tit}")
    else:
        L.append("  (ninguno)")
    L.append("")

    # ---- detalle de muertes ----
    L.append("DETALLE DE MUERTES POR REGLA:")
    for r in REGLAS:
        v = muertes_por_regla[r]
        if v:
            L.append(f"  {r} ({len(v)}):")
            for o, e, tit in v:
                L.append(f"    - [{e}] {tit}")
    L.append("")
    L.append("=" * 92)

    reporte = "\n".join(L)
    print(reporte)
    with open(OUT, "w") as f:
        f.write(reporte + "\n")
    print(f"\nReporte: {OUT}")


if __name__ == "__main__":
    main()
