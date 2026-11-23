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

package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
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
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;

public class ExcavationBurrow extends SimpleAdaptation<ExcavationBurrow.Config> {
  public static final PlayerPreference<CommonPreferences.Scale> DEPTH = CommonPreferences.scale("depth", ExcavationMessages.PREFERENCE_EXCAVATIONBURROW_DEPTH);
  public static final PlayerPreference<CommonPreferences.Toggle> FOOD_RESERVE = CommonPreferences.toggle("food-reserve", ExcavationMessages.PREFERENCE_EXCAVATIONBURROW_FOOD_RESERVE, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> DURABILITY_RESERVE = CommonPreferences.toggle("durability-reserve", ExcavationMessages.PREFERENCE_EXCAVATIONBURROW_DURABILITY_RESERVE, CommonPreferences.Toggle.OFF);

  private static final Id LAVA = Id.minecraft("lava");
  private final Cooldowns cooldowns = cooldowns();

  public ExcavationBurrow() {
    super("excavation-burrow");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("coarse_dirt"));
    setInterval(4130);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("coarse_dirt"))
        .key("challenge_excavation_burrow_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_excavation_burrow_100", "excavation.burrow.burrows-dug", 100, 450);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, DEPTH, FOOD_RESERVE, DURABILITY_RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getMaxDepth(level), 1);
    statLore(v, getConfig().durabilityCostPerBlock, 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 3);
    statLore(v, C.RED, "- ", getConfig().hungerCost, 4);
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_BLOCK && action != InteractAction.RIGHT_CLICK_AIR) {
      return;
    }

    if (e.hand() != null && e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    if (!p.isSneaking()) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!isShovel(hand)) {
      return;
    }
    if (action == InteractAction.RIGHT_CLICK_AIR && Tasks.isFoliaThreading()) {
      return;
    }

    boolean vetoed = e.clickedBlock() != null
        ? e.useBlock() == UseResult.DENY
        : e.useItem() == UseResult.DENY;

    BlockRef clicked = e.clickedBlock();
    if (action == InteractAction.RIGHT_CLICK_AIR) {
      clicked = p.targetBlock(5);
      if (clicked == null) {
        clicked = p.world().blockAt(p.location()).relative(BlockFace.DOWN);
      }
    }

    if (clicked == null || !isShovelable(clicked.type())) {
      return;
    }

    BlockActionContext context = resolveBlockBreakContext(p, clicked.location());
    if (context == null) {
      return;
    }

    int level = context.level();
    long cooldownMillis = getCooldownMillis(level);
    if (vetoed) {
      if (!cooldowns.isReady(p.uuid(), cooldownMillis)) {
        e.setCancelled(true);
      }
      return;
    }

    if (!cooldowns.isReady(p.uuid(), cooldownMillis)) {
      return;
    }

    int hungerCost = getConfig().hungerCost;
    if (p.foodLevel() - hungerCost < (preferenceEnabled(p, FOOD_RESERVE) ? 8 : 0)) {
      return;
    }

    List<BlockRef> plan = planDig(p, clicked, Math.max(1, (int) Math.floor(getMaxDepth(level) * preference(p, DEPTH).multiplier())));
    if (plan.isEmpty()) {
      return;
    }

    if (!canApplyDurability(hand, plan.size() * getConfig().durabilityCostPerBlock
        + (preferenceEnabled(p, DURABILITY_RESERVE) ? maxDurability(hand) / 4 : 0))) {
      fx(p.location(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 4, 0, 1.0D, 0, 0.15D, 0.01D)
          .chord(Sounds.BLOCK_ANVIL_PLACE, 0.5f, 0.65f, Sounds.ITEM_SHIELD_BLOCK, 0.3f, 1.4f);
      return;
    }

    int planSize = plan.size();
    BlockStateRef clickedData = clicked.state();
    BlockStateRef floorData = plan.get(planSize - 1).state();
    if (!digBlock(p, plan.get(0), 1.1F)) {
      return;
    }

    cooldowns.mark(p.uuid(), cooldownMillis);
    p.setFoodLevel(Math.max(0, p.foodLevel() - hungerCost));
    e.setCancelled(true);

    timeline(p.location())
        .duration(6)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.blockCrack(clickedData), 1.4D - (1.1D * progress), 10, 0.1D);
          if (tick == 0) {
            fx.chord(Sounds.BLOCK_ROOTED_DIRT_BREAK, 0.6f, 0.7f, Sounds.ITEM_SHOVEL_FLATTEN, 0.5f, 0.9f);
          }
        })
        .start();

    scheduleDig(p, plan, floorData);
    addStat(p, "excavation.burrow.burrows-dug", 1);
  }

  private List<BlockRef> planDig(PlayerRef p, BlockRef start, int maxDepth) {
    WorldRef world = start.world();
    int stopY = world.minHeight() + getConfig().safeFloorMargin;
    List<BlockRef> plan = new ArrayList<>(maxDepth);
    BlockRef current = start;
    for (int i = 0; i < maxDepth; i++) {
      if (current.y() <= stopY
          || (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(current.location()))) {
        break;
      }

      if (!isShovelable(current.type())) {
        break;
      }

      if (!canBlockBreak(p, current.location())) {
        break;
      }

      BlockRef below = current.relative(BlockFace.DOWN);
      Id belowType = below.type();
      if (belowType.equals(LAVA)) {
        break;
      }

      if (belowType.isAir() && below.relative(BlockFace.DOWN).type().isAir()) {
        break;
      }

      plan.add(current);
      current = below;
    }

    return plan;
  }

  private void scheduleDig(PlayerRef player, List<BlockRef> plan, BlockStateRef floorData) {
    int interval = Math.max(1, getConfig().ticksPerBlock);
    int size = plan.size();
    Loc floor = plan.get(size - 1).location().add(0.5, 0.0, 0.5);
    for (int i = 1; i < size; i++) {
      BlockRef block = plan.get(i);
      float pitch = (float) (1.1D + (0.4D * ((double) i / size)));
      int delay = i * interval;
      Tasks.runEntity(player, () -> digBlock(player, block, pitch), delay);
    }

    Tasks.runAt(floor, () -> bottomOut(floor, floorData), (size * interval) + interval);
  }

  private void bottomOut(Loc floor, BlockStateRef floorData) {
    fx(floor, FxPriority.GAMEPLAY)
        .dome(Particles.CLOUD, 0.6D, 12)
        .particle(Particles.blockCrack(floorData), 6, 0, 0.2D, 0, 0.15D, 0.15D)
        .chord(Sounds.BLOCK_ROOTED_DIRT_BREAK, 0.8f, 0.6f, Sounds.ITEM_SHOVEL_FLATTEN, 0.4f, 0.5f);
  }

  private boolean digBlock(PlayerRef player, BlockRef block, float pitch) {
    Loc location = block.location();
    if (!player.isOnline()
        || !player.world().uid().equals(block.world().uid())
        || (Tasks.isFoliaThreading()
        && (!Tasks.isOwnedByCurrentRegion(player) || !Tasks.isOwnedByCurrentRegion(location)))) {
      return false;
    }

    if (!isShovelable(block.type())
        || block.relative(BlockFace.DOWN).type().equals(LAVA)
        || !canBlockBreak(player, location)) {
      return false;
    }

    ItemRef hand = player.inventory().mainHand();
    if (!isShovel(hand)
        || !canApplyDurability(hand, getConfig().durabilityCostPerBlock)
        || !ProtectionProbes.attemptBlockBreakProbe(player, block)) {
      return false;
    }

    BlockStateRef data = block.state();
    if (!block.breakNaturally(hand)) {
      return false;
    }

    applyDurability(player, hand, getConfig().durabilityCostPerBlock);
    addStat(player, "excavation.burrow.blocks-burrowed", 1);
    xp(player, getConfig().xpPerBlock);
    fx(block.center(), FxPriority.TRAIL)
        .particle(Particles.blockCrack(data), 4, 0, 0, 0, 0.15D, 0.02D)
        .particle(Particles.CLOUD, 1, 0, 0, 0, 0.05D, 0.01D)
        .sound(Sounds.ITEM_SHOVEL_FLATTEN, 0.45f, pitch);
    return true;
  }

  private boolean canApplyDurability(ItemRef hand, int cost) {
    if (cost <= 0) {
      return true;
    }

    if (hand == null || hand.isEmpty()) {
      return false;
    }

    return hand.damage() + cost < maxDurability(hand);
  }

  private boolean applyDurability(PlayerRef p, ItemRef hand, int cost) {
    if (cost <= 0) {
      return true;
    }

    if (hand == null || hand.isEmpty()) {
      return false;
    }

    int maxDurability = maxDurability(hand);
    int currentDamage = hand.damage();
    if (currentDamage + cost >= maxDurability) {
      return false;
    }

    hand.setDamage(currentDamage + cost);
    p.inventory().setMainHand(hand);
    return true;
  }

  private static int maxDurability(ItemRef hand) {
    return AdaptCore.platform().host().items().maxDurability(hand.type());
  }

  private boolean isShovelable(Id type) {
    return ExcavationBlocks.isShovelable(type);
  }

  private int getMaxDepth(int level) {
    return Math.max(2, (int) Math.round(getConfig().depthBase + (getLevelPercent(level) * getConfig().depthFactor)));
  }

  private long getCooldownMillis(int level) {
    return Math.max(2000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }


  @ConfigDescription("Sneak-right-click soft ground with a shovel to rapidly dig straight down, stopping before hazards.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Depth Base for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double depthBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Depth Factor for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double depthFactor = 13;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ticks Per Block for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int ticksPerBlock = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Safe Floor Margin for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int safeFloorMargin = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Per Block for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int durabilityCostPerBlock = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hunger points consumed per burrow activation.", impact = "Higher values drain more food per dig; activation fails when food is below the cost. Set to 0 to disable the hunger cost.")
    int hungerCost = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 14000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 7000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Block for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBlock = 2;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
