# 18. Diario: objetivos de proteínas, hidratos y grasas y edición de entradas

## Objetivo y problema

El diario (informe 08) ya mostraba la lista de entradas del día y permitía borrarlas, pero tenía tres carencias:

1. **Cada entrada solo mostraba sus kcal.** Las proteínas, hidratos y grasas de cada entrada llegaban del servidor en `LogEntry`, pero la fila no las pintaba. Para saber de dónde venía la proteína del día había que abrir cada receta.
2. **No se podía corregir una cantidad.** Si el usuario apuntaba 1 ración y en realidad había comido 1,5, o 100 g de un alimento en lugar de 150, la única forma de arreglarlo era borrar la entrada y volver a añadirla, buscando otra vez la receta o el alimento.
3. **Solo las kcal tenían objetivo.** El resumen del día comparaba las kcal consumidas con el objetivo calórico, con una barra de progreso y el texto "te quedan X kcal". Las proteínas, hidratos y grasas aparecían como un número suelto, sin referencia.

La tercera carencia tenía una causa concreta. Los gramos objetivo de cada macronutriente ya se calculaban en el servidor desde el informe 09: `getMacroTargets(user)` en `nutrition/macroTargets.js` reparte las kcal del objetivo según el objetivo del perfil (perder, mantener o ganar peso) y las convierte a gramos con los factores de Atwater. Pero ese cálculo solo lo usaba el recomendador, para decidir qué recetas encajan con lo que queda del día. `GET /log` no lo llamaba, así que la aplicación no tenía con qué comparar los macros. El dato existía; faltaba conectarlo al diario.

## Decisiones técnicas

**Un mismo criterio para los cuatro valores.** Para las kcal, `compareWithGoal` devuelve el objetivo, lo que queda, el exceso y el progreso. Se ha añadido junto a ella `compareMacrosWithGoals(totals, macroTargets)`, que hace lo mismo para proteínas, hidratos y grasas con las mismas reglas: lo que queda y el exceso nunca son negativos (`Math.max(0, …)`, solo uno de los dos es distinto de cero) y el progreso es la fracción consumida, que puede pasar de 1 para que el cliente sepa cuánto se ha pasado. `compareWithGoal` no se ha tocado: su respuesta ya la consume la aplicación tal cual. La única diferencia es el redondeo: las kcal van en enteros y los gramos con un decimal, como en `sumTotals`.

**Nombres de campo con el mismo patrón.** La respuesta de `GET /log` añade doce campos: `proteinGoal`, `remainingProtein`, `excessProtein`, `proteinProgress` y sus equivalentes con `Carbs` y `Fat`. Siguen la forma de `dailyCalorieGoal`/`remainingKcal`/`excessKcal`/`progress`. Se prefirió no anidarlos en un objeto `macroGoals` para que el cliente los trate igual que las kcal, y se mantuvieron planos también para no cambiar la forma de la respuesta existente. Con el perfil incompleto los doce son `null`, igual que `dailyCalorieGoal`, y la aplicación vuelve al comportamiento anterior.

**Mismos objetivos que el recomendador.** El diario llama a la misma función `getMacroTargets` que el recomendador. Así, si el diario dice que quedan 60 g de proteína, el recomendador busca recetas para esos mismos 60 g. Si cada uno calculara los objetivos a su manera, la aplicación podría mostrar dos cifras distintas para lo mismo.

**Editar solo la cantidad.** `PUT /log/:id` cambia las raciones de una entrada de receta o los gramos de una de alimento. No permite cambiar el tipo (pasar de receta a alimento) ni el origen (cambiar una receta por otra): eso ya es otra entrada, y para eso están borrar y añadir. El tipo se decide igual que en la respuesta, por si `grams` es nulo, y no por `recipeId`/`foodId`. La razón es que, al borrarse la receta o el alimento, ese id pasa a `null` (`onDelete: SetNull`, informe 13), pero la cantidad se conserva. Si el cuerpo trae el campo del otro tipo (`grams` en una entrada de receta, por ejemplo), se responde 400 con un mensaje que dice qué campo corresponde. Los rangos son los mismos de la creación (`LOG_LIMITS`: de 0,1 a 20 raciones y de 1 a 5000 g).

**Recalcular con los datos actuales, o no editar.** Al editar, los valores se recalculan con `macrosFromRecipe` o `macrosFromFood`, las mismas funciones de la creación. Para eso hay que leer la receta o el alimento. Si ya no existen, se responde 404 ("La receta de esta entrada ya no existe; no se pueden recalcular sus valores") y la entrada no se modifica. La alternativa era escalar las kcal y macros guardados en la entrada en proporción a la nueva cantidad. Eso habría funcionado incluso sin la receta, pero arrastra el redondeo a un decimal de cada entrada y mezclaría dos formas de calcular la misma cosa. En la aplicación, el botón de editar no aparece en las entradas cuya receta o alimento ya no existe.

**Pertenencia comprobada en la escritura.** Como en `DELETE /log/:id`, la escritura es un `updateMany` filtrado por `id` y `userId`. Si no afecta a ninguna fila, se responde 404, tanto si la entrada no existe como si es de otro usuario, sin revelar cuál de los dos casos es. Antes de escribir hay una lectura (`findFirst` con el mismo filtro) para saber el tipo de la entrada y de qué receta o alimento viene. El `updateMany` repite el filtro en lugar de confiar en esa lectura, de modo que la comprobación de propiedad está en la misma operación que modifica los datos.

## Cómo funciona

### Backend

En `GET /log`, después de sumar las entradas del día, la ruta calcula el objetivo calórico con `calculateCalorieGoal`, como antes, y ahora también los gramos objetivo con `getMacroTargets(user)`. Las dos comparaciones se mezclan en la respuesta. Por ejemplo, con un objetivo de 150 g de proteína y 100 g consumidos, la respuesta lleva `proteinGoal: 150`, `remainingProtein: 50`, `excessProtein: 0` y `proteinProgress: 0.667`. Con 250 g de hidratos frente a un objetivo de 200 g, lleva `remainingCarbs: 0`, `excessCarbs: 50` y `carbsProgress: 1.25`.

`PUT /log/:id` valida el id, lee la entrada del usuario (404 si no la encuentra) y mira su tipo. En una entrada de receta, rechaza `grams`, comprueba que `servings` esté en rango, relee la receta y calcula los valores de esas raciones a partir de los totales y del número de raciones de la receta. En una de alimento hace lo mismo con `grams`, releyendo el alimento con `findFoodById`, que busca en el índice de alimentos en memoria. Después guarda la nueva cantidad y los cuatro valores con `updateMany` y devuelve la entrada actualizada con el mismo formato que `POST /log`. El nombre copiado de la entrada no se actualiza: sigue siendo el que tenía la receta cuando se registró.

### Aplicación Android

**Macros de cada entrada.** `EntryRow` tiene una tercera línea bajo el nombre y el tipo/cantidad: "P: 18 g · H: 32 g · G: 9 g", en `bodySmall` y con el color secundario del resto de la fila. El texto lo genera `entryMacrosLabel`.

**Editar.** Junto al botón de borrar hay un icono de lápiz. Al pulsarlo se abre `EditEntryDialog`, un `AlertDialog` con el mismo estilo que el de "Añadir a mi diario de hoy" del detalle de receta. Muestra el nombre de la entrada y un campo numérico "Raciones" o "Gramos", precargado con la cantidad actual. El campo acepta coma o punto decimal, marca el error si el valor está fuera de rango y, si es válido, muestra las kcal aproximadas ("≈ 465 kcal"). Esas kcal salen de escalar las de la entrada; el valor exacto lo calcula el servidor. "Guardar" solo se activa si el valor es válido y distinto del actual. Al confirmar, `LogViewModel.updateEntry` llama a `PUT /log/:id` por `LogRepository.updateEntry`, con el mismo patrón `ApiResult` que `addRecipe` y `addFood`. Si va bien, se sustituye la entrada en `dayState` por la que devuelve el servidor, se muestra "Actualizado: …" en un Snackbar y se recarga el día para que los totales y las barras se pongan al día. Si falla, se muestra el error y la entrada no cambia, porque no se modifica nada antes de tener la respuesta.

**Barras de macros.** En la tarjeta de resumen, los tres `MacroStat` se han sustituido por `MacroProgressStat`. Cada uno muestra los gramos consumidos y, si hay objetivo, "de X g", una barra de 4 dp de alto (la de kcal tiene 10 dp) y la etiqueta. Si se ha superado el objetivo, los gramos y la barra pasan al color de error, igual que las kcal. Los tres siguen repartiéndose la fila con `Modifier.weight(1f)`. Sin objetivo (perfil incompleto) solo se ven los gramos y la etiqueta, como antes.

### Pruebas

`test/dailyLog.test.js` (7 pruebas nuevas), con un cliente de Prisma en memoria:

- `compareMacrosWithGoals` con consumo por debajo, por encima y exactamente en el objetivo, y todo a `null` sin objetivos.
- `GET /log` con el perfil completo devuelve los tres objetivos iguales a `getMacroTargets` y lo que queda, el exceso y el progreso coherentes con los totales; con el perfil incompleto, los doce campos son `null`.
- Editar las raciones de una receta (1 → 2,5 raciones de una receta de 4 raciones y 400 kcal da 250 kcal, 25 g de proteína…) y los gramos de un alimento (100 → 250 g).
- Raciones y gramos fuera de rango, de tipo texto o el campo que no corresponde se rechazan con 400, sin modificar nada.
- La entrada de otro usuario da el mismo 404 que una inexistente, y no se modifica.
- Si la receta ya no existe, tanto si el id sigue en la entrada como si ya es `null`, se responde 404 y la entrada queda igual.

## Limitaciones

- **Editar recalcula con la receta actual, no con la del día en que se registró.** Al crear una entrada, sus valores se copian de la receta en ese momento y no cambian aunque la receta se edite después (informe 08). Editar la cantidad rompe esa regla para esa entrada: se relee la receta y se usan sus totales *actuales*. Si el autor ha cambiado los ingredientes desde que se registró la entrada, el valor recalculado no corresponde a lo que realmente se comió ese día. Lo mismo pasa con un alimento cuyos valores se hayan corregido en la tabla `Food`. Es la misma dependencia que ya existe al crear la entrada, que siempre usa la versión de la receta de ese momento, solo que ahora puede actuar sobre una entrada antigua. Evitarlo exigiría guardar versiones de las recetas, o guardar en la entrada los valores por ración, y escalar con esos.
- **Las entradas huérfanas no se pueden editar.** Si la receta o el alimento se han borrado, solo se puede borrar la entrada. Escalar sus valores guardados sería una alternativa razonable para este caso concreto, pero se descartó para no tener dos formas de calcular.
- **Los objetivos de macros son una heurística.** Son los repartos por objetivo del informe 09, dentro de los rangos AMDR, no una pauta personalizada. Pasarse de proteína se marca en rojo igual que pasarse de kcal, aunque no tenga las mismas implicaciones. Se ha preferido un criterio uniforme y fácil de leer.
- **La barra de macros compara con el objetivo actual.** Como el calendario (informe 16), un día pasado se compara con los objetivos que resultan del perfil de hoy.

## Archivos creados o modificados

**Backend (`backend/`)**

| Archivo | Cambio |
|---|---|
| `src/nutrition/dailyLog.js` (modificado) | `compareMacrosWithGoals`: objetivo, lo que queda, exceso y progreso de proteína, hidratos y grasas. |
| `src/routes/log.js` (modificado) | `GET /log` añade los objetivos de macros con `getMacroTargets`; nuevo `PUT /log/:id`. |
| `test/dailyLog.test.js` (nuevo) | 7 pruebas de la comparación, de `GET /log` y de `PUT /log/:id`. |

**Aplicación Android (`app/src/main/java/.../`)**

| Archivo | Cambio |
|---|---|
| `data/LogModels.kt` (modificado) | Campos de objetivos de macros en `DailyLog` y `UpdateLogEntryRequest`. |
| `ApiService.kt`, `data/LogRepository.kt` (modificados) | `PUT log/{id}` y `updateEntry`. |
| `ui/log/LogViewModel.kt` (modificado) | `updateEntry`: actualiza la entrada, avisa y recarga el día. |
| `ui/log/LogScreen.kt` (modificado) | Macros por entrada, botón y diálogo de edición, `MacroProgressStat` en el resumen. |
| `ui/home/HomeScreen.kt` (modificado) | Conecta `onUpdateEntry` con el ViewModel. |
