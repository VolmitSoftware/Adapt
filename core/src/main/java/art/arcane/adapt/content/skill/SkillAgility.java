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
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.skill.SkillOwnerPulse;
import art.arcane.adapt.api.world.AdaptPlayer;
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
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

public class SkillAgility extends SimpleSkill<SkillAgility.Config> {
  private final SkillOwnerPulse.Registration ownerPulse;
  private final Map<UUID, Loc> lastPassivePositions = playerState();
  private final Map<UUID, Double> pendingMoveDistance = playerState();

  public SkillAgility() {
    super("agility", SkillPresentation.of(SkillMessages.AGILITY_NAME, SkillMessages.AGILITY_ICON, SkillMessages.AGILITY_DESCRIPTION,
        SkillMessages.AGILITY_QUIP, SkillMessages.AGILITY_UNLOCK_QUIP_1, SkillMessages.AGILITY_UNLOCK_QUIP_2));
    registerConfiguration(Config.class);
    setColor(C.GREEN);
    setInterval(975);
    setIcon(Id.minecraft("feather"));
    registerAdaptation(new AgilityWindUp());
    registerAdaptation(new AgilityWallJump());
    registerAdaptation(new AgilitySuperJump());
    registerAdaptation(new AgilityArmorUp());
    registerAdaptation(new AgilityLadderSlide());
    registerAdaptation(new AgilityRollLanding());
    registerAdaptation(new AgilitySlipstreamSlide());
    registerAdaptation(new AgilityAirDash());
    registerAdaptation(new AgilityCatReflexes());
    registerAdaptation(new AgilityFeatherfoot());
    registerAdaptation(new AgilityVault());
    registerAdaptation(new AgilityMarathoner());
    registerAdaptation(new AgilityKipUp());
    registerUpgrades(new Slipstream(), new Skyrunner(), new Untouchable());
    registerBlocks(new AgilityTrigger(), new AgilityFightStrike(), new AgilityWorkStrike(), new AgilityBoon(),
        new AgilityFuel(), new AgilityState());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("leather_boots"))
        .key("challenge_move_1k")
        .model(CustomModel.get(Id.minecraft("leather_boots"), "advancement", "agility", "challenge_move_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("golden_boots"))
            .key("challenge_sprint_marathon")
            .model(CustomModel.get(Id.minecraft("golden_boots"), "advancement", "agility", "challenge_sprint_marathon"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_move_1k", "move", 1000, () -> getConfig().challengeMove1kReward);
    registerMilestone("challenge_sprint_marathon", "move.sprint", 42195, () -> getConfig().challengeSprintMarathonReward);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("golden_boots")).key("challenge_sprint_dist_5k")
        .model(CustomModel.get(Id.minecraft("golden_boots"), "advancement", "agility", "challenge_sprint_dist_5k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_boots"))
            .key("challenge_sprint_dist_50k")
            .model(CustomModel.get(Id.minecraft("diamond_boots"), "advancement", "agility", "challenge_sprint_dist_50k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_sprint_dist_5k", "move.sprint", 5000, () -> getConfig().challengeSprint5kReward);
    registerMilestone("challenge_sprint_dist_50k", "move.sprint", 50000, () -> getConfig().challengeSprint5kReward * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("lily_pad")).key("challenge_agility_swim_1k")
        .model(CustomModel.get(Id.minecraft("lily_pad"), "advancement", "agility", "challenge_agility_swim_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("heart_of_the_sea"))
            .key("challenge_agility_swim_10k")
            .model(CustomModel.get(Id.minecraft("heart_of_the_sea"), "advancement", "agility", "challenge_agility_swim_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_swim_1k", "move.swim", 1000, () -> getConfig().challengeSprint5kReward);
    registerMilestone("challenge_agility_swim_10k", "move.swim", 10000, () -> getConfig().challengeSprint5kReward * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("feather")).key("challenge_fly_1k")
        .model(CustomModel.get(Id.minecraft("feather"), "advancement", "agility", "challenge_fly_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("elytra"))
            .key("challenge_fly_10k")
            .model(CustomModel.get(Id.minecraft("elytra"), "advancement", "agility", "challenge_fly_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_fly_1k", "move.fly", 1000, () -> getConfig().challengeSprint5kReward);
    registerMilestone("challenge_fly_10k", "move.fly", 10000, () -> getConfig().challengeSprint5kReward * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("leather_leggings")).key("challenge_agility_sneak_500")
        .model(CustomModel.get(Id.minecraft("leather_leggings"), "advancement", "agility", "challenge_agility_sneak_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("iron_leggings"))
            .key("challenge_agility_sneak_5k")
            .model(CustomModel.get(Id.minecraft("iron_leggings"), "advancement", "agility", "challenge_agility_sneak_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_sneak_500", "move.sneak", 500, () -> getConfig().challengeSprint5kReward);
    registerMilestone("challenge_agility_sneak_5k", "move.sneak", 5000, () -> getConfig().challengeSprint5kReward * 2);
    ownerPulse = SkillOwnerPulse.register(this, this::getInterval, this::pulsePassiveXp);
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
      AdaptPlayer adaptPlayer = getPlayer(p);
      adaptPlayer.getData().addStat("move", distance);

      if (p.isFlying() || p.isGliding()) {
        adaptPlayer.getData().addStat("move.fly", distance);
        lastPassivePositions.remove(p.uuid());
        return;
      }

      if (p.isSneaking()) {
        adaptPlayer.getData().addStat("move.sneak", distance);
      } else if (p.isSwimming()) {
        adaptPlayer.getData().addStat("move.swim", distance);
      } else if (p.isSprinting()) {
        adaptPlayer.getData().addStat("move.sprint", distance);
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
    ownerPulse.unregister();
    super.unregister();
  }

  private void pulsePassiveXp(AdaptPlayer adaptPlayer, PlayerRef player, long elapsedMillis, long cadenceMillis) {
    shouldReturnForPlayer(player, () -> {
      Loc current = player.location();
      Loc previous = lastPassivePositions.put(player.uuid(), current);
      Double moved = pendingMoveDistance.remove(player.uuid());
      if (player.vehicle() != null || player.isFlying() || player.isGliding()
          || !MovementXp.moved(previous, current)) {
        return;
      }
      if (moved != null) {
        xpSilent(player, getConfig().moveXpPassive * moved, "agility:move");
      }
      double cadenceScale = (double) elapsedMillis / cadenceMillis;
      if (player.isSprinting() && !player.isFlying() && !player.isSwimming() && !player.isSneaking()) {
        xpSilent(player, getConfig().sprintXpPassive * cadenceScale, "agility:sprint");
      }

      if (player.isSwimming() && !player.isFlying() && !player.isSprinting() && !player.isSneaking()) {
        xpSilent(player, getConfig().swimXpPassive * cadenceScale, "agility:swim");
      }

      if (!player.isOnGround() && player.velocity().y() > 0D && !player.isFlying() && !player.isSneaking()) {
        xpSilent(player, getConfig().jumpXpPassive * cadenceScale, "agility:jump");
      }

      if (player.isClimbing() && !player.isFlying() && !player.isSneaking()) {
        xpSilent(player, getConfig().climbXpPassive * cadenceScale, "agility:climb");
      }
    });
  }


  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config extends SkillConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&a";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Move1k Reward for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeMove1kReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sprint5k Reward for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSprint5kReward = 2000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sprint Marathon Reward for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSprintMarathonReward = 6500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sprint Xp Passive for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sprintXpPassive = 1.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Swim Xp Passive for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double swimXpPassive = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Jump Xp Passive for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double jumpXpPassive = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Climb Xp Passive for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double climbXpPassive = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Move Xp Passive for the Agility skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double moveXpPassive = 0.15;
  }
}
