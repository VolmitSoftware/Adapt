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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.xp.CraftingXpTransaction;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.recipe.RecipeChoice;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class CraftingBulkArtisan extends SimpleAdaptation<CraftingBulkArtisan.Config> {
  public static final PlayerPreference<CraftingPreferences.Category> MATERIALS = CraftingPreferences.categories("materials", CraftingMessages.PREFERENCE_CRAFTINGBULKARTISAN_MATERIALS);
  public static final PlayerPreference<CommonPreferences.Scale> BATCH = CommonPreferences.scale("batch-limit", CraftingMessages.PREFERENCE_CRAFTINGBULKARTISAN_BATCH);
  public static final PlayerPreference<CraftingPreferences.Reserve> RESERVE = CraftingPreferences.reserve("ingredient-reserve", CraftingMessages.PREFERENCE_CRAFTINGBULKARTISAN_RESERVE);

  private final Map<UUID, PendingBatch> pendingBatches = playerState();
  private final Cooldowns throttle = cooldowns();
  static final int STORAGE_SLOTS = 36;

  public CraftingBulkArtisan() {
    super("crafting-bulk-artisan");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("crafting_table"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("oak_planks"))
        .key("challenge_crafting_bulk_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("chest"))
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
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(CraftingMessages.BULK_ARTISAN_LORE1));
    statLore(v, getBatchCap(level), 2);
  }

  static int batchCap(int base, double factor, double levelPercent) {
    return Math.max(1, base + (int) Math.round(levelPercent * factor));
  }

  private int getBatchCap(int level) {
    return batchCap(getConfig().batchCapBase, getConfig().batchCapFactor, getLevelPercent(level));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    if (e.click() != ClickKind.SHIFT_LEFT && e.click() != ClickKind.SHIFT_RIGHT) {
      return;
    }
    UUID playerId = p.uuid();
    pendingBatches.remove(playerId);
    if (e.action() == InventoryAction.NOTHING || e.action() == InventoryAction.CLONE_STACK) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    Optional<RecipeView> recipe = e.recipeKey() == null ? Optional.empty()
        : AdaptCore.platform().recipes().recipe(e.recipeKey());
    if (recipe.isEmpty()) {
      return;
    }

    ItemRef result = e.currentItem();
    if (result == null || result.isEmpty() || result.type().isAir() || !preference(p, MATERIALS).allows(result.type())) {
      return;
    }

    List<RecipeChoice> perCraft = CraftingIngredients.perCraftChoices(recipe.get());
    if (perCraft.isEmpty()) {
      return;
    }

    if (!throttle.isReady(p.uuid(), getConfig().throttleMs)) {
      return;
    }
    throttle.mark(p.uuid());

    int cap = (int) Math.floor(getBatchCap(level) * preference(p, BATCH).multiplier());
    PendingBatch pending = new PendingBatch(e, perCraft, result.type(), result.amount(), cap,
        !CraftingXpTransaction.isResourceConversion(e.matrix(), result.type()));
    pendingBatches.put(playerId, pending);
    Tasks.runEntity(p, () -> pendingBatches.remove(playerId, pending), 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ItemCraftedEvent e) {
    PlayerRef player = e.player();
    PendingBatch pending = committedBatch(pendingBatches, player.uuid(), e.crafted());
    if (pending != null) {
      ItemRef template = e.crafted().copy();
      template.setAmount(pending.resultAmount());
      Tasks.runEntity(player, () -> augmentBatch(player, pending, template), 1);
    }
  }

  static PendingBatch committedBatch(Map<UUID, PendingBatch> pendingBatches, UUID playerId, ItemRef crafted) {
    PendingBatch pending = pendingBatches.remove(playerId);
    if (pending == null || pending.event().dispatchCancelled() || crafted == null || crafted.amount() <= 0
        || !crafted.type().equals(pending.result())) {
      return null;
    }
    return pending;
  }

  private void augmentBatch(PlayerRef p, PendingBatch pending, ItemRef template) {
    if (!p.isOnline() || getActiveLevel(p) <= 0) {
      return;
    }

    int resultAmount = Math.max(1, template.amount());
    int maxCrafts = pending.cap() / resultAmount;
    if (maxCrafts <= 0) {
      return;
    }

    int capacityItems = capacityFor(p, template);
    maxCrafts = Math.min(maxCrafts, capacityItems / resultAmount);
    if (maxCrafts <= 0) {
      return;
    }

    ItemRef[] storage = storageContents(p.inventory());
    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(pending.choices(), storage, maxCrafts, preference(p, RESERVE).amount());
    if (plan.crafts() <= 0 || !payItemCost(p, "materials", null, plan.crafts(), () -> consumePlan(p, storage, plan))) {
      return;
    }

    int produced = plan.crafts() * resultAmount;
    ItemRef give = template.copy();
    give.setAmount(produced);
    List<ItemRef> leftovers = p.inventory().addItem(give);
    for (ItemRef leftover : leftovers) {
      if (leftover != null && !leftover.isEmpty() && !leftover.type().isAir() && leftover.amount() > 0) {
        p.world().dropItemNaturally(p.location().position(), leftover);
      }
    }

    rewardBatch(p, pending, produced);
    fx(p.location().add(0, 1, 0), FxPriority.AMBIENT)
        .particle(Particles.CRIT_MAGIC, Math.min(16, 4 + produced / 4), 0, 0.2D, 0, 0.35D, 0.2D)
        .chord(Sounds.BLOCK_BARREL_CLOSE, 0.5F, 1.4F, Sounds.ENTITY_ITEM_PICKUP, 0.5F, 1.2F);
  }

  void rewardBatch(PlayerRef player, PendingBatch pending, int produced) {
    if (!pending.rewardEligible() || produced <= 0) {
      return;
    }
    addStat(player, "crafting.bulk-artisan.batch-crafted", produced);
    xp(player, produced * getConfig().xpPerBatchItem);
  }

  private boolean consumePlan(PlayerRef p, ItemRef[] storage, CraftingIngredients.BatchPlan plan) {
    PlayerInventoryRef inventory = p.inventory();
    for (int slot = 0; slot < plan.costs().length; slot++) {
      int cost = plan.costs()[slot];
      ItemRef current = inventory.item(slot);
      if (cost > 0 && (current.isEmpty() || storage[slot] == null || !current.isSimilar(storage[slot]) || current.amount() < cost)) {
        return false;
      }
    }
    for (int slot = 0; slot < plan.costs().length; slot++) {
      int cost = plan.costs()[slot];
      if (cost > 0) {
        ItemRef remaining = inventory.item(slot).copy();
        remaining.setAmount(remaining.amount() - cost);
        if (remaining.amount() > 0) {
          inventory.setItem(slot, remaining);
        } else {
          inventory.clear(slot);
        }
      }
    }
    return true;
  }

  private int capacityFor(PlayerRef p, ItemRef template) {
    int maxStack = Math.max(1, template.maxStackSize());
    int capacity = 0;
    for (ItemRef slot : storageContents(p.inventory())) {
      if (slot == null || slot.type().isAir()) {
        capacity += maxStack;
      } else if (slot.isSimilar(template)) {
        capacity += Math.max(0, maxStack - slot.amount());
      }
    }
    return capacity;
  }

  /** The 36 main storage slots in slot order, empty slots as null. */
  static ItemRef[] storageContents(PlayerInventoryRef inventory) {
    ItemRef[] storage = new ItemRef[STORAGE_SLOTS];
    for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
      ItemRef item = inventory.item(slot);
      storage[slot] = item == null || item.isEmpty() ? null : item;
    }
    return storage;
  }

  record PendingBatch(CraftItemEvent event, List<RecipeChoice> choices, Id result,
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
