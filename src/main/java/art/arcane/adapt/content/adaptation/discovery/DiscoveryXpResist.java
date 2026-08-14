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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
import art.arcane.adapt.localization.catalog.GuiMessages;

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
  private static final long FAIL_FX_THROTTLE_MILLIS = 3000L;
  private final Cooldowns cooldowns = cooldowns();
  private final Cooldowns failFxThrottle = cooldowns();

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
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(DiscoveryMessages.RESIST_LORE0));
    statLore(v, Form.pc(getEffectiveness(getLevelPercent(level)), 0), 1);
    statLore(v, getXpTaken(level), 2);
  }

  private double getEffectiveness(double factor) {
    return Math.min(getConfig().maxEffectiveness, factor * factor + getConfig().effectivenessBase);
  }

  private int getXpTaken(double level) {
    double d = (getConfig().levelCostAdd * getConfig().amplifier) - (level * getConfig().levelDrain);
    return Math.max(1, (int) Math.round(d));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
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

    UUID id = p.getUniqueId();
    if (cooldowns.isReady(id, COOLDOWN_MILLIS)) {
      int xpCost = getXpTaken(level);
      double effectiveness = getEffectiveness(getLevelPercent(level));
      double originalDamage = e.getDamage();
      ExperienceLevelCharge defaultCharge = new ExperienceLevelCharge(p, xpCost);
      if (!payExperienceCost(p, "experience-levels", xpCost, defaultCharge::take)) {
        playCostFailure(p);
        return;
      }
      e.setDamage(Math.max(0D, e.getDamage() * (1D - effectiveness)));
      xp(p, 5);
      cooldowns.mark(id);
      addStat(p, "discovery.xp-resist.saves", 1);
      if (defaultCharge.wasTaken()) {
        Adapt.actionbar(p, C.YELLOW + "-" + xpCost + " XP " + AdaptLanguage.text(GuiMessages.LEVELS));
      }

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
    } else if (failFxThrottle.isReady(id, FAIL_FX_THROTTLE_MILLIS)) {
      failFxThrottle.mark(id);
      fx(p.getLocation(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.5F);
    }
  }

  private boolean isCriticalHealthDamage(Player p, EntityDamageEvent e) {
    return isCriticalHealthDamage(p.getHealth(), e.getFinalDamage(), getConfig().triggerHealthThreshold);
  }

  static boolean isCriticalHealthDamage(double health, double finalDamage, double configuredThreshold) {
    if (!Double.isFinite(health) || !Double.isFinite(finalDamage) || finalDamage <= 0D) {
      return false;
    }

    double threshold = Double.isFinite(configuredThreshold) ? Math.max(0D, configuredThreshold) : 0D;
    return health - finalDamage <= threshold;
  }

  static boolean spendLevels(Player player, int amount) {
    if (player == null || amount <= 0) {
      return false;
    }

    int currentLevel = player.getLevel();
    if (currentLevel < amount) {
      return false;
    }

    float progress = Math.max(0.0F, Math.min(1.0F, player.getExp()));
    player.giveExpLevels(-amount);
    player.sendExperienceChange(progress, currentLevel - amount);
    return true;
  }

  private void playCostFailure(Player p) {
    if (!failFxThrottle.isReady(p.getUniqueId(), FAIL_FX_THROTTLE_MILLIS)) {
      return;
    }

    failFxThrottle.mark(p.getUniqueId());
    fx(p.getLocation(), FxPriority.COMBAT)
        .dustBurst(Color.RED, 10, 0.8D, 1.0F)
        .chord(Sound.BLOCK_FUNGUS_BREAK, 0.6F, 0.6F, Sound.BLOCK_GLASS_BREAK, 0.5F, 0.8F);
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

  static final class ExperienceLevelCharge {
    private final Player player;
    private final int amount;
    private boolean attempted;
    private boolean taken;

    ExperienceLevelCharge(Player player, int amount) {
      this.player = player;
      this.amount = amount;
    }

    boolean take() {
      if (attempted) {
        return taken;
      }

      attempted = true;
      taken = spendLevels(player, amount);
      return taken;
    }

    boolean wasTaken() {
      return taken;
    }
  }
}
