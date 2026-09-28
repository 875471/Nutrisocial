# 16. Funcionalidades pendientes de la propuesta: calendario mensual, buscador de recetas, tiempo estimado al escanear y recetas guardadas

## Objetivo y problema

Al contrastar la aplicación con la propuesta del TFG quedaban cuatro funcionalidades descritas en ella que todavía no existían:

1. **Calendario nutricional mensual.** La propuesta describe una vista de mes en la que cada día se marca con un círculo verde, rojo o azul según si las calorías consumidas fueron adecuadas, excesivas o insuficientes. El diario solo se podía recorrer día a día (el informe 08 lo dejaba como limitación).
2. **Buscador de recetas por texto libre.** La propuesta distingue el "buscador de recetas" (por nombre e ingredientes, con orden por tiempo) del "buscador por despensa" (núcleo 3.3, informe 11). No había ninguna búsqueda sobre las recetas de toda la aplicación: "Mis recetas" solo muestra las propias y el feed solo se recorre en orden cronológico.
3. **Tiempo de preparación estimado al escanear.** La propuesta indica que, al transcribir una receta por OCR, el tiempo de preparación se estime a partir del número de pasos e ingredientes como valor por defecto editable. `prepMinutes` era siempre manual, también en las recetas escaneadas.
4. **Recetas guardadas.** La propuesta habla de "recetas publicadas y guardadas por el usuario". Solo existían las propias. Los "me gusta" no cumplen esa función: son una señal social que ven los demás, no una lista personal para volver a encontrar una receta.

## Decisiones técnicas

### Calendario: el estado se calcula en el servidor

`GET /log/calendar?month=AAAA-MM` devuelve, para cada día del mes con al menos una entrada, `{ date, kcal, status }`. El cálculo del estado está en el servidor, en una función pura (`dayStatus` en `nutrition/dailyLog.js`), por la misma razón que el resto de comparaciones con el objetivo: la aplicación solo pinta, y la regla está en un único sitio, con sus pruebas. Los umbrales son los de la propuesta:

| Estado | Condición (kcal del día frente al objetivo) | Color |
|---|---|---|
| `adecuado` | entre el 90 % y el 110 %, ambos incluidos | verde |
| `excesivo` | más del 110 % | rojo |
| `insuficiente` | menos del 90 % | azul |

Los días sin entradas no aparecen en la respuesta: el cliente los pinta sin círculo. Así, un día sin registrar no se confunde con un día "insuficiente", que sería una conclusión falsa: no haber apuntado nada no significa no haber comido.

**El objetivo que se usa es el actual del perfil**, no el que tuviera el usuario ese día. La aplicación no guarda un histórico de objetivos: el objetivo se calcula al vuelo a partir del perfil (`calorieGoal.js`, informe 08), y si el usuario cambia de peso o de objetivo, todo el calendario se reevalúa con el nuevo. Es una limitación conocida (ver Limitaciones) y la leyenda del calendario lo dice expresamente: "Comparado con tu objetivo actual". Si el perfil está incompleto, los días se devuelven con sus kcal pero con `status: null`, y la aplicación los marca en un tono neutro con un aviso para completar el perfil.

La consulta trae solo `date` y `kcal` de las entradas del mes (rango `[día 1, día 1 del mes siguiente)` sobre el índice `(userId, date)` que ya existía) y la suma por día se hace en memoria (`calendarDays`). Un mes tiene como mucho unas decenas o cientos de entradas, así que no compensaba un `groupBy` en la base de datos, que además sería más difícil de probar con la base simulada.

### Buscador: filtrado en memoria y sin tildes

`GET /recipes/search?q=&sortBy=createdAt|prepMinutes&order=asc|desc&cursor=&limit=20` busca en las recetas de todos los autores. La búsqueda tiene que ignorar tildes y mayúsculas ("platano" debe encontrar "Plátano"), y PostgreSQL no lo hace sin la extensión `unaccent`, que habría que activar en Neon y no se puede probar con la base simulada de las pruebas. Por eso la búsqueda se hace en dos pasos:

1. Se cargan de todas las recetas solo los campos ligeros: id, título, tiempo, fecha y nombres de ingredientes, **sin fotos**. Se filtran y se ordenan en memoria con la misma `normalize()` que usan el emparejamiento de alimentos y la despensa.
2. De la página resultante (20 ids) se carga todo lo que necesita la tarjeta, con el mismo `select` que el feed (`feedSelect`, compartido ahora por el feed, el buscador y las guardadas).

**Coincidencia.** Todas las palabras de la búsqueda, normalizadas, en singular aproximado y sin palabras vacías ("de", "con"...), deben aparecer como subcadena en el título o en algún ingrediente. "pollo arroz" encuentra "Arroz con pollo y verduras"; "limones" encuentra un ingrediente "limón". Se exige que estén todas (Y lógico) porque es lo que espera quien escribe varias palabras en un buscador: cada palabra más estrecha el resultado.

**Orden.** Por defecto, de la más reciente a la más antigua. Por tiempo, ascendente ("Más rápidas") o descendente ("Más elaboradas"). Las recetas **sin tiempo indicado van siempre al final**, en los dos sentidos: no se sabe si son rápidas o lentas, y ponerlas primero en "Más rápidas" (como haría un orden ingenuo con `null` como 0) sería engañoso.

**Paginación.** Se mantiene el patrón de cursor del feed: el cursor es el id de la última receta recibida y se busca su posición en la lista ordenada. Si ya no está (se ha borrado entre una página y la siguiente), se responde 400 y la aplicación vuelve a empezar. La respuesta incluye `total`, el número de resultados, para mostrar "N recetas".

### Tiempo estimado: una fórmula simple y explícita

En `POST /recipes/parse-ocr`, justo después de estructurar el texto, se añade:

```
prepMinutesEstimated = acotar(redondear_a_5(10 + 5 · pasos + 2 · ingredientes), 10, 180)
```

- **10 minutos de base**: sacar utensilios, precalentar, emplatar.
- **5 minutos por paso**: un paso típico de receta casera ("pochar la cebolla", "batir los huevos") ronda esos minutos de trabajo activo.
- **2 minutos por ingrediente**: pesarlo, lavarlo, pelarlo o trocearlo.
- **Redondeo a 5 minutos**, porque una estimación de "37 minutos" aparenta una precisión que no tiene.
- **Acotado entre 10 y 180 minutos**, para que una lectura con muchas líneas mal clasificadas no proponga tiempos absurdos.

Es una heurística aproximada, pensada **solo como punto de partida editable**, y no un dato leído de la foto. Por eso el campo se llama `prepMinutesEstimated` y no `prepMinutes`, y la aplicación lo marca en el formulario como "Estimado según pasos e ingredientes" hasta que el usuario lo toca. Como referencia, con las recetas de demostración (tiempos escritos a mano) la fórmula da 40 minutos para la tortilla de patatas (4 pasos y 5 ingredientes; real, 40) y 45 minutos para las lentejas con verduras (4 pasos y 7 ingredientes; real, 50). No puede saber que un paso es "cocer 40 minutos" o "dejar en remojo la noche anterior". Leer esos tiempos del texto de los pasos sería una mejora posible, pero la propuesta pedía explícitamente una estimación por recuento. Si no se ha reconocido ningún paso ni ingrediente, el campo vale `null` y el formulario lo deja vacío.

### Guardadas: una tabla propia, como los likes

`SavedRecipe (userId, recipeId, createdAt)` con índice único `(userId, recipeId)` y borrado en cascada desde el usuario y desde la receta. Es el mismo diseño que `RecipeLike` (informe 12), y por las mismas razones:

- Guardar usa `upsert` y quitar usa `deleteMany`, así que las dos operaciones son **idempotentes** y devuelven el estado final (`{ savedByMe }`). Repetir la petición no duplica ni falla.
- `savedByMe` se calcula igual que `likedByMe`: en la misma consulta, pidiendo como mucho el guardado del propio usuario (`saves` filtrado por `userId` con `take: 1`). Se añadió a `socialFields`/`socialSummary`, así que aparece automáticamente en **todas** las respuestas de receta (detalle, "Mis recetas", feed, buscador y guardadas) sin tocar cada ruta.
- Se ha preferido una tabla nueva a reutilizar los likes con un indicador, porque son conceptos distintos: el like es público (cuenta, "Le gusta a...") y el guardado es privado. Nadie más ve quién ha guardado una receta, y el autor tampoco sabe cuántas veces se ha guardado.

`GET /recipes/saved` ordena por la fecha en que se guardó, de la más reciente a la más antigua (no por la fecha de la receta), que es lo que espera quien acaba de guardar algo. Pagina con el mismo cursor que el feed (el id de la última receta recibida), que se localiza por el par único `(usuario, receta)` del guardado.

La migración (`20260929120000_saved_recipes`) solo crea la tabla y sus índices. Se generó con `prisma migrate diff` contra la base de datos real, se revisó y se aplicó con `prisma migrate deploy`. Al ser una tabla nueva, no afecta a los datos existentes ni al backend desplegado.

### Android: un solo ViewModel de tarjetas para tres listas

El feed, el buscador y las guardadas muestran lo mismo: tarjetas de receta paginadas con "me gusta", guardado y comentarios desde la propia tarjeta. En lugar de triplicar esa lógica (actualización optimista del corazón, borradores de comentario por tarjeta, "Cargar más", recetas borradas entretanto...), `FeedViewModel` pasa a ser una clase abierta con un único punto de variación, `fetchPage(cursor)`:

- `FeedViewModel` pide el feed.
- `RecipeSearchViewModel` pide `GET /recipes/search` con la búsqueda y el orden.
- `SavedRecipesViewModel` pide `GET /recipes/saved`.

La lista de tarjetas en sí (`feedCardItems`) también se ha extraído de la pantalla de inicio y la comparten las tres. `HomeScreen` sincroniza con las tres listas lo que cambie en el detalle (likes, guardado, edición, comentarios o receta borrada), igual que antes hacía solo con el feed.

Un detalle del buscador: el texto del campo cambia con cada letra, pero la búsqueda se lanza 350 ms después de dejar de escribir (o al pulsar la lupa del teclado). Los parámetros de la lista mostrada se guardan aparte (`activeParams`), para que "Cargar más" siga la búsqueda que se está viendo aunque el campo ya tenga otro texto.

## Cómo funciona

### Backend

| Ruta | Respuesta |
|---|---|
| `GET /log/calendar?month=2026-09` | `{ month, dailyCalorieGoal, days: [{ date, kcal, status }], missingProfileFields }`. 400 si el mes no tiene el formato `AAAA-MM`. |
| `GET /recipes/search?q=&sortBy=&order=&cursor=&limit=` | `{ recipes, nextCursor, total }`, con las mismas tarjetas que el feed. 400 si `sortBy` u `order` no son válidos, si `q` pasa de 100 caracteres o si el cursor ya no está en los resultados. |
| `POST /recipes/parse-ocr` | Añade `prepMinutesEstimated` (o `null`). |
| `POST` / `DELETE /recipes/:id/save` | `{ savedByMe }`. 404 si la receta no existe. |
| `GET /recipes/saved?cursor=&limit=` | `{ recipes, nextCursor }`. |
| Todas las respuestas de receta | Añaden `savedByMe`. |

Las rutas nuevas sin parámetro (`/search`, `/saved`) se declaran antes de `/:id`, como `/feed` y `/by-pantry`.

### Aplicación Android

**Calendario.** La barra superior del Diario tiene un icono de calendario que cambia a la vista de mes, y en ella un icono de "día" que vuelve. La vista de mes empieza en el mes del día que se estaba viendo, con flechas para cambiar de mes. La rejilla (`LazyVerticalGrid` de 7 columnas) empieza en lunes, como es habitual en España, y deja huecos antes del día 1. Cada día con datos lleva un círculo del color de su estado. Los tres colores se han añadido al tema (`NutritionStatusColors`, con variantes para modo claro y oscuro, porque el esquema de Material 3 no tiene un verde y un azul equivalentes a su "error"). Hoy se marca con un borde del color principal, y el día seleccionado, con un borde oscuro. Al tocar un día se vuelve a la vista de día con esa fecha. Debajo, una leyenda explica los colores e indica el objetivo con el que se han comparado. Con un lector de pantalla, cada casilla se lee como "15: 2.400 kcal, excesivo". El botón "Añadir" solo aparece en la vista de día, porque en la de mes no está claro a qué día se añadiría. Las funciones de fechas nuevas (`monthGrid`, `shiftMonth`, `monthLabel`) usan `Calendar`, como el resto de `DateUtils.kt` (sin `java.time` por el `minSdk 24`).

**Pestaña Recetas.** Pasa a llamarse "Recetas" y tiene tres segmentos (`PrimaryTabRow`): "Mis recetas" (la lista de siempre, con "Nueva receta" y "Escanear"), "Explorar" y "Guardadas". El segmento elegido se conserva al abrir una receta y volver.

- **Explorar**: campo de búsqueda con lupa ("Buscar por nombre o ingrediente") y botón para borrarlo, tres `FilterChip` de orden ("Más recientes", "Más rápidas", "Más elaboradas"), el número de resultados y las mismas tarjetas que el feed, con tirar para refrescar y "Cargar más".
- **Guardadas**: las recetas guardadas con la misma tarjeta. Se recarga al abrir el segmento.

**Marcador.** La fila de acciones de la tarjeta (feed, buscador, guardadas y detalle, que usan el mismo componente) tiene un marcador entre los comentarios y "Compartir": vacío si no está guardada y relleno, en el color principal, si lo está. Se actualiza al momento y vuelve atrás con un aviso si el servidor falla, igual que el "me gusta". Al quitar el marcador desde "Guardadas" la tarjeta no desaparece al instante (se puede volver a guardar si fue sin querer): sale de la lista al refrescar. Los iconos de marcador y calendario no están en el conjunto básico de iconos de Material que usa el proyecto, así que se han añadido como vectores (`res/drawable`), como los de la tarjeta del feed.

**Escáner.** El formulario ya precargaba lo que devuelve `/recipes/parse-ocr` (título, raciones, ingredientes con su alimento y pasos). Ahora precarga también el tiempo estimado, editable exactamente igual que los demás campos, con el texto de ayuda "Estimado según pasos e ingredientes" hasta que el usuario lo cambia.

### Pruebas

- Backend (100 pruebas en total, 13 nuevas):
  - `test/calendar.test.js` (nuevo): umbrales de `dayStatus` (los extremos del ±10 % son adecuados), `parseMonth`, suma por día de `calendarDays` y la ruta completa sobre una base simulada, con meses fuera de rango, otro usuario y perfil incompleto.
  - `test/recipeSearch.test.js` (nuevo): normalización de la búsqueda, coincidencia en título e ingredientes, sin tildes y con plurales, los tres órdenes con las recetas sin tiempo al final, paginación y validación de parámetros.
  - `test/recipeSocial.test.js`: la ruta de búsqueda y el ciclo completo de guardadas (guardar dos veces, `savedByMe` por usuario en el feed, el detalle y la búsqueda, la lista paginada, quitar y un cursor ya no guardado).
  - `test/parseRecipeText.test.js`: la fórmula de estimación, con el redondeo y los dos límites.
- Android: `MonthCalendarTest` (4 pruebas) con el cambio de mes y de año, la rejilla que empieza en lunes (septiembre y febrero de 2026, febrero bisiesto de 2028), el nombre del mes y la descripción accesible de cada casilla.
- Comprobación con el backend en local contra la base de datos real y una cuenta de demostración: el calendario responde (vacío y sin objetivo para una cuenta sin perfil), "pollo" ordenado por tiempo devuelve las 3 recetas de 30, 35 y 45 minutos, "PLATANO" encuentra las 4 que llevan plátano, guardar y quitar funcionan y dejan la base como estaba, y el escaneo de una tortilla propone 35 minutos.

## Limitaciones

- **Objetivo actual, no histórico.** El calendario compara todos los días con el objetivo que tiene hoy el perfil. Si el usuario cambia de peso, de nivel de actividad o de objetivo, los días pasados pueden cambiar de color aunque lo que comió no haya cambiado. Solucionarlo exigiría guardar el objetivo de cada día (o un histórico de cambios del perfil) al registrar las entradas.
- **El día en curso sale incompleto.** El día de hoy aparece en azul ("insuficiente") hasta que se registran todas las comidas. Es correcto con los datos que hay, pero puede leerse como un aviso cuando el día aún no ha terminado.
- **Escalabilidad del buscador.** Filtrar en memoria cargando título e ingredientes de todas las recetas es adecuado para cientos o unos pocos miles de recetas, pero no para una red social real. Lo correcto a mayor escala sería la búsqueda de texto completo de PostgreSQL (`tsvector` con la configuración `spanish` y `unaccent`) con un índice GIN, que además ordenaría por relevancia.
- **Sin orden por relevancia ni tolerancia a erratas.** El buscador exige que las palabras aparezcan tal cual (salvo tildes y plurales): "tortila" no encuentra "tortilla". El emparejamiento de alimentos sí tolera una letra de diferencia (informe 04), pero aquí se ha preferido un resultado predecible.
- **Tiempo estimado aproximado.** La fórmula no lee los tiempos escritos en los pasos ("hornear 40 minutos"), así que subestima guisos y horneados largos y puede sobrestimar recetas con muchos ingredientes que solo se mezclan. Es un valor de partida y así se indica en el formulario. Sus coeficientes no se han ajustado con datos.
- **Listas de tarjetas sincronizadas solo a través del detalle.** Lo que cambia en el detalle se copia al feed, al buscador y a las guardadas. Pero un "me gusta" o un guardado hecho directamente en una tarjeta del buscador no se refleja en la misma receta del feed hasta refrescarlo, porque cada lista tiene su propio estado.
- **Sin prueba manual completa en el emulador.** Los endpoints se han comprobado contra el backend local y la aplicación compila y pasa sus pruebas, pero en esta sesión no se ha recorrido a mano la interfaz nueva en el emulador (calendario, segmentos de Recetas y marcador).

## Archivos creados o modificados

**Backend (`backend/`)**

| Archivo | Cambio |
|---|---|
| `prisma/schema.prisma`, `prisma/migrations/20260929120000_saved_recipes/` (nuevo) | Modelo `SavedRecipe` con índice único `(userId, recipeId)` y cascada. |
| `src/utils/day.js` (modificado) | `parseMonth`. |
| `src/nutrition/dailyLog.js` (modificado) | `dayStatus` y `calendarDays` (margen del ±10 %). |
| `src/routes/log.js` (modificado) | `GET /log/calendar`. |
| `src/social/recipeSearch.js` (nuevo) | Normalización, coincidencia, orden y paginación del buscador. |
| `src/ocr/estimatePrepTime.js` (nuevo) | Fórmula del tiempo estimado. |
| `src/social/recipeSocial.js` (modificado) | `savedByMe` en `socialFields`/`socialSummary`. |
| `src/routes/recipes.js` (modificado) | `feedSelect`/`toFeedItems` compartidos; `GET /search`, `GET /saved`, `POST`/`DELETE /:id/save`; `prepMinutesEstimated` en `parse-ocr`. |
| `test/calendar.test.js`, `test/recipeSearch.test.js` (nuevos), `test/recipeSocial.test.js`, `test/parseRecipeText.test.js` (modificados) | 13 pruebas nuevas; la base simulada incluye los guardados. |

**Aplicación Android (`app/src/`)**

| Archivo | Cambio |
|---|---|
| `main/java/.../ui/log/MonthCalendar.kt` (nuevo) | Rejilla del mes, casillas con círculo de estado y leyenda. |
| `main/java/.../ui/log/LogViewModel.kt`, `LogScreen.kt` (modificados) | Vista de día o de mes, cambio de mes, carga del calendario y apertura de un día. |
| `main/java/.../ui/DateUtils.kt` (modificado) | `monthOf`, `shiftMonth`, `monthLabel`, `monthGrid`. |
| `main/java/.../ui/theme/Color.kt`, `Theme.kt` (modificados) | Colores de estado (claro y oscuro) y `MaterialTheme.statusColors`. |
| `main/java/.../ui/home/FeedViewModel.kt` (modificado) | Clase abierta con `fetchPage`, `restart`, `toggleSave` y `total`. |
| `main/java/.../ui/explore/RecipeSearchViewModel.kt`, `SavedRecipesViewModel.kt`, `ExploreContent.kt` (nuevos) | Buscador con retardo y orden, guardadas, y sus pantallas. |
| `main/java/.../ui/home/HomeTabScreen.kt` (modificado) | `feedCardItems` reutilizable y acción de guardar. |
| `main/java/.../ui/recipes/RecipeListScreen.kt` (modificado) | Segmentos "Mis recetas", "Explorar" y "Guardadas". |
| `main/java/.../ui/recipes/RecipeFeedCard.kt`, `RecipeDetailScreen.kt`, `RecipeViewModel.kt` (modificados) | Marcador en la tarjeta y guardado desde el detalle; tiempo estimado precargado. |
| `main/java/.../ui/recipes/RecipeFormScreen.kt` (modificado) | Texto de ayuda del tiempo estimado. |
| `main/java/.../ui/home/HomeScreen.kt` (modificado) | ViewModels del buscador y de guardadas, segmentos y sincronización con las tres listas. |
| `main/java/.../data/*.kt`, `ApiService.kt` (modificados) | `CalendarMonth`, `CalendarDay`, `SaveState`, `savedByMe`, `prepMinutesEstimated`, `total` y las rutas nuevas. |
| `main/res/drawable/ic_calendar_month.xml`, `ic_today.xml`, `ic_bookmark.xml`, `ic_bookmark_border.xml` (nuevos) | Iconos de Material como vectores. |
| `test/.../MonthCalendarTest.kt` (nuevo) | 4 pruebas de las utilidades del calendario. |
