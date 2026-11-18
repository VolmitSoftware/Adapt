package art.arcane.adapt.bukkit.compat;

import art.arcane.adapt.api.potion.AdaptPotionRegistry;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.potion.BrewingTask;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.function.Consumer;

public final class BrewingBridge {
  private BrewingBridge() {
  }

  @ApiBridge(value = BrewingRecipe.class, name = "getIngredient")
  public static Material ingredient(BrewingRecipe recipe) {
    Id ingredient = recipe.getIngredient();
    return ingredient == null ? null : BukkitRefs.material(ingredient);
  }

  @ApiBridge(value = BrewingRecipe.class, name = "getBasePotion")
  public static ItemStack basePotion(BrewingRecipe recipe) {
    return stack(recipe.getBasePotion());
  }

  @ApiBridge(value = BrewingRecipe.class, name = "getResult")
  public static ItemStack result(BrewingRecipe recipe) {
    return stack(recipe.getResult());
  }

  @ApiBridge(value = BrewingRecipe.BrewingRecipeBuilder.class, name = "ingredient")
  public static BrewingRecipe.BrewingRecipeBuilder ingredient(BrewingRecipe.BrewingRecipeBuilder builder,
                                                              Material ingredient) {
    return builder.ingredient(ingredient == null ? null : BukkitRefs.id(ingredient));
  }

  @ApiBridge(value = BrewingRecipe.BrewingRecipeBuilder.class, name = "basePotion")
  public static BrewingRecipe.BrewingRecipeBuilder basePotion(BrewingRecipe.BrewingRecipeBuilder builder,
                                                              ItemStack basePotion) {
    return builder.basePotion(ref(basePotion));
  }

  @ApiBridge(value = BrewingRecipe.BrewingRecipeBuilder.class, name = "result")
  public static BrewingRecipe.BrewingRecipeBuilder result(BrewingRecipe.BrewingRecipeBuilder builder,
                                                          ItemStack result) {
    return builder.result(ref(result));
  }

  @ApiBridge(value = BrewingTask.class, instance = false)
  public static BrewingTask create(BrewingRecipe recipe, Location location, UUID brewerId,
                                   Consumer<BrewingTask> completion) {
    return BrewingTask.create(recipe, block(location), brewerId, completion);
  }

  @ApiBridge(value = BrewingTask.class, instance = false)
  public static ItemStack decrease(ItemStack source, int amount) {
    return BukkitRefs.item(BrewingTask.decrease(BukkitRefs.item(source), amount));
  }

  @ApiBridge(value = BrewingTask.class, instance = false)
  public static boolean isValid(BrewingRecipe recipe, Location location) {
    return BrewingTask.isValid(recipe, block(location));
  }

  @ApiBridge(value = AdaptPotionRegistry.class, instance = false)
  public static void record(UUID playerId, PotionEffectType type) {
    AdaptPotionRegistry.record(playerId, type == null ? null : BukkitRefs.id(type));
  }

  @ApiBridge(value = AdaptPotionRegistry.class, instance = false)
  public static int strip(Player player) {
    return AdaptPotionRegistry.strip(BukkitRefs.player(player));
  }

  @ApiBridge(value = AdaptPotionRegistry.class, instance = false)
  public static void retainActive(Player player) {
    AdaptPotionRegistry.retainActive(BukkitRefs.player(player));
  }

  private static BlockRef block(Location location) {
    return location == null ? null : BukkitRefs.block(location.getBlock());
  }

  private static ItemRef ref(ItemStack stack) {
    return stack == null ? null : BukkitRefs.item(stack);
  }

  private static ItemStack stack(ItemRef item) {
    return item == null ? null : BukkitRefs.item(item);
  }
}
