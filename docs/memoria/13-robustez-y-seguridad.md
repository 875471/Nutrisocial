# 13. Robustez y seguridad: editar y borrar recetas, eliminar la cuenta y protección del acceso

## Objetivo y problema

Con los cuatro núcleos técnicos implementados, la aplicación tenía huecos que un usuario real notaría enseguida, y algunos puntos débiles de seguridad:

- **Una receta, una vez creada, no se podía corregir ni borrar.** Solo se podía cambiar su foto (informe 12). Una errata en el título o una cantidad mal puesta obligaban a crear otra receta y a convivir con la mala.
- **No había forma de darse de baja.** Un usuario no podía eliminar su cuenta ni sus datos.
- **El acceso era fácil de atacar.** `POST /auth/login` admitía intentos ilimitados, así que se podía probar contraseñas a ritmo de máquina. `POST /auth/register` aceptaba cualquier contraseña, incluida una de un carácter, y cualquier texto como email. Si faltaba la variable `JWT_SECRET`, el servidor arrancaba sin quejarse y fallaba después, en el primer login, con un error poco claro de la librería de tokens.
- **Algunos casos límite no estaban bien resueltos.** Por ejemplo, abrir una receta que su autor acababa de borrar, o pulsar "Buscar recetas" con la despensa vacía.

## Decisiones técnicas

**Editar reutiliza exactamente la lógica de crear.** La validación del cuerpo y el cálculo nutricional de `POST /recipes` se han extraído a una función, `buildRecipeData(body)`, que ahora usan `POST /recipes` y el nuevo `PUT /recipes/:id`. Así, las dos rutas no pueden divergir: los mismos campos, los mismos mensajes de error y los totales recalculados igual, contra la tabla `Food`. Al editar, los ingredientes **se sustituyen por completo** dentro de una única actualización anidada de Prisma (`ingredients: { deleteMany: {}, create: [...] }`), que se ejecuta como una sola operación atómica. No queda nunca una receta con los ingredientes antiguos borrados y los nuevos sin crear. Se descartó actualizar ingrediente a ingrediente: habría que emparejar filas antiguas y nuevas sin un identificador estable (el cliente no lo conoce) y no aporta nada, porque los totales se recalculan de todas formas.

**La foto no viaja en la edición.** `PUT /recipes/:id` solo cambia la foto si el cuerpo trae `imageBase64` y, si no, conserva la que hubiera. La aplicación no la envía en el `PUT`: si el usuario la ha cambiado en el formulario, la guarda después con `PUT /recipes/:id/image`, la ruta que ya existía. El motivo es el mismo del informe 12: Gson omite los campos `null`, así que no podría expresar "quitar la foto" dentro del `PUT` general. Si la receta se guarda pero la foto falla, los cambios se conservan y el detalle avisa "Cambios guardados, pero no la foto: …".

**Una sola comprobación de autoría.** Editar, borrar y cambiar la foto usan la misma función, `findOwnRecipeId`, que responde 404 si la receta no existe y 403 si quien pregunta no es su autor, antes de validar el cuerpo o calcular nada. Así, alguien que no es el autor no puede, por ejemplo, provocar búsquedas en Open Food Facts con una edición que se va a rechazar.

**Borrar una receta sin romper el diario.** El esquema ya tenía `onDelete: SetNull` en `LogEntry.recipeId`, y cada entrada del diario guarda una copia del nombre y de los valores nutricionales (informe 08). Por eso borrar una receta no altera el histórico: las entradas siguen sumando lo mismo, solo que ya no enlazan a la receta. `DELETE /recipes/:id` cuenta, en la misma transacción que el borrado, **cuántas de esas entradas son del propio autor** y lo devuelve en `orphanedLogEntries`, y la aplicación lo avisa ("Las 2 entradas de tu diario con esta receta se conservan, pero ya no enlazan a ella"). Las entradas de otros usuarios no se cuentan: la cifra le diría al autor cuánta gente ha registrado su receta, un dato que no le corresponde.

**Borrar la cuenta exige la contraseña, y la respuesta a un fallo es 403, no 401.** `DELETE /auth/me` necesita el token y, además, la contraseña actual en el cuerpo, que se comprueba con `bcrypt.compare` antes de borrar nada. Así, un token robado o una sesión olvidada en un móvil compartido no basta para eliminar la cuenta. La contraseña va en el cuerpo y no en la URL, para que no quede en los registros de acceso del servidor. Retrofit no admite cuerpo en `@DELETE`, así que en Android se declara con `@HTTP(method = "DELETE", hasBody = true)`. El detalle importante es el código de error. La aplicación cierra la sesión automáticamente ante cualquier 401 de una petición autenticada (`AuthInterceptor`, informe 02), porque 401 significa "token caducado o inválido". Si una contraseña mal tecleada devolviera 401, el usuario sería expulsado al login por equivocarse al escribir. Por eso la ruta responde **403**: el token es válido, lo que falla es la confirmación. En este cambio también se corrigió el propio interceptor, que no añadía el token a ninguna ruta que empezara por `/auth/`. Ahora solo lo omite en `login` y `register`. El borrado en sí es un único `prisma.user.delete`: las relaciones del esquema tienen `onDelete: Cascade`, así que desaparecen las recetas del usuario (con sus ingredientes y los likes que recibieron), sus likes, su diario y su despensa.

**Límite de intentos por IP con `express-rate-limit`.** Login y registro aceptan como mucho **10 intentos por IP cada 15 minutos**. A partir de ahí, la respuesta es un 429 con un mensaje en castellano y la cabecera estándar `RateLimit`, que indica cuándo reintentar. Hay dos matices:

- **En el login solo cuentan los intentos fallidos** (`skipSuccessfulRequests`). El objetivo es frenar a quien prueba contraseñas, no a quien entra bien varias veces seguidas.
- **En el registro cuentan todos**, para frenar también la creación masiva de cuentas.

`DELETE /auth/me` tiene su propio límite de fallos, para que un token robado no sirva para adivinar la contraseña a base de intentos. Los tres limitadores salen de la misma factoría (`middleware/rateLimit.js`).

Un punto delicado del despliegue: Render pone un proxy delante del servidor, así que sin configuración Express vería siempre la IP del proxy, y el límite de 10 intentos se repartiría entre **todos** los usuarios a la vez. Se activa `app.set('trust proxy', 1)` para que tome la IP del cliente de la cabecera `X-Forwarded-For` que añade ese proxy.

**Validación del registro en el servidor.** La aplicación ya validaba en el cliente, pero con un mínimo de 4 caracteres, y el servidor no validaba nada: cualquier cliente que no fuera la aplicación podía saltarse las reglas. Ahora el servidor exige:

- Contraseña de **8 caracteres** como mínimo y de **72 bytes** como máximo. bcrypt ignora sin avisar lo que pasa de 72 bytes, así que dos contraseñas largas que solo se diferenciaran al final serían la misma.
- Email con el formato `algo@algo.dominio`, sin espacios.
- Nombre no vacío y de 50 caracteres como máximo.

Además, email y nombre se guardan sin espacios sobrantes. La expresión regular del email es deliberadamente simple. Validar un email "de verdad" con una expresión regular es inútil, porque la única forma de saber si una dirección existe es mandarle un correo (ver Limitaciones).

En la aplicación, el mínimo de 8 caracteres **solo se aplica al registrarse**. Antes, la misma validación servía para el login, y subirla a 8 habría impedido entrar a quien se registró con una contraseña de 4 a 7 caracteres, que era válida entonces.

**No arrancar sin `JWT_SECRET`.** `index.js` comprueba la variable nada más cargar `.env`. Si falta, escribe un error que dice qué falta y dónde configurarlo, y termina con `process.exit(1)`. Render marca así el despliegue como fallido en lugar de publicar un servidor que no puede autenticar a nadie.

**Revisión de fugas de datos.** Se repasaron todas las consultas de Prisma que tocan `User`. El único `include` del autor en las recetas ya pedía solo su nombre (`author: { select: { name: true } }`); no hace falta el id, porque la receta ya lo lleva en `authorId`. Además, ninguna ruta devuelve objetos de Prisma tal cual: todas las respuestas se construyen campo a campo (`toRecipeResponse`, `toFeedItem`, `toProfileResponse`, y ahora `toUserResponse` en la autenticación). Aunque una consulta trajera el usuario completo, el hash no llegaría a la respuesta. Para que esto no se rompa en el futuro, hay una prueba que comprueba que ni el hash ni un campo `password` aparecen en las respuestas de recetas y de login. El Prisma simulado de la prueba devuelve el usuario completo si una consulta lo pide entero, así que la prueba fallaría si alguien cambiara el `select` por un `include: { author: true }`.

## Cómo funciona

**Editar una receta.** En el detalle de una receta propia aparecen, bajo los botones de la foto, "Editar" y "Eliminar" (en rojo). "Editar" llama a `RecipeViewModel.startEditing()`, que rellena el formulario a partir de la receta ya cargada, sin volver a pedirla al servidor. Rellena título, raciones, tiempo, pasos, foto e ingredientes con su cantidad, su unidad y el alimento que tenían asociado, para que al guardar no se vuelvan a buscar por nombre. Después navega a la ruta `recetas/editar`, que usa el mismo `RecipeFormScreen` que la de crear (la parte común está en `RecipeFormDestination`). En modo edición, el título de la pantalla es "Editar receta", el botón dice "Guardar cambios" y se llama a `PUT /recipes/:id` en vez de `POST`. Al terminar, se vuelve al detalle, que ya muestra la receta actualizada con el aviso "Cambios guardados". La copia de "Mis recetas" y la tarjeta del feed también se actualizan, incluidas las kcal por ración, que cambian si cambian los ingredientes o las raciones.

**Eliminar una receta.** "Eliminar" pide confirmación ("¿Eliminar la receta?"). La confirmación explica que desaparecerá de las recetas del usuario y del inicio, con sus me gusta, que lo ya apuntado en el diario se conserva y que no se puede deshacer. Al aceptar, se llama a `DELETE /recipes/:id`. La receta se quita de "Mis recetas" y del feed, y se vuelve a la lista, que muestra un *snackbar* con el resultado y, si procede, cuántas entradas del diario se han quedado sin enlace. Si el detalle se había abierto desde otra pestaña (Inicio, Diario o Despensa), se cambia a la pestaña de recetas.

**Eliminar la cuenta.** Al final del perfil, separada del resto por debajo de "Cerrar sesión" y sobre fondo de color de error, está la "Zona peligrosa", con el botón "Eliminar cuenta". El borrado tiene dos pasos:

1. Un diálogo pide la contraseña. No se guarda en el estado guardado de la pantalla (`rememberSaveable`), porque una contraseña no debe acabar ahí.
2. Un segundo diálogo avisa de que "Esta acción no se puede deshacer" y enumera lo que se borrará.

Mientras se borra, el diálogo no se puede cerrar. Si el servidor rechaza la contraseña, se vuelve al primer paso con el error bajo el campo. Si acepta, `ProfileViewModel` marca la cuenta como borrada, la pantalla llama al mismo `onLogout` que "Cerrar sesión", y la navegación vuelve al login con la pila limpia.

**Casos límite revisados.**

- **Feed vacío.** Ya estaba resuelto en el informe 12: con un usuario nuevo o la base de datos recién sembrada, se muestra "Todavía no hay recetas" con una indicación para crear la primera, dentro de la lista (así también funciona tirar para refrescar).
- **Despensa vacía.** El botón "Buscar recetas" ya estaba desactivado sin ingredientes, pero no explicaba por qué. Ahora, debajo, se lee "Añade al menos un ingrediente para poder buscar recetas". Además, `PantryViewModel.searchRecipes` comprueba la despensa antes de llamar al servidor, por si se invocara por otra vía.
- **Receta borrada mientras se mira.** Todas las rutas de una receta concreta responden con el mismo 404: "Esta receta ya no existe". Antes, si el detalle tenía una copia en la lista y el servidor fallaba, se seguía mostrando la copia sin avisar. Ahora un 404 al cargar se trata aparte: el detalle muestra "Esta receta ya no existe", con una explicación y un botón "Volver" (reintentar no tendría sentido), y la receta se quita de la lista. Lo mismo ocurre si el 404 llega al dar me gusta o al cambiar la foto. En el feed, un me gusta a una receta borrada la quita de la lista.
- **Me gusta sin conexión.** Se revisó el comportamiento del informe 12 y se confirmó. Si la petición falla (sin red, el error llega como `ApiResult.Error` sin código), el corazón y el contador vuelven a su estado anterior y un *snackbar* lo explica, tanto en el detalle como en el feed. Mientras hay una petición en curso para una receta se ignoran más toques, así que no puede quedar un estado distinto al del servidor. Se añadió el caso 404 descrito arriba, que antes se habría tratado como un fallo de red.

**Pruebas.** `test/security.test.js` añade 10 pruebas y la batería del backend pasa de 62 a 72, todas correctas. Las rutas se prueban por HTTP, con Express y JWT reales, sobre un Prisma simulado. `fetch` está bloqueado para que ninguna prueba llegue a Open Food Facts. Los módulos se recargan en cada prueba, para que cada una empiece con los contadores del límite a cero. Las pruebas cubren:

- **Editar y borrar recetas.** Editar o borrar una receta ajena da 403 y no cambia nada. El autor sí puede editar, y los totales se recalculan: 200 g de harina a 340 kcal/100 g dan 680 kcal, 170 por ración de 4. Al borrar, se informa de 2 entradas del diario del autor, sin contar la de otro usuario. Después, `GET` de la receta da 404 con "Esta receta ya no existe".
- **Borrar la cuenta.** Con la contraseña incorrecta da 403 y la cuenta sigue ahí. Sin contraseña da 400 y sin token, 401. Con la contraseña correcta da 204, la cuenta y sus recetas desaparecen, el otro usuario no se ve afectado, y un segundo intento con el mismo token da 404. (Desde el repaso del informe 22, `requireAuth` comprueba además que el usuario del token siga existiendo: ese segundo intento, y cualquier otra petición con el token de una cuenta borrada, da 401 y la aplicación cierra la sesión sola.)
- **Límite de intentos.** El 11.º login fallido en la ventana da 429, aunque ese intento lleve la contraseña correcta. Doce logins correctos seguidos no se bloquean. El 11.º registro da 429.
- **Validación del registro.** Se prueban contraseñas de 7 caracteres, cuatro emails mal formados, un nombre en blanco y una contraseña que no es texto. En el caso correcto, se comprueba que se recortan los espacios y que la respuesta no incluye la contraseña.
- **Fugas de datos.** Ni el hash ni un campo `password` aparecen en las respuestas de recetas y de login.
- **Arranque sin `JWT_SECRET`.** Se lanza `node src/index.js` sin la variable y se comprueba que termina con código 1 y un mensaje que la nombra.

En Android, la aplicación compila sin avisos y sin imports sin usar. Los nuevos flujos no se probaron a mano en el emulador: el backend desplegado todavía no tenía estas rutas, y probarlos contra el local habría escrito en la base de datos de producción.

## Limitaciones

- **No hay recuperación de contraseña.** Quien la olvide no puede recuperarla: haría falta enviar un correo con un enlace de un solo uso y caducidad corta, lo que requiere un servicio de envío de correo (SMTP o una API como SendGrid) que no forma parte del proyecto. Tampoco se puede cambiar la contraseña desde la aplicación.
- **No se verifica el email al registrarse.** La expresión regular solo comprueba el formato. Se puede crear una cuenta con una dirección inexistente o ajena. Verificarla también requeriría enviar correos.
- *Actualización (informe 14): la verificación del email y la recuperación de contraseña se implementaron después con Brevo; ver ese informe.*
- **El límite de intentos es por IP y está en memoria.** No distingue usuarios detrás de la misma red: en una residencia, una universidad o una red móvil con NAT compartida, los intentos fallidos de unos cuentan para todos, y 10 registros cada 15 minutos pueden quedarse cortos en una demostración en clase con todos en la misma wifi. Al revés, un atacante con muchas IP puede repartir sus intentos. Además, los contadores viven en la memoria del proceso: se reinician con cada despliegue o cada vez que Render despierta el servicio, y no se compartirían entre varias instancias (haría falta un almacén común, como Redis). Tampoco hay bloqueo por cuenta, que protegería una cuenta concreta aunque los intentos vengan de IP distintas.
- **La IP del cliente depende del proxy.** `trust proxy` confía en un salto de proxy, que es lo que pone Render. Si la infraestructura cambiara (otro proxy o una CDN delante), habría que revisar ese valor, o el límite volvería a ver una sola IP para todos.
- **Los tokens no se pueden revocar.** Un token JWT vale 7 días. Cerrar sesión solo lo borra del móvil y, tras eliminar la cuenta, un token robado sigue siendo formalmente válido hasta caducar, aunque ya no sirva de nada porque el usuario no existe (las rutas devuelven 404). Revocar tokens exigiría una lista negra o tokens de corta duración con renovación.
- **Borrar es definitivo.** No hay papelera ni periodo de gracia, ni para las recetas ni para la cuenta. Tampoco se ofrece descargar los datos antes de borrar la cuenta (portabilidad).
- **Editar no conserva historial.** La receta editada sustituye a la anterior. Las entradas del diario conservan sus valores copiados, pero el detalle de la receta muestra ya la nueva versión.
- **Sin pruebas de interfaz ni prueba manual.** Los flujos de Android (editar, eliminar, borrar la cuenta) compilan, pero no tienen pruebas automáticas de Compose y no se han recorrido a mano en un dispositivo contra un backend con estas rutas.

## Archivos creados o modificados

**Backend (`backend/`)**

| Archivo | Cambio |
|---|---|
| `package.json`, `package-lock.json` (modificados) | Dependencia `express-rate-limit` (8.7). |
| `src/middleware/rateLimit.js` (nuevo) | `createAuthLimiter`: 10 intentos por IP cada 15 minutos, 429 en JSON, cabecera `RateLimit`. |
| `src/routes/auth.js` (modificado) | Validación del registro (contraseña de 8 a 72 bytes, email, nombre), límites en login, registro y borrado, `DELETE /auth/me` con contraseña (403 si no coincide), `toUserResponse`. |
| `src/routes/recipes.js` (modificado) | `buildRecipeData` compartido por `POST` y `PUT`, `findOwnRecipeId`, `PUT /recipes/:id`, `DELETE /recipes/:id` con `orphanedLogEntries`, 404 "Esta receta ya no existe". |
| `src/index.js` (modificado) | No arranca sin `JWT_SECRET`; `trust proxy` para el límite de intentos detrás de Render. |
| `test/security.test.js` (nuevo) | 10 pruebas de autoría, borrado de cuenta, límite de intentos, validación, fugas y arranque. |

**Aplicación Android (`app/src/main/java/com/example/nutrisocial/`)**

| Archivo | Cambio |
|---|---|
| `data/AuthInterceptor.kt` (modificado) | Solo omite el token en login y registro, no en todo `/auth/`. |
| `data/AuthModels.kt`, `data/AuthRepository.kt` (modificados) | `DeleteAccountRequest`, `deleteAccount` y mensajes para 403, 404 y 429. |
| `data/RecipeModels.kt`, `data/RecipeRepository.kt`, `ApiService.kt` (modificados) | `DeleteRecipeResponse`, `updateRecipe`, `deleteRecipe`, `deleteAccount` (`@HTTP` con cuerpo) y `RECIPE_GONE_MESSAGE`. |
| `ui/auth/AuthViewModel.kt` (modificado) | Mínimo de 8 caracteres solo en el registro. |
| `ui/recipes/RecipeViewModel.kt` (modificado) | Modo edición (`startEditing`, `saveEdit`), `deleteRecipe`, tratamiento del 404 (`onRecipeGone`) y aviso para la lista. |
| `ui/recipes/RecipeDetailScreen.kt` (modificado) | "Editar" y "Eliminar" con confirmación para el autor; pantalla "Esta receta ya no existe" con "Volver". |
| `ui/recipes/RecipeFormScreen.kt` (modificado) | Título "Editar receta" y botón "Guardar cambios" en modo edición. |
| `ui/recipes/RecipeListScreen.kt` (modificado) | *Snackbar* para el aviso tras eliminar. |
| `ui/home/HomeScreen.kt` (modificado) | Ruta `recetas/editar`, `RecipeFormDestination` compartido, vuelta a la lista tras borrar y acciones del perfil. |
| `ui/home/ProfileViewModel.kt`, `ui/home/ProfileScreen.kt` (modificados) | `AccountDeletionState`, `deleteAccount` y la "Zona peligrosa" con los dos diálogos. |
| `ui/home/FeedViewModel.kt` (modificado) | `removeRecipe`, 404 al dar me gusta, y kcal, raciones y tiempo sincronizados al editar. |
| `ui/pantry/PantryViewModel.kt`, `ui/pantry/PantryScreen.kt` (modificados) | No busca con la despensa vacía y explica por qué el botón está desactivado. |
