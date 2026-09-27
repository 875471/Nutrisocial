-- AlterTable
ALTER TABLE "Recipe" ADD COLUMN     "imageBase64" TEXT;

-- CreateTable
CREATE TABLE "RecipeLike" (
    "id" SERIAL NOT NULL,
    "userId" INTEGER NOT NULL,
    "recipeId" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "RecipeLike_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "RecipeLike_recipeId_idx" ON "RecipeLike"("recipeId");

-- CreateIndex
CREATE UNIQUE INDEX "RecipeLike_userId_recipeId_key" ON "RecipeLike"("userId", "recipeId");

-- AddForeignKey
ALTER TABLE "RecipeLike" ADD CONSTRAINT "RecipeLike_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RecipeLike" ADD CONSTRAINT "RecipeLike_recipeId_fkey" FOREIGN KEY ("recipeId") REFERENCES "Recipe"("id") ON DELETE CASCADE ON UPDATE CASCADE;

