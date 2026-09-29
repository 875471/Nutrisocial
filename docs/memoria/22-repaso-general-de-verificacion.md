# 22. Repaso general de verificación

## Objetivo y problema

Tras una larga serie de iteraciones seguidas se habían ido acumulando cambios en muchas partes de la aplicación: autenticación, recetas, OCR, nutrición, perfil, diario, despensa, recomendador, feed social, comentarios, seguir usuarios, notificaciones, guardadas, buscador, calendario, seguridad y verificación de email. Varias de esas iteraciones tocaron la navegación (la pestaña Recetas desapareció, "Mis recetas" y "Guardadas" pasaron al Perfil, Inicio se dividió en "Explorar" y "Amigos") y el esquema de datos (`Comment`, `SavedRecipe`, `Follow` y `Notification` en pocos días). En esas condiciones es fácil que algo que funcionaba deje de hacerlo sin que ninguna prueba lo detecte; ya había pasado una vez con la URL del servidor.

Esta iteración no añade funcionalidad. Es una pasada de verificación completa antes de seguir, dividida en siete partes:

1. Pruebas y compilación.
2. Migraciones y datos de partida desde una base de datos vacía.
3. Los flujos principales de extremo a extremo.
4. Código muerto de las reorganizaciones.
5. Coherencia visual y de estados.
6. Evaluaciones cuantitativas.
7. Correspondencia entre los informes y los commits.

## Decisiones técnicas

**Nunca contra producción.** `backend/.env` apunta a la base de datos de producción de Neon, y no hay otra. Para comprobar las migraciones desde cero y recorrer los flujos se creó un clúster de PostgreSQL 17 temporal con el PostgreSQL ya instalado en el equipo (`initdb` en una carpeta de trabajo, puerto 55432, sin contraseña). El backend local se arrancó con `DATABASE_URL`/`DIRECT_URL` apuntando a ese clúster. Antes de cada paso se comprobó en la salida que la conexión iba a `localhost:55432`. Contra producción solo se hicieron lecturas: el estado de las migraciones y las dos evaluaciones que leen recetas, después de comprobar que no escriben nada.

**Dos capas para los flujos.** La primera es un script (fuera del repositorio) que recorre la API como lo haría la aplicación y anota cada comprobación como correcta o fallida. Es repetible y cubre lo que la interfaz no deja ver, como el 401 con un token caducado o los códigos de error. La segunda es un recorrido por la interfaz en el emulador, con la aplicación apuntando temporalmente al backend local (`10.0.2.2:3007` y tráfico en claro). Las dos líneas cambiadas se restauraron con `git checkout` al terminar. El emulador se arrancó sin ventana y sin guardar su instantánea, para no alterar el estado con el que lo usa el autor. En ese modo `screencap` devuelve imágenes en blanco, así que la interfaz se inspeccionó con el árbol de accesibilidad (`uiautomator dump`): textos, descripciones y posiciones de cada elemento. Que los estados de pantalla sean legibles así es también una comprobación de accesibilidad.

**El backend local sin Brevo.** El servidor se arrancó con `BREVO_API_KEY` vacía y `NODE_ENV=development`. Así no se envía ningún correo real, y el enlace de verificación y el código de recuperación se escriben en el log (informe 14). El script los lee de ahí.

## Cómo funciona

### 1. Pruebas y compilación

Las 123 pruebas del backend pasaban al empezar. La aplicación compila en limpio (`clean assembleDebug assembleDebugAndroidTest testDebugUnitTest`), también el APK de pruebas instrumentadas. Las pruebas instrumentadas se ejecutaron en el emulador: 3, sin fallos, con una omitida. La omitida es `OcrRecognitionEvalTest`, que se salta sola con `assumeTrue` cuando `androidTest/assets/ocr_eval/` no tiene fotos. Para que la ejecución no desinstalara la aplicación (y cerrara la sesión) se usó `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`.

### 2. Migraciones y datos de partida

Sobre la base de datos vacía, `prisma migrate deploy` aplica las nueve migraciones en orden y sin conflictos: desde `init` hasta `notifications`. `prisma migrate status` dice que la base está al día y `prisma migrate diff` entre la base migrada y `schema.prisma` no encuentra diferencias (código de salida 0). No hay migraciones huérfanas ni cambios del esquema sin migración. En producción, `migrate status` (solo lectura) confirma las mismas nueve migraciones aplicadas.

`prisma/seed.js` carga los 953 alimentos de BEDCA y `prisma/seedDemoData.js` crea 14 usuarios, 31 recetas y 160 "me gusta". Al ejecutarlo por segunda vez no crea nada nuevo, así que sigue siendo idempotente con el esquema actual.

### 3. Flujos de extremo a extremo

El script comprueba 54 puntos:

- **Autenticación:** registro, login con email sin verificar (403 `EMAIL_NOT_VERIFIED`), enlace de verificación de un solo uso, recuperación de contraseña con código incorrecto y correcto, y login con la contraseña antigua y con la nueva.
- **Perfil:** vacío, completo con objetivo calórico y fuera de rango.
- **Recetas:** buscador de alimentos; receta creada con nutrición, propuesta de OCR con tiempo estimado, edición y "Mis recetas".
- **Diario:** receta y alimento suelto, macros por entrada, objetivos de kcal y macros, edición de la cantidad con recálculo, calendario del mes con el estado del día, recomendaciones y borrado.
- **Despensa:** añadir, duplicado (409), recetas con cobertura completa, quitar un ingrediente y que la receta pase a "casi".
- **Social:** Explorar por orden y por texto, Amigos vacío y con recetas, me gusta, comentario, guardar y quitar de guardadas, y buscador de personas.
- **Notificaciones:** follow, me gusta y comentario, contador y `read-all`.
- **Borrar una receta:** aviso de una entrada huérfana en el diario, que se conserva sin receta.
- **Cuenta y sesión:** token caducado, borrar la cuenta con contraseña incorrecta y correcta, y uso del token de la cuenta borrada.

La primera ejecución dio 52 de 54. Uno de los dos fallos era del propio script, que exigía un recálculo exacto cuando hay redondeo de 1 kcal. El otro era un fallo real, el primero de la lista siguiente.

**Fallo 1: una cuenta borrada no cerraba la sesión.** `requireAuth` solo comprobaba la firma del JWT. Si la cuenta se borraba desde otro dispositivo, los tokens que ya se habían entregado seguían siendo válidos durante sus 7 días. Con ellos, `GET /recipes/mine`, `/recipes/search`, `/notifications` y `/pantry` respondían 200 con listas vacías, `/profile` y `/log` respondían 404, y las rutas que escriben habrían fallado con un 500 por la clave ajena. La aplicación solo cierra la sesión ante un 401 (`AuthInterceptor`), así que el usuario se quedaba dentro de una cuenta que ya no existe. Ahora `requireAuth` busca además al usuario por su clave primaria y, si ya no existe, responde 401 "Tu cuenta ya no existe". Es una consulta más por petición, por clave primaria. La prueba de `DELETE /auth/me` de `security.test.js` comprueba ahora ese 401 (antes esperaba 404), y el informe 13 lleva una nota. En el emulador, al borrar la cuenta desde la API con la aplicación abierta, la siguiente pantalla que pide datos devuelve al login.

En la interfaz se recorrieron:

- **Login.**
- **Explorar:** el buscador desplegable, que se abre con el teclado, filtra y se cierra con la ×; el orden y las tarjetas.
- **Amigos:** el desplegable de la cabecera, Amigos vacío, el buscador de personas, seguir y Amigos con recetas.
- **Notificaciones:** la campana con "2 sin leer", la lista, tocar una notificación para abrir la receta y el punto que desaparece.
- **Perfil:** cabecera, "Mis recetas" con "Escanear" y el botón de nueva receta, "Guardadas" sin ellos, y "Mis datos".
- **Diario:** "Ir al perfil" lleva a "Mis datos"; objetivos de kcal y macros, añadir una recomendación, editar la cantidad y el calendario con el estado del día.
- **Despensa:** alimentos reconocidos y receta a la que le falta poco.
- **Tarjeta:** me gusta, guardar y seguir.
- **Cuenta borrada:** vuelta al login.

Así apareció el segundo fallo real.

**Fallo 2: los estados de carga, vacío y error de las listas salían descentrados y recortados.** En "Explorar", "Amigos", "Guardadas" y "Notificaciones", el estado a pantalla completa dentro de la lista usaba `Modifier.fillParentMaxSize(0.6f)`, con la idea de ocupar el 60 % del alto. Pero `fillParentMaxSize` aplica la fracción también al ancho. El mensaje ocupaba el 60 % izquierdo de la pantalla (en el árbol de accesibilidad, de x = 144 a x = 662 en una pantalla de 1280), y el texto, al partirse en más líneas, empujaba el botón fuera del hueco. En "Amigos" sin seguir a nadie, el botón "Buscar personas" no llegaba a verse, y es la única salida de ese estado. Se ha sustituido en los ocho sitios por `listStateModifier()` (en `RecipeComponents.kt`): todo el ancho y una altura *mínima* de 400 dp, no fija, para que el contenido crezca en lugar de recortarse. Después del cambio, el mensaje queda centrado (x = 640) y el botón aparece.

### 4. Código muerto

- **`FeedViewModel`** aún cargaba por defecto el feed global (`GET /recipes/feed`) y tenía un parámetro `loadOnInit`. Desde el informe 19 nadie lo instancia directamente y sus tres subclases (Explorar, Amigos y Guardadas) pasaban `false`. Ahora es `abstract`, con `fetchPage` abstracto y sin `loadOnInit`. Con ello desaparecen `FeedRepository.getFeed` y `ApiService.getFeed`.
- **`SessionManager.isLoggedIn`**, un `Flow` que no leía nadie.
- **`ExampleUnitTest` y `ExampleInstrumentedTest`**, las plantillas de Android Studio (`2 + 2 == 4`).
- **Dos comentarios desfasados:** el de los pasos en `schema.prisma`, que decía que se guardan como JSON "porque SQLite no tiene tipo lista" cuando la base es PostgreSQL, y el de `FeedRecipe`, que remitía a `GET /recipes/feed`.

Se buscaron referencias a `HomeRoutes.RECETAS`, `RecipesTab.EXPLORE`, `FeedHeader` y los parámetros eliminados en rondas anteriores, y no queda ninguna. Un script que cuenta cuántas veces aparece cada declaración en todo el código dio más candidatos, pero todos eran falsos positivos: sobrescrituras del framework, clases declaradas en el manifiesto, pruebas o campos de respuestas JSON.

**Se ha conservado a propósito `GET /recipes/feed` en el backend.** La aplicación actual ya no lo llama (el feed global es "Explorar" sin texto), pero una versión anterior de la aplicación instalada en un móvil sí. Además está documentado (informes 12 y 15), tiene pruebas y comparte la paginación con el feed de amigos. Lo mismo ocurre con `GET /foods/units` (informe 04).

### 5. Coherencia visual y de estados

- **Carga:** todas las pantallas usan el mismo indicador circular (`LoadingBox` o su equivalente). Los indicadores pequeños quedan para acciones en curso dentro de un botón o un campo, y la barra lineal para refrescar con resultados ya en pantalla.
- **Vacíos y errores:** todas las listas usan `CenteredMessage` o `InlineEmptyState` (icono y texto), con "Reintentar" en los errores.
- **La excepción era el selector de recetas de la hoja "Añadir" del diario.** La carga era un texto sin indicador, el error no tenía botón para reintentar y el aviso de lista vacía mandaba a crear la receta "en «Mis recetas»", que desde el informe 19 ya no es una pestaña. Ahora la carga tiene su indicador, el error tiene "Reintentar" (nueva acción `onRetryRecipes`, que vuelve a pedir "Mis recetas") y el aviso vacío es un `InlineEmptyState` que dice "Crea una desde Perfil › «Mis recetas»".
- **Descripciones de iconos:** ningún `IconButton` tiene un icono sin `contentDescription`.

### 6. Evaluaciones

`eval:despensa` y `eval:recomendador` dan cifras algo distintas de las guardadas. La causa es que producción tiene ahora 33 recetas en vez de 32. Con una receta más, la misma semilla (2026) genera otra secuencia de simulaciones. Para distinguir un cambio de lógica de la variación de muestreo, la evaluación de la despensa se repitió con cuatro semillas más:

- **Acierto en primera posición con el 60 % de cobertura:** oscila entre el 53 % y el 77 % según la semilla.
- **MRR global:** entre 0,69 y 0,75.

El MRR global nuevo (0,738) es prácticamente el guardado (0,741), y en el recomendador la reducción de la desviación pasa del 18,9 % al 19,1 %. No son cambios relevantes, así que se han conservado los resultados guardados, que son los que cita el informe 17.

### 7. Memoria

Todos los commits con funcionalidad tienen su informe. Los que no lo tienen son de infraestructura (`.gitattributes`, arreglos de despliegue) o están explicados dentro de otro informe (el orden del diario de `eb42c08`, en el 18; el rediseño visual, en el 14).

## Limitaciones

- **La interfaz se ha comprobado con el árbol de accesibilidad, no con capturas.** El emulador sin ventana no produce imágenes, así que problemas puramente visuales (colores, solapes que no cambian las posiciones, animaciones) no se habrían detectado. El fallo de los estados descentrados sí se vio, precisamente por sus coordenadas.
- **No se ha recorrido todo por la interfaz.** El registro, la verificación, la recuperación de contraseña, crear y editar recetas con foto y por OCR con la cámara, y borrar la cuenta se comprobaron por la API; en el emulador solo se abrió el formulario. El escaneo real depende de una foto y de ML Kit, y su evaluación está pendiente de las fotos del autor (informes 17 y 20).
- **Las evaluaciones varían bastante con la semilla.** Con unas 30 recetas, el acierto por franja de cobertura depende mucho de qué despensas salgan. Para citar cifras estables haría falta promediar varias semillas o tener más recetas.
- **Una consulta más en cada petición.** Comprobar que el usuario existe cuesta una lectura por clave primaria. Con Neon en otra región son unos milisegundos por petición; a cambio, una sesión de una cuenta borrada ya no puede quedarse abierta.

## Archivos creados o modificados

| Archivo | Cambio |
| --- | --- |
| `backend/src/middleware/auth.js` (modificado) | `requireAuth` comprueba que el usuario del token siga existiendo; si no, 401. |
| `backend/test/security.test.js` (modificado) | El token de una cuenta borrada da 401. |
| `backend/prisma/schema.prisma` (modificado) | Comentario de los pasos al día. |
| `app/.../ui/recipes/RecipeComponents.kt` (modificado) | `listStateModifier()` para los estados de carga, vacío y error dentro de listas. |
| `app/.../ui/explore/ExploreContent.kt`, `ui/home/HomeTabScreen.kt`, `ui/notifications/NotificationsScreen.kt` (modificados) | `listStateModifier()` en lugar de `fillParentMaxSize(0.6f)`. |
| `app/.../ui/log/AddEntrySheet.kt` (modificado) | Carga con indicador, error con "Reintentar" y aviso vacío con icono y el camino nuevo a "Mis recetas". |
| `app/.../ui/home/HomeScreen.kt` (modificado) | `onRetryRecipes` en la hoja del diario. |
| `app/.../ui/home/FeedViewModel.kt` (modificado) | Clase abstracta, sin carga por defecto del feed global ni `loadOnInit`. |
| `app/.../ui/explore/*ViewModel.kt` (modificados) | Sin `loadOnInit`. |
| `app/.../data/FeedRepository.kt`, `ApiService.kt` (modificados) | Sin `getFeed`. |
| `app/.../data/SessionManager.kt` (modificado) | Sin `isLoggedIn`. |
| `app/.../data/RecipeModels.kt` (modificado) | Comentario de `FeedRecipe` al día. |
| `app/src/test/.../ExampleUnitTest.kt`, `app/src/androidTest/.../ExampleInstrumentedTest.kt` (borrados) | Plantillas de Android Studio sin contenido. |
| `docs/memoria/13-robustez-y-seguridad.md` (modificado) | Nota sobre el 401 con el token de una cuenta borrada. |
