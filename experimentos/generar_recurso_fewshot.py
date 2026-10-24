#!/usr/bin/env python3
"""
Genera el recurso few-shot estático de producción a partir del gold set v2 CONGELADO.

La configuración oficial medida (corrida 6: 82.6%, 0 FP, reproducible) incluyó 11 ejemplos
few-shot construidos con las MISMAS funciones del script de evaluación (mismo prompt de proceso,
mismas respuestas, mismo orden). Este script materializa esos 11 pares (prompt, respuesta)
como recurso de clase para que el adaptador Java reproduzca el prompt medido sin necesidad
del gold set en runtime (decisión del usuario 2026-10-23: embeberlos como recurso estático).

NO llama al modelo: solo lee la BD (psql) y escribe el JSON de recursos.

Uso: python3 experimentos/generar_recurso_fewshot.py
Salida: src/main/resources/prompts/ejemplos_fewshot_c4.json
"""
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import fewshot_eval_v6 as ev  # reutiliza EJEMPLOS, cargar_datos() y mensaje_ejemplo()

SALIDA = Path(__file__).parent.parent / "src/main/resources/prompts/ejemplos_fewshot_c4.json"


def main():
    print("Cargando gold set v2 CONGELADO + BD ...")
    gold, procesos, catalogo = ev.cargar_datos()

    ocids_ejemplo = [o for nivel in ev.EJEMPLOS for o in ev.EJEMPLOS[nivel]]
    assert len(ocids_ejemplo) == 11 and len(set(ocids_ejemplo)) == 11, "los 11 OCIDs de ejemplo no cuadran"

    recursos = []
    for nivel in ("ALTO", "MEDIO", "REVISAR", "NO"):
        for ocid in ev.EJEMPLOS[nivel]:
            mensajes = ev.mensaje_ejemplo(gold[ocid], procesos[ocid], catalogo, nivel)
            recursos.append({
                "ocid": ocid,
                "nivel_ejemplo": nivel,
                "prompt": mensajes[0]["content"],
                "respuesta": json.loads(mensajes[1]["content"]),
            })

    SALIDA.parent.mkdir(parents=True, exist_ok=True)
    SALIDA.write_text(json.dumps(recursos, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"OK: {len(recursos)} ejemplos -> {SALIDA}")
    for r in recursos:
        print(f"  {r['nivel_ejemplo']:8s} {r['ocid']}")


if __name__ == "__main__":
    main()
