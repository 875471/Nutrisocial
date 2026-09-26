# 06. Despliegue del backend en producción (Neon + Render)

## Objetivo y problema

Hasta este punto el backend solo funcionaba en local (SQLite + `npm run dev` en el portátil del desarrollador), lo que impedía usar la app desde el móvil físico fuera de la misma red local y hacía el proyecto dependiente de que el ordenador estuviera encendido. Se necesitaba una base de datos y un servidor accesibles permanentemente desde internet, sin coste y sin tarjeta de crédito.

## Decisiones técnicas

- **Base de datos**: migración de SQLite a **PostgreSQL gestionado en Neon** (plan gratuito, sin caducidad, sin tarjeta). Se usan dos cadenas de conexión: `DATABASE_URL` (pooled, con sufijo `-pooler`, para las consultas normales de Prisma Client) y `DIRECT_URL` (conexión directa, para `prisma migrate`), porque el *connection pooling* de Neon (PgBouncer en modo transacción) no soporta bien las migraciones.
- **Hosting del backend**: **Render** (plan gratuito, sin tarjeta), como servicio *Web Service* que ejecuta `node src/index.js` de forma persistente. Se descartó Vercel por ser serverless (filesystem efímero, no pensado para un proceso Express permanente) y Railway/Fly.io por haber dejado de ofrecer planes gratuitos reales sin tarjeta.
- **Limitación asumida**: el plan gratuito de Render "duerme" el servicio tras 15 minutos de inactividad; la primera petición tras dormirse tarda entre 30 y 60 segundos en responder mientras el contenedor arranca de nuevo.
- **Cliente Android**: `RetrofitClient.kt` apunta ahora a la URL pública de Render (`https://nutrisocial.onrender.com/`) en vez de `10.0.2.2` (solo válido en el emulador) o una IP local. Se añaden timeouts de conexión/lectura/escritura de 60s en el `OkHttpClient` para tolerar el arranque en frío. Al ser ya HTTPS, se elimina `android:usesCleartextTraffic="true"` del manifest.

## Cómo funciona

1. El repositorio se conecta a Render, que reconstruye el backend automáticamente (o mediante *deploy* manual) cada vez que hay un push a la rama `main`: ejecuta `npm install && npx prisma generate` como build y `node src/index.js` como arranque.
2. Un problema encontrado durante el despliegue: Render instala por defecto con `NODE_ENV=production`, lo que hace que `npm install` **omita las `devDependencies`**. La CLI de `prisma` estaba en `devDependencies`, así que `npx prisma generate` no la encontraba instalada e intentaba descargar una versión distinta sobre la marcha, provocando errores. Solución: mover `"prisma"` a `dependencies` en `backend/package.json`, ya que en producción sí hace falta para generar el cliente en cada build.
3. Las variables de entorno (`DATABASE_URL`, `DIRECT_URL`, `JWT_SECRET`) se configuran en el panel de Render, no se suben al repositorio.
4. La app Android consume la API en `https://nutrisocial.onrender.com/`, funcionando igual desde el emulador, desde el móvil físico en cualquier red, o desde fuera de casa.

## Limitaciones

- El "sueño" del servicio gratuito introduce una latencia notable (hasta ~60s) en la primera petición tras un periodo de inactividad; no hay un plan gratuito sin este comportamiento.
- No hay un pipeline de CI/CD con tests automáticos antes del despliegue; el build de Render simplemente instala dependencias y genera el cliente Prisma.
- La base de datos Neon en el plan gratuito también "escala a cero" tras inactividad prolongada, lo que puede sumarse a la latencia de arranque en frío del backend.
- No se ha configurado un dominio propio; se usa el subdominio `onrender.com` proporcionado por la plataforma.

## Archivos creados o modificados

- `backend/package.json`: `prisma` movido de `devDependencies` a `dependencies`.
- `backend/prisma/schema.prisma`: `datasource db` con `provider = "postgresql"`, `url = env("DATABASE_URL")`, `directUrl = env("DIRECT_URL")`.
- `backend/prisma/migrations/`: migración inicial recreada contra PostgreSQL.
- `app/src/main/java/com/example/nutrisocial/RetrofitClient.kt`: `BASE_URL` actualizada a la URL de Render; timeouts de 60s añadidos al `OkHttpClient`.
- `app/src/main/AndroidManifest.xml`: eliminado `android:usesCleartextTraffic="true"`.
