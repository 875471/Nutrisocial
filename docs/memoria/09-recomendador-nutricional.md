# 09. Recomendador nutricional complementario

## Objetivo y problema

Con el diario del informe 08, el usuario ya sabe cuánto lleva comido y cuánto le queda para su objetivo calórico. Pero la aplicación solo le daba un número: "Te quedan 1.259 kcal". La siguiente pregunta es *¿y qué como?* Este núcleo técnico (3.4 de la propuesta) responde con un **recomendador complementario**. A partir de lo registrado en el día y del objetivo del usuario, sugiere recetas de la propia base de datos que ayudan a completar el día sin pasarse. Entre las recetas que caben, da prioridad a las que acercan los macronutrientes a un reparto saludable.

Estas son las dificultades principales:

- **Pasar de un objetivo calórico a un objetivo de macronutrientes.** El perfil solo da kcal, y para valorar si una receta "encaja" hace falta saber cuántos gramos de proteína, hidratos y grasas quedan.
- **Definir una puntuación** que combine dos criterios distintos (cantidad de energía y reparto de esa energía), que sea explicable y que se pueda probar con datos controlados.
- **Tratar los casos sin recomendación** (perfil incompleto, objetivo ya cubierto) como respuestas normales, no como errores.
- **Reutilizar lo existente**: la carga del día, `sumTotals` y `calculateCalorieGoal`, en lugar de duplicar la lógica.

## Decisiones técnicas

**Reparto de macros por objetivo.** El nuevo módulo `src/nutrition/macroTargets.js` define qué porcentaje de las kcal diarias debe venir de cada macronutriente, según el objetivo del perfil:

| Objetivo | Proteína | Hidratos | Grasas |
|---|---|---|---|
| Perder peso | 35 % | 35 % | 30 % |
| Mantener | 25 % | 45 % | 30 % |
| Ganar peso | 25 % | 50 % | 25 % |

Los tres repartos quedan dentro de los rangos aceptables de distribución de macronutrientes (AMDR) que se usan como referencia para adultos: del 10 al 35 % de proteína, del 45 al 65 % de hidratos y del 20 al 35 % de grasas. La excepción es el 35 % de hidratos al perder peso, que queda por debajo del rango a propósito para hacer sitio a la proteína.

- **Perder peso.** La proteína sube al 35 %, el máximo del rango. En déficit calórico, una ingesta alta de proteína ayuda a conservar masa muscular y además sacia más, así que la parte de hidratos baja.
- **Mantener.** Un reparto "de manual", en el centro de los rangos.
- **Ganar peso.** Suben los hidratos, la fuente de energía más fácil de aumentar, y bajan un poco las grasas.

Si el objetivo es nulo o desconocido, se usa el reparto de mantener. Los porcentajes se convierten a gramos con los factores de Atwater: 4 kcal/g para proteína e hidratos y 9 kcal/g para grasas. Por ejemplo, con 2000 kcal para mantener salen 125 g de proteína, 225 g de hidratos y 66,7 g de grasas. `getMacroTargets(user)` devuelve esos gramos, o `null` si el perfil no permite calcular el objetivo, y delega en `calculateCalorieGoal`. Igual que el objetivo calórico, no se guarda: se recalcula en cada petición.

**Lo que queda del día.** `recommend.js` resta lo consumido (el resultado de `sumTotals`) del objetivo calórico y de los gramos objetivo. Un macro ya superado cuenta como 0, no como negativo: si el usuario ya ha tomado toda la proteína del día, lo que queda simplemente no pide más proteína.

**Fórmula de puntuación.** Cada receta se valora **por ración** (totales de la receta ÷ raciones), que es lo que se añade al diario. Primero hay un filtro y después una puntuación entre 0 y 1 con dos partes:

1. **Filtro.** Se descartan las raciones que superan en más de un 15 % las kcal que quedan (`kcal ración > kcal restantes × 1,15`). Una receta que rompe el objetivo del día no es una buena recomendación, pero se deja un pequeño margen porque las cifras son estimaciones y una receta de 680 kcal cuando quedan 600 sigue siendo razonable. También se descartan las recetas sin valores calculados (0 kcal) y las ya registradas ese día.
2. **Encaje calórico.** Con `ratio = kcal ración / kcal restantes`, vale `ratio` si la ración no se pasa (cuanto más completa el día, mejor) y `1 − 3·(ratio − 1)` si se pasa. Así, pasarse penaliza tres veces más que quedarse corto: una ración que se pasa un 15 % puntúa 0,55, igual que una que cubre el 55 %.
3. **Encaje de macros.** El reparto energético de la ración (fracción de sus kcal que viene de proteína, hidratos y grasas) se compara con el de lo que queda, mediante la distancia euclídea `d` entre los dos vectores. Se normaliza como `1 − d/√2`, porque √2 es la distancia máxima entre dos repartos (toda la energía de un macro frente a toda de otro). El resultado es 1 si los repartos son idénticos y 0 si son opuestos. Las fracciones se calculan sobre la suma de las kcal de los tres macros y no sobre las kcal declaradas de la receta, para que siempre sumen 1 aunque haya pequeñas diferencias de redondeo.
4. **Penalización por ración pequeña.** Si la ración cubre menos del 30 % de lo que queda, se restan 0,15: una ensalada de 150 kcal puede tener un reparto perfecto, pero no "completa" el día.

`puntuación = 0,5 · encaje calórico + 0,5 · encaje de macros − penalización`

Los dos criterios pesan lo mismo porque el enunciado da prioridad a ambos. Las constantes (15 %, 30 %, 0,15, pesos) están nombradas al principio del módulo para poder ajustarlas. Si todos los macros ya están cubiertos pero aún quedan kcal (poco habitual, porque los gramos objetivo suman exactamente el objetivo calórico), se compara con el reparto general del objetivo.

**Motivo generado.** Cada recomendación lleva una frase corta construida a partir de sus valores: "Te aporta 720 kcal y 45 g de proteína, casi justo lo que te queda hoy". La terminación cambia según el `ratio`: "se pasa solo 80 kcal de lo que te queda hoy" si se pasa, "casi justo lo que te queda hoy" a partir del 80 %, "es ligera, tendrás que completarla con algo más" por debajo del 30 % y "encaja con lo que te queda hoy" en el resto. Se genera en el servidor para que la lógica y su explicación estén en el mismo sitio.

**Función pura y consulta separadas.** Como en los módulos anteriores, la lógica que se prueba no toca la base de datos. `rankRecipes(recipes, remaining, opciones)` es una función pura que filtra, puntúa, ordena y devuelve las 5 mejores. `recommendRecipes({ user, consumedTotals, excludeRecipeIds })` calcula el objetivo y lo que queda, decide los casos especiales y, solo si hace falta, consulta las recetas con `prisma.recipe.findMany`, con un `select` de los campos necesarios. El cliente de Prisma se puede inyectar (`db`) para probar también esta función sin base de datos.

**Casos sin recomendación como respuesta normal.** Primero se comprueba el perfil: sin objetivo calórico no se puede calcular lo que queda, así que se devuelve `reason: 'perfil_incompleto'` con los campos que faltan, sin consultar las recetas. Si quedan 50 kcal o menos, se devuelve `reason: 'objetivo_cubierto'` en vez de forzar sugerencias. Los dos casos responden con estado 200, porque no son errores.

**Endpoint.** `GET /log/recommendations?date=AAAA-MM-DD`, protegido con el mismo middleware JWT. La carga del usuario y de las entradas del día, con la validación de la fecha, se extrajo de `GET /log` a una función `loadDay` que usan las dos rutas. Las recetas ya registradas ese día se excluyen para no repetir la sugerencia. La respuesta es `{ date, remaining, recommendations, reason?, missingProfileFields? }`. Cada recomendación incluye `id`, `title`, `kcalPorRacion`, `proteinPorRacion`, `carbsPorRacion`, `fatPorRacion`, `motivo` y la `score` calculada, que la aplicación no muestra pero es útil para depurar y explicar el orden.

**Todas las recetas de la base de datos.** El recomendador considera todas las recetas, no solo las del usuario, porque la gracia de una red social de recetas es descubrir las de otros. `POST /log` ya permitía registrar cualquier receta existente (informe 08), así que el botón "Añadir al registro" funciona también con recetas ajenas.

**Aplicación Android.** Se sigue el mismo patrón: `DailyRecommendations`, `RemainingMacros` y `RecipeRecommendation` en `LogModels.kt`, la llamada en `ApiService`, `getRecommendations` en `LogRepository` y un `RecommendationsUiState` (cargando, éxito o error) en `LogViewModel`. Las recomendaciones tienen su propio estado, separado del día: si fallan, el diario se sigue viendo y la sección muestra su propio "Reintentar". Se recargan cada vez que se recarga el día (al entrar en la pestaña, al cambiar de fecha, al añadir o quitar una entrada), porque dependen de lo registrado. El botón "Añadir al registro" llama al `addRecipe` ya existente con una ración y, mientras se guarda, desactiva los demás botones para evitar dobles registros.

## Cómo funciona

En la pestaña Diario, justo debajo de la tarjeta de resumen, aparece la sección **"Recomendado para completar tu día"**. Su contenido depende de la respuesta:

- **Perfil incompleto.** Una tarjeta "Completa tu perfil" explica que hace falta el objetivo calórico para sugerir recetas, con un enlace "Ir al perfil".
- **Objetivo cubierto.** Una tarjeta con un icono de verificación: "¡Objetivo del día cubierto! Ya has llegado a tus kcal de hoy, no hace falta sumar más recetas."
- **Ninguna receta encaja.** Un texto indica cuántas kcal quedan y sugiere una receta más ligera o un alimento suelto.
- **Con recomendaciones.** Hasta 5 tarjetas con el mismo estilo que las de la lista de recetas: inicial, título, kcal por ración, una línea con proteínas, hidratos y grasas por ración, el motivo y el botón "Añadir al registro". Al pulsarlo, aparece "Añadido: Lentejas", el resumen se actualiza y la receta desaparece de las recomendaciones, sustituida por las siguientes que mejor encajan.

**Ejemplo.** Un hombre de 30 años, 80 kg, 180 cm, actividad moderada, que quiere mantener el peso, tiene un objetivo de 2759 kcal: 172,4 g de proteína, 310,4 g de hidratos y 92 g de grasas. Si ha registrado 1500 kcal (90 g de proteína, 150 g de hidratos y 50 g de grasas), le quedan 1259 kcal, 82,4 g de proteína, 160,4 g de hidratos y 42 g de grasas. Con tres recetas en la base de datos:

- La que ya ha comido hoy no se recomienda.
- Una tarta de 2000 kcal por ración se descarta, porque supera 1259 × 1,15 = 1448 kcal.
- Unas lentejas de 4 raciones (800 kcal, 50 g de proteína, 100 g de hidratos y 22,5 g de grasas por ración) se recomiendan con puntuación 0,805 y el motivo "Te aporta 800 kcal y 50 g de proteína, encaja con lo que te queda hoy".

Este caso se comprobó levantando la ruta real de Express con Prisma simulado, junto con la fecha no válida (400) y el perfil sin objetivo (`perfil_incompleto`, `missingProfileFields: ["goal"]`).

**Pruebas.** Se añadió `backend/test/recommend.test.js`, con nueve pruebas:

- el reparto en gramos de los tres objetivos y el reparto por defecto con un objetivo nulo o desconocido
- `getMacroTargets` con el perfil completo e incompleto
- que lo que queda no baja de 0 cuando un macro ya está cubierto
- el perfil incompleto: sin recomendaciones, con los campos que faltan y sin consultar las recetas
- el objetivo cubierto con 50 kcal de margen, con 0 y con exceso, también sin consultar las recetas
- el filtro: se descarta una ración de 700 kcal cuando quedan 600 (más del 15 %) y se mantiene una de 680; también se descartan la receta excluida y la que no tiene valores
- el orden con datos controlados: con 800 kcal restantes repartidas al 25/45/30 %, queda primero una ración equilibrada de 720 kcal, después una equilibrada de 400 kcal, después una de 744 kcal casi toda de grasa y por último una equilibrada de 160 kcal (penalizada por pequeña); una de 1080 kcal se descarta. También se comprueban los valores por ración y los textos del motivo.
- el límite de 5 recomendaciones
- el uso del reparto general cuando todos los macros están cubiertos

Junto con las anteriores suman 32 pruebas, y todas pasan. La aplicación compila (`compileDebugKotlin`) sin avisos nuevos.

## Limitaciones

- **El reparto de macros es una heurística general, no una recomendación dietética.** Los porcentajes por objetivo son valores razonables dentro de los rangos de referencia para adultos sanos, pero no son una pauta personalizada ni clínica. No tienen en cuenta el peso corporal (las recomendaciones de proteína suelen darse en g/kg), el deporte que se practica, ni condiciones como la diabetes, la enfermedad renal (en la que una dieta alta en proteína puede estar contraindicada), el embarazo o los trastornos de la conducta alimentaria. A esto se suman las limitaciones de la estimación del objetivo calórico descritas en el informe 08.
- **Solo recomienda recetas que ya existen.** El recomendador ordena las recetas de la base de datos; no genera recetas nuevas ni propone cambiar cantidades o ingredientes. Si ninguna receta encaja, solo puede decirlo. La calidad de las sugerencias depende de cuántas recetas haya y de lo completos que sean sus valores (informe 04): una receta con ingredientes que no contaron parece más ligera de lo que es.
- **Supone que una ración completa el día.** La puntuación premia la receta que por sí sola se acerca más a lo que queda. Por la mañana, con 2000 kcal por delante, eso favorece platos muy grandes en vez de sugerir, por ejemplo, un desayuno. No hay franjas de comida ni se combinan varias recetas para cubrir el día, y siempre se propone una ración entera, sin ajustar la cantidad.
- **No hay preferencias ni restricciones.** No se tienen en cuenta alergias, intolerancias, dietas (vegetariana, sin gluten...), gustos, ni la variedad a lo largo de los días: solo se excluyen las recetas ya registradas ese mismo día.
- **Pesos elegidos a mano.** Los pesos y umbrales de la fórmula (50/50, 15 %, 30 %, 0,15) se eligieron por criterio y se comprobaron con ejemplos, no se validaron con usuarios ni se ajustaron con datos.
- **Escalabilidad.** Cada petición lee todas las recetas y las puntúa en memoria. Con cientos o pocos miles de recetas es instantáneo, pero con una base de datos grande habría que prefiltrar en la consulta (por ejemplo, por kcal por ración) o precalcular los valores por ración.
- **Recetas privadas.** Se recomiendan recetas de cualquier usuario. Hoy todas las recetas son visibles para todos (`GET /recipes/:id`), pero si en el futuro hay recetas privadas, habrá que filtrarlas aquí también.

## Archivos creados o modificados

**Backend (`backend/`)**

| Archivo | Cambio |
|---|---|
| `src/nutrition/macroTargets.js` (nuevo) | Reparto de macros por objetivo, conversión a gramos y `getMacroTargets`. |
| `src/nutrition/recommend.js` (nuevo) | Lo que queda del día, filtro, puntuación, motivo, `rankRecipes` (pura) y `recommendRecipes`. |
| `src/routes/log.js` (modificado) | `loadDay` compartido por `GET /log` y el nuevo `GET /log/recommendations`. |
| `test/recommend.test.js` (nuevo) | Nueve pruebas del reparto de macros y del recomendador. |

**Aplicación Android (`app/src/main/java/com/example/nutrisocial/`)**

| Archivo | Cambio |
|---|---|
| `data/LogModels.kt` (modificado) | `RemainingMacros`, `RecipeRecommendation` y `DailyRecommendations` con las constantes de `reason`. |
| `ApiService.kt` (modificado) | `GET log/recommendations`. |
| `data/LogRepository.kt` (modificado) | `getRecommendations(date)`. |
| `ui/log/LogViewModel.kt` (modificado) | `RecommendationsUiState`, carga junto con el día y `addRecommendation` (una ración). |
| `ui/log/LogScreen.kt` (modificado) | Sección "Recomendado para completar tu día" con avisos de perfil incompleto y objetivo cubierto, tarjetas de recomendación y botón "Añadir al registro". |
| `ui/home/HomeScreen.kt` (modificado) | Paso del estado de recomendaciones y de las nuevas acciones a `LogScreen`. |
