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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
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

import java.util.List;

public class ExcavationBurrow extends SimpleAdaptation<ExcavationBurrow.Config> {
  public static final Id ID = Id.adapt("excavation/burrow");
  public static final PlayerPreference<CommonPreferences.Scale> DEPTH = CommonPreferences.scale("depth", Text.key("adapt.excavation.preferences.excavationburrow.depth"));
  public static final PlayerPreference<CommonPreferences.Toggle> FOOD_RESERVE = CommonPreferences.toggle("food-reserve", Text.key("adapt.excavation.preferences.excavationburrow.food-reserve"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> DURABILITY_RESERVE = CommonPreferences.toggle("durability-reserve", Text.key("adapt.excavation.preferences.excavationburrow.durability-reserve"), CommonPreferences.Toggle.OFF);

  private final Cooldowns cooldowns = cooldowns();
  private final ExcavationShaft shaft = new ExcavationShaft(this, (PlayerRef player, BlockRef dug) -> {
    addStat(player, "excavation.burrow.blocks-burrowed", 1);
    xp(player, getConfig().xpPerBlock);
  });

  public ExcavationBurrow() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("coarse_dirt"));
    setInterval(4130);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("coarse_dirt"))
        .key("challenge_excavation_burrow_100")
        .frame(AdvancementFrame.CHALLENGE)
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
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownMillis(level), 1), 3);
    statLore(v, TextFormat.RED, "- ", getConfig().hungerCost, 4);
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
    if (!ItemKinds.isShovel(hand)) {
      return;
    }
    if (action == InteractAction.RIGHT_CLICK_AIR && AdaptApi.scheduler().isRegionized()) {
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

    List<BlockRef> plan = shaft.plan(p, clicked, Math.max(1, (int) Math.floor(getMaxDepth(level) * preference(p, DEPTH).multiplier())),
        getConfig().safeFloorMargin);
    if (plan.isEmpty()) {
      return;
    }

    if (!ExcavationShaft.canApplyDurability(hand, plan.size() * getConfig().durabilityCostPerBlock
        + (preferenceEnabled(p, DURABILITY_RESERVE) ? ExcavationShaft.maxDurability(hand) / 4 : 0))) {
      fx(p.location(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 4, 0, 1.0D, 0, 0.15D, 0.01D)
          .chord(Sounds.BLOCK_ANVIL_PLACE, 0.5f, 0.65f, Sounds.ITEM_SHIELD_BLOCK, 0.3f, 1.4f);
      return;
    }

    int planSize = plan.size();
    BlockStateRef clickedData = clicked.state();
    BlockStateRef floorData = plan.get(planSize - 1).state();
    if (!shaft.dig(p, plan.get(0), 1.1F, getConfig().durabilityCostPerBlock)) {
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

    shaft.schedule(p, plan, floorData, getConfig().ticksPerBlock, getConfig().durabilityCostPerBlock);
    addStat(p, "excavation.burrow.burrows-dug", 1);
  }

  static void bottomOutFx(Adaptation<?> owner, Loc floor, BlockStateRef floorData) {
    Fx.now(owner, floor, FxPriority.GAMEPLAY)
        .dome(Particles.CLOUD, 0.6D, 12)
        .particle(Particles.blockCrack(floorData), 6, 0, 0.2D, 0, 0.15D, 0.15D)
        .chord(Sounds.BLOCK_ROOTED_DIRT_BREAK, 0.8f, 0.6f, Sounds.ITEM_SHOVEL_FLATTEN, 0.4f, 0.5f);
  }

  static void digFx(Adaptation<?> owner, Loc center, BlockStateRef data, float pitch) {
    Fx.now(owner, center, FxPriority.TRAIL)
        .particle(Particles.blockCrack(data), 4, 0, 0, 0, 0.15D, 0.02D)
        .particle(Particles.CLOUD, 1, 0, 0, 0, 0.05D, 0.01D)
        .sound(Sounds.ITEM_SHOVEL_FLATTEN, 0.45f, pitch);
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
    @ConfigDoc(value = "Controls Depth Base for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double depthBase = 3;
    @ConfigDoc(value = "Controls Depth Factor for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double depthFactor = 13;
    @ConfigDoc(value = "Controls Ticks Per Block for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int ticksPerBlock = 2;
    @ConfigDoc(value = "Controls Safe Floor Margin for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int safeFloorMargin = 16;
    @ConfigDoc(value = "Controls Durability Cost Per Block for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int durabilityCostPerBlock = 1;
    @ConfigDoc(value = "Hunger points consumed per burrow activation.", impact = "Higher values drain more food per dig; activation fails when food is below the cost. Set to 0 to disable the hunger cost.")
    int hungerCost = 1;
    @ConfigDoc(value = "Controls Cooldown Millis Base for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 14000;
    @ConfigDoc(value = "Controls Cooldown Millis Factor for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 7000;
    @ConfigDoc(value = "Controls Xp Per Block for the Excavation Burrow adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBlock = 2;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
