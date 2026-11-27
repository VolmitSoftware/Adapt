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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.world.BonusBreaks;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.List;

public class ExcavationTunneler extends SimpleAdaptation<ExcavationTunneler.Config> {
  public static final Id ID = Id.adapt("excavation/tunneler");
  public static final PlayerPreference<ExcavationPreferences.Trigger> TRIGGER = ExcavationPreferences.trigger("trigger", Text.key("adapt.excavation.preferences.excavationtunneler.trigger"));
  public static final PlayerPreference<ExcavationPreferences.Materials> MATERIALS = ExcavationPreferences.materials("materials", Text.key("adapt.excavation.preferences.excavationtunneler.materials"));
  public static final PlayerPreference<CommonPreferences.Scale> BLOCKS = CommonPreferences.scale("block-limit", Text.key("adapt.excavation.preferences.excavationtunneler.block-limit"));

  private final ExcavationPlane plane = new ExcavationPlane(this, (PlayerRef player, List<BlockRef> carved) -> {
    addStat(player, "excavation.tunneler.blocks-tunneled", carved.size());
    xp(player, carved.size() * getConfig().xpPerBonusBlock);
  });

  public ExcavationTunneler() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_shovel"));
    setInterval(3170);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_shovel"))
        .key("challenge_excavation_tunneler_10k")
        .frame(AdvancementFrame.CHALLENGE)
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
    statLore(v, TextFormat.YELLOW, "* ", getConfig().durabilityCostPerBonusBlock, 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (BonusBreaks.isActive(e.player())) {
      return;
    }
    PlayerRef p = e.player();
    if (!preference(p, TRIGGER).allows(p.isSneaking())) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!ItemKinds.isShovel(hand)) {
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
    plane.carve(p, e.block(), bonus, getConfig().durabilityCostPerBonusBlock,
        (Id type) -> preference(p, MATERIALS).allows(type));
  }

  private boolean isShovelable(Id type) {
    return ExcavationBlocks.isShovelable(type);
  }

  private int getBonusBlocks(int level) {
    return Math.max(1, Math.min(8, (int) Math.floor(getLevelPercent(level) * getConfig().bonusBlocksMax)));
  }


  static void durabilityStopFx(Adaptation<?> owner, Loc originCenter) {
    Fx.now(owner, originCenter, FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 2, 0, 0.2D, 0, 0.1D, 0.01D)
        .sound(Sounds.ITEM_SHIELD_BLOCK, 0.3f, 1.3f);
  }

  public static void planeSweepFx(Adaptation<?> owner, Loc origin, List<int[]> cells, List<BlockStateRef> data, int broken) {
    int count = cells.size();
    FxTimeline.at(owner, origin)
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

  @ConfigDescription("Sneak while digging soft blocks to carve a facing-oriented plane of blocks at once.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Bonus Blocks Max for the Excavation Tunneler adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusBlocksMax = 8;
    @ConfigDoc(value = "Controls Durability Cost Per Bonus Block for the Excavation Tunneler adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int durabilityCostPerBonusBlock = 1;
    @ConfigDoc(value = "Controls Xp Per Bonus Block for the Excavation Tunneler adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBonusBlock = 1.5;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
