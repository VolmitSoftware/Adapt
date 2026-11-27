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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.XpSource;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.architect.ArchitectBrickbat;
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
import art.arcane.adapt.content.adaptation.architect.blocks.ArchitectBoon;
import art.arcane.adapt.content.adaptation.architect.blocks.ArchitectFightStrike;
import art.arcane.adapt.content.adaptation.architect.blocks.ArchitectFuel;
import art.arcane.adapt.content.adaptation.architect.blocks.ArchitectState;
import art.arcane.adapt.content.adaptation.architect.blocks.ArchitectTrigger;
import art.arcane.adapt.content.adaptation.architect.blocks.ArchitectWorkStrike;
import art.arcane.adapt.content.adaptation.architect.upgraded.MasterBuilder;
import art.arcane.adapt.content.adaptation.architect.upgraded.Skyhook;
import art.arcane.adapt.content.adaptation.architect.upgraded.WreckingHand;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Set;
import java.util.Locale;

public class SkillArchitect extends SimpleSkill<SkillArchitect.Config> {
  public static final Id ID = Id.adapt("architect");
  private final XpSource place = xpSource("place", Text.key("adapt.xp_guide.architect.place"));
  private final Cooldowns highBuildPing = cooldowns();

  public SkillArchitect() {
    super(ID);
    xpNote(Text.key("adapt.xp_guide.architect.throw"));
    xpNote(Text.key("adapt.xp_guide.architect.storage"));
    registerConfiguration(Config.class);
    setColor(Rgb.of(0x55FFFF));
    setInterval(3100);
    setIcon(Id.minecraft("iron_bars"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("brick")).key("challenge_place_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Id.minecraft("brick"))
            .key("challenge_place_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
                .icon(Id.minecraft("nether_brick"))
                .key("challenge_place_50k")
                .frame(AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_place_1k", "blocks.placed", 1000, () -> getConfig().challengePlace1kReward);
    registerMilestone("challenge_place_5k", "blocks.placed", 5000, () -> getConfig().challengePlace1kReward);
    registerMilestone("challenge_place_50k", "blocks.placed", 50000, () -> getConfig().challengePlace1kReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_pickaxe")).key("challenge_demolish_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("tnt"))
            .key("challenge_demolish_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_demolish_500", "blocks.broken", 500, () -> getConfig().challengePlace1kReward);
    registerMilestone("challenge_demolish_5k", "blocks.broken", 5000, () -> getConfig().challengePlace1kReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("gold_ingot")).key("challenge_value_placed_10k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond"))
            .key("challenge_value_placed_100k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_value_placed_10k", "blocks.placed.value", 10000, () -> getConfig().challengePlace1kReward);
    registerMilestone("challenge_value_placed_100k", "blocks.placed.value", 100000, () -> getConfig().challengePlace1kReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("tnt_minecart")).key("challenge_demolish_val_5k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("end_crystal"))
            .key("challenge_demolish_val_50k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_demolish_val_5k", "architect.demolish.value", 5000, () -> getConfig().challengePlace1kReward);
    registerMilestone("challenge_demolish_val_50k", "architect.demolish.value", 50000, () -> getConfig().challengePlace1kReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("scaffolding")).key("challenge_high_build_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("lightning_rod"))
            .key("challenge_high_build_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_high_build_100", "architect.builds.high", 100, () -> getConfig().challengePlace1kReward);
    registerMilestone("challenge_high_build_1k", "architect.builds.high", 1000, () -> getConfig().challengePlace1kReward * 2);

    setIcon(Id.minecraft("smithing_table"));
    registerAdaptation(ArchitectGlass.ID, ArchitectGlass::new);
    registerAdaptation(ArchitectFoundation.ID, ArchitectFoundation::new);
    registerAdaptation(ArchitectPlacement.ID, ArchitectPlacement::new);
    registerAdaptation(ArchitectWirelessRedstone.ID, ArchitectWirelessRedstone::new);
    registerAdaptation(ArchitectElevator.ID, ArchitectElevator::new);
    registerAdaptation(ArchitectSmartShape.ID, ArchitectSmartShape::new);
    registerAdaptation(ArchitectScaffolder.ID, ArchitectScaffolder::new);
    registerAdaptation(ArchitectSupplyLine.ID, ArchitectSupplyLine::new);
    registerAdaptation(ArchitectSteadyHands.ID, ArchitectSteadyHands::new);
    registerAdaptation(ArchitectChalkLine.ID, ArchitectChalkLine::new);
    registerAdaptation(ArchitectDemolition.ID, ArchitectDemolition::new);
    registerAdaptation(ArchitectStonecutterSavant.ID, ArchitectStonecutterSavant::new);
    registerAdaptation(ArchitectBrickbat.ID, ArchitectBrickbat::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.architect"), Text.key("adapt.mutated.adjective.architect"))
        .trigger(TriggerKind.WORK, ArchitectTrigger::new)
        .strikes(ArchitectFightStrike::new, ArchitectWorkStrike::new)
        .boon(ArchitectBoon::new)
        .fuel(ArchitectFuel::new)
        .state(ArchitectState::new)
        .toolUpgrade(MasterBuilder.ID, MasterBuilder::new,
            List.of(ArchitectPlacement.ID, ArchitectSupplyLine.ID, ArchitectChalkLine.ID), Set.of(ArchitectPlacement.ID, ArchitectSupplyLine.ID, ArchitectChalkLine.ID))
        .toolUpgrade(Skyhook.ID, Skyhook::new,
            List.of(ArchitectFoundation.ID, ArchitectScaffolder.ID, ArchitectSteadyHands.ID), Set.of(ArchitectFoundation.ID, ArchitectScaffolder.ID, ArchitectSteadyHands.ID))
        .toolUpgrade(WreckingHand.ID, WreckingHand::new,
            List.of(ArchitectDemolition.ID, ArchitectGlass.ID, ArchitectSmartShape.ID), Set.of(ArchitectDemolition.ID, ArchitectGlass.ID, ArchitectSmartShape.ID))
        .sounds(SoundPalette.of(Id.minecraft("block.scaffolding.place"), Id.minecraft("block.wood.place"),
            Id.minecraft("block.stone.place"), Id.minecraft("block.scaffolding.step")))
        .tint(Rgb.of(0xf6ecd0))
        // Smart Shape, Stonecutter Savant, Chalk Line and Wireless Redstone.
        .reserve(BarterGesture.SNEAK_LEFT_CLICK)
        .build());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      BlockRef block = e.block();
      if (XpProvenance.placeXpMultiplier(block) <= 0D) {
        return;
      }
      if (!ItemKinds.isStorage(AdaptApi.platform().host().items().blockState(block.type()))) {
        double v = getValue(block) * getConfig().xpValueMultiplier;
        PlayerProfile adaptPlayer = getPlayer(p);
        adaptPlayer.addStat("blocks.placed", 1);
        adaptPlayer.addStat("blocks.placed.value", v);
        if (block.y() > 128) {
          adaptPlayer.addStat("architect.builds.high", 1);
          if (highBuildPing.isReady(p.uuid(), 5000)) {
            highBuildPing.mark(p.uuid());
            fx(block.location().add(0.5, 1.0, 0.5), FxPriority.TRANSITION)
                .column(Particles.END_ROD, 3, 1.4)
                .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, 1.9f);
          }
        }

        try {
          double integrity = XpProvenance.placeXpMultiplier(block);
          double adjacency = XpProvenance.adjacencyBonusMultiplier(p, block);
          place.award(p, block.location().add(0.5, 0.5, 0.5), blockXP(block, getConfig().xpBase + v) * integrity * adjacency);
        } catch (Exception ignored) {
          AdaptApi.log().verbose("Failed to give XP to " + p.name() + " for placing " + block.type().path().toUpperCase(Locale.ROOT));
        }
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
      PlayerProfile adaptPlayer = getPlayer(p);
      adaptPlayer.addStat("blocks.broken", 1);
      adaptPlayer.addStat("architect.demolish.value", getValue(e.block()));
    });
  }



  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Challenge Place1k Reward for the Architect skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengePlace1kReward = 1750;
    @ConfigDoc(value = "Controls Xp Value Multiplier for the Architect skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpValueMultiplier = 1.5;
    @ConfigDoc(value = "Controls Xp Base for the Architect skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpBase = 3;

    public Config() {
    }
  }
}
