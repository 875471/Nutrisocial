# Módulo de autenticación de NutriSocial (cliente Android)

## 1. Problema que resuelve

NutriSocial es una red social orientada a la nutrición, por lo que casi toda su funcionalidad (publicar comidas, seguir a otros usuarios, consultar el propio historial) depende de saber quién está usando la aplicación. El módulo de autenticación es la puerta de entrada a todo lo demás: permite que una persona cree una cuenta, inicie sesión con ella y, sobre todo, que no tenga que volver a introducir sus credenciales cada vez que abre la aplicación.

En el lado del cliente esto se traduce en tres necesidades concretas. La primera es comunicarse con los dos endpoints que ofrece el backend, `POST /auth/register` y `POST /auth/login`, interpretando tanto las respuestas correctas como los distintos errores que pueden devolver (datos incompletos, email ya registrado, credenciales incorrectas o simplemente que el servidor no esté accesible). La segunda es presentar al usuario unas pantallas de registro e inicio de sesión que le informen en todo momento de qué está pasando. La tercera es conservar la sesión (el token emitido por el servidor y los datos básicos del usuario) de forma persistente, de modo que al reabrir la aplicación se muestre directamente la pantalla principal.

## 2. Arquitectura

La implementación sigue la arquitectura recomendada por Google para aplicaciones Android modernas, organizada en capas con dependencias en un único sentido: la interfaz conoce al ViewModel, el ViewModel conoce al repositorio y al gestor de sesión, y estos conocen a Retrofit y a DataStore, pero nunca al revés.

```
UI (Jetpack Compose)          LoginScreen · RegisterScreen · HomeScreen · AppNavHost
        │  eventos ↓   ↑ StateFlow
ViewModel                     AuthViewModel
        │
Datos                         AuthRepository ──► ApiService (Retrofit) ──► backend Express
                              SessionManager ──► DataStore Preferences (disco)
```

**Capa de red.** `ApiService` es la interfaz de Retrofit que declara las llamadas HTTP como funciones `suspend` que devuelven `Response<T>`. Devolver `Response` en lugar del cuerpo directamente es deliberado: así Retrofit no lanza una excepción ante un código 4xx y es el repositorio quien decide cómo interpretarlo. Las peticiones y respuestas se modelan con *data classes* de Kotlin (`RegisterRequest`, `LoginRequest`, `User`, `LoginResponse`, `ApiErrorBody`) que Gson serializa automáticamente.

**Repositorio.** `AuthRepository` encapsula el acceso a la API y ofrece al resto de la aplicación una interfaz que no depende de Retrofit ni de HTTP. Su responsabilidad principal es transformar cualquier desenlace de una llamada de red en un valor del tipo sellado `ApiResult` (llamado `AuthResult` en la primera versión y generalizado después al añadir las recetas), que solo puede ser `Success` o `Error`. De este modo las capas superiores nunca tienen que capturar excepciones: el compilador obliga, mediante expresiones `when` exhaustivas, a tratar ambos casos.

**Persistencia de sesión.** `SessionManager` guarda el token y los datos del usuario usando Jetpack DataStore Preferences. Se eligió DataStore frente a `SharedPreferences` porque sus operaciones son asíncronas y seguras frente a concurrencia (no bloquean el hilo principal) y porque expone los datos como un `Flow`, lo que encaja de forma natural con el modelo reactivo de Compose.

**ViewModel.** `AuthViewModel` concentra la lógica de presentación: valida los formularios, lanza las operaciones en `viewModelScope`, guarda la sesión cuando la autenticación tiene éxito y publica el estado de cada pantalla mediante `StateFlow`. Al vivir más que la actividad, sobrevive a cambios de configuración como una rotación de pantalla sin perder una petición en curso. Se implementa como `AndroidViewModel` para poder construir el `SessionManager` a partir del contexto de aplicación sin necesidad, por ahora, de un framework de inyección de dependencias.

**Interfaz.** Las pantallas son funciones Compose sin lógica de negocio: reciben un estado y unas funciones de callback, y se limitan a dibujar. Esta separación (*state hoisting*) permite además previsualizarlas en Android Studio con estados arbitrarios, por ejemplo un error, sin ejecutar la aplicación. La navegación entre ellas se resuelve con Navigation Compose en `AppNavHost`.

La razón de fondo para esta división es que cada pieza tenga un único motivo para cambiar. Si el backend modifica el formato de sus errores, solo cambia el repositorio; si se decide cifrar el token, solo cambia `SessionManager`; si se rediseña la interfaz, el ViewModel permanece intacto. También facilita las pruebas, porque el repositorio recibe `ApiService` por parámetro y puede sustituirse por una implementación falsa.

## 3. Gestión de errores y estados de la interfaz

Los errores se tratan en dos niveles. El primero es la validación en el cliente, que se realiza en el ViewModel antes de enviar nada al servidor: el nombre no puede estar vacío (en el registro), el email debe tener un formato válido según `Patterns.EMAIL_ADDRESS` y la contraseña debe tener al menos cuatro caracteres. Esto evita peticiones inútiles y proporciona una respuesta inmediata al usuario.

El segundo nivel son los errores que llegan de la red, que se clasifican de forma centralizada en una única función, `safeApiCall`, compartida por todos los repositorios:

```kotlin
val response = call()
if (response.isSuccessful && body != null) ApiResult.Success(body)
else ApiResult.Error(parseErrorBody(response) ?: defaultErrorMessage(code) ?: genericErrorMessage(code))
// IOException → "No se pudo conectar con el servidor..."
```

Cuando el servidor responde con un código de error, el repositorio intenta leer el cuerpo `{ "error": "..." }` que envía el backend y muestra ese mensaje. Si el cuerpo no existe o no se puede interpretar, recurre a un mensaje genérico según el código HTTP (400, 401, 409 o 5xx). Los fallos de conectividad (`IOException`) se traducen a un mensaje comprensible. La única excepción que se deja propagar a propósito es `CancellationException`, para no romper la cancelación estructurada de corrutinas cuando el usuario abandona la pantalla.

Cada formulario tiene su propio estado, modelado como una interfaz sellada con cuatro valores posibles: `Idle`, `Loading`, `Success` y `Error(message)`. La interfaz reacciona a ese estado de forma declarativa: en `Loading` los campos y el botón se deshabilitan y el texto del botón se sustituye por un `CircularProgressIndicator`, lo que además impide enviar dos veces la misma petición; en `Error` aparece el mensaje en el color de error del tema de Material 3. Los textos introducidos se guardan con `rememberSaveable`, de modo que no se pierden al rotar el dispositivo.

Una decisión particular afecta al registro: el endpoint `/auth/register` devuelve el usuario creado pero no un token. Para no obligar a la persona a escribir de nuevo sus credenciales, el ViewModel encadena automáticamente un inicio de sesión tras un registro correcto, y solo entonces considera la operación completada.

## 4. Persistencia y comprobación de la sesión

Cuando el inicio de sesión tiene éxito, `SessionManager.saveSession` escribe en DataStore, en una única transacción, el token y el identificador, email y nombre del usuario. La lectura se expone como un `Flow<Session?>` que emite `null` si no hay token guardado y que vuelve a emitir automáticamente cada vez que el almacén cambia.

El ViewModel convierte ese flujo en un `StateFlow<SessionState>` con tres valores: `Checking` (aún no se ha leído el disco), `LoggedIn(user)` y `LoggedOut`. Al arrancar la aplicación, `AppNavHost` muestra un indicador de carga mientras el estado es `Checking` y, en cuanto se resuelve, elige el destino inicial: la pantalla principal si existe sesión y la de inicio de sesión en caso contrario. Así se evita el parpadeo que se produciría si se mostrase primero el login y se redirigiese después.

A partir de ahí, la sesión almacenada actúa como única fuente de verdad para la navegación. Las pantallas no navegan a Home al terminar un login, sino que el login guarda la sesión; `AppNavHost` observa el cambio y navega, limpiando la pila de pantallas para que el botón «atrás» no devuelva al formulario. Del mismo modo, «Cerrar sesión» se limita a borrar el almacén, y la transición a la pantalla de login es consecuencia de ello. Este enfoque garantiza que la interfaz y los datos persistidos no puedan quedar desincronizados.

Conviene señalar dos limitaciones conocidas, a abordar en iteraciones posteriores: el token se guarda sin cifrar (sería recomendable cifrarlo con Android Keystore) y la aplicación no comprueba todavía si el token ha caducado; tampoco lo envía aún en las peticiones, ya que por ahora no existen endpoints protegidos.

## 5. Archivos creados y modificados

Todos los archivos de código están en `app/src/main/java/com/example/nutrisocial/`.

| Archivo | Responsabilidad |
|---|---|
| `data/AuthModels.kt` (nuevo) | Data classes de peticiones y respuestas de `/auth/*` y del cuerpo de error. |
| `data/ApiResult.kt` (nuevo) | Tipo sellado `Success`/`Error` y función `safeApiCall`, que captura excepciones y traduce los errores HTTP. |
| `data/AuthRepository.kt` (nuevo) | Llamadas de registro y login, con mensajes de error propios de la autenticación (401, 409). |
| `data/SessionManager.kt` (nuevo) | Persistencia del token y del usuario con DataStore; expone la sesión como `Flow`. |
| `ui/auth/AuthViewModel.kt` (nuevo) | Validación, estados de login/registro, estado de sesión y cierre de sesión. |
| `ui/auth/AuthComponents.kt` (nuevo) | Componentes Compose compartidos por los formularios (campo, botón con carga, error). |
| `ui/auth/LoginScreen.kt` (nuevo) | Pantalla de inicio de sesión. |
| `ui/auth/RegisterScreen.kt` (nuevo) | Pantalla de registro. |
| `ui/home/HomeScreen.kt` (nuevo) | Pantalla principal provisional con saludo y cierre de sesión. |
| `ui/navigation/AppNavHost.kt` (nuevo) | Grafo de navegación y redirección según el estado de sesión. |
| `ApiService.kt` (modificado) | Añadidos los métodos `register` y `login`. |
| `MainActivity.kt` (modificado) | Sustituida la pantalla de prueba de `/health` por `AppNavHost`. |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` (modificados) | Dependencias de DataStore, Navigation Compose y la integración de lifecycle con Compose. |
