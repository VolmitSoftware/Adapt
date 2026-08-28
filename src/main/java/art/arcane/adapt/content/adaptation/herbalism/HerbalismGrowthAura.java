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

package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class HerbalismGrowthAura extends SimpleAdaptation<HerbalismGrowthAura.Config> {
  private static final int PLAYER_CHECKS_PER_TICK = 32;
  private static final int LOCATION_SAMPLES_PER_TICK = 32;
  private static final int MUTATIONS_PER_TICK = 16;
  private static final int COMPLETIONS_PER_TICK = 16;
  private static final int MAX_QUEUED_SAMPLES = 4096;
  private static final int MAX_QUEUED_MUTATIONS = 4096;
  private static final int MAX_QUEUED_COMPLETIONS = 2048;
  private static final long ROSTER_REFRESH_MILLIS = 5000L;
  private static final long PULSE_INTERVAL_MILLIS = 850L;
  private static final long IDLE_CHECK_MILLIS = 250L;
  private final Map<UUID, Long> nextCheckAt = playerState();
  private final Queue<UUID> playerQueue = new ConcurrentLinkedQueue<>();
  private final Set<UUID> queuedPlayers = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingPulses = ConcurrentHashMap.newKeySet();
  private final Queue<GrowthSample> locationSamples = new ConcurrentLinkedQueue<>();
  private final AtomicInteger queuedSamples = new AtomicInteger();
  private final PriorityBlockingQueue<GrowthMutation> mutations = new PriorityBlockingQueue<>();
  private final AtomicInteger queuedMutations = new AtomicInteger();
  private final Queue<GrowthPulse> completedPulses = new ConcurrentLinkedQueue<>();
  private final AtomicInteger queuedCompletions = new AtomicInteger();
  private volatile long nextRosterRefreshAt;

  public HerbalismGrowthAura() {
    super("herbalism-growth-aura");
    registerConfiguration(Config.class);
    setIcon(Material.BONE_MEAL);
    setInterval(10);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WHEAT)
        .key("challenge_herbalism_growth_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.HAY_BLOCK)
            .key("challenge_herbalism_growth_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_herbalism_growth_1k", "herbalism.growth-aura.blocks-grown", 1000, 300);
    registerMilestone("challenge_herbalism_growth_25k", "herbalism.growth-aura.blocks-grown", 25000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(getLevelPercent(level)), 0), 1);
    statLore(v, Form.pc(getStrength(level), 0), 2);
    statLore(v, C.YELLOW, "+ ", Form.f(getFoodCost(getLevelPercent(level)), 2), 3);
  }

  private double getRadius(double factor) {
    return factor * getConfig().radiusFactor;
  }

  private double getStrength(int level) {
    return level * getConfig().strengthFactor;
  }

  private double getFoodCost(double factor) {
    return M.lerp(1D - factor, getConfig().maxFoodCost, getConfig().minFoodCost);
  }

  @Override
  public boolean hasTickDemand() {
    return super.hasTickDemand()
        || !playerQueue.isEmpty()
        || !queuedPlayers.isEmpty()
        || !pendingPulses.isEmpty()
        || queuedSamples.get() > 0
        || !locationSamples.isEmpty()
        || queuedMutations.get() > 0
        || !mutations.isEmpty()
        || queuedCompletions.get() > 0
        || !completedPulses.isEmpty();
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }


  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    refreshRoster(now);
    processLocationSamples();
    processMutations(now);
    processCompletions();
    processPlayers(now);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    nextCheckAt.remove(id);
    pendingPulses.remove(id);
    if (queuedPlayers.remove(id)) {
      playerQueue.remove(id);
    }
  }

  private void refreshRoster(long now) {
    if (now < nextRosterRefreshAt) {
      return;
    }
    nextRosterRefreshAt = now + ROSTER_REFRESH_MILLIS;
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player p = adaptPlayer.getPlayer();
      if (p != null) {
        queuePlayer(p.getUniqueId());
      }
    }
  }

  private void processPlayers(long now) {
    int attempts = workFor(queuedPlayers.size(), PLAYER_CHECKS_PER_TICK);
    for (int i = 0; i < attempts; i++) {
      UUID id = playerQueue.poll();
      if (id == null) {
        break;
      }
      queuedPlayers.remove(id);
      Long due = nextCheckAt.get(id);
      if (due != null && due > now) {
        queuePlayer(id);
        continue;
      }
      Player p = Bukkit.getPlayer(id);
      if (p == null) {
        nextCheckAt.remove(id);
        pendingPulses.remove(id);
        continue;
      }
      withPlayerThread(p, () -> evaluatePlayer(p));
    }
  }

  private void evaluatePlayer(Player p) {
    UUID id = p.getUniqueId();
    if (!p.isOnline() || getLevel(p) <= 0) {
      nextCheckAt.remove(id);
      pendingPulses.remove(id);
      return;
    }

    long now = System.currentTimeMillis();
    int level = getActiveLevel(p);
    if (level <= 0 || pendingPulses.contains(id)) {
      nextCheckAt.put(id, now + IDLE_CHECK_MILLIS);
      queuePlayer(id);
      return;
    }

    double factor = getLevelPercent(level);
    double foodCost = getFoodCost(factor);
    AdaptPlayer adaptPlayer = getPlayer(p);
    if (adaptPlayer == null) {
      nextCheckAt.put(id, now + IDLE_CHECK_MILLIS);
      queuePlayer(id);
      return;
    }

    pendingPulses.add(id);
    nextCheckAt.put(id, now + PULSE_INTERVAL_MILLIS);
    startGrowthPulse(p, factor, getStrength(level), foodCost);
    queuePlayer(id);
  }

  private void startGrowthPulse(Player p, double factor, double strength, double foodCost) {
    double radius = getRadius(factor);
    int sampleCount = sampleCountForRadius(radius);
    if (sampleCount <= 0) {
      pendingPulses.remove(p.getUniqueId());
      return;
    }

    ThreadLocalRandom random = ThreadLocalRandom.current();
    Location center = p.getLocation();
    GrowthPulse pulse = new GrowthPulse(p, sampleCount);
    for (int i = 0; i < sampleCount; i++) {
      double angle = Math.toRadians(random.nextDouble(360D));
      Vector offset = new Vector(Math.sin(angle), 0D, Math.cos(angle));
      offset.setY(random.nextInt(-1, 2));
      offset.multiply(radius <= 0D ? 0D : random.nextDouble(radius));
      Location target = center.clone().add(offset);
      GrowthSample sample = new GrowthSample(target, strength, foodCost, getConfig().surfaceOnly, pulse);
      if (!offerLocationSample(sample)) {
        completeGrowthSample(pulse, 0);
      }
    }
  }

  private boolean offerLocationSample(GrowthSample sample) {
    int queued = queuedSamples.incrementAndGet();
    if (queued > MAX_QUEUED_SAMPLES) {
      queuedSamples.decrementAndGet();
      return false;
    }
    locationSamples.add(sample);
    return true;
  }

  private void processLocationSamples() {
    int attempts = workFor(queuedSamples.get(), LOCATION_SAMPLES_PER_TICK);
    for (int i = 0; i < attempts; i++) {
      GrowthSample sample = locationSamples.poll();
      if (sample == null) {
        break;
      }
      queuedSamples.decrementAndGet();
      if (!J.runAt(sample.location, () -> inspectLocationSample(sample))) {
        completeGrowthSample(sample.pulse, 0);
      }
    }
  }

  private void inspectLocationSample(GrowthSample sample) {
    Block block = sample.location.getBlock();
    if (sample.surfaceOnly && block.getWorld().getHighestBlockYAt(sample.location) + 1 != block.getY()) {
      completeGrowthSample(sample.pulse, 0);
      return;
    }
    if (!(block.getBlockData() instanceof Ageable ageable)) {
      completeGrowthSample(sample.pulse, 0);
      return;
    }

    int remainingAge = ageable.getMaximumAge() - ageable.getAge();
    if (remainingAge <= 0) {
      completeGrowthSample(sample.pulse, 0);
      return;
    }
    int increments = (int) Math.max(1D, Math.min(sample.strength, remainingAge));
    long dueAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(1500L, 3001L);
    if (!offerMutation(new GrowthMutation(sample.location, increments, sample.foodCost, dueAt, sample.pulse))) {
      completeGrowthSample(sample.pulse, 0);
    }
  }

  private boolean offerMutation(GrowthMutation mutation) {
    int queued = queuedMutations.incrementAndGet();
    if (queued > MAX_QUEUED_MUTATIONS) {
      queuedMutations.decrementAndGet();
      return false;
    }
    mutations.add(mutation);
    return true;
  }

  private void processMutations(long now) {
    int attempts = workFor(queuedMutations.get(), MUTATIONS_PER_TICK);
    for (int i = 0; i < attempts; i++) {
      GrowthMutation mutation = mutations.peek();
      if (mutation == null || mutation.dueAt > now) {
        break;
      }
      mutations.poll();
      queuedMutations.decrementAndGet();
      if (!J.runAt(mutation.location, () -> applyMutation(mutation))) {
        completeGrowthSample(mutation.pulse, 0);
      }
    }
  }

  private void applyMutation(GrowthMutation mutation) {
    Player p = mutation.pulse.player;
    if ((J.isFoliaThreading() && !J.isOwnedByCurrentRegion(p)) || !p.isOnline()) {
      completeGrowthSample(mutation.pulse, 0);
      return;
    }

    AdaptPlayer adaptPlayer = getPlayer(p);
    if (adaptPlayer == null) {
      completeGrowthSample(mutation.pulse, 0);
      return;
    }

    Block block = mutation.location.getBlock();
    if (!(block.getBlockData() instanceof Ageable ageable)
        || ageable.getAge() >= ageable.getMaximumAge()
        || !canInteract(p, block.getLocation())
        || !canBlockPlace(p, block.getLocation())
        || !ProtectionEventProbe.attemptBlockUse(p, block)
        || !ProtectionEventProbe.attemptBlockPlaceProbe(p, block)) {
      completeGrowthSample(mutation.pulse, 0);
      return;
    }

    int grown = 0;
    while (grown < mutation.increments) {
      if (!(block.getBlockData() instanceof Ageable current)
          || current.getAge() >= current.getMaximumAge()
          || !payHungerCost(p, "hunger", (int) Math.ceil(mutation.foodCost),
          () -> adaptPlayer.consumeFood(mutation.foodCost, 10))) {
        break;
      }
      current.setAge(current.getAge() + 1);
      block.setBlockData(current, true);
      grown++;
    }

    if (grown <= 0) {
      completeGrowthSample(mutation.pulse, 0);
      return;
    }
    BlockData finalData = block.getBlockData();
    if (finalData instanceof Ageable current && current.getAge() >= current.getMaximumAge()) {
      fx(block.getLocation().add(0.5, 1.0, 0.5), FxPriority.AMBIENT)
          .dustRing(Color.LIME, 0.6D, 10, 0.9F)
          .particle(Particle.HAPPY_VILLAGER, 2, 0, 0, 0, 0.1D, 0.02D)
          .chord(Sound.ITEM_CROP_PLANT, 0.35F, 1.2F, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.3F, 1.6F);
    } else {
      fx(block.getLocation().add(0.5, 0.6, 0.5), FxPriority.AMBIENT)
          .particle(Particle.HAPPY_VILLAGER, 2, 0, 0, 0, 0.1D, 0.02D)
          .particle(Particle.COMPOSTER, 1, 0, 0.1D, 0, 0.1D, 0.02D)
          .sound(Sound.ITEM_CROP_PLANT, 0.25F, 1.5F);
    }
    completeGrowthSample(mutation.pulse, grown);
  }

  private void completeGrowthSample(GrowthPulse pulse, int grown) {
    if (grown > 0) {
      addStat(pulse.player, "herbalism.growth-aura.blocks-grown", grown);
    }
    if (pulse.remaining.decrementAndGet() != 0) {
      return;
    }
    int queued = queuedCompletions.incrementAndGet();
    if (queued > MAX_QUEUED_COMPLETIONS) {
      queuedCompletions.decrementAndGet();
      pendingPulses.remove(pulse.player.getUniqueId());
      return;
    }
    completedPulses.add(pulse);
  }

  private void processCompletions() {
    int attempts = workFor(queuedCompletions.get(), COMPLETIONS_PER_TICK);
    for (int i = 0; i < attempts; i++) {
      GrowthPulse pulse = completedPulses.poll();
      if (pulse == null) {
        break;
      }
      queuedCompletions.decrementAndGet();
      if (!J.runEntity(pulse.player, () -> finishGrowthPulse(pulse))) {
        pendingPulses.remove(pulse.player.getUniqueId());
      }
    }
  }

  private void finishGrowthPulse(GrowthPulse pulse) {
    pendingPulses.remove(pulse.player.getUniqueId());
  }

  private void queuePlayer(UUID id) {
    if (queuedPlayers.add(id)) {
      playerQueue.add(id);
    }
  }

  static int workFor(int queued, int limit) {
    return Math.min(Math.max(0, queued), Math.max(0, limit));
  }

  static int sampleCountForRadius(double radius) {
    double limit = Math.max(3D, Math.min(Math.max(0D, radius * radius), 256D));
    return (int) Math.ceil(limit);
  }

  private static final class GrowthPulse {
    private final Player player;
    private final AtomicInteger remaining;

    private GrowthPulse(Player player, int samples) {
      this.player = player;
      this.remaining = new AtomicInteger(samples);
    }
  }

  private static final class GrowthSample {
    private final Location location;
    private final double strength;
    private final double foodCost;
    private final boolean surfaceOnly;
    private final GrowthPulse pulse;

    private GrowthSample(Location location, double strength, double foodCost, boolean surfaceOnly, GrowthPulse pulse) {
      this.location = location;
      this.strength = strength;
      this.foodCost = foodCost;
      this.surfaceOnly = surfaceOnly;
      this.pulse = pulse;
    }
  }

  private static final class GrowthMutation implements Comparable<GrowthMutation> {
    private final Location location;
    private final int increments;
    private final double foodCost;
    private final long dueAt;
    private final GrowthPulse pulse;

    private GrowthMutation(Location location, int increments, double foodCost, long dueAt, GrowthPulse pulse) {
      this.location = location;
      this.increments = increments;
      this.foodCost = foodCost;
      this.dueAt = dueAt;
      this.pulse = pulse;
    }

    @Override
    public int compareTo(GrowthMutation other) {
      return Long.compare(dueAt, other.dueAt);
    }
  }

  @ConfigDescription("Grow nature around you in an aura at the cost of hunger.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Surface Only for the Herbalism Growth Aura adaptation.", impact = "True enables this behavior and false disables it.")
    boolean surfaceOnly = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Food Cost for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minFoodCost = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Food Cost for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxFoodCost = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Strength Factor for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double strengthFactor = 0.75;

    public Config() {
      baseCost = 8;
      costFactor = 0.325;
      maxLevel = 7;
      initialCost = 12;
    }
  }
}
