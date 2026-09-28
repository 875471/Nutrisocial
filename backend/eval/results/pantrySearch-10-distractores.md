# Evaluación del buscador por despensa (núcleo 3.3)

Generado el 2026-09-29 con `npm run eval:despensa -- --distractores=10`: 400 simulaciones (100 por nivel de cobertura, semilla 2026) sobre las 32 recetas de la base de datos, de las que 32 tienen 3 o más ingredientes y pueden ser objetivo. Cada despensa lleva los ingredientes elegidos de la receta objetivo más 10 ingredientes al azar de otras recetas que no le sirven.

precision@k es la proporción de simulaciones en las que la receta objetivo aparece entre las k primeras del resultado de `matchRecipesToPantry`. "Cobertura real" es la media de la fracción de ingredientes de la objetivo que había en la despensa (el porcentaje nominal se redondea al número entero de ingredientes). MRR es la media de 1/posición (0 si no aparece).

## Resultados

| Cobertura simulada | Simulaciones | Cobertura real | precision@1 | precision@5 | precision@10 | Aparece en la lista | MRR | Posición media (si aparece) | Recetas devueltas (media) | Con empates delante |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 100 % | 100 | 100 % | 87,0 % | 100,0 % | 100,0 % | 100,0 % | 0,932 | 1,1 | 12,9 | 13,0 % |
| 80 % | 100 | 77 % | 54,0 % | 98,0 % | 100,0 % | 100,0 % | 0,724 | 1,8 | 11,0 | 30,0 % |
| 60 % | 100 | 57 % | 16,0 % | 59,0 % | 94,0 % | 100,0 % | 0,353 | 4,9 | 9,6 | 47,0 % |
| 40 % | 100 | 42 % | 1,0 % | 8,0 % | 26,0 % | 31,0 % | 0,054 | 7,6 | 8,1 | 26,0 % |
| Todas | 400 | 69 % | 39,5 % | 66,3 % | 80,0 % | 82,8 % | 0,516 | 3,1 | 10,4 | 29,0 % |

- La función descarta las recetas con menos del 50 % de ingredientes cubiertos (`MIN_COVERAGE`), así que con una cobertura real por debajo de ese umbral la receta objetivo no puede aparecer: es una decisión de diseño (informe 11), no un fallo del emparejamiento.
- "Con empates delante" es el porcentaje de simulaciones en las que otra receta con la misma cobertura y el mismo número de faltas aparece antes que la objetivo: su orden entre ellas no depende de la despensa.
