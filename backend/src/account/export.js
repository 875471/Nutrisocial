// Exportación de los datos de la cuenta (GET /profile/export): todo lo que el usuario ha creado o
// registrado, en un JSON que puede guardar o llevarse a otro servicio (derecho de acceso y de
// portabilidad, arts. 15 y 20 del RGPD).
//
// Los campos de cada modelo se copian con una lista explícita, nunca con `...fila`: así un campo
// sensible que se añada más adelante al modelo (como ya lo son la contraseña hasheada o los hashes
// de los códigos de verificación) no acaba en la exportación sin que nadie lo decida.

const EXPORT_FORMAT_VERSION = 1;

// Lo que se pide a Prisma para cada parte. La contraseña y los tokens no se seleccionan siquiera.
const USER_SELECT = {
  id: true, email: true, name: true, createdAt: true, emailVerified: true,
  birthDate: true, heightCm: true, weightKg: true, sex: true, activityLevel: true, goal: true,
};

const RECIPE_SELECT = {
  id: true, title: true, description: true, steps: true, servings: true, prepMinutes: true,
  kcal: true, protein: true, carbs: true, fat: true, totalWeightGrams: true, imageBase64: true,
  aiGenerated: true, dietTags: true, hidden: true, createdAt: true,
  ingredients: {
    orderBy: { position: 'asc' },
    select: { name: true, quantity: true, unit: true, grams: true, food: { select: { name: true, source: true } } },
  },
};

const LOG_SELECT = {
  date: true, name: true, recipeId: true, servings: true, grams: true,
  kcal: true, protein: true, carbs: true, fat: true, createdAt: true,
};

const day = (date) => (date ? date.toISOString().slice(0, 10) : null);

function parseSteps(steps) {
  try {
    return JSON.parse(steps);
  } catch {
    return [steps];
  }
}

/**
 * Compone el documento de exportación a partir de las filas ya cargadas. Función pura (sin base
 * de datos) para poder probarla y para que el formato esté en un solo sitio.
 */
function buildAccountExport({
  user, recipes, logEntries, pantryItems, comments, likes, saves, following, followers, shoppingList, subscription,
}, now = new Date()) {
  return {
    format: 'nutrisocial-account-export',
    version: EXPORT_FORMAT_VERSION,
    exportedAt: now.toISOString(),
    profile: {
      id: user.id,
      email: user.email,
      name: user.name,
      createdAt: user.createdAt,
      emailVerified: user.emailVerified,
      birthDate: day(user.birthDate),
      heightCm: user.heightCm,
      weightKg: user.weightKg,
      sex: user.sex,
      activityLevel: user.activityLevel,
      goal: user.goal,
    },
    subscription: subscription
      ? {
        plan: subscription.plan,
        status: subscription.status,
        startedAt: subscription.startedAt,
        currentPeriodEnd: subscription.currentPeriodEnd,
        canceledAt: subscription.canceledAt,
      }
      : null,
    recipes: recipes.map((r) => ({
      id: r.id,
      title: r.title,
      description: r.description,
      servings: r.servings,
      prepMinutes: r.prepMinutes,
      ingredients: r.ingredients.map((i) => ({
        name: i.name,
        quantity: i.quantity,
        unit: i.unit,
        grams: i.grams,
        food: i.food ? { name: i.food.name, source: i.food.source } : null,
      })),
      steps: parseSteps(r.steps),
      nutritionTotal: { kcal: r.kcal, protein: r.protein, carbs: r.carbs, fat: r.fat },
      totalWeightGrams: r.totalWeightGrams,
      dietTags: r.dietTags ?? [],
      aiGenerated: r.aiGenerated,
      hidden: r.hidden ?? false,
      // JPEG en Base64, sin el prefijo "data:" (como se guarda); null si no tiene foto.
      imageBase64: r.imageBase64,
      createdAt: r.createdAt,
    })),
    logEntries: logEntries.map((e) => ({
      date: day(e.date),
      name: e.name,
      recipeId: e.recipeId,
      servings: e.servings,
      grams: e.grams,
      kcal: e.kcal,
      protein: e.protein,
      carbs: e.carbs,
      fat: e.fat,
      createdAt: e.createdAt,
    })),
    pantry: pantryItems.map((p) => ({ name: p.name, food: p.food?.name ?? null, createdAt: p.createdAt })),
    comments: comments.map((c) => ({
      text: c.text,
      recipeId: c.recipeId,
      recipeTitle: c.recipe?.title ?? null,
      hidden: c.hidden ?? false,
      createdAt: c.createdAt,
    })),
    likedRecipes: likes.map((l) => ({ recipeId: l.recipeId, recipeTitle: l.recipe?.title ?? null, createdAt: l.createdAt })),
    savedRecipes: saves.map((s) => ({ recipeId: s.recipeId, recipeTitle: s.recipe?.title ?? null, createdAt: s.createdAt })),
    // De las demás personas solo se exporta el nombre: sus datos no son del usuario que exporta.
    following: following.map((f) => ({ name: f.following.name, since: f.createdAt })),
    followers: followers.map((f) => ({ name: f.follower.name, since: f.createdAt })),
    shoppingList: shoppingList.map((i) => ({
      label: i.label, quantity: i.quantity, checked: i.checked, sourceRecipeId: i.sourceRecipeId, createdAt: i.createdAt,
    })),
  };
}

/** Carga de la base de datos todo lo del usuario `userId` y lo compone con buildAccountExport. */
async function loadAccountExport(prisma, userId) {
  const user = await prisma.user.findUnique({ where: { id: userId }, select: USER_SELECT });
  if (!user) return null;
  const byUser = { where: { userId } };
  const titled = { recipe: { select: { title: true } } };
  const [recipes, logEntries, pantryItems, comments, likes, saves, following, followers, shoppingList, subscription] = await Promise.all([
    prisma.recipe.findMany({ where: { authorId: userId }, orderBy: { createdAt: 'asc' }, select: RECIPE_SELECT }),
    prisma.logEntry.findMany({ ...byUser, orderBy: [{ date: 'asc' }, { createdAt: 'asc' }], select: LOG_SELECT }),
    prisma.pantryItem.findMany({ ...byUser, orderBy: { createdAt: 'asc' }, select: { name: true, createdAt: true, food: { select: { name: true } } } }),
    prisma.comment.findMany({
      ...byUser, orderBy: { createdAt: 'asc' }, select: { text: true, recipeId: true, hidden: true, createdAt: true, ...titled },
    }),
    prisma.recipeLike.findMany({ ...byUser, orderBy: { createdAt: 'asc' }, select: { recipeId: true, createdAt: true, ...titled } }),
    prisma.savedRecipe.findMany({ ...byUser, orderBy: { createdAt: 'asc' }, select: { recipeId: true, createdAt: true, ...titled } }),
    prisma.follow.findMany({ where: { followerId: userId }, orderBy: { createdAt: 'asc' }, select: { createdAt: true, following: { select: { name: true } } } }),
    prisma.follow.findMany({ where: { followingId: userId }, orderBy: { createdAt: 'asc' }, select: { createdAt: true, follower: { select: { name: true } } } }),
    prisma.shoppingListItem.findMany({
      ...byUser, orderBy: { createdAt: 'asc' },
      select: { label: true, quantity: true, checked: true, sourceRecipeId: true, createdAt: true },
    }),
    prisma.subscription.findUnique({ where: { userId } }),
  ]);
  return buildAccountExport({
    user, recipes, logEntries, pantryItems, comments, likes, saves, following, followers, shoppingList, subscription,
  });
}

module.exports = { EXPORT_FORMAT_VERSION, buildAccountExport, loadAccountExport };
