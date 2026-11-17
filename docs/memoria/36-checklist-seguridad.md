# 36. Revisión de seguridad frente a OWASP API Security Top 10

## Objetivo y problema

La seguridad del backend se había ido construyendo por partes: autenticación con JWT y bcrypt (informe 02), límites de intentos y comprobación de autoría (informe 13), verificación de email y recuperación de contraseña (informe 14). Faltaba repasarla entera contra una referencia reconocida, para saber qué está cubierto, qué no y por qué. La referencia es la **OWASP API Security Top 10 (edición 2023)**, la lista de los diez riesgos más frecuentes en APIs. El repaso no pretendía añadir funcionalidad. Solo se cambió código donde había algo claramente mal; se encontraron tres casos, descritos al final de cada punto y resumidos en «Correcciones hechas».

## Decisiones técnicas

**Repasar el código, no solo la configuración.** Cada punto se ha comprobado leyendo las rutas y los middleware (`src/routes/*`, `src/middleware/*`), no suponiendo. Cuando una mitigación ya tenía prueba automática, se cita; cuando se ha corregido algo, se ha añadido una prueba en `test/security.test.js`.

**Marcar con honestidad lo que queda fuera.** Varios riesgos solo se mitigan del todo con infraestructura que no tiene sentido en un TFG desplegado en el plan gratuito de Render (WAF, gestión de secretos rotables, registro centralizado). Se dejan anotados como fuera de alcance, con el motivo.

## Cómo funciona: repaso punto por punto

### API1 — Autorización rota a nivel de objeto (BOLA)

*Riesgo:* acceder a objetos de otro usuario cambiando el id de la URL.

**Mitigado.** Todas las rutas que leen o modifican datos personales filtran por el usuario del token, nunca por un id del cliente: el diario, la despensa y la lista de la compra usan `where: { id, userId: req.userId }` (`updateMany`/`deleteMany`), y un id ajeno da `404`, no `403`, para no confirmar que existe. Editar, borrar o cambiar la foto de una receta pasa por `findOwnRecipeId`, que exige ser el autor; borrar un comentario exige ser su autor. La exportación de datos (informe 31) solo carga filas del usuario autenticado. Pruebas: `security.test.js` (editar y borrar recetas ajenas), `shoppingList.test.js` (elementos ajenos dan `404`), `accountExport.test.js` (no salen datos de otro usuario).

### API2 — Autenticación rota

*Riesgo:* contraseñas débiles, fuerza bruta, tokens mal validados o robados.

**Mitigado en lo principal.** Las contraseñas se guardan con bcrypt (coste 10) y se exigen 8 caracteres como mínimo. El login tiene un límite de 10 intentos fallidos cada 15 minutos por IP (los correctos no cuentan), igual que el registro, la recuperación de contraseña y el borrado de cuenta. No se puede iniciar sesión sin verificar el email. Los mensajes de «he olvidado mi contraseña» y de reenvío de verificación son idénticos exista o no la cuenta, para no revelar qué emails están registrados. El token JWT caduca a los 7 días, se firma con un secreto que solo existe en el entorno de Render, y el servidor no arranca sin él. `requireAuth` comprueba además que el usuario del token sigue existiendo, así que una cuenta borrada no conserva sesiones válidas. Los códigos de verificación y de recuperación se guardan hasheados (SHA-256) y caducan.

*Corregido en esta revisión:* la aplicación guarda el token en DataStore, y `android:allowBackup="true"` hacía que el archivo entrase en la copia de seguridad en la nube de Android: restaurar la copia en otro móvil daba la sesión abierta. Las reglas `data_extraction_rules.xml` y `backup_rules.xml` excluyen ahora ese archivo; tras restaurar, se inicia sesión de nuevo.

*Fuera de alcance:* no hay revocación de tokens (cambiar la contraseña no cierra las otras sesiones hasta que caducan), ni segundo factor. La revocación exigiría guardar una versión de sesión por usuario y consultarla en cada petición.

### API3 — Autorización rota a nivel de propiedad (exposición excesiva y asignación masiva)

*Riesgo:* devolver campos que el cliente no debería ver, o dejar que el cliente escriba campos que no debería poder cambiar.

**Mitigado.** Las respuestas se construyen campo a campo (`toProfileResponse`, `toRecipeResponse`, `toFeedItem`, `toCommentResponse`, `buildAccountExport`) y las consultas de Prisma usan `select` explícitos: de otros usuarios solo se devuelve el id y el nombre, nunca el email. Una prueba (`security.test.js`) comprueba que ninguna respuesta de recetas o de login contiene el hash de la contraseña; otra (`accountExport.test.js`) que la exportación no contiene contraseñas ni tokens. En escritura, cada ruta copia solo los campos permitidos (`validateProfile` recorre una lista fija, `buildRecipeData` arma las columnas una a una), así que un cuerpo con `authorId`, `isAdmin`, `hidden` o `emailVerified` se ignora. `isAdmin` y la suscripción no se pueden modificar por ninguna ruta de usuario: el rol solo se asigna con `scripts/setAdmin.js`.

### API4 — Consumo de recursos sin restricción

*Riesgo:* peticiones enormes o masivas que agotan memoria, CPU o servicios de pago.

**Mitigado parcialmente.** El cuerpo JSON está limitado a 3 MB y la foto se valida a 2 MB reales y por su firma (JPEG, PNG, WebP). Todas las listas se paginan con un máximo por página (feed y comentarios 50, notificaciones su propio tope). La generación de recetas con IA, que cuesta dinero en cada llamada, tiene un límite de 10 cada 15 minutos; la exportación de datos, de 5.

*Corregido en esta revisión:* no había ningún límite general. Un script podía martillear el buscador o el buscador por despensa, que cargan en memoria todas las recetas (sin fotos) en cada petición. Ahora `createApiLimiter` limita toda la API a 300 peticiones por minuto, unas 5 por segundo sostenidas, muy por encima del uso normal de la aplicación. Cuenta **por usuario** cuando la petición trae un token válido y por IP cuando no, para que varias personas detrás de la misma IP (una red móvil con NAT) no compartan cupo. `/health` queda fuera porque lo usa Render. El valor se puede cambiar con `API_RATE_LIMIT_PER_MINUTE`.

*Fuera de alcance:* el límite se guarda en memoria del proceso; con varias instancias de Render habría que guardarlo en Redis. El buscador en memoria seguiría siendo un cuello de botella con miles de recetas (capítulo de escalabilidad).

### API5 — Autorización rota a nivel de función

*Riesgo:* que un usuario normal llame a funciones reservadas (administración, funciones de pago).

**Mitigado.** Las funciones Premium (OCR y recomendaciones del diario) pasan por `requirePremium`. La moderación de otros usuarios exige `isAdmin` (informe 38), que no se puede conceder por API. No hay rutas de administración aparte que pudieran quedar sin proteger.

*Corregido en esta revisión:* `requirePremium` solo miraba que la suscripción tuviera `status = 'active'`, no que `currentPeriodEnd` siguiera en el futuro. Como la suscripción simulada no se renueva sola, una suscripción mensual daba Premium para siempre. Ahora `isSubscriptionActive` comprueba las dos cosas, y lo usan tanto el middleware como `GET /subscription` (que deja de decir `isPremium: true` al caducar). Prueba en `security.test.js`.

### API6 — Acceso sin restricción a flujos de negocio sensibles

*Riesgo:* automatizar un flujo legítimo de forma abusiva (registros masivos, spam).

**Mitigado parcialmente.** El registro tiene límite por IP y exige verificar el email antes de usar la cuenta, lo que frena la creación masiva. Los «me gusta», guardados y seguimientos son idempotentes (`upsert`), así que repetirlos no infla contadores ni duplica notificaciones. Compartir una receta solo se puede hacer con alguien a quien se sigue, y repetir la misma no repite el aviso.

*Fuera de alcance:* no hay CAPTCHA ni detección de bots. Los comentarios no tienen límite propio, más allá del general; contra el spam ya existe la moderación del informe 38.

### API7 — Falsificación de peticiones del lado del servidor (SSRF)

*Riesgo:* que el servidor haga peticiones a una URL que controla el cliente.

**No aplica / mitigado.** El backend solo hace peticiones salientes a tres destinos fijos escritos en el código: la API de Open Food Facts, la de Brevo (correo) y la de Anthropic (generación de recetas). El cliente solo aporta el texto de búsqueda, que se envía como parámetro con `URLSearchParams`, nunca como parte de la URL de destino. Las fotos llegan en Base64 en el cuerpo, no como URL que el servidor tenga que descargar.

### API8 — Configuración de seguridad incorrecta

*Riesgo:* cabeceras, errores detallados, CORS abierto, transporte sin cifrar.

**Mitigado.** Render sirve la API solo por HTTPS, y la aplicación usa una `BASE_URL` `https://` sin permitir tráfico en claro. El manejador de errores devuelve un mensaje genérico en los `500` (el detalle solo va al log del servidor). No se configura CORS: la API la usa una aplicación nativa, no un navegador, así que sin cabeceras CORS ningún sitio web puede llamarla desde el navegador de una víctima. Detrás del proxy de Render se usa `trust proxy` para que los límites vean la IP real.

*Corregido en esta revisión:* Express añadía la cabecera `X-Powered-By: Express` a todas las respuestas. Ahora se desactiva: no sirve al cliente y da pistas a un atacante.

*Fuera de alcance:* no se usa `helmet`. Sus cabeceras (CSP, `X-Frame-Options`…) protegen páginas HTML en navegadores, y la API solo devuelve JSON a una aplicación nativa.

### API9 — Gestión incorrecta del inventario

*Riesgo:* versiones antiguas o rutas olvidadas sin documentar ni proteger.

**Mitigado.** Hay un único despliegue (Render, rama `main`) y una única versión de la API. Todas las rutas están documentadas en el anexo B, que se actualiza con cada funcionalidad. Las rutas de una funcionalidad revertida (el enlace público de recetas, informe 23) se quitaron del código, no solo de la aplicación.

*Fuera de alcance:* no hay versionado de la API (`/v1`). Con una sola aplicación cliente que se actualiza a la vez que el backend, no compensa.

### API10 — Consumo inseguro de APIs de terceros

*Riesgo:* fiarse de lo que devuelve un servicio externo.

**Mitigado.** Las respuestas de Open Food Facts se validan antes de usarlas: un fallo de red, un error HTTP o una respuesta con forma rara devuelven `null` sin lanzar, y los valores nutricionales deben ser números en rangos razonables (`openFoodFacts.test.js`). Las propuestas del modelo de lenguaje se validan contra un esquema (ingredientes con unidades de la lista fija) y **los macronutrientes nunca los pone el modelo**: se calculan con el mismo cálculo que una receta normal (informe 24). Todas las llamadas externas tienen tiempo máximo (en esta revisión se ha bajado el de Anthropic de los 10 minutos por defecto del SDK a 60 segundos).

### Correcciones hechas

| Punto | Problema | Corrección |
| --- | --- | --- |
| API5 | Premium no caducaba al pasar `currentPeriodEnd` | `isSubscriptionActive` en `billing/plans.js`, usada por `requirePremium` y `GET /subscription` |
| API4 | Sin límite general de peticiones | `createApiLimiter`: 300/min por usuario o IP para toda la API |
| API2 | El token de sesión entraba en la copia de seguridad en la nube | Exclusión del archivo de DataStore en las reglas de copia de seguridad |
| API8 | Cabecera `X-Powered-By: Express` | `app.disable('x-powered-by')` |

Las 171 pruebas del backend pasan, incluidas las tres nuevas de `security.test.js` (caducidad de Premium, clave del límite general por usuario o IP, y `429` en JSON al superar el límite).

## Limitaciones

- Es una revisión hecha por el propio autor, no una auditoría externa ni una prueba de penetración. Las herramientas automáticas (OWASP ZAP, análisis de dependencias con `npm audit` de forma continua) quedarían como siguiente paso.
- No hay registro de eventos de seguridad (intentos fallidos, `403`, `429`) más allá del log de Render, que no se conserva mucho tiempo ni genera alertas.
- Los límites de peticiones viven en la memoria del proceso: se reinician con cada despliegue y no se comparten entre instancias.
- Sin revocación de tokens ni segundo factor (API2).

## Archivos creados o modificados

- `backend/src/billing/plans.js`: `isSubscriptionActive`.
- `backend/src/middleware/premium.js` y `backend/src/routes/subscription.js`: comprobación de caducidad.
- `backend/src/middleware/rateLimit.js`: `createApiLimiter` y `apiLimiterKey`.
- `backend/src/index.js`: límite general y `x-powered-by` desactivado.
- `backend/src/ai/generateRecipe.js`: tiempo máximo de 60 s para la llamada al modelo.
- `backend/test/security.test.js`: tres pruebas nuevas.
- `app/src/main/res/xml/data_extraction_rules.xml` y `backup_rules.xml`: el token no entra en la copia de seguridad.
