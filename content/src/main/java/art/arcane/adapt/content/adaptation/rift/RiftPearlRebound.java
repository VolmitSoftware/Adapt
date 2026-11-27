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

package art.arcane.adapt.content.adaptation.rift;

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
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;

public class RiftPearlRebound extends SimpleAdaptation<RiftPearlRebound.Config> {
  public static final Id ID = Id.adapt("rift/pearl_rebound");
  public static final PlayerPreference<CommonPreferences.Toggle> REBOUND = CommonPreferences.toggle("rebound", Text.key("adapt.riftpearlrebound.preferences.preference_rebound"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> DAMAGE = CommonPreferences.toggle("damage-reduction", Text.key("adapt.riftpearlrebound.preferences.preference_damage"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", Text.key("adapt.riftpearlrebound.preferences.preference_sneak"), CommonPreferences.Toggle.OFF);

  private static final double HARD_MAX_DAMAGE_REDUCTION = 0.9D;
  public static final double HARD_MAX_AIM_BIAS = 0.9D;
  private static final double MIN_OUTWARD_REBOUND = 0.35D;
  public static final Id ENDER_PEARL = Id.minecraft("ender_pearl");

  private final RiftPearlBounce bounce = new RiftPearlBounce(this::getAimBias, this::getReboundSpeed,
      (PlayerRef p) -> {
        addStat(p, "rift.pearl-rebound.rebounds", 1);
        xp(p, getConfig().xpOnRebound, "rift:pearl-rebound:rebound");
      });

  public RiftPearlRebound() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("slime_ball"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_rebound_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("slime_ball"))
            .key("challenge_rift_rebound_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_rebound_100", "rift.pearl-rebound.rebounds", 100, 400);
    registerMilestone("challenge_rift_rebound_1k", "rift.pearl-rebound.rebounds", 1000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, REBOUND, DAMAGE, SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, TextFormat.GREEN, "+ ", Format.pc(getDamageReduction(level), 0), 1);
    statLore(v, TextFormat.GREEN, "+ ", Format.pc(getAimBias(level), 0), 2);
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef pearl = e.projectile();
    PlayerRef p = bounce.thrower(pearl);
    if (p == null) {
      return;
    }

    int level = getActiveLevel(p);
    if (level > 0 && preferenceEnabled(p, REBOUND) && (!preferenceEnabled(p, SNEAK) || p.isSneaking())) {
      bounce.tag(pearl, level);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(ProjectileHitEvent e) {
    bounce.hit(this, e);
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)
        || !ENDER_PEARL.equals(e.damageType())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || !preferenceEnabled(p, DAMAGE)) {
      return;
    }

    e.setDamage(calculateReducedDamage(e.damage(), getDamageReduction(level)));
  }

  /**
   * Only plain pearls bounce. The launch tag is written before another adaptation can claim the
   * projectile, so the claim is re-checked on impact: a pearl carrying a foreign key belongs to a
   * feature like Ender Taglock and stealing it would drop that feature's payload and leave the
   * replacement pearl teleporting the thrower.
   */
  static boolean shouldRebound(Integer storedLevel, boolean alreadyRebounded, boolean unclaimed) {
    return storedLevel != null && storedLevel > 0 && !alreadyRebounded && unclaimed;
  }

  /**
   * Blends the reflected bounce toward the crosshair, but never back into the struck face: a player still
   * aiming at the wall would otherwise send the rebound straight into it and teleport at the first surface.
   */
  static Vec3 reboundDirection(Vec3 reflected, Vec3 aim, Vec3 normal, double bias) {
    Vec3 direction = reflected.multiply(1D - bias).add(aim.multiply(bias));
    if (direction.lengthSquared() <= 1.0E-6D) {
      return normal.normalize();
    }
    direction = direction.normalize();
    double outward = direction.dot(normal);
    if (outward < MIN_OUTWARD_REBOUND) {
      direction = direction.add(normal.multiply(MIN_OUTWARD_REBOUND - outward));
    }
    return direction.lengthSquared() <= 1.0E-6D ? normal.normalize() : direction.normalize();
  }

  private double getDamageReduction(int level) {
    double value = getConfig().damageReductionBase + (Math.max(0, level - 1) * getConfig().damageReductionPerLevel);
    return Math.max(0D, Math.min(HARD_MAX_DAMAGE_REDUCTION, value));
  }

  private double getAimBias(int level) {
    double value = getConfig().aimBiasBase + (Math.max(0, level - 1) * getConfig().aimBiasPerLevel);
    return Math.max(0D, Math.min(HARD_MAX_AIM_BIAS, value));
  }

  private double getReboundSpeed() {
    return Math.max(0.4D, getConfig().reboundSpeed);
  }

  static double calculateReducedDamage(double damage, double reduction) {
    if (!Double.isFinite(damage) || damage <= 0D) {
      return 0D;
    }
    double safeReduction = Double.isFinite(reduction) ? Math.max(0D, Math.min(HARD_MAX_DAMAGE_REDUCTION, reduction)) : 0D;
    return damage * (1D - safeReduction);
  }

  public static void bounceFx(Adaptation<?> owner, Loc spawnAt) {
    Fx.now(owner, spawnAt, FxPriority.TRAIL)
        .burst(Particles.SMOKE, 4, 0.15)
        .particle(Particles.REVERSE_PORTAL, 6, 0, 0.2, 0, 0.25, 0.03)
        .chord(Sounds.BLOCK_SLIME_BLOCK_HIT, 0.7f, 1.4f, Sounds.ENTITY_ENDER_EYE_LAUNCH, 0.4f, 1.6f);
  }

  @ConfigDescription("Plain thrown pearls bounce once off the first surface toward your crosshair, and their ender pearl landing damage is reduced.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Fraction of ender pearl teleport damage removed at level 1.", impact = "Higher values soften pearl landings more; capped at 90 percent.")
    double damageReductionBase = 0.3;
    @ConfigDoc(value = "Extra pearl damage reduction fraction per adaptation level beyond the first.", impact = "Higher values make leveling reduce pearl damage faster.")
    double damageReductionPerLevel = 0.15;
    @ConfigDoc(value = "Fraction the rebounded pearl steers toward your look direction at level 1.", impact = "Higher values aim the bounce more at your crosshair and less like a pure reflection; capped at 90 percent.")
    double aimBiasBase = 0.3;
    @ConfigDoc(value = "Extra aim steering fraction per adaptation level beyond the first.", impact = "Higher values make leveling aim the bounce at you more precisely.")
    double aimBiasPerLevel = 0.15;
    @ConfigDoc(value = "Launch speed of the rebounded pearl.", impact = "Higher values throw the bounced pearl further and faster.")
    double reboundSpeed = 1.5;
    @ConfigDoc(value = "Skill XP granted each time a pearl rebounds.", impact = "Higher values grant more skill XP per rebound.")
    double xpOnRebound = 6;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 4;
      initialCost = 1;
    }
  }
}
