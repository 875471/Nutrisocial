# 38. Moderación mínima de contenido social

## Objetivo y problema

La capa social (feed, comentarios, recetas compartidas) dejaba publicar a cualquiera, pero no había ninguna forma de retirar contenido salvo borrarlo: solo el autor podía borrar su receta o su comentario, y ni siquiera la autora de una receta podía quitar un comentario ofensivo o de spam que otra persona hubiera dejado en ella. El objetivo es una moderación **mínima**: poder ocultar una receta o un comentario sin borrarlo, y que lo oculto deje de salir en las consultas públicas. No se busca un panel de administración ni un sistema de denuncias.

## Decisiones técnicas

**Ocultar, no borrar.** Recetas y comentarios tienen una columna nueva `hidden` (booleana, `false` por defecto). Ocultar no destruye nada: los «me gusta», los comentarios de una receta oculta y las entradas del diario que la usan se conservan, y quien la ocultó puede volver a mostrarla. Un borrado ya existía (`DELETE /recipes/:id`, `DELETE /comments/:id`) y es irreversible; la moderación cubre el caso en el que se quiere retirar algo de la vista sin perderlo, o en el que quien modera no es el autor y no debe poder destruir el contenido de otra persona.

**Quién puede moderar.** Las reglas están en un único módulo, `src/social/moderation.js`, para que todas las rutas apliquen las mismas:

- una receta la puede ocultar su autor o un administrador;
- un comentario lo puede ocultar su autor, **el autor de la receta comentada** o un administrador.

La segunda regla es la que resuelve el caso real de una red pequeña: quien publica una receta modera su propia conversación, igual que en la mayoría de redes sociales. El rol de administrador es una columna `isAdmin` en `User` que no se puede activar desde la API: solo con `scripts/setAdmin.js --email=...`, igual que `setPremium.js` (informe 26). Así no hay ninguna ruta que conceda privilegios.

**Lo oculto se trata como inexistente.** Para quien no puede moderarla, una receta oculta responde `404` en el detalle, en los comentarios, al dar «me gusta», guardarla o compartirla (todas pasan por `findRecipeId`). Responder `403` revelaría que la receta existe. Por la misma razón, `PUT /recipes/:id/hidden` sobre una receta oculta ajena da `404`, y solo da `403` si la receta es visible (quien pregunta ya sabe que existe).

**El filtro va en la consulta, no después.** El fragmento `VISIBLE = { hidden: false }` se añade al `where` de Prisma del feed general, el de amigos, el buscador, las guardadas (filtrando por la relación, `recipe: VISIBLE`) y el buscador por despensa. Filtrar en la base de datos mantiene correcta la paginación por cursor: si se quitaran las ocultas después, una página podría llegar con menos elementos de los pedidos. Los comentarios se filtran igual en la lista, en el total que acompaña cada página y en la vista previa de la tarjeta del feed. El contador de comentarios de la tarjeta usa un recuento filtrado de Prisma (`_count: { select: { comments: { where: { hidden: false } } } }`), de modo que el número coincide con lo que se ve. El número de recetas en el buscador de personas tampoco cuenta las ocultas.

**El autor sigue viendo lo suyo.** `GET /recipes/mine` y el detalle devuelven las recetas ocultas a su autor con `hidden: true`, para que sepa que no se ven. Pero el feed no se las enseña ni a él: el feed es lo que ven todos, y enseñar ahí lo oculto daría una idea falsa de lo publicado.

**Solo se pregunta por el administrador cuando hace falta.** Comprobar si quien pregunta es administrador es una consulta más. En el caso habitual (receta visible) no se hace: `canSee` solo consulta el usuario cuando la receta está oculta.

## Cómo funciona

`PUT /recipes/:id/hidden` y `PUT /comments/:id/hidden` reciben `{ "hidden": true }` para ocultar o `{ "hidden": false }` para volver a mostrar, y responden `{ id, hidden }`. Un cuerpo sin booleano da `400`; un id inexistente, `404`; alguien sin permiso, `403` (o `404` en una receta que ya no puede ver). La migración `20261117120000_add_moderation` añade las tres columnas con valor por defecto, así que todo el contenido existente sigue visible.

`test/moderation.test.js` tiene 4 pruebas. La primera comprueba las reglas puras (autor, autor de la receta, administrador, tercero). Las otras tres prueban las rutas reales con Prisma en memoria: la autora oculta una receta, desaparece del feed de otro usuario, su detalle y sus comentarios dan `404`, la autora y el administrador la siguen viendo, y al mostrarla vuelve al feed; un administrador oculta la receta de otra persona; y la autora de una receta oculta un comentario de spam ajeno, que deja de contar en la lista, el total, el contador y la vista previa de la tarjeta, hasta que su autor lo vuelve a mostrar. Además se lanzaron contra la base de datos real (solo lectura) las consultas nuevas con recuento filtrado, para comprobar que Prisma y PostgreSQL las aceptan. Las 165 pruebas del backend pasan.

## Limitaciones

- **Sin interfaz en la aplicación.** La moderación es solo de API: la aplicación Android no tiene todavía la opción «Ocultar» en el menú de la receta ni en los comentarios. Las rutas están listas para añadirla.
- **Sin denuncias.** Un usuario no puede avisar de contenido inapropiado; solo pueden actuar el autor, el autor de la receta y los administradores. Un sistema de denuncias necesitaría su propio modelo y una cola de revisión.
- **Sin panel de administración.** Un administrador modera con las mismas rutas que cualquiera, y para encontrar el contenido tiene que conocer su id.
- **Las notificaciones antiguas siguen ahí.** Una notificación de «me gusta» o de comentario sobre una receta que después se oculta sigue en la lista del autor; al tocarla, el autor sí puede abrirla.
- **Sin registro de quién ocultó qué.** Solo se guarda el estado; no hay historial de moderación.

## Archivos creados o modificados

- `backend/prisma/schema.prisma`: `Recipe.hidden`, `Comment.hidden` y `User.isAdmin`.
- `backend/prisma/migrations/20261117120000_add_moderation/migration.sql` (nuevo).
- `backend/src/social/moderation.js` (nuevo): reglas de moderación y filtro `VISIBLE`.
- `backend/src/routes/recipes.js`: `PUT /:id/hidden`, filtro de ocultas en feed, búsqueda, guardadas y despensa, `404` en el detalle y en `findRecipeId`, campo `hidden` en la respuesta.
- `backend/src/routes/comments.js`: `PUT /:id/hidden`.
- `backend/src/social/recipeSocial.js` y `backend/src/social/comments.js`: recuento y vista previa sin comentarios ocultos.
- `backend/src/routes/users.js`: el número de recetas no cuenta las ocultas.
- `backend/scripts/setAdmin.js` (nuevo).
- `backend/test/moderation.test.js` (nuevo) y `backend/test/dietTags.test.js` (el doble de Prisma crea las recetas con `hidden: false`).
- `docs/anexo-b-documentacion-api-rest.md`: rutas de moderación.
