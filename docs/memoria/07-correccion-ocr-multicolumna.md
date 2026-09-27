# 07. Corrección del análisis de recetas escaneadas con varias columnas

## Objetivo y problema

El escáner de recetas del informe 05 se había probado sobre todo con recetas de una sola columna, escritas a mano o con una fuente manuscrita. Al probarlo con capturas reales de recetas de repostería sacadas de internet apareció otro tipo de fallo. Estas recetas tienen un formato muy habitual: el título ocupa dos líneas, los ingredientes están en dos columnas y los pasos van en una lista. La receta de prueba fue "Galletas con chips de chocolate", con ocho ingredientes y cuatro pasos. La propuesta que llegó a la pantalla "Revisar receta escaneada" tenía estos errores:

- El título se quedó en "Galletas con chips", y la segunda línea ("de chocolate") se añadió como un ingrediente suelto llamado "chocolate", sin cantidad.
- "115 g de mantequilla blanda" llegó con cantidad 15.
- "1 huevo" llegó como un ingrediente llamado "Ihuevo", sin cantidad.
- "5 ml de esencia de vainilla" no apareció entre los ingredientes. Se convirtió en el primer paso, con el texto ". 5 ml de esencia de vainilla".

Los otros cinco ingredientes se reconocieron bien. El objetivo de esta iteración es corregir estos fallos de forma general, no solo para esta receta, y dejar una herramienta para diagnosticar más rápido los próximos casos.

Estos fallos tienen tres causas distintas:

1. **Orden de lectura no lineal.** ML Kit devuelve el texto por bloques, es decir, por regiones de la imagen que considera independientes. En una maqueta a varias columnas, cada columna, e incluso cada grupo de líneas, puede formar un bloque propio. El orden de esos bloques no tiene por qué coincidir con el orden de lectura humano. Por eso un ingrediente de la columna derecha puede llegar detrás del encabezado "Preparación", y las dos líneas del título pueden llegar como bloques separados. La heurística del informe 05 recorría las líneas en orden y confiaba en que los encabezados delimitaban las secciones. Con este orden de lectura, esa suposición deja de cumplirse.
2. **Errores del OCR pegados a la cantidad.** Las correcciones del informe 05 solo interpretaban una "I" o una "l" como "1" si iban separadas por un espacio ("l cebolla"). ML Kit también las devuelve pegadas al nombre ("Ihuevo") o a otras cifras ("I15").
3. **Viñetas convertidas en signos de puntuación.** Una viñeta pequeña ("·") se leyó como un punto seguido de un espacio. El punto no estaba en la lista de viñetas, así que la línea no empezaba por una cantidad y no se reconocía como ingrediente.

## Decisiones técnicas

**No depender solo de los encabezados.** La solución más completa sería reconstruir el orden de lectura a partir de la geometría: ML Kit da la caja de cada bloque y de cada línea, y con ella se podrían agrupar las columnas. Se descartó en esta iteración por dos motivos. Obligaría a cambiar el formato de la petición (texto con coordenadas en lugar de texto plano). Además, el orden de lectura correcto en maquetas arbitrarias es un problema difícil en sí mismo. En su lugar, se optó por hacer la heurística más tolerante al desorden, con reglas que solo se activan cuando la forma de la línea no deja dudas.

**Ingredientes que aparecen detrás de "Preparación".** En la sección de pasos, una línea que empieza por un número se trataba siempre como un paso, porque suele ser el fragmento de una frase partida ("20 minutos hasta que dore"). Esa regla se mantiene, pero con una excepción acotada. Si la línea tiene la forma inequívoca de un ingrediente (cantidad, una unidad de medida reconocida, "de" y un nombre, como en "5 ml de esencia de vainilla") y ninguna de sus palabras es un verbo de cocina, se clasifica como ingrediente. Se exige la unidad escrita y la preposición porque "3 huevos" dentro de la preparación sí puede ser el final de una frase partida. Se comprueban todas las palabras, y no solo la primera, para que "2 cucharadas de aceite y remover." siga siendo un paso. La lista de verbos es la misma del informe 05, con sus formas conjugadas y sus pronombres pegados.

**Título en varias líneas.** Una línea que sigue inmediatamente al título se une a él si es corta, no lleva viñeta, no empieza por una cantidad ni por un verbo, y además se cumple una de estas dos condiciones:

- Está antes del primer encabezado de la receta. Si hay un "Ingredientes" más abajo, lo que va antes y no tiene forma de ingrediente es parte del título.
- Empieza por un conector ("de", "con", "y", "al", "sin"...). Un título partido suele continuar así, y un ingrediente sin cantidad casi nunca empieza de ese modo.

La segunda condición es necesaria porque, en una receta sin encabezados, una línea corta tras el título ("Tomate") debe seguir siendo un ingrediente.

**Un "1" leído como "I" o "l" pegado a la palabra.** Si la "I" o la "l" inicial va pegada a otras cifras ("I15 g"), se sustituye siempre por un 1: ninguna palabra española empieza así. Si va pegada a letras ("Ihuevo"), la corrección es arriesgada, porque muchas palabras empiezan por "l" de verdad: "leche", "lentejas", "limón". Por eso solo se corrige cuando el resto de la palabra es un alimento que se cuenta por piezas: huevo, diente, loncha, cebolla, tomate, limón, yema... Esta lista vive en el propio parser, que sigue sin consultar la base de datos. Con esa condición, "Ihuevo" pasa a ser "1 huevo", mientras que "leche" no cambia, porque "eche" no es un alimento.

**Unidad de las cantidades sin medida.** Una línea como "1 huevo" o "2 dientes de ajo" ya se interpretaba como piezas, con la unidad `unidad` (la que el formulario muestra como "ud" y la que acepta `POST /recipes`). Lo que fallaba era la separación entre la cantidad y el nombre, no la unidad. Unidades como "loncha" no se convierten en `unidad` eliminando la palabra del nombre ("3 lonchas de queso" deja el nombre "lonchas de queso"). El motivo es nutricional: el peso por pieza de BEDCA es el de una pieza entera. En el ajo esa pieza es precisamente un diente, pero en el queso sería el queso completo.

**Cantidades de más de dos cifras.** El informe de pruebas apuntaba a la expresión regular de la cantidad. Al revisarla, se comprobó que ya aceptaba cualquier número de cifras y decimales con coma o con punto: "115 g de mantequilla blanda" daba 115 al procesarla directamente. El 15 venía, por tanto, del texto que devolvió ML Kit y no de la expresión regular. Las variantes más probables son "I15", una cifra partida por un espacio ("1 15 g") o el "1" absorbido por la viñeta. Las dos primeras se corrigen ahora. La cifra partida solo se une si va seguida de una unidad de peso o volumen, para que "1 15 gambas" no cambie. La tercera no se puede recuperar a partir del texto, porque el dígito ya no está. Para confirmar la causa la próxima vez, se añadió el registro del texto bruto que se describe más abajo. Además, se añadieron pruebas con cantidades de tres y cuatro cifras y con decimales, para que un cambio futuro de la expresión no rompa estos casos.

**Viñetas convertidas en puntuación.** Un punto, una coma o un punto y coma aislados al principio de la línea, seguidos de un espacio, se tratan ahora como una viñeta, igual que otros símbolos habituales ("▪", "●"). La expresión también quita varias viñetas seguidas ("- · "). Un punto pegado a una cifra (".5") no se toca.

**Instrumentación.** `RecipeTextRecognizer` escribe en Logcat, con la etiqueta `OCR_RAW`, el texto plano que devuelve ML Kit (`Text.getText()`) antes de reconstruirlo línea a línea y enviarlo al servidor. Así, cuando una foto real se interpreta mal, basta con filtrar Logcat por `OCR_RAW` para ver si el fallo es del OCR (texto mal leído o desordenado) o de la heurística (texto bien leído pero mal clasificado). Se mantiene el registro de `ScanRecipeViewModel` con la etiqueta `NutriSocialOcr`, que muestra el texto ya reconstruido por bloques, es decir, exactamente lo que recibe el servidor.

## Cómo funciona

El recorrido de las líneas es el mismo del informe 05. Antes de empezar, el parser localiza el primer encabezado de la receta, y durante el recorrido se aplican las reglas nuevas en este orden:

1. Se quitan las viñetas, incluidos ahora los signos de puntuación sueltos al principio de la línea.
2. Se corrigen los errores del OCR en la cantidad: "I" o "l" pegada a cifras o a un alimento contable, espacio dentro de la cifra, "O" por 0 y "9" por "g".
3. Fuera de la sección de pasos, una línea que empieza por una cantidad es un ingrediente, como antes. Dentro de la sección de pasos, solo lo es si tiene la forma "cantidad + unidad + de + nombre" y no contiene verbos.
4. La primera línea sin cantidad ni verbo es el título, y las líneas siguientes que cumplen las condiciones descritas se le añaden.
5. El resto de reglas no cambian: ingredientes sin cantidad bajo "Ingredientes" y pasos, uniendo las líneas partidas.

Con la receta de las galletas, simulando el desorden que produce ML Kit (el título partido en dos bloques, "1 huevo" leído como "- Ihuevo" y "5 ml de esencia de vainilla" detrás de "Preparación" con un punto delante), el resultado es el esperado:

- Título: "Galletas con chips de chocolate".
- Ocho ingredientes con su cantidad y su unidad: mantequilla blanda 115 g, azúcar rubia 100 g, azúcar blanca 100 g, harina 190 g, huevo 1 ud, bicarbonato 3 g, chips de chocolate 170 g y esencia de vainilla 5 ml.
- Cuatro pasos, sin ningún ingrediente entre ellos.

Este caso quedó como prueba de regresión en `backend/test/parseRecipeText.test.js`. Junto a él se añadieron otras cuatro pruebas:

- Cantidades de tres y cuatro cifras, decimales y "3 lonchas de queso".
- Las variantes "Ihuevo", "I15 g" y "1 15 g", comprobando que "leche", "lentejas" y "1 15 gambas" no se alteran.
- El título en dos líneas, con y sin encabezados. Sin encabezados, "Tomate" sigue siendo un ingrediente.
- Los límites de la regla de ingredientes dentro de "Preparación": una línea con verbo, un tiempo de horno o "3 huevos" siguen siendo pasos.

Las diez pruebas anteriores siguen pasando sin cambios, 15 en total.

## Limitaciones

- **Títulos en fuentes decorativas.** El reconocimiento de títulos en fuentes cursivas, manuscritas o decorativas sigue sin funcionar bien. Es una limitación conocida de ML Kit Text Recognition v2, que solo reconoce bien el texto impreso, y el título es justo donde las recetas de internet suelen usar tipografías decorativas. Si el OCR no lee el título, el parser no puede recuperarlo, y el usuario tiene que escribirlo en el formulario.
- **Maquetas complejas.** Las reglas nuevas toleran el desorden típico de dos columnas, pero no reconstruyen el orden de lectura. Las maquetas con más de dos columnas, con texto superpuesto a fotografías o con cajas y recuadros intercalados pueden seguir mezclando secciones. La mejora natural sería enviar al servidor las coordenadas de cada bloque que ya da ML Kit y ordenar las columnas por geometría antes de clasificar.
- **Ingredientes sin unidad dentro de los pasos.** Un ingrediente de la columna derecha que llegue detrás de "Preparación" sin unidad de medida ("1 huevo") sigue clasificándose como paso. Separarlo del final de una frase partida exigiría más contexto del que da el texto plano.
- **Lista cerrada de alimentos contables.** La corrección de "Ihuevo" solo funciona con los alimentos de la lista del parser. Un ingrediente que no esté en ella ("Isalchicha") se queda sin corregir, y el usuario tiene que arreglarlo en el formulario.
- **Dígitos perdidos por el OCR.** Si ML Kit omite una cifra, o la funde con la viñeta, la cantidad llega mal y ninguna regla puede detectarlo. El registro `OCR_RAW` sirve para distinguir este caso de un fallo del parser, pero no lo evita. La revisión del usuario sigue siendo imprescindible.

## Archivos creados o modificados

| Archivo | Cambio |
|---|---|
| `backend/src/ocr/parseRecipeText.js` (modificado) | Puntuación suelta tratada como viñeta. Corrección de "I"/"l" pegada a cifras o a un alimento contable y de cifras partidas por un espacio. Ingredientes con forma inequívoca dentro de "Preparación" (`parseStrayIngredient`). Título en varias líneas. |
| `backend/test/parseRecipeText.test.js` (modificado) | Cinco pruebas nuevas, entre ellas la regresión de la receta a dos columnas. |
| `app/src/main/java/com/example/nutrisocial/data/ocr/RecipeTextRecognizer.kt` (modificado) | Registro del texto bruto de ML Kit en Logcat con la etiqueta `OCR_RAW`. |
| `docs/memoria/07-correccion-ocr-multicolumna.md` (nuevo) | Este informe. |
