# 05 · Digitalización de recetas en papel mediante OCR

## Objetivo y problema

Muchas recetas familiares solo existen en papel: cuadernos escritos a mano, fichas o recortes. Pasarlas a NutriSocial con el formulario manual obliga a teclear cada ingrediente, cada cantidad y cada paso, y eso desanima justo a los usuarios que más recetas tienen. El objetivo de esta iteración es que una foto de la receta baste para obtener un borrador: la aplicación lee el texto, separa los ingredientes (con su cantidad y unidad) de los pasos, relaciona cada ingrediente con un alimento de BEDCA y deja todo en el formulario de creación para que el usuario lo revise y lo guarde.

El problema se descompone en cuatro etapas, cada una con sus propias dificultades:

1. **Captura.** Hay que obtener la imagen con la cámara o desde la galería, lo que en Android implica pedir el permiso de cámara en tiempo de ejecución y dar a la aplicación de cámara un archivo donde escribir la foto.
2. **Reconocimiento óptico de caracteres (OCR).** La imagen tiene que convertirse en texto. Es la etapa más sensible a la calidad de la foto y de la letra.
3. **Interpretación.** El texto reconocido es una secuencia de líneas sin estructura. Hay que decidir qué es título, qué es ingrediente y qué es paso, y extraer cantidad y unidad de cada ingrediente, tolerando los errores del OCR.
4. **Emparejamiento.** Cada ingrediente debe asociarse a un alimento de la tabla `Food` para que el cálculo nutricional del informe 04 funcione.

El resultado nunca se guarda directamente. Es una propuesta que el usuario corrige, porque ninguna de las etapas es infalible.

## Decisiones técnicas

**OCR en el dispositivo con ML Kit.** Para reconocer el texto se usa Google ML Kit Text Recognition v2 (`com.google.mlkit:text-recognition:16.0.1`, la versión estable actual según la documentación oficial) en su variante con el modelo latino incluido en el APK. Se descartaron los servicios de OCR en la nube (Google Cloud Vision, Azure) por tres motivos. Primero, la privacidad: la foto no sale del teléfono y al servidor solo llega el texto. Segundo, el coste: no hay facturación por imagen ni claves de API que custodiar. Tercero, la disponibilidad: funciona sin conexión. Entre las dos variantes de ML Kit se eligió la integrada en el APK (a costa de unos megabytes más de tamaño) frente a la que descarga el modelo a través de Google Play Services. Así el modelo está disponible desde la primera ejecución y la aplicación también funciona en emuladores y dispositivos sin Play Services actualizado. La imagen se carga con `InputImage.fromFilePath`, que respeta la orientación EXIF: una foto hecha en vertical se procesa derecha sin girarla a mano.

ML Kit devuelve el texto organizado en bloques, líneas y elementos. En lugar del texto plano, se reconstruye con una línea por cada línea detectada y una línea en blanco entre bloques, porque la heurística posterior trabaja línea a línea. Para no añadir la dependencia `kotlinx-coroutines-play-services`, la tarea asíncrona de ML Kit se adapta a una función `suspend` con `suspendCancellableCoroutine`. El reconocedor está encapsulado en `RecipeTextRecognizer` y se libera (`close`) cuando se destruye el ViewModel que lo usa.

**Captura con los contratos de Activity Result.** La foto se toma con `ActivityResultContracts.TakePicture`, que delega en la aplicación de cámara del sistema. Así no hace falta construir una vista de cámara propia con CameraX. Ese contrato necesita una `Uri` donde la cámara escriba la imagen. Por eso se configuró un `FileProvider` con la ruta `cache/ocr/`. Las fotos son temporales, se borran al empezar el siguiente escaneo y, al estar en la caché, Android puede eliminarlas si necesita espacio. La `Uri` pendiente se guarda con `rememberSaveable`, porque el sistema puede destruir la actividad mientras la cámara está en primer plano. El permiso `CAMERA` se pide en tiempo de ejecución con `ActivityResultContracts.RequestPermission`, y solo al pulsar "Hacer una foto". Declararlo en el manifiesto es obligatorio: si una aplicación lo declara y no lo tiene concedido, Android impide lanzar la cámara aunque sea la del sistema. Si el usuario lo deniega, la pantalla explica que puede concederlo en los ajustes o elegir una imagen de la galería. La galería usa `ActivityResultContracts.PickVisualMedia`, que no requiere permisos de almacenamiento: en Android 13 o superior abre el selector de fotos del sistema y en versiones anteriores recurre al selector de documentos. La cámara se declara como característica opcional (`uses-feature ... required="false"`) para no excluir de Google Play a los dispositivos sin cámara.

**Heurística en el servidor, no en la aplicación.** La interpretación del texto se implementó en el backend (`POST /recipes/parse-ocr`) y no en Android por dos razones. La primera es que necesita `matchFood` y la tabla `Food`, que ya viven en el servidor. La segunda es que así la heurística se puede mejorar sin publicar una nueva versión de la aplicación. El endpoint está protegido con el mismo middleware JWT que el resto de rutas de recetas, limita el texto a 20 000 caracteres y no escribe nada en la base de datos. La lógica está en una función pura, `parseRecipeText`, sin acceso a la base de datos, lo que permite probarla de forma aislada. Se añadió una batería de pruebas con el ejecutor integrado en Node (`node --test`, sin dependencias nuevas), que se lanza con `npm test`.

**Clasificación de líneas por reglas.** La heurística recorre las líneas en orden, descartando las vacías, y aplica reglas sencillas y explicables:

- *Encabezados.* Líneas como "Ingredientes", "Preparación", "Elaboración" o "Modo de hacerlo" no se incluyen en la receta, pero cambian el contexto: a partir de ahí se sabe si se está en la lista de ingredientes o en la de pasos. Estos apartados son muy habituales en las recetas escritas a mano y son la señal más fiable de que se dispone.
- *Raciones.* "Para 4 personas", "6 raciones" o "Comensales: 2" rellenan el campo de raciones.
- *Ingrediente con cantidad.* Una línea que empieza por una cantidad es un ingrediente. Se admiten enteros, decimales con coma o punto, fracciones ("1/2"), números mixtos ("1 1/2"), fracciones Unicode ("½"), rangos ("2-3", que se toma como la media) y números escritos con letra ("dos", "media"). Tras la cantidad puede ir una unidad, pegada o separada ("200g", "200 g", "2 cdas."). Se reconocen g, kg, ml, l, cucharada, cucharadita, taza, pizca, unidad, diente y vaso, con sus plurales y abreviaturas, y se traducen a las unidades que admite el cálculo nutricional. "Diente" se trata como pieza, porque el peso por pieza del ajo es el de un diente. "Vaso" se trata como taza. Si no hay unidad ("3 huevos"), la cantidad se interpreta como piezas. El resto de la línea, sin un "de" inicial, es el nombre. Se excluyen los falsos positivos más comunes: temperaturas y tiempos ("180º", "20 minutos") y líneas cuyo nombre empieza por un verbo ("2 batir los huevos").
- *Título.* La primera línea es el título si es corta y no tiene cantidad ni verbo.
- *Ingrediente sin cantidad.* Una línea corta sin verbo ("Sal", "Aceite de oliva") es un ingrediente si está bajo "Ingredientes" o si todavía no ha aparecido ningún paso.
- *Paso.* Todo lo demás es un paso. Se eliminan las viñetas y la numeración ("1.", "2)", "Paso 3:"). Si una línea continúa la anterior (el paso previo no terminó en punto y la línea empieza en minúscula), se une a ella: la letra manuscrita parte las frases en varias líneas y el OCR las devuelve por separado.

Para reconocer los pasos se usa una lista de verbos de cocina. De cada verbo se generan automáticamente las formas con las que suele empezar un paso: infinitivo ("mezclar"), imperativo ("mezcla"), forma de usted ("mezcle"), vosotros ("mezclad") y gerundio ("mezclando"). A esas formas se añaden algunos irregulares ("cuece", "pon", "fríe") y los pronombres pegados al verbo ("mézclalo"). Una primera versión comparaba prefijos, y confundía ingredientes con verbos: "coco" parecía *cocer*, "batata" *batir* y "dorada" *dorar*. Comparar palabras completas elimina esos casos, y hay pruebas específicas que lo comprueban. Además, cuando el texto está bajo el encabezado "Preparación", una línea que empieza por número se trata como paso y no como ingrediente, porque en ese contexto suele ser un fragmento de una frase partida ("20 minutos hasta que dore").

**Corrección de errores típicos del OCR.** Las pruebas en el emulador con letra manuscrita simulada (ver "Cómo funciona") mostraron errores sistemáticos en la zona más importante de cada línea, la cantidad. Se corrigieron con reglas acotadas:

- Una "l", "I" o "|" suelta al principio de la línea es un 1 ("l cebolla").
- Una "O" junto a cifras es un 0 ("2OO g").
- Un "9" aislado entre la cantidad y una palabra es una "g" manuscrita ("300 9 de lentejas").
- En el nombre, un dígito dentro de una palabra con al menos tres letras es una letra mal leída ("2anahorias" → "zanahorias", con 2→z, 0→o, 1→l, 5→s y 8→b).
- En la línea de raciones se tolera una letra suelta pegada al número y la palabra truncada ("Para A4 persoas").

Para los errores dentro del nombre que no afectan a dígitos ("cebola"), se añadió a `matchFood` un último recurso: aceptar un alimento cuyo nombre base esté a una letra de distancia de Levenshtein, o a dos si el nombre es largo. No se aplica a palabras de menos de cinco letras, porque en ellas una sola letra cambia el alimento ("pera" y "pepa"). Esta regla tiene la puntuación más baja, así que nunca desplaza a una coincidencia mejor. Las pruebas de regresión del informe 04 siguen dando los mismos resultados. Los errores dentro de los pasos, en cambio, no se corrigen: son texto libre y el usuario los ve y los edita en el formulario.

**Emparejamiento reutilizando `matchFood`.** Cada ingrediente detectado se busca con la misma función que usa la creación de recetas. La respuesta incluye `rawName`, `quantity`, `unit`, `matched` y `foodName`, como estaba previsto. Además incluye `foodId`, para que el formulario pueda marcar el alimento como reconocido, y los campos opcionales `title`, `servings` y `lines`. Este último indica cómo se clasificó cada línea. La aplicación no lo usa, pero facilita evaluar la heurística. Por el mismo motivo, la aplicación escribe el texto bruto reconocido en logcat con la etiqueta `NutriSocialOcr`. Durante esta prueba también se detectó que "yogur natural" se emparejaba con "Mousse de yogur, natural". Se ajustó la regla de "todas las palabras presentes" para preferir los alimentos cuyo nombre base empieza por la primera palabra de la consulta.

**Integración con el formulario existente.** La propuesta no tiene una pantalla de edición propia. Se vuelca en el mismo formulario de creación, en el `RecipeViewModel` compartido: título, raciones, ingredientes (con cantidad, unidad y, si hubo coincidencia, el alimento reconocido) y pasos. Así el usuario corrige con los controles que ya conoce: autocompletado, desplegable de unidades y botones para añadir o quitar filas. Al guardar se usa exactamente el mismo `POST /recipes`, que recalcula los macronutrientes en el servidor. Nada del OCR se da por válido sin pasar por la validación y el cálculo normales. El formulario indica claramente que se trata de una propuesta: cambia el título de la barra a "Revisar receta escaneada" y muestra un aviso, que se puede ocultar, de que el contenido se ha extraído automáticamente y debe revisarse. Además, bajo cada ingrediente reconocido aparece "Se calculará como «…»", para que se vea con qué alimento de BEDCA se ha asociado.

**Estado y navegación.** El escaneo tiene su propio `ScanRecipeViewModel`, con ámbito en la pantalla de escaneo, que modela el proceso como una máquina de estados: reposo, leyendo el texto, analizando, sin texto, error y propuesta lista. Es un `AndroidViewModel` porque ML Kit necesita un `Context`. Si falla la red al analizar, "Reintentar" reenvía el texto ya reconocido sin repetir el OCR. Cuando la propuesta está lista, la pantalla de escaneo se sustituye por el formulario en la pila de navegación, de modo que "volver" desde el formulario lleva a la lista de recetas y no de nuevo a la cámara.

**Iconos.** El proyecto solo incluye los iconos básicos de Material, que no tienen cámara ni galería. En lugar de añadir `material-icons-extended`, una librería de varios megabytes, se añadieron los dos iconos necesarios como *vector drawables* con los trazados oficiales de Material Symbols.

## Cómo funciona

El pipeline completo tiene cuatro etapas: **captura → OCR en el dispositivo → heurística de clasificación → emparejamiento con BEDCA**, seguidas de la revisión del usuario.

En la pestaña Mis recetas, la barra superior tiene un botón "Escanear" que abre la pantalla de escaneo. Esta explica qué va a ocurrir, da consejos para una buena lectura (luz, hoja plana, cantidad al principio de cada línea, apartados separados) y ofrece dos botones: "Hacer una foto" y "Elegir de la galería". La primera vez que se pulsa "Hacer una foto", Android pide el permiso de cámara. Con el permiso concedido, la aplicación crea un archivo vacío en la caché, obtiene su `Uri` del `FileProvider` y abre la cámara del sistema. Al confirmar la foto, o al elegir una imagen de la galería, empieza el procesamiento.

Primero, ML Kit reconoce el texto en el propio teléfono. Mientras tanto se muestra un indicador con el mensaje "Leyendo el texto de la foto…". Si no reconoce nada, la pantalla lo indica con sugerencias para repetir la foto y los botones siguen disponibles para intentarlo de nuevo. Si hay texto, se envía a `POST /recipes/parse-ocr` con el mensaje "Identificando ingredientes y pasos…". El servidor clasifica las líneas, extrae cantidades y unidades, empareja los ingredientes con la tabla `Food` y devuelve la propuesta. La aplicación la vuelca en el formulario y navega a él.

Por ejemplo, para una receta de lentejas escrita con letra manuscrita simulada, ML Kit devolvió en el emulador, entre otras, estas líneas:

```
Para A4 persoas
300 9 de lentejas
-1 cebola
-2 2anahorias
1. Picar la cebolla, el ajo y el
pimiento y pocharlos en el aceite.
4. Cocer 4D mintos a fueoo lento.
```

Con las correcciones descritas, el servidor propuso 4 raciones y los ingredientes "lentejas" (300 g, asociado a "Lenteja, seca, cruda"), "cebola" (1 unidad, asociado a "Cebolla" por distancia de edición) y "zanahorias" (2 unidades, asociado a "Zanahoria, cruda"). Unió las dos primeras líneas de preparación en un único paso. El último paso se propuso tal cual, con sus errores, y se corrigió a mano en el formulario antes de guardar. Tras guardar, el servidor calculó 336 kcal por ración y un peso de ración de unos 203 g.

## Limitaciones

- **La separación entre ingredientes y pasos es heurística.** Se basa en expresiones regulares y listas de palabras (verbos, unidades, encabezados), sin ningún modelo de lenguaje que resuelva los casos ambiguos. Funciona bien con recetas que siguen la estructura habitual (cantidad al principio de la línea, apartados separados), pero falla con formatos libres. Algunos ejemplos: ingredientes sin cantidad escritos después de los pasos, cantidades al final ("harina, 200 g"), varios ingredientes en una misma línea ("sal y pimienta"), unidades no contempladas ("un sobre de levadura", "un chorrito de aceite") o pasos que no empiezan por verbo ni por conector. Usar un modelo de lenguaje para los casos ambiguos, o para la receta completa, es una ampliación futura que la propia propuesta del proyecto contempla. La arquitectura lo facilita: bastaría con sustituir o complementar `parseRecipeText` en el servidor, sin cambiar la aplicación ni el formato de la respuesta.
- **La calidad depende de la letra y de la foto.** ML Kit Text Recognition v2 está optimizado para texto impreso. Con letra manuscrita los resultados varían mucho según la persona: letra ligada, trazos poco claros, tinta desvaída, papel arrugado, sombras, desenfoque o una foto inclinada degradan la lectura. Los errores más dañinos son los de las cantidades: un "1" leído como "7" produce un cálculo nutricional incorrecto que ninguna heurística puede detectar. Las correcciones de errores típicos son reglas deliberadamente acotadas y no cubren todos los casos. Por todo ello, la revisión del usuario no es un paso opcional sino parte del diseño.
- **Ingredientes con varias palabras mal leídas.** La corrección por distancia de edición solo actúa sobre el nombre base del alimento y no se aplica a nombres cortos. Un ingrediente muy deformado por el OCR se queda sin alimento asociado y no suma en el cálculo. El formulario lo muestra sin la marca de reconocido, y el usuario puede elegir el alimento con el autocompletado.
- **Pruebas en emulador con letra simulada.** El flujo completo se probó en un emulador Android 12 (API 32). Se probaron la denegación y la concesión del permiso, una foto de la cámara virtual (sin texto, que produjo el aviso correspondiente) y una imagen de la galería generada con una fuente de estilo manuscrito ("Ink Free") sobre papel pautado y ligeramente girada. En esa imagen, las 16 líneas no vacías se clasificaron correctamente: título, raciones, dos encabezados, siete ingredientes y cinco líneas de pasos que dieron cuatro pasos. Pero una fuente tipográfica es mucho más regular que la letra de una persona, así que este resultado no es representativo.
- **No se ha evaluado todavía sobre un conjunto amplio de recetas reales.** Como primera medida de precisión para la memoria, se recomienda probar la aplicación en un móvil real con entre 5 y 10 fotos de recetas manuscritas propias, a ser posible de distintas personas y con distinta estructura. Para cada foto se puede anotar:
  1. El número de líneas no vacías de la receta original.
  2. Cuántas se clasificaron correctamente como título, ingrediente o paso, comparando la receta en papel con el formulario, o con el campo `lines` de la respuesta.
  3. Cuántos ingredientes tienen la cantidad y la unidad correctas.
  4. Cuántos ingredientes se asociaron al alimento de BEDCA correcto.

  El cociente entre líneas bien clasificadas y líneas totales da una primera medida de precisión de la heurística, y los otros dos recuentos, de la extracción y del emparejamiento. Conviene separar además los fallos del OCR (texto mal leído) de los de la heurística (texto bien leído pero mal clasificado). Para ello, el texto bruto de cada foto se puede recuperar en logcat filtrando por la etiqueta `NutriSocialOcr`, o copiando el cuerpo de la petición a `/recipes/parse-ocr`.
- **Sin recorte ni corrección de perspectiva.** La foto se procesa entera, tal cual. No se detectan los bordes de la hoja ni se corrige la perspectiva o el contraste, técnicas que mejorarían la lectura de fotos tomadas en ángulo.
- **Escritura latina.** El modelo incluido reconoce alfabeto latino, suficiente para recetas en español, pero no otros sistemas de escritura.

## Archivos creados o modificados

**Aplicación Android**

| Archivo | Responsabilidad |
|---|---|
| `AndroidManifest.xml` (modificado) | Permiso `CAMERA`, cámara como característica opcional y declaración del `FileProvider`. |
| `res/xml/file_paths.xml` (nuevo) | Carpeta de la caché (`ocr/`) que el `FileProvider` puede compartir con la cámara. |
| `res/drawable/ic_photo_camera.xml`, `ic_photo_library.xml` (nuevos) | Iconos de cámara y galería de Material Symbols. |
| `data/ocr/RecipeTextRecognizer.kt` (nuevo) | Envoltorio de ML Kit: `InputImage` desde la `Uri`, reconocimiento como función `suspend` y texto línea a línea. |
| `data/RecipeModels.kt` (modificado) | `ParseOcrRequest`, `OcrRecipeProposal`, `OcrIngredient` y peso de la receta en `Nutrition`. |
| `ApiService.kt`, `data/RecipeRepository.kt` (modificados) | Llamada a `POST /recipes/parse-ocr`. |
| `ui/scan/ScanRecipeViewModel.kt` (nuevo) | Máquina de estados del escaneo: OCR, análisis, sin texto, error, reintento y propuesta lista. |
| `ui/scan/ScanRecipeScreen.kt` (nuevo) | Pantalla de escaneo: permiso de cámara, `TakePicture`, `PickVisualMedia`, consejos y estados de carga y error. |
| `ui/recipes/RecipeViewModel.kt` (modificado) | `loadOcrProposal` para precargar el formulario y estado `fromOcr`. |
| `ui/recipes/RecipeFormScreen.kt` (modificado) | Aviso de receta extraída automáticamente y alimento asociado bajo cada ingrediente. |
| `ui/recipes/RecipeListScreen.kt` (modificado) | Botón "Escanear" en la barra superior. |
| `ui/home/HomeScreen.kt` (modificado) | Ruta `recetas/escanear` y paso de la propuesta al formulario. |

**Backend (`backend/`)**

| Archivo | Responsabilidad |
|---|---|
| `src/ocr/parseRecipeText.js` (nuevo) | Heurística de clasificación de líneas, extracción de cantidad y unidad, y corrección de errores del OCR. |
| `src/routes/recipes.js` (modificado) | Endpoint `POST /recipes/parse-ocr`, con emparejamiento de cada ingrediente mediante `matchFood`. |
| `src/nutrition/matchFood.js` (modificado) | Último recurso por distancia de edición y preferencia por el nombre base en la regla de "todas las palabras". |
| `test/parseRecipeText.test.js` (nuevo) | Diez pruebas de la heurística, incluida la salida real de ML Kit obtenida en el emulador. |
| `package.json` (modificado) | Script `npm test` con el ejecutor de pruebas integrado de Node. |

**Otros:** `gradle/libs.versions.toml` y `app/build.gradle.kts` (dependencia `com.google.mlkit:text-recognition:16.0.1`).
