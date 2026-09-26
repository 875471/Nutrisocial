# 03 · Gestión de recetas e identidad visual

## Objetivo y problema

Una vez resuelto el acceso de usuarios, NutriSocial necesitaba su primer contenido propio: las recetas. El objetivo de esta iteración es que cada persona pueda crear recetas, consultar la lista de las que ha guardado y ver cualquiera de ellas en detalle. Es la base sobre la que se construirán después las funciones sociales (compartir, comentar o seguir las recetas de otros), por lo que conviene que el modelo de datos y la estructura de la aplicación estén bien planteados desde el principio.

Esta parte plantea varios problemas nuevos respecto a la autenticación. En el servidor, por primera vez hay recursos que pertenecen a un usuario, así que las rutas deben saber quién hace cada petición y no pueden fiarse de lo que diga el cliente. En la aplicación, las peticiones tienen que ir autenticadas con el token obtenido al iniciar sesión, y aparece un formulario más complejo que los de login y registro, con listas de ingredientes y pasos de longitud variable. Además, la aplicación pasa de tener una sola pantalla tras el login a necesitar una navegación con varias secciones. Por último, se aprovechó la iteración para sustituir el aspecto por defecto de Material 3 por una identidad visual propia.

## Decisiones técnicas

**Modelo `Recipe` en Prisma.** La receta se relaciona con su autor mediante una clave ajena `authorId` hacia `User`, con borrado en cascada para no dejar recetas huérfanas si algún día se eliminan cuentas, y un índice sobre esa columna porque la consulta más frecuente es "las recetas de este usuario". Los ingredientes y los pasos son listas, pero SQLite no tiene un tipo lista ni Prisma admite arrays escalares con este proveedor. Se optó por guardarlos como JSON serializado en un campo de texto. La alternativa normalizada (tablas `Ingredient` y `Step` con su orden) sería más flexible para búsquedas por ingrediente, pero añade complejidad que todavía no aporta nada. La serialización queda encapsulada en el servidor, de modo que la API siempre expone arrays y la aplicación no sabe cómo se almacenan: si en el futuro se normaliza, el cliente no cambia.

**Middleware de autenticación.** En lugar de comprobar el token en cada ruta, se creó un middleware `requireAuth` que lee la cabecera `Authorization: Bearer <token>`, lo verifica con `JWT_SECRET` y deja el identificador del usuario en `req.userId`. Se aplica una sola vez a todo el router de `/recipes`. Una decisión de seguridad importante es que el `authorId` de una receta nueva se toma siempre del token y nunca del cuerpo de la petición: un cliente malicioso no puede crear recetas en nombre de otro.

**Validación en el servidor.** Aunque la aplicación ya valida los datos, el servidor no puede depender de ello, porque cualquiera puede llamar a la API directamente. Se comprueba que el título no esté vacío, que haya al menos un ingrediente y un paso (descartando los elementos en blanco), que las raciones sean un entero positivo y que el tiempo, si se indica, sea un entero no negativo. También se añadió un manejador de errores global que responde siempre en JSON, de modo que la aplicación puede mostrar el mensaje de cualquier error con el mismo mecanismo.

**Interceptor de OkHttp para el token.** Pasar el token como parámetro en cada método de `ApiService` obligaría a todos los repositorios a conocerlo. En su lugar, un `AuthInterceptor` añade la cabecera a todas las peticiones salvo las de `/auth/`, leyendo el token del `SessionManager` (DataStore). Así, los endpoints protegidos se declaran igual que los públicos. El mismo interceptor resuelve otro problema: si el servidor responde 401 a una petición autenticada (por ejemplo, porque el token de siete días ha caducado), borra la sesión, y la aplicación vuelve sola a la pantalla de login gracias a que la navegación ya observaba el estado de sesión (ver informe 02). Como el interceptor necesita un `Context` para acceder a DataStore, se añadió una clase `Application` (`NutriSocialApp`) que inicializa `RetrofitClient` al arrancar el proceso.

**Generalización del resultado sellado.** El tipo `AuthResult` y la función que traducía respuestas HTTP estaban dentro de la capa de autenticación. Al necesitarlos también para las recetas, se extrajeron a `ApiResult` y `safeApiCall`, compartidos por todos los repositorios. Cada repositorio puede aportar mensajes propios para ciertos códigos (por ejemplo, "Receta no encontrada" para un 404), pero la lógica de captura de excepciones y de lectura del cuerpo de error existe en un solo sitio.

**Navegación anidada con barra inferior.** La pantalla principal pasó a ser un contenedor con una `NavigationBar` de Material 3 (Inicio, Mis recetas y Perfil) y su propio grafo de navegación, anidado dentro del grafo principal que decide entre login y home. Esta separación mantiene intacta la lógica de sesión del informe anterior y deja las pantallas de recetas dentro de la sección autenticada. La barra se oculta en el formulario y en el detalle para dar más espacio y dejar claro que son pantallas de un solo propósito, con su botón de volver.

**Un ViewModel compartido por las pantallas de recetas.** `RecipeViewModel` se crea con el ámbito de la entrada "home" del grafo principal, no de cada pantalla. Así, la lista, el formulario y el detalle comparten los mismos datos: al guardar una receta, la lista ya la contiene al volver, y el detalle puede mostrarse al instante con la copia que ya estaba en la lista. Al cerrar sesión, esa entrada desaparece y el ViewModel se destruye con ella, por lo que otro usuario nunca verá datos del anterior.

**Identidad visual propia.** Se desactivó el color dinámico de Android 12+ (Material You), que sustituiría los colores de la aplicación por los del fondo de pantalla del usuario, y se definieron esquemas de color completos para tema claro y oscuro. El verde hoja (`#2E7D32`) es el color principal; un naranja tostado (`#E8A33D`) aporta un acento cálido, asociado a alimentos como la calabaza o los cereales; y un marrón tierra completa la paleta como color terciario. El fondo es un crema cálido (`#FAF6EF`) en lugar de blanco puro, y en oscuro, un marrón casi negro en lugar de gris. Además de los colores de acento, se definieron explícitamente los niveles de superficie (`surfaceContainer*`), que Material 3 usa en tarjetas y barras, para que también tuvieran el tono cálido. Las formas y el espaciado se centralizaron en `Dimens.kt`: un radio de 16 dp en tarjetas y botones (12 dp en campos de texto), una escala de espaciado de 4, 8, 16, 24 y 32 dp de la que salen todos los márgenes, y una elevación común de 3 dp en las tarjetas.

## Cómo funciona

Al iniciar sesión, la aplicación muestra la pestaña Inicio con un saludo y dos tarjetas de acceso rápido: una resume cuántas recetas tiene guardadas el usuario y lleva a la lista, y la otra abre directamente el formulario de creación. En ese momento, el `RecipeViewModel` ya ha pedido al servidor la lista de recetas propias (`GET /recipes/mine`), así que el resumen se actualiza en cuanto llega la respuesta.

La pestaña Mis recetas muestra esa lista como tarjetas. Cada una lleva un cuadro con la inicial del título, que da color mientras la aplicación no admita fotos, y dos etiquetas con las raciones y el tiempo de preparación, este último solo si se indicó. La pantalla refleja los tres estados de la carga: un indicador de progreso mientras se espera, un mensaje con botón de reintentar si falla, y un estado vacío que invita a crear la primera receta si la lista no tiene elementos. El botón flotante "Nueva receta" abre el formulario.

El formulario mantiene su contenido en el ViewModel y no en la propia pantalla. Esto tiene dos ventajas: sobrevive a la rotación del dispositivo, y las listas de ingredientes y pasos se tratan como datos normales. Cada una empieza con un campo vacío; el botón "Añadir ingrediente" o "Añadir paso" agrega otro, y la cruz junto a cada campo lo elimina (dejando siempre al menos uno). Los campos numéricos solo admiten dígitos. Al pulsar "Guardar receta", el ViewModel descarta los elementos en blanco y valida los datos; si algo falta, muestra el error bajo el formulario sin llegar a llamar al servidor. Si todo es correcto, envía `POST /recipes` y, mientras espera, deshabilita el formulario y muestra un indicador en el botón. Cuando el servidor confirma la creación, la receta se inserta al principio de la lista local, se lanza una recarga desde el servidor para mantener la coherencia y la aplicación vuelve a Mis recetas con el formulario limpio.

Al pulsar una tarjeta se navega al detalle, pasando el identificador de la receta como argumento de la ruta. El ViewModel muestra de inmediato la versión que ya tenía en la lista y, en paralelo, la pide al servidor con `GET /recipes/:id` para tener la versión más reciente. El detalle presenta el título, las etiquetas de raciones y tiempo, los ingredientes en una tarjeta con viñetas y los pasos numerados en otra.

Todas estas peticiones pasan por el `AuthInterceptor`, que añade el token sin que ningún repositorio ni pantalla intervenga. En el servidor, `requireAuth` lo valida antes de llegar a las rutas, y las recetas se crean y consultan siempre a nombre del usuario del token. La pestaña Perfil, de momento un marcador de posición, muestra el nombre y el email del usuario y alberga el botón de cierre de sesión.

El flujo completo se verificó en un emulador Android 12 (API 32): registro, alta de una receta con dos ingredientes y dos pasos, vuelta automática a la lista con la receta nueva, apertura del detalle, validación con el formulario vacío, cierre de sesión, persistencia de la sesión tras cerrar y reabrir la aplicación, y aspecto en tema claro y oscuro. Los endpoints se probaron también por separado con peticiones sin token, con un token falso, con datos inválidos, con identificadores inexistentes y con JSON mal formado.

## Archivos creados o modificados

**Backend (`backend/`)**

| Archivo | Responsabilidad |
|---|---|
| `prisma/schema.prisma` (modificado) | Nuevo modelo `Recipe` y relación `User.recipes`. |
| `prisma/migrations/…_add_recipe/` (nuevo) | Migración que crea la tabla `Recipe` y su índice. |
| `src/middleware/auth.js` (nuevo) | Middleware `requireAuth`: valida el JWT y expone `req.userId`. |
| `src/routes/recipes.js` (nuevo) | `POST /recipes`, `GET /recipes/mine` y `GET /recipes/:id`, con validación y conversión JSON ↔ arrays. |
| `src/index.js` (modificado) | Registro de las rutas de recetas y manejador de errores global en JSON. |

**Aplicación Android (`app/src/main/java/com/example/nutrisocial/`)**

| Archivo | Responsabilidad |
|---|---|
| `data/ApiResult.kt` (antes `AuthResult.kt`) | Resultado sellado y `safeApiCall`, comunes a todos los repositorios. |
| `data/AuthRepository.kt` (modificado) | Adaptado a `safeApiCall`. |
| `data/RecipeModels.kt` (nuevo) | Data classes `Recipe` y `CreateRecipeRequest`. |
| `data/RecipeRepository.kt` (nuevo) | Acceso a los tres endpoints de recetas. |
| `data/AuthInterceptor.kt` (nuevo) | Añade el token a las peticiones y cierra la sesión ante un 401. |
| `data/SessionManager.kt` (modificado) | Expone el token como `Flow` para el interceptor. |
| `NutriSocialApp.kt` (nuevo) | Clase `Application` que inicializa `RetrofitClient`. |
| `RetrofitClient.kt` (modificado) | Cliente OkHttp propio con el interceptor. |
| `ApiService.kt` (modificado) | Métodos `createRecipe`, `getMyRecipes` y `getRecipe`. |
| `ui/recipes/RecipeViewModel.kt` (nuevo) | Estados de lista, detalle y formulario; validación y guardado. |
| `ui/recipes/RecipeListScreen.kt` (nuevo) | Lista "Mis recetas" con estados de carga, error y vacío, y FAB. |
| `ui/recipes/RecipeFormScreen.kt` (nuevo) | Formulario con listas editables de ingredientes y pasos. |
| `ui/recipes/RecipeDetailScreen.kt` (nuevo) | Vista completa de una receta. |
| `ui/recipes/RecipeComponents.kt` (nuevo) | Tarjeta de receta, etiquetas y mensajes de estado reutilizables. |
| `ui/home/HomeScreen.kt` (reescrito) | Contenedor con `NavigationBar` y grafo de navegación interno. |
| `ui/home/HomeTabScreen.kt` (nuevo) | Pestaña Inicio: saludo y accesos rápidos. |
| `ui/home/ProfileScreen.kt` (nuevo) | Pestaña Perfil provisional con cierre de sesión. |
| `ui/navigation/AppNavHost.kt` (modificado) | Pasa el usuario de la sesión a `HomeScreen`. |
| `ui/auth/AuthComponents.kt` (modificado) | Adaptado a la nueva escala de espaciado y forma de botones. |
| `ui/theme/Color.kt`, `Theme.kt`, `Type.kt` (reescritos) | Paleta propia clara/oscura, sin color dinámico, y tipografía con títulos reforzados. |
| `ui/theme/Dimens.kt` (nuevo) | Escala de espaciado, radios y formas de Material. |

**Otros:** `AndroidManifest.xml` (registro de `NutriSocialApp`), `app/build.gradle.kts` y `gradle/libs.versions.toml` (dependencia explícita de OkHttp 4.12).
