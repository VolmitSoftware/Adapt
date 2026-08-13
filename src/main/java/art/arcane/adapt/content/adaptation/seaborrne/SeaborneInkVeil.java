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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;

public class SeaborneInkVeil extends SimpleAdaptation<SeaborneInkVeil.Config> {
  private static final double MAX_CLOUD_RADIUS = 16D;
  private static final int MAX_AFFECTED_HOSTILES = 128;
  private static final int MAX_EFFECT_TICKS = 20 * 60;
  private static final int MAX_CLOUD_VISUAL_TICKS = 40;

  private final Cooldowns cloudCooldowns = cooldowns();
  private final Map<UUID, Long> concealedUntil = playerState();

  public SeaborneInkVeil() {
    super("seaborne-ink-veil");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.ink_veil");
    setIcon(Material.INK_SAC);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.INK_SAC)
        .key("challenge_seaborne_ink_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.GLOW_INK_SAC)
            .key("challenge_seaborne_ink_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_seaborne_ink_100", "seaborne.ink-veil.clouds-burst", 100, 300);
    registerMilestone("challenge_seaborne_ink_1k", "seaborne.ink-veil.clouds-burst", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getCloudSize(level), 1), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    withPlayerThread(p, e, () -> {
      if (!p.isInWater()) {
        return;
      }

      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      if (!cloudCooldowns.isReady(p.getUniqueId(), getCooldownMillis(level))) {
        return;
      }

      burstInk(p, level);
      cloudCooldowns.mark(p.getUniqueId());
    });
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityTargetLivingEntityEvent e) {
    if (!(e.getTarget() instanceof Player player)
        || !isInkHunter(e.getEntity())
        || !isConcealed(player.getUniqueId(), System.currentTimeMillis())) {
      return;
    }

    e.setCancelled(true);
    if (e.getEntity() instanceof Mob mob) {
      mob.setTarget(null);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    concealedUntil.remove(e.getPlayer().getUniqueId());
  }

  private void burstInk(Player p, int level) {
    double radius = getCloudSize(level);
    Location center = p.getLocation().clone().add(0D, 1.0D, 0D);
    int blindTicks = getBlindTicks(level);
    renewConcealment(p, getConcealmentTicks(level));

    int maxAffected = getMaxAffectedHostiles();
    int affected = 0;
    int hunters = 0;
    for (Entity entity : p.getWorld().getNearbyEntities(center, radius, radius, radius)) {
      if (!(entity instanceof Monster monster)) {
        continue;
      }

      boolean applyBlindness = affected < maxAffected;
      boolean clearHunter = isInkHunter(monster) && hunters < MAX_AFFECTED_HOSTILES;
      if (!applyBlindness && !clearHunter) {
        continue;
      }
      if (applyBlindness) {
        affected++;
      }
      if (clearHunter) {
        hunters++;
      }

      J.runEntity(monster, () -> {
        if (applyBlindness) {
          monster.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindTicks, 0, false, true, true));
        }
        if (clearHunter && monster.getTarget() == p) {
          monster.setTarget(null);
        }
      });
    }

    addStat(p, "seaborne.ink-veil.clouds-burst", 1);
    xp(p, getConfig().burstXp);
    fx(center, FxPriority.COMBAT)
        .particle(Particle.SQUID_INK, (int) Math.min(48D, radius * 8D), 0D, 0D, 0D, radius * 0.35D, 0.02D)
        .dustBurst(Color.BLACK, (int) Math.min(24D, radius * 3D), radius * 0.5D, 1.4F)
        .chord(Sound.ENTITY_DOLPHIN_SPLASH, 0.6F, 0.6F, Sound.BLOCK_CONDUIT_DEACTIVATE, 0.5F, 0.7F);
    timeline(center)
        .duration(getCloudVisualTicks())
        .priority(FxPriority.COMBAT)
        .cullRadius(radius + 16D)
        .frame((f, tick, progress) -> {
          double cloudRadius = radius * (0.2D + (progress * 0.8D));
          f.ring(Particle.SQUID_INK, cloudRadius, Math.max(8, (int) Math.round(radius * 3D)), 0D);
          if ((tick & 1) == 0) {
            f.dome(Particle.SQUID_INK, cloudRadius, Math.max(4, (int) Math.round(radius)));
          }
        })
        .start();
  }

  private double getCloudSize(int level) {
    return cloudSize(getConfig().cloudSizeBase, getConfig().cloudSizeFactor, getLevelPercent(level));
  }

  private long getCooldownMillis(int level) {
    return cooldownMillis(getConfig().cooldownMillisBase, getConfig().cooldownMillisReduction, getLevelPercent(level));
  }

  private int getConcealmentTicks(int level) {
    return scaledTicks(getConfig().concealmentTicksBase, getConfig().concealmentTicksFactor, getLevelPercent(level));
  }

  private int getBlindTicks(int level) {
    return scaledTicks(getConfig().blindTicksBase, getConfig().blindTicksFactor, getLevelPercent(level));
  }

  private int getMaxAffectedHostiles() {
    return Math.max(0, Math.min(MAX_AFFECTED_HOSTILES, getConfig().maxAffectedHostiles));
  }

  private int getCloudVisualTicks() {
    return Math.max(1, Math.min(MAX_CLOUD_VISUAL_TICKS, getConfig().cloudVisualTicks));
  }

  private void renewConcealment(Player player, int durationTicks) {
    UUID playerId = player.getUniqueId();
    long expiry = System.currentTimeMillis() + (durationTicks * 50L);
    concealedUntil.put(playerId, expiry);
    boolean scheduled = J.runEntity(player, () -> concealedUntil.remove(playerId, expiry), durationTicks);
    if (!scheduled) {
      concealedUntil.remove(playerId, expiry);
    }
  }

  private boolean isConcealed(UUID playerId, long now) {
    Long until = concealedUntil.get(playerId);
    if (until == null) {
      return false;
    }
    if (isConcealed(until, now)) {
      return true;
    }
    concealedUntil.remove(playerId, until);
    return false;
  }

  private static boolean isInkHunter(Entity entity) {
    return entity instanceof Drowned || entity instanceof Guardian;
  }

  static double cloudSize(double base, double factor, double levelPercent) {
    double scaled = base + (levelPercent * factor);
    if (!Double.isFinite(scaled)) {
      return 0.5D;
    }
    return Math.max(0.5D, Math.min(MAX_CLOUD_RADIUS, scaled));
  }

  static long cooldownMillis(long base, long reduction, double levelPercent) {
    double scaled = base - (levelPercent * reduction);
    if (!Double.isFinite(scaled)) {
      return 3000L;
    }
    return Math.max(3000L, Math.min(60L * 60L * 1000L, (long) scaled));
  }

  static int scaledTicks(double base, double factor, double levelPercent) {
    double scaled = base + (levelPercent * factor);
    if (!Double.isFinite(scaled)) {
      return 1;
    }
    return Math.max(1, Math.min(MAX_EFFECT_TICKS, (int) Math.round(scaled)));
  }

  static boolean isConcealed(long concealedUntil, long now) {
    return concealedUntil > now;
  }

  @Override
  public void unregister() {
    concealedUntil.clear();
    super.unregister();
  }

  @ConfigDescription("Taking damage underwater bursts an ink cloud that blinds hostiles and briefly hides you from drowned and guardians.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base ink cloud radius in blocks.", impact = "Higher values blind hostiles across a wider area at low levels.")
    double cloudSizeBase = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional ink cloud radius gained across levels.", impact = "Higher values greatly widen the cloud at higher levels.")
    double cloudSizeFactor = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base cooldown in milliseconds between ink bursts.", impact = "Lower values allow more frequent bursts; the effective cooldown shrinks as you level.")
    long cooldownMillisBase = 12000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown milliseconds removed at max level.", impact = "Higher values make higher levels burst far more often (floored at 3s).")
    long cooldownMillisReduction = 8000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base duration in ticks that drowned and guardians cannot target the player after a burst.", impact = "Higher values keep those underwater hunters disengaged longer at low levels.")
    double concealmentTicksBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional anti-target duration in ticks gained across levels.", impact = "Higher values extend protection from drowned and guardian targeting at higher levels.")
    double concealmentTicksFactor = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base blindness duration in ticks applied to nearby hostiles.", impact = "Higher values blind hostiles for longer at low levels.")
    double blindTicksBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional blindness ticks gained across levels.", impact = "Higher values blind hostiles for longer at higher levels.")
    double blindTicksFactor = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum number of nearby hostile mobs affected by each ink burst.", impact = "Higher values let one cloud blind more mobs but increase burst-time work in dense farms.")
    int maxAffectedHostiles = 24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the expanding squid-ink cloud visual.", impact = "Higher values keep the cloud visible longer and emit more particles.")
    int cloudVisualTicks = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus XP granted when an ink cloud bursts.", impact = "Higher values reward defensive bursts with more skill XP.")
    double burstXp = 10;

    public Config() {
      baseCost = 4;
      costFactor = 0.55;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
