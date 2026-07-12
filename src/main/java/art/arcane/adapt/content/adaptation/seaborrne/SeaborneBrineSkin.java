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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;

public class SeaborneBrineSkin extends SimpleAdaptation<SeaborneBrineSkin.Config> {
  private static final int REGEN_DURATION_TICKS = 100;
  private static final int REGEN_REFRESH_THRESHOLD_TICKS = 40;

  private final Map<UUID, Long> wetUntil = playerState();
  private final Cooldowns wetFx = cooldowns();
  private final Cooldowns reductionFx = cooldowns();

  public SeaborneBrineSkin() {
    super("seaborne-brine-skin");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.brine_skin");
    setIcon(Material.KELP);
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.KELP)
        .key("challenge_seaborne_brine_72k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_seaborne_brine_72k", "seaborne.brine-skin.wet-ticks", 72000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getRegenAmplifier(level) + 1, 1);
    statLore(v, Form.pc(getDamageReduction(level), 0), 2);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player player = adaptPlayer.getPlayer();
      withPlayerThread(player, () -> {
        UUID id = player.getUniqueId();
        if (!player.isOnline()) {
          return;
        }

        int level = getActiveLevel(player);
        if (level <= 0) {
          wetUntil.remove(id);
          return;
        }

        if (!isWet(player)) {
          return;
        }

        wetUntil.put(id, System.currentTimeMillis() + getLingerMillis(level));
        applyRegen(player, level);
        addStat(player, "seaborne.brine-skin.wet-ticks", getInterval() / 50D);
        if (wetFx.isReady(id, 2000L)) {
          wetFx.mark(id);
          fx(player.getLocation().clone().add(0D, 1.0D, 0D), FxPriority.AMBIENT)
              .particle(Particle.SPLASH, 3, 0D, 0.4D, 0D, 0.25D, 0.02D)
              .particle(Particle.GLOW, 2, 0D, 0.5D, 0D, 0.2D, 0D);
        }
      });
    }
  }

  private void applyRegen(Player player, int level) {
    int amplifier = getRegenAmplifier(level);
    PotionEffect current = player.getPotionEffect(PotionEffectType.REGENERATION);
    if (current == null || current.getAmplifier() < amplifier || current.getDuration() <= REGEN_REFRESH_THRESHOLD_TICKS) {
      player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, REGEN_DURATION_TICKS, amplifier, false, false, true));
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    withPlayerThread(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      Long until = wetUntil.get(p.getUniqueId());
      boolean lingering = until != null && System.currentTimeMillis() < until;
      if (!isWet(p) && !lingering) {
        return;
      }

      e.setDamage(e.getDamage() * (1D - getDamageReduction(level)));
      if (reductionFx.isReady(p.getUniqueId(), 500L)) {
        reductionFx.mark(p.getUniqueId());
        fx(p.getLocation().clone().add(0D, 1.0D, 0D), FxPriority.COMBAT)
            .ring(Particle.SPLASH, 0.6D, 8, 0.1D)
            .sound(Sound.ITEM_SHIELD_BLOCK, 0.35F, 1.4F);
      }
    });
  }

  private boolean isWet(Player p) {
    if (p.isInWater() || p.isSwimming()) {
      return true;
    }

    if (!p.getWorld().hasStorm()) {
      return false;
    }

    Location location = p.getLocation();
    return location.getY() >= p.getWorld().getHighestBlockYAt(location) - 1;
  }

  private int getRegenAmplifier(int level) {
    return regenAmplifier(getLevelPercent(level));
  }

  private double getDamageReduction(int level) {
    return damageReduction(getConfig().damageReductionBase, getConfig().damageReductionFactor, getConfig().maxDamageReduction, getLevelPercent(level));
  }

  private long getLingerMillis(int level) {
    return (long) (Math.max(0D, getConfig().lingerSecondsBase + (getLevelPercent(level) * getConfig().lingerSecondsFactor)) * 1000D);
  }

  static int regenAmplifier(double levelPercent) {
    return Math.max(0, Math.min(2, (int) Math.floor(levelPercent * 3D)));
  }

  static double damageReduction(double base, double factor, double max, double levelPercent) {
    return Math.min(max, base + (levelPercent * factor));
  }

  @ConfigDescription("While wet you slowly regenerate and take reduced damage, and the buff lingers briefly after you dry off.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base damage reduction fraction while wet.", impact = "Higher values reduce more damage at low levels.")
    double damageReductionBase = 0.06;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional damage reduction fraction gained across levels.", impact = "Higher values reduce more damage at higher levels.")
    double damageReductionFactor = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on damage reduction fraction.", impact = "Lower values keep the reduction modest even at max level.")
    double maxDamageReduction = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base seconds the wet buff lingers after drying off.", impact = "Higher values keep protection active longer after leaving water at low levels.")
    double lingerSecondsBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional linger seconds gained across levels.", impact = "Higher values keep protection active far longer at higher levels.")
    double lingerSecondsFactor = 4;

    public Config() {
      baseCost = 3;
      costFactor = 0.55;
      maxLevel = 5;
      initialCost = 3;
    }
  }
}
