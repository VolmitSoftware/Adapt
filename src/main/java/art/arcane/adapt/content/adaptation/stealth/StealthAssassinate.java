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
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Boss;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Objects;
import java.util.UUID;

public class StealthAssassinate extends SimpleAdaptation<StealthAssassinate.Config> {
  private static final Color ASSASSINATE_PURPLE = Color.fromRGB(0x2A0A3A);

  private final StealthCore stealth;
  private final Cooldowns cooldowns = cooldowns();

  public StealthAssassinate(StealthCore stealth) {
    super("stealth-assassinate");
    this.stealth = Objects.requireNonNull(stealth);
    registerConfiguration(Config.class);
    setIcon(Material.NETHERITE_SWORD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_stealth_assassinate_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.WITHER_ROSE)
            .key("challenge_stealth_assassinate_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
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

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getHealthCap(level), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldown(level), 1), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.MeleeContext combat = resolveMeleeContext(e);
    if (combat == null) {
      return;
    }

    Player attacker = combat.attacker();
    LivingEntity target = combat.target();
    if (target == attacker || target instanceof Player || target instanceof Boss
        || target.getType() == EntityType.WARDEN) {
      return;
    }

    if (!stealth.isUndetected(attacker, target)) {
      return;
    }

    double maxHealth = getMaxHealth(target);
    if (maxHealth <= 0 || maxHealth > getHealthCap(combat.level())) {
      return;
    }

    UUID id = attacker.getUniqueId();
    long cooldown = getCooldown(combat.level());
    if (!cooldowns.isReady(id, cooldown)) {
      return;
    }

    cooldowns.mark(id);
    double lethal = Math.max(e.getDamage(), (target.getHealth() * 100D) + 1000D);
    e.setDamage(lethal);
    xp(attacker, getConfig().xpOnExecution);
    addStat(attacker, "stealth.assassinate.executions", 1);

    Location center = target.getLocation().add(0, 1.0D, 0);
    fx(center, FxPriority.COMBAT)
        .burst(Particle.CRIT, 16, 0.35D)
        .particle(Particle.DAMAGE_INDICATOR, 6, 0, 0, 0, 0.25D, 0.05D)
        .burst(Particles.SMOKE, 8, 0.3D)
        .dustBurst(ASSASSINATE_PURPLE, 8, 0.4D, 1.2F)
        .chord(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1F, 0.6F, Sound.ITEM_TRIDENT_HIT, 0.5F, 0.7F, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.3F, 1.6F);
    timeline(target.getLocation()).duration(5).priority(FxPriority.COMBAT).cullRadius(28)
        .frame((fx, tick, progress) -> {
          fx.ring(Particle.SOUL, 1.4D - (1.1D * progress), 12, 0.2D);
          if (tick == 0) {
            fx.sound(Sound.ITEM_TRIDENT_RETURN, 0.5F, 0.5F);
          }
        }).start();
  }

  private double getMaxHealth(LivingEntity entity) {
    AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
    return attr == null ? entity.getHealth() : attr.getValue();
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
}
