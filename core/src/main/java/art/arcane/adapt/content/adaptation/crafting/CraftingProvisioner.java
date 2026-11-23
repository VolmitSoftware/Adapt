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
import art.arcane.adapt.platform.event.block.FurnaceSmeltEvent;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public class CraftingProvisioner extends SimpleAdaptation<CraftingProvisioner.Config> {
  private final Map<UUID, PendingPortions> pendingPortions = playerState();

  public CraftingProvisioner() {
    super("crafting-provisioner");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("cooked_beef"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bread"))
        .key("challenge_crafting_provisioner_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("cake"))
            .key("challenge_crafting_provisioner_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_crafting_provisioner_500", "crafting.provisioner.bonus-portions", 500, 400);
    registerMilestone("challenge_crafting_provisioner_5k", "crafting.provisioner.bonus-portions", 5000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getBonusChance(level), 0), 1);
    statLore(v, getBonusPortions(level), 2);
  }

  static double bonusChance(double base, double factor, double max, double levelPercent) {
    return Math.min(max, base + (levelPercent * factor));
  }

  static int bonusPortions(int base, double factor, double levelPercent) {
    return Math.max(1, base + (int) Math.round(levelPercent * factor));
  }

  private double getBonusChance(int level) {
    return bonusChance(getConfig().bonusChanceBase, getConfig().bonusChanceFactor, getConfig().bonusChanceMax, getLevelPercent(level));
  }

  private int getBonusPortions(int level) {
    return bonusPortions(getConfig().bonusPortionsBase, getConfig().bonusPortionsFactor, getLevelPercent(level));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    UUID playerId = p.uuid();
    pendingPortions.remove(playerId);
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

    ItemRef result = recipe.get().result();
    if (result == null || result.isEmpty() || !items().isEdible(result.type())) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getBonusChance(level)) {
      return;
    }

    PendingPortions pending = new PendingPortions(e, result.type(), getBonusPortions(level));
    pendingPortions.put(playerId, pending);
    Tasks.runEntity(p, () -> pendingPortions.remove(playerId, pending), 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ItemCraftedEvent e) {
    PlayerRef player = e.player();
    PendingPortions pending = committedPortions(pendingPortions, player.uuid(), e.crafted());
    if (pending != null) {
      Tasks.runEntity(player, () -> giveBonusFood(player, pending.food(), pending.portions()), 1);
    }
  }

  static PendingPortions committedPortions(Map<UUID, PendingPortions> pendingPortions, UUID playerId, ItemRef crafted) {
    PendingPortions pending = pendingPortions.remove(playerId);
    if (pending == null || pending.event().dispatchCancelled() || crafted == null || crafted.amount() <= 0
        || !crafted.type().equals(pending.food())) {
      return null;
    }
    return pending;
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(FurnaceSmeltEvent e) {
    ItemRef result = e.result();
    if (result == null || result.isEmpty() || !items().isEdible(result.type())) {
      return;
    }

    Loc furnace = e.block().location();
    PlayerRef beneficiary = nearestEligiblePlayer(furnace);
    if (beneficiary == null) {
      return;
    }

    int level = getActiveLevel(beneficiary);
    if (ThreadLocalRandom.current().nextDouble() > getBonusChance(level)) {
      return;
    }

    int portions = getBonusPortions(level);
    ItemRef boosted = result.copy();
    boosted.setAmount(Math.min(boosted.maxStackSize(), boosted.amount() + portions));
    int granted = boosted.amount() - result.amount();
    if (granted <= 0) {
      return;
    }
    e.setResult(boosted);
    Tasks.runEntity(beneficiary, () -> addStat(beneficiary, "crafting.provisioner.bonus-portions", granted));
    Loc above = furnace.add(0.5D, 1.0D, 0.5D);
    fx(above, FxPriority.AMBIENT)
        .particle(Particles.CRIT_MAGIC, 4, 0, 0.2D, 0, 0.2D, 0.15D)
        .sound(Sounds.ENTITY_GENERIC_EAT, 0.4F, 1.4F);
  }

  private PlayerRef nearestEligiblePlayer(Loc furnace) {
    double radius = getConfig().cookingRadius;
    if (!Double.isFinite(radius)) {
      return null;
    }
    double searchRadius = Math.abs(radius);
    return nearestEligiblePlayer(
        furnace,
        searchRadius,
        furnace.world().players(),
        player -> getActiveLevel(player) > 0
    );
  }

  static PlayerRef nearestEligiblePlayer(
      Loc furnace,
      double radius,
      Iterable<? extends PlayerRef> candidates,
      Predicate<PlayerRef> eligible
  ) {
    double radiusSq = radius * radius;
    PlayerRef nearest = null;
    double nearestSq = Double.MAX_VALUE;
    for (PlayerRef player : candidates) {
      double distanceSq = player.location().distanceSquared(furnace);
      if (distanceSq > radiusSq || distanceSq >= nearestSq || !eligible.test(player)) {
        continue;
      }
      nearest = player;
      nearestSq = distanceSq;
    }
    return nearest;
  }

  private void giveBonusFood(PlayerRef p, Id food, int portions) {
    if (!p.isOnline() || getActiveLevel(p) <= 0) {
      return;
    }

    ItemRef give = items().create(food, portions);
    List<ItemRef> leftovers = p.inventory().addItem(give);
    for (ItemRef leftover : leftovers) {
      if (leftover != null && !leftover.isEmpty() && !leftover.type().isAir() && leftover.amount() > 0) {
        p.world().dropItemNaturally(p.location().position(), leftover);
      }
    }

    addStat(p, "crafting.provisioner.bonus-portions", portions);
    fx(p.location().add(0, 1, 0), FxPriority.AMBIENT)
        .particle(Particles.CRIT_MAGIC, 4, 0, 0.2D, 0, 0.2D, 0.15D)
        .sound(Sounds.ENTITY_GENERIC_EAT, 0.4F, 1.5F);
  }

  private static ItemOps items() {
    return AdaptCore.platform().host().items();
  }

  record PendingPortions(CraftItemEvent event, Id food, int portions) {
  }

  @ConfigDescription("Crafting or cooking food has a chance to yield bonus portions.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus portion chance at level 1.", impact = "Higher values grant bonus food more often at low levels.")
    double bonusChanceBase = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional bonus portion chance gained across levels.", impact = "Higher values scale the bonus chance more steeply.")
    double bonusChanceFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum bonus portion chance at full level.", impact = "Higher values raise the ceiling on bonus chance.")
    double bonusChanceMax = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus portions granted at level 1.", impact = "Higher values grant more food per proc even at low levels.")
    int bonusPortionsBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional bonus portions gained across levels.", impact = "Higher values grant more food per proc at higher levels.")
    double bonusPortionsFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius in blocks searched for a player when cooking food.", impact = "Higher values credit players farther from the furnace.")
    double cookingRadius = 8.0;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 1;
    }
  }
}
