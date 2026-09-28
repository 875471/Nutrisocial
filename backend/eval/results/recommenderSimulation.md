# Evaluación del recomendador nutricional (núcleo 3.4)

Generado el 2026-09-29 con `npm run eval:recomendador`: 200 días simulados por escenario (semilla 2026) con las 31 recetas de la base de datos que tienen valores nutricionales.

Desviación final = distancia entre lo consumido en el día (recetas ya registradas + la elegida) y el objetivo. "Aleatoria" es el valor esperado de elegir al azar entre las recetas que no se pasan de las kcal que quedan (media de todas ellas); "Recomendador", la primera recomendación de `recommendRecipes`. La reducción relativa es 1 − (desviación media del recomendador / desviación media aleatoria). "Gana" es el porcentaje de días en que la desviación del recomendador es menor que la esperada al azar.

## Escenario «siguiente comida» (1 o 2 recetas ya registradas)

200 días simulados (0 perfiles descartados por no poder construir el escenario o porque ninguna receta cabía en el margen). Recetas ya registradas por día: 1,5 de media.

### Desviación en kcal

| Objetivo | Días | Objetivo medio (kcal) | Margen antes (kcal) | Aleatoria | Recomendador | Reducción (kcal) | Reducción relativa | Gana | Mejor posible |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Perder peso | 70 | 1916 | 1344 | 968 | 710 | 258 | 26,6 % | 100,0 % | 668 |
| Mantener | 64 | 2246 | 1689 | 1300 | 1010 | 290 | 22,3 % | 100,0 % | 975 |
| Ganar peso | 66 | 2786 | 2213 | 1826 | 1603 | 223 | 12,2 % | 100,0 % | 1504 |
| Todos | 200 | 2309 | 1741 | 1357 | 1101 | 257 | 18,9 % | 100,0 % | 1042 |

### Desviación en macronutrientes (g de proteínas + hidratos + grasas)

| Objetivo | Días | Desviación antes | Aleatoria | Recomendador | Reducción (g) | Reducción relativa | Gana |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Perder peso | 70 | 299 | 236 | 184 | 52 | 22,0 % | 97,1 % |
| Mantener | 64 | 364 | 294 | 231 | 63 | 21,4 % | 100,0 % |
| Ganar peso | 66 | 493 | 424 | 368 | 55 | 13,0 % | 100,0 % |
| Todos | 200 | 384 | 316 | 260 | 57 | 17,9 % | 99,0 % |

- Mediana de la reducción relativa por día: kcal 18,0 %, macros 18,1 %.
- La primera recomendación se pasa de las kcal que quedaban en el 1,0 % de los días (el motor admite pasarse hasta un 15 %; la estrategia aleatoria, por definición, nunca se pasa).
- "Mejor posible": desviación en kcal de la receta que mejor habría cerrado el día mirando solo las kcal, una cota de lo alcanzable con estas 31 recetas y una sola ración.
- Candidatas medias por día para la estrategia aleatoria: 29,0.


## Escenario «cierre del día» (quedan entre 300 y 900 kcal)

200 días simulados (0 perfiles descartados por no poder construir el escenario o porque ninguna receta cabía en el margen). Recetas ya registradas por día: 4,3 de media.

### Desviación en kcal

| Objetivo | Días | Objetivo medio (kcal) | Margen antes (kcal) | Aleatoria | Recomendador | Reducción (kcal) | Reducción relativa | Gana | Mejor posible |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Perder peso | 68 | 1842 | 696 | 328 | 58 | 270 | 82,2 % | 100,0 % | 52 |
| Mantener | 63 | 2449 | 671 | 311 | 54 | 257 | 82,7 % | 100,0 % | 44 |
| Ganar peso | 69 | 2833 | 694 | 330 | 63 | 267 | 81,0 % | 100,0 % | 54 |
| Todos | 200 | 2375 | 687 | 323 | 58 | 265 | 81,9 % | 100,0 % | 50 |

### Desviación en macronutrientes (g de proteínas + hidratos + grasas)

| Objetivo | Días | Desviación antes | Aleatoria | Recomendador | Reducción (g) | Reducción relativa | Gana |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Perder peso | 68 | 181 | 140 | 118 | 22 | 15,8 % | 77,9 % |
| Mantener | 63 | 192 | 147 | 102 | 46 | 31,1 % | 98,4 % |
| Ganar peso | 69 | 244 | 208 | 165 | 44 | 20,9 % | 100,0 % |
| Todos | 200 | 206 | 166 | 129 | 37 | 22,3 % | 92,0 % |

- Mediana de la reducción relativa por día: kcal 84,9 %, macros 23,9 %.
- La primera recomendación se pasa de las kcal que quedaban en el 10,5 % de los días (el motor admite pasarse hasta un 15 %; la estrategia aleatoria, por definición, nunca se pasa).
- "Mejor posible": desviación en kcal de la receta que mejor habría cerrado el día mirando solo las kcal, una cota de lo alcanzable con estas 31 recetas y una sola ración.
- Candidatas medias por día para la estrategia aleatoria: 24,5.
