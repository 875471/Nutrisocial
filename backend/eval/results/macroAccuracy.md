# Evaluación del cálculo de macronutrientes (núcleo 3.2)

Generado el 2026-09-29 con `npm run eval:macros` sobre 14 recetas de `eval/data/macroReferenceRecipes.json`. Open Food Facts desactivado (solo BEDCA). Valores **por ración**.

> **Resultados PROVISIONALES.** 14 de las 14 recetas tienen valores de referencia sin verificar (borrador escrito con valores típicos de tablas, ver el LEEME de `eval/data/macroReferenceRecipes.json`). No citar estos números en la memoria hasta revisar las referencias y volver a ejecutar con `--solo-verificadas`.

## Error por macronutriente

| Macronutriente | Error medio absoluto | Mediana del error absoluto | Error medio porcentual | Mediana del error porcentual |
| --- | --- | --- | --- | --- |
| Energía (kcal) | 32,4 | 13,5 | 11,2 % | 3,8 % |
| Proteínas (g) | 1,2 | 0,7 | 9,6 % | 7,4 % |
| Hidratos (g) | 2,3 | 2,0 | 7,8 % | 6,8 % |
| Grasas (g) | 1,9 | 1,3 | 12,3 % | 9,2 % |

- Error porcentual medio de los cuatro macronutrientes: **10,2 %**.
- Recetas con las kcal por ración a ±10 % de la referencia: 12/14; a ±20 %: 13/14.
- Sesgo medio de las kcal (positivo = la app da más que la referencia): 9,8 %.
- El error porcentual no se calcula cuando la referencia es menor de 1 (kcal o g), para no dividir por casi cero.

### Sin las recetas con un alimento incoherente en la tabla Food

- «Garbanzo, hervido» (BEDCA): 358,7 kcal/100 g, pero sus macros suman 132,9 kcal (4·P + 4·H + 9·G). Afecta a: Garbanzos con espinacas.

Es un error del dato de origen, no del cálculo. Sin esa receta (13 recetas):

| Macronutriente | Error medio absoluto | Mediana del error absoluto | Error medio porcentual | Mediana del error porcentual |
| --- | --- | --- | --- | --- |
| Energía (kcal) | 16,4 | 10,9 | 4,4 % | 3,2 % |
| Proteínas (g) | 1,1 | 0,7 | 9,1 % | 6,1 % |
| Hidratos (g) | 2,4 | 2,1 | 7,7 % | 6,4 % |
| Grasas (g) | 1,9 | 1,2 | 12,2 % | 8,2 % |

- Error porcentual medio de los cuatro macronutrientes: **8,4 %**.

## Resultados por receta (por ración)

| Receta | Verificada | kcal app / ref. | Error kcal | Proteínas app / ref. | Hidratos app / ref. | Grasas app / ref. | Sin contar |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Porridge de avena con plátano | no | 438 / 420 | 4,4 % | 16,0 / 14,4 | 67,0 / 62,5 | 11,4 / 11,3 | — |
| Tostadas con tomate y aceite | no | 287 / 293 | -2,1 % | 5,5 / 5,7 | 30,3 / 34,1 | 16,0 / 14,5 | — |
| Yogur griego con fresas y nueces | no | 342 / 340 | 0,6 % | 11,5 / 8,0 | 18,3 / 18,3 | 25,9 / 26,3 | — |
| Lentejas con verduras | no | 336 / 347 | -3,1 % | 19,9 / 19,2 | 43,7 / 44,8 | 8,7 / 8,1 | — |
| Garbanzos con espinacas | no | 482 / 241 | 99,9 % | 11,0 / 9,5 | 20,8 / 18,9 | 14,5 / 12,8 | — |
| Macarrones con tomate y atún | no | 561 / 557 | 0,8 % | 28,4 / 27,7 | 76,8 / 82,1 | 15,1 / 12,1 | — |
| Pollo al limón con arroz | no | 588 / 570 | 3,2 % | 40,2 / 40,1 | 66,5 / 61,9 | 17,5 / 16,7 | — |
| Espaguetis a la boloñesa | no | 714 / 664 | 7,5 % | 30,6 / 33,5 | 79,8 / 80,9 | 30,0 / 22,1 | — |
| Salmón a la plancha con verduras | no | 450 / 474 | -4,9 % | 30,7 / 32,7 | 7,3 / 7,0 | 33,5 / 33,6 | — |
| Crema de calabaza | no | 171 / 161 | 6,2 % | 3,8 / 3,3 | 19,6 / 22,1 | 8,6 / 7,0 | — |
| Tortilla de patatas | no | 385 / 369 | 4,4 % | 15,0 / 13,4 | 24,8 / 28,5 | 25,3 / 21,5 | — |
| Revuelto de champiñones y ajos tiernos | no | 284 / 247 | 14,6 % | 17,4 / 17,5 | 6,2 / 4,8 | 21,2 / 17,6 | — |
| Bizcocho de yogur | no | 361 / 351 | 2,6 % | 6,7 / 6,2 | 48,3 / 48,9 | 15,5 / 14,3 | — |
| Arroz con leche | no | 289 / 281 | 3,0 % | 6,9 / 7,1 | 50,0 / 47,9 | 6,6 / 6,3 | — |

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
| garbanzo hervido | Garbanzo, hervido (dato incoherente) | 400 |
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
