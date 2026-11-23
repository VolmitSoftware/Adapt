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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
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
import art.arcane.adapt.content.integration.iris.IrisTreeFellerPort;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AxeMessages;
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
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static art.arcane.adapt.util.data.BlockFlag.VEIN_MINED;

public class AxeLeafVeinminer extends SimpleAdaptation<AxeLeafVeinminer.Config> {
  private static final Id MANGROVE_ROOTS = Id.minecraft("mangrove_roots");
  private static final Id MUDDY_MANGROVE_ROOTS = Id.minecraft("muddy_mangrove_roots");

  public AxeLeafVeinminer() {
    super("axe-leaf-veinminer");
    registerConfiguration(AxeLeafVeinminer.Config.class);
    setLocalizationKey("axe.leaf_miner");
    setIcon(Id.minecraft("birch_leaves"));
    setInterval(5849);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("oak_leaves"))
        .key("challenge_axe_leaf_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_axe_leaf_5k", "axe.leaf-veinminer.leaves-broken", 5000, 400);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, AxePreferences.TRIGGER, AxePreferences.WORK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(AxeMessages.LEAF_MINER_LORE1));
    statLore(v, C.GREEN, "", level + getConfig().baseRange, 2);
    v.addLore(C.ITALIC + AdaptLanguage.text(AxeMessages.LEAF_MINER_LORE3));
  }

  private int getRadius(int lvl) {
    return lvl + getConfig().baseRange;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {

    if (VEIN_MINED.get(e.block()) || AdaptPorts.get(IrisTreeFellerPort.class, IrisTreeFellerPort.NONE).isManagedBreak(e)) {
      return;
    }

    PlayerRef p = e.player();
    ItemRef tool = p.inventory().mainHand();
    if (!preference(p, AxePreferences.TRIGGER).accepts(p.isSneaking()) || !isAxe(tool)) {
      return;
    }

    Id blockType = e.block().type();
    if (!isLeavesMaterial(blockType) || !hasActiveAdaptation(p)) {
      return;
    }

    if (!canBlockBreak(p, e.block().location())) {
      return;
    }

    VEIN_MINED.add(e.block());

    BlockRef block = e.block();
    BlockStateRef leafData = block.state();
    Set<BlockRef> blockMap = new HashSet<>();
    Deque<BlockRef> stack = new ArrayDeque<>();
    Set<BlockRef> queued = new HashSet<>();
    stack.push(block);
    queued.add(block);
    int radius = getRadius(getLevel(p));
    int radiusSquared = radius * radius;
    int maxBlocks = Math.max(1, (int) Math.floor(getConfig().maxBlocks * preference(p, AxePreferences.WORK).multiplier()));
    while (!stack.isEmpty() && blockMap.size() < maxBlocks) {
      BlockRef currentBlock = stack.pop();
      if (blockMap.contains(currentBlock)) {
        continue;
      }
      blockMap.add(currentBlock);
      for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
          for (int z = -1; z <= 1; z++) {
            if (x == 0 && y == 0 && z == 0) {
              continue;
            }
            BlockRef b = currentBlock.relative(x, y, z);
            if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(b.location()))
                || !b.type().equals(blockType)
                || blockMap.contains(b)
                || !queued.add(b)) {
              continue;
            }
            if (distanceSquared(block, b) <= radiusSquared && canBlockBreak(p, b.location())) {
              stack.push(b);
            } else {
              queued.remove(b);
            }
          }
        }
      }
    }

    Set<BlockRef> siblings = new HashSet<>(blockMap);
    siblings.remove(block);
    boolean scheduled = Tasks.runEntity(
        p,
        () -> mineLeaves(p, block, blockType, leafData, siblings, radius),
        1
    );
    if (!scheduled) {
      VEIN_MINED.remove(block);
    }
  }

  private void mineLeaves(PlayerRef player, BlockRef origin, Id blockType, BlockStateRef leafData,
                          Set<BlockRef> targets, int radius) {
    Loc originLocation = origin.location();
    if (!player.isOnline() || getActiveLevel(player) <= 0
        || !player.world().uid().equals(origin.world().uid())
        || (Tasks.isFoliaThreading()
        && (!Tasks.isOwnedByCurrentRegion(player) || !Tasks.isOwnedByCurrentRegion(originLocation)))
        || origin.type().equals(blockType)) {
      VEIN_MINED.remove(origin);
      return;
    }

    ItemRef tool = player.inventory().mainHand();
    if (!isAxe(tool)) {
      VEIN_MINED.remove(origin);
      return;
    }

    fx(player.eyeLocation(), FxPriority.TRAIL)
        .line(Particles.VILLAGER_HAPPY, origin.x() + 0.5D, origin.y() + 0.5D, origin.z() + 0.5D, 5)
        .sound(Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 0.5F, 1.2F);
    List<BlockRef> brokenBlocks = new ArrayList<>(targets.size() + 1);
    brokenBlocks.add(origin);
    try {
      for (BlockRef target : targets) {
        Loc location = target.location();
        if ((Tasks.isFoliaThreading()
            && (!Tasks.isOwnedByCurrentRegion(player) || !Tasks.isOwnedByCurrentRegion(location)))
            || !target.type().equals(blockType)
            || !canBlockBreak(player, location)) {
          continue;
        }

        VEIN_MINED.add(target);
        try {
          if (ProtectionProbes.attemptBlockBreak(player, target)) {
            brokenBlocks.add(target);
          }
        } finally {
          VEIN_MINED.remove(target);
        }
      }
    } finally {
      VEIN_MINED.remove(origin);
    }

    emitLeafFeedback(player, origin, leafData, brokenBlocks, radius);
  }

  private void emitLeafFeedback(PlayerRef player, BlockRef origin, BlockStateRef leafData,
                                List<BlockRef> brokenBlocks, int radius) {
    int leavesBroken = brokenBlocks.size();
    if (leavesBroken > 0) {
      addStat(player, "axe.leaf-veinminer.leaves-broken", leavesBroken);
      double sumX = 0.0D;
      double sumY = 0.0D;
      double sumZ = 0.0D;
      for (BlockRef selected : brokenBlocks) {
        sumX += selected.x();
        sumY += selected.y();
        sumZ += selected.z();
      }
      Loc center = new Loc(origin.world(), (sumX / leavesBroken) + 0.5D, (sumY / leavesBroken) + 0.5D, (sumZ / leavesBroken) + 0.5D);
      double extent = Math.min(3.0D, 1.0D + (radius * 0.35D));
      timeline(center)
          .duration(8)
          .priority(FxPriority.TRANSITION)
          .cullRadius(24)
          .frame((fx, tick, progress) -> {
            fx.particle(Particles.blockCrack(leafData), 3, 0.0D, extent * 0.4D * (1.0D - progress), 0.0D, extent, 0.02D);
            if (tick == 0) {
              fx.chord(Sounds.BLOCK_AZALEA_LEAVES_BREAK, 0.7F, 1.0F, Sounds.BLOCK_GRASS_BREAK, 0.4F, 1.3F);
            }
            if ((tick & 1) == 0) {
              fx.particle(Particles.VILLAGER_HAPPY, 1, 0.0D, 0.0D, 0.0D, extent, 0.0D);
            }
          })
          .start();
      if (leavesBroken >= 40) {
        fx(center, FxPriority.AMBIENT)
            .burst(Particles.VILLAGER_HAPPY, 6, 0.4D)
            .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.5F);
      }
    }
  }



  private int distanceSquared(BlockRef first, BlockRef second) {
    int dx = first.x() - second.x();
    int dy = first.y() - second.y();
    int dz = first.z() - second.z();
    return (dx * dx) + (dy * dy) + (dz * dz);
  }

  private boolean isLeavesMaterial(Id type) {
    return type.equals(MANGROVE_ROOTS)
        || type.equals(MUDDY_MANGROVE_ROOTS)
        || type.path().endsWith("_leaves");
  }

  @ConfigDescription("Break bulk leaves at once while sneaking.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Range for the Axe Leaf Veinminer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseRange = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blocks for the Axe Leaf Veinminer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxBlocks = 128;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
