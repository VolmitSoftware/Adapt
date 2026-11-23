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

package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.ViewerDisplayPort;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HunterBloodTrail extends SimpleAdaptation<HunterBloodTrail.Config> {
  private static final Id TRAIL_BLOCK = Id.minecraft("red_stained_glass");
  private final Map<UUID, Wound> wounds = new ConcurrentHashMap<>();

  public HunterBloodTrail() {
    super("hunter-blood-trail");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("redstone"));
    setInterval(250);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("redstone"))
        .key("challenge_hunter_blood_trail_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("redstone_block"))
            .key("challenge_hunter_blood_trail_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_hunter_blood_trail_250", "hunter.blood-trail.trails-followed", 250, 400);
    registerMilestone("challenge_hunter_blood_trail_2500", "hunter.blood-trail.trails-followed", 2500, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HunterPreferences.TRAIL, HunterPreferences.GLOW);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getTrailDurationTicks(level) / 20.0D, 1), 1);
    statLore(v, C.YELLOW, "+ ", Form.f(getTrackingRange(level), 1), 2);
  }

  static boolean isWounded(double healthAfter, double maxHealth, double fraction) {
    return healthAfter > 0 && maxHealth > 0 && healthAfter <= maxHealth * fraction;
  }

  static double trackingRange(double levelPercent, double base, double factor) {
    return base + (levelPercent * factor);
  }

  static long trailDurationTicks(double levelPercent, long base, double factor) {
    return base + Math.round(levelPercent * factor);
  }

  long getTrailDurationTicks(int level) {
    return trailDurationTicks(getLevelPercent(level), getConfig().trailDurationTicksBase, getConfig().trailDurationTicksFactor);
  }

  double getTrackingRange(int level) {
    return trackingRange(getLevelPercent(level), getConfig().rangeBase, getConfig().rangeFactor);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    AttackContext attack = resolveAttackContext(e);
    if (attack == null || attack.target() instanceof PlayerRef || !(attack.target() instanceof LivingRef victim)) {
      return;
    }

    double maxHealth = victim.maxHealth();
    double healthAfter = victim.health() - e.finalDamage();
    if (!isWounded(healthAfter, maxHealth, getConfig().woundHealthFraction)) {
      return;
    }

    PlayerRef p = attack.attacker();
    int level = attack.level();
    long expiresAt = M.ms() + (getTrailDurationTicks(level) * 50L);
    double range = getTrackingRange(level);

    Wound previous = wounds.put(victim.uuid(),
        new Wound(p.uuid(), expiresAt, range, victim.world().uid()));
    if (wounds.size() > Math.max(1, getConfig().maxTrackedWounds)) {
      pruneOldest();
    }
    if (previous == null || !previous.hunterId.equals(p.uuid())) {
      xpSilent(p, getConfig().xpPerWound);
    }
  }

  private void pruneOldest() {
    UUID oldestKey = null;
    long oldestExpiry = Long.MAX_VALUE;
    for (Map.Entry<UUID, Wound> entry : wounds.entrySet()) {
      if (entry.getValue().expiresAt < oldestExpiry) {
        oldestExpiry = entry.getValue().expiresAt;
        oldestKey = entry.getKey();
      }
    }
    if (oldestKey != null) {
      wounds.remove(oldestKey);
    }
  }

  @Override
  public boolean hasTickDemand() {
    return !wounds.isEmpty();
  }

  @Override
  public void onTick() {
    long now = M.ms();
    int processed = 0;
    int limit = Math.max(1, getConfig().maxTrackedWounds);
    Iterator<Map.Entry<UUID, Wound>> iterator = wounds.entrySet().iterator();
    while (iterator.hasNext() && processed < limit) {
      Map.Entry<UUID, Wound> entry = iterator.next();
      Wound wound = entry.getValue();
      if (wound.expiresAt <= now) {
        iterator.remove();
        continue;
      }
      processed++;
      renderWound(entry.getKey(), wound);
    }
  }

  private void renderWound(UUID entityId, Wound wound) {
    PlatformHost host = AdaptCore.platform().host();
    EntityRef mob = host.entity(entityId);
    PlayerRef hunter = host.player(wound.hunterId);
    if (mob == null || hunter == null) {
      wounds.remove(entityId);
      return;
    }

    Tasks.runEntity(mob, () -> {
      if (!mob.isValid()) {
        wounds.remove(entityId);
        return;
      }
      Loc current = mob.location().add(0, mob.height() * 0.35D, 0);
      Tasks.runEntity(hunter, () -> emitTrail(hunter, entityId, wound, current));
    });
  }

  private void emitTrail(PlayerRef hunter, UUID entityId, Wound wound, Loc current) {
    if (!hunter.isOnline() || wounds.get(entityId) != wound || getActiveLevel(hunter) <= 0
        || !preferenceEnabled(hunter, HunterPreferences.TRAIL)) {
      return;
    }

    WorldRef hunterWorld = hunter.world();
    if (!hunterWorld.uid().equals(wound.worldId) || current.world() == null || !hunterWorld.equals(current.world())) {
      wound.lastRender = null;
      return;
    }

    Loc hunterLocation = hunter.location();
    if (hunterLocation.distanceSquared(current) > wound.range * wound.range) {
      wound.lastRender = null;
      return;
    }

    if (!wound.followed) {
      wound.followed = true;
      addStat(hunter, "hunter.blood-trail.trails-followed", 1);
    }

    Loc previous = wound.lastRender;
    if (previous != null && previous.world() != null && previous.world().equals(current.world())) {
      if (previous.distanceSquared(current) > 1.0E-5D) {
        displays().showLine(
            getName(),
            "wound-" + entityId,
            hunter,
            previous,
            current,
            AdaptCore.platform().host().items().blockState(TRAIL_BLOCK),
            preference(hunter, HunterPreferences.GLOW).color(),
            getConfig().trailThickness,
            getConfig().displayDurationTicks
        );
      }
    }
    wound.lastRender = current;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.player().uuid();
    wounds.entrySet().removeIf(entry -> entry.getValue().hunterId.equals(playerId));
    displays().clearViewer(getName(), playerId);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    UUID playerId = player.getPlayer().uuid();
    wounds.entrySet().removeIf(entry -> entry.getValue().hunterId.equals(playerId));
    displays().clearViewer(getName(), playerId);
  }

  @Override
  public void unregister() {
    wounds.clear();
    displays().clearChannel(getName());
    super.unregister();
  }

  private static ViewerDisplayPort displays() {
    return AdaptPorts.get(ViewerDisplayPort.class, ViewerDisplayPort.NONE);
  }

  @ConfigDescription("Mobs you wound below half health leave a private glowing blood trail only you can see.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base blood-trail duration in ticks for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long trailDurationTicksBase = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional blood-trail duration in ticks gained across levels for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double trailDurationTicksFactor = 200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base tracking range in blocks for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeBase = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional tracking range in blocks gained across levels for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeFactor = 32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of max health a target must fall to or below to bleed for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double woundHealthFraction = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum simultaneously tracked blood trails for the Hunter Blood Trail adaptation.", impact = "Lower values reduce scheduling and particle work.")
    int maxTrackedWounds = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Thickness of each private glowing blood trail segment.", impact = "Higher values make the trail wider and easier to follow.")
    double trailThickness = 0.06;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration of each private glowing blood trail segment, in ticks.", impact = "Higher values leave a longer-lived trail behind wounded targets.")
    int displayDurationTicks = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Silent xp granted when a fresh target starts bleeding for the Hunter Blood Trail adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerWound = 3;

    public Config() {
      baseCost = 1;
      costFactor = 0.5;
      initialCost = 1;
    }
  }

  private static final class Wound {
    private final UUID hunterId;
    private final long expiresAt;
    private final double range;
    private final UUID worldId;
    private volatile Loc lastRender;
    private volatile boolean followed;

    private Wound(UUID hunterId, long expiresAt, double range, UUID worldId) {
      this.hunterId = hunterId;
      this.expiresAt = expiresAt;
      this.range = range;
      this.worldId = worldId;
    }
  }
}
