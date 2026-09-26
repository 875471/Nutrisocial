-- AlterTable
ALTER TABLE "Recipe" ADD COLUMN "totalWeightGrams" REAL;

-- Migración de datos: las recetas ya creadas obtienen su peso sumando los gramos
-- estimados de sus ingredientes (queda NULL si ninguno tiene peso).
UPDATE "Recipe" SET "totalWeightGrams" = (
    SELECT SUM("grams") FROM "RecipeIngredient"
    WHERE "RecipeIngredient"."recipeId" = "Recipe"."id" AND "grams" IS NOT NULL
);
