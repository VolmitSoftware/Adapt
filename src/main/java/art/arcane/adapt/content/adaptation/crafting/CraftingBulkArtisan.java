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

import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.CommonPreferences;
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
import io.papermc.paper.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.api.xp.CraftingXpTransaction;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CraftingBulkArtisan extends SimpleAdaptation<CraftingBulkArtisan.Config> {
  public static final PlayerPreference<CraftingPreferences.Category> MATERIALS = CraftingPreferences.categories("materials", CraftingMessages.PREFERENCE_CRAFTINGBULKARTISAN_MATERIALS);
  public static final PlayerPreference<CommonPreferences.Scale> BATCH = CommonPreferences.scale("batch-limit", CraftingMessages.PREFERENCE_CRAFTINGBULKARTISAN_BATCH);
  public static final PlayerPreference<CraftingPreferences.Reserve> RESERVE = CraftingPreferences.reserve("ingredient-reserve", CraftingMessages.PREFERENCE_CRAFTINGBULKARTISAN_RESERVE);

  private final Map<UUID, PendingBatch> pendingBatches = playerState();
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
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, MATERIALS, BATCH, RESERVE);
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

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p) || !e.isShiftClick()) {
      return;
    }
    UUID playerId = p.getUniqueId();
    pendingBatches.remove(playerId);
    if (e.getAction() == InventoryAction.NOTHING || e.getAction() == InventoryAction.CLONE_STACK) {
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

    ItemStack result = e.getCurrentItem();
    if (result == null || result.getType().isAir() || !preference(p, MATERIALS).allows(result.getType())) {
      return;
    }

    List<RecipeChoice> perCraft = CraftingIngredients.perCraftChoices(recipe);
    if (perCraft.isEmpty()) {
      return;
    }

    if (!throttle.isReady(p.getUniqueId(), getConfig().throttleMs)) {
      return;
    }
    throttle.mark(p.getUniqueId());

    int cap = (int) Math.floor(getBatchCap(level) * preference(p, BATCH).multiplier());
    PendingBatch pending = new PendingBatch(e, perCraft, result.getType(), result.getAmount(), cap,
        !CraftingXpTransaction.isResourceConversion(e.getInventory().getMatrix(), result.getType()));
    pendingBatches.put(playerId, pending);
    J.runEntity(p, () -> pendingBatches.remove(playerId, pending), 1);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ItemCraftedEvent e) {
    Player player = e.getPlayer();
    PendingBatch pending = committedBatch(pendingBatches, player.getUniqueId(), e.getCraftedItem());
    if (pending != null) {
      ItemStack template = e.getCraftedItem().clone();
      template.setAmount(pending.resultAmount());
      J.runEntity(player, () -> augmentBatch(player, pending, template), 1);
    }
  }

  static PendingBatch committedBatch(Map<UUID, PendingBatch> pendingBatches, UUID playerId, ItemStack crafted) {
    PendingBatch pending = pendingBatches.remove(playerId);
    if (pending == null || pending.event().isCancelled() || crafted == null || crafted.getAmount() <= 0
        || crafted.getType() != pending.result()) {
      return null;
    }
    return pending;
  }

  private void augmentBatch(Player p, PendingBatch pending, ItemStack template) {
    if (!p.isOnline() || getActiveLevel(p) <= 0) {
      return;
    }

    int resultAmount = Math.max(1, template.getAmount());
    int maxCrafts = pending.cap() / resultAmount;
    if (maxCrafts <= 0) {
      return;
    }

    int capacityItems = capacityFor(p, template);
    maxCrafts = Math.min(maxCrafts, capacityItems / resultAmount);
    if (maxCrafts <= 0) {
      return;
    }

    ItemStack[] storage = p.getInventory().getStorageContents();
    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(pending.choices(), storage, maxCrafts, preference(p, RESERVE).amount());
    if (plan.crafts() <= 0 || !payItemCost(p, "materials", null, plan.crafts(), () -> consumePlan(p, storage, plan))) {
      return;
    }

    int produced = plan.crafts() * resultAmount;
    ItemStack give = template.clone();
    give.setAmount(produced);
    Map<Integer, ItemStack> leftovers = p.getInventory().addItem(give);
    for (ItemStack leftover : leftovers.values()) {
      if (leftover != null && !leftover.getType().isAir() && leftover.getAmount() > 0) {
        p.getWorld().dropItemNaturally(p.getLocation(), leftover);
      }
    }

    rewardBatch(p, pending, produced);
    fx(p.getLocation().add(0, 1, 0), FxPriority.AMBIENT)
        .particle(Particles.CRIT_MAGIC, Math.min(16, 4 + produced / 4), 0, 0.2D, 0, 0.35D, 0.2D)
        .chord(Sound.BLOCK_BARREL_CLOSE, 0.5F, 1.4F, Sound.ENTITY_ITEM_PICKUP, 0.5F, 1.2F);
  }

  void rewardBatch(Player player, PendingBatch pending, int produced) {
    if (!pending.rewardEligible() || produced <= 0) {
      return;
    }
    addStat(player, "crafting.bulk-artisan.batch-crafted", produced);
    xp(player, produced * getConfig().xpPerBatchItem);
  }

  private boolean consumePlan(Player p, ItemStack[] storage, CraftingIngredients.BatchPlan plan) {
    for (int slot = 0; slot < plan.costs().length; slot++) {
      int cost = plan.costs()[slot];
      ItemStack current = p.getInventory().getItem(slot);
      if (cost > 0 && (current == null || !current.isSimilar(storage[slot]) || current.getAmount() < cost)) {
        return false;
      }
    }
    for (int slot = 0; slot < plan.costs().length; slot++) {
      int cost = plan.costs()[slot];
      if (cost > 0) {
        ItemStack remaining = p.getInventory().getItem(slot).clone();
        remaining.setAmount(remaining.getAmount() - cost);
        p.getInventory().setItem(slot, remaining.getAmount() > 0 ? remaining : null);
      }
    }
    return true;
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

  record PendingBatch(CraftItemEvent event, List<RecipeChoice> choices, Material result,
                      int resultAmount, int cap, boolean rewardEligible) {
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
