# 17. Evaluación cuantitativa de los cuatro núcleos técnicos

## Objetivo y problema

La propuesta del TFG fija como objetivo específico evaluar cada núcleo técnico con una métrica objetiva:

| Núcleo | Qué hace | Métrica prevista |
|---|---|---|
| 3.1 Digitalización de recetas por OCR | Reconoce el texto de una foto (ML Kit, en el móvil) y lo separa en título, ingredientes y pasos (servidor) | CER y WER del reconocimiento; precisión de la segmentación |
| 3.2 Cálculo nutricional | Asocia cada ingrediente a un alimento de BEDCA, convierte la cantidad a gramos y suma | Error medio absoluto frente a una referencia |
| 3.3 Buscador por despensa | Ordena las recetas según cuántos ingredientes cubre la despensa | precision@k |
| 3.4 Recomendador nutricional | Propone la receta que mejor completa el día frente al objetivo | Reducción de la desviación frente a una elección aleatoria |

Hasta ahora el proyecto tenía **pruebas funcionales** (100 en el backend y 17 unitarias en Android), que comprueban que el código hace lo que se diseñó: una fracción se interpreta bien, un 403 se devuelve cuando toca. Pero una prueba funcional no dice **cómo de bien** resuelve cada núcleo su problema con datos realistas. Tampoco había ninguna métrica calculada. Esta iteración añade, para cada núcleo, un script ejecutable que calcula su métrica, sus resultados y su interpretación.

## Decisiones técnicas

### Evaluar no es probar: una carpeta aparte

Todo está en `backend/eval/`, separado de `backend/test/`. Los scripts de evaluación no tienen aserciones que fallen: miden. Un cambio que empeore la precisión del buscador no debe romper `npm test`, pero sí se debe poder ver en su informe. Por el mismo motivo, los archivos se llaman `*.eval.js`: el ejecutor de pruebas de Node (`node --test`) solo recoge `*.test.js` y la carpeta `test/`, así que `npm test` sigue ejecutando exactamente las mismas 100 pruebas.

```
backend/eval/
├── lib/metrics.js                  distancia de edición, precisión/exhaustividad/F1, aleatorio con semilla, formato
├── data/ocrSegmentationCases.json  20 casos de texto OCR con la segmentación correcta
├── data/macroReferenceRecipes.json 14 recetas con su valor de referencia (borrador pendiente de verificar)
├── ocrSegmentation.eval.js         núcleo 3.1 (parte de servidor)
├── macroAccuracy.eval.js           núcleo 3.2
├── pantrySearch.eval.js            núcleo 3.3
├── recommenderSimulation.eval.js   núcleo 3.4
└── results/*.md                    informe que escribe cada script
```

Cada script se lanza con `npm run eval:ocr-segmentacion`, `eval:macros`, `eval:despensa` o `eval:recomendador` desde `backend/`. Imprime el informe por consola y lo guarda en `eval/results/`, en Markdown con tablas y con la coma decimal, listo para copiarlo a la memoria.

### Se mide el código real, no una copia

Cada evaluación llama a **la misma función que usa la aplicación**:

- La segmentación usa `parseRecipeText`, la de `POST /recipes/parse-ocr`.
- El cálculo nutricional usa `resolveIngredients`, la de `POST /recipes`, con `matchFood` y la conversión de unidades.
- El buscador usa `matchRecipesToPantry`, la de `GET /recipes/by-pantry`.
- El recomendador usa `recommendRecipes`, la de `GET /log/recommendations`.

Reimplementar la lógica en el script sería más cómodo, pero se evaluaría la copia y no la aplicación.

### Reproducibles

Las simulaciones (núcleos 3.3 y 3.4) usan un generador pseudoaleatorio con semilla (*mulberry32*, en `lib/metrics.js`) en lugar de `Math.random()`. Con la misma semilla y los mismos datos, cada ejecución da exactamente los mismos números. Así, las cifras de la memoria se pueden regenerar y comprobar. La semilla y el número de simulaciones se pueden cambiar por parámetro (`--semilla=`, `--simulaciones=`) para comprobar que las conclusiones no dependen de un sorteo concreto.

Las evaluaciones que usan datos reales (3.2, 3.3 y 3.4) **solo leen** la base de datos del `.env`: la tabla `Food` con BEDCA y las 32 recetas de la aplicación, en su mayoría las de demostración (informe 14). La del cálculo nutricional desactiva además Open Food Facts por defecto, como el script de datos de demostración. Así el resultado no depende de un servicio externo y la evaluación no guarda productos nuevos en `Food`. Con `--con-off` se puede activar.

### Sin librerías de métricas

La distancia de edición (Levenshtein), la precisión, la exhaustividad (*recall*), el F1, la media y la mediana están escritas en el propio proyecto, tanto en JavaScript (`eval/lib/metrics.js`) como en Kotlin (`OcrTextMetrics.kt`). Son pocas líneas y así cada cifra se puede seguir hasta su definición sin depender de cómo la implementa una librería.

## Cómo funciona

### 3.1 OCR, parte 1: segmentación del texto (servidor)

**Qué se mide.** Dado el texto que ha reconocido ML Kit, se mide si el servidor separa bien los ingredientes (con su cantidad y su unidad) y los pasos. Esta parte se puede evaluar sin fotos nuevas, porque su entrada es texto.

**Datos.** 20 casos en `eval/data/ocrSegmentationCases.json`, cada uno con el texto (`rawText`) y la respuesta correcta según una persona (`expectedTitle`, `expectedIngredients` con nombre, cantidad y unidad, y `expectedSteps`):

- **2 salidas reales de ML Kit** de rondas anteriores: las lentejas con letra manuscrita simulada del informe 05, con sus errores de lectura ("300 9 de lentejas", "2anahorias"), y las galletas con chips de chocolate a dos columnas del informe 07, con el orden de lectura alterado.
- **18 casos construidos** para cubrir, cada uno, una variante de formato: encabezados y viñetas, sin encabezados, ingredientes numerados, fracciones y números mixtos, cantidades pegadas ("500g"), rangos ("2-3 dientes"), números con letra ("media taza"), errores de OCR en las cifras ("2OO g"), pasos antes que ingredientes, pasos partidos en varias líneas, pasos sin verbo, sin título, sin cantidades... Tres de ellos reproducen a propósito limitaciones ya conocidas del informe 05: la cantidad al final ("Tomates, 3"), dos ingredientes en una línea ("Sal y pimienta") y un ingrediente sin unidad detrás de "Preparación".

La respuesta correcta se ha escrito según lo que es correcto, **no copiando lo que devuelve el parser**. Si no, la evaluación mediría el parser contra sí mismo. Los nombres se escriben tal como aparecen en el texto, con sus erratas si las tiene ("cebola"), porque corregir la ortografía no es tarea de la segmentación. Las unidades se escriben como las guarda la aplicación (un vaso es una taza, un diente es una unidad).

**Emparejamiento.** Los ingredientes propuestos no tienen por qué salir en el mismo orden ni con el mismo texto exacto, así que no se compara posición a posición. Cada elemento esperado se empareja con el propuesto más parecido que siga libre, si la similitud de edición sobre el texto normalizado (sin tildes ni mayúsculas) supera un umbral:

- **Ingrediente:** nombre con similitud ≥ 0,8. Tolera un plural o una letra de diferencia, pero no "Tomates, 3" frente a "Tomates".
- **Paso:** texto con similitud ≥ 0,9. Tolera la puntuación, pero no un paso partido en dos o dos pasos unidos en uno.

Con los pares se cuentan los aciertos (verdaderos positivos) y se calculan:

- **Precisión:** qué parte de lo propuesto es correcto.
- **Exhaustividad:** qué parte de lo esperado se encontró.
- **F1:** su media armónica.

Los tres se calculan para ingredientes y para pasos, sumando los recuentos de todos los casos (micropromedio). De los ingredientes bien separados se mide además si la cantidad (±0,01) y la unidad son correctas. El "acierto global" es el F1 de ingredientes y pasos juntos.

**Resultados** (`eval/results/ocrSegmentation.md`):

| Conjunto | Casos | Ingr. precisión | Ingr. exhaustividad | Ingr. F1 | Cantidad y unidad | Pasos F1 | Título | Acierto global (F1) | Casos perfectos |
|---|---|---|---|---|---|---|---|---|---|
| Todos | 20 | 96,3 % | 89,5 % | 92,8 % | 98,7 % | 96,7 % | 100 % | **94,4 %** | 15/20 |
| Reales (ML Kit) | 2 | 100 % | 100 % | 100 % | 100 % | 100 % | 100 % | 100 % | 2/2 |
| Construidos | 18 | 95,4 % | 87,3 % | 91,2 % | 98,4 % | 96,2 % | 100 % | 93,4 % | 13/18 |

De los 86 ingredientes esperados, 77 se separan bien, y el 88,4 % de los esperados sale completamente correcto (nombre, cantidad y unidad). Los 59 pasos esperados se encuentran todos (exhaustividad del 100 %). Los errores están localizados:

- **Cantidad detrás del nombre** ("Tomates, 3") y **dos ingredientes en una línea** ("Sal y pimienta"): la heurística no los contempla (informe 05). Son los casos con peor resultado (57 % cada uno).
- **Ingrediente sin cantidad de más de 4 palabras** ("Aceite de oliva virgen extra"): supera el límite de palabras de un ingrediente sin cantidad y se toma por un paso.
- **Ingrediente sin unidad detrás de "Preparación"** ("2 huevos"): se mantiene como paso a propósito, porque "3 huevos" dentro de la preparación suele ser el final de una frase partida (informe 07).
- **Número mixto con fracción Unicode separada** ("1 ½ plátanos"): se lee la cantidad 1 y el nombre "½ plátanos".

**Interpretación.** La heurística separa bien las recetas con la estructura habitual (cantidad al principio, apartados). Sus fallos son los formatos que ya se habían identificado como limitaciones, lo que confirma el diagnóstico del informe 05 con números. Hay que leer estas cifras con cautela: varios casos construidos se parecen a las pruebas con las que se desarrolló la heurística, así que miden sobre todo que **cubre las variantes previstas**, y son una estimación optimista del comportamiento con recetas nuevas. El parser no se ha retocado a la vista de estos resultados, para no ajustarlos al propio conjunto de evaluación. La medida representativa será la de los textos reales de las fotos del apartado siguiente (ver "Pendiente de completar").

### 3.1 OCR, parte 2: CER y WER del reconocimiento (móvil)

**Qué se mide.** Cuántos caracteres y cuántas palabras lee mal ML Kit en fotos reales de recetas. Se usan las dos métricas estándar de OCR:

```
CER = (S + B + I) / N        (en caracteres)
WER = (S + B + I) / N        (en palabras)
```

S, B e I son las sustituciones, borrados e inserciones de la distancia de edición mínima entre el texto reconocido y la transcripción correcta, y N es la longitud de la transcripción. Un CER del 5 % equivale a unos 5 caracteres erróneos de cada 100. El WER suele ser bastante mayor que el CER, porque un solo carácter mal leído estropea toda la palabra.

**Por qué en el móvil.** ML Kit se ejecuta en el dispositivo (informe 05), así que el reconocimiento no se puede evaluar en el servidor. La evaluación es una prueba instrumentada de Android, `OcrRecognitionEvalTest`, que se ejecuta en un móvil o un emulador. Recorre los pares `recetaNN.jpg` + `recetaNN.txt` de `app/src/androidTest/assets/ocr_eval/` y pasa cada foto por `RecipeTextRecognizer`, **el mismo código que el escáner**: incluye la orientación EXIF y la reconstrucción por bloques y líneas que se envía al servidor. Después calcula el CER y el WER con `OcrTextMetrics`, una implementación propia de la distancia de Levenshtein con 5 pruebas unitarias.

**Normalización.** Antes de comparar, los espacios y saltos de línea seguidos se reducen a un solo espacio. La línea en blanco que la aplicación pone entre bloques, o el punto exacto en que la transcripción parte una línea, no son errores de lectura. Las mayúsculas, las tildes y la puntuación **sí cuentan**, porque un "1" leído como "l" es un error real. Se da además una variante tolerante, sin distinguir mayúsculas ni tildes, que separa los errores graves de los cosméticos.

**Agregación.** Se dan dos medias:

- **Media por foto:** cada receta pesa lo mismo.
- **Global:** errores totales entre caracteres totales; una receta larga pesa más.

**Salida.** El informe (una tabla por foto y las medias) se escribe en Logcat con la etiqueta `OCR_EVAL`, en el resultado de la instrumentación y en `Android/data/com.example.nutrisocial/files/ocr_eval/` del dispositivo. En esa carpeta queda también el texto reconocido de cada foto (`recetaNN.reconocido.txt`), que sirve para ampliar la evaluación de la segmentación con textos reales.

**Comprobación del montaje.** El conjunto de fotos está vacío (ver "Pendiente de completar"), pero el recorrido se probó en el emulador con una imagen temporal generada con texto impreso: ML Kit leyó "cebola" en lugar de "cebolla" y la prueba dio un CER del 0,5 % y un WER del 3,0 % (1 carácter de 186, 1 palabra de 33). La imagen se quitó después. Esa primera ejecución detectó también un error de la propia prueba: el método no devolvía `void` y JUnit no lo aceptaba. No es un resultado de la evaluación, solo la comprobación de que funciona.

### 3.2 Cálculo nutricional: error medio absoluto

**Qué se mide.** Cuánto se aleja el valor que calcula la aplicación del valor real de la receta, por ración, para cada macronutriente.

**Datos.** 14 recetas variadas en `eval/data/macroReferenceRecipes.json` (desayunos, legumbres, pasta, pescado, huevos, verdura y repostería), tomadas de los datos de demostración. Los ingredientes están escritos como los escribiría un usuario ("2 cucharadas de aceite de oliva", "1 cebolla"). La referencia de cada receta no es un número suelto, sino un **desglose**: qué alimento es realmente cada ingrediente (`referenceBreakdown`), cuántos gramos pesa de verdad (un huevo mediano sin cáscara, unos 53 g; una cucharada de aceite, 13,5 g) y sus valores por 100 g (`referenceFoods`). Con este formato la referencia se puede revisar alimento a alimento contra BEDCA o contra una etiqueta, sin rehacer sumas a mano. Si se conoce el total directamente, por ejemplo de una etiqueta, se puede escribir en `referenceTotals`, que tiene prioridad.

**Procedimiento.** Para cada receta:

1. Se ejecuta `resolveIngredients` con los ingredientes tal cual.
2. Se dividen los totales de la aplicación y los de la referencia entre las raciones.
3. Se calculan, por macronutriente:
   - el **error absoluto**: |app − referencia|, en kcal o en gramos;
   - el **error relativo**: |app − referencia| / referencia. No se calcula si la referencia es menor que 1, para no dividir por casi cero.

Después se dan la media (**error medio absoluto**, MAE, y **error medio porcentual**, MAPE) y la mediana. La mediana es menos sensible a una receta aislada con mucho error. El informe incluye, para cada ingrediente, el alimento que eligió `matchFood` y los gramos que estimó la aplicación, para localizar el origen de cada error.

**Resultados provisionales** (`eval/results/macroAccuracy.md`), **con referencias todavía sin verificar**:

| Macronutriente (por ración) | Error medio absoluto | Error medio porcentual | Mediana del error porcentual |
|---|---|---|---|
| Energía | 32,4 kcal | 11,2 % | 3,8 % |
| Proteínas | 1,2 g | 9,6 % | 7,4 % |
| Hidratos | 2,3 g | 7,8 % | 6,8 % |
| Grasas | 1,9 g | 12,3 % | 9,2 % |

12 de las 14 recetas quedan a ±10 % de la referencia en kcal por ración.

**Un error en los datos de BEDCA.** La media de las kcal está dominada por una receta: "Garbanzos con espinacas" da 482 kcal por ración frente a 241 de referencia. El informe de emparejamiento mostró la causa: el alimento "Garbanzo, hervido" de la tabla `Food` tiene **358,7 kcal/100 g**, pero sus macronutrientes (8,9 g de proteína, 18,7 g de hidratos y 2,5 g de grasa) solo suman 132,9 kcal con los factores de Atwater (4·P + 4·H + 9·G). El valor ya viene así en el archivo descargado de BEDCA, así que la importación es fiel y el error está en el dato de origen. Para detectar estos casos, la evaluación comprueba ahora la coherencia de cada alimento: si sus kcal se alejan más de un 30 % y más de 40 kcal/100 g de las que dan sus macros, lo marca y da también el error sin las recetas afectadas. Recorriendo toda la tabla, de los 953 alimentos de BEDCA, 27 incumplen la regla. 23 son bebidas alcohólicas, en las que la diferencia es correcta porque el alcohol (7 kcal/g) no está entre los macros. Solo 4 son errores: "Garbanzo, hervido", "Dorada, cruda", "Kebab" y "Queso para untar, natural, bajo en calorías". Sin la receta de garbanzos (13 recetas):

| Macronutriente (por ración) | Error medio absoluto | Error medio porcentual |
|---|---|---|
| Energía | 16,4 kcal | 4,4 % |
| Proteínas | 1,1 g | 9,1 % |
| Hidratos | 2,4 g | 7,7 % |
| Grasas | 1,9 g | 12,2 % |

**Interpretación.** Con la salvedad de que las referencias son provisionales, el error en energía es pequeño (4,4 % sin el dato erróneo) y se explica sobre todo por dos convenciones de la conversión de unidades:

- **La aplicación pesa el huevo con cáscara** (60 g por pieza) y **la cucharada de aceite en 15 g**, frente a unos 53 g y 13,5 g reales. Eso sobrestima un poco las grasas de las recetas con huevo o mucho aceite (tortilla, revuelto, boloñesa), y el sesgo sale positivo: la aplicación da algo más que la referencia.
- **Algunos emparejamientos eligen una variante distinta** de la que se quería: "pechuga de pollo" se asocia a "con piel" y "espinacas" a "congelada". Afectan poco a la energía, pero sí a proteínas y grasas en porcentaje, que son cantidades pequeñas.

El hallazgo más útil es el del dato de BEDCA: un solo valor erróneo duplica las kcal de una receta. Ninguna prueba funcional lo habría detectado, porque el cálculo es correcto con el dato que recibe.

### 3.3 Buscador por despensa: precision@k

**Qué se mide.** Si una receta que se puede cocinar (del todo o casi) con lo que hay en la despensa aparece en los primeros puestos del buscador.

**Procedimiento.** No hacen falta datos nuevos: se genera todo a partir de las 32 recetas de la base de datos. Cada simulación:

1. Elige una receta objetivo al azar entre las que tienen 3 o más ingredientes.
2. Construye una despensa con una fracción de sus ingredientes (100 %, 80 %, 60 % o 40 %, redondeada al número entero de ingredientes) más 3 ingredientes al azar de **otras** recetas que no son ninguno de los suyos. Así la despensa no es trivial y hay recetas que compiten. Los ingredientes se toman con el alimento asociado al guardarlos, igual que los de una despensa real.
3. Ejecuta `matchRecipesToPantry` contra todas las recetas, en el mismo orden en que las carga la ruta.
4. Anota la posición de la receta objetivo.

Se hacen 100 simulaciones por nivel de cobertura (400 en total). **precision@k** es la proporción de simulaciones en las que la receta objetivo aparece entre las k primeras. En rigor, como solo hay una receta relevante por simulación, esta medida es la tasa de acierto en los k primeros (*hit rate@k*), pero se mantiene el nombre de la propuesta. Se da también el **MRR** (media de 1/posición, 0 si no aparece), que premia aparecer primero.

**Resultados** (`eval/results/pantrySearch.md`, 3 ingredientes de relleno):

| Cobertura simulada | Cobertura real | precision@1 | precision@5 | precision@10 | MRR |
|---|---|---|---|---|---|
| 100 % | 100 % | 94,0 % | 100 % | 100 % | 0,968 |
| 80 % | 78 % | 91,0 % | 100 % | 100 % | 0,953 |
| 60 % | 58 % | 77,0 % | 100 % | 100 % | 0,873 |
| 40 % | 41 % | 11,0 % | 25,0 % | 25,0 % | 0,168 |
| Todas | 69 % | 68,3 % | 81,3 % | 81,3 % | 0,741 |

Con solo 32 recetas y 3 ingredientes de relleno, el buscador devuelve de media entre 1 y 5 recetas, así que precision@5 y precision@10 coinciden. Para ver cómo se degrada con más competencia se repitió con **10 ingredientes de relleno** (`npm run eval:despensa -- --distractores=10`, `eval/results/pantrySearch-10-distractores.md`), que devuelve de media 8-13 recetas:

| Cobertura simulada | precision@1 | precision@5 | precision@10 | MRR |
|---|---|---|---|---|
| 100 % | 87,0 % | 100 % | 100 % | 0,932 |
| 80 % | 54,0 % | 98,0 % | 100 % | 0,724 |
| 60 % | 16,0 % | 59,0 % | 94,0 % | 0,353 |
| 40 % | 1,0 % | 8,0 % | 26,0 % | 0,054 |
| Todas | 39,5 % | 66,3 % | 80,0 % | 0,516 |

**Interpretación.**

- **Con la despensa completa, la receta aparece siempre entre las 5 primeras.** Casi siempre es la primera (94 % y 87 %). Cuando no lo es, se debe a empates: otra receta con cobertura 1 y 0 faltas, pero con menos ingredientes, que la despensa también cubre entera, va delante.
- **La precisión cae de forma gradual al faltar ingredientes**, más cuanto más competencia hay. Es lo esperable del criterio de orden (cobertura y, después, número de faltas): con el 60 % de los ingredientes, otras recetas que comparten los básicos (aceite, cebolla, ajo) quedan por delante. Aun así, con 10 ingredientes de relleno la receta objetivo sigue entre las 10 primeras en el 94 % de los casos.
- **Con el 40 % la receta casi nunca aparece, y es a propósito.** El buscador descarta las recetas con menos de la mitad de ingredientes cubiertos (`MIN_COVERAGE = 0,5`, informe 11). El 25-31 % en que sí aparece corresponde a recetas de 4 o 5 ingredientes, en las que el redondeo deja una cobertura real del 50 %. La métrica confirma que el umbral hace lo que se pretendía: no llenar la lista de recetas a las que les falta más de la mitad.

### 3.4 Recomendador: reducción de la desviación frente a una elección aleatoria

**Qué se mide.** Si la receta que recomienda el motor deja el día más cerca del objetivo que elegir una receta al azar.

**Procedimiento.** Cada simulación es un día de un usuario ficticio:

1. **Perfil aleatorio razonable:**
   - sexo;
   - edad entre 18 y 65 años;
   - altura (163-192 cm en hombres y 152-180 cm en mujeres);
   - peso a partir de un IMC entre 19 y 32;
   - nivel de actividad y objetivo uniformes entre los de la aplicación.

   Con `calorieGoal.js` y `macroTargets.js` se calcula su objetivo diario de kcal y de gramos de cada macronutriente. La edad se calcula con una fecha fija, para que el resultado no dependa del día en que se ejecute.
2. **Recetas ya registradas ese día**, una ración de cada una, en dos escenarios:
   - **"Siguiente comida"**, el de la propuesta: 1 o 2 recetas al azar, dejando al menos 300 kcal de margen.
   - **"Cierre del día"**, complementario: recetas al azar hasta que quedan entre 300 y 900 kcal, es decir, falta una sola comida.
3. **Dos estrategias** para la receta siguiente:
   - **(a) Recomendador:** la primera recomendación de `recommendRecipes`, la función real, a la que se inyectan las recetas ya cargadas para no repetir la consulta.
   - **(b) Aleatoria:** una receta al azar entre las que no se pasan de las kcal que quedan. En lugar de un único sorteo por día se usa la media de **todas** esas candidatas, que es el valor esperado de elegir al azar. Así la comparación no depende de la suerte de un sorteo concreto.
4. **Desviación final** tras añadir la receta:
   - **en kcal:** |consumido − objetivo|;
   - **en macronutrientes:** |P − P objetivo| + |H − H objetivo| + |G − G objetivo|, en gramos.

Se hacen 200 días por escenario. Se dan:

- la **reducción absoluta**: desviación media aleatoria − desviación media del recomendador;
- la **reducción relativa**: 1 − media del recomendador / media aleatoria;
- el porcentaje de días en que el recomendador **gana**;
- como cota, la desviación que habría dejado la mejor receta posible mirando solo las kcal.

Todo se separa por objetivo.

**Resultados** (`eval/results/recommenderSimulation.md`):

*Escenario "siguiente comida"* (quedan de media 1.741 kcal):

| Objetivo | Desviación kcal aleatoria → recomendador | Reducción kcal | Desviación macros aleatoria → recomendador | Reducción macros | Gana (kcal / macros) |
|---|---|---|---|---|---|
| Perder peso | 968 → 710 | −258 kcal (26,6 %) | 236 → 184 g | −52 g (22,0 %) | 100 % / 97 % |
| Mantener | 1.300 → 1.010 | −290 kcal (22,3 %) | 294 → 231 g | −63 g (21,4 %) | 100 % / 100 % |
| Ganar peso | 1.826 → 1.603 | −223 kcal (12,2 %) | 424 → 368 g | −55 g (13,0 %) | 100 % / 100 % |
| **Todos** | 1.357 → 1.101 | **−257 kcal (18,9 %)** | 316 → 260 g | **−57 g (17,9 %)** | 100 % / 99 % |

*Escenario "cierre del día"* (quedan de media 687 kcal):

| Objetivo | Desviación kcal aleatoria → recomendador | Reducción kcal | Desviación macros aleatoria → recomendador | Reducción macros | Gana (kcal / macros) |
|---|---|---|---|---|---|
| Perder peso | 328 → 58 | −270 kcal (82,2 %) | 140 → 118 g | −22 g (15,8 %) | 100 % / 78 % |
| Mantener | 311 → 54 | −257 kcal (82,7 %) | 147 → 102 g | −46 g (31,1 %) | 100 % / 98 % |
| Ganar peso | 330 → 63 | −267 kcal (81,0 %) | 208 → 165 g | −44 g (20,9 %) | 100 % / 100 % |
| **Todos** | 323 → 58 | **−265 kcal (81,9 %)** | 166 → 129 g | **−37 g (22,3 %)** | 100 % / 92 % |

**Interpretación.**

- **El recomendador reduce siempre la desviación en kcal frente al azar.** En el escenario de la propuesta, la reducción es moderada (19 %) por una razón estructural, no del motor: tras 1 o 2 recetas quedan unas 1.700 kcal, y ninguna ración de las recetas disponibles (entre unas 100 y 700 kcal) puede cerrar el día. Lo mejor posible con una sola receta deja 1.042 kcal de desviación, y el recomendador se queda en 1.101, cerca de esa cota. En ese escenario, acierta sobre todo porque elige raciones que llenan más del margen sin pasarse.
- **El escenario de cierre del día es el que muestra su calidad.** Deja el día a 58 kcal del objetivo de media, frente a 323 eligiendo al azar (−82 %), muy cerca del mínimo alcanzable con estas recetas (50 kcal). Solo se pasa de lo que quedaba en el 10,5 % de los días, siempre dentro del 15 % que permite el diseño (informe 09).
- **En macronutrientes, la mejora es menor pero consistente:** un 18-22 % menos de desviación, y mejor que el azar en el 92-99 % de los días. Es lógico: el encaje de macros pesa la mitad de la puntuación, y con 31 recetas no siempre hay una que cuadre a la vez las kcal y el reparto.
- **Por objetivo**, la mejora relativa es mayor al perder peso y menor al ganarlo. Con objetivos más altos el margen es mayor, y la desviación que deja cualquier receta también lo es. En el cierre del día, la reducción en kcal es igual en los tres objetivos (81-83 %).

## Pendiente de completar por el autor

Dos evaluaciones dependen de datos que solo puede aportar el autor.

**1. Fotos para el CER y el WER (núcleo 3.1).** En `app/src/androidTest/assets/ocr_eval/`, un par de archivos por receta con el mismo nombre:

- `receta01.jpg` (o `.jpeg`/`.png`): la foto tal como sale de la cámara del móvil, sin recortar ni retocar.
- `receta01.txt`: la transcripción correcta, a mano, en UTF-8. Copia exacta de lo escrito, con sus mayúsculas, tildes y erratas, una línea por línea del papel y en orden de lectura (con dos columnas, primero la izquierda entera). Sin comentarios ni líneas de más. Las instrucciones están también en el `LEEME.md` de esa carpeta.

Se ejecuta con el móvil o el emulador conectado:

```
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.nutrisocial.OcrRecognitionEvalTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

La última opción evita que Gradle desinstale la aplicación al terminar, lo que borraría la sesión iniciada y los resultados guardados en el móvil.

**Tamaño de la muestra.** La propuesta habla de 50-100 recetas. Fotografiar y transcribir a mano esa cantidad llevaría, a unos 10-15 minutos por receta, entre 8 y 25 horas, y no cabe en el tiempo que queda. **Se propone una muestra de 20 fotos (mínimo 15) como decisión consciente de alcance**, que sigue siendo defendible:

- Con 20 fotos de 300-500 caracteres se comparan unos 8.000 caracteres.
- La incertidumbre de la media la domina la variación entre fotos. Con una desviación típica del CER de unos 10 puntos entre fotos, el intervalo de confianza del 95 % de la media es de unos ±4-5 puntos (1,96 · 10/√20). Basta para distinguir un OCR útil (CER < 10 %) de uno que no lo es (CER > 20 %).
- Para que las 20 fotos representen la variedad real, conviene:
  - al menos 3 o 4 letras de personas distintas;
  - unas 5 recetas impresas (libro o captura) como referencia;
  - recetas de una y de dos columnas;
  - distintas condiciones de luz, sin buscar a propósito la foto perfecta.

La memoria debe decir expresamente que la muestra se redujo por tiempo.

Además, los textos reconocidos que guarda la prueba (`recetaNN.reconocido.txt`) se pueden añadir como casos `"origen": "real"` a `backend/eval/data/ocrSegmentationCases.json`, escribiendo a mano su segmentación correcta. Así la evaluación de la segmentación pasaría a medirse sobre salidas reales de ML Kit y no sobre casos construidos, que es su principal limitación.

**2. Referencias de macronutrientes (núcleo 3.2).** En `backend/eval/data/macroReferenceRecipes.json`:

1. **`referenceFoods`:** comprobar cada uno de los 40 alimentos en BEDCA (https://www.bedca.net) o en la etiqueta del producto que se use, corregir sus cuatro valores por 100 g y cambiar su `source` por la fuente real.
2. **`referenceBreakdown`:** revisar los gramos de cada receta. Lo ideal es cocinar alguna de ellas y **pesar** los ingredientes: es la referencia más sólida y la más fácil de defender.
3. Poner `"verified": true` en cada receta revisada y ejecutar `npm run eval:macros -- --solo-verificadas`. Mientras quede alguna sin verificar, el informe lo avisa en su cabecera.

Los valores actuales son un **borrador** escrito con valores típicos de tablas (BEDCA y USDA, de memoria y redondeados) y pesos medios. Sirven para comprobar que la evaluación funciona y para una primera estimación, **no para citarlos como resultado**.

## Limitaciones

- **Segmentación evaluada sobre todo con casos construidos.** Solo 2 de los 20 casos son salidas reales de ML Kit. Además, varios casos construidos se parecen a las pruebas con las que se desarrolló la heurística, así que el 94,4 % es optimista. La medida representativa llegará con los textos de las fotos reales. (El informe 20 añade dos casos más, de ingredientes partidos en varias líneas: con 22 casos el acierto global es del 94,9 %.)
- **CER y WER aún sin datos.** La prueba y las métricas están hechas y comprobadas, pero la muestra de fotos no. La muestra propuesta (20) es menor que la de la propuesta (50-100) por tiempo. Tampoco se ha separado en la métrica el efecto del **orden de lectura**: si ML Kit lee las columnas en otro orden, el CER sube aunque cada línea esté bien leída. Si ocurre, conviene anotarlo junto al resultado de esa foto.
- **Referencias nutricionales provisionales y dependientes de convenciones.** Además de estar sin verificar, cualquier referencia calculada a partir de tablas hereda sus supuestos: pesos medios de pieza, densidades y alimento en crudo. La única referencia independiente de verdad sería pesar y, para los platos, un análisis de laboratorio, que queda fuera del alcance.
- **Datos erróneos en BEDCA.** La evaluación encontró 4 alimentos sin alcohol cuyas kcal no cuadran con sus macros. No se han corregido en la base de datos de producción: es una decisión que corresponde al autor (corregirlos a mano, recalcular sus kcal con Atwater o excluirlos del emparejamiento), y después habría que volver a guardar las recetas que los usan, porque los totales se calculan al guardar. La comprobación se podría añadir también a la carga de la semilla (`prisma/seed.js`) para rechazar o marcar esos alimentos.
- **Corpus pequeño en los núcleos 3.3 y 3.4.** Con 32 recetas, las listas del buscador son cortas y el recomendador tiene pocas opciones. Las cifras describen el comportamiento con el catálogo actual, no con miles de recetas. Con más recetas, el buscador tendría más competencia (su precisión bajaría, como muestra el escenario de 10 ingredientes de relleno) y el recomendador más opciones de encaje (su desviación bajaría).
- **Simulaciones, no usuarios.** Las despensas y los días son sintéticos: en el buscador, los ingredientes de la despensa se eligen al azar, y en el recomendador, las recetas ya registradas también, con una sola ración. Una evaluación con usuarios reales (despensas reales y diarios de varias semanas) mediría además si las recomendaciones se aceptan, algo que ninguna simulación puede medir.
- **Una sola métrica de desviación.** La desviación de macronutrientes suma gramos de proteínas, hidratos y grasas sin ponderarlos, aunque un gramo de grasa aporta más del doble de energía que uno de hidratos. Es una elección sencilla y fácil de interpretar, pero no la única posible.

## Archivos creados o modificados

**Backend (`backend/`)**

| Archivo | Cambio |
|---|---|
| `eval/lib/metrics.js` (nuevo) | Levenshtein, similitud, precisión/exhaustividad/F1, media y mediana, aleatorio con semilla, formato y escritura de informes. |
| `eval/data/ocrSegmentationCases.json` (nuevo) | 20 casos de segmentación (2 reales y 18 construidos). |
| `eval/ocrSegmentation.eval.js` (nuevo) | Evaluación de la segmentación (núcleo 3.1). |
| `eval/data/macroReferenceRecipes.json` (nuevo) | 14 recetas con referencia en desglose (borrador pendiente de verificar). |
| `eval/macroAccuracy.eval.js` (nuevo) | Error medio absoluto y porcentual, comprobación de coherencia de `Food` (núcleo 3.2). |
| `eval/pantrySearch.eval.js` (nuevo) | precision@k y MRR por nivel de cobertura (núcleo 3.3). |
| `eval/recommenderSimulation.eval.js` (nuevo) | Recomendador frente a elección aleatoria en dos escenarios (núcleo 3.4). |
| `eval/results/*.md` (nuevos) | Informes generados por los cuatro scripts. |
| `package.json` (modificado) | Scripts `eval:ocr-segmentacion`, `eval:macros`, `eval:despensa` y `eval:recomendador`. |

**Aplicación Android (`app/src/`)**

| Archivo | Cambio |
|---|---|
| `main/java/.../data/ocr/OcrTextMetrics.kt` (nuevo) | CER y WER con distancia de edición propia, variante sin mayúsculas ni tildes. |
| `androidTest/java/.../OcrRecognitionEvalTest.kt` (nuevo) | Prueba instrumentada que mide el CER y el WER de ML Kit sobre los pares foto/transcripción. |
| `androidTest/assets/ocr_eval/LEEME.md` (nuevo) | Formato de los archivos y reglas para transcribir. |
| `test/.../OcrTextMetricsTest.kt` (nuevo) | 5 pruebas de la distancia de edición y del CER y el WER. |
