# 35. Estadísticas y tendencias del diario

## Objetivo y problema

El Diario enseñaba un día (entradas, totales y progreso frente a los objetivos) y un calendario mensual con el estado de cada día (informe 08). Con eso se ve si hoy se va bien, pero no la tendencia: si la última semana se ha comido siempre por encima del objetivo, o si la proteína se queda corta casi todos los días. El objetivo es una sección de **Estadísticas** en el Diario con un gráfico de calorías y otro de macronutrientes de los últimos 7 o 30 días, hecha con los datos que ya hay en `LogEntry`.

## Decisiones técnicas

**Agregar en el servidor, una sola petición.** Para dibujar 30 días con el endpoint existente (`GET /log?date=`) harían falta 30 peticiones. El nuevo `GET /log/stats?days=7|30&end=AAAA-MM-DD` hace una sola consulta por rango de fechas (aprovecha el índice `(userId, date)` de `LogEntry`) y devuelve la serie ya agregada. La agregación está en un módulo puro, `src/nutrition/dailyStats.js`, que se reutilizará para el resumen semanal (informe 41).

**El «hoy» lo decide el móvil.** Como el resto del diario, el servidor no calcula qué día es: el cliente manda `end` con su fecha local. Así, a las 00:30 en España el periodo ya incluye el día nuevo aunque el servidor esté en UTC.

**Solo dos periodos, 7 y 30 días.** Son los que pide la pantalla, y fijarlos evita validar rangos arbitrarios y consultas enormes. Otro valor da `400`.

**Un punto por día, también los vacíos.** La serie trae todos los días del periodo, a cero los que no tienen entradas. Si se omitieran, el gráfico juntaría días que no son consecutivos y la tendencia engañaría.

**Medias por día con registro.** La media diaria de kcal y macros solo cuenta los días con alguna entrada. Un día sin apuntar nada casi siempre es un olvido, no un ayuno: contarlo como 0 hundiría la media y haría pensar que se come mucho menos. La pantalla lo explica con una línea al pie y muestra «5 de 7 días con registro». Por la misma razón, en el gráfico de líneas un día sin registro **corta** la línea en vez de bajarla a cero.

**Objetivos actuales como referencia.** La respuesta incluye el objetivo calórico y de macros del perfil actual, para dibujar la línea de objetivo. Igual que en el calendario (informe 08), no hay histórico de objetivos: si el usuario cambió de peso hace dos semanas, los días anteriores se comparan con el objetivo nuevo. Está dicho en la pantalla.

**Canvas en vez de una librería de gráficos.** Son dos gráficos sencillos: barras de kcal con una línea discontinua de objetivo, y tres líneas de macros. Con `Canvas` de Compose son unas 80 líneas, sin dependencias nuevas, y usan directamente los colores del tema, así que funcionan igual en modo claro y oscuro. Una librería como Vico daría ejes con marcas y tooltips, pero añade una dependencia y su propio sistema de estilos para algo que aquí no hace falta.

**Accesibles sin ver el gráfico.** Un `Canvas` no tiene texto que TalkBack pueda leer. Cada gráfico lleva una descripción calculada con los datos (`kcalChartDescription`): «Gráfico de barras de calorías de 7 días. Máximo 2500 kcal el 17/11, mínimo 1800 kcal el 15/11. 1 día por encima del objetivo de 2000 kcal.». La de macros dice la media de cada uno frente a su objetivo. Además, la tarjeta de medias da los mismos números en texto.

## Cómo funciona

**API.** `GET /log/stats?days=7&end=2026-11-17` responde:

```json
{ "from": "2026-11-11", "to": "2026-11-17",
  "days": [{ "date": "2026-11-11", "kcal": 0, "protein": 0, "carbs": 0, "fat": 0, "entries": 0 }, ...],
  "daysLogged": 5, "averages": { "kcal": 2010, "protein": 94, "carbs": 186, "fat": 60 },
  "dailyCalorieGoal": 2100, "macroTargets": { "proteinG": 131.3, "carbsG": 236.3, "fatG": 70 } }
```

Sin días con registro, `averages` es `null`; con el perfil incompleto, los objetivos son `null` y la serie llega igual.

**Pantalla.** En la barra superior del Diario hay un icono de gráfico de barras («Ver estadísticas»). Abre la pantalla «Estadísticas», con un selector «7 días / 30 días» arriba y, debajo:

- la tarjeta **Media diaria**: kcal medias frente al objetivo, gramos medios de cada macronutriente y cuántos días tienen registro;
- **Calorías por día**: una barra por día, con la línea discontinua del objetivo y las fechas de inicio y fin bajo el eje;
- **Macronutrientes por día**: una línea por macronutriente (proteínas, hidratos y grasas, con los colores primario, secundario y terciario del tema) y su leyenda.

Si el periodo no tiene ningún día registrado, un mensaje invita a usar el Diario. `LogStatsViewModel` guarda el periodo elegido y cancela la carga anterior si se cambia de periodo antes de que llegue la respuesta, para que una respuesta lenta de 30 días no pise una más reciente de 7.

**Pruebas.** `test/dailyStats.test.js` tiene 3 pruebas: el cálculo de los días del periodo (cruzando de octubre a noviembre), la serie con días vacíos y medias solo de los días con registro, y la ruta real con Prisma en memoria (7 y 30 días, solo las entradas propias, objetivos a `null` sin perfil y los `400` con un periodo o una fecha no válidos). En la aplicación, `LogStatsTest` comprueba las fechas del eje y la descripción para TalkBack. Las 174 pruebas del backend y las unitarias de la aplicación pasan.

## Limitaciones

- **Sin histórico de objetivos**: los días pasados se comparan con el objetivo actual.
- **Solo 7 y 30 días**: no hay vista anual ni rango libre.
- **Sin interacción con el gráfico**: no se puede tocar una barra para ver el valor exacto de ese día ni abrir el día desde ahí. El calendario mensual sigue siendo la forma de ir a un día concreto.
- **Eje Y sin marcas**: el gráfico de barras solo marca el objetivo; los valores exactos están en la tarjeta de medias y en la descripción accesible.

## Archivos creados o modificados

- `backend/src/nutrition/dailyStats.js` (nuevo): días del periodo, serie y medias.
- `backend/src/routes/log.js`: `GET /log/stats`.
- `backend/test/dailyStats.test.js` (nuevo).
- `app/.../data/LogModels.kt`: `LogStats`, `LogStatsDay`, `LogStatsAverages`, `MacroTargets`.
- `app/.../ApiService.kt` y `app/.../data/LogRepository.kt`: `getLogStats` / `getStats`.
- `app/.../ui/log/LogStatsViewModel.kt` y `LogStatsScreen.kt` (nuevos).
- `app/.../ui/log/LogScreen.kt`: botón «Ver estadísticas» en la barra superior.
- `app/.../ui/home/HomeScreen.kt`: ruta `diario/estadisticas`.
- `app/src/main/res/drawable/ic_bar_chart.xml` (nuevo).
- `app/src/test/.../LogStatsTest.kt` (nuevo).
- `docs/anexo-b-documentacion-api-rest.md`: ruta `/log/stats`.
