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
import io.papermc.paper.event.inventory.ItemCraftedEvent;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CraftingThriftyHands extends SimpleAdaptation<CraftingThriftyHands.Config> {
  private final Map<UUID, PendingRefund> pendingRefunds = playerState();

  public CraftingThriftyHands() {
    super("crafting-thrifty-hands");
    registerConfiguration(Config.class);
    setIcon(Material.STRING);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.STRING)
        .key("challenge_crafting_thrifty_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.LEAD)
            .key("challenge_crafting_thrifty_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
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

  static Set<Material> refundMaterials(ItemStack[] matrix) {
    Set<Material> materials = new HashSet<>();
    for (ItemStack ingredient : matrix) {
      if (ingredient == null || ingredient.getAmount() <= 0) {
        continue;
      }
      Material material = ingredient.getType();
      if (material != Material.AIR && material != Material.CAVE_AIR && material != Material.VOID_AIR) {
        materials.add(material);
      }
    }
    return materials.size() >= 3 ? materials : Set.of();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }
    UUID playerId = p.getUniqueId();
    pendingRefunds.remove(playerId);

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    Recipe recipe = e.getRecipe();
    if (!(recipe instanceof ShapedRecipe) && !(recipe instanceof ShapelessRecipe)) {
      return;
    }

    if (e.getAction() == InventoryAction.NOTHING || e.getAction() == InventoryAction.CLONE_STACK) {
      return;
    }

    ItemStack result = e.getCurrentItem();
    if (result == null || result.getType().isAir() || result.getAmount() <= 0) {
      return;
    }

    Set<Material> materials = refundMaterials(e.getInventory().getMatrix());
    if (materials.isEmpty()) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getRefundChance(level)) {
      return;
    }

    Material refund = pickRandom(materials);
    if (refund == null) {
      return;
    }

    PendingRefund pending = new PendingRefund(e, refund, result.getType());
    pendingRefunds.put(playerId, pending);
    J.runEntity(p, () -> pendingRefunds.remove(playerId, pending), 1);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ItemCraftedEvent e) {
    Player player = e.getPlayer();
    Material refund = committedRefund(pendingRefunds, player.getUniqueId(), e.getCraftedItem());
    if (refund != null) {
      J.runEntity(player, () -> giveRefund(player, refund), 1);
    }
  }

  static Material committedRefund(Map<UUID, PendingRefund> pendingRefunds, UUID playerId, ItemStack crafted) {
    PendingRefund pending = pendingRefunds.remove(playerId);
    if (pending == null || pending.event().isCancelled() || crafted == null || crafted.getAmount() <= 0
        || crafted.getType() != pending.result()) {
      return null;
    }
    return pending.material();
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

  record PendingRefund(CraftItemEvent event, Material material, Material result) {
  }

  @ConfigDescription("Crafts using at least three different materials have a chance to refund one ingredient unit.")
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
