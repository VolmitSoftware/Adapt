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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BlockingMirrorBlock extends SimpleAdaptation<BlockingMirrorBlock.Config> {
  public static final Id ID = Id.adapt("blocking/mirror_block");
  private final BlockingMirror mirror = new BlockingMirror(this, "mirror-damage-factor", this::commitReflection);

  public BlockingMirrorBlock() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("light_weighted_pressure_plate"));
    setInterval(1200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_blocking_mirror_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_blocking_mirror_100", "blocking.mirror-block.projectiles-reflected", 100, 500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_blocking_mirror_3in5")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
  }

  @Override
  protected void onRuntimeActivated() {
    mirror.activate();
  }

  @Override
  public void unregister() {
    mirror.close();
    super.unregister();
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BlockingPreferences.PLAYERS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getReflectChance(level), 0), 1);
    statLore(v, Format.pc(getReflectedDamageFactor(level), 0), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getReflectCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof ProjectileRef projectile)) {
      return;
    }

    mirror.applyReflectedDamage(e, projectile);

    if (!(e.entity() instanceof PlayerRef defender) || !mirror.accepting()
        || !isMirrorReady(defender) || BlockingMirror.reflected(projectile)) {
      return;
    }

    if (projectile.shooter() instanceof PlayerRef && !preferenceEnabled(defender, BlockingPreferences.PLAYERS)) {
      return;
    }
    int level = getActiveLevel(defender);
    long now = System.currentTimeMillis();
    long next = getStorageLong(defender, "mirrorBlockNext", 0L);
    if (next > now) {
      onCooldownTell(defender);
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getReflectChance(level)) {
      return;
    }

    BlockingMirror.Shot shot = new BlockingMirror.Shot(getReflectVelocityFactor(level), getReflectedDamageFactor(level),
        getReflectCooldownMillis(level), getConfig().minReflectedVelocitySquared, getConfig().fallbackReflectedSpeed);
    if (mirror.reflect(defender, projectile, shot)) {
      e.setCancelled(true);
    }
  }

  public static void mirrorFlash(Adaptation<?> owner, PlayerRef defender) {
    Loc chest = defender.location().add(0, 1.0D, 0);
    Fx.now(owner, chest, FxPriority.COMBAT)
        .ring(Particles.END_ROD, 0.5D, 8, 0)
        .burst(Particles.CRIT, 10, 0.35D)
        .chord(Sounds.ITEM_SHIELD_BLOCK, 1.0F, 1.35F, Sounds.BLOCK_AMETHYST_BLOCK_HIT, 0.8F, 0.8F, Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5F, 1.5F);
  }

  public static void reflectTrail(Adaptation<?> owner, ProjectileRef projectile) {
    FxTimeline.follow(owner, projectile)
        .duration(40)
        .priority(FxPriority.TRAIL)
        .cullRadius(24.0D)
        .frame((fxE, tick, progress) -> fxE.particle(Particles.WAX_ON, 1, 0, 0, 0, 0, 0))
        .start();
  }

  private void onCooldownTell(PlayerRef defender) {
    Vec3 look = defender.eyeLocation().direction();
    Loc front = defender.eyeLocation().add(look.x() * 0.4D, look.y() * 0.4D, look.z() * 0.4D);
    fx(front, FxPriority.AMBIENT)
        .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05D, 0.01D);
  }

  private void commitReflection(PlayerRef defender, BlockingMirror.Reflection reflection) {
    long now = System.currentTimeMillis();
    setStorage(defender, "mirrorBlockNext", now + reflection.cooldownMillis());
    HudStatus.cooldown(defender.uuid(), this, now, reflection.cooldownMillis());
    mirrorFlash(this, defender);
    xp(defender, getConfig().xpOnReflect);
    addStat(defender, "blocking.mirror-block.projectiles-reflected", 1);

    long windowStart = getStorageLong(defender, "mirrorWindowStart", 0L);
    int windowCount = getStorageInt(defender, "mirrorWindowCount", 0);
    if (now - windowStart > 5000L) {
      windowStart = now;
      windowCount = 1;
    } else {
      windowCount++;
    }
    setStorage(defender, "mirrorWindowStart", windowStart);
    setStorage(defender, "mirrorWindowCount", windowCount);
    if (windowCount >= 3 && grantOnce(defender, "challenge_blocking_mirror_3in5")) {
      fx(defender.location().add(0, 1.0D, 0), FxPriority.TRANSITION)
          .particle(Particles.REVERSE_PORTAL, 12, 0, 0.3D, 0, 0.4D, 0.02D)
          .sound(Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F);
    }
  }

  private boolean isMirrorReady(PlayerRef p) {
    return hasActiveAdaptation(p) && p.isBlocking() && BlockingShields.held(this, p);
  }

  private double getReflectChance(int level) {
    return Math.min(getConfig().maxReflectChance, getConfig().reflectChanceBase + (getLevelPercent(level) * getConfig().reflectChanceFactor));
  }

  private double getReflectedDamageFactor(int level) {
    return Math.min(getConfig().maxReflectedDamageFactor,
        getConfig().reflectedDamageFactorBase + (getLevelPercent(level) * getConfig().reflectedDamageFactorIncrease));
  }

  private double getReflectVelocityFactor(int level) {
    return Math.min(getConfig().maxReflectVelocityFactor, getConfig().reflectVelocityFactorBase + (getLevelPercent(level) * getConfig().reflectVelocityFactor));
  }

  private long getReflectCooldownMillis(int level) {
    return Math.max(100L, Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  @ConfigDescription("Blocking with a shield can reflect incoming projectiles at reduced force and damage.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Reflect Chance Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectChanceBase = 0.1;
    @ConfigDoc(value = "Controls Reflect Chance Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectChanceFactor = 0.35;
    @ConfigDoc(value = "Controls Max Reflect Chance for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReflectChance = 0.7;
    @ConfigDoc(value = "Controls Reflected Damage Factor Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectedDamageFactorBase = 0.45;
    @ConfigDoc(value = "Controls Reflected Damage Factor Increase for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectedDamageFactorIncrease = 0.35;
    @ConfigDoc(value = "Controls Max Reflected Damage Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReflectedDamageFactor = 0.95;
    @ConfigDoc(value = "Controls Reflect Velocity Factor Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectVelocityFactorBase = 0.42;
    @ConfigDoc(value = "Controls Reflect Velocity Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectVelocityFactor = 0.45;
    @ConfigDoc(value = "Controls Max Reflect Velocity Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReflectVelocityFactor = 1.1;
    @ConfigDoc(value = "Controls Cooldown Millis Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 2000;
    @ConfigDoc(value = "Controls Cooldown Millis Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 1200;
    @ConfigDoc(value = "Controls Min Reflected Velocity Squared for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minReflectedVelocitySquared = 0.08;
    @ConfigDoc(value = "Controls Fallback Reflected Speed for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fallbackReflectedSpeed = 0.95;
    @ConfigDoc(value = "Controls Xp On Reflect for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnReflect = 8;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
