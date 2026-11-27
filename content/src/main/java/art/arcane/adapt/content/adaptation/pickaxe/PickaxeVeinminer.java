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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.integration.HiddenVein;
import art.arcane.adapt.api.integration.Integrations;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
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

import java.util.ArrayList;
import java.util.List;

import static art.arcane.adapt.api.world.BlockFlag.VEIN_MINED;

public class PickaxeVeinminer extends SimpleAdaptation<PickaxeVeinminer.Config> {
  public static final Id ID = Id.adapt("pickaxe/veinminer");
  public PickaxeVeinminer() {
    super(ID);
    registerConfiguration(PickaxeVeinminer.Config.class);
    setLocalizationKey("adapt.pickaxe.vein_miner");
    setIcon(Id.minecraft("iron_pickaxe"));
    setInterval(8484);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond_pickaxe"))
        .key("challenge_pickaxe_veinminer_2500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond_pickaxe"))
        .key("challenge_pickaxe_veinminer_20")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_veinminer_2500", "pickaxe.veinminer.ores-veinmined", 2500, 500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, PickaxePreferences.TRIGGER, PickaxePreferences.MATERIALS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.pickaxe.vein_miner.lore1").resolve());
    statLore(v, TextFormat.GREEN, "", level + getConfig().baseRange, 2);
    v.addLore(TextFormat.ITALIC + Text.key("adapt.pickaxe.vein_miner.lore3").resolve());
  }

  private int getRadius(int lvl) {
    return lvl + getConfig().baseRange;
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(BlockBreakEvent e) {
    if (VEIN_MINED.get(e.block())) {
      return;
    }

    PlayerRef p = e.player();
    ItemRef tool = p.inventory().mainHand();
    if (!preference(p, PickaxePreferences.TRIGGER).accepts(p.isSneaking()) || !ItemKinds.isPickaxe(tool)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!PickaxeVein.isVeinminable(e.block().type())) {
      chainHiddenVein(e.block(), p, level);
      return;
    }
    BlockRef block = e.block();
    if (!preference(p, PickaxePreferences.MATERIALS).accepts(block.type())) {
      return;
    }
    Id targetFamily = PickaxeVein.veinFamily(block.type());
    List<BlockRef> siblings = PickaxeVein.siblings(this, p, block, getRadius(level), getConfig().maxBlocks);
    AdaptApi.scheduler().runEntity(p, () -> mineConnectedVein(p, block, targetFamily, siblings), 1);
  }

  private void mineConnectedVein(PlayerRef player, BlockRef origin, Id targetFamily, List<BlockRef> siblings) {
    int successful = PickaxeVein.breakSiblings(this, player, origin, targetFamily, siblings,
        (Id type) -> preference(player, PickaxePreferences.MATERIALS).accepts(type),
        (BlockRef sibling, BlockStateRef blockData, int broken) -> {
          FxEmitter crumble = fx(sibling.center(), FxPriority.TRAIL)
              .particle(Particles.blockCrack(blockData), 3, 0, 0, 0, 0.25, 0.0);
          if ((broken & 3) == 0) {
            crumble.sound(Sounds.BLOCK_STONE_BREAK, 0.5f,
                (float) (0.8D + Math.min(0.5D, broken * 0.02D)));
          }
        });
    completeVeinFeedback(player, origin, successful);
  }

  private void completeVeinFeedback(PlayerRef player, BlockRef origin, int successful) {
    if (successful <= 0) {
      return;
    }
    PlayerProfile adaptPlayer = getPlayer(player);
    if (adaptPlayer == null) {
      return;
    }
    addStat(player, "pickaxe.veinminer.ores-veinmined", successful);
    double ringRadius = Math.min(4.0D, 0.8D + (successful * 0.03D));
    fx(origin.center(), FxPriority.GAMEPLAY)
        .ring(Particles.ELECTRIC_SPARK, ringRadius, Math.min(28, 8 + successful), 0.2D)
        .chord(Sounds.BLOCK_AMETHYST_CLUSTER_BREAK, 0.7f, 0.9f,
            Sounds.BLOCK_DEEPSLATE_BREAK, 0.5f, 0.6f);
    if (successful < 20) {
      return;
    }
    fx(player, FxPriority.TRANSITION)
        .dustHelix(0.6D, 2.0D, 14, 0, 1.0F)
        .sound(Sounds.ENTITY_PLAYER_LEVELUP, 0.4f, 2.0f);
    if (AdaptApi.settings().advancements()
        && !adaptPlayer.isGranted(getId(), "challenge_pickaxe_veinminer_20")) {
      adaptPlayer.grant(getId(), "challenge_pickaxe_veinminer_20");
    }
  }

  private void chainHiddenVein(BlockRef block, PlayerRef p, int level) {
    PickaxePreferences.Materials selection = preference(p, PickaxePreferences.MATERIALS);
    if (selection != PickaxePreferences.Materials.ALL) {
      boolean accepted = false;
      for (HiddenVein target : Integrations.hiddenVeins(block.location(), 1)) {
        if (sameBlock(target.location(), block) && selection.accepts(target.display())) {
          accepted = true;
          break;
        }
      }
      if (!accepted) {
        return;
      }
    }
    List<BlockRef> siblings = Integrations.hiddenVeinSiblings(block);
    if (siblings.isEmpty()) {
      return;
    }

    int radius = getRadius(level);
    int radiusSquared = radius * radius;
    Loc origin = block.location();
    Id originType = block.type();
    List<BlockRef> targets = new ArrayList<>();
    for (BlockRef sibling : siblings) {
      Loc siblingLocation = sibling.location();
      if ((!AdaptApi.scheduler().isRegionized() || AdaptApi.scheduler().owns(siblingLocation))
          && siblingLocation.distanceSquared(origin) <= radiusSquared
          && canBlockBreak(p, siblingLocation)) {
        targets.add(sibling);
      }
    }

    if (targets.isEmpty()) {
      return;
    }

    AdaptApi.scheduler().runEntity(p, () -> {
      if (!p.isOnline() || getActiveLevel(p) <= 0
          || !p.world().uid().equals(block.world().uid())
          || (AdaptApi.scheduler().isRegionized()
          && (!AdaptApi.scheduler().owns(p) || !AdaptApi.scheduler().owns(block.location())))
          || block.type().equals(originType)) {
        return;
      }
      int successful = 0;
      for (BlockRef target : targets) {
        if (!p.isOnline()
            || !p.world().uid().equals(target.world().uid())
            || (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(target.location()))
            || !canBlockBreak(p, target.location())
            || VEIN_MINED.get(target)) {
          continue;
        }
        VEIN_MINED.add(target);
        try {
          if (ProtectionProbes.attemptBlockBreak(p, target)) {
            successful++;
          }
        } finally {
          VEIN_MINED.remove(target);
        }
      }
      if (successful > 0) {
        addStat(p, "pickaxe.veinminer.ores-veinmined", successful);
        fx(block.center(), FxPriority.GAMEPLAY)
            .particle(Particles.WAX_ON, Math.min(12, successful * 2), 0, 0.3, 0, 0.4, 0.02)
            .sound(Sounds.BLOCK_AMETHYST_BLOCK_HIT, 0.5f, 1.4f);
      }
    }, 1);
  }

  private static boolean sameBlock(Loc location, BlockRef block) {
    return location.world() != null && location.world().uid().equals(block.world().uid())
        && location.blockX() == block.x() && location.blockY() == block.y() && location.blockZ() == block.z();
  }

  @ConfigDescription("Break connected ore veins at once while sneaking.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Base Range for the Pickaxe Veinminer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseRange = 2;
    @ConfigDoc(value = "Maximum blocks mined by one Veinminer activation.", impact = "Higher values allow larger artificial veins but increase the worst-case block update cost.")
    int maxBlocks = 64;

    public Config() {
      baseCost = 7;
      costFactor = 0.7;
      initialCost = 6;
    }
  }
}
