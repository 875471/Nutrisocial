# 28. Lista de la compra

## Objetivo y problema

El buscador por despensa (núcleo 3.3, informe 11) ya dice qué recetas se pueden cocinar con lo que hay en casa y qué ingredientes faltan para las que casi se pueden. Pero esa información se quedaba en la pantalla: para ir a comprar lo que falta había que apuntarlo a mano en otra aplicación. El objetivo de esta parte es cerrar ese ciclo con una **lista de la compra** dentro de NutriSocial:

- elementos sueltos, para cualquier cosa que no venga de una receta;
- un botón en el detalle de cada receta que añade a la lista **solo los ingredientes que faltan en la despensa**;
- marcar lo comprado y vaciarlo de golpe.

La función es gratuita, como la despensa. Premium sigue limitado al escáner OCR y a las recomendaciones del Diario (informe 26).

## Decisiones técnicas

**Reutilizar la comparación del buscador por despensa, no reimplementarla.** Decidir si un ingrediente «está» en la despensa no es trivial. Se compara por alimento de BEDCA cuando los dos lo tienen y, si no, por nombre, en ambos sentidos. Además, un sustituto cuenta como cubierto: margarina por mantequilla (informe 11). Si la lista de la compra tuviera su propia versión de esa lógica, tarde o temprano el buscador diría «tienes todo» y el botón añadiría algo, o al revés. Por eso `pantryMatch.js` se ha reorganizado sin cambiar su comportamiento. La comparación de una receta con la despensa está ahora en una función interna, `compareRecipe`, que usan tanto `matchRecipesToPantry` (el buscador) como la nueva `missingIngredients`. La única diferencia entre las dos es el corte de cobertura mínima: el buscador descarta las recetas con menos de la mitad de ingredientes, pero la lista de la compra necesita lo que falta aunque falte casi todo. Las 13 pruebas que ya tenía `pantryMatch` siguen pasando sin cambios.

**Simplificación deliberada: no se fusionan elementos.** Si el mismo ingrediente se añade dos veces, de dos recetas distintas o de una receta y a mano, son dos filas. No se suman las cantidades. Sumar bien exigiría convertir entre unidades de ingredientes distintos: «200 g» de harina más «1 taza» de harina necesita la densidad de la harina, y la tabla de conversión (informe 04) solo es aproximada para algunas unidades. Una suma mal hecha («200 g + 1 taza = 201») es peor que dos líneas que el usuario lee y junta mentalmente en un segundo. Por eso `quantity` es texto libre («250 g», «0,5 tazas», «un manojo»), no número más unidad. La decisión está escrita en el comentario de cabecera de la ruta y en el del modelo, y una prueba comprueba que añadir dos veces la misma receta duplica las filas.

**Simplificación deliberada: comprar no llena la despensa.** Marcar un elemento como comprado no lo añade a la despensa. Hacerlo bien tiene casos difíciles: el nombre del elemento puede ser distinto del de la despensa («harina de trigo» frente a «harina»), puede que ya esté en ella (la despensa no admite duplicados) o puede que el usuario haya comprado algo para gastarlo hoy. La pantalla lo dice con una línea bajo lo comprado: «Lo que marcas como comprado no se añade solo a tu despensa». Pasarlo a la despensa queda como un paso manual.

**Modelo propio, con la receta de origen opcional.** `ShoppingListItem` tiene `userId` (se borra con la cuenta, `onDelete: Cascade`), `label`, `quantity` opcional, `checked` y `createdAt`. También tiene `sourceRecipeId`, la receta de la que salió, como relación opcional con `onDelete: SetNull`: si el autor borra la receta, el elemento se queda en la lista, pero sin enlace. Lo que el usuario tiene que comprar no depende de que la receta siga existiendo. La respuesta incluye el título de la receta (`sourceRecipeTitle`) para enseñar «Para «Bizcocho»» bajo cada elemento, y al tocarlo se abre la receta. Un índice `(userId, checked, createdAt)` cubre el orden de la lista.

**Orden: lo pendiente primero.** `GET /shopping-list` devuelve primero los elementos sin marcar y después los marcados, y dentro de cada grupo por orden de alta (`createdAt` ascendente, y el id para desempatar). Así lo que queda por comprar está arriba, en el orden en que se apuntó.

**Todo o nada al añadir desde una receta.** `POST /from-recipe/:recipeId` crea un elemento por cada ingrediente que falta, dentro de una única `$transaction`. Si falla una inserción, no queda la lista a medias. La cantidad se escribe a partir de la de la receta, con coma decimal y la unidad en plural cuando no es 1 («0,5 tazas», «3 unidades»). Un ingrediente sin cantidad («sal») se añade sin ella. La respuesta trae lo añadido y cuántos ingredientes cubría ya la despensa, y con eso la aplicación compone el aviso («Añadidos 3 ingredientes a la lista de la compra (3 ya en tu despensa).»). Si no falta nada responde `200` con la lista vacía en vez de `201`, y el aviso dice «Ya tienes todo en la despensa».

**Vaciar exige `?checked=true`.** `DELETE /shopping-list` sin parámetros responde `400`: un `DELETE` sobre la colección que borrara toda la lista por un error del cliente sería un fallo difícil de perdonar. Para vaciar lo comprado hay que pedirlo explícitamente.

**Los elementos de otro usuario se tratan como inexistentes.** `PATCH` y `DELETE /:id` filtran por `id` y `userId` a la vez (`updateMany`/`deleteMany`), igual que la despensa. Un id ajeno da `404`, no `403`, para no revelar qué ids existen.

**Un ViewModel compartido.** `ShoppingListViewModel` tiene ámbito en `HomeScreen`, como `SubscriptionViewModel` (informe 26). El botón del detalle de receta llama a su `addMissingFromRecipe`, y la pestaña de la lista ve los elementos nuevos sin volver a pedirlos. Marcar, desmarcar y borrar se aplican en la pantalla al momento y se deshacen si el servidor falla (si responde `404`, el elemento ya no existía y se quita). Después de cada cambio se recoloca la lista con el mismo orden que el servidor (`sortedForShopping`), sin pedirla otra vez.

**Una pestaña más en la barra inferior.** La lista tiene que estar a mano en el supermercado, así que es una pestaña propia, «Compra», entre Despensa y Perfil. Con ella son cinco, el máximo recomendado en Material 3, y las etiquetas siguen siendo de una palabra para que no se corten. El icono de carrito, que antes era el de la Despensa, pasa a la lista de la compra, donde se entiende mejor. La Despensa usa ahora un icono de nevera (`ic_kitchen`, de Material Icons).

## Cómo funciona

**API.** Todas las rutas van bajo `/shopping-list` y piden sesión:

- `GET /` devuelve los elementos del usuario: `{ id, label, quantity, checked, sourceRecipeId, sourceRecipeTitle, createdAt }`.
- `POST /` con `{ label, quantity? }` añade un elemento suelto. El nombre es obligatorio (100 caracteres como mucho). La cantidad es texto opcional (50 como mucho). Responde `201`.
- `POST /from-recipe/:recipeId` añade lo que falta y responde `{ added: [...], alreadyInPantry }`. Da `404` si la receta no existe.
- `PATCH /:id` con `checked`, `label` o `quantity` (cualquier combinación, al menos uno) marca, desmarca o corrige. `quantity: null` o vacía la quita.
- `DELETE /:id` borra un elemento (`204`).
- `DELETE /?checked=true` borra los marcados del usuario y responde `{ deleted: n }`.

**Pantalla «Lista de la compra».** Arriba hay un campo para el nombre, otro más estrecho para la cantidad y el botón de añadir (también con «Hecho» en el teclado). Debajo, la tarjeta «Por comprar (n)» y, si hay algo marcado, la tarjeta «Comprado (n)», con los elementos tachados y el botón «Vaciar comprados», que pide confirmación. Cada fila tiene una casilla, el nombre con la cantidad («Harina de trigo · 250 g»), la receta de origen si la hay y una × para quitarla. Se marca tocando la casilla o la fila entera, para no tener que acertar con el dedo en el supermercado. Con la lista vacía, un mensaje explica las dos formas de añadir cosas. Tirar hacia abajo recarga la lista.

**Detalle de receta.** Debajo de la tarjeta de la receta hay un botón «Añadir lo que falta a la lista de la compra». Mientras trabaja enseña un indicador. Al terminar, un snackbar dice cuántos ingredientes se han añadido y cuántos ya estaban en la despensa.

**Pruebas.** `test/shoppingList.test.js` tiene 5 pruebas. Una es de función pura: el formato de la cantidad (coma decimal, plurales, sin unidad, sin cantidad). Las otras cuatro prueban las rutas reales con Prisma en memoria:

- **Alta manual:** limpieza del nombre, cantidad opcional, los `400` y que cada usuario ve solo su lista.
- **`from-recipe`:** en una receta de seis ingredientes con huevos, aceite y margarina en la despensa, solo se añaden harina, azúcar y sal. La mantequilla no se añade porque la margarina la sustituye. Las cantidades se formatean bien, todos llevan la receta de origen y añadir la receta dos veces duplica las filas. Con la despensa vacía se añade todo, con la despensa completa no se añade nada (`200`), y hay `404`/`400` con recetas inexistentes o ids no válidos.
- **Marcar y desmarcar:** los marcados pasan al final y vuelven a su sitio al desmarcarlos. También se prueban la edición de nombre y cantidad, los `400` y el `404` al tocar un elemento ajeno.
- **Vaciar:** sin `?checked=true` no borra nada; con él, solo los marcados del propio usuario. También el borrado individual.

Igual que con las etiquetas (informe 27), se ejecutó también el backend contra un PostgreSQL local temporal. Allí se comprobaron la transacción real de `from-recipe` (la margarina de la despensa cubrió la mantequilla de la receta), el orden de la lista, el `404` entre usuarios y el vaciado. En la aplicación, `DietTagsAndShoppingListTest` comprueba el orden local de la lista y los textos del aviso. Las 161 pruebas del backend y las 30 pruebas unitarias de la aplicación pasan, y la aplicación compila.

## Limitaciones

- **Sin fusión de elementos ni suma de cantidades.** Es la simplificación descrita arriba: el mismo ingrediente de dos recetas son dos filas. Para fusionarlos haría falta normalizar los nombres (la normalización de `matchFood` serviría), sumar solo cantidades de la misma unidad y dejar separadas las demás.
- **Comprar no llena la despensa.** Marcar un elemento no lo añade a la despensa. Un botón «Pasar lo comprado a la despensa» resolvería el caso habitual, aunque tendría que decidir qué hacer con los nombres que no coinciden.
- **Sin compartir la lista.** Es personal: no se puede compartir con quien vive en la misma casa, aunque la capa social ya tiene seguidores (informe 19). Compartirla bien exigiría una lista con varios miembros y sincronización entre dispositivos.
- **Sin orden por pasillos ni categorías.** Los elementos van en orden de alta, no agrupados por sección del supermercado (frutería, lácteos…).
- **La lista no se actualiza sola en otro dispositivo.** Se recarga al entrar en la pestaña y al tirar hacia abajo, como el resto de la aplicación. No hay notificaciones push.

## Archivos creados o modificados

- `backend/prisma/schema.prisma`: modelo `ShoppingListItem` y relaciones inversas en `User` y `Recipe`.
- `backend/prisma/migrations/20261112120000_add_diet_tags_and_shopping_list/migration.sql` (nuevo, compartido con el informe 27).
- `backend/src/routes/shoppingList.js` (nuevo) y `backend/src/index.js` (montaje en `/shopping-list`).
- `backend/src/nutrition/pantryMatch.js`: `compareRecipe` y `missingIngredients`, compartidas con el buscador por despensa.
- `backend/test/shoppingList.test.js` (nuevo).
- `app/.../data/ShoppingListModels.kt`, `ShoppingListRepository.kt` (nuevos) y `ApiService.kt`.
- `app/.../ui/shopping/ShoppingListScreen.kt`, `ShoppingListViewModel.kt` (nuevos).
- `app/.../ui/home/HomeScreen.kt`: pestaña «Compra» y botón del detalle.
- `app/.../ui/recipes/RecipeDetailScreen.kt`: botón «Añadir lo que falta a la lista de la compra».
- `app/src/main/res/drawable/ic_kitchen.xml` (nuevo): icono de la Despensa.
- `app/src/test/.../DietTagsAndShoppingListTest.kt` (nuevo, compartido con el informe 27).
- `docs/anexo-b-documentacion-api-rest.md`: grupo de rutas `/shopping-list`.
