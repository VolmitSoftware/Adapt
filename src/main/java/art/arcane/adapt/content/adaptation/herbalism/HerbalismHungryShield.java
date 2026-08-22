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

package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

public class HerbalismHungryShield extends SimpleAdaptation<HerbalismHungryShield.Config> {
  private static final int MIN_FOOD = 6;
  private final Cooldowns shieldBreakCooldown = cooldowns();
  private final Cooldowns absorbFxCooldown = cooldowns();
  private final Cooldowns dotChargeCooldown = cooldowns();

  public HerbalismHungryShield() {
    super("herbalism-hungry-shield");
    registerConfiguration(Config.class);
    setIcon(Material.APPLE);
    setInterval(875);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BREAD)
        .key("challenge_herbalism_shield_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.GOLDEN_APPLE)
            .key("challenge_herbalism_shield_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_herbalism_shield_500", "herbalism.hungry-shield.damage-absorbed", 500, 400);
    registerMilestone("challenge_herbalism_shield_5k", "herbalism.hungry-shield.damage-absorbed", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    Config cfg = getConfig();
    String absorb = Form.pc(getEffectiveness(getLevelPercent(level)), 0);
    statLore(v, absorb, 1);
    statLore(v, covers(EntityDamageEvent.DamageCause.CONTACT, level, cfg) ? absorb : "-", 2);
    statLore(v, covers(EntityDamageEvent.DamageCause.ENTITY_ATTACK, level, cfg) ? absorb : "-", 3);
    statLore(v, covers(EntityDamageEvent.DamageCause.FIRE, level, cfg) ? absorb : "-", 4);
    statLore(v, covers(EntityDamageEvent.DamageCause.PROJECTILE, level, cfg) ? absorb : "-", 5);
    statLore(v, covers(EntityDamageEvent.DamageCause.MAGIC, level, cfg) ? absorb : "-", 6);
  }

  private double getEffectiveness(double factor) {
    return Math.min(getConfig().maxEffectiveness, factor * factor + getConfig().effectivenessBase);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> absorb(p, e));
  }

  private void absorb(Player p, EntityDamageEvent e) {
    int level = getActiveLevel(p);
    EntityDamageEvent.DamageCause cause = e.getCause();
    if (level <= 0 || !covers(cause, level, getConfig())) {
      return;
    }

    boolean overTime = isDamageOverTime(cause);
    if (overTime && !dotChargeCooldown.isReady(p.getUniqueId(), getConfig().dotChargeIntervalMs)) {
      return;
    }

    double available = p.getFoodLevel() + p.getSaturation();
    double absorbed = absorbedDamage(e.getDamage(), getEffectiveness(getLevelPercent(level)), available, MIN_FOOD);
    if (absorbed <= 0) {
      shieldBreak(p, e.getDamage());
      return;
    }

    AdaptPlayer adaptPlayer = getPlayer(p);
    if (!payHungerCost(p, "hunger", (int) Math.ceil(absorbed), () -> {
      adaptPlayer.applyFoodCharge(absorbed);
      return true;
    })) {
      shieldBreak(p, e.getDamage());
      return;
    }

    if (overTime) {
      dotChargeCooldown.mark(p.getUniqueId());
    }

    e.setDamage(Math.max(0D, e.getDamage() - absorbed));
    addStat(p, "herbalism.hungry-shield.damage-absorbed", (int) Math.ceil(absorbed));
    xp(p, absorbed);

    if (absorbFxCooldown.isReady(p.getUniqueId(), 500L)) {
      absorbFxCooldown.mark(p.getUniqueId());
      int intensity = (int) Math.max(3, Math.min(12, Math.ceil(absorbed)));
      double ringRadius = Math.min(1.6D, 0.7D + (absorbed * 0.08D));
      fx(p.getLocation().add(0, 1, 0), FxPriority.COMBAT)
          .dustRing(Color.fromRGB(210, 140, 40), ringRadius, 16, 1.0F)
          .burst(Particles.CRIT_MAGIC, intensity, 0.5D)
          .chord(Sound.ITEM_SHIELD_BLOCK, 0.5F, 0.9F, Sound.BLOCK_GRASS_BREAK, 0.4F, 0.7F);
    }
  }

  private void shieldBreak(Player p, double damage) {
    if (damage <= 2 || !shieldBreakCooldown.isReady(p.getUniqueId(), 1500L)) {
      return;
    }

    shieldBreakCooldown.mark(p.getUniqueId());
    fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.3D)
        .chord(Sound.BLOCK_NOTE_BLOCK_BASS, 0.4F, 0.6F, Sound.ITEM_SHIELD_BLOCK, 0.3F, 0.5F);
  }

  static double absorbedDamage(double damage, double effectiveness, double availableFood, int minFood) {
    if (!Double.isFinite(damage)
        || !Double.isFinite(effectiveness)
        || !Double.isFinite(availableFood)
        || damage <= 0D
        || effectiveness <= 0D) {
      return 0D;
    }

    return Math.min(damage * effectiveness, Math.max(0D, availableFood - minFood));
  }

  static boolean covers(EntityDamageEvent.DamageCause cause, int level, Config cfg) {
    if (cause == null || cfg == null || level <= 0) {
      return false;
    }

    return switch (cause) {
      case CONTACT, CRAMMING, DROWNING, SUFFOCATION, FLY_INTO_WALL, HOT_FLOOR, FREEZE -> level >= cfg.basicsUnlockLevel;
      case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, THORNS -> level >= cfg.meleeUnlockLevel;
      case FIRE, FIRE_TICK, LAVA, CAMPFIRE -> level >= cfg.fireUnlockLevel;
      case PROJECTILE, BLOCK_EXPLOSION, ENTITY_EXPLOSION, FALLING_BLOCK, LIGHTNING -> level >= cfg.burstUnlockLevel;
      case MAGIC, POISON, WITHER, DRAGON_BREATH, SONIC_BOOM -> level >= cfg.magicUnlockLevel;
      default -> false;
    };
  }

  static boolean isDamageOverTime(EntityDamageEvent.DamageCause cause) {
    if (cause == null) {
      return false;
    }

    return switch (cause) {
      case FIRE, FIRE_TICK, LAVA, CAMPFIRE, HOT_FLOOR, POISON, WITHER, DROWNING, FREEZE -> true;
      default -> false;
    };
  }

  @ConfigDescription("Take damage to your hunger before your health, covering more damage types as the adaptation levels up.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Effectiveness Base for the Herbalism Hungry Shield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double effectivenessBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Effectiveness for the Herbalism Hungry Shield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxEffectiveness = 0.95;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Level required before contact, cramming, drowning, suffocation, wall, magma and freeze damage are shielded.", impact = "Higher values delay when the basic damage types become shielded.")
    int basicsUnlockLevel = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Level required before melee, sweep and thorns damage is shielded.", impact = "Higher values delay when melee damage becomes shielded.")
    int meleeUnlockLevel = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Level required before fire, lava and campfire damage is shielded.", impact = "Higher values delay when burning damage becomes shielded.")
    int fireUnlockLevel = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Level required before projectile, explosion, falling block and lightning damage is shielded.", impact = "Higher values delay when burst damage becomes shielded.")
    int burstUnlockLevel = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Level required before magic, poison, wither, dragon breath and sonic boom damage is shielded.", impact = "Higher values delay when magical damage becomes shielded.")
    int magicUnlockLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between hunger charges for damage-over-time sources such as fire, lava, poison and drowning.", impact = "Higher values let more damage-over-time ticks through unshielded but drain hunger far slower.")
    long dotChargeIntervalMs = 1000;

    public Config() {
      baseCost = 7;
      costFactor = 0.78;
      initialCost = 10;
    }
  }
}
