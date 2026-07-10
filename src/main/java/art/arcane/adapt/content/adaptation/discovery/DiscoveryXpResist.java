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

package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.UUID;

public class DiscoveryXpResist extends SimpleAdaptation<DiscoveryXpResist.Config> {
  private static final long COOLDOWN_MILLIS = 15000L;
  private final Cooldowns cooldowns = cooldowns();

  public DiscoveryXpResist() {
    super("discovery-xp-resist");
    registerConfiguration(Config.class);
    setLocalizationKey("discovery.resist");
    setIcon(Material.TOTEM_OF_UNDYING);
    setInterval(5215);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TOTEM_OF_UNDYING)
        .key("challenge_discovery_xp_resist_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENCHANTED_GOLDEN_APPLE)
            .key("challenge_discovery_xp_resist_250")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TOTEM_OF_UNDYING)
        .key("challenge_discovery_xp_resist_clutch")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_discovery_xp_resist_25", "discovery.xp-resist.saves", 25, 500);
    registerMilestone("challenge_discovery_xp_resist_250", "discovery.xp-resist.saves", 250, 2000);
  }


  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("discovery.resist.lore0"));
    v.addLore(C.GREEN + "+ " + Form.pc(getEffectiveness(getLevelPercent(level)), 0) + C.GRAY + Localizer.dLocalize("discovery.resist.lore1"));
    v.addLore(C.GREEN + "+ " + getXpTaken(level) + " " + C.GRAY + Localizer.dLocalize("discovery.resist.lore2"));
  }

  private double getEffectiveness(double factor) {
    return Math.min(getConfig().maxEffectiveness, factor * factor + getConfig().effectivenessBase);
  }

  private int getXpTaken(double level) {
    double d = (getConfig().levelCostAdd * getConfig().amplifier) - (level * getConfig().levelDrain);
    return Math.max(1, (int) Math.round(d));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!isCriticalHealthDamage(p, e)) {
      return;
    }

    int xpCost = getXpTaken(level);
    if (p.getLevel() < xpCost) {
      fx(p.getLocation(), FxPriority.COMBAT)
          .dustBurst(Color.RED, 10, 0.8D, 1.0F)
          .chord(Sound.BLOCK_FUNGUS_BREAK, 0.6F, 0.6F, Sound.BLOCK_GLASS_BREAK, 0.5F, 0.8F);
      return;
    }

    UUID id = p.getUniqueId();
    if (cooldowns.isReady(id, COOLDOWN_MILLIS)) {
      double effectiveness = getEffectiveness(getLevelPercent(level));
      double originalDamage = e.getDamage();
      e.setDamage(Math.max(0D, e.getDamage() * (1D - effectiveness)));
      xp(p, 5);
      cooldowns.mark(id);
      p.setLevel(p.getLevel() - xpCost);
      addStat(p, "discovery.xp-resist.saves", 1);

      double startRadius = 0.8D + (getLevelPercent(level) * 1.6D);
      timeline(p)
          .duration(12)
          .priority(FxPriority.COMBAT)
          .cullRadius(24)
          .frame((f, tick, progress) -> {
            double radius = startRadius * (1.0D - progress);
            f.ring(Particle.ELECTRIC_SPARK, radius, 8, 0.1D);
            if ((tick & 1) == 0) {
              f.dustRing(Color.LIME, radius, 8, 1.1F);
            }
            if (tick == 0) {
              f.chord(Sound.ENTITY_IRON_GOLEM_REPAIR, 0.8F, 0.8F, Sound.ITEM_TOTEM_USE, 0.5F, 1.2F);
            }
            if ((tick & 3) == 0) {
              f.sound(Sound.BLOCK_BEACON_ACTIVATE, 0.3F, (float) (1.0D + (progress * 0.6D)));
            }
          })
          .onComplete(() -> fx(p.getLocation(), FxPriority.COMBAT).particle(Particles.TOTEM, 16, 0, 0, 0, 0.2, 0.1))
          .start();

      if (originalDamage >= 30.0 && grantOnce(p, "challenge_discovery_xp_resist_clutch")) {
        fx(p.getLocation(), FxPriority.GAMEPLAY)
            .dome(Particles.END_ROD, 2.0D, 24)
            .dustBurst(Color.fromRGB(255, 215, 0), 20, 1.2D, 1.4F)
            .sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5F, 1.0F);
      }
    } else {
      fx(p.getLocation(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.5F);
    }
  }

  private boolean isCriticalHealthDamage(Player p, EntityDamageEvent e) {
    double threshold = Math.max(0D, getConfig().triggerHealthThreshold);
    double absorption = Math.max(0D, p.getAbsorptionAmount());
    double rawDamage = Math.max(0D, e.getDamage());
    double finalDamage = Math.max(0D, e.getFinalDamage());
    double healthAfterRaw = p.getHealth() - Math.max(0D, rawDamage - absorption);
    double healthAfterFinal = p.getHealth() - Math.max(0D, finalDamage - absorption);
    double predictedHealth = Math.min(healthAfterRaw, healthAfterFinal);
    return predictedHealth <= 0D || predictedHealth <= threshold || p.getHealth() <= threshold;
  }


  @ConfigDescription("Consume experience to mitigate damage when a hit would drop you below 5 hearts.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Effectiveness Base for the Discovery Xp Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double effectivenessBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Effectiveness for the Discovery Xp Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxEffectiveness = 0.95;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Level Drain for the Discovery Xp Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int levelDrain = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Level Cost Add for the Discovery Xp Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int levelCostAdd = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Amplifier for the Discovery Xp Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double amplifier = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Trigger Health Threshold for the Discovery Xp Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double triggerHealthThreshold = 10.0;

    public Config() {
      baseCost = 5;
      costFactor = 0.8;
      initialCost = 3;
    }
  }
}
