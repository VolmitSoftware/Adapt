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
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import com.google.common.collect.Multimap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class CraftingMasterwork extends SimpleAdaptation<CraftingMasterwork.Config> {
  private final NamespacedKey attributeKey = new NamespacedKey(Adapt.instance, "masterwork_bonus");

  public CraftingMasterwork() {
    super("crafting-masterwork");
    registerConfiguration(Config.class);
    setIcon(Material.NETHERITE_INGOT);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_CHESTPLATE)
        .key("challenge_crafting_masterwork_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_CHESTPLATE)
            .key("challenge_crafting_masterwork_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_crafting_masterwork_50", "crafting.masterwork.pieces-forged", 50, 400);
    registerMilestone("challenge_crafting_masterwork_500", "crafting.masterwork.pieces-forged", 500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getRollChance(level), 0), 1);
    statLore(v, Form.pc(getBonusPercent(level), 0), 2);
    v.addLore(C.LIGHT_PURPLE + "~ " + C.GRAY + AdaptLanguage.text(CraftingMessages.MASTERWORK_LORE3));
  }

  static double masterworkChance(double base, double factor, double max, double levelPercent) {
    return Math.min(max, base + (levelPercent * factor));
  }

  static double bonusPercent(double base, double factor, double levelPercent) {
    return base + (levelPercent * factor);
  }

  static int bonusDurability(int baseMaxDurability, double bonusPercent) {
    return Math.max(1, (int) Math.round(Math.max(1, baseMaxDurability) * bonusPercent));
  }

  private double getRollChance(int level) {
    return masterworkChance(getConfig().rollChanceBase, getConfig().rollChanceFactor, getConfig().rollChanceMax, getLevelPercent(level));
  }

  private double getBonusPercent(int level) {
    return bonusPercent(getConfig().bonusPercentBase, getConfig().bonusPercentFactor, getLevelPercent(level));
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void on(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p) || e.isShiftClick()) {
      return;
    }

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

    if (ThreadLocalRandom.current().nextDouble() > getRollChance(level)) {
      return;
    }

    ItemStack forged = result.clone();
    Damageable meta = (Damageable) forged.getItemMeta();
    int baseMax = Math.max(1, forged.getType().getMaxDurability());
    int bonus = bonusDurability(baseMax, getBonusPercent(level));
    meta.setMaxDamage(baseMax + bonus);

    boolean gotAttribute = level >= getMaxLevel()
        && ThreadLocalRandom.current().nextDouble() <= getConfig().attributeChance
        && applyAttribute(forged.getType(), meta, tool);

    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
    lore.add(C.AQUA + AdaptLanguage.text(
        CraftingMessages.MASTERWORK_BONUS,
        trusted("bonus", C.GRAY + String.valueOf(bonus))
    ));
    if (gotAttribute) {
      lore.add(C.LIGHT_PURPLE + AdaptLanguage.text(CraftingMessages.MASTERWORK_ATTRIBUTE_TAG));
    }
    meta.setLore(lore);
    forged.setItemMeta(meta);
    e.setCurrentItem(forged);

    addStat(p, "crafting.masterwork.pieces-forged", 1);
    fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
        .burst(Particles.CRIT_MAGIC, 8, 0.2D)
        .particle(Particles.ENCHANTMENT_TABLE, 10, 0, 0.3D, 0, 0.4D, 0.4D)
        .chord(Sound.BLOCK_ANVIL_USE, 0.5F, 1.3F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, gotAttribute ? 1.9F : 1.4F);
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

  @ConfigDescription("Tools and armor you craft can roll bonus max durability, with a chance for a minor attribute bonus at full level.")
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
}
