/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AdvancementMessages;
import art.arcane.adapt.localization.catalog.CraftingMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CraftingDeconstruction extends SimpleAdaptation<CraftingDeconstruction.Config> {
  public CraftingDeconstruction() {
    super("crafting-deconstruction");
    registerConfiguration(Config.class);
    setIcon(Material.SHEARS);
    setInterval(5590);
    AdvancementSpec deconstruction5k = AdvancementSpec.challenge(
        "challenge_crafting_decon_5k",
        Material.IRON_INGOT,
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_DECON_5K_TITLE),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_DECON_5K_DESCRIPTION)
    );
    AdvancementSpec deconstruction200 = AdvancementSpec.challenge(
        "challenge_crafting_decon_200",
        Material.SHEARS,
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_DECON_200_TITLE),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_DECON_200_DESCRIPTION)
    ).withChild(deconstruction5k);
    registerAdvancementSpec(deconstruction200);
    registerStatTracker(deconstruction200.statTracker("crafting.deconstruction.items-deconstructed", 200, 300));
    registerStatTracker(deconstruction5k.statTracker("crafting.deconstruction.items-deconstructed", 5000, 1000));
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(CraftingMessages.DECONSTRUCTION_LORE1));
    v.addLore(C.GREEN + AdaptLanguage.text(CraftingMessages.DECONSTRUCTION_LORE2));
  }

  public List<ItemStack> getDeconstructionOfferings(ItemStack forStuff) {
    if (forStuff == null || forStuff.getType().isAir() || forStuff.getAmount() <= 0
        || !hasEligibleRepairState(forStuff)) {
      return List.of();
    }

    List<ItemStack> best = List.of();
    int bestIngredientCount = 0;
    for (Recipe recipe : Bukkit.getRecipesFor(recipeLookupItem(forStuff))) {
      List<ItemStack> offering = getDeconstructionOfferings(forStuff, recipe);
      int ingredientCount = ingredientCount(recipe);
      if (isLowerValue(forStuff, offering) && ingredientCount > bestIngredientCount) {
        best = offering;
        bestIngredientCount = ingredientCount;
      }
    }
    return best;
  }

  static boolean hasEligibleRepairState(ItemStack source) {
    if (!isArmor(source.getType())) {
      return true;
    }
    return source.getItemMeta() instanceof Damageable damageable && damageable.getDamage() == 0;
  }

  static ItemStack recipeLookupItem(ItemStack source) {
    if (!isArmor(source.getType())) {
      return source;
    }
    ItemStack lookup = source.clone();
    lookup.setItemMeta(null);
    return lookup;
  }

  static List<ItemStack> getDeconstructionOfferings(ItemStack source, Recipe recipe) {
    if (source == null || recipe == null || source.getAmount() <= 0) {
      return List.of();
    }

    ItemStack recipeResult = recipe.getResult();
    int outputAmount = Math.max(1, recipeResult.getAmount());
    Map<Material, Integer> ingredients = ingredientCounts(recipe);
    Material selected = null;
    int selectedCount = 0;
    for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
      if (entry.getValue() > selectedCount) {
        selected = entry.getKey();
        selectedCount = entry.getValue();
      }
    }
    if (selected == null) {
      return List.of();
    }

    int salvage = salvageAmount(selectedCount, source.getAmount(), outputAmount);
    return splitOfferings(selected, salvage, selected.getMaxStackSize());
  }

  static int salvageAmount(int ingredientCount, int sourceAmount, int outputAmount) {
    if (ingredientCount <= 0 || sourceAmount <= 0 || outputAmount <= 0) {
      return 0;
    }
    return ((ingredientCount * sourceAmount) / outputAmount) / 2;
  }

  static List<Integer> splitAmounts(int amount, int maxStackSize) {
    if (amount <= 0 || maxStackSize <= 0) {
      return List.of();
    }
    List<Integer> split = new ArrayList<>((amount + maxStackSize - 1) / maxStackSize);
    int remaining = amount;
    while (remaining > 0) {
      int next = Math.min(remaining, maxStackSize);
      split.add(next);
      remaining -= next;
    }
    return List.copyOf(split);
  }

  private static List<ItemStack> splitOfferings(Material material, int amount, int maxStackSize) {
    List<Integer> amounts = splitAmounts(amount, maxStackSize);
    if (amounts.isEmpty()) {
      return List.of();
    }
    List<ItemStack> offerings = new ArrayList<>(amounts.size());
    for (int split : amounts) {
      offerings.add(new ItemStack(material, split));
    }
    return List.copyOf(offerings);
  }

  static int shapedIngredientCount(String[] shape, Map<Character, ?> choices) {
    if (shape == null || choices == null || choices.isEmpty()) {
      return 0;
    }

    int count = 0;
    for (String row : shape) {
      if (row == null) {
        continue;
      }
      for (int column = 0; column < row.length(); column++) {
        if (choices.containsKey(row.charAt(column))) {
          count++;
        }
      }
    }
    return count;
  }

  static <T> Map<T, Integer> shapedIngredientCounts(String[] shape, Map<Character, T> choices) {
    Map<T, Integer> counts = new LinkedHashMap<>();
    if (shape == null || choices == null || choices.isEmpty()) {
      return counts;
    }

    for (String row : shape) {
      if (row == null) {
        continue;
      }
      for (int column = 0; column < row.length(); column++) {
        T choice = choices.get(row.charAt(column));
        if (choice != null) {
          counts.merge(choice, 1, Integer::sum);
        }
      }
    }
    return counts;
  }

  private static int ingredientCount(Recipe recipe) {
    int count = 0;
    for (int amount : ingredientCounts(recipe).values()) {
      count += amount;
    }
    return count;
  }

  private static Map<Material, Integer> ingredientCounts(Recipe recipe) {
    Map<Material, Integer> counts = new LinkedHashMap<>();
    if (recipe instanceof ShapelessRecipe shapeless) {
      mergeChoices(counts, shapeless.getChoiceList());
    } else if (recipe instanceof ShapedRecipe shaped) {
      Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
      int expectedIngredientCount = shapedIngredientCount(shaped.getShape(), choices);
      Map<Character, Material> materials = new LinkedHashMap<>();
      for (Map.Entry<Character, RecipeChoice> entry : choices.entrySet()) {
        Material material = representativeMaterial(entry.getValue());
        if (material != null && material != Material.AIR) {
          materials.put(entry.getKey(), material);
        }
      }
      counts.putAll(shapedIngredientCounts(shaped.getShape(), materials));
      if (ingredientTotal(counts) != expectedIngredientCount) {
        counts.clear();
      }
    }
    return counts;
  }

  private static int ingredientTotal(Map<Material, Integer> counts) {
    int total = 0;
    for (int amount : counts.values()) {
      total += amount;
    }
    return total;
  }

  private static void mergeChoices(Map<Material, Integer> counts, Iterable<RecipeChoice> choices) {
    for (RecipeChoice choice : choices) {
      mergeChoice(counts, choice);
    }
  }

  private static void mergeChoice(Map<Material, Integer> counts, RecipeChoice choice) {
    Material material = representativeMaterial(choice);
    if (material != null && material != Material.AIR) {
      counts.merge(material, 1, Integer::sum);
    }
  }

  private static Material representativeMaterial(RecipeChoice choice) {
    if (choice instanceof RecipeChoice.ExactChoice exact) {
      List<ItemStack> choices = exact.getChoices();
      return choices.isEmpty() ? null : choices.getFirst().getType();
    }
    if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
      List<Material> choices = materialChoice.getChoices();
      return choices.isEmpty() ? null : choices.getFirst();
    }
    return null;
  }

  private static boolean isArmor(Material type) {
    String name = type.name();
    return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
        || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
  }


  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (e.getHand() == EquipmentSlot.OFF_HAND) {
      return;
    }

    Player player = e.getPlayer();
    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(player)) {
      return;
    }
    ItemStack mainHandItem = player.getInventory().getItemInMainHand();
    if (!hasActiveAdaptation(player)) {
      return;
    }

    if (!player.isSneaking() || mainHandItem.getType() != Material.SHEARS) {
      return;
    }

    // Perform a ray trace for 6 blocks looking for an item
    Location eyeLocation = player.getEyeLocation();
    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(eyeLocation, 6.0D, 6.0D)) {
      return;
    }
    RayTraceResult rayTrace = player.getWorld().rayTraceEntities(
        eyeLocation,
        eyeLocation.getDirection(),
        6,
        entity -> entity instanceof Item
    );
    if (rayTrace != null && rayTrace.getHitEntity() instanceof Item itemEntity) {
      processItemInteraction(player, mainHandItem, itemEntity);
    }
  }

  private void processItemInteraction(Player player, ItemStack mainHandItem, Item itemEntity) {
    if (J.isFoliaThreading()
        && (!J.isOwnedByCurrentRegion(player) || !J.isOwnedByCurrentRegion(itemEntity))) {
      return;
    }
    if (!canSnatchItem(player, itemEntity)) {
      return;
    }

    ItemStack forStuff = itemEntity.getItemStack().clone();
    List<ItemStack> offerings = getDeconstructionOfferings(forStuff);
    Location itemLocation = itemEntity.getLocation();

    if (!offerings.isEmpty()) {
      ItemStack offering = offerings.getFirst();
      double salvageValue = getValue(offering) * totalAmount(offerings);
      if (!ProtectionEventProbe.attemptItemPickup(player, itemEntity, 0)) {
        return;
      }
      if (J.isFoliaThreading()
          && (!J.isOwnedByCurrentRegion(player) || !J.isOwnedByCurrentRegion(itemEntity))) {
        return;
      }
      if (!itemEntity.isValid() || itemEntity.isDead()) {
        return;
      }
      ItemStack current = itemEntity.getItemStack();
      if (!current.isSimilar(forStuff) || current.getAmount() != forStuff.getAmount()) {
        return;
      }

      itemEntity.setItemStack(offering);
      for (int index = 1; index < offerings.size(); index++) {
        itemEntity.getWorld().dropItem(itemLocation, offerings.get(index));
      }
      fx(itemLocation, FxPriority.COMBAT)
          .particle(Particles.ITEM_CRACK, 18, 0, 0, 0, 0.15D, 0.15D, forStuff)
          .ring(Particle.WAX_ON, 0.5D, 10, 0.2D)
          .chord(Sound.BLOCK_BASALT_BREAK, 1F, 0.2F, Sound.BLOCK_BEEHIVE_SHEAR, 1F, 0.7F, Sound.ITEM_SHIELD_BREAK, 0.4F, 1.4F);
      xp(player, salvageValue, "deconstruct");
      addStat(player, "crafting.deconstruction.items-deconstructed", 1);

      Damageable damageable = (Damageable) mainHandItem.getItemMeta();
      int newDamage = damageable.getDamage() + 8 * forStuff.getAmount();
      int maxDurability = mainHandItem.getType().getMaxDurability();
      if (newDamage >= maxDurability) {
        player.getInventory().setItemInMainHand(null);
        fx(itemLocation, FxPriority.COMBAT)
            .particle(Particles.ITEM_CRACK, 6, 0, 0.1D, 0, 0.2D, 0.05D, new ItemStack(Material.SHEARS))
            .sound(Sound.ENTITY_ITEM_BREAK, 0.8F, 1.0F);
      } else {
        damageable.setDamage(newDamage);
        mainHandItem.setItemMeta(damageable);
        if (newDamage >= maxDurability * 0.9D) {
          fx(itemLocation, FxPriority.TRANSITION)
              .particle(Particle.ELECTRIC_SPARK, 6, 0, 0.1D, 0, 0.2D, 0.05D)
              .burst(Particles.CRIT_MAGIC, 3, 0.15D)
              .sound(Sound.ENTITY_ITEM_BREAK, 0.4F, 1.6F);
        }
      }
    } else {
      fx(itemLocation, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 4, 0.15D)
          .particle(Particle.WAX_ON, 2, 0, 0.2D, 0, 0.06D, 0.02D)
          .chord(Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1F, 1F, Sound.BLOCK_FIRE_EXTINGUISH, 0.3F, 1.5F);
    }
  }

  private boolean isLowerValue(ItemStack source, List<ItemStack> offerings) {
    if (offerings.isEmpty()) {
      return false;
    }
    double salvageValue = getValue(offerings.getFirst()) * totalAmount(offerings);
    double sourceValue = getValue(source) * source.getAmount();
    return salvageValue < sourceValue;
  }

  private static int totalAmount(List<ItemStack> stacks) {
    int total = 0;
    for (ItemStack stack : stacks) {
      total += stack.getAmount();
    }
    return total;
  }



  @ConfigDescription("Deconstruct blocks and items into salvageable base components using shears.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 9;
      costFactor = 1.0;
      initialCost = 8;
      maxLevel = 1;
    }
  }
}
