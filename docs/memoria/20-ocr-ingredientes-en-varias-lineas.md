# 20. OCR: ingredientes partidos en varias líneas

## Objetivo y problema

Al escribir una receta a mano, un ingrediente largo no siempre cabe en un renglón: "2 cucharadas de aceite de" y, debajo, "oliva virgen extra". ML Kit devuelve cada renglón como una línea distinta y el analizador (`backend/src/ocr/parseRecipeText.js`) clasificaba cada línea por separado. La primera se reconocía bien como ingrediente con cantidad ("aceite de", 2 cucharadas). La segunda, al no empezar por una cantidad, acababa de una de estas dos formas:

- **Como otro ingrediente sin cantidad** ("oliva virgen extra"), si era corta y no tenía verbo.
- **Como un paso**, si pasaba de cuatro palabras.

En los dos casos el usuario encontraba en el formulario un ingrediente con el nombre cortado, que no se emparejaba con ningún alimento, y otro que sobraba.

Esta iteración añade una regla para pegar esas líneas al ingrediente anterior. Iba acompañada de una ronda de depuración con fotos reales de recetas manuscritas, en la que Claude Code leería cada foto, compararía su lectura con la salida real de la aplicación y corregiría los fallos sistemáticos. **Esa ronda no se ha podido hacer todavía:** la carpeta `app/src/androidTest/assets/ocr_eval/` solo contiene `LEEME.md` y no hay ninguna foto. Sus resultados se añadirán a este informe cuando se haga. Este informe recoge por ahora solo la regla de los ingredientes partidos, que no dependía de las fotos.

## Decisiones técnicas

**La regla literal rompía listas que ya funcionaban.** La primera versión de la regla era la más directa: dentro del bloque de ingredientes, cualquier línea que no empiece por una cantidad, una viñeta o un número de lista es continuación del ingrediente anterior. Se implementó y se midió con la evaluación de segmentación (informe 17) antes de adoptarla. El resultado fue peor que no tenerla: el acierto global bajó del 94,4 % al 93,2 % y tres casos empeoraron. El más claro es `sin-cantidades`, que pasó del 87,5 % al 50 %. Una lista de ingredientes sin cantidades ("Lechuga", "Pepino", "Aceite de oliva", "Sal") es justamente una sucesión de líneas sin cantidad, y la regla literal la convertía en un único ingrediente "Lechuga Pepino Aceite de oliva Sal". Lo mismo pasaba con "Sal" o "Pimienta" detrás de un ingrediente con cantidad, que es de lo más habitual al final de una lista.

**Hace falta una señal de que el renglón se ha cortado.** La versión adoptada solo pega una línea al ingrediente anterior si hay algún indicio de que es la misma frase. Vale cualquiera de estos cuatro:

1. **La línea anterior acaba abierta:** en un conector ("de", "con", "y", "o", "para", "sin", "al", "la"...), en una coma, en un guion o en un paréntesis sin cerrar. Por ejemplo, "200 g de harina de" / "trigo integral".
2. **La línea empieza por un conector:** "o de oliva suave", "y pimentón", "(opcional)".
3. **Lista con viñetas:** si el ingrediente anterior llevaba viñeta y esta línea no, pero empieza en minúscula, es la segunda mitad del renglón. Por ejemplo, "- 2 cucharadas de nata" / "líquida para cocinar".
4. **Ingrediente en tres líneas o más:** si la línea anterior ya era una continuación, basta con que esta empiece en minúscula. Por ejemplo, "aceite de" / "oliva virgen" / "extra".

"Sal", "Pimienta" o "Lechuga" no cumplen ninguna de las cuatro, así que siguen siendo ingredientes independientes. Con esta versión, los 20 casos que ya había dan exactamente el mismo resultado que antes.

**Solo en el bloque de ingredientes y nunca en la primera línea.** La regla se aplica bajo el encabezado de ingredientes o, en recetas sin encabezados, antes del primer paso. Además, la línea anterior tiene que haber sido un ingrediente: si entre medias hay un encabezado, un título o un paso, no se pega nada. En la sección de pasos no se aplica nunca. Allí una línea sin cantidad es lo normal, y los pasos partidos ya tienen su propia regla de unión (informe 05). Una línea "huérfana" justo después del encabezado, sin ingrediente anterior, se trata como antes: ingrediente sin cantidad. Tampoco se pega una línea que empiece por un verbo de cocina, que es un paso aunque aparezca antes del encabezado de preparación.

**La "o" inicial: viñeta o conjunción.** El analizador ya trataba una "o" seguida de espacio al principio de la línea como una viñeta, porque un círculo manuscrito se lee a menudo como la letra "o" (informe 07). Eso impedía reconocer "o de oliva suave" como continuación. Ahora, si el ingrediente anterior no llevaba viñeta, la "o" inicial se interpreta como la conjunción. En una lista sin viñetas no aparece de repente un círculo. En una lista con viñetas se sigue tratando como viñeta, como hasta ahora.

**Se mide antes de cambiar.** Siguiendo lo aprendido en el informe 04 (una regla mal colocada rompió un caso que antes funcionaba), cada versión se comparó con la evaluación completa antes de darla por buena. La comparación entre la regla literal y la regla con señales está en la sección siguiente.

## Cómo funciona

Mientras recorre las líneas, `parseRecipeText` recuerda el último ingrediente reconocido: la línea tal cual, si llevaba viñeta y si ya era una continuación. Ese recuerdo se borra en cuanto aparece algo que no es un ingrediente (un encabezado, el título, las raciones o un paso), de modo que la continuación solo puede ir justo detrás de otro ingrediente.

Cada línea pasa primero por la comprobación de cantidad de siempre. Si empieza por una cantidad, es un ingrediente nuevo. Si no, y se cumplen las condiciones del bloque, `continuesIngredient` decide si hay señal de corte. En ese caso, el texto se añade con un espacio al nombre del último ingrediente, con las mismas correcciones de letras mal leídas que el resto del nombre (`fixOcrLetters`). La línea queda anotada en `lines` con su propio tipo, `ingredient-continuation`, para que se vea en la evaluación. La cantidad y la unidad no cambian: son las de la primera línea.

Por ejemplo, este texto:

```
Ingredientes
100 ml de aceite de girasol
o de oliva suave
Sal
```

da dos ingredientes: "aceite de girasol o de oliva suave" (100 ml) y "Sal" (sin cantidad).

### Resultados

| Versión | Acierto global (F1), 20 casos anteriores | Casos perfectos | `sin-cantidades` |
| --- | --- | --- | --- |
| Sin la regla (punto de partida) | 94,4 % | 15/20 | 87,5 % |
| Regla literal (cualquier línea sin cantidad) | 93,2 % | 15/20 | 50,0 % |
| Regla con señales de corte (adoptada) | 94,4 % | 15/20 | 87,5 % |

Se han añadido dos casos a `eval/data/ocrSegmentationCases.json`:

- **`ingrediente-en-dos-lineas`:** dos ingredientes partidos en dos renglones, uno con "de" al final y otro con "o" al principio, y una "Sal" que debe quedar aparte.
- **`ingrediente-en-tres-lineas`:** una lista con viñetas en la que un ingrediente ocupa tres renglones sin viñeta, y una "- Pimienta" que debe quedar aparte.

Sin la regla, estos dos casos se quedan en el 62,5 % y el 71,4 %; con ella, los dos llegan al 100 %. Sobre los 22 casos, el acierto global pasa del 91,8 % al 94,9 % y los casos perfectos, de 15 a 17. Las cifras del informe 17 eran sobre 20 casos. `eval/results/ocrSegmentation.md` tiene ya las de 22.

Los dos casos nuevos son **construidos**, no salidas reales de ML Kit, porque no había fotos con ingredientes partidos. Cuando la ronda de fotos encuentre alguno, se añadirá su texto real.

Las pruebas unitarias (`test/parseRecipeText.test.js`) cubren:

- La unión en dos líneas y en tres.
- La "o" como conjunción.
- La lista con viñetas y un renglón en minúscula sin viñeta.
- Que "Lechuga", "Pepino", "Sal" y "Pimienta" siguen separadas.
- Que la primera línea del bloque no se pega a nada.
- Que en los pasos la regla no se aplica aunque la línea anterior acabe en "de".

Las 119 pruebas del backend pasan.

## Limitaciones

- **Sin la ronda de fotos.** Este informe no incluye todavía la comparación con fotos reales. No se sabe aún qué fallos reales quedan ni con qué frecuencia aparecen ingredientes partidos en recetas manuscritas.
- **Cortes sin señal.** Si un ingrediente se parte justo entre dos palabras que no son conectores y la segunda línea empieza en mayúscula ("2 cucharadas de aceite" / "Virgen extra"), no hay forma de distinguirla de un ingrediente nuevo sin cantidad ("Sal"), y se queda como dos ingredientes. Pegarla siempre es lo que hacía la regla literal, que empeoraba las listas sin cantidades.
- **Una viñeta perdida en minúscula.** En una lista con viñetas, si ML Kit no lee la viñeta de un ingrediente sin cantidad escrito en minúscula ("sal"), esa línea se pegará al ingrediente anterior.
- **Continuaciones que empiezan por un número.** Un renglón como "3 si son pequeños)" se sigue leyendo como un ingrediente nuevo con cantidad, porque la cantidad se comprueba antes que la continuación.
- **Las medidas siguen siendo de casos construidos.** Con 2 salidas reales de ML Kit sobre 22 casos, el 94,9 % sigue siendo optimista, como ya advertía el informe 17. El CER/WER oficial de la memoria saldrá de `OcrRecognitionEvalTest` con las transcripciones a mano del usuario en los `.txt` de `ocr_eval/`. Ni esta regla ni la futura ronda de fotos (en la que es el propio Claude Code quien lee las fotos, una forma rápida de depurar, no una medición independiente) sustituyen esa medida.

## Archivos creados o modificados

| Archivo | Cambio |
| --- | --- |
| `backend/src/ocr/parseRecipeText.js` (modificado) | `continuesIngredient` y sus señales de corte, unión con el ingrediente anterior (tipo de línea `ingredient-continuation`) y la "o" inicial como conjunción en listas sin viñetas. |
| `backend/eval/data/ocrSegmentationCases.json` (modificado) | Casos `ingrediente-en-dos-lineas` e `ingrediente-en-tres-lineas`. |
| `backend/eval/results/ocrSegmentation.md` (regenerado) | Resultados con 22 casos. |
| `backend/test/parseRecipeText.test.js` (modificado) | Cuatro pruebas de ingredientes partidos. |
