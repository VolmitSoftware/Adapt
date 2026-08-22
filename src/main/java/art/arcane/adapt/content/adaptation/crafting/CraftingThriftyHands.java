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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class CraftingThriftyHands extends SimpleAdaptation<CraftingThriftyHands.Config> {

  public CraftingThriftyHands() {
    super("crafting-thrifty-hands");
    registerConfiguration(Config.class);
    setIcon(Material.STRING);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.STRING)
        .key("challenge_crafting_thrifty_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.LEAD)
            .key("challenge_crafting_thrifty_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_crafting_thrifty_500", "crafting.thrifty-hands.ingredients-refunded", 500, 400);
    registerMilestone("challenge_crafting_thrifty_5k", "crafting.thrifty-hands.ingredients-refunded", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getRefundChance(level), 0), 1);
  }

  static double refundChance(double base, double factor, double max, double levelPercent) {
    return Math.min(max, base + (levelPercent * factor));
  }

  private double getRefundChance(int level) {
    return refundChance(getConfig().refundChanceBase, getConfig().refundChanceFactor, getConfig().refundChanceMax, getLevelPercent(level));
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void on(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    Recipe recipe = e.getRecipe();
    if (recipe == null) {
      return;
    }

    Map<Material, Integer> perCraft = CraftingIngredients.perCraftCounts(recipe);
    if (perCraft == null) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getRefundChance(level)) {
      return;
    }

    Material refund = pickRandom(perCraft.keySet());
    if (refund == null) {
      return;
    }

    J.runEntity(p, () -> giveRefund(p, refund));
  }

  private Material pickRandom(Set<Material> materials) {
    if (materials.isEmpty()) {
      return null;
    }
    int target = ThreadLocalRandom.current().nextInt(materials.size());
    int index = 0;
    for (Material material : materials) {
      if (index == target) {
        return material;
      }
      index++;
    }
    return null;
  }

  private void giveRefund(Player p, Material refund) {
    if (!p.isOnline() || getActiveLevel(p) <= 0) {
      return;
    }

    ItemStack give = new ItemStack(refund, 1);
    Map<Integer, ItemStack> leftovers = p.getInventory().addItem(give);
    for (ItemStack leftover : leftovers.values()) {
      if (leftover != null && !leftover.getType().isAir() && leftover.getAmount() > 0) {
        p.getWorld().dropItemNaturally(p.getLocation(), leftover);
      }
    }

    addStat(p, "crafting.thrifty-hands.ingredients-refunded", 1);
    fx(p.getLocation().add(0, 1, 0), FxPriority.AMBIENT)
        .particle(Particles.CRIT_MAGIC, 3, 0, 0.2D, 0, 0.25D, 0.15D)
        .sound(Sound.ENTITY_ITEM_PICKUP, 0.4F, 1.6F);
  }

  @ConfigDescription("Every craft has a chance to refund one of its ingredients back into your inventory.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Refund chance at level 1.", impact = "Higher values refund ingredients more often even at low levels.")
    double refundChanceBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional refund chance gained across levels.", impact = "Higher values scale the refund chance more steeply.")
    double refundChanceFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum refund chance at full level.", impact = "Higher values raise the ceiling on refund chance.")
    double refundChanceMax = 0.6;

    public Config() {
      baseCost = 3;
      costFactor = 0.3;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
