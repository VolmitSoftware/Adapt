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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class SwordsLungeStrike extends SimpleAdaptation<SwordsLungeStrike.Config> {
  private static final Rgb LUNGE = Rgb.of(0xFFF2B0);
  private final Cooldowns lungeCooldown = cooldowns();

  public SwordsLungeStrike() {
    super("sword-lunge-strike");
    needs(PlatformNeed.partialAttribute(Attributes.ENTITY_INTERACTION_RANGE, "the lunge gives no extra reach"));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_sword"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_swords_lunge_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_sword"))
            .key("challenge_swords_lunge_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_swords_lunge_250", "swords.lunge-strike.lunges", 250, 400);
    registerMilestone("challenge_swords_lunge_2500", "swords.lunge-strike.lunges", 2500, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SwordPreferences.AIRBORNE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getLungeForce(level), 2), 1);
    statLore(v, Form.f(getBonusReach(level), 1), 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, this::isSword);
    if (combat == null) {
      return;
    }

    PlayerRef p = combat.attacker();
    if (!p.isSprinting() || (preferenceEnabled(p, SwordPreferences.AIRBORNE) && p.isOnGround())) {
      return;
    }

    if (!lungeCooldown.isReady(p.uuid(), (long) getConfig().cooldownMillis)) {
      return;
    }

    lungeCooldown.mark(p.uuid());
    LivingRef target = combat.target();
    double surge = getSurge(combat.level());
    Vec3 forward = p.location().direction().withY(0);
    if (forward.lengthSquared() < 1.0E-6D) {
      forward = new Vec3(0, 0, 1);
    }
    Vec3 applied = forward.normalize().multiply(surge).withY(getConfig().verticalBoost);
    Tasks.runEntity(p, () -> {
      if (p.isOnline()) {
        p.setVelocity(p.velocity().add(applied));
      }
    });
    applyBonusReach(p, combat.level());

    Loc tip = p.eyeLocation().add(p.location().direction().multiply(1.4D));
    fx(tip, FxPriority.COMBAT)
        .particle(Particles.CRIT, 8, 0, 0, 0, 0.25D, 0.05D)
        .particle(Particles.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0)
        .dustBurst(LUNGE, 4, 0.3D, 1.0F)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 0.7F, 1.3F, Sounds.ITEM_TRIDENT_RIPTIDE_1, 0.4F, 1.5F);
    xp(p, target.location(), getConfig().xpPerLunge);
    addStat(p, "swords.lunge-strike.lunges", 1);
  }

  private double getSurge(int level) {
    double raw = getLungeForce(level) + (getBonusReach(level) * getConfig().reachVelocityFactor);
    return Math.min(getConfig().maxSurge, raw);
  }

  private double getLungeForce(int level) {
    return getConfig().forceBase + (getLevelPercent(level) * getConfig().forceFactor);
  }

  private double getBonusReach(int level) {
    return getConfig().reachBase + (getLevelPercent(level) * getConfig().reachFactor);
  }

  private void applyBonusReach(PlayerRef p, int level) {
    AdaptAttributeService.get().applyTimed(
        p,
        getName(),
        "reach",
        Attributes.ENTITY_INTERACTION_RANGE,
        getBonusReach(level),
        AttributeOperation.ADD_VALUE,
        reachWindowTicks(getConfig().reachWindowTicks));
  }

  static int reachWindowTicks(double configuredTicks) {
    return Math.max(5, (int) Math.round(configuredTicks));
  }

  @ConfigDescription("Sprint-attacking with a sword lunges you into the strike with extra reach.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Force Base for the Swords Lunge Strike adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double forceBase = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Force Factor for the Swords Lunge Strike adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double forceFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reach Base for the Swords Lunge Strike adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reachBase = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reach Factor for the Swords Lunge Strike adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reachFactor = 1.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls how much bonus reach translates into forward lunge velocity.", impact = "Higher values make the reach component carry you farther per strike.")
    double reachVelocityFactor = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls the small upward hop applied to the lunge.", impact = "Higher values add more lift so the lunge clears ground friction.")
    double verticalBoost = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How long the bonus entity-reach window lasts after a lunge, in ticks.", impact = "Higher values keep the extended attack range active longer per lunge.")
    double reachWindowTicks = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on total horizontal lunge velocity.", impact = "Lower values keep the lunge shorter and more controlled.")
    double maxSurge = 1.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum delay between lunges in milliseconds.", impact = "Higher values space out lunges; lower values allow faster repeat lunges.")
    double cooldownMillis = 350;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Lunge for the Swords Lunge Strike adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerLunge = 6;

    public Config() {
      baseCost = 4;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
