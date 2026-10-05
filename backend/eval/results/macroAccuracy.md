# Evaluación del cálculo de macronutrientes (núcleo 3.2)

Generado el 2026-10-01 con `npm run eval:macros -- --solo-verificadas` sobre 14 recetas de `eval/data/macroReferenceRecipes.json`. Open Food Facts desactivado (solo BEDCA). Valores **por ración**.

> Todas las recetas evaluadas tienen la referencia verificada.

## Error por macronutriente

| Macronutriente | Error medio absoluto | Mediana del error absoluto | Error medio porcentual | Mediana del error porcentual |
| --- | --- | --- | --- | --- |
| Energía (kcal) | 16,6 | 13,4 | 4,5 % | 3,9 % |
| Proteínas (g) | 0,8 | 0,2 | 5,3 % | 1,4 % |
| Hidratos (g) | 0,8 | 0,3 | 3,9 % | 0,8 % |
| Grasas (g) | 1,7 | 1,4 | 10,0 % | 9,4 % |

- Error porcentual medio de los cuatro macronutrientes: **5,9 %**.
- Recetas con las kcal por ración a ±10 % de la referencia: 13/14; a ±20 %: 14/14.
- Sesgo medio de las kcal (positivo = la app da más que la referencia): 4,1 %.
- El error porcentual no se calcula cuando la referencia es menor de 1 (kcal o g), para no dividir por casi cero.

## Resultados por receta (por ración)

| Receta | Verificada | kcal app / ref. | Error kcal | Proteínas app / ref. | Hidratos app / ref. | Grasas app / ref. | Sin contar |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Porridge de avena con plátano | sí | 438 / 437 | 0,2 % | 16,0 / 16,1 | 67,0 / 66,2 | 11,4 / 11,6 | — |
| Tostadas con tomate y aceite | sí | 287 / 275 | 4,5 % | 5,5 / 5,6 | 30,3 / 30,5 | 16,0 / 14,5 | — |
| Yogur griego con fresas y nueces | sí | 342 / 349 | -1,8 % | 11,5 / 11,5 | 18,3 / 19,8 | 25,9 / 25,9 | — |
| Lentejas con verduras | sí | 336 / 326 | 3,1 % | 19,9 / 19,8 | 43,7 / 43,0 | 8,7 / 8,0 | — |
| Garbanzos con espinacas | sí | 256 / 242 | 5,8 % | 11,0 / 9,1 | 20,8 / 15,7 | 14,5 / 13,6 | — |
| Macarrones con tomate y atún | sí | 561 / 532 | 5,5 % | 28,4 / 26,8 | 76,8 / 76,5 | 15,1 / 12,7 | — |
| Pollo al limón con arroz | sí | 588 / 574 | 2,5 % | 40,2 / 40,1 | 66,5 / 66,3 | 17,5 / 16,0 | — |
| Espaguetis a la boloñesa | sí | 714 / 662 | 7,8 % | 30,6 / 34,3 | 79,8 / 79,4 | 30,0 / 22,2 | — |
| Salmón a la plancha con verduras | sí | 450 / 438 | 2,9 % | 30,7 / 30,6 | 7,3 / 7,5 | 33,5 / 32,0 | — |
| Crema de calabaza | sí | 171 / 165 | 3,7 % | 3,8 / 3,8 | 19,6 / 19,8 | 8,6 / 7,8 | — |
| Tortilla de patatas | sí | 385 / 355 | 8,5 % | 15,0 / 13,6 | 24,8 / 24,5 | 25,3 / 22,6 | — |
| Revuelto de champiñones y ajos tiernos | sí | 284 / 255 | 11,2 % | 17,4 / 15,7 | 6,2 / 6,0 | 21,2 / 18,9 | — |
| Bizcocho de yogur | sí | 361 / 346 | 4,2 % | 6,7 / 6,1 | 48,3 / 48,0 | 15,5 / 14,3 | — |
| Arroz con leche | sí | 289 / 291 | -0,6 % | 6,9 / 7,0 | 50,0 / 49,9 | 6,6 / 6,8 | — |

## Emparejamiento de cada ingrediente

Alimento de la tabla Food elegido por `matchFood` y gramos estimados por la conversión de unidades, para localizar el origen de cada error.

**Porridge de avena con plátano**

| Ingrediente | Alimento asociado | Gramos (app) |
| --- | --- | --- |
| avena | Avena, cruda | 50 |
| leche | Leche de vaca, entera | 200 |
| plátano | Plátano | 120 |
| canela | Canela, en polvo | 1 |

**Tostadas con tomate y aceite**

| Ingrediente | Alimento asociado | Gramos (app) |
| --- | --- | --- |
| pan | Pan blanco, de barra | 120 |
| tomate | Tomate | 120 |
| aceite de oliva | Aceite de oliva | 30 |
| sal | Sal de mar | 1 |

**Yogur griego con fresas y nueces**

| Ingrediente | Alimento asociado | Gramos (app) |
| --- | --- | --- |
| yogur griego | Yogur griego | 125 |
| fresa | Fresa | 100 |
| nuez | Nuez | 20 |
| miel | Miel | 5 |

**Lentejas con verduras**

| Ingrediente | Alimento asociado | Gramos (app) |
| --- | --- | --- |
| lentejas | Lenteja, seca, cruda | 300 |
| cebolla | Cebolla | 150 |
| zanahoria | Zanahoria, cruda | 160 |
| pimiento rojo | Pimiento rojo, crudo | 160 |
| ajo | Ajo | 10 |
| aceite de oliva | Aceite de oliva | 30 |
| sal | Sal de mar | 1 |

**Garbanzos con espinacas**

| Ingrediente | Alimento asociado | Gramos (app) |
| --- | --- | --- |
| garbanzo hervido | Garbanzo, hervido | 400 |
| espinacas | Espinaca, picada, congelada, cruda | 250 |
| ajo | Ajo | 15 |
| comino | Comino | 5 |
| aceite de oliva | Aceite de oliva | 45 |

**Macarrones con tomate y atún**

| Ingrediente | Alimento asociado | Gramos (app) |
| --- | --- | --- |
| pasta | Pasta alimenticia, cruda | 400 |
| tomate frito | Tomate frito | 300 |
| atún en aceite | Atun en aceite vegetal | 160 |
| cebolla | Cebolla | 150 |
| queso mozzarella | Queso mozzarella | 100 |

**Pollo al limón con arroz**

| Ingrediente | Alimento asociado | Gramos (app) |
| --- | --- | --- |
| pechuga de pollo | Pollo, pechuga, con piel, crudo | 300 |
| zumo de limón | Zumo de limón, fresco | 45 |
| ajo | Ajo | 10 |
| arroz | Arroz | 150 |
| aceite de oliva | Aceite de oliva | 30 |

**Espaguetis a la boloñesa**

| Ingrediente | Alimento asociado | Gramos (app) |
| --- | --- | --- |
| pasta | Pasta alimenticia, cruda | 400 |
| carne picada de ternera | Carne picada | 400 |
| tomate triturado | Tomate, maduro, pelado y triturado, enlatado | 400 |
| cebolla | Cebolla | 150 |
| zanahoria | Zanahoria, cruda | 80 |
| aceite de oliva | Aceite de oliva | 30 |

**Salmón a la plancha con verduras**

| Ingrediente | Alimento asociado | Gramos (app) |
| --- | --- | --- |
| salmón | Salmón | 300 |
| calabacín | Calabacín | 250 |
| berenjena | Berenjena | 250 |
| aceite de oliva | Aceite de oliva | 30 |
| sal | Sal de mar | 1 |

**Crema de calabaza**

| Ingrediente | Alimento asociado | Gramos (app) |
| --- | --- | --- |
| calabaza | Calabaza, cruda | 800 |
| cebolla | Cebolla | 150 |
| patata | Patata, cruda | 170 |
| aceite de oliva | Aceite de oliva | 30 |
| sal | Sal de mar | 1 |

**Tortilla de patatas**

| Ingrediente | Alimento asociado | Gramos (app) |
| --- | --- | --- |
| patata | Patata, cruda | 600 |
| huevo | Huevo de gallina fresco | 360 |
| cebolla | Cebolla | 150 |
| aceite de oliva | Aceite de oliva | 60 |
| sal | Sal de mar | 1 |

**Revuelto de champiñones y ajos tiernos**

| Ingrediente | Alimento asociado | Gramos (app) |
| --- | --- | --- |
| champiñón | Champiñon | 250 |
| huevo | Huevo de gallina fresco | 240 |
| ajo | Ajo | 10 |
| aceite de oliva | Aceite de oliva | 15 |

**Bizcocho de yogur**

| Ingrediente | Alimento asociado | Gramos (app) |
| --- | --- | --- |
| yogur | Yogur, búlgaro | 125 |
| harina de trigo | Harina de trigo | 250 |
| azúcar | Azúcar blanca | 200 |
| huevo | Huevo de gallina fresco | 180 |
| aceite de girasol | Aceite de girasol | 100 |

**Arroz con leche**

| Ingrediente | Alimento asociado | Gramos (app) |
| --- | --- | --- |
| leche | Leche de vaca, entera | 1000 |
| arroz | Arroz | 150 |
| azúcar | Azúcar blanca | 120 |
| canela | Canela, en polvo | 5 |
