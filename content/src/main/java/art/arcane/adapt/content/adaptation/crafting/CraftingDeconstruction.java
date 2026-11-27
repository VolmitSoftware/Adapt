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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeChoice;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CraftingDeconstruction extends SimpleAdaptation<CraftingDeconstruction.Config> {
  public static final Id ID = Id.adapt("crafting/deconstruction");
  private static final Id SHEARS = Id.minecraft("shears");
  public static final PlayerPreference<CraftingPreferences.Category> MATERIALS = CraftingPreferences.categories("materials", Text.key("adapt.crafting.preferences.craftingdeconstruction.materials"));
  public static final PlayerPreference<CommonPreferences.Toggle> CONFIRM = CommonPreferences.toggle("confirmation", Text.key("adapt.crafting.preferences.craftingdeconstruction.confirmation"), CommonPreferences.Toggle.OFF);

  public CraftingDeconstruction() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(SHEARS);
    setInterval(5590);
    AdaptAdvancement deconstruction5k = AdaptAdvancement.builder().key("challenge_crafting_decon_5k").icon(Id.minecraft("iron_ingot")).title(Text.key("adapt.advancement.challenge_crafting_decon_5k.title")).description(Text.key("adapt.advancement.challenge_crafting_decon_5k.description")).frame(AdvancementFrame.CHALLENGE).visibility(AdvancementVisibility.VANILLA).build();
    AdaptAdvancement deconstruction200 = AdaptAdvancement.builder().key("challenge_crafting_decon_200").icon(SHEARS).title(Text.key("adapt.advancement.challenge_crafting_decon_200.title")).description(Text.key("adapt.advancement.challenge_crafting_decon_200.description")).frame(AdvancementFrame.CHALLENGE).visibility(AdvancementVisibility.VANILLA).child(deconstruction5k).build();
    registerAdvancement(deconstruction200);
    registerMilestone(deconstruction200.key(), "crafting.deconstruction.items-deconstructed", 200, 300);
    registerMilestone(deconstruction5k.key(), "crafting.deconstruction.items-deconstructed", 5000, 1000);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, MATERIALS, CONFIRM);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.crafting.deconstruction.lore1").resolve());
    v.addLore(TextFormat.GREEN + Text.key("adapt.crafting.deconstruction.lore2").resolve());
  }

  public List<ItemRef> getDeconstructionOfferings(ItemRef forStuff) {
    if (!salvageable(forStuff)) {
      return List.of();
    }

    List<ItemRef> best = List.of();
    int bestIngredientCount = 0;
    for (RecipeView recipe : AdaptApi.platform().recipes().recipesFor(recipeLookupItem(forStuff))) {
      List<ItemRef> offering = getDeconstructionOfferings(forStuff, recipe);
      int ingredientCount = ingredientCount(recipe);
      if (isLowerValue(forStuff, offering) && ingredientCount > bestIngredientCount) {
        best = offering;
        bestIngredientCount = ingredientCount;
      }
    }
    return best;
  }

  static boolean salvageable(ItemRef source) {
    return source != null && !source.isEmpty() && !source.type().isAir() && source.amount() > 0
        && !GolemItems.isGolemItem(source) && hasEligibleRepairState(source);
  }

  static boolean hasEligibleRepairState(ItemRef source) {
    if (!isArmor(source.type())) {
      return true;
    }
    return source.damage() == 0;
  }

  static ItemRef recipeLookupItem(ItemRef source) {
    if (!isArmor(source.type())) {
      return source;
    }
    return items().create(source.type(), source.amount());
  }

  static List<ItemRef> getDeconstructionOfferings(ItemRef source, RecipeView recipe) {
    if (source == null || recipe == null || source.amount() <= 0) {
      return List.of();
    }

    ItemRef recipeResult = recipe.result();
    int outputAmount = Math.max(1, recipeResult.amount());
    Map<Id, Integer> ingredients = ingredientCounts(recipe);
    Id selected = null;
    int selectedCount = 0;
    for (Map.Entry<Id, Integer> entry : ingredients.entrySet()) {
      if (entry.getValue() > selectedCount) {
        selected = entry.getKey();
        selectedCount = entry.getValue();
      }
    }
    if (selected == null) {
      return List.of();
    }

    int salvage = salvageAmount(selectedCount, source.amount(), outputAmount);
    return splitOfferings(selected, salvage, items().maxStackSize(selected));
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

  private static List<ItemRef> splitOfferings(Id material, int amount, int maxStackSize) {
    List<Integer> amounts = splitAmounts(amount, maxStackSize);
    if (amounts.isEmpty()) {
      return List.of();
    }
    List<ItemRef> offerings = new ArrayList<>(amounts.size());
    for (int split : amounts) {
      offerings.add(items().create(material, split));
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
        if (choices.get(row.charAt(column)) != null) {
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

  private static int ingredientCount(RecipeView recipe) {
    int count = 0;
    for (int amount : ingredientCounts(recipe).values()) {
      count += amount;
    }
    return count;
  }

  private static Map<Id, Integer> ingredientCounts(RecipeView recipe) {
    Map<Id, Integer> counts = new LinkedHashMap<>();
    if (recipe.kind() == RecipeView.Kind.SHAPELESS) {
      for (RecipeChoice choice : recipe.choices()) {
        Id material = choice.representativeType();
        if (material != null && !material.isAir()) {
          counts.merge(material, 1, Integer::sum);
        }
      }
    } else if (recipe.kind() == RecipeView.Kind.SHAPED) {
      String[] shape = recipe.shape().toArray(new String[0]);
      int expectedIngredientCount = shapedIngredientCount(shape, recipe.shapedChoices());
      Map<Character, Id> materials = new LinkedHashMap<>();
      for (Map.Entry<Character, RecipeChoice> entry : recipe.shapedChoices().entrySet()) {
        Id material = entry.getValue().representativeType();
        if (material != null && !material.isAir()) {
          materials.put(entry.getKey(), material);
        }
      }
      counts.putAll(shapedIngredientCounts(shape, materials));
      if (ingredientTotal(counts) != expectedIngredientCount) {
        counts.clear();
      }
    }
    return counts;
  }

  private static int ingredientTotal(Map<Id, Integer> counts) {
    int total = 0;
    for (int amount : counts.values()) {
      total += amount;
    }
    return total;
  }

  private static boolean isArmor(Id type) {
    String name = type.path().toUpperCase(Locale.ROOT);
    return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
        || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
  }



  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    if (e.hand() == Hand.OFF_HAND) {
      return;
    }

    PlayerRef player = e.player();
    if (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(player)) {
      return;
    }
    ItemRef mainHandItem = player.inventory().mainHand();
    if (!hasActiveAdaptation(player)) {
      return;
    }

    if (!player.isSneaking() || !mainHandItem.type().equals(SHEARS)) {
      return;
    }

    Loc eyeLocation = player.eyeLocation();
    if (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().ownsArea(eyeLocation, 6.0D, 6.0D)) {
      return;
    }
    EntityRef hit = player.world().rayTraceEntity(
        eyeLocation.position(),
        eyeLocation.direction(),
        6,
        0.0D,
        (EntityRef entity) -> entity instanceof ItemEntityRef
    );
    if (hit instanceof ItemEntityRef itemEntity) {
      processItemInteraction(player, mainHandItem, itemEntity);
    }
  }

  private void processItemInteraction(PlayerRef player, ItemRef mainHandItem, ItemEntityRef itemEntity) {
    if (AdaptApi.scheduler().isRegionized()
        && (!AdaptApi.scheduler().owns(player) || !AdaptApi.scheduler().owns(itemEntity))) {
      return;
    }
    if (!canSnatchItem(player, itemEntity)) {
      return;
    }

    ItemRef forStuff = itemEntity.item().copy();
    if (preferenceEnabled(player, CONFIRM)
        && (forStuff.hasDisplayName() || !forStuff.enchantments().isEmpty())
        && !confirm(player, "salvage:" + itemEntity.uuid(), forStuff)) {
      return;
    }
    if (!preference(player, MATERIALS).allows(forStuff.type())) {
      return;
    }
    List<ItemRef> offerings = getDeconstructionOfferings(forStuff);
    Loc itemLocation = itemEntity.location();

    if (!offerings.isEmpty()) {
      ItemRef offering = offerings.getFirst();
      double salvageValue = getValue(offering) * totalAmount(offerings);
      if (!ProtectionProbes.attemptItemPickup(player, itemEntity, 0)) {
        return;
      }
      if (AdaptApi.scheduler().isRegionized()
          && (!AdaptApi.scheduler().owns(player) || !AdaptApi.scheduler().owns(itemEntity))) {
        return;
      }
      if (!itemEntity.isValid() || itemEntity.isDead()) {
        return;
      }
      ItemRef current = itemEntity.item();
      if (!current.isSimilar(forStuff) || current.amount() != forStuff.amount()) {
        return;
      }

      itemEntity.setItem(offering);
      for (int index = 1; index < offerings.size(); index++) {
        itemLocation.world().dropItem(itemLocation.position(), offerings.get(index));
      }
      fx(itemLocation, FxPriority.COMBAT)
          .particle(Particles.itemCrack(forStuff), 18, 0, 0, 0, 0.15D, 0.15D)
          .ring(Particles.WAX_ON, 0.5D, 10, 0.2D)
          .chord(Sounds.BLOCK_BASALT_BREAK, 1F, 0.2F, Sounds.BLOCK_BEEHIVE_SHEAR, 1F, 0.7F, Sounds.ITEM_SHIELD_BREAK, 0.4F, 1.4F);
      xp(player, salvageValue, "deconstruct");
      addStat(player, "crafting.deconstruction.items-deconstructed", 1);
      Mutations.landed(this, player);

      int newDamage = mainHandItem.damage() + 8 * forStuff.amount();
      int maxDurability = items().maxDurability(mainHandItem.type());
      if (newDamage >= maxDurability) {
        player.inventory().setMainHand(items().empty());
        fx(itemLocation, FxPriority.COMBAT)
            .particle(Particles.itemCrack(items().create(SHEARS, 1)), 6, 0, 0.1D, 0, 0.2D, 0.05D)
            .sound(Sounds.ENTITY_ITEM_BREAK, 0.8F, 1.0F);
      } else {
        mainHandItem.setDamage(newDamage);
        player.inventory().setMainHand(mainHandItem);
        if (newDamage >= maxDurability * 0.9D) {
          fx(itemLocation, FxPriority.TRANSITION)
              .particle(Particles.ELECTRIC_SPARK, 6, 0, 0.1D, 0, 0.2D, 0.05D)
              .burst(Particles.CRIT, 3, 0.15D)
              .sound(Sounds.ENTITY_ITEM_BREAK, 0.4F, 1.6F);
        }
      }
    } else {
      fx(itemLocation, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 4, 0.15D)
          .particle(Particles.WAX_ON, 2, 0, 0.2D, 0, 0.06D, 0.02D)
          .chord(Sounds.BLOCK_REDSTONE_TORCH_BURNOUT, 1F, 1F, Sounds.BLOCK_FIRE_EXTINGUISH, 0.3F, 1.5F);
    }
  }

  private boolean isLowerValue(ItemRef source, List<ItemRef> offerings) {
    if (offerings.isEmpty()) {
      return false;
    }
    double salvageValue = getValue(offerings.getFirst()) * totalAmount(offerings);
    double sourceValue = getValue(source) * source.amount();
    return salvageValue < sourceValue;
  }

  private static int totalAmount(List<ItemRef> stacks) {
    int total = 0;
    for (ItemRef stack : stacks) {
      total += stack.amount();
    }
    return total;
  }

  private static ItemOps items() {
    return AdaptApi.platform().host().items();
  }


  @ConfigDescription("Deconstruct blocks and items into salvageable base components using shears.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 8;
      costFactor = 1;
      initialCost = 8;
      maxLevel = 1;
    }
  }
}
