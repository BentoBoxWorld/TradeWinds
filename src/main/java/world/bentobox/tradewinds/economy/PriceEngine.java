package world.bentobox.tradewinds.economy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.Damageable;

import world.bentobox.tradewinds.TradeWinds;

/**
 * Base price engine, ported from BlueBook (BentoBoxWorld/BlueBook) so
 * TradeWinds carries its own pricing logic. A material's base price is either
 * configured (economy.base-prices) or derived from its crafting recipe:
 * ingredient costs recurse (depth-capped, cycle-detected), smelting adds a
 * fuel share, stonecutting passes through, and the result is divided by the
 * recipe's output count. Damaged items are worth proportionally less.
 * <p>
 * Island modifiers (type, band, stock drift) are applied on top by
 * {@link MarketService}; this class only answers "what is this worth,
 * anywhere".
 *
 * @author tastybento
 */
public class PriceEngine {

    private static final int MAX_RECIPE_DEPTH = 6;

    private final TradeWinds addon;
    private final Function<ItemStack, List<Recipe>> recipeSource;
    // Material name -> resolved base price
    private final Map<String, Double> cache = new HashMap<>();

    public PriceEngine(TradeWinds addon) {
        this(addon, Bukkit::getRecipesFor);
    }

    /**
     * Test constructor with an injectable recipe source.
     */
    PriceEngine(TradeWinds addon, Function<ItemStack, List<Recipe>> recipeSource) {
        this.addon = addon;
        this.recipeSource = recipeSource;
    }

    /**
     * Clears the price cache. Call after a settings reload.
     */
    public void invalidate() {
        cache.clear();
    }

    /**
     * The base price for an item, or a negative value if it cannot be priced
     * (not configured and not craftable from priced ingredients).
     */
    public double getPrice(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return -1;
        }
        String key = item.getType().name();
        Double base = cache.get(key);
        if (base == null) {
            Double configured = addon.getSettings().getBasePrices().get(key);
            base = configured != null ? configured : deriveFromRecipes(item, new HashSet<>(), 0);
            if (base >= 0) {
                cache.put(key, base);
            }
        }
        if (base < 0) {
            return base;
        }
        // NBT is part of the price (Stage 7.5 Phase 4): a worn bow is worth less
        // than a mint one, a Silk Touch pick more than a plain one, and a Potion
        // of Strength II is not a bottle of water
        return applyPotion(item, applyEnchantments(item, applyDurability(item, base)));
    }

    /**
     * Enchantments as a price premium. {@link #getEnchantmentValue} was written
     * long before anything called it - the hold used to discard NBT on the way
     * in, so there was never an enchanted item left to price.
     */
    private double applyEnchantments(ItemStack item, double base) {
        double factor = addon.getSettings().getEnchantmentPriceFactor();
        if (factor <= 0) {
            return base;
        }
        return base * (1.0 + getEnchantmentValue(item) * factor);
    }

    /**
     * Potions by what is actually in them. Every potion shares one Material, so
     * without this a Potion of Strength II and a bottle of water are the same
     * good - which is the clearest case for pricing NBT at all.
     */
    private double applyPotion(ItemStack item, double base) {
        double perEffect = addon.getSettings().getPotionEffectPrice();
        if (perEffect <= 0 || !(item.getItemMeta() instanceof PotionMeta potion)) {
            return base;
        }
        double value = 0.0;
        // The base type carries the everyday brews; custom effects are stacked on
        if (potion.getBasePotionType() != null) {
            for (PotionEffect effect : potion.getBasePotionType().getPotionEffects()) {
                value += perEffect * (1 + effect.getAmplifier());
            }
        }
        for (PotionEffect effect : potion.getCustomEffects()) {
            value += perEffect * (1 + effect.getAmplifier());
        }
        return base + value;
    }

    /**
     * A value representing the enchantment quality of an item - multiply by a
     * config factor and add as a price fraction when selling gear.
     */
    public double getEnchantmentValue(ItemStack item) {
        if (item == null || item.getEnchantments().isEmpty()) {
            return 0.0;
        }
        double value = 0.0;
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            value += enchantWeight(entry.getKey()) * entry.getValue();
        }
        return value;
    }

    private double deriveFromRecipes(ItemStack item, Set<String> visited, int depth) {
        if (depth > MAX_RECIPE_DEPTH) {
            return -1;
        }
        String key = item.getType().name();
        Double known = cache.containsKey(key) ? cache.get(key) : addon.getSettings().getBasePrices().get(key);
        if (known != null) {
            return known;
        }
        if (!visited.add(key)) {
            return -1;
        }

        List<Recipe> recipes = recipeSource.apply(item);
        if (recipes.isEmpty()) {
            return -1;
        }
        Recipe recipe = recipes.get(0);
        int output = Math.max(1, recipe.getResult().getAmount());
        double cost = calculateRecipeCost(recipe, visited, depth);
        if (cost < 0) {
            return -1;
        }
        return cost / output;
    }

    /**
     * Calculate the cost for a specific recipe type.
     */
    private double calculateRecipeCost(Recipe recipe, Set<String> visited, int depth) {
        if (recipe instanceof ShapedRecipe shaped) {
            return sumIngredientCosts(shaped.getIngredientMap().values(), visited, depth);
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            return sumIngredientCosts(shapeless.getIngredientList(), visited, depth);
        } else if (recipe instanceof CookingRecipe<?> cooking) {
            return calculateCookingCost(cooking, visited, depth);
        } else if (recipe instanceof StonecuttingRecipe stonecutting) {
            return deriveFromRecipes(stonecutting.getInput(), new HashSet<>(visited), depth + 1);
        }
        return -1;
    }

    /**
     * Sum the costs of multiple ingredients.
     */
    private double sumIngredientCosts(java.util.Collection<ItemStack> ingredients, Set<String> visited, int depth) {
        double cost = 0;
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType().isAir()) {
                continue;
            }
            double ingredientCost = deriveFromRecipes(ingredient, new HashSet<>(visited), depth + 1);
            if (ingredientCost < 0) {
                return -1;
            }
            cost += ingredientCost * ingredient.getAmount();
        }
        return cost;
    }

    /**
     * Calculate the cost for a cooking recipe (includes fuel).
     */
    private double calculateCookingCost(CookingRecipe<?> cooking, Set<String> visited, int depth) {
        double inputCost = deriveFromRecipes(cooking.getInput(), new HashSet<>(visited), depth + 1);
        if (inputCost < 0) {
            return -1;
        }
        // A coal smelts 8 items: each output carries its share of fuel
        double fuelCost = addon.getSettings().getBasePrices().getOrDefault("COAL", 1.0) / 8.0;
        return inputCost + fuelCost;
    }

    private double applyDurability(ItemStack item, double base) {
        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return base;
        }
        if (item.getItemMeta() instanceof Damageable damageable && damageable.hasDamage()
                && damageable.getDamage() > 0) {
            return base * (1.0 - (double) damageable.getDamage() / maxDurability);
        }
        return base;
    }

    private double enchantWeight(Enchantment enchantment) {
        if (enchantment.equals(Enchantment.THORNS) || enchantment.equals(Enchantment.INFINITY)
                || enchantment.equals(Enchantment.SILK_TOUCH)) {
            return 10.0;
        }
        if (enchantment.equals(Enchantment.AQUA_AFFINITY) || enchantment.equals(Enchantment.BLAST_PROTECTION)
                || enchantment.equals(Enchantment.RESPIRATION) || enchantment.equals(Enchantment.FIRE_ASPECT)
                || enchantment.equals(Enchantment.LOOTING) || enchantment.equals(Enchantment.FLAME)
                || enchantment.equals(Enchantment.PUNCH) || enchantment.equals(Enchantment.FORTUNE)) {
            return 5.0;
        }
        if (enchantment.equals(Enchantment.BANE_OF_ARTHROPODS) || enchantment.equals(Enchantment.KNOCKBACK)
                || enchantment.equals(Enchantment.SMITE)) {
            return 2.5;
        }
        if (enchantment.equals(Enchantment.FEATHER_FALLING) || enchantment.equals(Enchantment.FIRE_PROTECTION)
                || enchantment.equals(Enchantment.PROJECTILE_PROTECTION)) {
            return 2.0;
        }
        return 1.0;
    }

    /**
     * Converts UPPER_SNAKE_CASE to Title Case (e.g. OAK_LOG -> Oak Log).
     */
    public static String prettify(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        String[] parts = name.toLowerCase(java.util.Locale.ENGLISH).split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }
}
