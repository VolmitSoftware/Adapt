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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.XpSource;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.xp.MovementXp;
import art.arcane.adapt.content.adaptation.agility.AgilityAirDash;
import art.arcane.adapt.content.adaptation.agility.AgilityArmorUp;
import art.arcane.adapt.content.adaptation.agility.AgilityCatReflexes;
import art.arcane.adapt.content.adaptation.agility.AgilityFeatherfoot;
import art.arcane.adapt.content.adaptation.agility.AgilityKipUp;
import art.arcane.adapt.content.adaptation.agility.AgilityLadderSlide;
import art.arcane.adapt.content.adaptation.agility.AgilityMarathoner;
import art.arcane.adapt.content.adaptation.agility.AgilityRollLanding;
import art.arcane.adapt.content.adaptation.agility.AgilitySlipstreamSlide;
import art.arcane.adapt.content.adaptation.agility.AgilitySuperJump;
import art.arcane.adapt.content.adaptation.agility.AgilityVault;
import art.arcane.adapt.content.adaptation.agility.AgilityWallJump;
import art.arcane.adapt.content.adaptation.agility.AgilityWindUp;
import art.arcane.adapt.content.adaptation.agility.blocks.AgilityBoon;
import art.arcane.adapt.content.adaptation.agility.blocks.AgilityFightStrike;
import art.arcane.adapt.content.adaptation.agility.blocks.AgilityFuel;
import art.arcane.adapt.content.adaptation.agility.blocks.AgilityState;
import art.arcane.adapt.content.adaptation.agility.blocks.AgilityTrigger;
import art.arcane.adapt.content.adaptation.agility.blocks.AgilityWorkStrike;
import art.arcane.adapt.content.adaptation.agility.upgraded.Skyrunner;
import art.arcane.adapt.content.adaptation.agility.upgraded.Slipstream;
import art.arcane.adapt.content.adaptation.agility.upgraded.Untouchable;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

public class SkillAgility extends SimpleSkill<SkillAgility.Config> {
  public static final Id ID = Id.adapt("agility");
  private final XpSource move = xpSource("move", Text.key("adapt.xp_guide.agility.move"));
  private final XpSource sprint = xpSource("sprint", Text.key("adapt.xp_guide.agility.move"));
  private final XpSource swim = xpSource("swim", Text.key("adapt.xp_guide.agility.move"));
  private final XpSource jump = xpSource("jump", Text.key("adapt.xp_guide.agility.move"));
  private final XpSource climb = xpSource("climb", Text.key("adapt.xp_guide.agility.move"));
  private final Map<UUID, Loc> lastPassivePositions = playerState();
  private final Map<UUID, Double> pendingMoveDistance = playerState();

  public SkillAgility() {
    super(ID);
    registerConfiguration(Config.class);
    setColor(Rgb.of(0x55FF55));
    setInterval(975);
    setIcon(Id.minecraft("feather"));
    registerAdaptation(AgilityWindUp.ID, AgilityWindUp::new);
    registerAdaptation(AgilityWallJump.ID, AgilityWallJump::new);
    registerAdaptation(AgilitySuperJump.ID, AgilitySuperJump::new);
    registerAdaptation(AgilityArmorUp.ID, AgilityArmorUp::new);
    registerAdaptation(AgilityLadderSlide.ID, AgilityLadderSlide::new);
    registerAdaptation(AgilityRollLanding.ID, AgilityRollLanding::new);
    registerAdaptation(AgilitySlipstreamSlide.ID, AgilitySlipstreamSlide::new);
    registerAdaptation(AgilityAirDash.ID, AgilityAirDash::new);
    registerAdaptation(AgilityCatReflexes.ID, AgilityCatReflexes::new);
    registerAdaptation(AgilityFeatherfoot.ID, AgilityFeatherfoot::new);
    registerAdaptation(AgilityVault.ID, AgilityVault::new);
    registerAdaptation(AgilityMarathoner.ID, AgilityMarathoner::new);
    registerAdaptation(AgilityKipUp.ID, AgilityKipUp::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.agility"), Text.key("adapt.mutated.adjective.agility"))
        .trigger(TriggerKind.EITHER, AgilityTrigger::new)
        .strikes(AgilityFightStrike::new, AgilityWorkStrike::new)
        .boon(AgilityBoon::new)
        .fuel(AgilityFuel::new)
        .state(AgilityState::new)
        .upgrade(Slipstream.ID, Slipstream::new,
            List.of(AgilityWindUp.ID, AgilitySlipstreamSlide.ID, AgilityMarathoner.ID), Set.of())
        .toolUpgrade(Skyrunner.ID, Skyrunner::new,
            List.of(AgilityWallJump.ID, AgilitySuperJump.ID, AgilityAirDash.ID), Set.of())
        .upgrade(Untouchable.ID, Untouchable::new,
            List.of(AgilityCatReflexes.ID, AgilityRollLanding.ID, AgilityKipUp.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("entity.horse.jump"), Id.minecraft("entity.horse.gallop"),
            Id.minecraft("item.armor.equip_elytra"), Id.minecraft("entity.horse.step")))
        .tint(Rgb.of(0x9cd86a))
        // Air Dash and Skyrunner's dash.
        .reserve(BarterGesture.SNEAK_LEFT_CLICK)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("leather_boots"))
        .key("challenge_move_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("golden_boots"))
            .key("challenge_sprint_marathon")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_move_1k", "move", 1000, () -> getConfig().challengeMove1kReward);
    registerMilestone("challenge_sprint_marathon", "move.sprint", 42195, () -> getConfig().challengeSprintMarathonReward);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("golden_boots")).key("challenge_sprint_dist_5k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_boots"))
            .key("challenge_sprint_dist_50k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_sprint_dist_5k", "move.sprint", 5000, () -> getConfig().challengeSprint5kReward);
    registerMilestone("challenge_sprint_dist_50k", "move.sprint", 50000, () -> getConfig().challengeSprint5kReward * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("lily_pad")).key("challenge_agility_swim_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("heart_of_the_sea"))
            .key("challenge_agility_swim_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_swim_1k", "move.swim", 1000, () -> getConfig().challengeSprint5kReward);
    registerMilestone("challenge_agility_swim_10k", "move.swim", 10000, () -> getConfig().challengeSprint5kReward * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("feather")).key("challenge_fly_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("elytra"))
            .key("challenge_fly_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_fly_1k", "move.fly", 1000, () -> getConfig().challengeSprint5kReward);
    registerMilestone("challenge_fly_10k", "move.fly", 10000, () -> getConfig().challengeSprint5kReward * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("leather_leggings")).key("challenge_agility_sneak_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("iron_leggings"))
            .key("challenge_agility_sneak_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_sneak_500", "move.sneak", 500, () -> getConfig().challengeSprint5kReward);
    registerMilestone("challenge_agility_sneak_5k", "move.sneak", 5000, () -> getConfig().challengeSprint5kReward * 2);
    pulseLearners(this::getInterval, this::pulsePassiveXp);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    if (p.vehicle() != null) {
      lastPassivePositions.remove(p.uuid());
      pendingMoveDistance.remove(p.uuid());
      return;
    }
    Loc from = e.from();
    Loc to = e.to();
    if (to == null || !from.sameWorld(to)
        || (from.x() == to.x() && from.y() == to.y() && from.z() == to.z())) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      double distance = from.distance(to);
      if (!Double.isFinite(distance) || distance > 8D) {
        return;
      }
      PlayerProfile adaptPlayer = getPlayer(p);
      adaptPlayer.addStat("move", distance);

      if (p.isFlying() || p.isGliding()) {
        adaptPlayer.addStat("move.fly", distance);
        lastPassivePositions.remove(p.uuid());
        return;
      }

      if (p.isSneaking()) {
        adaptPlayer.addStat("move.sneak", distance);
      } else if (p.isSwimming()) {
        adaptPlayer.addStat("move.swim", distance);
      } else if (p.isSprinting()) {
        adaptPlayer.addStat("move.sprint", distance);
      }

      pendingMoveDistance.merge(p.uuid(), distance, Double::sum);
    });
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent event) {
    lastPassivePositions.remove(event.player().uuid());
    pendingMoveDistance.remove(event.player().uuid());
  }

  @Override
  public void unregister() {
    super.unregister();
  }

  private void pulsePassiveXp(PlayerRef player, long elapsedMillis) {
    long cadenceMillis = Math.max(50L, getInterval());
    shouldReturnForPlayer(player, () -> {
      Loc current = player.location();
      Loc previous = lastPassivePositions.put(player.uuid(), current);
      Double moved = pendingMoveDistance.remove(player.uuid());
      if (player.vehicle() != null || player.isFlying() || player.isGliding()
          || !MovementXp.moved(previous, current)) {
        return;
      }
      if (moved != null) {
        move.awardSilent(player, getConfig().moveXpPassive * moved);
      }
      double cadenceScale = (double) elapsedMillis / cadenceMillis;
      if (player.isSprinting() && !player.isFlying() && !player.isSwimming() && !player.isSneaking()) {
        sprint.awardSilent(player, getConfig().sprintXpPassive * cadenceScale);
      }

      if (player.isSwimming() && !player.isFlying() && !player.isSprinting() && !player.isSneaking()) {
        swim.awardSilent(player, getConfig().swimXpPassive * cadenceScale);
      }

      if (!player.isOnGround() && player.velocity().y() > 0D && !player.isFlying() && !player.isSneaking()) {
        jump.awardSilent(player, getConfig().jumpXpPassive * cadenceScale);
      }

      if (player.isClimbing() && !player.isFlying() && !player.isSneaking()) {
        climb.awardSilent(player, getConfig().climbXpPassive * cadenceScale);
      }
    });
  }



  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Challenge Move1k Reward for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeMove1kReward = 500;
    @ConfigDoc(value = "Controls Challenge Sprint5k Reward for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSprint5kReward = 2000;
    @ConfigDoc(value = "Controls Challenge Sprint Marathon Reward for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSprintMarathonReward = 6500;
    @ConfigDoc(value = "Controls Sprint Xp Passive for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sprintXpPassive = 1.05;
    @ConfigDoc(value = "Controls Swim Xp Passive for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double swimXpPassive = 0.4;
    @ConfigDoc(value = "Controls Jump Xp Passive for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double jumpXpPassive = 0.3;
    @ConfigDoc(value = "Controls Climb Xp Passive for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double climbXpPassive = 0.8;
    @ConfigDoc(value = "Controls Move Xp Passive for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double moveXpPassive = 0.15;

    public Config() {
    }
  }
}
