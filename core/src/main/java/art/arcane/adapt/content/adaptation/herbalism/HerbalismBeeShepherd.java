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

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.HerbalismMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class HerbalismBeeShepherd extends SimpleAdaptation<HerbalismBeeShepherd.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> PARTICLES = CommonPreferences.toggle("growth-particles", HerbalismMessages.PREFERENCE_HERBALISMBEESHEPHERD_PARTICLES, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> BEES = CommonPreferences.toggle("bee-attraction", HerbalismMessages.PREFERENCE_HERBALISMBEESHEPHERD_BEES, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> GROWTH = CommonPreferences.toggle("growth", HerbalismMessages.PREFERENCE_HERBALISMBEESHEPHERD_GROWTH, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FOOD_RESERVE = CommonPreferences.toggle("food-reserve", HerbalismMessages.PREFERENCE_HERBALISMBEESHEPHERD_FOOD_RESERVE, CommonPreferences.Toggle.OFF);

  private static final Rgb LIME = new Rgb(0, 255, 0);
  private static final Id BEE = Id.minecraft("bee");
  private static final Set<Id> FLOWERS = Set.of(Id.minecraft("dandelion"), Id.minecraft("poppy"),
      Id.minecraft("blue_orchid"), Id.minecraft("allium"), Id.minecraft("azure_bluet"), Id.minecraft("oxeye_daisy"),
      Id.minecraft("cornflower"), Id.minecraft("lily_of_the_valley"), Id.minecraft("wither_rose"),
      Id.minecraft("sunflower"), Id.minecraft("lilac"), Id.minecraft("rose_bush"), Id.minecraft("peony"),
      Id.minecraft("torchflower"), Id.minecraft("pink_petals"));
  private static final int PLAYER_CHECKS_PER_TICK = 32;
  private static final int GROWTH_SAMPLES_PER_TICK = 96;
  private static final int BEE_PULLS_PER_TICK = 8;
  private static final int COMPLETIONS_PER_TICK = 16;
  private static final int MAX_QUEUED_GROWTH_PULSES = 1024;
  private static final int MAX_QUEUED_BEE_PULLS = 2048;
  private static final int MAX_QUEUED_COMPLETIONS = 2048;
  private static final int MAX_BEES_PER_PULSE = 8;
  private static final int MAX_TRACKED_BEES_PER_PLAYER = 256;
  private static final int COST_UNPAID = 0;
  private static final int COST_CHARGING = 1;
  private static final int COST_PAID = 2;
  private static final int COST_DENIED = 3;
  private static final long ROSTER_REFRESH_MILLIS = 5000L;
  private static final long IDLE_CHECK_MILLIS = 250L;
  private static final long BEE_STAT_TTL_MILLIS = 60_000L;
  private final Cooldowns pulseCooldown = cooldowns();
  private final Cooldowns auraHint = cooldowns();
  private final Map<UUID, Long> nextCheckAt = playerState();
  private final Map<UUID, Map<UUID, Long>> countedBees = playerState();
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
    setIcon(Id.minecraft("bee_nest"));
    setInterval(10);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("honeycomb"))
        .key("challenge_herbalism_bee_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_herbalism_bee_100", "herbalism.bee-shepherd.bees-attracted", 100, 300);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, PARTICLES, BEES, GROWTH, FOOD_RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRadius(level)), 1);
    statLore(v, getGrowthAttempts(level), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getPulseMillis(level), 1), 3);
    statLore(v, Form.pc(getConfig().growthBonusPerBee, 0), 4);
  }

  @Override
  public boolean hasTickDemand() {
    return super.hasTickDemand()
        || !playerQueue.isEmpty()
        || !queuedPlayers.isEmpty()
        || !pendingPulses.isEmpty()
        || queuedGrowthPulses.get() > 0
        || !growthPulses.isEmpty()
        || queuedBeePulls.get() > 0
        || !beePulls.isEmpty()
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
    processGrowthSamples();
    processBeePulls();
    processCompletions();
    processPlayers(now);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    nextCheckAt.remove(id);
    pendingPulses.remove(id);
    countedBees.remove(id);
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
      PlayerRef p = adaptPlayer.getPlayer();
      if (p != null) {
        queuePlayer(p.uuid());
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
      PlayerRef p = AdaptCore.platform().host().player(id);
      if (p == null) {
        nextCheckAt.remove(id);
        pendingPulses.remove(id);
        continue;
      }
      withPlayerThread(p, () -> evaluatePlayer(p));
    }
  }

  private void evaluatePlayer(PlayerRef p) {
    UUID id = p.uuid();
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
    if (p.foodLevel() - foodCost < (preferenceEnabled(p, FOOD_RESERVE) ? 8 : 0)) {
      nextCheckAt.put(id, now + IDLE_CHECK_MILLIS);
      queuePlayer(id);
      return;
    }

    pendingPulses.add(id);
    nextCheckAt.put(id, now + pulseMillis);
    BeeHerd herd = preferenceEnabled(p, BEES) ? enqueueNearbyBees(p, level, now) : new BeeHerd(0, 0);
    if (herd.fresh() > 0) {
      addStat(p, "herbalism.bee-shepherd.bees-attracted", herd.fresh());
    }
    if (preferenceEnabled(p, GROWTH)) {
      startGrowthPulse(p, level, foodCost, herd.herded());
    } else {
      pendingPulses.remove(id);
      pulseCooldown.mark(id);
    }

    if (auraHint.isReady(id, 8000L)) {
      auraHint.mark(id);
      fx(p.location(), FxPriority.AMBIENT)
          .dustRing(LIME, getRadius(level), 20, 1.0F)
          .sound(Sounds.BLOCK_ROOTED_DIRT_PLACE, 0.4F, 1.1F);
    }
    queuePlayer(id);
  }

  private void startGrowthPulse(PlayerRef p, int level, int foodCost, int herdedBees) {
    int radius = Math.max(1, (int) Math.round(getRadius(level)));
    int attempts = growthAttemptsWithBees(getGrowthAttempts(level), herdedBees,
        getConfig().maxBonusBees, getConfig().growthBonusPerBee);
    GrowthPulse pulse = new GrowthPulse(p, p.location(), radius, getGrowthStep(level),
        getConfig().showGrowthParticles && preferenceEnabled(p, PARTICLES), attempts, foodCost, getConfig().xpPerGrowth);
    if (!offerGrowthPulse(pulse)) {
      pendingPulses.remove(p.uuid());
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
      Loc center = pulse.center;
      Loc target = new Loc(center.world(), center.blockX() + dx, center.blockY() + dy, center.blockZ() + dz);
      GrowthSample sample = new GrowthSample(target, pulse.growthStep, pulse.showParticles, pulse);
      if (--pulse.samplesToDispatch > 0 && !offerGrowthPulse(pulse)) {
        int skipped = pulse.samplesToDispatch;
        pulse.samplesToDispatch = 0;
        for (int j = 0; j < skipped; j++) {
          completeGrowthSample(pulse, false);
        }
      }
      if (!Tasks.runAt(sample.location, () -> growSample(sample))) {
        completeGrowthSample(pulse, false);
      }
    }
  }

  private void growSample(GrowthSample sample) {
    PlayerRef player = sample.pulse.player;
    if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(player)) || !player.isOnline()) {
      completeGrowthSample(sample.pulse, false);
      return;
    }
    if (sample.pulse.costState.get() == COST_DENIED) {
      completeGrowthSample(sample.pulse, false);
      return;
    }

    BlockRef block = sample.location.world().blockAt(sample.location);
    BlockStateRef ageable = block.state();
    if (AdaptConfig.get().isGrowthBlacklisted(block.type())
        || ageable.age() < 0
        || ageable.age() >= ageable.maxAge()
        || !canInteract(player, block.location())
        || !canBlockPlace(player, block.location())
        || !ProtectionProbes.attemptBlockUse(player, block)
        || !ProtectionProbes.attemptBlockPlaceProbe(player, block)) {
      completeGrowthSample(sample.pulse, false);
      return;
    }

    BlockStateRef current = block.state();
    if (current.age() < 0
        || current.age() >= current.maxAge()) {
      completeGrowthSample(sample.pulse, false);
      return;
    }
    if (!chargeGrowthPulse(sample.pulse)) {
      completeGrowthSample(sample.pulse, false);
      return;
    }
    BlockStateRef currentAfterCharge = block.state();
    if (currentAfterCharge.age() < 0
        || currentAfterCharge.age() >= currentAfterCharge.maxAge()) {
      completeGrowthSample(sample.pulse, false);
      return;
    }
    int grownAge = Math.min(currentAfterCharge.maxAge(), currentAfterCharge.age() + sample.growthStep);
    block.setState(currentAfterCharge.with("age", Integer.toString(grownAge)), true);
    if (sample.showParticles && grownAge >= currentAfterCharge.maxAge()
        && sample.pulse.emittedParticles.getAndIncrement() < 6) {
      fx(block.location().add(0.5, 1.0, 0.5), FxPriority.AMBIENT)
          .particle(Particles.HAPPY_VILLAGER, 2, 0, 0, 0, 0.1D, 0.01D)
          .particle(Particles.COMPOSTER, 1, 0, 0.1D, 0, 0.1D, 0.01D);
    }
    completeGrowthSample(sample.pulse, true);
  }

  private boolean chargeGrowthPulse(GrowthPulse pulse) {
    int state = pulse.costState.get();
    if (state == COST_PAID) {
      return true;
    }
    if (state != COST_UNPAID || !pulse.costState.compareAndSet(COST_UNPAID, COST_CHARGING)) {
      return pulse.costState.get() == COST_PAID;
    }

    boolean paid = false;
    try {
      PlayerRef player = pulse.player;
      paid = payHungerCost(player, "hunger", pulse.foodCost, () -> {
        if (player.foodLevel() - pulse.foodCost < (preferenceEnabled(player, FOOD_RESERVE) ? 8 : 0)) {
          return false;
        }
        player.setFoodLevel(Math.max(0, player.foodLevel() - pulse.foodCost));
        return true;
      });
      if (paid) {
        pulseCooldown.mark(player.uuid());
      }
      return paid;
    } finally {
      pulse.costState.set(paid ? COST_PAID : COST_DENIED);
    }
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
      pendingPulses.remove(pulse.player.uuid());
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
      if (!Tasks.runEntity(pulse.player, () -> finishGrowthPulse(pulse))) {
        pendingPulses.remove(pulse.player.uuid());
      }
    }
  }

  private void finishGrowthPulse(GrowthPulse pulse) {
    PlayerRef p = pulse.player;
    pendingPulses.remove(p.uuid());
    if (!p.isOnline()) {
      return;
    }
    int grown = pulse.grown.get();
    if (grown <= 0) {
      return;
    }

    fx(p.location().add(0, 1, 0), FxPriority.AMBIENT)
        .ring(Particles.HAPPY_VILLAGER, 0.9D, 8, 0.2D)
        .particle(Particles.COMPOSTER, 2, 0, 0.3D, 0, 0.2D, 0.02D)
        .chord(Sounds.ENTITY_BEE_POLLINATE, 0.85F, 1.25F, Sounds.BLOCK_NOTE_BLOCK_CHIME, 0.25F, 1.6F);
    xp(p, grown * pulse.xpPerGrowth);
  }

  private BeeHerd enqueueNearbyBees(PlayerRef p, int level, long now) {
    double radius = getRadius(level);
    Loc center = p.location();
    if (Tasks.isFoliaThreading()
        && !Tasks.isOwnedByCurrentRegion(center, radius, radius)) {
      return new BeeHerd(0, 0);
    }
    Map<UUID, Long> seen = countedBees.computeIfAbsent(p.uuid(), k -> new ConcurrentHashMap<>());
    evictExpiredBees(seen, now);
    int count = 0;
    int fresh = 0;
    Vec3 target = center.add(0, 0.75, 0).position();
    for (EntityRef entity : p.nearbyEntities(radius, radius, radius)) {
      if (!(entity instanceof MobRef bee) || !BEE.equals(entity.type())) {
        continue;
      }
      if (!offerBeePull(new BeePull(bee, target, getBeePullStrength(level), count < 4))) {
        break;
      }
      if (seen.put(bee.uuid(), now + BEE_STAT_TTL_MILLIS) == null) {
        fresh++;
      }
      if (++count >= MAX_BEES_PER_PULSE) {
        break;
      }
    }
    return new BeeHerd(count, fresh);
  }

  private void evictExpiredBees(Map<UUID, Long> seen, long now) {
    if (seen.isEmpty()) {
      return;
    }

    if (seen.size() > MAX_TRACKED_BEES_PER_PLAYER) {
      seen.clear();
      return;
    }

    seen.values().removeIf(expiresAt -> expiresAt == null || expiresAt <= now);
  }

  static int growthAttemptsWithBees(int baseAttempts, int bees, int maxBonusBees, double bonusPerBee) {
    if (baseAttempts <= 0) {
      return 0;
    }

    if (bees <= 0 || maxBonusBees <= 0 || !Double.isFinite(bonusPerBee) || bonusPerBee <= 0D) {
      return baseAttempts;
    }

    int counted = Math.min(bees, maxBonusBees);
    return Math.max(baseAttempts, (int) Math.round(baseAttempts * (1D + (counted * bonusPerBee))));
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
      Tasks.runEntity(pull.bee, () -> applyBeePull(pull));
    }
  }

  private void applyBeePull(BeePull pull) {
    if (!pull.bee.isValid()) {
      return;
    }
    Vec3 toward = pull.target.subtract(pull.bee.location().position());
    if (toward.lengthSquared() <= 0.001D) {
      return;
    }
    if (pull.showTrail) {
      fx(pull.bee.location(), FxPriority.TRAIL)
          .trail(Particles.HAPPY_VILLAGER, toward.x(), toward.y(), toward.z(), Math.min(3.0D, toward.length()), 3);
    }
    toward = toward.normalize().multiply(pull.strength);
    pull.bee.setVelocity(pull.bee.velocity().multiply(0.6D).add(toward));
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

  private boolean isHoldingFlower(PlayerRef p) {
    return isFlower(p.inventory().mainHand()) || isFlower(p.inventory().offHand());
  }

  private boolean isFlower(ItemRef item) {
    if (!isItem(item)) {
      return false;
    }

    Id type = item.type();
    return type.legacyName().endsWith("_TULIP") || FLOWERS.contains(type);
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
    private final PlayerRef player;
    private final Loc center;
    private final int radius;
    private final int growthStep;
    private final boolean showParticles;
    private final AtomicInteger remaining;
    private final AtomicInteger grown = new AtomicInteger();
    private final AtomicInteger emittedParticles = new AtomicInteger();
    private final AtomicInteger costState = new AtomicInteger(COST_UNPAID);
    private final int foodCost;
    private final double xpPerGrowth;
    private int samplesToDispatch;

    private GrowthPulse(PlayerRef player, Loc center, int radius, int growthStep, boolean showParticles,
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
    private final Loc location;
    private final int growthStep;
    private final boolean showParticles;
    private final GrowthPulse pulse;

    private GrowthSample(Loc location, int growthStep, boolean showParticles, GrowthPulse pulse) {
      this.location = location;
      this.growthStep = growthStep;
      this.showParticles = showParticles;
      this.pulse = pulse;
    }
  }

  private record BeeHerd(int herded, int fresh) {
  }

  private static final class BeePull {
    private final MobRef bee;
    private final Vec3 target;
    private final double strength;
    private final boolean showTrail;

    private BeePull(MobRef bee, Vec3 target, double strength, boolean showTrail) {
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra growth attempts granted per herded bee, as a fraction of the base attempts.", impact = "Higher values make herding bees matter much more for each growth pulse.")
    double growthBonusPerBee = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum number of herded bees that count toward the growth bonus.", impact = "Higher values let large swarms keep increasing the growth bonus.")
    int maxBonusBees = 5;
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
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
