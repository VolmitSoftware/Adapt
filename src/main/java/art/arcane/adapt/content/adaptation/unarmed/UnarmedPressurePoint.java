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

package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.UnarmedMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class UnarmedPressurePoint extends SimpleAdaptation<UnarmedPressurePoint.Config> {
  private static final Color PRESSURE_COLOR = Color.fromRGB(0x3A4B9F);

  public UnarmedPressurePoint() {
    super("unarmed-pressure-point");
    registerConfiguration(Config.class);
    setIcon(Material.TRIPWIRE_HOOK);
    setInterval(4733);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_pressure_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_pressure_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_pressure_500", "unarmed.pressure-point.pressure-strikes", 500, 400);
    registerMilestone("challenge_unarmed_pressure_5k", "unarmed.pressure-point.pressure-strikes", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getMaxSlownessAmplifier(level) + 1, 1);
    if (isWeaknessUnlocked(level)) {
      statLore(v, getConfig().maxWeaknessAmplifier + 1, 2);
    } else {
      v.addLore(C.GRAY + AdaptLanguage.text(UnarmedMessages.PRESSURE_POINT_LORE3));
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    Player p = attack.attacker();
    if (isTool(p.getInventory().getItemInMainHand()) || isTool(p.getInventory().getItemInOffHand())) {
      return;
    }

    if (!(attack.target() instanceof LivingEntity victim)) {
      return;
    }

    int level = attack.level();
    int slowAmplifier = applyStack(victim, PotionEffectType.SLOWNESS, getMaxSlownessAmplifier(level), getConfig().slownessDurationTicks);
    boolean weakened = false;
    if (isWeaknessUnlocked(level)) {
      applyStack(victim, PotionEffectType.WEAKNESS, getConfig().maxWeaknessAmplifier, getConfig().weaknessDurationTicks);
      weakened = true;
    }

    Location hit = victim.getLocation().add(0, 1.2D, 0);
    fx(hit, FxPriority.COMBAT)
        .particle(Particle.CRIT, 3, 0, 0, 0, 0.05D, 0.02D)
        .particle(Particle.WAX_ON, 1, 0, 0, 0, 0.02D, 0.0D)
        .chord(Sound.BLOCK_POINTED_DRIPSTONE_LAND, 0.6F, 1.6F, Sound.ENTITY_PLAYER_HURT, 0.2F, 1.8F);
    fx(victim.getLocation().add(0, 0.1D, 0), FxPriority.COMBAT)
        .dustBurst(PRESSURE_COLOR, 3 + Math.min(3, slowAmplifier), 0.25D, 0.9F);
    if (weakened) {
      fx(hit, FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 4, 0, 0, 0, 0.05D, 0.01D)
          .sound(Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.4F, 0.7F);
    }
    xp(p, getConfig().xpPerStrike);
    addStat(p, "unarmed.pressure-point.pressure-strikes", 1);
  }

  private int applyStack(LivingEntity victim, PotionEffectType type, int maxAmplifier, int durationTicks) {
    PotionEffect existing = victim.getPotionEffect(type);
    int amplifier = existing == null ? 0 : Math.min(maxAmplifier, existing.getAmplifier() + 1);
    victim.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, false, true, true), true);
    return amplifier;
  }

  private int getMaxSlownessAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().maxSlownessAmplifierBase + (getLevelPercent(level) * getConfig().maxSlownessAmplifierFactor)));
  }

  private boolean isWeaknessUnlocked(int level) {
    return getLevelPercent(level) >= getConfig().weaknessUnlockPercent;
  }


  @ConfigDescription("Bare-hand hits apply stacking slowness, with weakness at higher levels.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base maximum slowness amplifier at level 1.", impact = "Higher values allow stronger slowness stacking early.")
    double maxSlownessAmplifierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional maximum slowness amplifier granted at max level.", impact = "Higher values allow stronger slowness stacking as levels increase.")
    double maxSlownessAmplifierFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Slowness duration in ticks per pressure strike.", impact = "Higher values keep targets slowed for longer.")
    int slownessDurationTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Level percent required before weakness stacking unlocks.", impact = "Lower values unlock the weakness effect at earlier levels.")
    double weaknessUnlockPercent = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum weakness amplifier once unlocked.", impact = "Higher values allow stronger weakness stacking.")
    int maxWeaknessAmplifier = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Weakness duration in ticks per pressure strike.", impact = "Higher values keep targets weakened for longer.")
    int weaknessDurationTicks = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per pressure strike.", impact = "Higher values speed up unarmed skill progression from strikes.")
    double xpPerStrike = 3.1;

    public Config() {
      baseCost = 3;
      costFactor = 0.5;
      initialCost = 4;
    }
  }
}
