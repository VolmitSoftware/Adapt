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

package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.api.adaptation.Adaptation;
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
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PickaxeMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PickaxeTunnelBore extends SimpleAdaptation<PickaxeTunnelBore.Config> {
  private static final Set<Id> BORE_BLOCKS = Set.of(
      Id.minecraft("stone"), Id.minecraft("cobblestone"), Id.minecraft("mossy_cobblestone"),
      Id.minecraft("deepslate"), Id.minecraft("cobbled_deepslate"), Id.minecraft("tuff"),
      Id.minecraft("calcite"), Id.minecraft("andesite"), Id.minecraft("diorite"), Id.minecraft("granite"));
  private static final int[] SINGLE = {0};
  private static final int[] PAIR = {0, 1};
  private static final int[] TRIPLE = {-1, 0, 1};

  public PickaxeTunnelBore() {
    super("pickaxe-tunnel-bore");
    registerConfiguration(PickaxeTunnelBore.Config.class);
    setIcon(Id.minecraft("cobblestone"));
    setInterval(8123);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_pickaxe"))
        .key("challenge_pickaxe_tunnelbore_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_tunnelbore_10k", "pickaxe.tunnel-bore.blocks-bored", 10000, 500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, PickaxePreferences.TRIGGER, PickaxePreferences.MATERIALS, PickaxePreferences.SIZE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(PickaxeMessages.TUNNEL_BORE_LORE1));
    statLore(v, C.GREEN, "", getBoreWidth(level) + "x" + getBoreHeight(level), 2);
    statLore(v, C.RED, "- ", getConfig().durabilityPerBonusBlock, 3);
  }

  private int getBoreWidth(int level) {
    return level >= 2 ? 3 : 1;
  }

  private int getBoreHeight(int level) {
    return level >= 3 ? 3 : 2;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (BonusBlockBreaks.isActive(e.player())) {
      return;
    }
    BlockRef block = e.block();
    Id originType = block.type();
    if (!BORE_BLOCKS.contains(originType)) {
      return;
    }

    PlayerRef p = e.player();
    if (!preference(p, PickaxePreferences.TRIGGER).accepts(p.isSneaking())
        || !preference(p, PickaxePreferences.MATERIALS).accepts(originType)
        || !isPickaxe(p.inventory().mainHand())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, block.location());
    if (context == null) {
      return;
    }

    PickaxePreferences.Size size = preference(p, PickaxePreferences.SIZE);
    int boreWidth = size.width(getBoreWidth(context.level()));
    int boreHeight = size.height(getBoreHeight(context.level()));
    List<BlockRef> targets = collectPlane(p, block, boreWidth, boreHeight);
    if (targets.isEmpty()) {
      return;
    }

    Tasks.runEntity(
        p,
        () -> breakBorePlane(p, block, originType, boreWidth, boreHeight, targets),
        1
    );
  }

  private void breakBorePlane(PlayerRef player, BlockRef origin, Id originType, int boreWidth,
                              int boreHeight, List<BlockRef> targets) {
    Loc originLocation = origin.location();
    if (!player.isOnline()
        || !player.world().uid().equals(origin.world().uid())
        || (Tasks.isFoliaThreading()
        && (!Tasks.isOwnedByCurrentRegion(player) || !Tasks.isOwnedByCurrentRegion(originLocation)))
        || origin.type().equals(originType)) {
      return;
    }

    ItemRef tool = player.inventory().mainHand();
    if (!isPickaxe(tool) || getActiveLevel(player) <= 0) {
      return;
    }

    Loc center = origin.center();
    fx(center, FxPriority.GAMEPLAY)
        .ring(Particles.CRIT, 0.9D, 12, 0.1D)
        .sound(Sounds.BLOCK_STONE_BREAK, 0.5f, 0.7f);
    if (boreWidth * boreHeight >= 9) {
      fx(center, FxPriority.TRANSITION)
          .particle(Particles.CLOUD, 12, 0, 0.3, 0, 0.5, 0.02)
          .chord(Sounds.BLOCK_GRAVEL_BREAK, 0.6f, 0.5f, Sounds.ENTITY_RAVAGER_STEP, 0.4f, 0.6f);
    }

    int index = 0;
    int broken = 0;
    for (BlockRef target : targets) {
      if (!isPickaxe(player.inventory().mainHand())) {
        break;
      }
      Loc location = target.location();
      if ((Tasks.isFoliaThreading()
          && (!Tasks.isOwnedByCurrentRegion(player) || !Tasks.isOwnedByCurrentRegion(location)))
          || !BORE_BLOCKS.contains(target.type())
          || !preference(player, PickaxePreferences.MATERIALS).accepts(target.type())
          || !canBlockBreak(player, location)) {
        index++;
        continue;
      }

      if ((index & 1) == 0) {
        fx(target.center(), FxPriority.TRAIL)
            .particle(Particles.blockCrack(target.state()), 3, 0, 0, 0, 0.2, 0.0);
      }
      if (BonusBlockBreaks.breakBlock(player, target, getConfig().durabilityPerBonusBlock)) {
        broken++;
      }
      index++;
    }

    if (broken > 0) {
      addStat(player, "pickaxe.tunnel-bore.blocks-bored", broken);
    }
  }

  private List<BlockRef> collectPlane(PlayerRef p, BlockRef origin, int width, int height) {
    List<BlockRef> targets = new ArrayList<>(8);
    int[] wOffsets = width == 1 ? SINGLE : TRIPLE;
    int[] hOffsets = height == 2 ? PAIR : TRIPLE;
    Loc location = p.location();
    BlockFace facing = facing(location.yaw());
    float pitch = location.pitch();
    boolean steep = pitch > 60F || pitch < -60F;
    int fx = facing.dx();
    int fz = facing.dz();
    boolean widthOnX = fz != 0;
    for (int h : hOffsets) {
      for (int w : wOffsets) {
        if (h == 0 && w == 0) {
          continue;
        }

        BlockRef b;
        if (steep) {
          b = origin.relative((h * fx) - (w * fz), 0, (h * fz) + (w * fx));
        } else {
          b = origin.relative(widthOnX ? w : 0, h, widthOnX ? 0 : w);
        }

        Loc blockLocation = b.location();
        if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(blockLocation))
            || !BORE_BLOCKS.contains(b.type())) {
          continue;
        }

        if (!canBlockBreak(p, blockLocation)) {
          continue;
        }

        targets.add(b);
      }
    }

    return targets;
  }

  static BlockFace facing(float yaw) {
    return switch (((int) Math.floor((yaw / 90.0D) + 0.5D)) & 3) {
      case 0 -> BlockFace.SOUTH;
      case 1 -> BlockFace.WEST;
      case 2 -> BlockFace.NORTH;
      default -> BlockFace.EAST;
    };
  }

  @ConfigDescription("Sneak-mine stone-type blocks to bore a tunnel plane oriented by your facing.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra pickaxe durability damage taken per bonus block bored.", impact = "Higher values wear the pickaxe out faster while tunnel boring.")
    int durabilityPerBonusBlock = 1;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 3;
      initialCost = 3;
    }
  }
}
