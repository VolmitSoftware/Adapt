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
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.XpSource;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.MobOrigin;
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
import art.arcane.adapt.content.adaptation.stealth.blocks.StealthBoon;
import art.arcane.adapt.content.adaptation.stealth.blocks.StealthFightStrike;
import art.arcane.adapt.content.adaptation.stealth.blocks.StealthFuel;
import art.arcane.adapt.content.adaptation.stealth.blocks.StealthState;
import art.arcane.adapt.content.adaptation.stealth.blocks.StealthTrigger;
import art.arcane.adapt.content.adaptation.stealth.blocks.StealthWorkStrike;
import art.arcane.adapt.content.adaptation.stealth.upgraded.DeathMark;
import art.arcane.adapt.content.adaptation.stealth.upgraded.PhantomDecoy;
import art.arcane.adapt.content.adaptation.stealth.upgraded.UmbralForm;
import art.arcane.adapt.content.golem.GolemKillCredit;
import art.arcane.adapt.api.ability.Minions;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

public class SkillStealth extends SimpleSkill<SkillStealth.Config> {
  public static final Id ID = Id.adapt("stealth");
  private final XpSource opener = xpSource("opener", Text.key("adapt.xp_guide.stealth.opener"));
  private final XpSource combat = xpSource("combat", Text.key("adapt.xp_guide.stealth.combat"));
  private final XpSource kill = xpSource("kill", Text.key("adapt.xp_guide.stealth.kill"));
  private final XpSource sneak = xpSource("sneak", Text.key("adapt.xp_guide.stealth.sneak"));
  private static final double LOOKING_AT_DOT = 0.5D;
  private static final String OPENER_LEDGER = "stealth:opener";
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, Loc> lastPassivePositions = playerState();


  private StealthCore stealth;
  private StealthShadowDecoy shadowDecoy;
  private StealthSmokePellet smokePellet;

  public SkillStealth() {
    super(ID);
    registerConfiguration(Config.class);
    setColor(Rgb.of(0x555555));
    setInterval(1412);
    setIcon(Id.minecraft("wither_rose"));
    registerAdaptation(StealthCore.ID, () -> stealth = new StealthCore(() -> shadowDecoy, () -> smokePellet));
    registerAdaptation(StealthSpeed.ID, StealthSpeed::new);
    registerAdaptation(StealthSnatch.ID, StealthSnatch::new);
    registerAdaptation(StealthGhostArmor.ID, StealthGhostArmor::new);
    registerAdaptation(StealthSight.ID, StealthSight::new);
    registerAdaptation(StealthEnderVeil.ID, StealthEnderVeil::new);
    registerAdaptation(StealthShadowDecoy.ID, () -> shadowDecoy = new StealthShadowDecoy());
    registerAdaptation(StealthShadowmeld.ID, () -> new StealthShadowmeld(() -> stealth, () -> smokePellet));
    registerAdaptation(StealthSmokePellet.ID, () -> smokePellet = new StealthSmokePellet());
    registerAdaptation(StealthCutpurse.ID, () -> new StealthCutpurse(() -> stealth));
    registerAdaptation(StealthTrapSense.ID, StealthTrapSense::new);
    registerAdaptation(StealthAssassinate.ID, () -> new StealthAssassinate(() -> stealth));
    registerAdaptation(StealthDecoySwap.ID, () -> new StealthDecoySwap(() -> shadowDecoy));
    registerAdaptation(StealthUmbralRecovery.ID, StealthUmbralRecovery::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.stealth"), Text.key("adapt.mutated.adjective.stealth"))
        .trigger(TriggerKind.FIGHT, StealthTrigger::new)
        .strikes(StealthFightStrike::new, StealthWorkStrike::new)
        .boon(StealthBoon::new)
        .fuel(StealthFuel::new)
        .state(StealthState::new)
        .upgrade(UmbralForm.ID, UmbralForm::new, List.of(StealthShadowmeld.ID, StealthGhostArmor.ID, StealthSpeed.ID), Set.of())
        .upgrade(DeathMark.ID, DeathMark::new, List.of(StealthAssassinate.ID, StealthCutpurse.ID), Set.of())
        .upgrade(PhantomDecoy.ID, PhantomDecoy::new,
            List.of(StealthShadowDecoy.ID, StealthDecoySwap.ID, StealthSmokePellet.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("entity.bat.takeoff"), Id.minecraft("block.wool.step"),
            Id.minecraft("entity.illusioner.ambient"), Id.minecraft("block.moss_carpet.step")))
        .tint(Rgb.of(0xb0aabc))
        // Phantom Decoy and Decoy Swap (both gestures), Shadowmeld's and Shadow Decoy's double-tap preferences.
        .reserve(BarterGesture.SNEAK_SWAP, BarterGesture.DOUBLE_SNEAK)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("leather_leggings"))
        .key("challenge_sneak_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("chainmail_leggings"))
            .key("challenge_sneak_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("netherite_leggings"))
                .key("challenge_sneak_20k")
                .frame(AdvancementFrame.CHALLENGE)
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
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_stealth_dmg_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_dmg_500", "stealth.damage.sneaking", 500, () -> getConfig().challengeStealthDmg500Reward);
    registerMilestone("challenge_stealth_dmg_5k", "stealth.damage.sneaking", 5000, () -> getConfig().challengeStealthDmg5kReward);

    // Chain 3 - Stealth Kills While Sneaking
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("skeleton_skull"))
        .key("challenge_stealth_kills_10")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("wither_rose"))
            .key("challenge_stealth_kills_100")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_kills_10", "stealth.kills.sneaking", 10, () -> getConfig().challengeStealthKills10Reward);
    registerMilestone("challenge_stealth_kills_100", "stealth.kills.sneaking", 100, () -> getConfig().challengeStealthKills100Reward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bow"))
        .key("challenge_stealth_arrows_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("crossbow"))
            .key("challenge_stealth_arrows_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_arrows_50", "stealth.arrows.sneaking", 50, () -> getConfig().challengeStealthArrows50Reward);
    registerMilestone("challenge_stealth_arrows_500", "stealth.arrows.sneaking", 500, () -> getConfig().challengeStealthArrows500Reward);
    pulseLearners(this::getInterval, this::pulseSneakingXp);
    registerGroup(Text.key("adapt.skill.stealth.group.concealment"), List.of(Id.parseAdapt("adapt:stealth/silent_step"), Id.parseAdapt("adapt:stealth/speed"), Id.parseAdapt("adapt:stealth/shadowmeld"), Id.parseAdapt("adapt:stealth/smoke_pellet"), Id.parseAdapt("adapt:stealth/enderveil"), Id.parseAdapt("adapt:stealth/ghost_armor")));
    registerGroup(Text.key("adapt.skill.stealth.group.tricks"), List.of(Id.parseAdapt("adapt:stealth/vision"), Id.parseAdapt("adapt:stealth/trap_sense"), Id.parseAdapt("adapt:stealth/snatch"), Id.parseAdapt("adapt:stealth/shadow_decoy"), Id.parseAdapt("adapt:stealth/decoy_swap")));
    registerGroup(Text.key("adapt.skill.stealth.group.ambush"), List.of(Id.parseAdapt("adapt:stealth/cutpurse"), Id.parseAdapt("adapt:stealth/umbral_recovery"), Id.parseAdapt("adapt:stealth/assassinate")));
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef p) || !checkValidEntity(e.entity())) {
      return;
    }
    if (e.entity() instanceof MobRef victim) {
      shouldReturnForPlayer(p, e, () -> rewardOpener(p, victim));
    }
    if (!p.isSneaking()) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      if (!cooldowns.isReady(p.uuid(), getConfig().sneakCombatXpCooldown)) {
        return;
      }
      double creditedDamage = CombatXp.credit(getId().toString(), p, e);
      if (creditedDamage <= 0D) {
        return;
      }
      addStat(p, "stealth.damage.sneaking", creditedDamage);
      cooldowns.mark(p.uuid());
      combat.award(p, e.entity().location(), creditedDamage * getConfig().sneakCombatXPMultiplier);
    });
  }

  private void rewardOpener(PlayerRef p, MobRef victim) {
    LivingRef target = victim.target();
    if (victim.isDead() || victim.isInvulnerable() || !MobOrigin.paysKillReward(p, victim)
        || target != null && target.uuid().equals(p.uuid())
        || CombatXp.claim(OPENER_LEDGER, p, victim.uuid(), 1D, 1D) <= 0D) {
      return;
    }
    double amount = openerXp(getConfig().unawareOpenerXp, getConfig().unseenOpenerBonusXp,
        p.isSneaking() && !isLookingAt(victim.eyeLocation(), p.eyeLocation()));
    opener.award(p, victim.location(), amount);
  }

  static double openerXp(double unawareXp, double unseenBonusXp, boolean sneakingUnseen) {
    double base = Math.max(0D, unawareXp);
    return sneakingUnseen ? base + Math.max(0D, unseenBonusXp) : base;
  }

  public static boolean isLookingAt(Loc viewer, Loc target) {
    Vec3 toTarget = target.position().subtract(viewer.position());
    if (toTarget.lengthSquared() < 1.0E-6D) {
      return true;
    }
    return viewer.direction().normalize().dot(toTarget.normalize()) >= LOOKING_AT_DOT;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    if (e.platformCancelled() || e.killer() == null || GolemKillCredit.isGolemKill(victim)) {
      return;
    }

    if (Minions.isMinion(victim) || CombatXp.isGraveMob(victim)) {
      return;
    }

    PlayerRef p = e.killer();
    if (p.isSneaking()) {
      shouldReturnForPlayer(p, () -> {
        addStat(p, "stealth.kills.sneaking", 1);
        if (MobOrigin.paysKillReward(p, victim)) {
          kill.award(p, victim.location(), getConfig().sneakKillXP);
        }
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
    super.unregister();
  }

  private void pulseSneakingXp(PlayerRef player, long elapsedMillis) {
    long cadenceMillis = Math.max(50L, getInterval());
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
      sneak.awardSilent(player, getConfig().sneakXP * cadenceScale);
    });
  }


  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Challenge Sneak1k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSneak1kReward = 1750;
    @ConfigDoc(value = "Controls Challenge Sneak5k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSneak5kReward = 3500;
    @ConfigDoc(value = "Controls Challenge Sneak20k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSneak20kReward = 8750;
    @ConfigDoc(value = "Controls Sneak XP for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sneakXP = 0.4;
    @ConfigDoc(value = "Controls XP multiplier for dealing damage while sneaking.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sneakCombatXPMultiplier = 3.0;
    @ConfigDoc(value = "Cooldown in milliseconds between XP awards for sneaking combat damage.", impact = "Higher values reduce repeated combat XP frequency; lower values reward hits more often.")
    long sneakCombatXpCooldown = 1250;
    @ConfigDoc(value = "Controls XP awarded for killing while sneaking.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sneakKillXP = 60;
    @ConfigDoc(value = "Controls Challenge Stealth Dmg 500 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeStealthDmg500Reward = 1500;
    @ConfigDoc(value = "Controls Challenge Stealth Dmg 5k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeStealthDmg5kReward = 5000;
    @ConfigDoc(value = "Controls Challenge Stealth Kills 10 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeStealthKills10Reward = 1000;
    @ConfigDoc(value = "Controls Challenge Stealth Kills 100 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeStealthKills100Reward = 5000;
    @ConfigDoc(value = "Controls Challenge Stealth Arrows 50 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeStealthArrows50Reward = 1250;
    @ConfigDoc(value = "Controls Challenge Stealth Arrows 500 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeStealthArrows500Reward = 5000;
    @ConfigDoc(value = "XP for the first hit on a mob that is not targeting you.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double unawareOpenerXp = 75;
    @ConfigDoc(value = "Extra XP for that first hit when you are sneaking and the mob is not looking at you.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double unseenOpenerBonusXp = 100;

    public Config() {
    }
  }
}
