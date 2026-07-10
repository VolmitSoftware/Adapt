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
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class HerbalismBeeShepherd extends SimpleAdaptation<HerbalismBeeShepherd.Config> {
  private static final int PLAYER_CHECKS_PER_TICK = 32;
  private static final int GROWTH_SAMPLES_PER_TICK = 96;
  private static final int BEE_PULLS_PER_TICK = 8;
  private static final int COMPLETIONS_PER_TICK = 16;
  private static final int MAX_QUEUED_GROWTH_PULSES = 1024;
  private static final int MAX_QUEUED_BEE_PULLS = 2048;
  private static final int MAX_QUEUED_COMPLETIONS = 2048;
  private static final int MAX_BEES_PER_PULSE = 8;
  private static final long ROSTER_REFRESH_MILLIS = 5000L;
  private static final long IDLE_CHECK_MILLIS = 250L;
  private final Cooldowns pulseCooldown = cooldowns();
  private final Cooldowns auraHint = cooldowns();
  private final Map<UUID, Long> nextCheckAt = playerState();
  private final Queue<UUID> playerQueue = new ConcurrentLinkedQueue<>();
  private final Set<UUID> queuedPlayers = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingPulses = ConcurrentHashMap.newKeySet();
  private final Queue<GrowthPulse> growthPulses = new ConcurrentLinkedQueue<>();
  private final AtomicInteger queuedGrowthPulses = new AtomicInteger();
  private final Queue<BeePull> beePulls = new ConcurrentLinkedQueue<>();
  private final AtomicInteger queuedBeePulls = new AtomicInteger();
  private final Queue<GrowthPulse> completedPulses = new ConcurrentLinkedQueue<>();
  private final AtomicInteger queuedCompletions = new AtomicInteger();
  private volatile long nextRosterRefreshAt;

  public HerbalismBeeShepherd() {
    super("herbalism-bee-shepherd");
    registerConfiguration(Config.class);
    setIcon(Material.BEE_NEST);
    setInterval(10);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.HONEYCOMB)
        .key("challenge_herbalism_bee_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_herbalism_bee_100", "herbalism.bee-shepherd.bees-attracted", 100, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level)), 1);
    statLore(v, getGrowthAttempts(level), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getPulseMillis(level), 1), 3);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    refreshRoster(now);
    processGrowthSamples();
    processBeePulls();
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
    if (level <= 0 || !isHoldingFlower(p) || pendingPulses.contains(id)) {
      nextCheckAt.put(id, now + IDLE_CHECK_MILLIS);
      queuePlayer(id);
      return;
    }

    long pulseMillis = getPulseMillis(level);
    if (!pulseCooldown.isReady(id, pulseMillis)) {
      nextCheckAt.put(id, now + IDLE_CHECK_MILLIS);
      queuePlayer(id);
      return;
    }

    int foodCost = getFoodCost(level);
    if (p.getFoodLevel() < foodCost) {
      nextCheckAt.put(id, now + IDLE_CHECK_MILLIS);
      queuePlayer(id);
      return;
    }

    pendingPulses.add(id);
    pulseCooldown.mark(id);
    nextCheckAt.put(id, now + pulseMillis);
    startGrowthPulse(p, level, foodCost);
    int attracted = enqueueNearbyBees(p, level);
    if (attracted > 0) {
      addStat(p, "herbalism.bee-shepherd.bees-attracted", attracted);
    }

    if (auraHint.isReady(id, 8000L)) {
      auraHint.mark(id);
      fx(p.getLocation(), FxPriority.AMBIENT)
          .dustRing(Color.LIME, getRadius(level), 20, 1.0F)
          .sound(Sound.BLOCK_ROOTED_DIRT_PLACE, 0.4F, 1.1F);
    }
    queuePlayer(id);
  }

  private void startGrowthPulse(Player p, int level, int foodCost) {
    int radius = Math.max(1, (int) Math.round(getRadius(level)));
    int attempts = getGrowthAttempts(level);
    GrowthPulse pulse = new GrowthPulse(p, p.getLocation(), radius, getGrowthStep(level),
        getConfig().showGrowthParticles, attempts, foodCost, getConfig().xpPerGrowth);
    if (!offerGrowthPulse(pulse)) {
      pendingPulses.remove(p.getUniqueId());
    }
  }

  private boolean offerGrowthPulse(GrowthPulse pulse) {
    int queued = queuedGrowthPulses.incrementAndGet();
    if (queued > MAX_QUEUED_GROWTH_PULSES) {
      queuedGrowthPulses.decrementAndGet();
      return false;
    }
    growthPulses.add(pulse);
    return true;
  }

  private void processGrowthSamples() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int i = 0; i < GROWTH_SAMPLES_PER_TICK; i++) {
      GrowthPulse pulse = growthPulses.poll();
      if (pulse == null) {
        break;
      }
      queuedGrowthPulses.decrementAndGet();
      int dx = random.nextInt(-pulse.radius, pulse.radius + 1);
      int dz = random.nextInt(-pulse.radius, pulse.radius + 1);
      int dy = random.nextInt(-1, 2);
      Location center = pulse.center;
      Location target = new Location(center.getWorld(), center.getBlockX() + dx, center.getBlockY() + dy, center.getBlockZ() + dz);
      GrowthSample sample = new GrowthSample(target, pulse.growthStep, pulse.showParticles, pulse);
      if (--pulse.samplesToDispatch > 0 && !offerGrowthPulse(pulse)) {
        int skipped = pulse.samplesToDispatch;
        pulse.samplesToDispatch = 0;
        for (int j = 0; j < skipped; j++) {
          completeGrowthSample(pulse, false);
        }
      }
      if (!J.runAt(sample.location, () -> growSample(sample))) {
        completeGrowthSample(pulse, false);
      }
    }
  }

  private void growSample(GrowthSample sample) {
    Block block = sample.location.getBlock();
    if (!(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() >= ageable.getMaximumAge()) {
      completeGrowthSample(sample.pulse, false);
      return;
    }

    ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + sample.growthStep));
    block.setBlockData(ageable, true);
    if (sample.showParticles && ageable.getAge() >= ageable.getMaximumAge()
        && sample.pulse.emittedParticles.getAndIncrement() < 6) {
      fx(block.getLocation().add(0.5, 1.0, 0.5), FxPriority.AMBIENT)
          .particle(Particle.HAPPY_VILLAGER, 2, 0, 0, 0, 0.1D, 0.01D)
          .particle(Particle.COMPOSTER, 1, 0, 0.1D, 0, 0.1D, 0.01D);
    }
    completeGrowthSample(sample.pulse, true);
  }

  private void completeGrowthSample(GrowthPulse pulse, boolean grown) {
    if (grown) {
      pulse.grown.incrementAndGet();
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
    Player p = pulse.player;
    pendingPulses.remove(p.getUniqueId());
    if (!p.isOnline()) {
      return;
    }
    int grown = pulse.grown.get();
    if (grown <= 0) {
      return;
    }

    p.setFoodLevel(Math.max(0, p.getFoodLevel() - pulse.foodCost));
    fx(p.getLocation().add(0, 1, 0), FxPriority.AMBIENT)
        .ring(Particle.HAPPY_VILLAGER, 0.9D, 8, 0.2D)
        .particle(Particle.COMPOSTER, 2, 0, 0.3D, 0, 0.2D, 0.02D)
        .chord(Sound.ENTITY_BEE_POLLINATE, 0.85F, 1.25F, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.25F, 1.6F);
    xp(p, grown * pulse.xpPerGrowth);
  }

  private int enqueueNearbyBees(Player p, int level) {
    double radius = getRadius(level);
    int count = 0;
    Vector target = p.getLocation().add(0, 0.75, 0).toVector();
    for (Entity entity : p.getNearbyEntities(radius, radius, radius)) {
      if (!(entity instanceof Bee bee)) {
        continue;
      }
      if (!offerBeePull(new BeePull(bee, target.clone(), getBeePullStrength(level), count < 4))) {
        break;
      }
      if (++count >= MAX_BEES_PER_PULSE) {
        break;
      }
    }
    return count;
  }

  private boolean offerBeePull(BeePull pull) {
    int queued = queuedBeePulls.incrementAndGet();
    if (queued > MAX_QUEUED_BEE_PULLS) {
      queuedBeePulls.decrementAndGet();
      return false;
    }
    beePulls.add(pull);
    return true;
  }

  private void processBeePulls() {
    int attempts = workFor(queuedBeePulls.get(), BEE_PULLS_PER_TICK);
    for (int i = 0; i < attempts; i++) {
      BeePull pull = beePulls.poll();
      if (pull == null) {
        break;
      }
      queuedBeePulls.decrementAndGet();
      J.runEntity(pull.bee, () -> applyBeePull(pull));
    }
  }

  private void applyBeePull(BeePull pull) {
    if (!pull.bee.isValid()) {
      return;
    }
    Vector toward = pull.target.clone().subtract(pull.bee.getLocation().toVector());
    if (toward.lengthSquared() <= 0.001D) {
      return;
    }
    if (pull.showTrail) {
      fx(pull.bee.getLocation(), FxPriority.TRAIL)
          .trail(Particle.HAPPY_VILLAGER, toward.getX(), toward.getY(), toward.getZ(), Math.min(3.0D, toward.length()), 3);
    }
    toward.normalize().multiply(pull.strength);
    pull.bee.setVelocity(pull.bee.getVelocity().multiply(0.6D).add(toward));
    pull.bee.setTarget(null);
  }

  private void queuePlayer(UUID id) {
    if (queuedPlayers.add(id)) {
      playerQueue.add(id);
    }
  }

  static int workFor(int queued, int limit) {
    return Math.min(Math.max(0, queued), Math.max(0, limit));
  }

  private boolean isHoldingFlower(Player p) {
    return isFlower(p.getInventory().getItemInMainHand()) || isFlower(p.getInventory().getItemInOffHand());
  }

  private boolean isFlower(ItemStack item) {
    if (!isItem(item)) {
      return false;
    }

    Material type = item.getType();
    return type.name().endsWith("_TULIP")
        || type == Material.DANDELION
        || type == Material.POPPY
        || type == Material.BLUE_ORCHID
        || type == Material.ALLIUM
        || type == Material.AZURE_BLUET
        || type == Material.OXEYE_DAISY
        || type == Material.CORNFLOWER
        || type == Material.LILY_OF_THE_VALLEY
        || type == Material.WITHER_ROSE
        || type == Material.SUNFLOWER
        || type == Material.LILAC
        || type == Material.ROSE_BUSH
        || type == Material.PEONY
        || type == Material.TORCHFLOWER
        || type == Material.PINK_PETALS;
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private int getGrowthAttempts(int level) {
    return Math.max(1, (int) Math.round(getConfig().growthAttemptsBase + (getLevelPercent(level) * getConfig().growthAttemptsFactor)));
  }

  private int getGrowthStep(int level) {
    return Math.max(1, (int) Math.round(getConfig().growthStepBase + (getLevelPercent(level) * getConfig().growthStepFactor)));
  }

  private int getFoodCost(int level) {
    return Math.max(1, (int) Math.round(getConfig().foodCostBase - (getLevelPercent(level) * getConfig().foodCostFactor)));
  }

  private long getPulseMillis(int level) {
    return Math.max(250L, (long) Math.round(getConfig().pulseMillisBase - (getLevelPercent(level) * getConfig().pulseMillisFactor)));
  }

  private double getBeePullStrength(int level) {
    return Math.max(0.01, getConfig().beePullStrengthBase + (getLevelPercent(level) * getConfig().beePullStrengthFactor));
  }

  private static final class GrowthPulse {
    private final Player player;
    private final Location center;
    private final int radius;
    private final int growthStep;
    private final boolean showParticles;
    private final AtomicInteger remaining;
    private final AtomicInteger grown = new AtomicInteger();
    private final AtomicInteger emittedParticles = new AtomicInteger();
    private final int foodCost;
    private final double xpPerGrowth;
    private int samplesToDispatch;

    private GrowthPulse(Player player, Location center, int radius, int growthStep, boolean showParticles,
                        int attempts, int foodCost, double xpPerGrowth) {
      this.player = player;
      this.center = center;
      this.radius = radius;
      this.growthStep = growthStep;
      this.showParticles = showParticles;
      this.remaining = new AtomicInteger(attempts);
      this.foodCost = foodCost;
      this.xpPerGrowth = xpPerGrowth;
      this.samplesToDispatch = attempts;
    }
  }

  private static final class GrowthSample {
    private final Location location;
    private final int growthStep;
    private final boolean showParticles;
    private final GrowthPulse pulse;

    private GrowthSample(Location location, int growthStep, boolean showParticles, GrowthPulse pulse) {
      this.location = location;
      this.growthStep = growthStep;
      this.showParticles = showParticles;
      this.pulse = pulse;
    }
  }

  private static final class BeePull {
    private final Bee bee;
    private final Vector target;
    private final double strength;
    private final boolean showTrail;

    private BeePull(Bee bee, Vector target, double strength, boolean showTrail) {
      this.bee = bee;
      this.target = target;
      this.strength = strength;
      this.showTrail = showTrail;
    }
  }

  @ConfigDescription("Holding flowers near crops emits growth pulses and draws nearby bees toward you.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Growth Particles for the Herbalism Bee Shepherd adaptation.", impact = "True enables this behavior and false disables it.")
    boolean showGrowthParticles = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Growth Attempts Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthAttemptsBase = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Growth Attempts Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthAttemptsFactor = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Growth Step Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthStepBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Growth Step Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthStepFactor = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Food Cost Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double foodCostBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Food Cost Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double foodCostFactor = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pulse Millis Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pulseMillisBase = 900;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pulse Millis Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pulseMillisFactor = 650;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bee Pull Strength Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double beePullStrengthBase = 0.07;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bee Pull Strength Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double beePullStrengthFactor = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Growth for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerGrowth = 0.9;

    public Config() {
      baseCost = 3;
      costFactor = 0.64;
      initialCost = 3;
    }
  }
}
