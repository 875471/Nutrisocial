# Evaluación de la segmentación del texto OCR (núcleo 3.1)

Generado el 2026-09-29 con `npm run eval:ocr-segmentacion` sobre 20 casos de `eval/data/ocrSegmentationCases.json` (2 salidas reales de ML Kit y 18 construidos para cubrir variantes de formato). Cada texto se pasa por `parseRecipeText`, la misma función que usa `POST /recipes/parse-ocr`.

Criterios: un ingrediente está bien separado si su nombre tiene una similitud de edición ≥ 0,8 con el esperado (texto normalizado); un paso, si su texto la tiene ≥ 0,9. "Cantidad y unidad" es el porcentaje de ingredientes bien separados cuya cantidad y unidad también son correctas. "Acierto global" es el F1 de ingredientes y pasos juntos.

## Resultados agregados

| Conjunto | Casos | Ingr. precisión | Ingr. exhaustividad | Ingr. F1 | Cantidad y unidad | Pasos precisión | Pasos exhaustividad | Pasos F1 | Título | Acierto global (F1) | Casos perfectos |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Todos | 20 | 96,3 % | 89,5 % | 92,8 % | 98,7 % | 93,7 % | 100,0 % | 96,7 % | 100,0 % | 94,4 % | 15/20 |
| Reales (ML Kit) | 2 | 100,0 % | 100,0 % | 100,0 % | 100,0 % | 100,0 % | 100,0 % | 100,0 % | 100,0 % | 100,0 % | 2/2 |
| Sintéticos | 18 | 95,4 % | 87,3 % | 91,2 % | 98,4 % | 92,7 % | 100,0 % | 96,2 % | 100,0 % | 93,4 % | 13/18 |

- Ingredientes: 77 bien separados de 86 esperados (80 propuestos).
- Ingredientes completamente correctos (nombre, cantidad y unidad) sobre los esperados: 88,4 %.
- Pasos: 59 bien separados de 59 esperados (63 propuestos).

## Resultados por caso

| Caso | Origen | Ingredientes (ok/esp./prop.) | Cantidad y unidad ok | Pasos (ok/esp./prop.) | Título | Acierto (F1) |
| --- | --- | --- | --- | --- | --- | --- |
| real-lentejas-manuscrita | real | 7/7/7 | 7/7 | 4/4/4 | sí | 100,0 % |
| real-galletas-dos-columnas | real | 8/8/8 | 8/8 | 4/4/4 | sí | 100,0 % |
| encabezados-vinetas-pasos-partidos | sintético | 5/5/5 | 5/5 | 3/3/3 | sí | 100,0 % |
| sin-encabezados | sintético | 3/3/3 | 3/3 | 3/3/3 | sí | 100,0 % |
| ingredientes-numerados | sintético | 3/3/3 | 3/3 | 2/2/2 | sí | 100,0 % |
| numerado-sin-encabezados | sintético | 3/3/3 | 3/3 | 2/2/2 | sí | 100,0 % |
| fracciones | sintético | 4/4/4 | 3/4 | 2/2/2 | sí | 100,0 % |
| cantidades-pegadas | sintético | 5/5/5 | 5/5 | 4/4/4 | sí | 100,0 % |
| columnas-desordenadas | sintético | 2/3/2 | 2/2 | 3/3/4 | sí | 83,3 % |
| cantidad-al-final | sintético | 1/4/3 | 1/1 | 3/3/4 | sí | 57,1 % |
| varios-por-linea | sintético | 1/5/2 | 1/1 | 3/3/4 | sí | 57,1 % |
| numeros-con-letra | sintético | 5/5/5 | 5/5 | 4/4/4 | sí | 100,0 % |
| errores-ocr-en-cifras | sintético | 4/4/4 | 4/4 | 2/2/2 | sí | 100,0 % |
| pasos-sin-verbo | sintético | 5/5/5 | 5/5 | 3/3/3 | sí | 100,0 % |
| rangos | sintético | 4/4/4 | 4/4 | 3/3/3 | sí | 100,0 % |
| pasos-antes-que-ingredientes | sintético | 2/2/2 | 2/2 | 3/3/3 | sí | 100,0 % |
| pasos-en-varias-lineas | sintético | 3/3/3 | 3/3 | 3/3/3 | sí | 100,0 % |
| sin-titulo | sintético | 4/4/4 | 4/4 | 2/2/2 | sí | 100,0 % |
| sin-cantidades | sintético | 4/5/4 | 4/4 | 3/3/4 | sí | 87,5 % |
| mayusculas-y-vinetas-redondas | sintético | 4/4/4 | 4/4 | 3/3/3 | sí | 100,0 % |

## Errores encontrados

**fracciones**

- cantidad o unidad: esperado «plátanos» (1,5 unidad), obtenido «½ plátanos» (1 unidad)

**columnas-desordenadas**

- ingrediente no detectado: «huevos» (2 unidad)
- paso de más: «2 huevos»

**cantidad-al-final**

- ingrediente no detectado: «Tomates» (3 unidad)
- ingrediente no detectado: «Cebolla morada» (0,5 unidad)
- ingrediente no detectado: «Aceite de oliva» (2 cucharada)
- ingrediente de más: «Tomates, 3» (sin cantidad)
- ingrediente de más: «Cebolla morada, media» (sin cantidad)
- paso de más: «Aceite de oliva, 2 cucharadas»

**varios-por-linea**

- ingrediente no detectado: «Sal» (sin cantidad)
- ingrediente no detectado: «pimienta» (sin cantidad)
- ingrediente no detectado: «Aceite de oliva» (sin cantidad)
- ingrediente no detectado: «perejil» (sin cantidad)
- ingrediente de más: «Sal y pimienta» (sin cantidad)
- paso de más: «Aceite de oliva y perejil»

**sin-cantidades**

- ingrediente no detectado: «Aceite de oliva virgen extra» (sin cantidad)
- paso de más: «Aceite de oliva virgen extra»
