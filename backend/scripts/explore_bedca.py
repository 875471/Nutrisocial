"""
Exploración de la estructura de datos que devuelve pybedca.

pybedca es una librería NO oficial que envuelve el servicio público de consultas
XML de BEDCA (https://www.bedca.net/bdpub/procquery.php); BEDCA no ofrece una API
oficial. Este script solo sirve para ver qué campos trae cada alimento antes de
escribir fetch_bedca.py.

Uso: python backend/scripts/explore_bedca.py   (requiere `pip install pybedca`)
"""

import dataclasses

from pybedca import BedcaClient


def describe(label, obj):
    print(f"\n== {label} ({type(obj).__name__}) ==")
    for field in dataclasses.fields(obj):
        print(f"  {field.name}: {getattr(obj, field.name)!r}")


def main():
    with BedcaClient() as client:
        public_api = [name for name in dir(client) if not name.startswith("_")]
        print("API pública de BedcaClient:", public_api)

        foods = client.get_all_foods()
        print(f"\nAlimentos en total: {len(foods)}")
        describe("Primer elemento de get_all_foods()", foods[0])

        food = client.get_food_by_id(foods[0].id)
        describe("Detalle con get_food_by_id()", food)
        describe("Campo nutrients", food.nutrients)

        print("\n== Macronutrientes relevantes ==")
        for key in ("energy", "protein", "carbohydrate", "fat"):
            value = getattr(food.nutrients, key)
            print(f"  {key}: component={value.component!r} value={value.value!r} unit={value.unit!r}")


if __name__ == "__main__":
    main()
