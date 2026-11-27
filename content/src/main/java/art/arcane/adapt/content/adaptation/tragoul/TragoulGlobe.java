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

package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.AdaptationDamageTargets;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class TragoulGlobe extends SimpleAdaptation<TragoulGlobe.Config> {
  public static final Id ID = Id.adapt("tragoul/globe");
  private static final double HARD_MAX_RANGE = 24D;
  private static final int MAX_CANDIDATES_PER_ACTIVATION = 24;
  private static final int MAX_SHARED_TARGETS_PER_ACTIVATION = 8;
  private static final int MAX_ACTIVATIONS_PER_WINDOW = 32;
  private static final int MAX_SHARED_TARGETS_PER_WINDOW = 256;
  private static final long WORK_WINDOW_MILLIS = 50L;
  private static final long MIN_COOLDOWN_MILLIS = 500L;
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, Boolean> sharingDamage = playerState();
  private final GlobeWorkBudget workBudget = new GlobeWorkBudget(
      MAX_ACTIVATIONS_PER_WINDOW,
      MAX_SHARED_TARGETS_PER_WINDOW,
      WORK_WINDOW_MILLIS
  );

  public TragoulGlobe() {
    super(ID);
    registerConfiguration(TragoulGlobe.Config.class);
    setIcon(Id.minecraft("crying_obsidian"));
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("glass"))
        .key("challenge_tragoul_globe_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_tragoul_globe_1k", "tragoul.globe.mobs-shared-with", 1000, 400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("glass"))
        .key("challenge_tragoul_globe_5")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TragoulPreferences.PASSIVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.tragoul.globe.lore1").resolve());
    v.addLore(TextFormat.YELLOW + Text.key("adapt.tragoul.globe.range").withMarkup("range", Format.f((getConfig().rangePerLevel * level) + getConfig().initalRange, 1)).resolve());
    v.addLore(TextFormat.YELLOW + Text.key("adapt.tragoul.globe.damage").withMarkup("damage", Format.f(getConfig().bonusDamagePerLevel * level, 1)).resolve());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef p) || !(e.entity() instanceof LivingRef originalTarget)) {
      return;
    }

    if (!AdaptApi.scheduler().owns(p) || !AdaptApi.scheduler().owns(originalTarget)) {
      return;
    }

    UUID playerId = p.uuid();
    int level = getActiveLevel(p);
    if (level <= 0 || sharingDamage.containsKey(playerId) || !canDamageTarget(p, originalTarget)) {
      return;
    }

    long cooldownMillis = getCooldownMillis();
    long now = System.currentTimeMillis();
    if (!cooldowns.isReady(playerId, cooldownMillis) || !workBudget.tryActivation(now)) {
      return;
    }

    double range = getRange(level);
    Loc playerLocation = p.location();
    Collection<LivingRef> nearby = nearbyLiving(playerLocation, range);
    List<LivingRef> targets = collectTargets(p, originalTarget, nearby);
    int targetLimit = workBudget.reserveTargets(targets.size(), now);
    if (targetLimit <= 0) {
      return;
    }

    double originalDamage = e.damage();
    double damagePerEntity = damagePerEntity(originalDamage, targetLimit, getBonusDamage(level));
    e.setDamage(damagePerEntity);

    Loc chest = playerLocation.add(0, 1.0, 0);
    FxEmitter tether = fx(chest, FxPriority.COMBAT);
    int mobsSharedWith = shareDamage(p, playerId, targets, targetLimit, damagePerEntity, tether);
    if (mobsSharedWith <= 0) {
      e.setDamage(originalDamage);
      return;
    }

    cooldowns.mark(playerId, cooldownMillis);
    addStat(p, "tragoul.globe.mobs-shared-with", mobsSharedWith);
    playShareShockwave(playerLocation, range, mobsSharedWith);
    if (mobsSharedWith >= 5) {
      fx(chest, FxPriority.TRANSITION)
          .burst(Particles.SCULK_SOUL, 12, 0.5D)
          .sound(Sounds.BLOCK_SCULK_CATALYST_BLOOM, 0.6F, 1.2F);
      grantOnce(p, "challenge_tragoul_globe_5");
    }
  }

  private static Collection<LivingRef> nearbyLiving(Loc center, double range) {
    List<LivingRef> found = new ArrayList<>();
    if (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().ownsArea(center, range, range)) {
      return found;
    }
    for (EntityRef entity : center.world().nearbyEntities(center.position(), range, range, range)) {
      if (entity instanceof LivingRef living) {
        found.add(living);
      }
    }
    return found;
  }

  private List<LivingRef> collectTargets(PlayerRef player, LivingRef originalTarget,
                                         Collection<LivingRef> nearby) {
    List<LivingRef> targets = new ArrayList<>(MAX_SHARED_TARGETS_PER_ACTIVATION);
    int examined = 0;
    for (LivingRef candidate : nearby) {
      if (examined++ >= MAX_CANDIDATES_PER_ACTIVATION) {
        break;
      }
      if (candidate.uuid().equals(player.uuid()) || candidate.uuid().equals(originalTarget.uuid()) || !AdaptApi.scheduler().owns(candidate)) {
        continue;
      }
      if (!candidate.isValid() || candidate.isDead()
          || !AdaptationDamageTargets.allows(candidate, getConfig().ignorePassiveMobs || preferenceEnabled(player, TragoulPreferences.PASSIVE))
          || !canDamageTarget(player, candidate)) {
        continue;
      }
      targets.add(candidate);
      if (targets.size() >= MAX_SHARED_TARGETS_PER_ACTIVATION) {
        break;
      }
    }
    return targets;
  }

  private int shareDamage(PlayerRef player, UUID playerId, List<LivingRef> targets, int targetLimit,
                          double damage, FxEmitter tether) {
    int shared = 0;
    sharingDamage.put(playerId, Boolean.TRUE);
    try {
      for (int index = 0; index < targetLimit; index++) {
        LivingRef target = targets.get(index);
        double healthBefore = target.health() + target.absorption();
        target.damage(damage, player);
        double healthAfter = target.isDead() ? 0D : target.health() + target.absorption();
        if (healthAfter >= healthBefore) {
          continue;
        }

        Loc targetLocation = target.location();
        tether.line(Particles.SOUL, targetLocation.x(), targetLocation.y() + 1.0, targetLocation.z(), 3);
        shared++;
      }
    } finally {
      sharingDamage.remove(playerId);
    }
    return shared;
  }

  private double getRange(int level) {
    double configured = (getConfig().rangePerLevel * level) + getConfig().initalRange;
    if (!Double.isFinite(configured)) {
      return 1D;
    }
    return Math.max(1D, Math.min(HARD_MAX_RANGE, configured));
  }

  private long getCooldownMillis() {
    double configured = getConfig().cooldown;
    if (!Double.isFinite(configured)) {
      return MIN_COOLDOWN_MILLIS;
    }
    return Math.max(MIN_COOLDOWN_MILLIS, (long) (1000D * configured));
  }

  private double getBonusDamage(int level) {
    double configured = getConfig().bonusDamagePerLevel * level;
    return Double.isFinite(configured) ? Math.max(0D, configured) : 0D;
  }

  static double damagePerEntity(double originalDamage, int sharedTargets, double bonusDamage) {
    if (!Double.isFinite(originalDamage) || !Double.isFinite(bonusDamage)) {
      return 0D;
    }
    int divisor = Math.max(0, sharedTargets) + 1;
    return Math.max(0D, (originalDamage / divisor) + Math.max(0D, bonusDamage));
  }

  private void playShareShockwave(Loc center, double range, int mobsSharedWith) {
    if (mobsSharedWith <= 0) {
      return;
    }

    double inner = range * 0.3D;
    int ringPoints = Math.max(8, Math.min(24, mobsSharedWith * 4));
    timeline(center)
        .duration(6)
        .priority(FxPriority.TRANSITION)
        .cullRadius(range + 8)
        .frame((fx, tick, progress) -> {
          double radius = inner + ((range - inner) * progress);
          Rgb ringColor = new Rgb((int) (150 - (120 * progress)), (int) (12 - (7 * progress)), (int) (12 - (7 * progress)));
          fx.dustRing(ringColor, radius, ringPoints, 1.2F);
          if (tick == 0) {
            fx.chord(Sounds.ENTITY_WITHER_SHOOT, 0.4F, 1.6F, Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5F, 0.9F);
          }
        })
        .start();
  }




  @ConfigDescription("Spread your damage among all nearby enemies.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from secondary shared damage.", impact = "When enabled, protected mobs do not count toward the damage split. Direct attacks and player targeting are unchanged.")
    boolean ignorePassiveMobs = false;
    @ConfigDoc(value = "Controls Cooldown for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldown = 1;
    @ConfigDoc(value = "Controls Range Per Level for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangePerLevel = 3.0;
    @ConfigDoc(value = "Controls Inital Range for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double initalRange = 5.0;
    @ConfigDoc(value = "Controls Bonus Damage Per Level for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDamagePerLevel = 1;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }

  static final class GlobeWorkBudget {
    private final int activationLimit;
    private final int targetLimit;
    private final long windowMillis;
    private long windowStartedAt = Long.MIN_VALUE;
    private int activations;
    private int targets;

    GlobeWorkBudget(int activationLimit, int targetLimit, long windowMillis) {
      this.activationLimit = Math.max(1, activationLimit);
      this.targetLimit = Math.max(1, targetLimit);
      this.windowMillis = Math.max(1L, windowMillis);
    }

    synchronized boolean tryActivation(long now) {
      refresh(now);
      if (activations >= activationLimit) {
        return false;
      }
      activations++;
      return true;
    }

    synchronized int reserveTargets(int requested, long now) {
      refresh(now);
      int granted = Math.min(Math.max(0, requested), targetLimit - targets);
      targets += granted;
      return granted;
    }

    private void refresh(long now) {
      if (windowStartedAt == Long.MIN_VALUE || now - windowStartedAt >= windowMillis || now < windowStartedAt) {
        windowStartedAt = now;
        activations = 0;
        targets = 0;
      }
    }
  }
}
