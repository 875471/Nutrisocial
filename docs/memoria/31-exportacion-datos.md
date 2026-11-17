# 31. Exportación de los datos de la cuenta

## Objetivo y problema

NutriSocial guarda datos personales de cada usuario: perfil físico (fecha de nacimiento, altura, peso, sexo), un diario de lo que come, la despensa, recetas con fotos y la actividad social. La aplicación ya permitía borrar la cuenta (informe 14), pero no había forma de llevarse esos datos. El Reglamento General de Protección de Datos reconoce el derecho de acceso (art. 15) y el de portabilidad (art. 20): recibir los datos propios en un formato estructurado, de uso común y legible por máquina. El objetivo es un botón «Descargar mis datos» en el Perfil que entregue todo en un archivo JSON.

## Decisiones técnicas

**JSON y un único endpoint.** `GET /profile/export` devuelve un único documento JSON. Es el formato que ya usa toda la API, se lee con cualquier lenguaje y cumple lo de «estructurado y legible por máquina» sin más trabajo. Exportar también a CSV o PDF no aporta nada para el alcance del TFG.

**Lista explícita de campos, nunca la fila entera.** `User` tiene la contraseña hasheada, los hashes de los códigos de verificación y de recuperación, y ahora `isAdmin`. Si la exportación copiara las filas tal cual (`...user`), esos campos saldrían en el archivo, y un campo sensible que se añadiera en el futuro saldría también sin que nadie lo decidiera. Por eso `src/account/export.js` pide a Prisma solo las columnas necesarias (`USER_SELECT`, `RECIPE_SELECT`…) y compone el documento campo a campo en una función pura, `buildAccountExport`. La prueba comprueba que ni los nombres de esos campos ni el hash aparecen en el texto de la respuesta.

**Qué se exporta.** Lo que el usuario ha creado o registrado: perfil, suscripción, recetas propias (ingredientes con el alimento de referencia asociado, pasos, totales nutricionales, etiquetas, foto en Base64), entradas del diario, despensa, comentarios propios (con el título de la receta), recetas a las que dio «me gusta», recetas guardadas, a quién sigue y quién le sigue, y la lista de la compra. De las otras personas solo sale el nombre: sus datos no son del usuario que exporta. No se exportan las notificaciones, que describen acciones de otros y caducan en la práctica.

**Las fotos van dentro.** Las fotos de las recetas se guardan en Base64 en la base de datos (informe 12), y forman parte de los datos del usuario. Incluirlas hace que el archivo pueda pesar varios megas, así que la aplicación descarga la respuesta con `@Streaming`: Retrofit no la carga entera en memoria, sino que se copia a trozos al archivo de destino.

**Descarga como archivo.** La respuesta lleva `Content-Disposition: attachment; filename="nutrisocial-mis-datos-AAAA-MM-DD.json"` y `Cache-Control: no-store`, para que ningún proxy guarde una copia de datos personales. Como la consulta es pesada (diez consultas en paralelo y las fotos), la ruta tiene un límite de 5 peticiones cada 15 minutos por conexión, con el mismo limitador que la autenticación.

**Guardar con el selector de documentos del sistema.** En Android, el botón abre `ActivityResultContracts.CreateDocument("application/json")`: el usuario elige dónde guardar el archivo (Descargas, Drive…) y la aplicación recibe una `Uri` en la que escribir. No hace falta pedir permisos de almacenamiento, que en las versiones recientes de Android están muy restringidos. Y desde el selector se puede guardar directamente en Drive, lo que cubre también el caso de «compartirlo».

**El ViewModel no conoce el `Context`.** `ProfileViewModel.exportData` recibe una función que abre el flujo de salida (`() -> OutputStream?`). La pantalla la construye con `contentResolver.openOutputStream(uri)`. Así el ViewModel sigue sin depender de Android y se puede probar con un flujo en memoria.

## Cómo funciona

En «Mis datos» (Perfil), antes de «Cerrar sesión», hay una tarjeta «Tus datos» con una línea que explica qué se descarga y el botón «Descargar mis datos». Al pulsarlo, el sistema pregunta dónde guardar `nutrisocial-mis-datos-AAAA-MM-DD.json`. Si se cancela, no pasa nada. Si se elige un sitio, el botón pasa a «Descargando…» con un indicador. El repositorio llama a `GET /profile/export`, copia el cuerpo al archivo y devuelve los bytes escritos. Al terminar, un snackbar dice «Tus datos se han guardado (5,6 KB)» o el error correspondiente (sin conexión, demasiadas peticiones, no se pudo escribir el archivo).

El documento empieza con `format: "nutrisocial-account-export"`, `version: 1` y `exportedAt`, para que una herramienta que lo lea sepa qué tiene delante. Si el formato cambia en el futuro, se sube la versión. Después vienen `profile`, `subscription`, `recipes`, `logEntries`, `pantry`, `comments`, `likedRecipes`, `savedRecipes`, `following`, `followers` y `shoppingList`.

`test/accountExport.test.js` tiene 3 pruebas. La principal lanza la ruta real con un doble de Prisma con dos usuarios cuyas consultas filtran por el `where` recibido. Comprueba la cabecera de descarga, el formato, que todos los apartados traen los datos de Ana y ninguno de Luis, y que no aparecen la contraseña ni los tokens. Las otras dos comprueban el `401` sin sesión y los casos raros de `buildAccountExport` (pasos guardados como texto suelto, suscripción con solo los campos públicos). Además se ejecutó `loadAccountExport` contra la base de datos real (solo lectura) con una cuenta de demostración, para comprobar que las diez consultas son válidas en PostgreSQL. En la aplicación, `DataExportTest` comprueba el formato del tamaño del aviso. Las 168 pruebas del backend y las unitarias de la aplicación pasan.

## Limitaciones

- **Solo exportar, no importar.** No hay forma de cargar el archivo en otra cuenta de NutriSocial. Para la portabilidad hacia otro servicio basta con el JSON, pero restaurar una cuenta exigiría una ruta de importación con validación.
- **Sin descarga diferida.** El archivo se genera en la misma petición. Con cientos de recetas con foto podría superar el tiempo máximo de respuesta de Render; un servicio grande prepararía el archivo en segundo plano y avisaría al usuario cuando estuviera listo.
- **Fotos en Base64.** Es como se guardan, pero ocupan un tercio más que la imagen binaria. Un ZIP con las fotos como archivos JPEG sería más cómodo de usar, a cambio de más código.
- **Sin aviso por correo.** Un servicio comercial suele notificar por email cada exportación, por si alguien con el móvil desbloqueado la pidiera sin permiso.

## Archivos creados o modificados

- `backend/src/account/export.js` (nuevo): consultas y composición del documento.
- `backend/src/routes/profile.js`: `GET /profile/export` con su limitador.
- `backend/test/accountExport.test.js` (nuevo).
- `app/.../ApiService.kt`: `exportAccount` con `@Streaming`.
- `app/.../data/ProfileRepository.kt`: `exportAccount`, que copia la respuesta al archivo.
- `app/.../ui/home/ProfileViewModel.kt`: `exportData`, `isExporting` y `formatSize`.
- `app/.../ui/home/ProfileScreen.kt`: tarjeta «Tus datos» con el botón y el selector de documentos.
- `app/.../ui/home/HomeScreen.kt`: conexión de la acción.
- `app/src/test/.../DataExportTest.kt` (nuevo).
- `docs/anexo-b-documentacion-api-rest.md`: ruta de exportación.
