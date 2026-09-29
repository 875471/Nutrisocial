# 08. Perfil de usuario, objetivo calórico y diario nutricional

## Objetivo y problema

Hasta ahora NutriSocial calculaba los valores nutricionales de cada receta (informe 04), pero no los relacionaba con la persona que la come. Sin datos personales no se puede responder a la pregunta que da sentido a esos números: *¿cuánto debería comer yo y cuánto llevo hoy?* Esta iteración añade dos piezas que completan el núcleo técnico del proyecto:

1. **Perfil con objetivo calórico.** El usuario introduce su fecha de nacimiento, altura, peso, sexo, nivel de actividad y objetivo (perder, mantener o ganar peso). A partir de esos datos, la aplicación estima cuántas kilocalorías diarias necesita.
2. **Diario nutricional.** El usuario registra, día a día, lo que come: raciones de sus recetas o gramos de un alimento suelto de BEDCA. La aplicación muestra el total del día frente a su objetivo.

Estas son las dificultades principales:

- **Elegir una fórmula de estimación** que sea defendible y esté documentada.
- **Tratar las fechas sin ambigüedad.** Un "día" depende de la zona horaria, y el servidor y el teléfono pueden no compartirla.
- **Mantener estable el histórico.** Si una receta se edita o se borra, lo que el usuario registró la semana pasada no debe cambiar.
- **Integrar las dos funciones en la aplicación** con los mismos patrones de las anteriores.

## Decisiones técnicas

**Fórmula de Mifflin-St Jeor.** El gasto energético se estima en tres pasos. Primero se calcula la tasa metabólica basal (TMB), la energía que el cuerpo consume en reposo. Para ello se usa la ecuación de Mifflin-St Jeor (1990):

- Hombres: TMB = 10 × peso (kg) + 6,25 × altura (cm) − 5 × edad (años) + 5
- Mujeres: TMB = 10 × peso (kg) + 6,25 × altura (cm) − 5 × edad (años) − 161

La TMB se multiplica después por un factor de actividad física para obtener el gasto energético total diario (TDEE): 1,2 si es sedentario, 1,375 si es ligero, 1,55 si es moderado, 1,725 si es activo y 1,9 si es muy activo. Por último, se ajusta según el objetivo: −500 kcal para perder peso (aproximadamente medio kilo por semana), sin cambios para mantenerlo y +400 kcal para ganarlo.

Se eligió Mifflin-St Jeor frente a la clásica ecuación de Harris-Benedict (1919, revisada en 1984) porque es la que recomiendan actualmente las asociaciones de dietética para adultos sanos. En los estudios que comparan ambas fórmulas con la TMB medida por calorimetría indirecta, Mifflin-St Jeor acierta en un porcentaje mayor de personas y Harris-Benedict tiende a sobrestimar el gasto, sobre todo con la población actual, más sedentaria que la de principios del siglo XX. También se descartaron fórmulas más precisas, como la de Katch-McArdle, porque necesitan la masa magra del usuario, un dato que casi nadie conoce sin una báscula de bioimpedancia o una prueba específica. El código documenta explícitamente que el resultado es una estimación estándar de población y no una recomendación médica personalizada. La aplicación muestra el mismo aviso bajo el formulario.

**El objetivo no se guarda: se calcula al pedirlo.** La edad cambia con el tiempo. Si se guardara el objetivo, quedaría desactualizado en cada cumpleaños. Por eso el perfil guarda la fecha de nacimiento y el objetivo se recalcula en cada `GET /profile` y `GET /log`. El cálculo está en una función pura, `calculateCalorieGoal`, en `src/nutrition/calorieGoal.js`, sin acceso a la base de datos, igual que el analizador de OCR del informe 05. Así se prueba de forma aislada. Si falta algún dato, devuelve `dailyCalorieGoal: null` y la lista `missingFields` con los campos que faltan, para que la aplicación pueda indicar exactamente qué rellenar en vez de mostrar un error.

**Validación de rangos.** `PUT /profile` admite actualizaciones parciales: solo valida y guarda los campos presentes, y `null` borra un dato. Los rangos admitidos son de 14 a 100 años, de 30 a 300 kg y de 100 a 250 cm. El sexo, el nivel de actividad y el objetivo tienen que ser uno de los valores previstos. La aplicación repite las comprobaciones de altura y peso para avisar antes de enviar nada, y el calendario de la fecha de nacimiento solo permite elegir años dentro del rango. Aun así, la validación que cuenta es la del servidor.

**Modelo `LogEntry` con copia de los valores.** Cada entrada del diario guarda el día, el origen (receta con número de raciones, o alimento con gramos) y los valores ya calculados: kcal, proteínas, hidratos y grasas. Se copian en el momento de crear la entrada y no se recalculan después (salvo si el usuario edita la cantidad, que los recalcula con la receta actual; ver informe 18). Es el mismo principio que siguen las recetas con la tabla `Food` (informe 04): el histórico no cambia de forma retroactiva. Por el mismo motivo, respecto al modelo inicialmente previsto se añadió un campo `name` con el nombre de la receta o del alimento. Las relaciones con `Recipe` y `Food` usan `onDelete: SetNull`, así que, si la receta se borra, la entrada perdería la referencia y ya no se sabría qué se comió. Con el nombre copiado, el día sigue mostrando "Crema de calabaza, 1,5 raciones, 465 kcal" aunque la receta ya no exista. Esto se comprobó borrando una receta con entradas registradas. La relación con `User` usa `onDelete: Cascade`: si se borra el usuario, se borra su diario.

Los valores de una receta se reparten por raciones: la receta guarda los totales de la receta completa, así que cada entrada suma `total × raciones consumidas / raciones de la receta`. Los de un alimento se calculan a partir de sus valores por 100 g. Se admiten fracciones de ración (entre 0,1 y 20, por ejemplo media ración) y entre 1 y 5000 g. Se puede registrar cualquier receta existente, igual que `GET /recipes/:id` permite ver cualquiera, aunque la aplicación solo ofrece las del usuario.

**Fechas como días de calendario, no como instantes.** El cliente decide qué día es según la zona horaria del teléfono y lo envía como texto `AAAA-MM-DD`. El servidor lo guarda siempre como las 00:00 UTC de ese día (`src/utils/day.js`) y filtra por igualdad exacta. De este modo, una comida registrada a las 23:30 en España no aparece al día siguiente por la diferencia horaria con el servidor. Las fechas imposibles ("2026-02-30") se rechazan. En Android, como la aplicación tiene `minSdk 24` y `java.time` no está disponible sin *desugaring*, las operaciones con días (hoy, día anterior, día siguiente, conversión con el `DatePicker` de Material 3, que trabaja en milisegundos UTC) se hacen con `Calendar` y `SimpleDateFormat` en un pequeño archivo de utilidades.

**Endpoints.**

| Método y ruta | Función |
|---|---|
| `GET /profile` | Datos de perfil, edad, `dailyCalorieGoal`, `bmr`, `tdee` y `missingFields`. |
| `PUT /profile` | Actualización parcial con validación de rangos; devuelve el perfil recalculado. |
| `POST /log` | Crea una entrada a partir de `date` y o bien `recipeId` + `servings`, o bien `foodId` + `grams` (nunca los dos a la vez). |
| `GET /log?date=AAAA-MM-DD` | Entradas del día, totales y comparación con el objetivo: `remainingKcal`, `excessKcal` y `progress` (fracción consumida). |
| `DELETE /log/:id` | Borra una entrada del usuario autenticado. Si pertenece a otro usuario, responde 404, sin revelar que existe. |

Todas las rutas están protegidas con el middleware JWT existente. El borrado usa `deleteMany` con el `id` y el `userId` a la vez, de modo que la comprobación de propiedad y el borrado son una única consulta.

**Migración aplicada sin riesgo de pérdida de datos.** El `.env` local apunta a la base de datos de producción en Neon. `prisma migrate dev` puede proponer reiniciar la base de datos si detecta diferencias, así que se siguió un camino más seguro. Primero se generó el SQL sin conexión con `prisma migrate diff`, comparando el esquema anterior con el nuevo. Se comprobó que solo añadía columnas opcionales, una tabla nueva, un índice y claves ajenas. Después, `prisma migrate status` confirmó que no había diferencias pendientes, y la migración se aplicó con `prisma migrate deploy`, que nunca reinicia la base de datos (`20260927120000_profile_and_daily_log`).

**Aplicación Android: mismos patrones.** Se mantiene la arquitectura de las funciones anteriores: modelos de datos, `ApiService` de Retrofit, repositorios que devuelven `ApiResult`, ViewModels con `StateFlow` y pantallas de Compose que reciben el estado y las acciones. Las acciones se agrupan en clases (`ProfileActions`, `LogActions`, `AddEntryActions`), igual que `IngredientActions` en el formulario de recetas.

`ProfileViewModel` y `LogViewModel` tienen el mismo ámbito que `RecipeViewModel` (la entrada `home` del grafo de navegación). Así, el día elegido en el diario y el formulario del perfil se conservan al cambiar de pestaña, y se destruyen al cerrar sesión. El diario es una pestaña nueva de la barra inferior ("Diario") y recarga el día al entrar, porque el objetivo puede haber cambiado en el perfil. El buscador de alimentos reutiliza `GET /foods/search`, el mismo del formulario de recetas, con la misma espera de 300 ms entre pulsaciones. El componente `MacroStat` del detalle de receta se hizo público para reutilizarlo en el resumen del día.

## Cómo funciona

**Perfil.** La pestaña Perfil muestra arriba el nombre y el correo del usuario, y debajo una tarjeta destacada.

- Si el perfil está completo, la tarjeta es verde y muestra el objetivo en grande ("2.759 kcal/día") y una línea con el objetivo elegido, el metabolismo basal y el gasto estimado.
- Si faltan datos, la tarjeta es naranja y enumera en lenguaje natural lo que falta: "Para calcular tu objetivo calórico diario falta: fecha de nacimiento, peso y objetivo."

Debajo está el formulario:

- La fecha de nacimiento se elige en un calendario, y se muestra con la edad ("15/01/1996 (30 años)").
- Altura y peso, con sufijos "cm" y "kg" y teclado numérico. Se admite coma decimal.
- Sexo y objetivo, con botones segmentados.
- El nivel de actividad, con un desplegable que explica cada opción ("Ejercicio moderado 3-5 días por semana").

Al pulsar "Guardar y calcular", la aplicación envía `PUT /profile`, muestra "Perfil guardado" y actualiza la tarjeta con el objetivo que devuelve el servidor.

Ejemplo de cálculo: un hombre de 30 años, 80 kg y 180 cm tiene una TMB de 10·80 + 6,25·180 − 5·30 + 5 = 1780 kcal. Con actividad moderada, 1780 × 1,55 = 2759 kcal, que es su objetivo si quiere mantener el peso. Si quiere ganarlo, son 3159 kcal.

**Diario.** La pestaña Diario se abre en el día de hoy. Las flechas cambian al día anterior o al siguiente, y al tocar la fecha ("Hoy", "Ayer" o "sáb, 26 sept 2026") se abre un calendario. Fuera de hoy, la barra superior ofrece un botón "Hoy" para volver.

La tarjeta de resumen muestra las kcal consumidas frente al objetivo ("465 de 2.759 kcal"), una barra de progreso y lo que queda ("Te quedan 2.294 kcal"). Si se supera el objetivo, la barra y el texto pasan al color de error y el texto indica el exceso ("Te has pasado 300 kcal del objetivo"). Debajo aparecen los gramos de proteínas, hidratos y grasas del día. Si el perfil está incompleto, en lugar de la barra aparece un aviso con un enlace directo al perfil.

Debajo del resumen se listan las entradas del día, con el nombre, el tipo y la cantidad ("Receta · 1,5 raciones", "Alimento · 150 g") y las kcal. El botón de la papelera pide confirmación antes de quitar la entrada.

El botón "Añadir" abre una hoja inferior con dos modos:

- **Mis recetas.** Lista las recetas del usuario con sus kcal por ración. Se elige una y se indica el número de raciones.
- **Alimento suelto.** Busca en la tabla de alimentos de BEDCA mientras se escribe. Se elige un alimento (se muestran sus valores por 100 g) y se indican los gramos.

El botón de confirmación muestra ya el resultado ("Añadir (465 kcal)"). Al guardar, la hoja se cierra, aparece "Añadido: Crema de calabaza" y el resumen se actualiza.

**Pruebas.** Se añadió `backend/test/calorieGoal.test.js`, con ocho pruebas:

- el cálculo de la edad, antes y después del cumpleaños
- la ecuación de Mifflin-St Jeor para ambos sexos
- el objetivo completo con los tres ajustes
- los campos que faltan
- las fechas imposibles
- la validación del perfil, con todos los rangos, los valores no permitidos y la actualización parcial
- el reparto de una receta por raciones y de un alimento por gramos
- los totales del día con la comparación con el objetivo, por debajo, por encima y sin objetivo

Junto con las del OCR suman 23 pruebas, y todas pasan. Además, se probaron todos los endpoints con el servidor en local contra la base de datos real, con un usuario temporal que se borró al terminar. Se comprobaron:

- los errores de validación
- la creación con cada origen
- la petición con dos orígenes a la vez
- el alimento inexistente
- el borrado propio y el repetido (404)
- que una entrada conserva su nombre y sus valores tras borrar la receta

Finalmente, se recorrió el flujo completo en el emulador, con la aplicación apuntando temporalmente al servidor local.

Esa prueba reveló dos errores, que se corrigieron:

1. **Sesión que no se podía cerrar.** El emulador conservaba una sesión antigua de un usuario que ya no existe en la base de datos de producción. `GET /profile` respondía "Usuario no encontrado", y el botón "Cerrar sesión" solo aparecía cuando el perfil cargaba, así que no había forma de salir. Ahora también aparece en la pantalla de error.
2. **Falso error al borrar.** Retrofit entrega como `null` el cuerpo de una respuesta 204 aunque el tipo sea `Unit`, y `safeApiCall` lo interpretaba como "Respuesta vacía del servidor". La entrada se borraba, pero aparecía un aviso de error. Se añadió `safeApiCallNoContent` para las respuestas sin cuerpo.

## Limitaciones

- **Es una estimación general, no una prescripción.** Mifflin-St Jeor es una ecuación de población. Para una persona concreta, el error puede ser de un 10 % o más, y los factores de actividad son categorías amplias que el propio usuario elige, a menudo con optimismo. El objetivo es orientativo y la aplicación lo indica así.
- **No tiene en cuenta la composición corporal.** Dos personas con el mismo peso, altura y edad reciben el mismo objetivo aunque una tenga mucha más masa muscular. Tampoco contempla el embarazo, la lactancia, los deportistas de alto rendimiento, los menores en crecimiento ni ninguna condición médica (diabetes, trastornos tiroideos, trastornos de la conducta alimentaria...), casos en los que las necesidades pueden ser muy distintas.
- **Sin verificación clínica de los datos.** El usuario puede introducir datos poco realistas o falsos dentro de los rangos admitidos, y la aplicación no puede comprobarlos. Los rangos solo evitan valores absurdos.
- **Sin límite inferior de seguridad.** El ajuste de −500 kcal se aplica tal cual. En personas pequeñas y sedentarias puede dar objetivos por debajo de lo que suele recomendarse sin supervisión profesional (aproximadamente 1200 kcal en mujeres y 1500 kcal en hombres). Queda como mejora avisar en ese caso o poner un mínimo.
- **La precisión depende de las recetas.** El diario suma los valores guardados de cada receta. Si una receta tenía ingredientes que no contaron en el cálculo (informe 04), sus raciones también se quedan cortas en el diario. Tampoco se registra el peso real de lo que se come: una "ración" es la división de la receta completa entre sus raciones.
- **La tabla de alimentos de producción está vacía.** Durante las pruebas se comprobó que la tabla `Food` de la base de datos de Neon no tiene filas: la semilla de BEDCA (`npx prisma db seed`) no se ejecutó tras la migración a PostgreSQL del informe 06. Mientras no se cargue, en producción no se pueden registrar alimentos sueltos y las recetas nuevas no suman valores nutricionales. Es un paso de despliegue pendiente, no un defecto del código: el cálculo por gramos está cubierto por las pruebas.
- **Sin vista mensual ni estadísticas.** El diario se consulta día a día. Un calendario mensual con los días registrados, gráficas semanales o el seguimiento del peso son ampliaciones naturales sobre el mismo modelo de datos. *Actualización (informe 16): se ha añadido la vista mensual, con cada día marcado en verde, rojo o azul según sus kcal frente al objetivo.*
- **Sin edición de entradas.** Una entrada solo se puede quitar y volver a añadir. Tampoco hay franjas de comida (desayuno, comida, cena).

## Archivos creados o modificados

**Backend (`backend/`)**

| Archivo | Cambio |
|---|---|
| `prisma/schema.prisma` (modificado) | Campos de perfil en `User`. Modelo `LogEntry` con relaciones inversas en `User`, `Recipe` y `Food`. |
| `prisma/migrations/20260927120000_profile_and_daily_log/` (nuevo) | Migración de las columnas y la tabla nuevas. |
| `src/nutrition/calorieGoal.js` (nuevo) | Mifflin-St Jeor, factores de actividad, ajuste por objetivo, rangos y campos que faltan. |
| `src/nutrition/dailyLog.js` (nuevo) | Valores por raciones o por gramos, totales del día y comparación con el objetivo. |
| `src/utils/day.js` (nuevo) | Conversión entre `AAAA-MM-DD` y las 00:00 UTC de ese día, con validación. |
| `src/routes/profile.js` (nuevo) | `GET /profile` y `PUT /profile`. |
| `src/routes/log.js` (nuevo) | `POST /log`, `GET /log?date=` y `DELETE /log/:id`. |
| `src/index.js` (modificado) | Registro de las rutas `/profile` y `/log`. |
| `test/calorieGoal.test.js` (nuevo) | Ocho pruebas de objetivo calórico, fechas, validación y registro diario. |

**Aplicación Android (`app/src/main/java/com/example/nutrisocial/`)**

| Archivo | Cambio |
|---|---|
| `data/ProfileModels.kt` (nuevo) | `Profile`, `UpdateProfileRequest` y opciones de sexo, actividad y objetivo con sus textos. |
| `data/LogModels.kt` (nuevo) | `LogEntry`, `DailyLog` y `CreateLogEntryRequest`. |
| `data/ProfileRepository.kt`, `data/LogRepository.kt` (nuevos) | Acceso a los endpoints con `ApiResult`. |
| `data/ApiResult.kt` (modificado) | `safeApiCallNoContent` para respuestas 204. |
| `ApiService.kt` (modificado) | Llamadas de perfil y diario. |
| `ui/DateUtils.kt` (nuevo) | Días `AAAA-MM-DD`: hoy, desplazar días, conversión con el `DatePicker` y etiquetas ("Hoy", "Ayer"). |
| `ui/home/ProfileViewModel.kt` (nuevo) | Carga, edición y guardado del perfil. |
| `ui/home/ProfileScreen.kt` (modificado) | Tarjeta del objetivo o aviso de datos que faltan, formulario completo y cierre de sesión también en caso de error. |
| `ui/log/LogViewModel.kt` (nuevo) | Día seleccionado, carga, borrado, hoja de añadir y búsqueda de alimentos. |
| `ui/log/LogScreen.kt` (nuevo) | Selector de fecha, resumen con barra de progreso y macronutrientes, lista de entradas y confirmación de borrado. |
| `ui/log/AddEntrySheet.kt` (nuevo) | Hoja inferior para añadir una receta por raciones o un alimento por gramos, con las kcal previstas. |
| `ui/home/HomeScreen.kt` (modificado) | Pestaña "Diario" y ViewModels de perfil y diario. |
| `ui/recipes/RecipeDetailScreen.kt` (modificado) | `MacroStat` pasa a ser público para reutilizarlo. |
