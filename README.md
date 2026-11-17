# NutriSocial

[![Backend tests](https://github.com/875471/nutrisocial/actions/workflows/backend-tests.yml/badge.svg)](https://github.com/875471/nutrisocial/actions/workflows/backend-tests.yml)

Red social de recetas con cálculo nutricional, escaneo OCR de recetas, diario de comidas y buscador por despensa. Trabajo Fin de Grado.

- `app/`: aplicación Android (Kotlin, Jetpack Compose, MVVM).
- `backend/`: API REST (Node.js, Express, Prisma sobre PostgreSQL), desplegada en Render.
- `docs/`: memoria del TFG (`docs/memoria/`) y documentación de la API (`docs/anexo-b-documentacion-api-rest.md`).

## Pruebas del backend

```sh
cd backend
npm ci
npx prisma generate
npm test
```

Las pruebas no necesitan base de datos: sustituyen Prisma por un doble en memoria. Se ejecutan en cada push y pull request con GitHub Actions (`.github/workflows/backend-tests.yml`).
