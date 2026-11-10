# Anexo B. Documentación de la API REST

Todas las rutas se sirven bajo la URL base del backend desplegado en Render y devuelven JSON. Salvo las marcadas como públicas, todas requieren la cabecera `Authorization: Bearer <token>` con el JWT obtenido en el login. Los códigos de error siguen el estándar HTTP (400 datos inválidos, 401 no autenticado, 403 no autorizado, 404 no encontrado, 429 límite de peticiones excedido, 502 fallo de un servicio externo).

## B.1. /auth — Autenticación

| Método y ruta | Acceso | Descripción |
| --- | --- | --- |
| POST /auth/register | Pública | Crea una cuenta y envía correo de verificación. Limitada por IP. |
| POST /auth/login | Pública | Valida credenciales y devuelve un JWT válido 7 días. Limitada por IP. |
| GET /auth/verify | Pública (token) | Verifica el correo a partir del token recibido por email. |
| POST /auth/resend-verification | Pública | Reenvía el correo de verificación. Limitada por IP. |
| POST /auth/forgot-password | Pública | Envía un correo con un código de recuperación de un solo uso. Limitada por IP. |
| POST /auth/reset-password | Pública (código) | Cambia la contraseña a partir del código recibido. Limitada por IP. |
| DELETE /auth/me | Privada | Borra la cuenta autenticada y todo su contenido en cascada. Limitada por IP. |

**Tabla B.1.** *Rutas de autenticación.*

## B.2. /recipes — Recetas

| Método y ruta | Descripción |
| --- | --- |
| POST /recipes | Crea una receta con ingredientes estructurados; calcula y guarda los macronutrientes totales. |
| POST /recipes/parse-ocr | Estructura un texto bruto reconocido por OCR en título, ingredientes y pasos. |
| POST /recipes/generate | Genera una propuesta de receta (sin guardarla) a partir de una lista de ingredientes, usando un modelo de lenguaje; los macronutrientes se calculan con el mismo cálculo que una receta normal, nunca por el modelo. Limitada a 10 peticiones cada 15 minutos. |
| GET /recipes/mine | Lista las recetas publicadas por la persona autenticada. |
| GET /recipes/feed | Feed general de recetas, paginado. |
| GET /recipes/feed/friends | Feed de recetas de las personas seguidas. |
| GET /recipes/search | Búsqueda de recetas por texto. |
| GET /recipes/saved | Recetas guardadas por la persona autenticada. |
| GET /recipes/by-pantry | Recetas ordenadas por ajuste a la despensa actual, con sustitutos entre ingredientes afines (núcleo 3.3). |
| GET /recipes/:id | Detalle de una receta. |
| PUT /recipes/:id | Edita una receta propia; recalcula los macronutrientes. |
| DELETE /recipes/:id | Borra una receta propia. |
| PUT /recipes/:id/image | Sustituye la fotografía de una receta propia. |
| POST, DELETE /recipes/:id/like | Da o retira un «me gusta»; genera notificación al autor. |
| POST, DELETE /recipes/:id/save | Guarda o quita una receta de la lista personal. |
| GET, POST /recipes/:id/comments | Lista o añade comentarios; al añadir, genera notificación al autor. |
| POST /recipes/:id/share | Comparte una receta con una persona a la que se sigue; genera una notificación que enlaza a la receta. |

**Tabla B.2.** *Rutas de recetas (incluye OCR, generación por IA, feed, búsqueda por despensa y compartir).*

## B.3. Otros grupos de rutas

| Prefijo | Rutas principales | Descripción |
| --- | --- | --- |
| /foods | GET /search, GET /units | Búsqueda de alimentos de referencia y catálogo de unidades admitidas. |
| /profile | GET, PUT / | Lectura y actualización del perfil físico y objetivo nutricional. |
| /log | GET, POST /, PUT/DELETE /:id, GET /calendar, GET /recommendations | Diario nutricional: altas, ediciones, consulta por calendario y recomendaciones del día (núcleo 3.4). |
| /pantry | GET, POST /, DELETE /:id | Alta, baja y consulta de la despensa personal. |
| /comments | DELETE /:id | Borrado de un comentario propio. |
| /users | GET /search, POST/DELETE /:id/follow, GET /following | Búsqueda de personas, gestión de seguimiento y lista de personas a las que se sigue (para elegir con quién compartir una receta). |
| /notifications | GET /, POST /read-all | Notificaciones no leídas y su marcado como leídas. |

**Tabla B.3.** *Resto de la API: alimentos, perfil, diario, despensa y capa social.*

## B.4. Ejemplos de petición y respuesta

Para ilustrar el formato exacto que usa la API, se incluyen cinco ejemplos representativos: autenticación, el núcleo de OCR, la creación de una receta con cálculo de macronutrientes, el buscador por despensa y la generación de recetas con IA. El resto de rutas sigue el mismo estilo: JSON en el cuerpo de la petición y la respuesta, y los mismos nombres de campo que las entidades del apartado 3.1.2.

### POST /auth/login

Petición:

| Campo | Tipo | Ejemplo |
| --- | --- | --- |
| email | string | "persona@example.com" |
| password | string | "********" |

Respuesta (200):

| Campo | Tipo | Descripción |
| --- | --- | --- |
| token | string | JWT firmado, válido 7 días. |
| user | object | { id, name, email, emailVerified }. |

### POST /recipes/parse-ocr

Petición: `{ "rawText": "Lentejas con verduras\n300 g de lentejas\n150 g de cebolla\n…\n1. Poner las lentejas en remojo…" }` — el texto bruto reconocido por Google ML Kit en el dispositivo, con un salto de línea por cada línea detectada.

Respuesta (200):

| Campo | Tipo | Descripción |
| --- | --- | --- |
| title | string \| null | Título detectado, si lo hay. |
| ingredients | array | Lista de { name, quantity, unit, lineType }; `lineType` distingue ingredientes normales de continuaciones (apartado 3.4, núcleo 3.1). |
| steps | array de string | Pasos de preparación segmentados. |

### POST /recipes

Petición (resumida): `{ "title": "Lentejas con verduras", "servings": 4, "steps": […], "ingredients": [ { "name": "lentejas", "quantity": 300, "unit": "g" }, { "name": "cebolla", "quantity": 150, "unit": "g" }, … ] }`.

Respuesta (201): la receta creada, con `ingredients[].foodId` y `ingredients[].grams` ya resueltos por `matchFood` (apartado 3.3), y `kcal`, `protein`, `carbs`, `fat` calculados para el total de la receta.

### GET /recipes/by-pantry

Respuesta (200): lista de recetas ordenada por ajuste a la despensa, cada una con `coverage` (fracción de ingredientes disponibles), `missingIngredients` (los que faltan), `substitutions` (ingredientes cubiertos por un sustituto de la despensa, por ejemplo margarina por mantequilla) y los campos habituales de receta — la información que consume directamente la pantalla «Recetas que puedo cocinar» (anexo A.3).

### POST /recipes/generate

Petición: `{ "ingredients": ["pollo", "arroz", "pimiento"] }` — hasta 30 nombres de ingrediente, tal como los escribe la persona usuaria; el modelo puede suponer sal, aceite, agua y especias comunes.

Respuesta (200): `title`, `servings` y `steps` propuestos por el modelo; `ingredients[]` ya resueltos contra BEDCA/Open Food Facts igual que en una receta manual (nombre, cantidad, unidad, `foodId`, gramos); el bloque `nutrition` habitual (totales, por ración, peso e ingredientes no contados); y `aiGenerated: true`. No escribe nada en la base de datos: si la propuesta gusta, se guarda con `POST /recipes` añadiendo `"aiGenerated": true` al cuerpo. Responde `502` si el modelo falla o da una respuesta inválida, y `503` si el servidor no tiene configurada la clave de la API.
