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
import art.arcane.adapt.localization.catalog.CraftingMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import com.google.common.collect.Multimap;
import io.papermc.paper.event.inventory.ItemCraftedEvent;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class CraftingMasterwork extends SimpleAdaptation<CraftingMasterwork.Config> {
  private static final byte MASTERWORK_FLAG = 1;
  private static final byte ATTRIBUTE_FLAG = 2;
  private static final byte ENCHANT_FLAG = 4;
  private static final Set<String> BENEFICIAL_ENCHANTMENTS = Set.of(
      "protection",
      "fire_protection",
      "feather_falling",
      "blast_protection",
      "projectile_protection",
      "respiration",
      "aqua_affinity",
      "depth_strider",
      "sharpness",
      "smite",
      "bane_of_arthropods",
      "knockback",
      "fire_aspect",
      "looting",
      "sweeping_edge",
      "efficiency",
      "unbreaking",
      "fortune"
  );

  private final NamespacedKey attributeKey = new NamespacedKey(Adapt.instance, "masterwork_bonus");
  private final NamespacedKey masterworkKey = new NamespacedKey(Adapt.instance, "masterwork_roll");
  private final Map<UUID, ShiftBatch> shiftBatches = new ConcurrentHashMap<>();
  private final Set<UUID> pendingSingleMasterworks = ConcurrentHashMap.newKeySet();

  public CraftingMasterwork() {
    super("crafting-masterwork");
    registerConfiguration(Config.class);
    setIcon(Material.NETHERITE_INGOT);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_CHESTPLATE)
        .key("challenge_crafting_masterwork_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_CHESTPLATE)
            .key("challenge_crafting_masterwork_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_crafting_masterwork_50", "crafting.masterwork.pieces-forged", 50, 400);
    registerMilestone("challenge_crafting_masterwork_500", "crafting.masterwork.pieces-forged", 500, 1500);
  }

  @Override
  public void unregister() {
    shiftBatches.clear();
    pendingSingleMasterworks.clear();
    super.unregister();
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getRollChance(level), 0), 1);
    statLore(v, Form.pc(getBonusPercent(level), 0), 2);
    statLore(v, Form.pc(clampChance(getConfig().enchantmentChance), 0), 3);
    v.addLore(C.LIGHT_PURPLE + "~ " + C.GRAY + AdaptLanguage.text(CraftingMessages.MASTERWORK_LORE4));
  }

  static double masterworkChance(double base, double factor, double max, double levelPercent) {
    return clampChance(Math.min(max, base + (levelPercent * factor)));
  }

  static double bonusPercent(double base, double factor, double levelPercent) {
    return Math.max(0.0D, base + (levelPercent * factor));
  }

  static double rolledBonusPercent(double maximum, double minimumFraction, double randomUnit) {
    double safeMaximum = Math.max(0.0D, maximum);
    double safeMinimumFraction = Math.max(0.0D, Math.min(1.0D, minimumFraction));
    double safeRandomUnit = Math.max(0.0D, Math.min(1.0D, randomUnit));
    double minimum = safeMaximum * safeMinimumFraction;
    return minimum + ((safeMaximum - minimum) * safeRandomUnit);
  }

  static int bonusDurability(int baseMaxDurability, double bonusPercent) {
    return Math.max(1, (int) Math.round(Math.max(1, baseMaxDurability) * bonusPercent));
  }

  static double clampChance(double chance) {
    return Math.max(0.0D, Math.min(1.0D, chance));
  }

  private double getRollChance(int level) {
    return masterworkChance(getConfig().rollChanceBase, getConfig().rollChanceFactor, getConfig().rollChanceMax, getLevelPercent(level));
  }

  private double getBonusPercent(int level) {
    return bonusPercent(getConfig().bonusPercentBase, getConfig().bonusPercentFactor, getLevelPercent(level));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }
    UUID playerId = p.getUniqueId();
    shiftBatches.remove(playerId);
    pendingSingleMasterworks.remove(playerId);

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    Recipe recipe = e.getRecipe();
    if (!(recipe instanceof ShapedRecipe) && !(recipe instanceof ShapelessRecipe)) {
      return;
    }

    ItemStack result = e.getCurrentItem();
    if (result == null || result.getType().isAir()) {
      return;
    }

    boolean tool = isTool(result.getType());
    boolean armor = isArmor(result.getType());
    if ((!tool && !armor) || !(result.getItemMeta() instanceof Damageable)) {
      return;
    }

    ShiftBatch batch = null;
    if (e.isShiftClick()) {
      NamespacedKey recipeKey = recipeKey(recipe);
      if (recipeKey == null) {
        return;
      }
      ShiftBatch createdBatch = new ShiftBatch(new ShiftBatchSpec(result.getType(), recipeKey, level), result);
      batch = createdBatch;
      shiftBatches.put(playerId, createdBatch);
      J.s(() -> shiftBatches.remove(playerId, createdBatch), 1);
    }

    ItemStack forged = forge(result, level, tool);
    boolean masterwork = forged != result;
    if (batch != null) {
      batch.expectMasterwork(masterwork);
    } else if (masterwork) {
      pendingSingleMasterworks.add(playerId);
      J.s(() -> pendingSingleMasterworks.remove(playerId), 1);
    }
    if (forged != result) {
      e.setCurrentItem(forged);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareItemCraftEvent e) {
    if (!(e.getView().getPlayer() instanceof Player p)) {
      return;
    }

    ShiftBatch batch = shiftBatches.get(p.getUniqueId());
    ItemStack result = e.getInventory().getResult();
    Recipe recipe = e.getRecipe();
    if (batch == null || !batch.matches(recipe, result)) {
      return;
    }

    ItemStack prepared = batch.getPreparedResult();
    if (prepared == null && batch.isAwaitingNextResult()) {
      ItemStack baseResult = batch.createBaseResult(result.getAmount());
      boolean tool = isTool(baseResult.getType());
      prepared = forge(baseResult, batch.getLevel(), tool);
      batch.cachePreparedResult(prepared, prepared != baseResult);
    }
    if (prepared != null) {
      e.getInventory().setResult(prepared.clone());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ItemCraftedEvent e) {
    Player p = e.getPlayer();
    ItemStack crafted = e.getCraftedItem();
    ShiftBatch batch = shiftBatches.get(p.getUniqueId());
    boolean expectedMasterwork = false;
    if (batch != null && batch.matchesType(crafted)) {
      expectedMasterwork = batch.consumeExpectedMasterwork();
      batch.awaitNextResult();
    } else {
      expectedMasterwork = pendingSingleMasterworks.remove(p.getUniqueId());
    }

    if (!expectedMasterwork) {
      return;
    }
    ItemMeta meta = crafted.getItemMeta();
    if (meta == null) {
      return;
    }
    Byte flags = meta.getPersistentDataContainer().get(masterworkKey, PersistentDataType.BYTE);
    if (flags == null || (flags & MASTERWORK_FLAG) == 0) {
      return;
    }

    addStat(p, "crafting.masterwork.pieces-forged", 1);
    if (batch == null || batch.claimEffect()) {
      playForgeEffect(p, (flags & ATTRIBUTE_FLAG) != 0, (flags & ENCHANT_FLAG) != 0);
    }
  }

  private ItemStack forge(ItemStack result, int level, boolean tool) {
    if (ThreadLocalRandom.current().nextDouble() >= getRollChance(level)) {
      return result;
    }

    ItemStack forged = result.clone();
    Damageable meta = (Damageable) forged.getItemMeta();
    int baseMax = Math.max(1, forged.getType().getMaxDurability());
    double rolledPercent = rolledBonusPercent(
        getBonusPercent(level),
        getConfig().bonusRollMinimumFraction,
        ThreadLocalRandom.current().nextDouble()
    );
    int bonus = bonusDurability(baseMax, rolledPercent);
    meta.setMaxDamage(baseMax + bonus);

    boolean gotAttribute = level >= getMaxLevel()
        && ThreadLocalRandom.current().nextDouble() < clampChance(getConfig().attributeChance)
        && applyAttribute(forged.getType(), meta, tool);
    boolean gotEnchant = ThreadLocalRandom.current().nextDouble() < clampChance(getConfig().enchantmentChance)
        && applyBeneficialEnchant(forged, meta);

    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
    lore.add(C.AQUA + AdaptLanguage.text(
        CraftingMessages.MASTERWORK_BONUS,
        trusted("bonus", C.GRAY + String.valueOf(bonus))
    ));
    if (gotAttribute) {
      lore.add(C.LIGHT_PURPLE + AdaptLanguage.text(CraftingMessages.MASTERWORK_ATTRIBUTE_TAG));
    }
    meta.setLore(lore);
    byte flags = MASTERWORK_FLAG;
    if (gotAttribute) {
      flags |= ATTRIBUTE_FLAG;
    }
    if (gotEnchant) {
      flags |= ENCHANT_FLAG;
    }
    meta.getPersistentDataContainer().set(masterworkKey, PersistentDataType.BYTE, flags);
    forged.setItemMeta(meta);
    return forged;
  }

  private boolean applyAttribute(Material material, Damageable meta, boolean tool) {
    Attribute attribute = tool ? Attributes.ATTACK_DAMAGE : Attributes.ARMOR;
    if (attribute == null) {
      return false;
    }

    if (!meta.hasAttributeModifiers()) {
      Multimap<Attribute, AttributeModifier> defaults = material.getDefaultAttributeModifiers();
      for (Map.Entry<Attribute, AttributeModifier> entry : defaults.entries()) {
        meta.addAttributeModifier(entry.getKey(), entry.getValue());
      }
    }

    double amount = tool ? getConfig().attackDamageBonus : getConfig().armorBonus;
    EquipmentSlotGroup slot = tool ? EquipmentSlotGroup.MAINHAND : EquipmentSlotGroup.ARMOR;
    meta.addAttributeModifier(attribute, new AttributeModifier(attributeKey, amount, AttributeModifier.Operation.ADD_NUMBER, slot));
    return true;
  }

  private boolean applyBeneficialEnchant(ItemStack item, ItemMeta meta) {
    Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
    List<Enchantment> candidates = new ArrayList<>();
    for (Enchantment enchantment : registry) {
      if (!NamespacedKey.MINECRAFT.equals(enchantment.getKey().getNamespace())
          || !BENEFICIAL_ENCHANTMENTS.contains(enchantment.getKey().getKey())
          || !enchantment.canEnchantItem(item)
          || conflictsWithExisting(meta, enchantment)) {
        continue;
      }
      candidates.add(enchantment);
    }
    if (candidates.isEmpty()) {
      return false;
    }

    Enchantment selected = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    return meta.addEnchant(selected, Math.max(1, selected.getStartLevel()), false);
  }

  private boolean conflictsWithExisting(ItemMeta meta, Enchantment candidate) {
    for (Enchantment existing : meta.getEnchants().keySet()) {
      if (existing.equals(candidate) || existing.conflictsWith(candidate)) {
        return true;
      }
    }
    return false;
  }

  private void playForgeEffect(Player p, boolean gotAttribute, boolean gotEnchant) {
    float chimePitch = gotAttribute ? 1.9F : gotEnchant ? 1.65F : 1.4F;
    fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
        .burst(Particles.CRIT_MAGIC, 8, 0.2D)
        .particle(Particles.ENCHANTMENT_TABLE, 10, 0, 0.3D, 0, 0.4D, 0.4D)
        .chord(Sound.BLOCK_ANVIL_USE, 0.5F, 1.3F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, chimePitch);
  }

  private NamespacedKey recipeKey(Recipe recipe) {
    if (recipe instanceof ShapedRecipe shapedRecipe) {
      return shapedRecipe.getKey();
    }
    if (recipe instanceof ShapelessRecipe shapelessRecipe) {
      return shapelessRecipe.getKey();
    }
    return null;
  }

  private boolean isTool(Material type) {
    String name = type.name();
    return name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL")
        || name.endsWith("_HOE") || name.endsWith("_SWORD");
  }

  private boolean isArmor(Material type) {
    String name = type.name();
    return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
        || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
  }

  @ConfigDescription("Each crafted tool and armor piece independently rolls variable bonus durability, a minor beneficial enchantment, and a full-level attribute bonus.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance to roll a masterwork piece at level 1.", impact = "Higher values roll bonus durability more often at low levels.")
    double rollChanceBase = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional masterwork roll chance gained across levels.", impact = "Higher values scale the roll chance more steeply.")
    double rollChanceFactor = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum masterwork roll chance at full level.", impact = "Higher values raise the ceiling on the roll chance.")
    double rollChanceMax = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of base durability granted as a bonus at level 1.", impact = "Higher values grant more bonus durability even at low levels.")
    double bonusPercentBase = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional bonus durability fraction gained across levels.", impact = "Higher values scale the durability bonus more steeply.")
    double bonusPercentFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum fraction of the level-scaled durability bonus rolled by a masterwork piece.", impact = "Higher values make masterwork durability rolls more consistent and closer to their maximum.")
    double bonusRollMinimumFraction = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance for a masterwork piece to gain one compatible level-one beneficial enchantment.", impact = "Higher values add minor beneficial enchantments to masterwork gear more often.")
    double enchantmentChance = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance for a minor attribute bonus at full level.", impact = "Higher values roll the extra attribute bonus more often.")
    double attributeChance = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Attack damage granted by a masterwork tool attribute bonus.", impact = "Higher values make masterwork tools hit harder.")
    double attackDamageBonus = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Armor granted by a masterwork armor attribute bonus.", impact = "Higher values make masterwork armor more protective.")
    double armorBonus = 1.0;

    public Config() {
      baseCost = 4;
      costFactor = 0.4;
      maxLevel = 5;
      initialCost = 5;
    }
  }

  static final class ShiftBatch {
    private final ShiftBatchSpec spec;
    private final ItemStack baseResult;
    private ItemStack preparedResult;
    private boolean awaitingNextResult;
    private boolean expectedMasterwork;
    private boolean effectClaimed;

    ShiftBatch(ShiftBatchSpec spec, ItemStack baseResult) {
      this.spec = spec;
      this.baseResult = baseResult.clone();
    }

    int getLevel() {
      return spec.level();
    }

    boolean matches(Recipe recipe, ItemStack result) {
      if (result == null || result.getType() != spec.resultType()) {
        return false;
      }
      NamespacedKey currentKey;
      if (recipe instanceof ShapedRecipe shapedRecipe) {
        currentKey = shapedRecipe.getKey();
      } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
        currentKey = shapelessRecipe.getKey();
      } else {
        return false;
      }
      return spec.recipeKey().equals(currentKey);
    }

    boolean matchesType(ItemStack item) {
      return item != null && item.getType() == spec.resultType();
    }

    ItemStack createBaseResult(int amount) {
      ItemStack created = baseResult.clone();
      created.setAmount(amount);
      return created;
    }

    void awaitNextResult() {
      preparedResult = null;
      awaitingNextResult = true;
    }

    boolean isAwaitingNextResult() {
      return awaitingNextResult;
    }

    void cachePreparedResult(ItemStack result, boolean masterwork) {
      preparedResult = result.clone();
      awaitingNextResult = false;
      expectedMasterwork = masterwork;
    }

    ItemStack getPreparedResult() {
      return preparedResult == null ? null : preparedResult.clone();
    }

    void expectMasterwork(boolean masterwork) {
      expectedMasterwork = masterwork;
    }

    boolean consumeExpectedMasterwork() {
      boolean expected = expectedMasterwork;
      expectedMasterwork = false;
      return expected;
    }

    boolean claimEffect() {
      if (effectClaimed) {
        return false;
      }
      effectClaimed = true;
      return true;
    }
  }

  record ShiftBatchSpec(Material resultType, NamespacedKey recipeKey, int level) {
  }
}
