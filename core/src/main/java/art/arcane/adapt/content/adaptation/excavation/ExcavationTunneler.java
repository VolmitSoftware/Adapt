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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.BonusBlockBreaks;
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
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.ArrayList;
import java.util.List;

public class ExcavationTunneler extends SimpleAdaptation<ExcavationTunneler.Config> {
  public static final PlayerPreference<ExcavationPreferences.Trigger> TRIGGER = ExcavationPreferences.trigger("trigger", ExcavationMessages.PREFERENCE_EXCAVATIONTUNNELER_TRIGGER);
  public static final PlayerPreference<ExcavationPreferences.Materials> MATERIALS = ExcavationPreferences.materials("materials", ExcavationMessages.PREFERENCE_EXCAVATIONTUNNELER_MATERIALS);
  public static final PlayerPreference<CommonPreferences.Scale> BLOCKS = CommonPreferences.scale("block-limit", ExcavationMessages.PREFERENCE_EXCAVATIONTUNNELER_BLOCKS);

  private static final int[][] PLANE_OFFSETS = {
      {0, 1}, {0, -1}, {1, 0}, {-1, 0}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}
  };

  public ExcavationTunneler() {
    super("excavation-tunneler");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_shovel"));
    setInterval(3170);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_shovel"))
        .key("challenge_excavation_tunneler_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_excavation_tunneler_10k", "excavation.tunneler.blocks-tunneled", 10000, 600);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TRIGGER, MATERIALS, BLOCKS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getBonusBlocks(level), 1);
    statLore(v, C.YELLOW, "* ", getConfig().durabilityCostPerBonusBlock, 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (BonusBlockBreaks.isActive(e.player())) {
      return;
    }
    PlayerRef p = e.player();
    if (!preference(p, TRIGGER).allows(p.isSneaking())) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!isShovel(hand)) {
      return;
    }

    if (!isShovelable(e.block().type()) || !preference(p, MATERIALS).allows(e.block().type())) {
      return;
    }

    BlockActionContext context = resolveBlockBreakContext(p, e.block().location());
    if (context == null) {
      return;
    }

    int level = context.level();
    int bonus = Math.max(1, (int) Math.floor(getBonusBlocks(level) * preference(p, BLOCKS).multiplier()));
    float pitch = p.location().pitch();
    float yaw = p.location().yaw();
    boolean horizontal = pitch >= 50 || pitch <= -50;
    boolean facingX = !horizontal && isFacingX(yaw);

    BlockRef origin = e.block();
    Id originType = origin.type();
    List<int[]> targetOffsets = new ArrayList<>(PLANE_OFFSETS.length);
    for (int[] offset : PLANE_OFFSETS) {
      int dx;
      int dy;
      int dz;
      if (horizontal) {
        dx = offset[0];
        dy = 0;
        dz = offset[1];
      } else if (facingX) {
        dx = 0;
        dy = offset[1];
        dz = offset[0];
      } else {
        dx = offset[0];
        dy = offset[1];
        dz = 0;
      }

      BlockRef target = origin.relative(dx, dy, dz);
      Loc targetLocation = target.location();
      if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(targetLocation))
          || !isShovelable(target.type())
          || !preference(p, MATERIALS).allows(target.type())) {
        continue;
      }

      if (!canBlockBreak(p, targetLocation)) {
        continue;
      }

      targetOffsets.add(new int[]{dx, dy, dz});
    }

    if (targetOffsets.isEmpty()) {
      return;
    }

    Tasks.runEntity(p, () -> breakTunnelPlane(p, origin, originType, bonus, targetOffsets), 1);
  }

  private void breakTunnelPlane(PlayerRef player, BlockRef origin, Id originType, int bonus,
                                List<int[]> targetOffsets) {
    Loc originLocation = origin.location();
    if (!player.isOnline()
        || !player.world().uid().equals(origin.world().uid())
        || (Tasks.isFoliaThreading()
        && (!Tasks.isOwnedByCurrentRegion(player) || !Tasks.isOwnedByCurrentRegion(originLocation)))
        || origin.type().equals(originType)) {
      return;
    }

    ItemRef hand = player.inventory().mainHand();
    if (!isShovel(hand)) {
      return;
    }

    Loc originCenter = origin.center();
    List<int[]> sweepCells = new ArrayList<>(targetOffsets.size());
    List<BlockStateRef> sweepData = new ArrayList<>(targetOffsets.size());
    int broken = 0;
    boolean durabilityStop = false;
    for (int[] offset : targetOffsets) {
      if (broken >= bonus) {
        break;
      }

      BlockRef target = origin.relative(offset[0], offset[1], offset[2]);
      Loc targetLocation = target.location();
      if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(targetLocation))
          || !isShovelable(target.type())
          || !preference(player, MATERIALS).allows(target.type())
          || !canBlockBreak(player, targetLocation)) {
        continue;
      }

      if (!canApplyDurability(hand, getConfig().durabilityCostPerBonusBlock)) {
        durabilityStop = true;
        break;
      }

      BlockStateRef blockData = target.state();
      if (BonusBlockBreaks.breakBlock(player, target, getConfig().durabilityCostPerBonusBlock)) {
        sweepCells.add(offset);
        sweepData.add(blockData);
        broken++;
      }
    }

    if (durabilityStop) {
      fx(originCenter, FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0.2D, 0, 0.1D, 0.01D)
          .sound(Sounds.ITEM_SHIELD_BLOCK, 0.3f, 1.3f);
    }

    if (broken <= 0) {
      return;
    }

    planeSweep(originCenter, sweepCells, sweepData, broken);
    addStat(player, "excavation.tunneler.blocks-tunneled", broken);
    xp(player, broken * getConfig().xpPerBonusBlock);
  }

  private void planeSweep(Loc origin, List<int[]> cells, List<BlockStateRef> data, int broken) {
    int count = cells.size();
    timeline(origin)
        .duration(Math.max(4, count))
        .priority(FxPriority.TRAIL)
        .cullRadius(20)
        .frame((fx, tick, progress) -> {
          if (tick < count) {
            int[] c = cells.get(tick);
            fx.particle(Particles.blockCrack(data.get(tick)), 3, c[0], c[1], c[2], 0.15D, 0.05D);
          }
          if (tick == 0) {
            fx.chord(Sounds.ITEM_SHOVEL_FLATTEN, 0.7f, 0.8f, Sounds.BLOCK_GRAVEL_BREAK, Math.min(0.7f, 0.2f + (broken * 0.06f)), 0.7f);
          }
        })
        .start();
  }

  private boolean isFacingX(float yaw) {
    float normalized = ((yaw % 360) + 360) % 360;
    return (normalized >= 45 && normalized < 135) || (normalized >= 225 && normalized < 315);
  }

  private boolean canApplyDurability(ItemRef hand, int cost) {
    if (cost <= 0) {
      return true;
    }

    if (hand == null || hand.isEmpty()) {
      return false;
    }

    return hand.isUnbreakable() || hand.damage() + cost < hand.maxDamage();
  }

  private boolean isShovelable(Id type) {
    return ExcavationBlocks.isShovelable(type);
  }

  private int getBonusBlocks(int level) {
    return Math.max(1, Math.min(8, (int) Math.floor(getLevelPercent(level) * getConfig().bonusBlocksMax)));
  }


  @ConfigDescription("Sneak while digging soft blocks to carve a facing-oriented plane of blocks at once.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Blocks Max for the Excavation Tunneler adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusBlocksMax = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Per Bonus Block for the Excavation Tunneler adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int durabilityCostPerBonusBlock = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Bonus Block for the Excavation Tunneler adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBonusBlock = 1.5;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
