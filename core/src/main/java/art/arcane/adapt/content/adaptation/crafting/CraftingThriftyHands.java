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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CraftingThriftyHands extends SimpleAdaptation<CraftingThriftyHands.Config> {
  private final Map<UUID, PendingRefund> pendingRefunds = playerState();

  public CraftingThriftyHands() {
    super("crafting-thrifty-hands");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("string"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("string"))
        .key("challenge_crafting_thrifty_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("lead"))
            .key("challenge_crafting_thrifty_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_crafting_thrifty_500", "crafting.thrifty-hands.ingredients-refunded", 500, 400);
    registerMilestone("challenge_crafting_thrifty_5k", "crafting.thrifty-hands.ingredients-refunded", 5000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getRefundChance(level), 0), 1);
  }

  static double refundChance(double base, double factor, double max, double levelPercent) {
    return Math.min(max, base + (levelPercent * factor));
  }

  private double getRefundChance(int level) {
    return refundChance(getConfig().refundChanceBase, getConfig().refundChanceFactor, getConfig().refundChanceMax, getLevelPercent(level));
  }

  static Set<Id> refundMaterials(List<ItemRef> matrix) {
    Set<Id> materials = new HashSet<>();
    for (ItemRef ingredient : matrix) {
      if (ingredient == null || ingredient.amount() <= 0) {
        continue;
      }
      Id material = ingredient.type();
      if (!material.isAir()) {
        materials.add(material);
      }
    }
    return materials.size() >= 3 ? materials : Set.of();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    UUID playerId = p.uuid();
    pendingRefunds.remove(playerId);

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (e.recipeKind() != CraftItemEvent.RecipeKind.SHAPED && e.recipeKind() != CraftItemEvent.RecipeKind.SHAPELESS) {
      return;
    }

    if (e.action() == InventoryAction.NOTHING || e.action() == InventoryAction.CLONE_STACK) {
      return;
    }

    ItemRef result = e.currentItem();
    if (result == null || result.isEmpty() || result.type().isAir() || result.amount() <= 0) {
      return;
    }

    Set<Id> materials = refundMaterials(e.matrix());
    if (materials.isEmpty()) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getRefundChance(level)) {
      return;
    }

    Id refund = pickRandom(materials);
    if (refund == null) {
      return;
    }

    PendingRefund pending = new PendingRefund(e, refund, result.type());
    pendingRefunds.put(playerId, pending);
    Tasks.runEntity(p, () -> pendingRefunds.remove(playerId, pending), 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ItemCraftedEvent e) {
    PlayerRef player = e.player();
    Id refund = committedRefund(pendingRefunds, player.uuid(), e.crafted());
    if (refund != null) {
      Tasks.runEntity(player, () -> giveRefund(player, refund), 1);
    }
  }

  static Id committedRefund(Map<UUID, PendingRefund> pendingRefunds, UUID playerId, ItemRef crafted) {
    PendingRefund pending = pendingRefunds.remove(playerId);
    if (pending == null || pending.event().dispatchCancelled() || crafted == null || crafted.amount() <= 0
        || !crafted.type().equals(pending.result())) {
      return null;
    }
    return pending.material();
  }

  private Id pickRandom(Set<Id> materials) {
    if (materials.isEmpty()) {
      return null;
    }
    int target = ThreadLocalRandom.current().nextInt(materials.size());
    int index = 0;
    for (Id material : materials) {
      if (index == target) {
        return material;
      }
      index++;
    }
    return null;
  }

  private void giveRefund(PlayerRef p, Id refund) {
    if (!p.isOnline() || getActiveLevel(p) <= 0) {
      return;
    }

    ItemRef give = AdaptCore.platform().host().items().create(refund, 1);
    List<ItemRef> leftovers = p.inventory().addItem(give);
    for (ItemRef leftover : leftovers) {
      if (leftover != null && !leftover.isEmpty() && !leftover.type().isAir() && leftover.amount() > 0) {
        p.world().dropItemNaturally(p.location().position(), leftover);
      }
    }

    addStat(p, "crafting.thrifty-hands.ingredients-refunded", 1);
    fx(p.location().add(0, 1, 0), FxPriority.AMBIENT)
        .particle(Particles.CRIT_MAGIC, 3, 0, 0.2D, 0, 0.25D, 0.15D)
        .sound(Sounds.ENTITY_ITEM_PICKUP, 0.4F, 1.6F);
  }

  record PendingRefund(CraftItemEvent event, Id material, Id result) {
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
