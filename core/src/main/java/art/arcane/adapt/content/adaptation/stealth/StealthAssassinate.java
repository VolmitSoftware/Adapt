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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.catalog.StealthMessages;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class StealthAssassinate extends SimpleAdaptation<StealthAssassinate.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", StealthMessages.STEALTHASSASSINATE_PREFERENCE_SNEAK, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<Targets> TARGETS = new PlayerPreference<>(Targets.class,
      new PlayerPreference.Definition<>("targets", StealthMessages.STEALTHASSASSINATE_PREFERENCE_TARGETS, Targets.ALL, List.of(
          new PlayerPreference.Choice<>(Targets.ALL, StealthMessages.STEALTHASSASSINATE_PREFERENCE_TARGETS_ALL, Id.minecraft("iron_sword"), 1),
          new PlayerPreference.Choice<>(Targets.HOSTILES, StealthMessages.STEALTHASSASSINATE_PREFERENCE_TARGETS_HOSTILES, Id.minecraft("zombie_head"), 1),
          new PlayerPreference.Choice<>(Targets.ANIMALS, StealthMessages.STEALTHASSASSINATE_PREFERENCE_TARGETS_ANIMALS, Id.minecraft("wheat"), 1))));

  private static final Rgb ASSASSINATE_PURPLE = Rgb.of(0x2A0A3A);
  private static final Id WARDEN = Id.minecraft("warden");

  private final StealthCore stealth;
  private final Cooldowns cooldowns = cooldowns();

  public StealthAssassinate(StealthCore stealth) {
    super("stealth-assassinate");
    this.stealth = Objects.requireNonNull(stealth);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_sword"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_stealth_assassinate_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("wither_rose"))
            .key("challenge_stealth_assassinate_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_assassinate_50", "stealth.assassinate.executions", 50, 500);
    registerMilestone("challenge_stealth_assassinate_500", "stealth.assassinate.executions", 500, 2000);
  }

  static double computeHealthCap(double base, double factor, double percent) {
    return base + (percent * factor);
  }

  static long computeCooldown(double base, double factor, double percent) {
    return Math.max(8000L, Math.round(base - (percent * factor)));
  }

  static double executionDamage(double currentHealth) {
    return Math.max(0D, currentHealth);
  }

  static void applyExecutionDamage(EntityDamageByEntityEvent event, double currentHealth) {
    event.setDamage(executionDamage(currentHealth));
    event.setIgnoreReductions(true);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SNEAK, TARGETS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getHealthCap(level), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldown(level), 1), 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.MeleeContext combat = resolveMeleeContext(e);
    if (combat == null) {
      return;
    }

    PlayerRef attacker = combat.attacker();
    LivingRef target = combat.target();
    Targets selection = preference(attacker, TARGETS);
    if ((preferenceEnabled(attacker, SNEAK) && !attacker.isSneaking())
        || (selection == Targets.HOSTILES && !target.has(EntityTrait.MONSTER))
        || (selection == Targets.ANIMALS && !target.has(EntityTrait.ANIMAL))) {
      return;
    }
    if (target.uuid().equals(attacker.uuid()) || target instanceof PlayerRef || target.has(EntityTrait.BOSS)
        || target.type().equals(WARDEN)) {
      return;
    }

    if (!stealth.isUndetected(attacker, target)) {
      return;
    }

    double maxHealth = getMaxHealth(target);
    if (maxHealth <= 0 || maxHealth > getHealthCap(combat.level())) {
      return;
    }

    UUID id = attacker.uuid();
    long cooldown = getCooldown(combat.level());
    if (!cooldowns.isReady(id, cooldown)) {
      return;
    }

    cooldowns.mark(id);
    applyExecutionDamage(e, target.health());
    xp(attacker, getConfig().xpOnExecution);
    addStat(attacker, "stealth.assassinate.executions", 1);

    Loc center = target.location().add(0, 1.0D, 0);
    fx(center, FxPriority.COMBAT)
        .burst(Particles.CRIT, 16, 0.35D)
        .particle(Particles.DAMAGE_INDICATOR, 6, 0, 0, 0, 0.25D, 0.05D)
        .burst(Particles.SMOKE, 8, 0.3D)
        .dustBurst(ASSASSINATE_PURPLE, 8, 0.4D, 1.2F)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 1F, 0.6F, Sounds.ITEM_TRIDENT_HIT, 0.5F, 0.7F, Sounds.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.3F, 1.6F);
    timeline(target.location()).duration(5).priority(FxPriority.COMBAT).cullRadius(28)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.SOUL, 1.4D - (1.1D * progress), 12, 0.2D);
          if (tick == 0) {
            fx.sound(Sounds.ITEM_TRIDENT_RETURN, 0.5F, 0.5F);
          }
        }).start();
  }

  private double getMaxHealth(LivingRef entity) {
    return entity.hasAttribute(Attributes.MAX_HEALTH) ? entity.attributeValue(Attributes.MAX_HEALTH) : entity.health();
  }

  private double getHealthCap(int level) {
    return computeHealthCap(getConfig().healthCapBase, getConfig().healthCapFactor, getLevelPercent(level));
  }

  private long getCooldown(int level) {
    return computeCooldown(getConfig().cooldownBase, getConfig().cooldownFactor, getLevelPercent(level));
  }

  @ConfigDescription("While Stealth reports you undetected, a strike instantly executes non-boss mobs below a max-health cap.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base maximum health a mob can have to be executable.", impact = "Higher values let you execute tougher mobs.")
    double healthCapBase = 22.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra executable health granted across levels.", impact = "Higher values raise the executable health cap more per level.")
    double healthCapFactor = 38.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base cooldown between executions, in milliseconds.", impact = "Higher values mean longer waits between assassinations.")
    double cooldownBase = 40000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How much the cooldown is reduced by leveling, in milliseconds.", impact = "Higher values shorten the cooldown more at higher levels.")
    double cooldownFactor = 20000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Experience granted for a successful execution.", impact = "Higher values level the adaptation faster.")
    double xpOnExecution = 45;

    public Config() {
      baseCost = 6;
      costFactor = 0.85;
      maxLevel = 4;
      initialCost = 6;
    }
  }

  public enum Targets { ALL, HOSTILES, ANIMALS }
}
