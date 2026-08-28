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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SwordsCrescentGuard extends SimpleAdaptation<SwordsCrescentGuard.Config> {
  private static final Color CRESCENT = Color.fromRGB(0xFFD24A);

  public SwordsCrescentGuard() {
    super("sword-crescent-guard");
    registerConfiguration(Config.class);
    setIcon(Material.GOLDEN_APPLE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_swords_crescent_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_SWORD)
            .key("challenge_swords_crescent_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_swords_crescent_200", "swords.crescent-guard.guarded-kills", 200, 400);
    registerMilestone("challenge_swords_crescent_2500", "swords.crescent-guard.guarded-kills", 2500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getAbsorptionHearts(level), 1), 1);
    statLore(v, Form.duration(getDurationTicks(level) * 50D, 1), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    Player p = e.getEntity().getKiller();
    if (p == null) {
      return;
    }
    J.runEntity(p, () -> applyGuardForKill(p));
  }

  private void applyGuardForKill(Player p) {
    if (!p.isOnline()) {
      return;
    }
    if (!isSword(p.getInventory().getItemInMainHand())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    int amplifier = getAbsorptionAmplifier(level);
    int duration = getDurationTicks(level);
    applyGuard(p, amplifier, duration);
    addStat(p, "swords.crescent-guard.guarded-kills", 1);
    xp(p, getConfig().xpPerGuard);
    fx(p.getLocation().add(0, 1, 0), FxPriority.COMBAT)
        .dustBurst(CRESCENT, 6, 0.35D, 1.0F)
        .particle(Particle.HEART, 2, 0.3D, 0.3D, 0.3D, 0.1D, 0)
        .chord(Sound.ITEM_TOTEM_USE, 0.35F, 1.6F, Sound.BLOCK_NOTE_BLOCK_PLING, 0.4F, 1.5F);
  }

  private void applyGuard(Player p, int amplifier, int duration) {
    if (!p.isOnline()) {
      return;
    }

    PotionEffect current = p.getPotionEffect(PotionEffectType.ABSORPTION);
    int useAmplifier = current == null ? amplifier : Math.max(current.getAmplifier(), amplifier);
    int useDuration = current == null || !current.isInfinite()
        ? Math.max(current == null ? 0 : current.getDuration(), duration)
        : PotionEffect.INFINITE_DURATION;
    p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, useDuration, useAmplifier, true, true, true), true);
    double grantedAbsorption = absorptionPoints(useAmplifier);
    IAttribute capacity = Version.get().getAttribute(p, Attributes.MAX_ABSORPTION);
    if (capacity != null) {
      grantedAbsorption = Math.min(grantedAbsorption, capacity.getValue());
    }
    p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), grantedAbsorption));
  }

  private double getAbsorptionHearts(int level) {
    return absorptionPoints(getAbsorptionAmplifier(level)) / 2D;
  }

  static double absorptionPoints(int amplifier) {
    return 4D * (Math.max(0, amplifier) + 1);
  }

  private int getAbsorptionAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().amplifierBase + (getLevelPercent(level) * getConfig().amplifierFactor)));
  }

  private int getDurationTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().durationTicksBase + (getLevelPercent(level) * getConfig().durationTicksFactor)));
  }


  @ConfigDescription("Killing blows with a sword grant brief absorption hearts.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Amplifier Base for the Swords Crescent Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double amplifierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Amplifier Factor for the Swords Crescent Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double amplifierFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Base for the Swords Crescent Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksBase = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Factor for the Swords Crescent Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksFactor = 180;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Guard for the Swords Crescent Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerGuard = 8;

    public Config() {
      baseCost = 5;
      costFactor = 0.66;
      maxLevel = 5;
      initialCost = 5;
    }
  }
}
