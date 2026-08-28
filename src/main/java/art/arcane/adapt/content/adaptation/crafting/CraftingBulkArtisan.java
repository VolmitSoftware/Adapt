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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
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

public class CraftingBulkArtisan extends SimpleAdaptation<CraftingBulkArtisan.Config> {
  private final Cooldowns throttle = cooldowns();

  public CraftingBulkArtisan() {
    super("crafting-bulk-artisan");
    registerConfiguration(Config.class);
    setIcon(Material.CRAFTING_TABLE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.OAK_PLANKS)
        .key("challenge_crafting_bulk_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.CHEST)
            .key("challenge_crafting_bulk_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_crafting_bulk_1k", "crafting.bulk-artisan.batch-crafted", 1000, 400);
    registerMilestone("challenge_crafting_bulk_10k", "crafting.bulk-artisan.batch-crafted", 10000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(CraftingMessages.BULK_ARTISAN_LORE1));
    statLore(v, getBatchCap(level), 2);
  }

  static int batchCap(int base, double factor, double levelPercent) {
    return Math.max(1, base + (int) Math.round(levelPercent * factor));
  }

  private int getBatchCap(int level) {
    return batchCap(getConfig().batchCapBase, getConfig().batchCapFactor, getLevelPercent(level));
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p) || !e.isShiftClick()) {
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

    ItemStack result = recipe.getResult();
    if (result == null || result.getType().isAir()) {
      return;
    }

    Map<Material, Integer> perCraft = CraftingIngredients.perCraftCounts(recipe);
    if (perCraft == null) {
      return;
    }

    if (!throttle.isReady(p.getUniqueId(), getConfig().throttleMs)) {
      return;
    }
    throttle.mark(p.getUniqueId());

    int cap = getBatchCap(level);
    ItemStack template = result.clone();
    J.runEntity(p, () -> augmentBatch(p, perCraft, template, cap));
  }

  private void augmentBatch(Player p, Map<Material, Integer> perCraft, ItemStack template, int capItems) {
    if (!p.isOnline() || getActiveLevel(p) <= 0) {
      return;
    }

    int resultAmount = Math.max(1, template.getAmount());
    int maxCrafts = capItems / resultAmount;
    if (maxCrafts <= 0) {
      return;
    }

    for (Map.Entry<Material, Integer> entry : perCraft.entrySet()) {
      int required = entry.getValue();
      if (required <= 0) {
        continue;
      }
      maxCrafts = Math.min(maxCrafts, availablePlain(p, entry.getKey()) / required);
      if (maxCrafts <= 0) {
        return;
      }
    }

    int capacityItems = capacityFor(p, template);
    maxCrafts = Math.min(maxCrafts, capacityItems / resultAmount);
    if (maxCrafts <= 0) {
      return;
    }

    int crafts = maxCrafts;
    if (!payItemCost(p, "materials", null, crafts, () -> {
      for (Map.Entry<Material, Integer> entry : perCraft.entrySet()) {
        p.getInventory().removeItem(new ItemStack(entry.getKey(), entry.getValue() * crafts));
      }

      return true;
    })) {
      return;
    }

    int produced = maxCrafts * resultAmount;
    ItemStack give = template.clone();
    give.setAmount(produced);
    Map<Integer, ItemStack> leftovers = p.getInventory().addItem(give);
    for (ItemStack leftover : leftovers.values()) {
      if (leftover != null && !leftover.getType().isAir() && leftover.getAmount() > 0) {
        p.getWorld().dropItemNaturally(p.getLocation(), leftover);
      }
    }

    addStat(p, "crafting.bulk-artisan.batch-crafted", produced);
    xp(p, produced * getConfig().xpPerBatchItem);
    fx(p.getLocation().add(0, 1, 0), FxPriority.AMBIENT)
        .particle(Particles.CRIT_MAGIC, Math.min(16, 4 + produced / 4), 0, 0.2D, 0, 0.35D, 0.2D)
        .chord(Sound.BLOCK_BARREL_CLOSE, 0.5F, 1.4F, Sound.ENTITY_ITEM_PICKUP, 0.5F, 1.2F);
  }

  private int availablePlain(Player p, Material material) {
    ItemStack unit = new ItemStack(material);
    int total = 0;
    for (ItemStack slot : p.getInventory().getStorageContents()) {
      if (slot != null && slot.isSimilar(unit)) {
        total += slot.getAmount();
      }
    }
    return total;
  }

  private int capacityFor(Player p, ItemStack template) {
    int maxStack = Math.max(1, template.getMaxStackSize());
    int capacity = 0;
    for (ItemStack slot : p.getInventory().getStorageContents()) {
      if (slot == null || slot.getType().isAir()) {
        capacity += maxStack;
      } else if (slot.isSimilar(template)) {
        capacity += Math.max(0, maxStack - slot.getAmount());
      }
    }
    return capacity;
  }

  @ConfigDescription("Shift-click a craft result to pull extra ingredients from your inventory and craft a bigger batch in one action.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus crafted items available at level 1 per shift-craft.", impact = "Higher values allow larger batches even at low levels.")
    int batchCapBase = 32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional bonus crafted items granted across levels.", impact = "Higher values raise the maximum batch size at full level.")
    double batchCapFactor = 96;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Skill XP granted per bonus item produced.", impact = "Higher values reward bulk crafting with more XP.")
    double xpPerBatchItem = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum delay in milliseconds between bulk batches.", impact = "Higher values throttle rapid shift-clicks more aggressively.")
    long throttleMs = 250;

    public Config() {
      baseCost = 3;
      costFactor = 0.3;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
