"""
Descarga de BEDCA los macronutrientes (por 100 g) de todos sus alimentos y los
guarda en backend/data/bedca_foods.json, que después carga prisma/seed.js.

IMPORTANTE: BEDCA (Base de Datos Española de Composición de Alimentos) no ofrece
una API oficial. Este script usa pybedca, una librería NO oficial que envuelve el
servicio público de consultas XML de la web de BEDCA. Como ese servicio puede
cambiar o dejar de responder sin aviso, el resultado se congela en un JSON
versionado en el repositorio y la aplicación nunca consulta BEDCA en vivo. Solo
hace falta volver a ejecutar este script para actualizar el dataset a propósito.

Uso (desde la raíz del repo, requiere `pip install pybedca`):
    python backend/scripts/fetch_bedca.py

El progreso se guarda en backend/data/bedca_progress.json cada pocos alimentos;
si el script se interrumpe, al relanzarlo continúa donde lo dejó.
"""

import json
import time
from pathlib import Path

from pybedca import BedcaClient

DATA_DIR = Path(__file__).resolve().parent.parent / "data"
OUTPUT_FILE = DATA_DIR / "bedca_foods.json"
PROGRESS_FILE = DATA_DIR / "bedca_progress.json"

PAUSE_SECONDS = 0.15   # entre peticiones, para no saturar el servicio
SAVE_EVERY = 25        # alimentos procesados entre guardados del progreso
MAX_RETRIES = 3


def nutrient_value(food_value, as_energy=False):
    """Devuelve el valor numérico del nutriente, o None si BEDCA no lo tiene.

    pybedca rellena los componentes ausentes con component=None, y las trazas
    llegan como el texto 'trace' (se toman como 0). La energía viene en kJ y la
    librería ya la convierte a kcal.
    """
    if food_value.component is None:
        return None
    if food_value.value == "trace":
        return 0.0
    raw = food_value.value.kcal if as_energy else food_value.value.to_unit("g")
    return float(raw)


def extract_macros(food):
    n = food.nutrients
    kcal = nutrient_value(n.energy, as_energy=True)
    protein = nutrient_value(n.protein)
    carbs = nutrient_value(n.carbohydrate)
    fat = nutrient_value(n.fat)
    if None in (kcal, protein, carbs, fat):
        return None
    # Un alimento con macronutrientes pero energía 0 es un registro incompleto
    # (pybedca convierte los valores vacíos en 0.0, no en None).
    if kcal == 0 and protein + carbs + fat > 1:
        return None
    return {
        "name": food.name_es.strip(),
        "kcal": round(kcal, 1),
        "protein": round(protein, 2),
        "carbs": round(carbs, 2),
        "fat": round(fat, 2),
        "source": "BEDCA",
    }


def fetch_with_retries(client, food_id):
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            return client.get_food_by_id(food_id)
        except Exception as error:  # red, HTTP o XML mal formado
            if attempt == MAX_RETRIES:
                raise
            print(f"  reintento {attempt} para {food_id}: {error}")
            time.sleep(2 * attempt)


def load_progress():
    if PROGRESS_FILE.exists():
        return json.loads(PROGRESS_FILE.read_text(encoding="utf-8"))
    return {"processed": [], "foods": [], "discarded": []}


def save_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    progress = load_progress()
    processed = set(progress["processed"])

    with BedcaClient() as client:
        previews = client.get_all_foods()
        pending = [p for p in previews if p.id not in processed]
        print(f"{len(previews)} alimentos en BEDCA, {len(pending)} pendientes")

        for index, preview in enumerate(pending, start=1):
            try:
                macros = extract_macros(fetch_with_retries(client, preview.id))
            except Exception as error:
                print(f"  error definitivo en {preview.id} ({preview.name_es}): {error}")
                macros = None
            if macros:
                progress["foods"].append(macros)
            else:
                progress["discarded"].append(preview.name_es)
            progress["processed"].append(preview.id)

            if index % SAVE_EVERY == 0:
                save_json(PROGRESS_FILE, progress)
                print(f"  {index}/{len(pending)} procesados")
            time.sleep(PAUSE_SECONDS)

    save_json(PROGRESS_FILE, progress)

    # El seed hace upsert por nombre, así que no puede haber nombres repetidos.
    unique = {}
    for food in progress["foods"]:
        unique.setdefault(food["name"].lower(), food)
    foods = sorted(unique.values(), key=lambda f: f["name"].lower())
    save_json(OUTPUT_FILE, foods)

    print(f"Guardados {len(foods)} alimentos en {OUTPUT_FILE}")
    print(f"Descartados por datos incompletos o errores: {len(progress['discarded'])}")


if __name__ == "__main__":
    main()
