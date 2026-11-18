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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static art.arcane.adapt.util.data.BlockFlag.VEIN_MINED;

public class AxeWoodVeinminer extends SimpleAdaptation<AxeWoodVeinminer.Config> {
  private static final Set<Id> LOG_LIKE = Set.of(
      Id.minecraft("mushroom_stem"),
      Id.minecraft("brown_mushroom_block"),
      Id.minecraft("red_mushroom_block"),
      Id.minecraft("mangrove_roots"),
      Id.minecraft("muddy_mangrove_roots"));

  public AxeWoodVeinminer() {
    super("axe-wood-veinminer");
    registerConfiguration(AxeWoodVeinminer.Config.class);
    setLocalizationKey("axe.wood_miner");
    setIcon(Id.minecraft("diamond_axe"));
    setInterval(5849);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("oak_log"))
        .key("challenge_axe_wood_vein_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond_axe"))
        .key("challenge_axe_wood_vein_cascade")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_axe_wood_vein_2500", "axe.wood-veinminer.logs-veinmined", 2500, 500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, AxePreferences.TRIGGER, AxePreferences.WORK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(AxeMessages.WOOD_MINER_LORE1));
    statLore(v, C.GREEN, "", level + getConfig().baseRange, 2);
    v.addLore(C.ITALIC + AdaptLanguage.text(AxeMessages.WOOD_MINER_LORE3));
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
    if (!preference(p, AxePreferences.TRIGGER).accepts(p.isSneaking()) || !isAxe(tool) || !isLogMaterial(e.block().type())) {
      return;
    }

    if (!hasActiveAdaptation(p)) {
      return;
    }

    VEIN_MINED.add(e.block());
    BlockRef block = e.block();
    Id blockType = block.type();
    BlockStateRef logData = block.state();
    int maxBlocks = Math.max(1, (int) Math.floor(getConfig().maxBlocks * preference(p, AxePreferences.WORK).multiplier()));
    Set<BlockRef> blockMap = new HashSet<>(maxBlocks);
    int radius = getRadius(getLevel(p));
    int radiusSquared = radius * radius;
    scan:
    for (int i = 0; i < radius; i++) {
      for (int x = -i; x <= i; x++) {
        for (int y = -i; y <= i; y++) {
          for (int z = -i; z <= i; z++) {
            if (blockMap.size() >= maxBlocks) {
              break scan;
            }
            BlockRef b = block.relative(x, y, z);
            if ((!Tasks.isFoliaThreading() || Tasks.isOwnedByCurrentRegion(b.location()))
                && b.type().equals(blockType)) {
              if ((x * x) + (y * y) + (z * z) > radiusSquared) {
                continue;
              }
              if (!canBlockBreak(p, b.location())) {
                continue;
              }
              blockMap.add(b);
            }
          }
        }
      }
    }

    Set<BlockRef> siblings = new HashSet<>(blockMap);
    siblings.remove(block);
    boolean scheduled = Tasks.runEntity(
        p,
        () -> mineWood(p, block, blockType, logData, siblings),
        1
    );
    if (!scheduled) {
      VEIN_MINED.remove(block);
    }
  }

  private void mineWood(PlayerRef player, BlockRef origin, Id blockType, BlockStateRef logData,
                        Set<BlockRef> targets) {
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

    timeline(originLocation.add(0.5D, 0.5D, 0.5D))
        .duration(3)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.CRIT_MAGIC, 0.7D - (0.6D * progress), 8, 0.0D);
          if (tick == 0) {
            fx.sound(Sounds.ITEM_AXE_STRIP, 1.0F, 0.7F);
          }
        })
        .start();
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

    emitWoodFeedback(player, origin, logData, brokenBlocks);
  }

  private void emitWoodFeedback(PlayerRef player, BlockRef origin, BlockStateRef logData,
                                List<BlockRef> brokenBlocks) {
    int logsVeinmined = brokenBlocks.size();
    if (logsVeinmined > 0) {
      addStat(player, "axe.wood-veinminer.logs-veinmined", logsVeinmined);
      int minY = Integer.MAX_VALUE;
      int maxY = Integer.MIN_VALUE;
      double sumX = 0.0D;
      double sumZ = 0.0D;
      for (BlockRef b : brokenBlocks) {
        minY = Math.min(minY, b.y());
        maxY = Math.max(maxY, b.y());
        sumX += b.x();
        sumZ += b.z();
      }
      double climb = maxY - minY;
      int steps = Math.min(10, Math.max(3, logsVeinmined));
      int lastTick = steps - 1;
      Loc cascade = new Loc(origin.world(), (sumX / logsVeinmined) + 0.5D, minY + 0.5D, (sumZ / logsVeinmined) + 0.5D);
      timeline(cascade)
          .duration(steps)
          .priority(FxPriority.TRANSITION)
          .cullRadius(28)
          .frame((fx, tick, progress) -> {
            fx.particle(Particles.blockCrack(logData), 4, 0.0D, climb * progress, 0.0D, 0.2D, 0.02D);
            if ((tick & 1) == 0) {
              fx.sound(Sounds.BLOCK_WOOD_BREAK, 0.6F, (float) (0.8D + (0.5D * progress)));
            }
            if (tick == lastTick) {
              fx.sound(Sounds.ENTITY_RAVAGER_STUNNED, 0.3F, 0.5F);
            }
          })
          .start();
      if (logsVeinmined >= 15 && grantOnce(player, "challenge_axe_wood_vein_cascade")) {
        fx(player.location().add(0.0D, 1.0D, 0.0D), FxPriority.TRANSITION)
            .column(Particles.TOTEM, 16, 2.0D)
            .chord(Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.1F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.5F);
      }
    }
  }

  private static boolean isLogMaterial(Id type) {
    String name = type.path();
    return LOG_LIKE.contains(type)
        || name.endsWith("_log")
        || name.endsWith("_wood");
  }

  @ConfigDescription("Break bulk wood at once while sneaking.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blocks for the Axe Wood Veinminer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxBlocks = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Range for the Axe Wood Veinminer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseRange = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.95;
      initialCost = 4;
    }
  }
}
