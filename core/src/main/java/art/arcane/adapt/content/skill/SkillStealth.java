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

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.skill.SkillOwnerPulse;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.MovementXp;
import art.arcane.adapt.content.adaptation.stealth.StealthAssassinate;
import art.arcane.adapt.content.adaptation.stealth.StealthCore;
import art.arcane.adapt.content.adaptation.stealth.StealthCutpurse;
import art.arcane.adapt.content.adaptation.stealth.StealthDecoySwap;
import art.arcane.adapt.content.adaptation.stealth.StealthEnderVeil;
import art.arcane.adapt.content.adaptation.stealth.StealthGhostArmor;
import art.arcane.adapt.content.adaptation.stealth.StealthShadowDecoy;
import art.arcane.adapt.content.adaptation.stealth.StealthShadowmeld;
import art.arcane.adapt.content.adaptation.stealth.StealthSight;
import art.arcane.adapt.content.adaptation.stealth.StealthSmokePellet;
import art.arcane.adapt.content.adaptation.stealth.StealthSnatch;
import art.arcane.adapt.content.adaptation.stealth.StealthSpeed;
import art.arcane.adapt.content.adaptation.stealth.StealthTrapSense;
import art.arcane.adapt.content.adaptation.stealth.StealthUmbralRecovery;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

public class SkillStealth extends SimpleSkill<SkillStealth.Config> {
  private final Cooldowns cooldowns = cooldowns();
  private final SkillOwnerPulse.Registration ownerPulse;
  private final Map<UUID, Loc> lastPassivePositions = playerState();

  public SkillStealth() {
    super("stealth", SkillPresentation.of(SkillMessages.STEALTH_NAME, SkillMessages.STEALTH_ICON, SkillMessages.STEALTH_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.DARK_GRAY);
    setInterval(1412);
    setIcon(Id.minecraft("wither_rose"));
    StealthShadowDecoy shadowDecoy = new StealthShadowDecoy();
    StealthSmokePellet smokePellet = new StealthSmokePellet();
    StealthCore stealth = new StealthCore(shadowDecoy, smokePellet);
    registerAdaptation(stealth);
    registerAdaptation(new StealthSpeed());
    registerAdaptation(new StealthSnatch());
    registerAdaptation(new StealthGhostArmor());
    registerAdaptation(new StealthSight());
    registerAdaptation(new StealthEnderVeil());
    registerAdaptation(shadowDecoy);
    registerAdaptation(new StealthShadowmeld(stealth, smokePellet));
    registerAdaptation(smokePellet);
    registerAdaptation(new StealthCutpurse(stealth));
    registerAdaptation(new StealthTrapSense());
    registerAdaptation(new StealthAssassinate(stealth));
    registerAdaptation(new StealthDecoySwap(shadowDecoy));
    registerAdaptation(new StealthUmbralRecovery());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("leather_leggings"))
        .key("challenge_sneak_1k")
        .model(CustomModel.get(Id.minecraft("leather_leggings"), "advancement", "stealth", "challenge_sneak_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("chainmail_leggings"))
            .key("challenge_sneak_5k")
            .model(CustomModel.get(Id.minecraft("chainmail_leggings"), "advancement", "stealth", "challenge_sneak_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("netherite_leggings"))
                .key("challenge_sneak_20k")
                .model(CustomModel.get(Id.minecraft("netherite_leggings"), "advancement", "stealth", "challenge_sneak_20k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_sneak_1k", "move.sneak", 1000, () -> getConfig().challengeSneak1kReward);
    registerMilestone("challenge_sneak_5k", "move.sneak", 5000, () -> getConfig().challengeSneak5kReward);
    registerMilestone("challenge_sneak_20k", "move.sneak", 20000, () -> getConfig().challengeSneak20kReward);

    // Chain 2 - Stealth Damage While Sneaking
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("stone_sword"))
        .key("challenge_stealth_dmg_500")
        .model(CustomModel.get(Id.minecraft("stone_sword"), "advancement", "stealth", "challenge_stealth_dmg_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_stealth_dmg_5k")
            .model(CustomModel.get(Id.minecraft("netherite_sword"), "advancement", "stealth", "challenge_stealth_dmg_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_dmg_500", "stealth.damage.sneaking", 500, () -> getConfig().challengeStealthDmg500Reward);
    registerMilestone("challenge_stealth_dmg_5k", "stealth.damage.sneaking", 5000, () -> getConfig().challengeStealthDmg5kReward);

    // Chain 3 - Stealth Kills While Sneaking
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("skeleton_skull"))
        .key("challenge_stealth_kills_10")
        .model(CustomModel.get(Id.minecraft("skeleton_skull"), "advancement", "stealth", "challenge_stealth_kills_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("wither_rose"))
            .key("challenge_stealth_kills_100")
            .model(CustomModel.get(Id.minecraft("wither_rose"), "advancement", "stealth", "challenge_stealth_kills_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_kills_10", "stealth.kills.sneaking", 10, () -> getConfig().challengeStealthKills10Reward);
    registerMilestone("challenge_stealth_kills_100", "stealth.kills.sneaking", 100, () -> getConfig().challengeStealthKills100Reward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bow"))
        .key("challenge_stealth_arrows_50")
        .model(CustomModel.get(Id.minecraft("bow"), "advancement", "stealth", "challenge_stealth_arrows_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("crossbow"))
            .key("challenge_stealth_arrows_500")
            .model(CustomModel.get(Id.minecraft("crossbow"), "advancement", "stealth", "challenge_stealth_arrows_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_arrows_50", "stealth.arrows.sneaking", 50, () -> getConfig().challengeStealthArrows50Reward);
    registerMilestone("challenge_stealth_arrows_500", "stealth.arrows.sneaking", 500, () -> getConfig().challengeStealthArrows500Reward);
    ownerPulse = SkillOwnerPulse.register(this, this::getInterval, this::pulseSneakingXp);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef p)
        || !p.isSneaking()
        || !checkValidEntity(e.entity())) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      if (!cooldowns.isReady(p.uuid(), getConfig().sneakCombatXpCooldown)) {
        return;
      }
      double creditedDamage = CombatXp.credit(getPlayer(p).getData().getCombatXp(), getName(), p, e);
      if (creditedDamage <= 0D) {
        return;
      }
      addStat(p, "stealth.damage.sneaking", creditedDamage);
      cooldowns.mark(p.uuid());
      xp(p, e.entity().location(), creditedDamage * getConfig().sneakCombatXPMultiplier, "stealth:combat");
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    if (e.platformCancelled() || e.killer() == null || GolemKillCredit.isGolemKill(victim)) {
      return;
    }

    if (MinionIdentity.isMinion(victim) || CombatXp.isGraveMob(victim)) {
      return;
    }

    PlayerRef p = e.killer();
    if (p.isSneaking()) {
      shouldReturnForPlayer(p, () -> {
        addStat(p, "stealth.kills.sneaking", 1);
        xp(p, victim.location(), getConfig().sneakKillXP);
        fx(victim.location().add(0, 0.5D, 0), FxPriority.COMBAT)
            .burst(Particles.SMOKE, 6, 0.2D)
            .burst(Particles.CRIT, 2, 0.15D)
            .chord(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.5F, 0.7F, Sounds.PARTICLE_SOUL_ESCAPE, 0.4F, 1.3F);
      });
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    if (!(e.projectile().shooter() instanceof PlayerRef p)) {
      return;
    }
    if (p.isSneaking()) {
      shouldReturnForPlayer(p, e, () -> {
        addStat(p, "stealth.arrows.sneaking", 1);
      });
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent event) {
    lastPassivePositions.remove(event.player().uuid());
  }

  @Override
  public void unregister() {
    ownerPulse.unregister();
    super.unregister();
  }

  private void pulseSneakingXp(AdaptPlayer adaptPlayer, PlayerRef player, long elapsedMillis, long cadenceMillis) {
    shouldReturnForPlayer(player, () -> {
      Loc current = player.location();
      Loc previous = lastPassivePositions.put(player.uuid(), current);
      if (!MovementXp.moved(previous, current) || player.vehicle() != null
          || !player.isSneaking() || player.isSwimming() || player.isSprinting()
          || player.isFlying() || player.isGliding()) {
        return;
      }
      if (player.gameMode() != GameMode.SURVIVAL && player.gameMode() != GameMode.ADVENTURE) {
        return;
      }

      double cadenceScale = (double) elapsedMillis / cadenceMillis;
      xpSilent(player, getConfig().sneakXP * cadenceScale, "stealth:sneak");
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
    String skillColor = "&8";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sneak1k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSneak1kReward = 1750;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sneak5k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSneak5kReward = 3500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sneak20k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSneak20kReward = 8750;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sneak XP for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sneakXP = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP multiplier for dealing damage while sneaking.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sneakCombatXPMultiplier = 3.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown in milliseconds between XP awards for sneaking combat damage.", impact = "Higher values reduce repeated combat XP frequency; lower values reward hits more often.")
    long sneakCombatXpCooldown = 1250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP awarded for killing while sneaking.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sneakKillXP = 15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Dmg 500 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeStealthDmg500Reward = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Dmg 5k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeStealthDmg5kReward = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Kills 10 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeStealthKills10Reward = 1000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Kills 100 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeStealthKills100Reward = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Arrows 50 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeStealthArrows50Reward = 1250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Arrows 500 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeStealthArrows500Reward = 5000;
  }
}
