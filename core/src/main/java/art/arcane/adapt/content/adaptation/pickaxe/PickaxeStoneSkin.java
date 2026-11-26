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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PickaxeMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.Set;

public class PickaxeStoneSkin extends SimpleAdaptation<PickaxeStoneSkin.Config> {
  private static final Set<Id> STONE_BLOCKS = Set.of(
      Id.minecraft("stone"), Id.minecraft("cobblestone"), Id.minecraft("mossy_cobblestone"),
      Id.minecraft("deepslate"), Id.minecraft("cobbled_deepslate"), Id.minecraft("tuff"),
      Id.minecraft("calcite"), Id.minecraft("andesite"), Id.minecraft("diorite"), Id.minecraft("granite"));
  private final PickaxeHardening hardening = new PickaxeHardening(this, playerState());

  public PickaxeStoneSkin() {
    super("pickaxe-stone-skin");
    registerConfiguration(PickaxeStoneSkin.Config.class);
    setIcon(Id.minecraft("stone"));
    setInterval(5377);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("stone"))
        .key("challenge_pickaxe_stoneskin_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_stoneskin_10k", "pickaxe.stone-skin.stacks-gained", 10000, 500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(PickaxeMessages.STONE_SKIN_LORE1));
    statLore(v, C.GREEN, "", getTierCap(level), 2);
  }

  private int getTierCap(int level) {
    return Math.min(level, getConfig().maxAmplifier + 1);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (!STONE_BLOCKS.contains(e.block().type())) {
      return;
    }

    PlayerRef p = e.player();
    if (!isPickaxe(p.inventory().mainHand())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.block().location());
    if (context == null) {
      return;
    }

    PickaxeHardening.Step step = hardening.harden(p, getTierCap(context.level()), getConfig().blocksPerStack,
        getConfig().stackDurationMs, getConfig().effectDurationTicks);
    if (step.gained()) {
      addStat(p, "pickaxe.stone-skin.stacks-gained", 1);
    }
    int tier = step.tier();
    if (tier > 0 && step.rose()) {
      fx(p.location().add(0, 1, 0), FxPriority.TRANSITION)
          .dome(Particles.CLOUD, 1.2D, Math.min(20, tier * 4))
          .sound(Sounds.BLOCK_STONE_PLACE, 0.5f, 0.8f + ((tier - 1) * 0.2f));
      if (step.reachedCap()) {
        fx(p.location(), FxPriority.TRANSITION)
            .dustRing(0.9D, 12, 1.0F)
            .sound(Sounds.BLOCK_ANCIENT_DEBRIS_PLACE, 0.4f, 0.9f);
      }
    }
  }

  @ConfigDescription("Breaking stone-type blocks builds short-lived stacking damage resistance.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Stone blocks that must be broken to gain one resistance tier.", impact = "Higher values make resistance tiers slower to build.")
    int blocksPerStack = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds before built stacks expire without mining.", impact = "Higher values keep stacks alive longer between breaks.")
    long stackDurationMs = 6000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the applied resistance effect.", impact = "Higher values keep the resistance active longer after each break.")
    int effectDurationTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum resistance amplifier this adaptation can reach.", impact = "Higher values allow stronger damage reduction at max stacks.")
    int maxAmplifier = 3;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 4;
      initialCost = 1;
    }
  }
}
