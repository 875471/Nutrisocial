# 25. Compartir recetas con amigos

## Objetivo y problema

El botón de compartir de las tarjetas de receta abría el selector del sistema (`Intent.ACTION_SEND`) con un texto plano: «Mira esta receta en NutriSocial: …». Como no hay enlaces a recetas concretas (el enlace público del informe 23 se revirtió), quien lo recibía por otra aplicación solo veía un título, sin forma de abrir la receta.

El objetivo es compartir una receta dentro de la propia aplicación, con alguien a quien se sigue, de forma que le llegue un aviso que al tocarlo abra la receta. Se descartó un chat o mensajería en tiempo real: el sistema de notificaciones del informe 21 ya cubre el caso con poco código nuevo.

## Decisiones técnicas

**Una notificación más, no un modelo nuevo.** Compartir crea una notificación de tipo `share` en la tabla `Notification` que ya existía (`userId` destinatario, `actorId` quien comparte, `recipeId`). El campo `type` es texto, así que no hace falta migración. Se usa la función `notify()` de siempre, que nunca lanza: si falla la escritura, se registra en el log y la petición responde igualmente. También hereda su deduplicación: compartir otra vez la misma receta con la misma persona no repite el aviso.

**Solo con gente a la que se sigue.** El destinatario tiene que ser alguien a quien sigue quien comparte, según la tabla `Follow`. Así se evita que cualquiera pueda llenar de avisos a un desconocido, y la lista de la que elegir es corta y conocida. La comprobación se hace en el servidor, no solo en la interfaz.

**Validación del usuario compartida.** La función que validaba el id de otro usuario al seguirle (entero positivo, no uno mismo, que exista) pasó de `routes/users.js` a `social/otherUser.js`, con el mensaje de «uno mismo» como parámetro. La usan seguir (`/users/:id/follow`) y compartir (`/recipes/:id/share`), en lugar de duplicarla.

**La hoja de compartir, con su propio ViewModel.** El estado de la hoja (receta que se comparte, lista de seguidos, envío en curso, aviso final) vive en `ShareRecipeViewModel`. Cada pantalla con tarjetas (los feeds de Inicio, las guardadas y el detalle) obtiene uno con `viewModel()` y le pasa su `SnackbarHostState` para el aviso. La tarjeta solo recibe una acción `onShare`, sin estado propio, como el resto de sus acciones. Sin `onShare` no enseña el botón.

## Cómo funciona

`GET /users/following` devuelve `[{ id, name }]` de la gente a la que sigue el usuario, ordenada por nombre. `POST /recipes/:id/share` con `{ toUserId }` valida, por este orden: el destinatario (`400` si el id no es válido o es uno mismo, `404` si no existe), que se le sigue (`400` «Solo puedes compartir con alguien a quien sigues»), y la receta (`400` si el id no es válido, `404` si no existe). Si todo es correcto crea la notificación y responde `{ shared: true }`.

En la aplicación, el botón de compartir de la tarjeta abre una hoja inferior (`ModalBottomSheet`) que pide la lista de seguidos cada vez que se abre, porque se puede haber seguido a alguien desde otra pantalla. Cada persona tiene un botón «Compartir». Al pulsarlo se bloquean los demás mientras se envía; al terminar, la hoja se cierra y la pantalla enseña «Receta compartida con {nombre}» o el error. Si no se sigue a nadie, la hoja lo dice y ofrece un botón que lleva al buscador de personas.

Quien la recibe ve en Notificaciones «**Ana** te ha compartido su receta: **Pisto**». Como el aviso tiene receta y no es de tipo `follow`, al tocarlo se abre la receta, sin cambios en esa pantalla.

**Pruebas.** `test/recipeSocial.test.js` añade 4 pruebas sobre las rutas reales con Prisma en memoria: la lista de seguidos (vacía, por nombre y sin incluir lo que siguen otros); compartir con alguien a quien se sigue, que crea el aviso `share` con la receta y no lo repite si se comparte otra vez; que no se puede compartir con uno mismo ni con alguien a quien no se sigue, sin crear aviso; y los errores de destinatario (sin `toUserId`, no numérico, inexistente) y de receta inexistente.

## Limitaciones

- **Sin push.** El destinatario solo se entera al abrir la aplicación y mirar la campana, como con el resto de notificaciones.
- **Sin mensaje.** No se puede añadir un texto al compartir; el aviso dice solo quién y qué receta.
- **Compartir dos veces no avisa dos veces.** Por la deduplicación de `notify()`, si el destinatario ya había leído el primer aviso, volver a compartirle la misma receta no le vuelve a avisar.
- **Solo a gente a la que se sigue.** No se puede compartir con un seguidor al que no se sigue de vuelta, ni fuera de la aplicación: el texto plano por otras aplicaciones se ha quitado.
- **Sin pruebas de interfaz.** La hoja se ha compilado, pero no hay pruebas automáticas de Compose ni se ha probado en el emulador.

## Archivos creados o modificados

**Backend (`backend/`)**

| Archivo | Responsabilidad |
|---|---|
| `src/social/otherUser.js` (nuevo) | `findOtherUserId`, compartido por seguir y compartir. |
| `src/routes/users.js` (modificado) | `GET /users/following`; seguir usa `findOtherUserId`. |
| `src/routes/recipes.js` (modificado) | `POST /recipes/:id/share`. |
| `src/social/notifications.js` (modificado) | Tipo `share`. |
| `prisma/schema.prisma` (modificado) | Solo comentarios de `Notification` (sin migración). |
| `test/recipeSocial.test.js` (modificado) | 4 pruebas nuevas y la base simulada ampliada (`follow.findUnique`, seguidos con nombre). |

**Aplicación Android (`app/src/main/java/com/example/nutrisocial/`)**

| Archivo | Responsabilidad |
|---|---|
| `ui/recipes/ShareRecipeSheet.kt` (nuevo) | `ShareRecipeViewModel` y la hoja con la lista de seguidos. |
| `ui/recipes/RecipeFeedCard.kt` (modificado) | El botón de compartir llama a `onShare` en lugar de abrir `ACTION_SEND`. |
| `ui/home/HomeTabScreen.kt`, `ui/explore/ExploreContent.kt` (modificados) | `onShare` en las acciones del feed; la hoja en las listas de Inicio y Guardadas. |
| `ui/recipes/RecipeDetailScreen.kt`, `ui/home/HomeScreen.kt` (modificados) | La hoja en el detalle y el acceso al buscador de personas. |
| `ApiService.kt`, `data/UserRepository.kt`, `data/RecipeRepository.kt`, `data/RecipeModels.kt` (modificados) | `getFollowing`, `shareRecipe` y sus modelos. |
| `ui/notifications/NotificationsScreen.kt`, `data/NotificationModels.kt` (modificados) | Texto y documentación del tipo `share`. |
