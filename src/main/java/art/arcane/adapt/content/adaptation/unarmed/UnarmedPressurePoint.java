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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
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
  public UnarmedPressurePoint() {
    super("unarmed-pressure-point");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("unarmed.pressure_point.description"));
    setDisplayName(Localizer.dLocalize("unarmed.pressure_point.name"));
    setIcon(Material.TRIPWIRE_HOOK);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(4733);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_pressure_500")
        .title(Localizer.dLocalize("advancement.challenge_unarmed_pressure_500.title"))
        .description(Localizer.dLocalize("advancement.challenge_unarmed_pressure_500.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_pressure_5k")
            .title(Localizer.dLocalize("advancement.challenge_unarmed_pressure_5k.title"))
            .description(Localizer.dLocalize("advancement.challenge_unarmed_pressure_5k.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_pressure_500", "unarmed.pressure-point.pressure-strikes", 500, 400);
    registerMilestone("challenge_unarmed_pressure_5k", "unarmed.pressure-point.pressure-strikes", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + (getMaxSlownessAmplifier(level) + 1) + C.GRAY + " " + Localizer.dLocalize("unarmed.pressure_point.lore1"));
    if (isWeaknessUnlocked(level)) {
      v.addLore(C.GREEN + "+ " + (getConfig().maxWeaknessAmplifier + 1) + C.GRAY + " " + Localizer.dLocalize("unarmed.pressure_point.lore2"));
    } else {
      v.addLore(C.GRAY + Localizer.dLocalize("unarmed.pressure_point.lore3"));
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
    applyStack(victim, PotionEffectType.SLOWNESS, getMaxSlownessAmplifier(level), getConfig().slownessDurationTicks);
    if (isWeaknessUnlocked(level)) {
      applyStack(victim, PotionEffectType.WEAKNESS, getConfig().maxWeaknessAmplifier, getConfig().weaknessDurationTicks);
    }

    SoundPlayer.of(victim.getWorld()).play(victim.getLocation(), Sound.BLOCK_POINTED_DRIPSTONE_LAND, 0.6f, 1.6f);
    if (areParticlesEnabled()) {
      victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.2, 0), 5, 0.15, 0.2, 0.15, 0.05);
    }
    xp(p, getConfig().xpPerStrike);
    getPlayer(p).getData().addStat("unarmed.pressure-point.pressure-strikes", 1);
  }

  private void applyStack(LivingEntity victim, PotionEffectType type, int maxAmplifier, int durationTicks) {
    PotionEffect existing = victim.getPotionEffect(type);
    int amplifier = existing == null ? 0 : Math.min(maxAmplifier, existing.getAmplifier() + 1);
    victim.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, false, true, true), true);
  }

  private int getMaxSlownessAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().maxSlownessAmplifierBase + (getLevelPercent(level) * getConfig().maxSlownessAmplifierFactor)));
  }

  private boolean isWeaknessUnlocked(int level) {
    return getLevelPercent(level) >= getConfig().weaknessUnlockPercent;
  }

  @Override
  public void onTick() {

  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Bare-hand hits apply stacking slowness, with weakness at higher levels.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.5;
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
  }
}
