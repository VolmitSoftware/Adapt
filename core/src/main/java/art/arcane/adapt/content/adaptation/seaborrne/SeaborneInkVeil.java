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
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.SeabornMessages;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SeaborneInkVeil extends SimpleAdaptation<SeaborneInkVeil.Config> {
  public static final PlayerPreference<CommonPreferences.Scale> VISUALS = CommonPreferences.scale("visuals", SeabornMessages.SEABORNEINKVEIL_PREFERENCE_VISUALS);

  private static final double MAX_CLOUD_RADIUS = 16D;
  private static final int MAX_AFFECTED_HOSTILES = 128;
  private static final int MAX_EFFECT_TICKS = 20 * 60;
  private static final int MAX_CLOUD_VISUAL_TICKS = 40;
  private static final Id BLINDNESS = Id.minecraft("blindness");
  private static final Rgb INK = new Rgb(0, 0, 0);
  private static final Set<Id> INK_HUNTERS = Set.of(
      Id.minecraft("drowned"), Id.minecraft("guardian"), Id.minecraft("elder_guardian"));

  private final Cooldowns cloudCooldowns = cooldowns();
  private final Map<UUID, Long> concealedUntil = playerState();

  public SeaborneInkVeil() {
    super("seaborne-ink-veil");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.ink_veil");
    setIcon(Id.minecraft("ink_sac"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ink_sac"))
        .key("challenge_seaborne_ink_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("glow_ink_sac"))
            .key("challenge_seaborne_ink_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_seaborne_ink_100", "seaborne.ink-veil.clouds-burst", 100, 300);
    registerMilestone("challenge_seaborne_ink_1k", "seaborne.ink-veil.clouds-burst", 1000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, VISUALS);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    if (!isPlayerEnabled(player.getPlayer())) {
      concealedUntil.remove(player.getPlayer().uuid());
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getCloudSize(level), 1), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 2);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
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

      if (!cloudCooldowns.isReady(p.uuid(), getCooldownMillis(level))) {
        return;
      }

      burstInk(p, level);
      cloudCooldowns.mark(p.uuid());
    });
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityTargetEvent e) {
    if (!(e.target() instanceof PlayerRef player)
        || !isInkHunter(e.entity())
        || !isConcealed(player.uuid(), System.currentTimeMillis())) {
      return;
    }

    e.setCancelled(true);
    if (e.entity() instanceof MobRef mob) {
      mob.setTarget(null);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    concealedUntil.remove(e.player().uuid());
  }

  private void burstInk(PlayerRef p, int level) {
    double radius = getCloudSize(level);
    Loc center = p.location().add(0D, 1.0D, 0D);
    int blindTicks = getBlindTicks(level);
    renewConcealment(p, getConcealmentTicks(level));

    int maxAffected = getMaxAffectedHostiles();
    int affected = 0;
    int hunters = 0;
    for (EntityRef entity : p.world().nearbyEntities(center.position(), radius, radius, radius)) {
      if (!(entity instanceof MobRef monster) || !monster.has(EntityTrait.MONSTER)) {
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

      Tasks.runEntity(monster, () -> {
        if (applyBlindness) {
          monster.addEffect(new EffectInstance(BLINDNESS, blindTicks, 0, false, true, true));
        }
        LivingRef target = monster.target();
        if (clearHunter && target != null && target.uuid().equals(p.uuid())) {
          monster.setTarget(null);
        }
      });
    }

    addStat(p, "seaborne.ink-veil.clouds-burst", 1);
    xp(p, getConfig().burstXp);
    double density = preference(p, VISUALS).multiplier();
    FxEmitter burst = fx(center, FxPriority.COMBAT);
    emitInkBurst(burst.except(p), radius, 1D);
    emitInkBurst(burst.only(p), radius, density);
    burst.chord(Sounds.ENTITY_DOLPHIN_SPLASH, 0.6F, 0.6F, Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.5F, 0.7F);
    timeline(center)
        .duration(getCloudVisualTicks())
        .priority(FxPriority.COMBAT)
        .cullRadius(radius + 16D)
        .frame((f, tick, progress) -> {
          double cloudRadius = radius * (0.2D + (progress * 0.8D));
          emitInkFrame(f.except(p), cloudRadius, radius, tick, 1D);
          emitInkFrame(f.only(p), cloudRadius, radius, tick, density);
        })
        .start();
  }

  private void emitInkBurst(FxEmitter emitter, double radius, double density) {
    emitter.particle(Particles.SQUID_INK, (int) (Math.min(48D, radius * 8D) * density), 0D, 0D, 0D, radius * 0.35D, 0.02D)
        .dustBurst(INK, (int) (Math.min(24D, radius * 3D) * density), radius * 0.5D, 1.4F);
  }

  private void emitInkFrame(FxEmitter emitter, double cloudRadius, double radius, int tick, double density) {
    emitter.ring(Particles.SQUID_INK, cloudRadius, (int) (Math.max(8D, Math.round(radius * 3D)) * density), 0D);
    if ((tick & 1) == 0) {
      emitter.dome(Particles.SQUID_INK, cloudRadius, (int) (Math.max(4D, Math.round(radius)) * density));
    }
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

  private void renewConcealment(PlayerRef player, int durationTicks) {
    UUID playerId = player.uuid();
    long expiry = System.currentTimeMillis() + (durationTicks * 50L);
    concealedUntil.put(playerId, expiry);
    boolean scheduled = Tasks.runEntity(player, () -> concealedUntil.remove(playerId, expiry), durationTicks);
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

  private static boolean isInkHunter(EntityRef entity) {
    return INK_HUNTERS.contains(entity.type());
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
