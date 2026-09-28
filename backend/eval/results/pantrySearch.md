# Evaluación del buscador por despensa (núcleo 3.3)

Generado el 2026-09-29 con `npm run eval:despensa`: 400 simulaciones (100 por nivel de cobertura, semilla 2026) sobre las 32 recetas de la base de datos, de las que 32 tienen 3 o más ingredientes y pueden ser objetivo. Cada despensa lleva los ingredientes elegidos de la receta objetivo más 3 ingredientes al azar de otras recetas que no le sirven.

precision@k es la proporción de simulaciones en las que la receta objetivo aparece entre las k primeras del resultado de `matchRecipesToPantry`. "Cobertura real" es la media de la fracción de ingredientes de la objetivo que había en la despensa (el porcentaje nominal se redondea al número entero de ingredientes). MRR es la media de 1/posición (0 si no aparece).

## Resultados

| Cobertura simulada | Simulaciones | Cobertura real | precision@1 | precision@5 | precision@10 | Aparece en la lista | MRR | Posición media (si aparece) | Recetas devueltas (media) | Con empates delante |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 100 % | 100 | 100 % | 94,0 % | 100,0 % | 100,0 % | 100,0 % | 0,968 | 1,1 | 4,9 | 6,0 % |
| 80 % | 100 | 78 % | 91,0 % | 100,0 % | 100,0 % | 100,0 % | 0,953 | 1,1 | 3,8 | 8,0 % |
| 60 % | 100 | 58 % | 77,0 % | 100,0 % | 100,0 % | 100,0 % | 0,873 | 1,3 | 2,4 | 13,0 % |
| 40 % | 100 | 41 % | 11,0 % | 25,0 % | 25,0 % | 25,0 % | 0,168 | 1,8 | 1,3 | 10,0 % |
| Todas | 400 | 69 % | 68,3 % | 81,3 % | 81,3 % | 81,3 % | 0,741 | 1,2 | 3,1 | 9,3 % |

- La función descarta las recetas con menos del 50 % de ingredientes cubiertos (`MIN_COVERAGE`), así que con una cobertura real por debajo de ese umbral la receta objetivo no puede aparecer: es una decisión de diseño (informe 11), no un fallo del emparejamiento.
- "Con empates delante" es el porcentaje de simulaciones en las que otra receta con la misma cobertura y el mismo número de faltas aparece antes que la objetivo: su orden entre ellas no depende de la despensa.
