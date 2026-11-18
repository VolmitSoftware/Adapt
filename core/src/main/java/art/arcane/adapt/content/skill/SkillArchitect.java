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

package art.arcane.adapt.content.skill;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.XpNovelty;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.architect.ArchitectChalkLine;
import art.arcane.adapt.content.adaptation.architect.ArchitectDemolition;
import art.arcane.adapt.content.adaptation.architect.ArchitectElevator;
import art.arcane.adapt.content.adaptation.architect.ArchitectFoundation;
import art.arcane.adapt.content.adaptation.architect.ArchitectGlass;
import art.arcane.adapt.content.adaptation.architect.ArchitectPlacement;
import art.arcane.adapt.content.adaptation.architect.ArchitectScaffolder;
import art.arcane.adapt.content.adaptation.architect.ArchitectSmartShape;
import art.arcane.adapt.content.adaptation.architect.ArchitectSteadyHands;
import art.arcane.adapt.content.adaptation.architect.ArchitectStonecutterSavant;
import art.arcane.adapt.content.adaptation.architect.ArchitectSupplyLine;
import art.arcane.adapt.content.adaptation.architect.ArchitectWirelessRedstone;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.AdaptLog;
import lombok.NoArgsConstructor;

import java.util.Locale;

public class SkillArchitect extends SimpleSkill<SkillArchitect.Config> {
  private final Cooldowns placeCooldown = cooldowns();
  private final Cooldowns highBuildPing = cooldowns();

  public SkillArchitect() {
    super("architect", SkillPresentation.of(SkillMessages.ARCHITECT_NAME, SkillMessages.ARCHITECT_ICON, SkillMessages.ARCHITECT_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.AQUA);
    setInterval(3100);
    setIcon(Id.minecraft("iron_bars"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("brick")).key("challenge_place_1k")
        .model(CustomModel.get(Id.minecraft("brick"), "advancement", "architect", "challenge_place_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Id.minecraft("brick"))
            .key("challenge_place_5k")
            .model(CustomModel.get(Id.minecraft("brick"), "advancement", "architect", "challenge_place_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
                .icon(Id.minecraft("nether_brick"))
                .key("challenge_place_50k")
                .model(CustomModel.get(Id.minecraft("nether_brick"), "advancement", "architect", "challenge_place_50k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_place_1k", "blocks.placed", 1000, () -> getConfig().challengePlace1kReward);
    registerMilestone("challenge_place_5k", "blocks.placed", 5000, () -> getConfig().challengePlace1kReward);
    registerMilestone("challenge_place_50k", "blocks.placed", 50000, () -> getConfig().challengePlace1kReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_pickaxe")).key("challenge_demolish_500")
        .model(CustomModel.get(Id.minecraft("iron_pickaxe"), "advancement", "architect", "challenge_demolish_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("tnt"))
            .key("challenge_demolish_5k")
            .model(CustomModel.get(Id.minecraft("tnt"), "advancement", "architect", "challenge_demolish_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_demolish_500", "blocks.broken", 500, () -> getConfig().challengePlace1kReward);
    registerMilestone("challenge_demolish_5k", "blocks.broken", 5000, () -> getConfig().challengePlace1kReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("gold_ingot")).key("challenge_value_placed_10k")
        .model(CustomModel.get(Id.minecraft("gold_ingot"), "advancement", "architect", "challenge_value_placed_10k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond"))
            .key("challenge_value_placed_100k")
            .model(CustomModel.get(Id.minecraft("diamond"), "advancement", "architect", "challenge_value_placed_100k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_value_placed_10k", "blocks.placed.value", 10000, () -> getConfig().challengePlace1kReward);
    registerMilestone("challenge_value_placed_100k", "blocks.placed.value", 100000, () -> getConfig().challengePlace1kReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("tnt_minecart")).key("challenge_demolish_val_5k")
        .model(CustomModel.get(Id.minecraft("tnt_minecart"), "advancement", "architect", "challenge_demolish_val_5k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("end_crystal"))
            .key("challenge_demolish_val_50k")
            .model(CustomModel.get(Id.minecraft("end_crystal"), "advancement", "architect", "challenge_demolish_val_50k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_demolish_val_5k", "architect.demolish.value", 5000, () -> getConfig().challengePlace1kReward);
    registerMilestone("challenge_demolish_val_50k", "architect.demolish.value", 50000, () -> getConfig().challengePlace1kReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("scaffolding")).key("challenge_high_build_100")
        .model(CustomModel.get(Id.minecraft("scaffolding"), "advancement", "architect", "challenge_high_build_100"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("lightning_rod"))
            .key("challenge_high_build_1k")
            .model(CustomModel.get(Id.minecraft("lightning_rod"), "advancement", "architect", "challenge_high_build_1k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_high_build_100", "architect.builds.high", 100, () -> getConfig().challengePlace1kReward);
    registerMilestone("challenge_high_build_1k", "architect.builds.high", 1000, () -> getConfig().challengePlace1kReward * 2);

    setIcon(Id.minecraft("smithing_table"));
    registerAdaptation(new ArchitectGlass());
    registerAdaptation(new ArchitectFoundation());
    registerAdaptation(new ArchitectPlacement());
    registerAdaptation(new ArchitectWirelessRedstone());
    registerAdaptation(new ArchitectElevator());
    registerAdaptation(new ArchitectSmartShape());
    registerAdaptation(new ArchitectScaffolder());
    registerAdaptation(new ArchitectSupplyLine());
    registerAdaptation(new ArchitectSteadyHands());
    registerAdaptation(new ArchitectChalkLine());
    registerAdaptation(new ArchitectDemolition());
    registerAdaptation(new ArchitectStonecutterSavant());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      BlockRef block = e.block();
      if (XpProvenance.placeXpMultiplier(block) <= 0D) {
        return;
      }
      if (!isStorage(AdaptCore.platform().host().items().blockState(block.type()))) {
        double v = getValue(block) * getConfig().xpValueMultiplier;
        AdaptPlayer adaptPlayer = getPlayer(p);
        adaptPlayer.getData().addStat("blocks.placed", 1);
        adaptPlayer.getData().addStat("blocks.placed.value", v);
        if (block.y() > 128) {
          adaptPlayer.getData().addStat("architect.builds.high", 1);
          if (highBuildPing.isReady(p.uuid(), 5000)) {
            highBuildPing.mark(p.uuid());
            fx(block.location().add(0.5, 1.0, 0.5), FxPriority.TRANSITION)
                .column(Particles.END_ROD, 3, 1.4)
                .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, 1.9f);
          }
        }

        handleBlockCooldown(p, () -> {
          try {
            double integrity = XpProvenance.placeXpMultiplier(block);
            if (integrity <= 0) {
              return;
            }
            double adjacency = XpNovelty.adjacencyBonusMultiplier(p, block);
            xp(p, block.location().add(0.5, 0.5, 0.5), blockXP(block, getConfig().xpBase + v) * integrity * adjacency);
          } catch (Exception ignored) {
            AdaptLog.verbose("Failed to give XP to " + p.name() + " for placing " + block.type().path().toUpperCase(Locale.ROOT));
          }
        });
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      if (XpProvenance.breakXpMultiplier(e.block()) <= 0D) {
        return;
      }
      AdaptPlayer adaptPlayer = getPlayer(p);
      adaptPlayer.getData().addStat("blocks.broken", 1);
      adaptPlayer.getData().addStat("architect.demolish.value", getValue(e.block()));
    });
  }

  private void handleBlockCooldown(PlayerRef p, Runnable action) {
    if (!placeCooldown.isReady(p.uuid(), getConfig().cooldownDelay)) {
      return;
    }
    placeCooldown.mark(p.uuid());
    action.run();
  }


  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config extends SkillConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&b";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Place1k Reward for the Architect skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengePlace1kReward = 1750;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Value Multiplier for the Architect skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpValueMultiplier = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Architect skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Base for the Architect skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpBase = 3;
  }
}
