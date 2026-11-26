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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
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
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;

public class CraftingCompactor extends SimpleAdaptation<CraftingCompactor.Config> {
  public static final PlayerPreference<CraftingPreferences.Category> MATERIALS = CraftingPreferences.categories("materials", CraftingMessages.PREFERENCE_CRAFTINGCOMPACTOR_MATERIALS);
  public static final PlayerPreference<CommonPreferences.Scale> BATCH = CommonPreferences.scale("batch-limit", CraftingMessages.PREFERENCE_CRAFTINGCOMPACTOR_BATCH);
  public static final PlayerPreference<CraftingPreferences.Reserve> RESERVE = CraftingPreferences.reserve("loose-reserve", CraftingMessages.PREFERENCE_CRAFTINGCOMPACTOR_RESERVE);

  private static final int COMPACTOR_LEVELS = 1;
  private static final Id AIR = Id.minecraft("air");
  private static final Id CRAFTING_TABLE = Id.minecraft("crafting_table");

  private final Compaction compaction = new Compaction(this);

  public CraftingCompactor() {
    super("crafting-compactor");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_block"));
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_block"))
        .key("challenge_crafting_compactor_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_block"))
            .key("challenge_crafting_compactor_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_crafting_compactor_1k", "crafting.compactor.blocks-compacted", 1000, 400);
    registerMilestone("challenge_crafting_compactor_10k", "crafting.compactor.blocks-compacted", 10000, 1500);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, MATERIALS, BATCH, RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(CraftingMessages.COMPACTOR_LORE1));
    statLore(v, Compaction.materialsCovered(), 2);
    v.addLore(C.YELLOW + "* " + C.GRAY + AdaptLanguage.text(CraftingMessages.COMPACTOR_LORE3));
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.NORMAL)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    BlockRef targetBlock = p.targetBlock(5);
    Id targetType = targetBlock == null ? AIR : targetBlock.type();
    boolean defaultInventoryView = !p.hasOpenContainer();
    if (!isActivation(p.isSneaking(), level, defaultInventoryView, targetType)) {
      return;
    }

    e.setCancelled(true);
    int blocks = compaction.compact(p);
    if (blocks > 0) {
      addStat(p, "crafting.compactor.blocks-compacted", blocks);
      fx(p.location().add(0, 1, 0), FxPriority.AMBIENT)
          .particle(Particles.CRIT_MAGIC, Math.min(12, 3 + blocks), 0, 0.2D, 0, 0.25D, 0.15D)
          .sound(Sounds.BLOCK_STONE_PLACE, 0.4F, 1.2F);
    }
  }

  static boolean isActivation(boolean sneaking, int activeLevel, boolean defaultInventoryView, Id targetType) {
    return sneaking
        && activeLevel > 0
        && defaultInventoryView
        && CRAFTING_TABLE.equals(targetType);
  }

  @ConfigDescription("Sneak and swap hands while aiming at a Crafting Table to compact full stacks of ingots, gems, and raw ores into blocks.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 1;
      costFactor = 1;
      maxLevel = COMPACTOR_LEVELS;
      initialCost = 1;
    }
  }
}
