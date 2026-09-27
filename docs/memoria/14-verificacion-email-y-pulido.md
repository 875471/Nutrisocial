# 14. Verificación de email, recuperación de contraseña, fotos, OCR y pulido general

## Objetivo y problema

Esta iteración cierra varios huecos que impedían considerar la aplicación terminada:

1. **Las fotos de receta no funcionaban.** Una persona usuaria informó de que no veía ninguna foto. El código de fotos del informe 12 parecía correcto, pero nunca se había probado de extremo a extremo en un dispositivo.
2. **Cualquier email servía para registrarse,** existiera o no, fuera propio o ajeno. Tampoco había forma de recuperar una contraseña olvidada. El informe 13 dejaba ambos puntos como limitaciones.
3. **La lectura de recetas por foto (OCR)** no entendía las listas de ingredientes numeradas ("1. Harina") y procesaba sin avisar fotos oscuras o movidas, que después daban resultados pobres.
4. **La interfaz funcionaba pero era básica:** estados vacíos de solo texto, sombras distintas en cada pantalla, sin animaciones y con el icono por defecto de Android Studio.
5. **No había datos para enseñar la aplicación.** Con una sola receta, el feed, el buscador por despensa y las recomendaciones no se podían demostrar.

## Decisiones técnicas

### Diagnóstico de las fotos: primero ver, luego cambiar

En lugar de retocar la lógica a ciegas, primero se añadieron trazas en cada paso del recorrido de una foto (etiqueta `FotoReceta` en Logcat): compresión, envío, respuesta del servidor y carga. También se hizo que los errores mostraran su motivo real en lugar de un mensaje genérico. Con eso, una sola prueba en el emulador mostró el fallo:

```
E FotoReceta: No se pudo comprimir la foto content://…/image%3A37
E FotoReceta: java.io.IOException: No se pudo abrir la imagen
```

La causa estaba en `uriToCompressedBase64`:

```kotlin
resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    ?: throw IOException("No se pudo abrir la imagen")
```

Para leer solo las dimensiones se usa `inJustDecodeBounds = true`, y en ese modo `decodeStream` **devuelve siempre `null`**: rellena `bounds` pero no crea ningún bitmap. El operador `?:` interpretaba ese `null`, que es el comportamiento normal, como un fallo al abrir el archivo. **Todas las fotos, de cámara o de galería, se rechazaban** antes de comprimirse, y el mensaje genérico de entonces ("No se pudo leer la foto") lo ocultaba. La corrección separa los dos `null`: el de `openInputStream`, que sí indica un error, y el de `decodeStream`, que es el esperado. Tras corregirlo, la misma prueba completó el recorrido: una foto de 3000 × 2000 px quedó en unos 9 KB, se subió, el servidor la devolvió y se vio en el detalle y en la miniatura del feed. La foto de prueba se quitó después, y la receta quedó como estaba. Una comprobación de solo lectura en la base de datos confirmó que ninguna foto había llegado nunca al servidor, así que el problema no estaba en el backend ni en la visualización.

Las trazas y los mensajes concretos se han mantenido. Además, se añadió protección contra dos casos que antes cerraban la aplicación o daban un error opaco:

- Un `OutOfMemoryError` al decodificar una foto enorme se captura aparte. Es un `Error` y no una `Exception`, así que el `catch` anterior no lo cubría y la aplicación se cerraba.
- El 413 que puede devolver el proxy de Render sin cuerpo JSON ahora tiene un mensaje propio.

Si una foto guardada no se puede decodificar, se ve un icono de aviso en lugar de un hueco vacío, y el log registra la longitud del texto, que distingue un texto truncado de una foto corrupta.

### La tabla de alimentos de producción estaba vacía

Durante la misma prueba se vio que todos los ingredientes aparecían "sin datos nutricionales". La tabla `Food` de Neon tenía **0 filas**: el informe 08 ya había detectado que la semilla de BEDCA no se había cargado en producción, y el 10 lo dejó pendiente. Se ejecutó `npx prisma db seed`, que solo hace `upsert` por nombre y se puede repetir, y la tabla pasó a tener los 953 alimentos de BEDCA. Desde entonces, las recetas nuevas y editadas se calculan con normalidad. Las creadas antes siguen a 0 kcal hasta que se editen, porque los totales se guardan al guardar la receta.

### Verificación de email con Brevo

**Por qué Brevo.** Hace falta un proveedor de correo transaccional con plan gratuito y API REST. La mayoría de alternativas modernas (Resend, Mailgun, Postmark) obligan a verificar un **dominio propio** para enviar a cualquier destinatario, y en su modo de prueba solo permiten escribir a la dirección de la propia cuenta. Este proyecto no tiene dominio: el backend usa el subdominio `onrender.com`. Brevo (antes Sendinblue) permite enviar a cualquier dirección con solo verificar **un remitente**, el email del desarrollador, y su plan gratuito (unos 300 correos al día) sobra para un TFG. SendGrid, la otra opción similar, ha eliminado su plan gratuito permanente. SMTP con Gmail se descartó porque sus contraseñas de aplicación y sus límites no están pensados para enviar desde un servidor.

**Degradación sin configuración.** `src/email/brevo.js` llama a `POST https://api.brevo.com/v3/smtp/email` con la cabecera `api-key`. Necesita `BREVO_API_KEY` y `BREVO_SENDER_EMAIL`, el remitente verificado, porque Brevo rechaza remitentes sin verificar. Si falta alguna, **el registro no falla**: la cuenta se crea, la respuesta indica `emailSent: false` y el servidor lo avisa en el log. Fuera de producción, el log incluye además el enlace o el código, así que el flujo completo se puede probar sin cuenta de Brevo. Así se probó para este informe. `sendEmail` nunca lanza excepciones: un fallo de red, un tiempo agotado (10 s) o una respuesta de error de Brevo se registran y el flujo continúa.

**Los tokens se guardan hasheados.** En la base de datos solo se guarda el SHA-256 del token de verificación y del código de recuperación. Si se filtrara la tabla `User`, los tokens pendientes no servirían para verificar cuentas ajenas ni para cambiar contraseñas. Basta SHA-256 sin sal, y no hace falta bcrypt como en las contraseñas, porque los tokens son aleatorios y de alta entropía: no hay diccionario con el que atacarlos.

**Dos formatos de token.**

- **Verificación:** 32 bytes aleatorios en hexadecimal, con caducidad de 24 horas. Van en un enlace que se abre en el navegador (`GET /auth/verify?token=…`), y como la aplicación no tiene web, el servidor responde con una página HTML mínima de éxito, enlace no válido o enlace caducado.
- **Recuperación de contraseña:** un **código** de 8 caracteres (`K7QM-3XPD`) que se teclea en la app. Usa un alfabeto sin caracteres confusos (sin 0/O ni 1/I/L), caduca en 1 hora, solo sirve una vez y exige el email de la cuenta. Se eligió un código en lugar de un enlace porque la pantalla para escribir la contraseña nueva está en la aplicación, no en una web. Unos 40 bits de entropía serían pocos para un secreto permanente, pero con caducidad de una hora, un solo uso, el email obligatorio y el límite de intentos por IP, adivinarlo por fuerza bruta no es viable.

**Las cuentas existentes se mantienen.** El campo nuevo `emailVerified` vale `false` por defecto. Si la migración solo hubiera añadido la columna, **todas las cuentas creadas antes habrían quedado bloqueadas**, porque el login ahora exige el email verificado. La migración incluye un `UPDATE "User" SET "emailVerified" = true`, y los usuarios de demostración se crean ya verificados.

**No se revela qué emails existen.** El login solo dice "confirma tu email" (403 con `code: EMAIL_NOT_VERIFIED`) **después** de comprobar la contraseña. Con la contraseña mal, la respuesta es el 401 de siempre, así que el 403 no delata cuentas. `resend-verification` y `forgot-password` responden **el mismo mensaje** exista o no la cuenta ("Si hay una cuenta con ese email, te hemos enviado…"). Estas dos rutas tienen además un límite más estricto (5 peticiones cada 15 minutos por IP), porque cada petición es un correo real: no deben servir para llenar un buzón ajeno ni para agotar el cupo diario de Brevo.

**Cambiar la contraseña con el código verifica el email:** recibir el código demuestra que el buzón es de quien lo pide. En la app, un 403 del login no cierra ninguna sesión, porque el login no la tiene todavía, así que no entra en conflicto con la regla del 401 del informe 13.

### Aviso de foto oscura o borrosa antes del OCR

Antes de pasar la foto a ML Kit se miden dos valores sobre una copia reducida a 256 px de lado (unos 50 000 píxeles, que se calculan en milisegundos):

- **Brillo:** la luminancia media, de 0 a 255.
- **Nitidez:** la varianza del laplaciano, un operador que responde a los bordes. Un texto enfocado tiene muchos bordes muy marcados, y uno movido casi ninguno.

La varianza del laplaciano depende también de la exposición: la misma foto más oscura tiene bordes más débiles en valor absoluto. Normalizarla por la varianza de toda la imagen, que es lo habitual, **fallaba con las sombras**. En la calibración, una foto nítida con sombra puntuaba 0,37 y la misma foto desenfocada, 0,73, porque el degradado de la sombra inflaba la varianza global. Se normalizó por el **brillo medio al cuadrado**: una foto oscura pero enfocada puntúa lo mismo que la original (14,6 frente a 14,8), y un degradado suave apenas afecta.

Los umbrales se calibraron con la receta manuscrita de ejemplo, degradada a propósito:

| Variante | Brillo | Nitidez |
|---|---|---|
| Original | 242 | 14,6 |
| Con sombra y girada | 190 | 16,8 |
| Lápiz flojo (poco contraste) | 242 | 3,65 |
| Desenfoque de radio 4 | 242 | 1,80 |
| Desenfoque de radio 6 | 242 | 0,49 |
| Exposición al 35 % | 84 | 14,8 |
| Exposición al 20 % | 48 | 15,0 |

Se avisa si la nitidez es **menor que 1,0** o el brillo **menor que 65**. Son umbrales deliberadamente conservadores: el aviso debe saltar en casos claros, no molestar con fotos aceptables. En el emulador, la foto nítida dio 38,6 y pasó sin aviso, y la borrosa dio 0,34 y mostró "La foto parece borrosa. Acerca la cámara, espera a que enfoque y sujétala firme" con dos opciones: **Repetir foto** o **Continuar igualmente**. La decisión es del usuario, porque el indicador es aproximado.

### Rediseño visual

La paleta no se ha tocado. Lo que cambia es la **consistencia**:

- **Una sola elevación de tarjeta** (`CardElevation`, 2 dp) en todas las pantallas. Antes convivían 3 dp y 1 dp, y la lista de la despensa no tenía sombra.
- **Una escala tipográfica completa** con contraste por peso: negrita en los títulos de pantalla, seminegrita en los de tarjeta, media en los elementos de lista y normal en el texto.
- **Estados vacíos "ilustrados":** un icono grande en un círculo tintado, en rojo si es un error, sobre el título y el texto (`CenteredMessage`), y una versión compacta para los vacíos dentro de una sección, como el día sin entradas o la despensa vacía (`InlineEmptyState`).
- **Animaciones sutiles:** el corazón da un bote con un muelle amortiguado al dar "me gusta" y cambia de color con una transición, el contador se desliza hacia arriba o hacia abajo, y las fotos aparecen con un fundido.
- **Icono propio:** un plato visto desde arriba con una hoja, en los colores del tema. Es un vector adaptativo, con una versión monocroma para los iconos temáticos de Android 13 y PNG para Android 7, que no admite iconos adaptativos. El mismo dibujo se usa en los estados vacíos de recetas, así que la marca se reconoce dentro de la aplicación.

Se revisaron las cinco pestañas y el detalle en modo claro y oscuro con capturas del emulador. No había colores fijos fuera del tema, así que el modo oscuro solo depende de los esquemas de `Theme.kt`, que están completos.

## Cómo funciona

**Registro y verificación.** Al registrarse, el servidor valida los datos (informe 13), crea la cuenta con `emailVerified: false`, genera el token, guarda su hash con caducidad de 24 horas y envía el correo. La app pasa a la pantalla **"Confirma tu correo"**, que dice a qué dirección se ha enviado. Si el servidor no pudo enviarlo, lo dice claramente en lugar de fingir que ha salido. La pantalla tiene un botón para reenviar el correo y otro para ir al login. El usuario abre el enlace en el navegador del móvil y ve "¡Correo confirmado!". Si intenta entrar antes, el login muestra el mensaje con la opción **Reenviar correo de confirmación**, que genera un enlace nuevo e invalida el anterior. La app es compatible hacia atrás: si el servidor todavía no pide verificación (no devuelve `emailVerificationRequired`), inicia sesión directamente como antes.

**Recuperación de contraseña.** "¿Olvidaste tu contraseña?" en el login abre una pantalla con dos pasos, que reutiliza el email que se hubiera escrito:

1. Se pide el código con el email.
2. Se escribe el código recibido (se admite sin guion y en minúsculas) con la contraseña nueva repetida.

Al terminar, se vuelve al login con el aviso "Contraseña cambiada". "No me ha llegado: pedir otro código" vuelve al primer paso, y el código nuevo invalida el anterior.

**Datos de demostración.** `backend/prisma/seedDemoData.js` es un script aparte que **no forma parte del seed automático**, que solo carga BEDCA. Se ejecuta a mano, una vez, contra la base de datos que indique `.env`:

```
cd backend
node prisma/seedDemoData.js --dry-run   # solo lee: cómo se empareja cada ingrediente
node prisma/seedDemoData.js             # crea lo que falte
```

Crea **14 usuarios** con nombres españoles realistas. Los emails usan el dominio reservado `.example` (RFC 2606), que no existe ni puede recibir correo, así que nunca se escribirá a una persona real. Todos comparten la contraseña `demo1234` y se crean con el email verificado. También crea **31 recetas** (desayunos, comidas, cenas y postres) repartidas en los últimos 30 días, y **160 "me gusta"** deterministas. Los ingredientes se emparejan con `matchFood`, igual que en `POST /recipes`, pero **sin consultar Open Food Facts**: así el resultado es reproducible y no depende de un servicio con límite de peticiones. El modo `--dry-run` sirvió para revisar cada emparejamiento antes de escribir nada. Solo "garbanzos cocidos" no encontraba alimento, y se cambió por "garbanzo hervido". El script es **idempotente**: busca los usuarios por email, las recetas por autor y título, y los likes usan su índice único. Una segunda ejecución lo confirmó: 0 usuarios y 0 recetas nuevas. Estos datos son solo para las demostraciones del TFG y no forman parte de la lógica de producción.

**Pulido.**

- **Tirar para refrescar** también en "Mis recetas" y en la despensa, incluso desde los estados vacío y de error. Si el refresco falla con datos en pantalla, se conservan y se avisa.
- **Confirmaciones destructivas coherentes:** todas usan el color de error. "Quitar del diario" y "Quitar foto" no lo hacían. Borrar un ingrediente de la despensa no pide confirmación, porque se deshace volviendo a añadirlo.
- **Accesibilidad:** todos los botones de solo icono tienen descripción (se comprobó en todo el código). La inicial decorativa de las miniaturas ya no la lee TalkBack, y el contador de "me gusta" se anuncia como "3 me gusta" en lugar de "3".
- **OCR:** las listas numeradas ("1. Harina", "2. 200 g de azúcar") se clasifican como ingredientes bajo su encabezado y, sin encabezados, antes del primer paso si tienen forma de ingrediente (una cantidad, o una línea corta sin verbo). "1. Batir los huevos" sigue siendo un paso. Las fracciones ya funcionaban ("1/2", "1 1/2", "1 / 2", "½"); se añadieron pruebas propias y la barra de fracción tipográfica (⁄), que algunos OCR devuelven.

**Pruebas.** El backend pasa de 72 a **82 pruebas**, todas correctas:

- `test/emailVerification.test.js` (7 pruebas), con Brevo simulado. Los correos "enviados" se guardan y de ellos se lee el enlace o el código, como haría el usuario. Cubren: la petición a Brevo con la cabecera `api-key`; que solo se guarde el hash; el login bloqueado y que, con la contraseña mal, no revele la cuenta; el enlace de un solo uso; el caducado (410); el reenvío que invalida el anterior; las respuestas idénticas exista o no la cuenta; el registro sin Brevo o con Brevo caído; y el código de recuperación (de un solo uso, caducado, de otra cuenta, sin guion y en minúsculas).
- `test/parseRecipeText.test.js` (+3) para las fracciones y las listas numeradas.

En Android, `ImageQualityTest` (5 pruebas en la JVM) comprueba el indicador de calidad: un texto enfocado no avisa, un desenfoque fuerte sí, una foto oscura es oscura pero no borrosa, la nitidez no depende de la exposición y una imagen lisa no tiene bordes. A mano, en el emulador y contra el backend local, se recorrieron:

- La subida de una foto, antes y después de la corrección.
- El aviso de calidad con una foto borrosa y una nítida.
- El registro con "Confirma tu correo", el login bloqueado con reenvío, el enlace y el login correcto.
- Tirar para refrescar.
- El modo oscuro en todas las pestañas.

El usuario de prueba del registro se borró al terminar.

## Limitaciones

- **La cuenta de Brevo la tiene que crear el propietario del proyecto.** Hay que registrarse en brevo.com, verificar el remitente, generar la clave y configurar `BREVO_API_KEY` y `BREVO_SENDER_EMAIL` en `.env` y en Render. Hasta entonces, los registros se crean pero el correo no sale. Los usuarios pueden pedir el reenvío cuando esté configurado.
- **Los correos pueden acabar en spam.** Sin dominio propio no se pueden configurar SPF, DKIM ni DMARC para el remitente, así que los proveedores de correo desconfían más de estos envíos. El plan gratuito de Brevo tiene además un límite diario de envíos y añade su marca al pie.
- **Los tokens JWT no se revocan.** Cambiar la contraseña no cierra las sesiones ya abiertas en otros dispositivos, y un token de una cuenta borrada sigue siendo válido hasta que caduca (7 días). En ese caso las rutas responden 404 y la app permite cerrar sesión, como se vio en el emulador.
- **El código de recuperación es corto.** Unos 40 bits son suficientes con la caducidad de una hora, el uso único y el límite por IP, pero un atacante con muchas IP podría hacer más intentos. Una versión de producción añadiría un límite de intentos por cuenta.
- **El indicador de calidad es aproximado.** Mide el desenfoque global y la oscuridad, pero no detecta reflejos, fotos torcidas, texto demasiado pequeño ni una hoja que ocupa poco espacio en la foto. Los umbrales se calibraron con una sola receta de ejemplo y variantes sintéticas, no con fotos reales de muchos usuarios.
- **El emparejamiento de alimentos no tolera el plural en la segunda palabra.** "garbanzos hervidos" no encuentra "Garbanzo, hervido", y "garbanzo hervido" sí. Afecta a los ingredientes escritos a mano (informe 04), no solo a los de demostración.
- **Las recetas creadas con la tabla `Food` vacía siguen a 0 kcal** hasta que su autor las edite.
- **Los datos de demostración comparten la base de datos de producción.** Se distinguen por el dominio `@nutrisocial.example`, pero no hay un entorno separado para las demostraciones. Para retirarlos basta con borrar esos usuarios: sus recetas y likes se van en cascada.
- **Sin pruebas de interfaz automáticas.** Los flujos nuevos de Android se han probado a mano en el emulador. La recuperación de contraseña se probó en el backend, pero no se recorrió a mano en el emulador.

## Archivos creados o modificados

**Backend (`backend/`)**

| Archivo | Cambio |
|---|---|
| `prisma/schema.prisma`, `prisma/migrations/20260928100000_email_verification/` (nuevo) | `emailVerified`, hash y caducidad de los tokens de verificación y recuperación; la migración verifica las cuentas existentes. |
| `prisma/seedDemoData.js` (nuevo) | Datos de demostración idempotentes, con `--dry-run`. |
| `src/email/brevo.js` (nuevo) | Envío por la API de Brevo, degradación sin configuración y plantillas de los dos correos. |
| `src/auth/tokens.js` (nuevo) | Generación de tokens y códigos, normalización y hash SHA-256. |
| `src/routes/auth.js` (modificado) | Registro con verificación, login 403 sin verificar, `verify`, `resend-verification`, `forgot-password`, `reset-password` y sus límites. |
| `src/ocr/parseRecipeText.js` (modificado) | Listas de ingredientes numeradas y barra de fracción tipográfica. |
| `test/emailVerification.test.js` (nuevo), `test/parseRecipeText.test.js`, `test/security.test.js` (modificados) | 7 pruebas nuevas de verificación y recuperación, y 3 de OCR; usuarios simulados verificados. |

**Aplicación Android (`app/src/main/`)**

| Archivo | Cambio |
|---|---|
| `java/.../ui/ImageUtils.kt` (modificado) | **Corrección del fallo que rechazaba todas las fotos**; `OutOfMemoryError` controlado. |
| `java/.../ui/recipes/RecipeSocialComponents.kt` (modificado) | Trazas `FotoReceta`, motivo real de los errores, aviso de foto no decodificable, animaciones del "me gusta" y fundido de las fotos. |
| `java/.../ui/recipes/RecipeViewModel.kt` (modificado) | Trazas de envío y carga de fotos, y refresco de "Mis recetas". |
| `java/.../data/ApiResult.kt` (modificado) | Mensajes para 413 y 429 sin cuerpo JSON. |
| `java/.../data/ocr/ImageQuality.kt` (nuevo), `ui/scan/*` (modificados) | Indicador de brillo y nitidez, y aviso "Repetir foto / Continuar igualmente". |
| `java/.../ui/auth/*` (modificados y nuevos) | `VerifyEmailScreen`, `ForgotPasswordScreen`, aviso de email sin confirmar con reenvío, estados del `AuthViewModel` y `AuthNotice`. |
| `java/.../data/Auth*.kt`, `ApiService.kt` (modificados) | Modelos y rutas nuevas; el interceptor omite el token en todas las rutas públicas de `/auth`. |
| `java/.../ui/navigation/AppNavHost.kt` (modificado) | Rutas `verify-email` y `forgot-password`. |
| `java/.../ui/theme/Type.kt`, `Dimens.kt` (modificados) | Escala tipográfica y `CardElevation`. |
| `java/.../ui/recipes/RecipeComponents.kt` (modificado) | `CenteredMessage` con icono, `InlineEmptyState` e inicial decorativa. |
| `java/.../ui/*` (varias pantallas) | Elevación común, estados vacíos con icono, tirar para refrescar en recetas y despensa, y confirmaciones en rojo. |
| `res/drawable/ic_launcher_*.xml`, `ic_brand_plate.xml`, `res/mipmap-*` | Icono propio (adaptativo, monocromo y PNG heredados). |
| `test/.../ImageQualityTest.kt` (nuevo) | 5 pruebas del indicador de calidad. |
