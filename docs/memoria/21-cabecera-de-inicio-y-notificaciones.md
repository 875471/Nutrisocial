# 21. Cabecera de Inicio y notificaciones

## Objetivo y problema

Tras el informe 19, la parte de arriba de Inicio tenía tres piezas apiladas:

1. Un saludo ("Hola, Ana").
2. Un subtítulo ("Lo último que ha cocinado la comunidad").
3. Una fila de pestañas "Explorar" / "Amigos".

Debajo, en "Explorar", venían el campo de búsqueda y los filtros de orden. Antes de ver la primera receta había cuatro bandas de interfaz, y la primera no aportaba nada: el saludo repite un nombre que el usuario ya conoce y el subtítulo describe algo que la pantalla ya enseña.

Además, la aplicación no tenía forma de avisar de lo que otros usuarios hacen con lo tuyo. Con el sistema de seguir usuarios (informe 19) y los "me gusta" y comentarios (informes 12 y 15), NutriSocial ya genera interacciones entre personas. Sin embargo, el autor de una receta solo se enteraba de que le habían comentado si volvía a abrirla y miraba el contador.

Esta iteración cambia la cabecera de Inicio y añade notificaciones dentro de la aplicación:

- **Cabecera:** un título desplegable para elegir el segmento y, a la derecha, dos iconos (lupa y campana). El campo de búsqueda de "Explorar" deja de estar siempre a la vista y se despliega con la lupa.
- **Notificaciones:** avisos de nuevo seguidor, de "me gusta" y de comentario en una receta propia.

## Decisiones técnicas

**Un desplegable en lugar del saludo y la fila de pestañas.** Es el patrón de Hevy (y de Instagram con "Siguiendo"/"Favoritos"): el título de la barra superior es el nombre del feed que se está viendo, con una flecha que abre un menú con la otra opción. Ocupa una sola línea, que además comparte con los iconos, y deja claro en todo momento qué se está viendo. Es solo otra forma de elegir el segmento: el estado sigue siendo el mismo `selectedTab`/`onTabSelected` del informe 19, guardado en `HomeScreen`, y el contenido de cada segmento no ha cambiado. El buscador, sus filtros de orden y los resultados de "Explorar" siguen exactamente igual.

**El buscador se despliega con la lupa.** Con el campo de búsqueda siempre visible, "Explorar" empezaba con dos bandas (campo y filtros de orden) antes de la primera receta, y la mayoría de las veces se entra a mirar, no a buscar. Ahora el campo está plegado. La lupa lo despliega justo debajo de la cabecera con una animación (`AnimatedVisibility` que crece en vertical y aparece con un fundido), le da el foco y abre el teclado. Al volver a pulsarla, o con la × del propio campo, se pliega del todo, sin dejar hueco, y se borra lo escrito, de modo que vuelve el listado completo con el orden que hubiera elegido. Se decidió borrar el texto al plegar porque, si no, el campo quedaría oculto con una búsqueda activa y el usuario vería resultados filtrados sin saber por qué. Los `FilterChip` de orden siguen siempre visibles, esté desplegado o no. No se ha creado un segundo buscador: es el mismo campo de antes, que ahora aparece y desaparece. El estado `searchExpanded` vive en `HomeScreen` (con `rememberSaveable`), junto al segmento elegido, para que siga desplegado al volver del detalle de una receta. La lupa solo aparece en "Explorar"; en "Amigos" no hay nada que buscar.

**Notificaciones guardadas en la base de datos, sin push.** Cada aviso es una fila de `Notification` con el destinatario (`userId`), quién lo ha provocado (`actorId`), el tipo (`follow`, `like` o `comment`), la receta si la hay, si se ha leído y la fecha. La aplicación las consulta al entrar en Inicio y al abrir la campana. No hay notificaciones push: exigirían Firebase Cloud Messaging, guardar el token de cada dispositivo y un servicio que envíe los avisos, y Render en su plan gratuito se duerme cuando no hay tráfico. Para un TFG, ver los avisos al abrir la aplicación cubre el caso de uso sin esa infraestructura. La tabla ya tiene lo necesario para añadir push más adelante.

**Crear la notificación nunca rompe la acción que la provoca.** Se sigue el mismo criterio que con los correos de Brevo (informe 14). `notify` y `notifyRecipeAuthor` (en `social/notifications.js`) capturan cualquier error, lo escriben en el log y vuelven sin lanzar. Si falla la inserción, el "me gusta", el comentario o el seguimiento se guardan igualmente y la respuesta es la misma. Hay una prueba que simula la base de datos caída solo para las notificaciones y comprueba las tres respuestas. Las rutas esperan a que termine la notificación antes de responder (son una o dos consultas más). Así el orden es predecible y las pruebas no dependen de tiempos.

**No se notifica a uno mismo ni se repiten avisos.** Dar "me gusta" o comentar la propia receta no genera nada. "Seguir" y "me gusta" se pueden quitar y volver a dar. Si cada vez creara un aviso, un usuario indeciso llenaría la lista de otro con avisos idénticos. Por eso, en esos dos tipos, antes de crear se comprueba si ya existe una notificación igual (mismo destinatario, actor, tipo y receta). Los comentarios sí generan un aviso cada uno, porque cada comentario es un contenido distinto.

**Las relaciones borran en cascada.** Si se borra la receta, la cuenta del destinatario o la del actor, sus notificaciones desaparecen (`onDelete: Cascade` en las tres relaciones). Así no quedan avisos de "X ha comentado en…" que lleven a una receta que ya no existe. Como `Notification` tiene dos relaciones con `User`, Prisma exige nombrarlas: "NotificationRecipient" (las que recibe) y "NotificationActor" (las que ha provocado).

**El recuento de no leídas va en la primera página.** `GET /notifications` devuelve `unreadCount` solo cuando no hay cursor. Es lo que necesitan la campana (se pide una página de un elemento) y la pantalla (al cargar la primera página), y así las páginas siguientes no repiten la consulta. El cursor se valida contra el propio usuario, de modo que el id de una notificación ajena no sirve para paginar.

**Leídas al abrir la pantalla, pero se ven como nuevas esa vez.** Al abrir la pantalla de notificaciones se carga la primera página y después se llama a `POST /notifications/read-all`, que apaga el punto rojo. La lista se queda con el estado de lectura con que llegó, así que en esa visita las nuevas se distinguen con un fondo tenue y un punto. En la siguiente ya aparecen como leídas.

**La migración.** Se generó con `prisma migrate diff` contra la base de datos y se aplicó con `prisma migrate deploy`, como las anteriores. `prisma migrate dev` no se usa porque `.env` apunta a la base de datos de producción y ese comando puede ofrecer reiniciarla.

## Cómo funciona

### Backend

La migración `20260929230000_notifications` crea la tabla `Notification`, con un índice por (`userId`, `createdAt`) para listar las de un usuario por fecha.

Tres rutas existentes crean notificaciones después de guardar la acción:

- `POST /users/:id/follow` avisa al usuario seguido.
- `POST /recipes/:id/like` avisa al autor de la receta.
- `POST /recipes/:id/comments` avisa al autor de la receta.

Para "like" y comentario, `notifyRecipeAuthor` busca primero el autor de la receta. `notify` descarta los avisos a uno mismo y los duplicados de "follow" y "like" antes de insertar.

`routes/notifications.js` se monta en `/notifications` y exige sesión:

- **`GET /notifications?cursor=&limit=20`:** devuelve `{ notifications, nextCursor }` y, en la primera página, `unreadCount`. Cada notificación es `{ id, type, actorId, actorName, recipeId, recipeTitle, read, createdAt }`. El orden es de la más reciente a la más antigua, con el id como desempate, igual que el feed.
- **`POST /notifications/read-all`:** marca como leídas todas las del usuario con un `updateMany` y devuelve cuántas ha cambiado.

### Aplicación Android

**Cabecera.** `HomeTabScreen` tiene ahora una `TopAppBar`. El título es `FeedTabSelector`: el nombre del segmento activo en negrita con una flecha hacia abajo. Al pulsarlo se abre un `DropdownMenu` con "Explorar" y "Amigos", con una marca en el activo. Elegir uno cierra el menú y llama a `onTabSelected`. A la derecha, la lupa (solo en "Explorar") y la campana. La campana va en un `BadgedBox` que enseña un punto rojo si hay notificaciones sin leer, y su descripción para lectores de pantalla incluye cuántas son. El saludo y el subtítulo desaparecen. El botón "Nueva receta" sigue donde estaba.

**Lupa.** Es un `IconToggleButton`, que se ve marcado mientras el buscador está desplegado. `ExploreContent` recibe `searchExpanded` y `onCloseSearch`. Al desplegarse, espera un fotograma a que el campo esté en pantalla, le da el foco con un `FocusRequester` propio y muestra el teclado. Al plegarse, `RecipeSearchViewModel.clearQuery()` borra el texto y repite la búsqueda sin esperar al retardo de escritura.

**Campana y pantalla.** `NotificationsViewModel` tiene el ámbito de `HomeScreen`, como el resto de ViewModels de las pestañas. Al entrar en Inicio pide solo el recuento (`refreshUnreadCount`, una página de un elemento); si falla, el punto se queda como estaba y no se enseña ningún error. La campana abre `NotificationsScreen` (ruta `notificaciones`), que sigue el patrón de los comentarios: tirar para refrescar, "Cargar más" al final y mensajes centrados para la carga, el error y la lista vacía. Cada fila tiene el avatar de iniciales de quien la provocó, el texto según el tipo y el tiempo relativo:

- "**Lucía** ha empezado a seguirte"
- "**Lucía** ha dado me gusta a **Crema de calabaza**"
- "**Lucía** ha comentado en **Crema de calabaza**"

Las de "me gusta" y comentario abren el detalle de la receta. Las de seguidor no se pueden pulsar.

### Pruebas

En el backend hay 123 pruebas y pasan todas. Las nuevas comprueban:

- Que seguir, dar "me gusta" y comentar crean la notificación correcta para el otro usuario, con nombre y receta.
- Que interactuar con lo propio no crea nada.
- Que quitar y volver a dar el "me gusta" o el seguimiento no repite el aviso, mientras que dos comentarios crean dos.
- La paginación, que `unreadCount` sea correcto y solo aparezca en la primera página, y que el cursor de otro usuario se rechace.
- Que `read-all` las marca todas.
- Que un fallo al crear la notificación no rompe ninguna de las tres acciones.

Además se probó con un servidor local contra la base de datos real y dos cuentas de demostración: seguir, "me gusta" y comentario, lista con `unreadCount` 3, `read-all` y recuento a 0. Después se deshizo todo, incluidas las notificaciones de prueba.

## Limitaciones

- **Sin notificaciones push.** Los avisos solo se ven al abrir la aplicación: el punto rojo se actualiza al entrar en Inicio y la lista al abrir la campana. Con la aplicación cerrada no llega nada.
- **Un aviso de seguidor no lleva a ningún sitio.** No existe todavía una pantalla de perfil público de otro usuario, así que tocar "X ha empezado a seguirte" no hace nada. Cuando exista esa pantalla, bastará con navegar a ella con `actorId`.
- **Sin agrupar.** Veinte "me gusta" a la misma receta son veinte filas, no "A, B y 18 personas más han dado me gusta a…".
- **El punto no se actualiza solo.** Si llega una notificación mientras el usuario está en Inicio, el punto rojo no aparece hasta que vuelve a entrar en la pestaña. No hay sondeo periódico ni conexión en tiempo real.
- **Quitar el "me gusta" no borra el aviso.** Si alguien da "me gusta" y lo quita enseguida, el autor sigue viendo la notificación. Tampoco se borra la de un comentario cuando su autor lo elimina.
- **"Leídas" es todo o nada.** Abrir la pantalla las marca todas como leídas, aunque la lista sea larga y el usuario no haya bajado hasta el final. No se pueden marcar de una en una ni borrar.
- **Sin prueba en el dispositivo contra producción.** La interfaz se ha compilado y la API se ha probado contra un servidor local. Las rutas nuevas no estarán en Render hasta el siguiente despliegue.

## Archivos creados o modificados

| Archivo | Cambio |
| --- | --- |
| `backend/prisma/schema.prisma` (modificado) | Modelo `Notification` y sus relaciones en `User` y `Recipe`. |
| `backend/prisma/migrations/20260929230000_notifications/migration.sql` (nuevo) | Tabla `Notification` con su índice y claves ajenas. |
| `backend/src/social/notifications.js` (nuevo) | `notify`, `notifyRecipeAuthor` y el formato de respuesta. |
| `backend/src/routes/notifications.js` (nuevo) | `GET /notifications` y `POST /notifications/read-all`. |
| `backend/src/routes/users.js`, `recipes.js` (modificados) | Notificación al seguir, al dar "me gusta" y al comentar. |
| `backend/src/index.js` (modificado) | Monta `/notifications`. |
| `backend/test/recipeSocial.test.js` (modificado) | Doble de Prisma con `notification` y cuatro pruebas nuevas. |
| `app/.../data/NotificationModels.kt`, `NotificationRepository.kt` (nuevos) | Modelos y llamadas de notificaciones. |
| `app/.../ApiService.kt` (modificado) | Las dos rutas nuevas. |
| `app/.../ui/notifications/NotificationsViewModel.kt`, `NotificationsScreen.kt` (nuevos) | Recuento, lista paginada y pantalla. |
| `app/.../ui/home/HomeTabScreen.kt` (modificado) | Cabecera con desplegable, lupa y campana; sin saludo ni fila de pestañas. |
| `app/.../ui/explore/ExploreContent.kt` (modificado) | Campo de búsqueda desplegable (`SearchField` con `AnimatedVisibility`, foco automático y × para cerrar). |
| `app/.../ui/explore/RecipeSearchViewModel.kt` (modificado) | `clearQuery()` al plegar el buscador. |
| `app/.../ui/home/HomeScreen.kt` (modificado) | Ruta `notificaciones`, recuento al entrar en Inicio y estado `searchExpanded`. |
