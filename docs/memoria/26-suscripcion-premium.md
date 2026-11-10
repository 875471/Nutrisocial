# 26. Suscripción Premium

## Objetivo y problema

Hasta ahora todas las funciones de NutriSocial eran gratuitas. El objetivo de esta parte es añadir un modelo de suscripción Premium con su lógica de negocio y su interfaz completas: planes con precio, alta, baja, estado de la suscripción y funciones bloqueadas para quien no la tiene.

Es una **simulación**: no hay pasarela de pago real (ni Google Play Billing ni Stripe). Lo que interesa en el TFG es el modelo de datos, las reglas de acceso y la experiencia de usuario, no el cobro. Integrar una pasarela de pago exige una cuenta de comerciante, publicar la aplicación en Google Play y verificar los recibos en el servidor, y nada de eso aporta a los núcleos técnicos del proyecto.

Solo dos funciones pasan a ser Premium, las dos con más carga técnica y un valor añadido claro:

- El **escaneo OCR de recetas** (`POST /recipes/parse-ocr` y la pantalla «Escanear receta», informe 05).
- Las **recomendaciones para completar el día** del Diario (`GET /log/recommendations`, núcleo 3.4, informe 09).

El resto (recetas manuales, generación con IA, feed, despensa, diario, perfil y funciones sociales) sigue siendo gratis.

## Decisiones técnicas

**Un modelo `Subscription`, no campos sueltos en `User`.** Una suscripción tiene su propio ciclo de vida (alta, periodo, cancelación), y en el modelo de datos se ve más claro como entidad aparte que como cuatro columnas más en `User`. Tiene una fila por usuario como mucho (`userId @unique`) y se borra con la cuenta (`onDelete: Cascade`). Sus campos son `plan` (`"monthly"` o `"annual"`), `status` (`"active"` o `"canceled"`), `startedAt`, `currentPeriodEnd` y `canceledAt`. `User` tiene la relación inversa `subscription Subscription?`.

**Los precios viven en un único sitio del backend.** `src/billing/plans.js` define `PLANS`: mensual a 3,99 € y anual a 29,99 €, en céntimos para evitar errores de coma flotante. `GET /subscription/plans` los devuelve tal cual, y la aplicación no tiene ningún precio escrito a mano. Por eso el «Ahorras ~37%» del plan anual también se calcula en la aplicación a partir de los precios recibidos: si cambian en el backend, el porcentaje cambia solo.

**Fin de periodo con meses de calendario.** `currentPeriodEnd` es la fecha de alta más uno o doce meses. El proyecto no usa ninguna librería de fechas (el calendario del Diario trabaja con `Date` y UTC), así que el cálculo es manual en `addMonths`. `Date.setUTCMonth` desborda al mes siguiente cuando el día no existe (31 de enero más un mes daría 3 de marzo). Por eso `addMonths` deja la fecha en el último día del mes de destino: 31 de enero más un mes da 28 o 29 de febrero, como hacen las suscripciones reales.

**Suscribirse es un `upsert`.** Suscribirse otra vez (cambiar de plan, o volver tras cancelar) reutiliza la misma fila: empieza un periodo nuevo desde ahora, vuelve a `active` y borra `canceledAt`. No hay histórico de suscripciones. No hace falta para las reglas de acceso, y con una fila por usuario la consulta del middleware es una búsqueda por clave única.

**Cancelar revoca Premium al momento.** En una suscripción real, cancelar solo detiene la renovación y el acceso dura hasta `currentPeriodEnd`, porque ese periodo ya está pagado. Aquí no hay pago, así que se ha simplificado a propósito: `POST /subscription/cancel` pone `status: "canceled"`, y el middleware solo mira `status`. `currentPeriodEnd` se guarda, pero no se usa para dar acceso.

**El bloqueo, en un middleware del servidor.** `requirePremium` (`src/middleware/premium.js`) va detrás de `requireAuth`, que deja `req.userId`. Busca la suscripción del usuario y, si no existe o no está activa, responde `403` con `{ error: "Esta función es solo para usuarias Premium." }`. Se aplica solo a las dos rutas afectadas, con `router.post('/parse-ocr', requirePremium, …)`, en vez de a todo el router. El bloqueo de la aplicación es solo de interfaz: quien decide es el servidor, así que un cliente modificado tampoco podría usar estas funciones sin Premium. El OCR en sí (ML Kit) se ejecuta en el móvil, pero sin `parse-ocr` el texto reconocido no se convierte en receta.

**La función bloqueada se ve, no se esconde.** En la aplicación, la pantalla de escaneo y la sección de recomendaciones siguen en su sitio. En lugar del contenido aparece una tarjeta «Función Premium» con una frase sobre lo que desbloquea y un botón a la pantalla de planes. Así el usuario gratuito descubre la función y sabe cómo conseguirla. Para no repetir la comprobación, las dos pantallas usan el mismo composable `PremiumGate(isPremium, onUpgrade, description, content)`.

**Un estado de suscripción compartido.** `SubscriptionViewModel` tiene ámbito en `HomeScreen`, como el resto de ViewModels compartidos (informe 19). El Diario, el escáner, la fila del perfil y la pantalla de planes leen el mismo estado. Al suscribirse o cancelar, todos cambian a la vez, sin recargar.

**Tres estados, no dos.** `isPremium` es `true`, `false` o `null` mientras se consulta `GET /subscription` por primera vez. Con `null`, `PremiumGate` enseña un indicador de carga en vez de la tarjeta bloqueada. Si no, un usuario Premium vería un momento el candado, sobre todo con el servidor de Render despertando (30-60 s). Por eso `isPremium` es `Boolean?` y no `Boolean`. Si la consulta falla, se trata como gratis (el servidor tampoco daría acceso) y se reintenta al volver a entrar en el Diario o en el escáner.

**Script para dar Premium a una cuenta.** `backend/scripts/setPremium.js --email=…` crea o renueva una suscripción anual activa para una cuenta existente. Sirve para preparar una demostración sin pasar por la pantalla de planes. Está fuera de `prisma/` porque no forma parte de ningún seed.

**Migración segura.** La base de datos del `.env` es la de producción, así que no se usó `prisma migrate dev`, que puede proponer reiniciar la base. La migración `20261104120000_add_subscription` se generó con `prisma migrate diff` contra la base real. Se revisó (solo crea la tabla, el índice único y la clave ajena) y se aplicó con `prisma migrate deploy`. No hace falta rellenar datos: sin fila, el usuario es gratuito.

## Cómo funciona

**API.** Todas las rutas van bajo `/subscription` y piden sesión:

- `GET /subscription/plans` devuelve `{ monthly: {...}, annual: {...} }`, cada plan con `id`, `priceCents`, `currency`, `periodMonths` y `label`.
- `GET /subscription` devuelve `{ isPremium, plan, status, startedAt, currentPeriodEnd }`, o solo `{ isPremium: false }` si el usuario nunca se ha suscrito.
- `POST /subscription/subscribe` con `{ plan }`. Si `plan` no es una clave de `PLANS`, responde `400`. Se valida con `Object.hasOwn`, para que `"toString"` no pase por plan. No hay cobro: la propia llamada hace de pago. Responde con el estado nuevo.
- `POST /subscription/cancel` responde `404` si no hay ninguna suscripción activa. Si la hay, la cancela y responde con el estado nuevo (`isPremium: false`).

**Pantalla de planes.** Se llega a ella desde la fila «Suscripción» del perfil o desde el botón de cualquier tarjeta bloqueada. Al abrirla se piden el estado y los planes.

- **Sin suscripción activa**, la pantalla enumera lo que desbloquea Premium y una tarjeta por plan: nombre, precio con formato español («29,99 € al año»), equivalente mensual en los planes de más de un mes («Equivale a 2,50 € al mes») y botón «Suscribirme». El anual lleva la etiqueta «Ahorras ~37%». `savingsPercent` compara el precio por mes de cada plan con el del plan de periodo más corto: 29,99 / (12 · 3,99) = 0,626, es decir, un 37 % menos.
- **Con suscripción activa**, la pantalla enseña «Eres Premium», el plan y la fecha de fin del periodo, y un botón «Cancelar suscripción». Antes de cancelar se pide confirmación, porque se pierde el acceso al momento.

Tras suscribirse o cancelar, la pantalla vuelve atrás y un snackbar lo confirma («¡Ya eres Premium! Plan anual activado.»). Ese snackbar lo enseña `HomeScreen`, no la pantalla de planes: si lo enseñara ella, desaparecería con la pantalla al volver atrás. La pantalla deja claro con una línea al pie que es una suscripción de demostración sin cobro.

**Escaneo.** Desde el informe 19, «Escanear» no es una pestaña de la barra inferior. Es un botón de «Mis recetas», en el perfil, y sigue ahí también para usuarios gratuitos. La pantalla mantiene el título y el texto que explica qué hace. Sin Premium, la tarjeta bloqueada sustituye a los consejos y a los botones de cámara y galería. Al volver de suscribirse, la pantalla ya enseña la cámara.

**Diario.** El resumen del día y la lista de entradas no cambian. Debajo del título «Recomendado para completar tu día», un usuario gratuito ve la tarjeta bloqueada en lugar de las recetas. Para poder envolver la sección en `PremiumGate`, sus tarjetas (cinco como mucho) van ahora en un único elemento de la `LazyColumn`, no en uno por receta. Al volver de la pantalla de planes, el Diario recarga el día y las recomendaciones, como cada vez que se entra en él.

**Perfil.** Debajo de la cabecera hay una fila «Suscripción» que dice «Premium» o «Gratis» y abre la pantalla de planes.

**Pruebas.** `test/subscription.test.js` tiene 8 pruebas. Dos son de funciones puras: `addMonths`, con fin de mes, años bisiestos y cambio de año, y `periodEnd` para cada plan. Las otras seis prueban las rutas reales (Express + JWT), con Prisma en memoria como en `recipeSocial.test.js`:

- los planes;
- `isPremium: false` sin suscripción;
- el fin de periodo de cada plan y el `400` con planes no válidos;
- que suscribirse dos veces actualiza la fila en vez de duplicarla;
- que cancelar sin suscripción da `404`, y que con ella quita el acceso al momento aunque el periodo no haya terminado (y volver a suscribirse lo devuelve);
- que el OCR y las recomendaciones dan `403` sin Premium y funcionan con él, y que el Premium de un usuario no desbloquea nada a otro.

En la aplicación, `SubscriptionPlansTest` comprueba el porcentaje de ahorro calculado a partir de los precios recibidos y el formato de los precios. Las 148 pruebas del backend y las 25 pruebas unitarias de la aplicación pasan, y la aplicación compila.

También se probó el backend en local contra la base real, solo con lecturas. Con una cuenta Premium, `GET /subscription` devuelve el plan anual, y tanto el OCR como las recomendaciones responden. Con una cuenta de demostración sin suscripción, `GET /subscription` devuelve `isPremium: false` y `parse-ocr` responde `403`.

## Limitaciones

- **Suscripción simulada, sin pasarela de pago real.** No se cobra nada ni se verifica ningún recibo: cualquier usuario puede hacerse Premium pulsando «Suscribirme». Para producción haría falta Google Play Billing, con la compra verificada en el servidor, y avisos de renovación y reembolso (notificaciones de servidor de Google Play) para mantener `Subscription` al día.
- **Cancelar revoca el acceso al momento.** En una suscripción real, el acceso se mantendría hasta `currentPeriodEnd`. Aquí se pierde en cuanto se cancela, aunque el periodo no haya terminado.
- **No hay renovación ni caducidad.** Nada mira `currentPeriodEnd`: una suscripción activa no caduca sola al llegar esa fecha ni se renueva. Para hacerlo bastaría con que `requirePremium` comprobara también `currentPeriodEnd > now`, o con una tarea periódica que pasara a `canceled` las vencidas.
- **Sin histórico.** Al haber una sola fila por usuario, cada alta sobrescribe la anterior y no queda registro de cambios de plan ni de cancelaciones pasadas.
- **Versiones antiguas de la aplicación.** Una instalación anterior a esta versión no conoce Premium. Cuando el backend nuevo se despliegue, esa versión verá el `403` como un error genérico al escanear o en las recomendaciones, en vez de la tarjeta bloqueada.
- **Sin pruebas de interfaz.** Las pantallas compilan y la lógica de precios tiene pruebas unitarias, pero no hay pruebas automáticas de Compose ni se ha recorrido el flujo en el emulador.

## Archivos creados o modificados

**Backend (`backend/`)**

| Archivo | Responsabilidad |
|---|---|
| `prisma/schema.prisma` (modificado) | Modelo `Subscription` y relación `User.subscription`. |
| `prisma/migrations/20261104120000_add_subscription/` (nuevo) | Tabla `Subscription`, índice único por `userId` y clave ajena con borrado en cascada. |
| `src/billing/plans.js` (nuevo) | `PLANS`, `isValidPlan`, `addMonths` y `periodEnd`. |
| `src/routes/subscription.js` (nuevo) | `GET /subscription/plans`, `GET /subscription`, `POST /subscription/subscribe` y `POST /subscription/cancel`. |
| `src/middleware/premium.js` (nuevo) | `requirePremium`: `403` sin suscripción activa. |
| `src/routes/recipes.js`, `src/routes/log.js` (modificados) | `requirePremium` en `parse-ocr` y en `recommendations`. |
| `src/index.js` (modificado) | Monta `/subscription`. |
| `scripts/setPremium.js` (nuevo) | Da Premium anual a una cuenta por email. |
| `test/subscription.test.js` (nuevo) | 8 pruebas de fechas, rutas y bloqueo. |

**Aplicación Android (`app/src/main/java/com/example/nutrisocial/`)**

| Archivo | Responsabilidad |
|---|---|
| `data/SubscriptionModels.kt` (nuevo) | `Plan`, `SubscriptionStatus`, `SubscribeRequest`, `savingsPercent` y `formatCents`. |
| `data/SubscriptionRepository.kt` (nuevo) | Llamadas a la API envueltas en `safeApiCall`; planes ordenados por periodo. |
| `ApiService.kt` (modificado) | `getPlans`, `getSubscriptionStatus`, `subscribe` y `cancelSubscription`. |
| `ui/subscription/SubscriptionViewModel.kt` (nuevo) | Estado compartido de la suscripción, planes, alta y baja. |
| `ui/subscription/SubscriptionScreen.kt` (nuevo) | Pantalla de planes o plan actual, y `SubscriptionRow` del perfil. |
| `ui/subscription/PremiumGate.kt` (nuevo) | Composable que pinta el contenido o la tarjeta «Función Premium». |
| `ui/scan/ScanRecipeScreen.kt` (modificado) | Cámara y galería dentro de `PremiumGate`. |
| `ui/log/LogScreen.kt` (modificado) | Recomendaciones dentro de `PremiumGate`, en un único elemento de la lista. |
| `ui/home/HomeScreen.kt` (modificado) | Ruta `suscripcion`, `SubscriptionViewModel` compartido, fila del perfil y snackbar de confirmación. |
| `app/src/test/.../SubscriptionPlansTest.kt` (nuevo) | Porcentaje de ahorro y formato de precios. |
