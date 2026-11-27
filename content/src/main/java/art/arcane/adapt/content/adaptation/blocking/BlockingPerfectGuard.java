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

package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;

public class BlockingPerfectGuard extends SimpleAdaptation<BlockingPerfectGuard.Config> {
  public static final Id ID = Id.adapt("blocking/perfect_guard");
  private static final Id SLOWNESS = Id.minecraft("slowness");
  private static final Id WEAKNESS = Id.minecraft("weakness");
  private final BlockingParry parry = new BlockingParry(this, playerState());

  public BlockingPerfectGuard() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(BlockingShields.SHIELD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_blocking_perfect_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_blocking_perfect_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_blocking_perfect_100", "blocking.perfect-guard.hits-negated", 100, 600);
    registerMilestone("challenge_blocking_perfect_1k", "blocking.perfect-guard.hits-negated", 1000, 2500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BlockingPreferences.STAGGER);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getWindowMillis(level), 2), 1);
    statLore(v, Format.duration(getStaggerTicks(level) * 50D, 1), 2);
    statLore(v, Format.f(getStaggerAmplifier(level) + 1, 0), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    parry.raise(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef defender)) {
      return;
    }

    int level = getActiveLevel(defender);
    if (level <= 0 || !BlockingShields.held(this, defender)) {
      return;
    }

    long now = System.currentTimeMillis();
    if (!parry.timed(defender, getWindowMillis(level), now)) {
      return;
    }

    if (getStorageLong(defender, "perfectGuardNext", 0L) > now) {
      return;
    }

    LivingRef attacker = BlockingParry.attacker(e.damager());
    if (!BlockingParry.faces(defender, e.damager(), getConfig().minFacingAlignment)) {
      return;
    }

    e.setCancelled(true);
    parry.consume(defender);
    setStorage(defender, "perfectGuardNext", now + getConfig().cooldownMillis);
    HudStatus.cooldown(defender.uuid(), this, now, getConfig().cooldownMillis);
    xp(defender, getConfig().xpOnNegate);
    addStat(defender, "blocking.perfect-guard.hits-negated", 1);
    parryFlash(this, defender);

    if (!preferenceEnabled(defender, BlockingPreferences.STAGGER) || attacker == null || !canDamageTarget(defender, attacker)) {
      return;
    }

    LivingRef staggered = attacker;
    Loc origin = defender.location();
    int staggerTicks = getStaggerTicks(level);
    int staggerAmplifier = getStaggerAmplifier(level);
    double knockback = getConfig().staggerKnockback;
    AdaptApi.scheduler().runEntity(staggered, () -> applyStagger(staggered, origin, staggerTicks, staggerAmplifier, knockback));
  }

  private void applyStagger(LivingRef attacker, Loc origin, int staggerTicks, int staggerAmplifier, double knockback) {
    if (!attacker.isValid()) {
      return;
    }

    stagger(attacker, origin, staggerTicks, staggerAmplifier, knockback);

    fx(attacker.location().add(0, 1.0D, 0), FxPriority.COMBAT)
        .burst(Particles.CRIT, 6, 0.3D)
        .chord(Sounds.ITEM_SHIELD_BLOCK, 1.0F, 0.7F, Sounds.BLOCK_ANVIL_LAND, 0.35F, 1.5F);
  }

  /** Slows and weakens the target for {@code ticks} and pushes it away from {@code origin}. */
  public static void stagger(LivingRef target, Loc origin, int ticks, int slownessAmplifier, double knockback) {
    target.addEffect(EffectInstance.of(SLOWNESS, ticks, slownessAmplifier).withParticles(false));
    target.addEffect(EffectInstance.of(WEAKNESS, ticks, 0).withParticles(false));
    push(target, origin, knockback);
  }

  /** Pushes the target horizontally away from {@code origin}, or along its facing when they overlap. */
  public static void push(LivingRef target, Loc origin, double knockback) {
    Vec3 push = target.location().position().subtract(origin.position()).withY(0);
    if (push.lengthSquared() < 1.0E-6) {
      push = origin.direction().withY(0);
    }
    if (push.lengthSquared() < 1.0E-6) {
      push = new Vec3(1, 0, 0);
    }
    push = push.normalize();
    target.setVelocity(target.velocity().multiply(0.3D).add(push.multiply(knockback).withY(0.2D)));
  }

  public static void parryFlash(Adaptation<?> owner, PlayerRef defender) {
    Vec3 look = defender.eyeLocation().direction();
    Loc shield = defender.eyeLocation().add(look.x() * 0.6D, look.y() * 0.6D, look.z() * 0.6D);
    Fx.now(owner, shield, FxPriority.COMBAT)
        .burst(Particles.END_ROD, 8, 0.25D)
        .ring(Particles.CRIT, 0.6D, 8, 0)
        .chord(Sounds.ITEM_SHIELD_BLOCK, 1.0F, 1.6F, Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7F, 1.4F, Sounds.ITEM_TRIDENT_RETURN, 0.4F, 1.8F);
  }

  private long getWindowMillis(int level) {
    return Math.round(getConfig().windowMillisBase + (getLevelPercent(level) * getConfig().windowMillisFactor));
  }

  private int getStaggerTicks(int level) {
    return Math.max(10, (int) Math.round(getConfig().staggerTicksBase + (getLevelPercent(level) * getConfig().staggerTicksFactor)));
  }

  private int getStaggerAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().staggerAmplifierBase + (getLevelPercent(level) * getConfig().staggerAmplifierFactor)));
  }


  @ConfigDescription("Raise your shield in the instant before a hit lands to negate it and stagger the attacker.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Window Millis Base for the Blocking Perfect Guard adaptation.", impact = "Higher values widen the perfect-guard timing window at every level.")
    double windowMillisBase = 120;
    @ConfigDoc(value = "Controls Window Millis Factor for the Blocking Perfect Guard adaptation.", impact = "Higher values widen the perfect-guard timing window as you level up.")
    double windowMillisFactor = 240;
    @ConfigDoc(value = "Controls Stagger Ticks Base for the Blocking Perfect Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double staggerTicksBase = 20;
    @ConfigDoc(value = "Controls Stagger Ticks Factor for the Blocking Perfect Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double staggerTicksFactor = 40;
    @ConfigDoc(value = "Controls Stagger Amplifier Base for the Blocking Perfect Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double staggerAmplifierBase = 1;
    @ConfigDoc(value = "Controls Stagger Amplifier Factor for the Blocking Perfect Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double staggerAmplifierFactor = 2;
    @ConfigDoc(value = "Controls Stagger Knockback for the Blocking Perfect Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double staggerKnockback = 0.55;
    @ConfigDoc(value = "Controls Min Facing Alignment for the Blocking Perfect Guard adaptation.", impact = "Higher values require the attacker to be more directly in front to be parried.")
    double minFacingAlignment = 0.15;
    @ConfigDoc(value = "Controls Cooldown Millis for the Blocking Perfect Guard adaptation.", impact = "Higher values space out how often a perfect guard can fire and bound abuse.")
    long cooldownMillis = 1500;
    @ConfigDoc(value = "Controls Xp On Negate for the Blocking Perfect Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnNegate = 14;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
