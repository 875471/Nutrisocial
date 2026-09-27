# 12. Fotos de recetas, feed social y repaso de la interfaz

## Objetivo y problema

Hasta ahora, NutriSocial tenía de "social" poco más que el nombre. Cada usuario veía sus propias recetas y, como mucho, las de otros a través de las recomendaciones del diario (informe 09) o del buscador por despensa (informe 11), pero sin saber de quién eran. La pestaña "Inicio" solo tenía dos tarjetas de acceso rápido. Esta iteración cierra ese hueco con tres piezas:

1. **Fotos en las recetas.** Una receta sin foto cuesta más de elegir, sobre todo en una lista de recetas ajenas. El autor puede añadir una foto al crear la receta o después, desde su detalle.
2. **Feed social y "me gusta".** La pestaña "Inicio" pasa a mostrar las recetas de todos los usuarios, de la más reciente a la más antigua, con su autor, sus kcal por ración y un botón de "me gusta" con el contador.
3. **Repaso de la interfaz**, para que las cinco pestañas se comporten igual: estados de carga, error y vacío, tarjetas, navegación al detalle y barra inferior.

El reto técnico principal está en las fotos. El backend se ejecuta en Render con el plan gratuito, que **no tiene disco persistente**: todo archivo que el servidor escriba en su sistema de ficheros desaparece en el siguiente reinicio o despliegue, y en ese plan el servicio se duerme y se reinicia a menudo (informe 06). Guardar las fotos como archivos en el servidor, que sería lo intuitivo, las perdería a los pocos días.

## Decisiones técnicas

**Fotos en Base64 dentro de la base de datos.** La foto se guarda como texto Base64 en una columna `imageBase64 String? @db.Text` del modelo `Recipe`. La base de datos (PostgreSQL en Neon) es lo único persistente que tiene el proyecto, así que la foto vive junto a la receta: se crea, se lee y se borra con ella (el borrado en cascada ya existente también la elimina) y no requiere ningún servicio más. Es una **decisión pragmática para el alcance del TFG**, no la solución adecuada a gran escala. En producción real las fotos irían a un servicio de almacenamiento de objetos (Amazon S3, Cloudinary, Cloudflare R2…) y la base de datos guardaría solo su URL. Así se evita que cada consulta de recetas arrastre cientos de KB de texto, las imágenes se sirven desde una CDN y se pueden generar miniaturas en el servidor. El precio de esa alternativa es una cuenta y credenciales en un servicio externo, una dependencia más en el despliegue y, en la mayoría de casos, una tarjeta de crédito, que no encajan en el alcance del proyecto. El nombre del campo sigue el estilo en inglés que predomina en el esquema (`title`, `servings`, `prepMinutes`…).

**Compresión obligatoria en el móvil.** Base64 ocupa un 33 % más que el binario, así que guardar la foto original de una cámara (3-8 MB) sería inviable. Por eso la aplicación nunca envía la foto tal cual: `uriToCompressedBase64` la reduce para que su lado más largo no pase de **800 px** y la comprime a **JPEG con calidad 70**. Una foto de cámara queda así en unos 50-150 KB, de 30 a 60 veces menos. 800 px bastan para verla a lo ancho de un móvil, y la calidad 70 es el punto habitual en el que los artefactos de compresión apenas se notan en una foto de comida. La reducción se hace en dos pasos para no agotar la memoria: primero se decodifica ya submuestreada (`inSampleSize`, en potencias de 2) y después se escala al tamaño exacto. Además, se aplica la **orientación EXIF**, porque muchas cámaras guardan la foto "tumbada" y solo indican el giro en los metadatos. Sin corregirlo, las fotos verticales aparecerían de lado, ya que al recomprimir se pierde el EXIF. No se ha añadido ninguna librería de imágenes (como Coil): `BitmapFactory` y `Bitmap.compress` del propio Android bastan.

**Validación en el servidor.** Aunque la aplicación siempre comprime, el servidor no se fía del cliente. `validateImageBase64` (en `social/recipeSocial.js`) comprueba que el texto sea Base64 válido y que, una vez decodificado, **no pase de 2 MB**. El tamaño se calcula a partir de la longitud del texto, sin decodificarlo entero. También comprueba que empiece por la firma de un JPEG, PNG o WebP, para no guardar texto arbitrario que parezca Base64. Si pasa del límite, responde con un 400 que pide comprimirla más. Como la foto viaja dentro del JSON, el límite del cuerpo de Express (100 KB por defecto) sube a 3 MB, y si aun así se supera, la respuesta es un 413 en JSON con un mensaje en castellano.

**Quitar la foto con `null` explícito.** `PUT /recipes/:id/image` con `{ "imageBase64": null }` quita la foto, y un cuerpo sin el campo es un error, para que una petición mal formada no la borre sin querer. En Android esto tiene un detalle: el conversor Gson de Retrofit **omite los campos `null`**, así que `{ imageBase64: null }` llegaría como `{}`. Por eso esa petición se envía con un cuerpo JSON escrito a mano (`RequestBody`), en lugar de cambiar la configuración de Gson para toda la aplicación.

**Solo el autor cambia la foto.** La ruta compara `authorId` con el usuario del token (`canEditRecipe`) y responde 403 si no coinciden, antes de tocar nada. En la aplicación, el botón "Añadir foto" / "Cambiar foto" solo aparece si `recipe.authorId` coincide con el id del usuario en sesión. Se compara por id y no por nombre, porque dos usuarios pueden llamarse igual. Ocultar el botón es solo comodidad: la protección real es la comprobación del servidor.

**"Me gusta" como tabla propia.** `RecipeLike` guarda un par (usuario, receta) con índice único. Dar like usa `upsert`, así que repetirlo no duplica ni falla, y quitarlo usa `deleteMany`, así que quitar uno que no existe tampoco es un error. Las dos operaciones son idempotentes, lo que simplifica la aplicación (si una petición se repite, el resultado es el mismo) y ambas devuelven el estado final (`likesCount`, `likedByMe`). Para calcular esos dos campos no se cargan todos los likes: se pide a Prisma el recuento (`_count`) y, como mucho, el like del propio usuario (`likes` filtrado por `userId` con `take: 1`). Esa parte de la consulta y su traducción a la respuesta (`likeFields` y `likeSummary`) están separadas en funciones puras y se reutilizan en el detalle, en "Mis recetas" y en el feed.

**Cambio optimista con marcha atrás.** Al pulsar el corazón, el icono y el contador cambian al instante, sin esperar al servidor. Si la petición falla, se restaura el estado anterior y un *snackbar* lo explica. Mientras hay una petición en curso para una receta, se ignoran más toques sobre ella. Así, dos respuestas que llegan en distinto orden no pueden dejar el corazón en un estado distinto al del servidor.

**Feed con paginación por cursor.** `GET /recipes/feed?cursor=&limit=20` ordena por `createdAt` descendente y, a igualdad, por `id`, para que el orden sea estable. El cursor es el id de la última receta recibida. Se eligió frente a la paginación por página (`offset`) porque, si alguien publica una receta mientras otro usuario está leyendo el feed, con `offset` todo se desplaza un puesto y la página siguiente repite la última receta vista. Para saber si hay más sin hacer otra consulta, se piden `limit + 1` recetas: si llega la de más, `nextCursor` es el id de la última de la página, y si no, es `null`. Por si acaso, el cliente descarta ids repetidos al añadir una página.

**Imágenes fuera del hilo principal y con caché.** Decodificar un JPEG de 800 px tarda unos milisegundos, suficiente para notar tirones al hacer *scroll* si se hace en el hilo de la interfaz. `Base64Image` lo decodifica en segundo plano (`produceState` con `Dispatchers.Default`), guarda el resultado en una caché LRU limitada a 1/8 de la memoria de la aplicación y, mientras tanto, muestra un fondo neutro del tamaño final, para que la lista no salte al aparecer la foto. Las miniaturas se decodifican ya reducidas a unos 3 px por dp, así que una tarjeta de 72 dp no carga en memoria la foto de 800 px.

**Un único mecanismo para cámara y galería.** El escáner OCR (informe 05) ya resolvía la cámara (archivo temporal compartido con `FileProvider`, permiso en tiempo de ejecución, Uri guardado para sobrevivir a que Android recree la actividad) y la galería (selector de fotos del sistema, sin permisos). Ese código se ha sacado a `rememberPhotoPicker` y ahora lo usan el escáner, el formulario y el detalle. Cada uno escribe en su propia carpeta de la caché (`ocr/` y `photos/`, declaradas en `file_paths.xml`).

## Cómo funciona

**Backend.** La migración `20260927200000_recipe_image_and_likes` añade la columna `imageBase64` (opcional) y la tabla `RecipeLike`. Como en los informes 08 y 11, no se usó `prisma migrate dev`, porque el `.env` apunta a la base de datos de producción. El SQL se generó comparando el esquema nuevo con la base de datos real (`prisma migrate diff --from-url`, que solo lee), se comprobó que solo añadía una columna nula, una tabla, dos índices y dos claves ajenas, y se aplicó con `prisma migrate deploy`. En `routes/recipes.js`:

- `POST /recipes` acepta un `imageBase64` opcional, que se valida igual que en el `PUT`.
- `GET /recipes/:id`, `GET /recipes/mine` y la respuesta de `POST` incluyen ahora `imageBase64`, `authorName` (con un `include` del autor limitado a su nombre), `likesCount` y `likedByMe`. `GET /recipes/by-pantry` añade `imageBase64`, `authorId` y `authorName` a cada resultado.
- `GET /recipes/feed` devuelve `{ recipes, nextCursor }`. Cada receta trae `id`, `title`, `authorName`, `imageBase64`, `kcalPerServing`, `likesCount` y `likedByMe`, además de `authorId`, raciones y tiempo. Con un `select` explícito, no carga ingredientes ni pasos. El límite por defecto es 20 y el máximo, 50.
- `PUT /recipes/:id/image` valida el cuerpo, comprueba que la receta existe (404) y que la pide su autor (403), y guarda o quita la foto.
- `POST` y `DELETE /recipes/:id/like` comprueban que la receta existe y devuelven el recuento actualizado.

Las rutas nuevas (`/feed`) se declaran antes que `/:id`, igual que `/by-pantry`.

**Crear una receta con foto.** El formulario tiene una sección "Foto (opcional)" con el botón "Añadir foto", que despliega "Hacer una foto" o "Elegir de la galería". Al elegirla, se comprime en el momento (el botón muestra un indicador mientras tanto) y se ve una vista previa 4:3 con "Cambiar foto" y "Quitar". El Base64 se guarda en el estado del formulario (`photoBase64`) y viaja en el `POST` al guardar.

**Detalle de receta.** Si la receta tiene foto, aparece arriba a todo lo ancho. Si no, el detalle empieza por el título como antes, sin reservar un hueco. Bajo el título se lee "Por Ana", o "Receta tuya" si es propia, y a la derecha está el corazón con el contador. El autor ve además "Añadir foto" o "Cambiar foto" y, si hay foto, "Quitar foto" (con confirmación). El resultado se confirma con un *snackbar*.

**Inicio (feed).** La pestaña saluda al usuario ("Hola, Javier") y muestra el feed como una lista de tarjetas con la misma estructura que las de "Mis recetas": miniatura (la foto o, si no tiene, el cuadro con la inicial), título, autor, etiquetas de kcal por ración y tiempo, y el corazón. Al tocar una tarjeta se abre el detalle. Se puede tirar hacia abajo para refrescar (`PullToRefreshBox` de Material 3, disponible en la versión 1.3 que trae el BOM del proyecto). Al final de la lista, un botón "Cargar más" pide la página siguiente o, si no quedan, se indica "No hay más recetas". El acceso rápido a "Crear receta" pasa a ser el mismo botón flotante "Nueva receta" que ya tenía "Mis recetas". La carga, el error y el estado vacío se muestran dentro de la propia lista, para que tirar hacia abajo funcione también en esos casos.

**Coherencia entre pantallas.** `FeedViewModel` y `RecipeViewModel` son independientes, pero ambos viven en `HomeScreen`. Cuando el detalle cambia (al cargar, al dar like o al cambiar la foto), `HomeScreen` copia esos datos a la tarjeta del feed (`syncRecipe`), y `RecipeViewModel` actualiza también la copia de "Mis recetas". Al guardar una receta nueva, el feed se refresca para que aparezca la primera.

**Repaso de la interfaz.** Durante la revisión de las cinco pestañas se corrigió lo siguiente:

- **Tarjetas que no llevaban a ningún sitio.** Las recomendaciones y las entradas de receta del diario eran las únicas tarjetas de receta que no abrían el detalle. Ahora lo abren, como en "Mis recetas", el feed y la despensa. Las entradas de alimentos sueltos y las de recetas borradas no tienen detalle y siguen sin ser pulsables.
- **Miniaturas.** "Mis recetas", el feed y los resultados de la despensa usan el mismo componente de miniatura (`RecipeThumbnail`), así que una receta con foto se reconoce igual en toda la aplicación. La despensa muestra además el autor, porque sus resultados pueden ser de otros usuarios.
- **Barra inferior.** Las etiquetas pasan a ser de una palabra ("Mis recetas" → "Recetas"): con cinco pestañas, la etiqueta larga se cortaba en pantallas estrechas. El título de la pantalla sigue siendo "Mis recetas".
- **Saludo neutro.** "Bienvenido, …" pasa a "Hola, …", que no presupone el género del usuario.
- **Estados coherentes.** El feed usa los mismos componentes de carga (`LoadingBox`), error con "Reintentar" y vacío (`CenteredMessage`) que las demás pestañas, y los avisos puntuales van a un *snackbar*, como en el diario y el detalle.

**Pruebas.** `test/recipeSocial.test.js` añade 7 pruebas y la batería del backend pasa a 62, todas correctas:

- Cálculo de `likesCount` y `likedByMe` a partir de lo que devuelve Prisma, y filtro por usuario en la consulta.
- Validación de la foto: JPEG y PNG válidos, `null`, prefijo `data:`, texto no Base64, algo que no es una imagen, y el límite exacto de 2 MB y un byte más.
- Las rutas contra un Prisma simulado en memoria, con Express y JWT reales:
  - `PUT /recipes/:id/image` devuelve **403 a quien no es el autor**, tanto al poner como al quitar la foto, y la foto no cambia. Al autor le funciona, y los casos 404 y 400 responden como deben.
  - Dar like dos veces no duplica, `likedByMe` depende de quién pregunta, y quitar un like inexistente no es un error.
  - El feed pagina con el cursor, en orden, e incluye autor, kcal por ración y likes.

En Android, la aplicación compila sin avisos y se abrió en el emulador contra el backend en local: el feed cargó con el saludo, la tarjeta, "Receta tuya", el contador y el pie "No hay más recetas". La prueba manual de la foto y del like no se pudo completar en esa sesión (ver Limitaciones).

## Limitaciones

- **Límite de tamaño de la foto.** El servidor rechaza fotos de más de 2 MB decodificadas, y el cuerpo de la petición no puede pasar de 3 MB. Con la compresión del móvil (800 px, JPEG al 70 %) no se llega ni de lejos, pero un cliente que no comprima recibirá un error. La resolución máxima de 800 px basta para el móvil, pero se vería pobre a pantalla completa en una tableta o un monitor.
- **Almacenamiento de Neon.** El plan gratuito de Neon tiene un límite total de unos **0,5 GB**. Guardar las fotos en la base de datos lo consume mucho más rápido que guardar solo URLs: a unos 100 KB por foto (unos 135 KB en Base64), caben del orden de 3.000-4.000 recetas con foto antes de llenarla, compartiendo espacio con el resto de datos. Con una URL por receta cabrían millones.
- **Peso de las respuestas.** Como la foto va dentro de cada receta, una página del feed con 20 recetas con foto puede pesar 2-3 MB, y `GET /recipes/mine` devuelve todas las fotos del usuario de una vez. Con datos móviles es notable. Se podría reducir guardando además una miniatura pequeña para las listas, pero lo razonable sería el almacenamiento de objetos, con URLs e imágenes servidas y cacheadas por HTTP.
- **Sin moderación.** No hay ningún control sobre el contenido de las fotos ni de las recetas del feed: cualquier usuario registrado puede publicar cualquier imagen o texto, y no hay forma de denunciar una receta ni de ocultar a un usuario. En una aplicación pública haría falta al menos un mecanismo de denuncia y revisión, y probablemente un filtro automático de imágenes.
- **Producción real.** En una versión de producción las fotos irían a un servicio de almacenamiento de objetos (S3, Cloudinary, R2) con una CDN delante, subidas directamente desde el móvil con URLs firmadas, y la base de datos guardaría solo la URL. Este cambio afectaría solo al origen de `imageBase64` (pasaría a ser una URL) y al componente que la pinta.
- **El feed es solo cronológico.** No hay seguidores, ni filtros, ni orden por popularidad: se ven todas las recetas de todos los usuarios, de la más nueva a la más antigua, y los likes solo se cuentan. No hay comentarios.
- **Prueba en el emulador incompleta.** La aplicación compila, y la carga del feed se comprobó en el emulador contra el backend en local. Durante la sesión alguien más estaba usando el emulador, así que no se completaron a mano el like, el cambio de foto ni la creación de una receta con foto. Tampoco se subieron fotos de prueba, para no dejarlas en la base de datos de producción. La lógica del backend está cubierta por las pruebas, pero la compresión en el móvil (tamaños resultantes, giro EXIF) no tiene pruebas automáticas y falta verificarla en un dispositivo. Hasta desplegar el backend en Render, la aplicación (que apunta a Render) mostrará un error en el feed. *Actualización (informe 14): al completar esa prueba se descubrió que la compresión rechazaba todas las fotos por un error al leer sus dimensiones; se corrigió y el recorrido completo se verificó en el emulador.*

## Archivos creados o modificados

**Backend (`backend/`)**

| Archivo | Cambio |
|---|---|
| `prisma/schema.prisma` (modificado) | `imageBase64` en `Recipe` y modelo `RecipeLike` con relaciones en `User` y `Recipe`. |
| `prisma/migrations/20260927200000_recipe_image_and_likes/migration.sql` (nuevo) | Columna `imageBase64`, tabla `RecipeLike`, índice único e índice por receta. |
| `src/social/recipeSocial.js` (nuevo) | `validateImageBase64` (Base64, 2 MB, firma de imagen), `likeFields`, `likeSummary` y `canEditRecipe`. |
| `src/routes/recipes.js` (modificado) | Foto en `POST`, `authorName`, `imageBase64` y likes en las respuestas; `GET /feed`, `PUT /:id/image`, `POST` y `DELETE /:id/like`. |
| `src/index.js` (modificado) | Límite del cuerpo JSON a 3 MB y respuesta 413 en JSON. |
| `test/recipeSocial.test.js` (nuevo) | 7 pruebas: likes, validación de la foto, 403 del `PUT` para quien no es el autor, likes y feed por HTTP. |

**Aplicación Android (`app/src/main/`)**

| Archivo | Cambio |
|---|---|
| `java/.../ui/ImageUtils.kt` (nuevo) | `uriToCompressedBase64` (800 px, JPEG 70, EXIF), `base64ToBitmap` y decodificación con caché. |
| `java/.../ui/PhotoPicker.kt` (nuevo) | `rememberPhotoPicker`: cámara con `FileProvider` y permiso, y galería; extraído del escáner. |
| `java/.../ui/recipes/RecipeSocialComponents.kt` (nuevo) | `Base64Image`, `RecipeThumbnail`, `LikeButton`, `rememberRecipePhotoPicker` y `PhotoSourceButton`. |
| `java/.../ui/home/FeedViewModel.kt` (nuevo) | Carga, refresco, "Cargar más", like optimista y sincronización con el detalle. |
| `java/.../ui/home/HomeTabScreen.kt` (reescrito) | Feed con tirar para refrescar, "Cargar más", tarjetas con autor y likes, y botón "Nueva receta". |
| `java/.../data/FeedRepository.kt` (nuevo) | `getFeed(cursor)`. |
| `java/.../data/RecipeModels.kt`, `PantryModels.kt` (modificados) | Foto, autor y likes en `Recipe`; `FeedRecipe`, `FeedPage` y `LikeState`; foto en `CreateRecipeRequest`; foto y autor en los resultados de la despensa. |
| `java/.../data/RecipeRepository.kt`, `ApiService.kt` (modificados) | `updateImage` (JSON escrito a mano para enviar `null`), `setLiked`, y los endpoints de feed, foto y likes. |
| `java/.../ui/recipes/RecipeViewModel.kt` (modificado) | Foto en el formulario, `toggleLike` optimista, `updatePhoto` y estado de acciones del detalle. |
| `java/.../ui/recipes/RecipeFormScreen.kt` (modificado) | Sección "Foto (opcional)" con vista previa. |
| `java/.../ui/recipes/RecipeDetailScreen.kt` (modificado) | Foto, autor, "me gusta" y acciones de foto para el autor. |
| `java/.../ui/recipes/RecipeComponents.kt` (modificado) | `RecipeCard` usa la miniatura con foto. |
| `java/.../ui/scan/ScanRecipeScreen.kt` (modificado) | Usa `rememberPhotoPicker`. |
| `java/.../ui/pantry/PantryScreen.kt` (modificado) | Miniatura y autor en los resultados. |
| `java/.../ui/log/LogScreen.kt` (modificado) | Recomendaciones y entradas de receta abren el detalle. |
| `java/.../ui/home/HomeScreen.kt` (modificado) | `FeedViewModel`, acciones del feed y del detalle, sincronización, etiqueta "Recetas" y navegación desde el diario. |
| `res/xml/file_paths.xml` (modificado) | Carpeta `photos/` de la caché para las fotos de cámara. |
