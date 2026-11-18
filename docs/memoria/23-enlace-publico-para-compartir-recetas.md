# 23. Enlace público para compartir recetas

## Objetivo y problema

El botón "Compartir" de la tarjeta de receta (informe 15) abría el selector del sistema con un único texto: "Mira esta receta en NutriSocial: <título>". No había enlace, porque no existía ninguna dirección que se pudiera abrir sin la aplicación. La única forma de leer una receta era `GET /recipes/:id`, y todo el router de recetas está detrás de `requireAuth` (informe 02). Un enlace a esa ruta habría devuelto un `401` en JSON a cualquiera que lo abriese en el navegador.

Compartir solo el título sirve de poco: quien lo recibe tiene que instalar la aplicación, crearse una cuenta y buscar la receta por su nombre. El objetivo de esta iteración es que compartir dé un **enlace real**, que cualquiera pueda abrir en el navegador del móvil o del ordenador sin la aplicación y sin iniciar sesión, y que enseñe la receta completa: autor, raciones, valores nutricionales por ración, ingredientes y pasos.

## Decisiones técnicas

**Un router propio y separado, montado en `/share`.** La ruta pública no se ha añadido al router de recetas con una excepción a `requireAuth`, sino en un archivo nuevo, `src/routes/share.js`, montado en `index.js` con `app.use('/share', …)`. Así queda claro, con solo leer `index.js`, que `/share` es la **única zona de la API sin autenticación**, y no hay riesgo de que una ruta nueva de `/recipes` quede pública por estar en el lado equivocado de un `router.use(requireAuth)`. Además, el router solo tiene una ruta `GET`: no puede modificar nada.

**HTML generado en el servidor, sin plantillas ni JavaScript.** La página se abre directamente en un navegador, así que la respuesta es HTML y no JSON, también en el caso de error. Se construye con plantillas de texto de JavaScript, como las páginas de verificación de correo del informe 14, sin añadir un motor de plantillas como dependencia. El CSS va en línea en un `<style>`, y la página no carga ningún recurso externo ni ejecuta ningún script. Es una página que solo se lee, y así funciona igual en cualquier navegador.

**Escapar todo lo que escriben los usuarios.** El título, el nombre del autor, la descripción, los nombres y unidades de los ingredientes y los pasos son texto libre de los usuarios. Si se metiesen tal cual en el HTML, una receta titulada `<script>…</script>` ejecutaría código en el navegador de quien abriese el enlace: una inyección de código (*cross-site scripting*, XSS) almacenada. Todos esos valores pasan por `escapeHtml`, que sustituye `& < > " '` por sus entidades. Esa función ya existía en `src/email/brevo.js` para los correos. Para usarla desde dos sitios se ha movido a `src/utils/html.js`, y `brevo.js` la importa de ahí.

**Una segunda barrera: Content-Security-Policy.** La respuesta lleva la cabecera `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'`. Le dice al navegador que la página no puede ejecutar scripts ni cargar imágenes, fuentes o marcos de ningún sitio, y que solo se admite el CSS en línea. Si por un error futuro algún campo se colase sin escapar, el navegador no ejecutaría lo inyectado. El escape es la defensa principal; la cabecera limita el daño si falla.

**Pedir a la base de datos solo lo que se enseña.** `GET /recipes/:id` usa `include` y trae, entre otras cosas, la foto en Base64 y el estado social visto por el usuario. La ruta pública usa en su lugar un `select` explícito, con los campos de la receta que se muestran y del autor **solo el nombre**: `author: { select: { name: true } }`. En una ruta que cualquiera puede llamar, no basta con no pintar el email o el hash de la contraseña: no deben salir de la base de datos. Los valores por ración se calculan con `nutritionSummary`, la misma función que usa el detalle de la aplicación (informe 04), para que la página y la app den las mismas cifras.

**404 en HTML para recetas que no existen.** Si el id no existe (por ejemplo, porque el autor borró la receta) o no es un entero positivo, la ruta responde una página mínima de "Receta no encontrada" con estado `404`. Un id no válido ni siquiera llega a consultar la base de datos.

**La foto no se incluye.** Las fotos se guardan en Base64 dentro de la base de datos (informe 12) y pueden ocupar unos 2 MB. Meterlas en la página la haría mucho más pesada.

**La URL pública sale de la misma constante que la API.** En la aplicación, `RetrofitClient.kt` tenía la URL del backend en una constante privada, `BASE_URL`. Se ha añadido `WEB_BASE_URL`, pública e igual a `BASE_URL`, en lugar de escribir la dirección de Render otra vez en la tarjeta. Si el backend cambia de dirección, el enlace compartido cambia con él.

## Cómo funciona

### Backend

Al abrir `https://nutrisocial.onrender.com/share/recipes/42`, Express dirige la petición al router de `share.js`. Primero fija la cabecera CSP y el tipo `text/html`. Después convierte el parámetro en número y, si es un entero positivo, busca la receta con `prisma.recipe.findUnique` y el `select` descrito.

Si no la encuentra, responde `404` con la página de "Receta no encontrada". Si la encuentra, compone la página en este orden:

1. El título como encabezado.
2. Una línea con "Por <autor> · 4 raciones · 40 min".
3. La descripción, si la hay.
4. Cuatro tarjetas con las kcal, la proteína, los hidratos y la grasa **por ración**.
5. La lista de ingredientes con su cantidad y unidad ("sal — 0,5 pizca"), en el orden en que los escribió el autor.
6. Los pasos en una lista numerada.

Las cantidades se redondean a dos decimales y se escriben con coma decimal. Cada valor escrito por un usuario pasa por `escapeHtml` justo al insertarlo en la plantilla. La página tiene `lang="es"` y la etiqueta `viewport`, así que se lee bien en un móvil sin ampliar.

### Aplicación Android

En `RecipeFeedCard.kt`, el botón "Compartir" construye el mismo `Intent.ACTION_SEND` de tipo `text/plain`, pero el texto lleva ahora el enlace en una segunda línea:

```kotlin
"Mira esta receta en NutriSocial: ${recipe.title}\n${WEB_BASE_URL}share/recipes/${recipe.id}"
```

La tarjeta es el mismo componente en el feed, el buscador, las guardadas y el detalle (informes 15 y 16), así que el enlace aparece en todos esos sitios con un solo cambio. Las aplicaciones de mensajería convierten la URL en un enlace que se puede tocar, y al abrirlo se ve la receta en el navegador.

### Pruebas

`test/share.test.js` añade 5 pruebas. Montan solo el router de `/share` sobre un Prisma simulado y hacen peticiones HTTP reales **sin cabecera de autenticación**. La receta de prueba tiene HTML malicioso en el título (`<script>`), en el nombre del autor (`"><img src=x onerror=…>`), en un ingrediente y en un paso. Las pruebas comprueban:

- Que `escapeHtml` escapa los cinco caracteres especiales y no falla con `null`.
- Que la ruta responde `200` con `text/html` y la cabecera CSP, y que calcula bien los valores por ración y el formato de las cantidades.
- Que no aparece ningún `<script>`, `<img>` ni `<i>` en la página, y que el texto malicioso sale escapado.
- Que del autor solo se pide el nombre y que no se pide la foto.
- Que un id inexistente, uno no numérico y uno negativo dan `404` en HTML, y que los no válidos no consultan la base de datos.

Con ellas, el backend tiene 128 pruebas y pasan todas. La aplicación compila (`compileDebugKotlin`).

## Limitaciones

- **Todas las recetas son públicas.** Los ids son números correlativos, así que cualquiera puede leer todas las recetas probando `/share/recipes/1`, `2`, `3`… sin compartir nada. Para NutriSocial es aceptable, porque el feed ya enseña todas las recetas a cualquier usuario registrado y no hay recetas privadas. Si algún día las hubiera, habría que compartir un identificador aleatorio no adivinable en lugar del id, o comprobar la visibilidad en esta ruta.
- **Sin vista previa en las aplicaciones de mensajería.** La página no tiene etiquetas Open Graph (`og:title`, `og:description`, `og:image`), así que WhatsApp o Telegram muestran el enlace sin tarjeta con título e imagen.
- **Sin foto.** La página no enseña la foto de la receta (ver *Decisiones técnicas*). Para incluirla sin hacer pesada la página, haría falta una ruta aparte que sirva la imagen como archivo, lo que además permitiría usarla en `og:image`.
- **El enlace no abre la aplicación.** Aunque quien lo recibe tenga NutriSocial instalada, el enlace se abre en el navegador. Para abrir la receta en la aplicación harían falta *App Links* de Android: un filtro de intents en el manifiesto y el archivo `assetlinks.json` publicado en el dominio.
- **Primera apertura lenta.** El plan gratuito de Render duerme el servidor tras 15 minutos sin uso (informe 06). Si nadie lo ha usado hace rato, quien abra el enlace puede esperar entre 30 y 60 segundos antes de ver la página.
- **Sin límite de peticiones.** El límite de intentos del informe 13 protege el login y el registro, pero no esta ruta. Cada apertura es una consulta a la base de datos, sin caché.
- **Enlaces a localhost al probar en local.** `WEB_BASE_URL` sigue a `BASE_URL`. Si se cambia temporalmente para probar la aplicación contra un backend local, los enlaces compartidos durante ese tiempo apuntan a una dirección que solo existe en ese ordenador.
- **Sin prueba contra producción.** La ruta se ha probado con un Prisma simulado. Hasta que se despliegue en Render no se ha comprobado con recetas reales ni abriendo el enlace desde otro móvil.

## Archivos creados o modificados

| Archivo | Cambio |
| --- | --- |
| `backend/src/routes/share.js` (nuevo) | `GET /share/recipes/:id`: página HTML pública de una receta y 404 en HTML, con CSP y `select` mínimo. |
| `backend/src/utils/html.js` (nuevo) | `escapeHtml`, movida desde `email/brevo.js` para compartirla. |
| `backend/src/email/brevo.js` (modificado) | Importa `escapeHtml` de `utils/html.js`. |
| `backend/src/index.js` (modificado) | Monta `/share`, la única zona de la API sin `requireAuth`. |
| `backend/test/share.test.js` (nuevo) | 5 pruebas: acceso sin sesión, escape, campos pedidos y 404. |
| `app/.../RetrofitClient.kt` (modificado) | Constante pública `WEB_BASE_URL`. |
| `app/.../ui/recipes/RecipeFeedCard.kt` (modificado) | "Compartir" envía el título y el enlace a la página pública. |
