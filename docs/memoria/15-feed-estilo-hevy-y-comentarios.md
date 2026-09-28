# 15. Feed con tarjetas estilo Hevy y comentarios en recetas

> Informe escrito a posteriori: los cambios se hicieron en los commits `2db1437` (backend de comentarios y vistas previas), `2a7673d` (`likersPreview` en todas las respuestas) y `a5c8b62` (Android), pero el informe no se redactó entonces. Se escribe ahora para que la memoria no tenga ese hueco.

## Objetivo y problema

El feed del informe 12 era una lista de tarjetas compactas: miniatura, título, autor, kcal por ración, tiempo y el corazón del "me gusta". Cumplía su función, pero tenía dos carencias para una **red social** de recetas:

1. **No se veía la receta sin abrirla.** Para saber qué ingredientes llevaba o cuántos pasos tenía había que entrar al detalle, y la foto era una miniatura de 64 dp. El feed servía para descubrir títulos, no recetas.
2. **La única interacción era el "me gusta".** No había forma de preguntar al autor, dar una variante o comentar el resultado. El propio informe 12 lo dejaba como limitación: "No hay comentarios".

Como referencia de diseño se tomó **Hevy**, una aplicación de registro de entrenamientos con un feed social muy pulido. Sus publicaciones tienen una estructura muy reconocible: cabecera con avatar, nombre y hace cuánto se publicó; título y texto libre; una fila de estadísticas separadas por líneas finas (duración, volumen, series); un carrusel deslizable con fotos y el detalle del entrenamiento; acciones; "A X y a otras N personas les gusta"; los últimos comentarios; y un campo para comentar sin salir del feed. Esa estructura se traslada casi punto por punto a una receta: el entrenamiento se convierte en ingredientes y pasos, y las estadísticas pasan a ser tiempo, kcal, raciones y proteína.

## Decisiones técnicas

### Modelo de comentarios

`Comment` es una tabla propia con `text`, `userId`, `recipeId` y `createdAt`, con borrado en cascada desde la receta y desde el usuario: si se borra una cuenta o una receta, sus comentarios desaparecen con ella. Tiene un índice compuesto `(recipeId, createdAt)`, que es exactamente el orden en que se leen (los de una receta, del más reciente al más antiguo).

- **Sin edición.** Un comentario se publica o se borra; para corregirlo, se borra y se escribe otro. Así no hace falta guardar ni mostrar "editado", y lo que otros usuarios han leído no cambia por debajo.
- **Solo el autor puede borrar.** `DELETE /comments/:id` responde 403 si el comentario es de otro usuario. El autor de la receta **no** puede borrar comentarios ajenos en su receta: sería una forma de moderación, y moderar requiere reglas (qué se puede borrar, si se avisa) que están fuera del alcance.
- **Texto limitado a 500 caracteres**, recortado y no vacío (`validateCommentText`, función pura con sus pruebas). La app corta en el mismo límite mientras se escribe, así que el error del servidor solo aparece si alguien llama a la API directamente.
- **Rutas.** Listar y crear van bajo la receta (`GET`/`POST /recipes/:id/comments`), porque siempre se hace en su contexto; borrar va por el id del comentario (`DELETE /comments/:id`), porque para borrar no hace falta saber de qué receta es.

### Paginación y recuento

`GET /recipes/:id/comments` pagina con el **mismo patrón de cursor que el feed** (informe 12): el cursor es el id del último comentario recibido, se piden `limit + 1` para saber si hay página siguiente y el orden es `createdAt` descendente con el `id` como desempate. Cada página incluye además `total`, el número de comentarios de la receta: la aplicación lo necesita para el contador de la tarjeta y, si solo se contaran los de las páginas cargadas, el contador bajaría a 20 al abrir una receta con 30 comentarios.

### Vistas previas sociales sin consultas de más

La tarjeta nueva necesita, para cada receta del feed, tres datos sociales además de los likes:

- `commentsCount`: total de comentarios.
- `commentsPreview`: los **2** más recientes, con el nombre de su autor.
- `likersPreview`: los nombres de quienes dieron los **3** últimos "me gusta", para escribir "Le gusta a Lucía y a otras 4 personas".

Los dos primeros se añaden a la misma consulta de Prisma del feed: `_count` pasa a contar también `comments` (`socialFields`, que amplía el `likeFields` del informe 12), y la relación `comments` se pide ordenada y con `take: 2`. `likersPreview`, en cambio, **no cabe en esa consulta**: la relación `likes` ya se está usando allí filtrada por el usuario actual, para saber si ha dado su like (`likedByMe`), y Prisma no permite pedir la misma relación dos veces con filtros distintos. Se resuelve con **una segunda consulta para toda la página** (`likersSelect` + `likersByRecipe`): los últimos 3 likes de cada receta de la página, en una sola ida a la base de datos, que se convierten en un mapa `id de receta → nombres`. Así, una página de 20 recetas sigue costando dos consultas, no veintiuna.

En el commit siguiente (`2a7673d`) `likersPreview` se extendió a **todas** las respuestas de receta (detalle, "Mis recetas", edición, cambio de foto) mediante `toRecipeResponses`, para que la frase "Le gusta a..." fuera la misma en el feed y en el detalle.

### El feed trae ingredientes y pasos

El carrusel enseña ingredientes y pasos sin abrir la receta, así que `GET /recipes/feed` pasa a incluirlos, con un `select` ajustado: de cada ingrediente solo nombre, cantidad y unidad, sin el peso estimado ni el alimento asociado, que solo interesan en el detalle. También se añade `proteinPerServing` para la barra de estadísticas y la `description` opcional, un campo nuevo de `Recipe` (texto libre de hasta 1000 caracteres) que hace el papel del texto de una publicación de Hevy. Las páginas pesan algo más, pero el texto de una receta es despreciable al lado de su foto (informe 12).

### Una sola tarjeta para el feed y el detalle

`RecipeFeedCard` es el mismo componente en el feed y en el detalle. Recibe un `RecipeCardData`, construido desde un `FeedRecipe` (feed) o desde un `Recipe` (detalle) con dos funciones `toCardData()`. En el detalle, `expandLists = true` despliega por completo ingredientes y pasos, y `showCommentsPreview = false` sustituye la vista previa de comentarios por el enlace "Ver los N comentarios". Además, el detalle añade a cada ingrediente la nota de su cálculo nutricional ("sin datos nutricionales", "de Open Food Facts"). Así, lo que se ve en el feed y al abrir la receta es visualmente lo mismo, y un cambio de diseño solo se hace una vez.

### Coherencia entre pantallas

Un comentario puede publicarse desde tres sitios (la tarjeta del feed, el detalle y la pantalla de comentarios) y borrarse desde uno. Siguiendo el mismo criterio que los likes en el informe 12, los ViewModels son independientes y `HomeScreen` copia los cambios:

- Al publicar desde la tarjeta o desde el detalle, el comentario nuevo pasa a ser el primero de la vista previa del feed y el contador sube uno (`onCommentPosted`).
- Al salir de la pantalla de comentarios, el feed y el detalle toman de ella el total y los dos últimos (`syncComments`, `syncCommentsCount`). Es la fuente más fiable, porque ahí se han podido borrar comentarios.
- `syncRecipe`, que ya copiaba al feed los likes y la foto del detalle, **no** toca los comentarios, para no contar dos veces el mismo comentario nuevo.

Si al comentar la receta ya no existe (404, borrada desde otro dispositivo), se quita del feed con un aviso, igual que con los likes. Si falla por otro motivo, el texto se queda en el campo para reintentar.

## Cómo funciona

### Backend

- `GET /recipes/:id/comments?cursor=&limit=20` → `{ comments: [{ id, text, createdAt, authorId, authorName }], total, nextCursor }`. Límite por defecto 20, máximo 50.
- `POST /recipes/:id/comments` con `{ text }` → 201 con el comentario creado. 400 si el texto está vacío o pasa de 500 caracteres; 404 si la receta no existe.
- `DELETE /comments/:id` → `{ deleted: true }`. 403 si no es del usuario; 404 si no existe.
- `GET /recipes/feed` añade a cada receta `description`, `proteinPerServing`, `ingredients` (nombre, cantidad, unidad), `steps`, `commentsCount`, `commentsPreview` y `likersPreview`.
- El detalle y el resto de respuestas de receta añaden `description`, `commentsCount` y `likersPreview`.

La lógica que no depende de la base de datos (validación del texto, formato de las respuestas, fragmentos de consulta y conversión de las vistas previas) está en `src/social/comments.js`, separada de las rutas como ya se hizo con `recipeSocial.js`.

### Aplicación Android

**La tarjeta** (`RecipeFeedCard.kt`) se compone, de arriba abajo, de:

1. **Cabecera**: avatar con iniciales, nombre del autor y tiempo relativo ("hace 3 horas"). La aplicación no tiene fotos de perfil, así que el avatar (`Avatar.kt`) es un círculo con las iniciales sobre un color de la paleta elegido a partir del nombre: la misma persona tiene siempre el mismo color en el feed, en los comentarios y en cualquier móvil. El tiempo relativo (`formatRelativeTime`) usa la unidad entera mayor, de "ahora mismo" a "hace N años".
2. **Título y descripción** (si la hay).
3. **Barra de estadísticas**: tiempo, kcal por ración, raciones y proteína por ración, cada una con su icono y separadas por divisores verticales. Si la receta no tiene datos nutricionales, se muestra "—" en lugar de un 0 engañoso. Para un lector de pantalla, cada estadística se lee de una vez ("Kcal: 410").
4. **Carrusel** (`HorizontalPager`): foto (si la hay), ingredientes y pasos, con puntos indicadores debajo. Los ingredientes muestran los 5 primeros y "Ver N ingredientes más"; los pasos, los 3 primeros, numerados en círculos. Con foto, las páginas de texto miden como mínimo lo mismo que ella, para que la tarjeta no cambie de alto al deslizar.
5. **Acciones**: el corazón animado del informe 14 con su contador, el globo de comentarios con el suyo y "Compartir", que abre el selector del sistema con el título de la receta (no hay enlaces web a recetas concretas).
6. **"Le gusta a..."**: la frase se construye en `likersText`, una función pura con pruebas unitarias. Distingue si el usuario ha dado su like ("Te gusta a ti y a 3 personas más") y descarta su propio nombre de `likersPreview`, que puede ir por detrás de la actualización optimista del corazón.
7. **Últimos comentarios** (nombre en negrita y texto, como mucho dos líneas) y "Ver los N comentarios" si hay más.
8. **Campo de comentario** con el avatar propio y el botón de enviar, también desde el teclado.

**La pantalla de comentarios** (`CommentsScreen.kt`, `CommentsViewModel.kt`) lista los comentarios paginados con "Cargar más" y tiene el campo de escritura fijo abajo. Permite borrar los propios tras una confirmación. El `CommentsViewModel` tiene ámbito en esa pantalla: al salir, la lista se descarta, y el recuento y los dos últimos ya se han copiado al feed y al detalle.

**El formulario de receta** incluye un campo opcional de descripción.

### Pruebas

- Backend (`test/recipeSocial.test.js`): 5 pruebas nuevas, sobre el mismo cliente de Prisma en memoria del informe 12: validación del texto; paginación de comentarios del más reciente al más antiguo con el total; `POST` con validación y autor correcto; 403 al borrar un comentario ajeno; y `commentsCount`, `commentsPreview` y `likersPreview` en el feed y en el detalle.
- Android (`FeedCardTextTest.kt`): tiempo relativo en todas sus unidades, iniciales y color del avatar, todas las variantes de "Le gusta a..." y la etiqueta de los ingredientes del carrusel.

## Limitaciones

- **Sin moderación ni notificaciones.** El autor de una receta no puede borrar comentarios ajenos en ella, no hay denuncias y no se avisa al autor cuando alguien comenta. Para una aplicación pública, la moderación sería imprescindible (ya se señalaba para las fotos en el informe 12).
- **Sin respuestas ni menciones.** Los comentarios son una lista plana. No se puede responder a un comentario concreto ni mencionar a otro usuario.
- **Vistas previas con cierto retraso.** Los contadores y las vistas previas se sincronizan entre pantallas del mismo móvil, pero lo que publiquen otros usuarios solo aparece al refrescar el feed; no hay actualización en tiempo real.
- **Peso de las páginas del feed.** Traer ingredientes, pasos y vistas previas hace cada página algo más pesada. Sigue siendo poco comparado con las fotos en Base64, que son el problema de tamaño real (informe 12).
- **Compartir solo texto.** "Compartir" envía el título, no un enlace, porque la aplicación no tiene web ni enlaces profundos (*deep links*).

## Archivos creados o modificados

**Backend (`backend/`)**

| Archivo | Cambio |
|---|---|
| `prisma/schema.prisma`, `prisma/migrations/20260928180000_comments_and_description/` (nuevo) | Modelo `Comment` con índice `(recipeId, createdAt)` y `Recipe.description`. |
| `src/social/comments.js` (nuevo) | Validación del texto, formato de las respuestas, vista previa de comentarios y consulta de `likersPreview`. |
| `src/social/recipeSocial.js` (modificado) | `socialFields`/`socialSummary`: likes y recuento de comentarios. |
| `src/routes/recipes.js` (modificado) | `GET`/`POST /:id/comments`, descripción, feed con ingredientes, pasos y vistas previas, `likersPreview` en todas las respuestas. |
| `src/routes/comments.js` (nuevo), `src/index.js` (modificado) | `DELETE /comments/:id`. |
| `test/recipeSocial.test.js`, `test/security.test.js` (modificados) | 5 pruebas nuevas y ajuste de las existentes. |

**Aplicación Android (`app/src/`)**

| Archivo | Cambio |
|---|---|
| `main/java/.../ui/recipes/RecipeFeedCard.kt` (nuevo) | Tarjeta estilo Hevy compartida por el feed y el detalle. |
| `main/java/.../ui/comments/CommentsScreen.kt`, `CommentsViewModel.kt` (nuevos) | Lista paginada, publicar y borrar comentarios propios. |
| `main/java/.../data/CommentModels.kt`, `CommentRepository.kt` (nuevos) | Modelos y llamadas de comentarios. |
| `main/java/.../ui/Avatar.kt`, `ui/DateUtils.kt` (nuevo y modificado) | Avatar de iniciales y tiempo relativo. |
| `main/java/.../ui/home/FeedViewModel.kt`, `HomeTabScreen.kt`, `HomeScreen.kt` (modificados) | Borradores de comentario por tarjeta, sincronización de comentarios y ruta de la pantalla de comentarios. |
| `main/java/.../ui/recipes/RecipeDetailScreen.kt`, `RecipeViewModel.kt`, `RecipeFormScreen.kt` (modificados) | Detalle con la tarjeta desplegada, comentar desde el detalle y campo de descripción. |
| `main/java/.../data/RecipeModels.kt`, `ApiService.kt` (modificados) | Campos nuevos del feed y del detalle, y rutas de comentarios. |
| `main/res/drawable/ic_*.xml` (nuevos) | Iconos de la barra de estadísticas y de comentarios. |
| `test/.../FeedCardTextTest.kt` (nuevo) | Pruebas de los textos de la tarjeta. |
