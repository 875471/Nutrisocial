# 19. Seguir usuarios y reorganización de la navegación

## Objetivo y problema

La propuesta del TFG describe el inicio de la aplicación como un feed con "publicaciones de usuarios seguidos o modo exploración". Hasta ahora solo existía la segunda mitad. El inicio enseñaba las recetas de toda la comunidad y no había forma de seguir a nadie, así que el usuario no podía quedarse solo con lo que publica la gente que le interesa. Una red social de recetas en la que no se puede seguir a nadie se parece más a un recetario público que a una red social.

La navegación también tenía un problema de organización. La pestaña "Recetas" (informe 16) mezclaba dos tipos de contenido en tres segmentos:

- **Listas personales**: "Mis recetas" (las propias) y "Guardadas" (las que el usuario se ha apartado).
- **Descubrimiento**: "Explorar", el buscador sobre las recetas de todos.

A la vez, "Inicio" tenía un feed global que, con la búsqueda vacía, enseñaba casi lo mismo que "Explorar". Había dos sitios para descubrir recetas y ninguno para ver lo que hace uno mismo junto a sus datos.

Esta iteración añade el sistema de seguir usuarios y recoloca las pantallas según a quién pertenece el contenido:

- **Inicio**: lo que publican los demás, con dos segmentos, "Explorar" y "Amigos".
- **Perfil**: lo que es del usuario, sus recetas y las que ha guardado.

## Decisiones técnicas

**Seguimiento simple, como una cuenta pública de Instagram.** Cualquier usuario puede seguir a cualquier otro sin pedir permiso. No hay solicitudes de seguimiento ni cuentas privadas, y el seguido no tiene que aceptar nada. Tampoco hay relación de amistad recíproca: si A sigue a B, B no sigue a A a menos que lo haga él. Para NutriSocial basta, porque todo lo que se publica (recetas, comentarios y "me gusta") ya es público para cualquier usuario registrado y el feed global lo enseña a todo el mundo. Una cuenta privada solo tendría sentido si además se pudiera ocultar el contenido, y eso cambiaría todas las consultas del feed, del buscador y de la despensa. Seguir a alguien no da acceso a nada nuevo: solo sirve para filtrar.

**Una tabla de relación con índice único.** El modelo `Follow` guarda el par (`followerId`, `followingId`) con su fecha. Los dos extremos apuntan a `User` con `onDelete: Cascade`, así que al borrar una cuenta desaparecen tanto sus seguimientos como sus seguidores. Como `User` tiene dos relaciones con el mismo modelo, Prisma exige nombrarlas: "Following" (a quién sigue) y "Followers" (quién le sigue). El índice único `@@unique([followerId, followingId])` impide los duplicados en la propia base de datos, no solo en la aplicación. Se ha añadido además un índice sobre `followingId`, porque `isFollowedByMe` se calcula en cada tarjeta buscando por el autor.

**Operaciones idempotentes, como los "me gusta".** `POST /users/:id/follow` usa `upsert` y `DELETE /users/:id/follow` usa `deleteMany`. Seguir dos veces a la misma persona o dejar de seguir a alguien a quien no se sigue no es un error, y las dos rutas devuelven el estado final (`{ isFollowedByMe }`). Así la aplicación puede reintentar sin miedo y el cambio optimista del botón se confirma con lo que diga el servidor. Seguirse a uno mismo se rechaza con 400 y un usuario inexistente da 404.

**`isFollowedByMe` en la misma consulta que la receta.** Las tarjetas del feed, del buscador y de las guardadas comparten `feedSelect`. Antes cargaban solo el nombre del autor. Ahora cargan también, como mucho, el seguimiento de quien pregunta a ese autor (`authorFields(userId)` en `social/recipeSocial.js`, con el mismo patrón `where` + `take: 1` que `likedByMe` y `savedByMe`). `socialSummary` lo traduce a un booleano. No hace falta ninguna consulta más por tarjeta, y el detalle de receta también lo recibe porque `recipeInclude` usa la misma función.

**El feed de amigos es el feed con un filtro.** `GET /recipes/feed/friends` lee primero los ids de los usuarios seguidos y reutiliza la paginación del feed (ahora en una función `feedPage` que comparten las dos rutas) con `where: { authorId: { in: ids } }`. Si el usuario no sigue a nadie, devuelve directamente `{ recipes: [], nextCursor: null }`, sin consultar recetas. Es un estado normal, no un error: la aplicación lo aprovecha para invitar a buscar personas. Las recetas propias no aparecen, porque nadie se sigue a sí mismo.

**Buscador de personas con la misma normalización que los alimentos.** `GET /users/search?q=` compara los nombres con `normalize` de `nutrition/normalize.js`, el mismo que usan `matchFood` y el buscador de recetas. Así "alvaro" encuentra a "Álvaro Núñez" y "NUÑEZ" también. Cada palabra de la búsqueda tiene que aparecer en el nombre como subcadena, de modo que "mar" encuentra a "Marta" y a "Omar". El filtro se hace en memoria, igual que en el buscador de recetas y por el mismo motivo: PostgreSQL no quita las tildes sin la extensión `unaccent`. La función pura `searchUsers` (en `social/userSearch.js`) devuelve los ids de la página y la ruta carga solo esos usuarios con su número de recetas y si ya se les sigue. Sin texto se devuelven todos por orden alfabético, para poder descubrir gente sin saber su nombre. El resultado nunca incluye a quien busca.

**Explorar y Amigos en Inicio; Mis recetas y Guardadas en Perfil.** La propuesta pide exactamente esas dos formas de ver el feed, y el lugar natural es la primera pestaña de la aplicación. "Explorar" sin texto ya enseñaba todas las recetas de la más reciente a la más antigua (informe 16), así que hace de feed global y el feed antiguo de Inicio sobra: no se ha duplicado nada. "Mis recetas" y "Guardadas" son listas personales, igual que en Instagram las publicaciones propias y las guardadas se ven desde el perfil, así que pasan a la pestaña Perfil. La barra inferior se queda con cuatro pestañas (Inicio, Diario, Despensa y Perfil).

**Los datos personales, en una pantalla aparte.** El perfil era un formulario largo y desplazable (datos, objetivo calórico, cerrar sesión, eliminar cuenta). Meter dentro dos listas de tarjetas con paginación y tirar para refrescar habría obligado a anidar listas desplazables. En su lugar, la pestaña Perfil tiene una cabecera compacta (avatar, nombre, objetivo calórico) con un botón "Mis datos" que abre el formulario de siempre como pantalla propia, con flecha para volver. El formulario no ha cambiado; solo su título ("Mis datos") y la forma de llegar a él. El aviso del diario "completa tu perfil" lleva ahora directamente a esa pantalla.

**El segmento elegido vive en `HomeScreen`.** Antes el segmento de la pestaña Recetas se guardaba con `rememberSaveable` dentro de su destino de navegación. Ahora los segmentos de Inicio y de Perfil se guardan un nivel más arriba, en `HomeScreen`. Así, al crear o borrar una receta, la aplicación puede llevar al usuario a "Mis recetas" aunque estuviera en "Guardadas". Eso lo hace `showMyRecipes()`, que sustituye a las dos llamadas repetidas que antes volvían a `HomeRoutes.RECETAS`.

## Cómo funciona

### Backend

La migración `20260929200000_follows` crea la tabla `Follow` con sus dos claves ajenas, el índice único y el índice por `followingId`. Se generó con `prisma migrate diff` contra la base de datos y se aplicó con `prisma migrate deploy`, como las anteriores.

`routes/users.js` se monta en `/users` y exige sesión en todas sus rutas. Para seguir o dejar de seguir, `findOtherUserId` comprueba en este orden que el id sea un entero positivo, que no sea el del propio usuario y que exista. Si todo es correcto, se hace el `upsert` o el `deleteMany`. El buscador valida `q` (texto de hasta 100 caracteres) y `limit` (20 por defecto, 50 como máximo), carga el id y el nombre de todos los usuarios, filtra y ordena con `searchUsers` y completa la página con `_count.recipes` y el seguimiento del usuario. Cada resultado es `{ id, name, isFollowedByMe, recipesCount }`.

En `routes/recipes.js`, `GET /recipes/feed/friends` lee los seguidos y, si hay alguno, llama a `feedPage` con el filtro por autor. Esa función pide una receta de más para saber si hay página siguiente, igual que el feed. Todas las tarjetas (feed, amigos, buscador y guardadas) y el detalle llevan ahora `isFollowedByMe`.

### Aplicación Android

**Inicio.** `HomeTabScreen` muestra el saludo, un `PrimaryTabRow` con "Explorar" y "Amigos" y, debajo, el contenido del segmento, que pone `HomeScreen` con su propio ViewModel. Es el mismo patrón que ya usaba la pestaña Recetas. El botón "Nueva receta" sigue en Inicio.

- **Explorar** es el `ExploreContent` del informe 16, trasladado sin cambios de comportamiento con su `RecipeSearchViewModel`.
- **Amigos** usa `FriendsFeedViewModel`, una subclase de `FeedViewModel` que solo cambia de dónde salen las páginas (`GET /recipes/feed/friends`). Por eso tiene gratis la paginación por cursor, tirar para refrescar, los "me gusta", los guardados y los comentarios desde la tarjeta. Se recarga cada vez que se abre el segmento, porque el usuario puede haber empezado a seguir a alguien desde otro sitio. Si no hay recetas, un mensaje centrado explica qué se verá ahí y ofrece el botón "Buscar personas". Si las hay, el mismo enlace queda sobre la primera tarjeta para seguir a más gente.

**Buscador de personas.** Es una pantalla propia (`PeopleSearchScreen`, ruta `personas`) con flecha para volver. Tiene un campo de búsqueda que espera 350 ms tras la última tecla, como el de recetas, y una fila por persona: avatar de iniciales, nombre, número de recetas ("Sin recetas todavía", "1 receta", "4 recetas") y el botón "Seguir"/"Siguiendo". Al cambiar la búsqueda se siguen viendo los resultados anteriores con una barra de progreso fina.

**Botón en la tarjeta.** La cabecera de `RecipeFeedCard` enseña, a la derecha del nombre del autor, un botón pequeño `FollowButton`. Es relleno ("Seguir") cuando no se sigue al autor y con borde ("Siguiendo") cuando sí, para que la acción pendiente llame la atención y el estado ya conseguido quede discreto. No aparece en las recetas propias: `feedCardItems` recibe el id del usuario y deja `onToggleFollow` a `null` en ellas. El mismo componente lo usa el buscador de personas.

**Cambio optimista y sincronización.** `FeedViewModel.toggleFollow(authorId)` cambia al momento `isFollowedByMe` en todas las tarjetas de ese autor de la lista, no solo en la pulsada. Luego llama al servidor y deshace el cambio con un aviso si falla, con el mismo mecanismo de peticiones pendientes que el "me gusta". El estado confirmado se copia a las otras listas (Explorar, Amigos y Guardadas) con `applyFollow`, a través del callback `onFollowChanged` de `HomeScreen`. El buscador de personas usa el mismo callback, de modo que al seguir a alguien desde allí sus tarjetas de "Explorar" ya dicen "Siguiendo" al volver.

**Perfil.** `RecipeListScreen` es ahora la pestaña Perfil: título "Perfil", la cabecera `ProfileHeader` y dos segmentos, "Mis recetas" (la lista de siempre) y "Guardadas" (`SavedRecipesContent` con su `SavedRecipesViewModel`, sin cambios). "Nueva receta" y "Escanear" solo se enseñan en "Mis recetas". Borrar una receta desde el detalle o guardar una nueva lleva a "Mis recetas" con `showMyRecipes()`, que vuelve a la pestaña Perfil si ya estaba en la pila o navega a ella desde la pestaña en que se estuviera.

### Pruebas

En el backend hay 115 pruebas y pasan todas. Las nuevas cubren:

- Seguir y dejar de seguir, con idempotencia.
- Que no se puede seguir a uno mismo.
- Ids no válidos o de usuarios inexistentes.
- Que el feed de amigos solo trae recetas de los seguidos, paginado, y vacío si no se sigue a nadie.
- Que `isFollowedByMe` depende de quién pregunta, en el feed y en el detalle.
- El buscador de personas: excluye a quien busca, no distingue tildes ni mayúsculas y calcula bien `isFollowedByMe` y `recipesCount`.
- Las funciones puras de `userSearch.js`, en `test/userSearch.test.js`.

Las pruebas de rutas usan el cliente de Prisma en memoria de `recipeSocial.test.js`, ampliado con `follow` y `user`. Como ese doble no valida la sintaxis real de Prisma, se hizo además una prueba manual con un servidor local contra la base de datos y una cuenta de demostración, deshaciendo después el seguimiento: seguir, feed de amigos, `isFollowedByMe` en feed y detalle, seguirse a uno mismo, dejar de seguir y buscador.

## Limitaciones

- **Sin notificaciones.** Ganar un seguidor no avisa de nada. La aplicación no tiene todavía ningún sistema de notificaciones (tampoco para los "me gusta" o los comentarios).
- **Sin contadores ni listas de seguidores.** El perfil no enseña cuántos seguidores tiene el usuario ni a cuántos sigue, y no hay pantalla para ver esas listas. Los datos están en la tabla `Follow`: sería contar filas y añadir dos rutas.
- **No hay perfil de otros usuarios.** Pulsar el nombre de un autor no abre su perfil con sus recetas. Solo se le puede seguir desde una de sus tarjetas o desde el buscador de personas.
- **Seguir desde el detalle.** El detalle de receta recibe `isFollowedByMe`, pero su tarjeta no enseña el botón, que de momento solo está en las listas.
- **Sin bloqueo ni privacidad.** No se puede impedir que alguien te siga ni ocultar tus recetas. Es coherente con que todo el contenido sea público, pero haría falta si el contenido dejara de serlo.
- **Búsqueda por nombre visible.** No hay nombre de usuario único. Dos personas con el mismo nombre solo se distinguen por el número de recetas. Además, el buscador carga los nombres de todos los usuarios en memoria en cada búsqueda: con los usuarios de un TFG es instantáneo, pero con muchos miles habría que buscar en la base de datos (con `unaccent` o una columna con el nombre ya normalizado).
- **La lista de amigos no se poda al momento.** Al dejar de seguir a alguien desde "Amigos", sus recetas siguen en la lista hasta refrescar, igual que una receta que se deja de guardar en "Guardadas". Se hace así para poder deshacer un toque accidental sin que la tarjeta desaparezca.
- **Recarga en cada visita.** "Amigos" (como "Guardadas") se recarga cada vez que se abre, también al volver del detalle de una receta. Es sencillo y siempre está al día, a cambio de una petición más.
- **Sin prueba en el dispositivo contra producción.** La interfaz se ha compilado y la API se ha probado contra un servidor local, pero las rutas nuevas no están en el servidor de Render hasta el siguiente despliegue.

## Archivos creados o modificados

| Archivo | Cambio |
| --- | --- |
| `backend/prisma/schema.prisma` (modificado) | Modelo `Follow` y relaciones `following`/`followers` en `User`. |
| `backend/prisma/migrations/20260929200000_follows/migration.sql` (nuevo) | Tabla `Follow` con índice único e índice por `followingId`. |
| `backend/src/routes/users.js` (nuevo) | Seguir, dejar de seguir y buscador de personas. |
| `backend/src/social/userSearch.js` (nuevo) | Validación, normalización y filtrado del buscador de personas. |
| `backend/src/social/recipeSocial.js` (modificado) | `authorFields` e `isFollowedByMe` en `socialSummary`. |
| `backend/src/routes/recipes.js` (modificado) | `GET /recipes/feed/friends`, `feedPage` compartida y seguimiento en las tarjetas y el detalle. |
| `backend/src/index.js` (modificado) | Monta `/users`. |
| `backend/test/recipeSocial.test.js` (modificado) | Doble de Prisma con `follow` y `user`; pruebas de seguir, feed de amigos, `isFollowedByMe` y buscador. |
| `backend/test/userSearch.test.js` (nuevo) | Pruebas de las funciones puras del buscador de personas. |
| `app/.../data/UserModels.kt`, `UserRepository.kt` (nuevos) | `UserSummary`, `FollowState` y llamadas de seguir y buscar. |
| `app/.../data/RecipeModels.kt`, `FeedRepository.kt`, `ApiService.kt` (modificados) | `isFollowedByMe` y las rutas nuevas. |
| `app/.../ui/home/FeedViewModel.kt` (modificado) | `toggleFollow` optimista y `applyFollow`. |
| `app/.../ui/explore/FriendsFeedViewModel.kt` (nuevo) | Páginas del feed de amigos. |
| `app/.../ui/explore/ExploreContent.kt` (modificado) | `FriendsFeedContent` con su estado vacío, y el id del usuario para el botón de seguir. |
| `app/.../ui/people/PeopleSearchScreen.kt`, `PeopleSearchViewModel.kt` (nuevos) | Buscador de personas. |
| `app/.../ui/recipes/RecipeFeedCard.kt` (modificado) | `FollowButton` en la cabecera de la tarjeta. |
| `app/.../ui/home/HomeTabScreen.kt` (modificado) | Inicio con los segmentos "Explorar" y "Amigos". |
| `app/.../ui/recipes/RecipeListScreen.kt` (modificado) | Pestaña Perfil con cabecera y los segmentos "Mis recetas" y "Guardadas". |
| `app/.../ui/home/ProfileScreen.kt` (modificado) | `ProfileHeader`; el formulario pasa a ser la pantalla "Mis datos". |
| `app/.../ui/home/HomeScreen.kt` (modificado) | Sin la pestaña Recetas; rutas `perfil/datos` y `personas`; segmentos en `HomeScreen` y `showMyRecipes()`. |
| `app/src/main/res/drawable/ic_person_add.xml` (nuevo) | Icono "person_add" de Material Icons. |
