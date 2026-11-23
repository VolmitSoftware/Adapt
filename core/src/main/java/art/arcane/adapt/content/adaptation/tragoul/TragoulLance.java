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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
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
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class TragoulLance extends SimpleAdaptation<TragoulLance.Config> {
  private static final Rgb LANCE_MAROON = new Rgb(128, 0, 0);
  private static final ParticleData.Dust LANCE_TIP = new ParticleData.Dust(new Rgb(235, 20, 35), 1.35F);
  private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
  private static final double HARD_MAX_RANGE = 32D;
  private static final int HARD_MAX_CHAIN_HITS = 6;
  private static final int MAX_CANDIDATES_PER_SEARCH = 24;
  private static final int MAX_CANDIDATE_HANDOFFS = 8;
  private static final int MAX_SEARCHES_PER_WINDOW = 32;
  private static final int MAX_PENDING_CHAINS = 256;
  private static final double IMPACT_MOVE_TOLERANCE_SQUARED = 6.25D;
  private static final long WORK_WINDOW_MILLIS = 50L;
  private static final long CHAIN_TIMEOUT_MILLIS = 30_000L;
  private static final long COOLDOWN_MILLIS = 5000L;
  private final Cooldowns cooldowns = cooldowns();
  private final LanceAdmission admission = new LanceAdmission(MAX_PENDING_CHAINS);
  private final LanceWorkBudget workBudget = new LanceWorkBudget(MAX_SEARCHES_PER_WINDOW, WORK_WINDOW_MILLIS);
  private volatile boolean acceptingChains = true;

  public TragoulLance() {
    super("tragoul-lance");
    registerConfiguration(TragoulLance.Config.class);
    setIcon(Id.minecraft("trident"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_tragoul_lance_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond_sword"))
        .key("challenge_tragoul_lance_kills_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_tragoul_lance_200", "tragoul.lance.lances-spawned", 200, 400);
    registerMilestone("challenge_tragoul_lance_kills_100", "tragoul.lance.lance-kills", 100, 1000);
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @Override
  public void unregister() {
    acceptingChains = false;
    admission.clear();
    super.unregister();
  }

  @Handle(priority = EventPriority.LOWEST)
  public void onEntityDeath(EntityDeathEvent event) {
    LivingRef victim = event.entity();
    LastDamage damage = victim.lastDamage();
    if (damage == null || !damage.byEntity()) {
      return;
    }
    PlayerRef owner = resolvePlayerDamager(damage.damager());
    if (owner == null) {
      return;
    }

    Loc source = victim.location();
    UUID tameOwnerId = null;
    if (victim.has(EntityTrait.TAMEABLE)) {
      tameOwnerId = victim.tameOwner();
    }
    SourceSnapshot snapshot = new SourceSnapshot(
        source,
        victim instanceof PlayerRef,
        isProtectedFriendly(null, victim),
        tameOwnerId,
        Math.max(0D, damage.finalDamage())
    );
    Tasks.runEntity(owner, () -> prepareChainOwnerOwned(owner, snapshot));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    admission.cancel(event.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent event) {
    admission.cancel(event.player().uuid());
  }

  private void prepareChainOwnerOwned(PlayerRef owner, SourceSnapshot source) {
    if (!acceptingChains || !isOwnerEligible(owner)) {
      return;
    }

    int level = getActiveLevel(owner);
    UUID ownerId = owner.uuid();
    if (level <= 0 || !cooldowns.isReady(ownerId, COOLDOWN_MILLIS)
        || !canDamageSnapshotOwned(owner, source.player(), source.protectedFriendly(), source.tameOwnerId(), source.location())) {
      return;
    }

    long token = admission.admit(ownerId, System.currentTimeMillis(), CHAIN_TIMEOUT_MILLIS);
    if (token < 0L) {
      return;
    }

    int remainingHits = Math.max(1, Math.min(HARD_MAX_CHAIN_HITS, level));
    double range = Math.max(1D, Math.min(HARD_MAX_RANGE, 5D + (4D * level)));
    boolean unarmored = isUnarmored(armorContents(owner));
    double damage = lanceDamage(source.damage(), getSeekerDamageMultiplier(),
        getUnarmoredDamageMultiplier(), unarmored);
    if (damage <= 0D) {
      admission.complete(ownerId, token);
      return;
    }

    LanceChain chain = new LanceChain(
        owner,
        ownerId,
        token,
        range,
        getSelfDamage(level),
        getSeekerDelay()
    );
    requestSearchOwnerOwned(chain, source.location(), damage, remainingHits);
  }

  private static ItemRef[] armorContents(PlayerRef owner) {
    ItemRef[] contents = new ItemRef[ARMOR_SLOTS.length];
    for (int index = 0; index < ARMOR_SLOTS.length; index++) {
      contents[index] = owner.inventory().armor(ARMOR_SLOTS[index]);
    }
    return contents;
  }

  private void requestSearchOwnerOwned(LanceChain chain, Loc source, double damage, int remainingHits) {
    if (!isCurrent(chain) || !isOwnerEligible(chain.owner()) || remainingHits <= 0
        || !workBudget.trySearch(System.currentTimeMillis())) {
      admission.complete(chain.ownerId(), chain.token());
      return;
    }

    SearchPass pass = new SearchPass(chain, source, damage, remainingHits);
    if (!Tasks.runAt(source, () -> collectCandidatesRegionOwned(pass))) {
      admission.complete(chain.ownerId(), chain.token());
    }
  }

  private void collectCandidatesRegionOwned(SearchPass pass) {
    if (!isCurrent(pass.chain())) {
      return;
    }

    Loc source = pass.source();
    Collection<EntityRef> nearby = source.world().nearbyEntities(
        source.position(),
        pass.chain().range(),
        pass.chain().range(),
        pass.chain().range()
    );
    List<LivingRef> candidates = new ArrayList<>(MAX_CANDIDATE_HANDOFFS);
    int examined = 0;
    for (EntityRef entity : nearby) {
      if (examined++ >= MAX_CANDIDATES_PER_SEARCH) {
        break;
      }
      if (entity instanceof LivingRef candidate
          && AdaptationDamageTargets.allows(candidate, getConfig().ignorePassiveMobs)
          && !isCaster(pass.chain().ownerId(), candidate.uuid())) {
        candidates.add(candidate);
        if (candidates.size() >= MAX_CANDIDATE_HANDOFFS) {
          break;
        }
      }
    }

    if (candidates.isEmpty()) {
      finishChain(pass.chain());
      return;
    }

    CandidateBatch batch = new CandidateBatch(pass, candidates.size());
    for (LivingRef candidate : candidates) {
      if (!Tasks.runEntity(candidate, () -> batch.complete(captureTargetOwned(pass.chain().ownerId(), candidate)))) {
        batch.complete(null);
      }
    }
    batch.armTimeout();
  }

  private TargetSnapshot captureTargetOwned(UUID ownerId, LivingRef target) {
    if (!target.isValid() || target.isDead()
        || !AdaptationDamageTargets.allows(target, getConfig().ignorePassiveMobs)) {
      return null;
    }

    UUID targetId = target.uuid();
    if (isCaster(ownerId, targetId)) {
      return null;
    }
    boolean protectedFriendly = isProtectedFriendly(null, target);
    if (!protectedFriendly && target.has(EntityTrait.TAMEABLE)) {
      protectedFriendly = ownerId.equals(target.tameOwner());
    }
    return new TargetSnapshot(
        target,
        targetId,
        target.location(),
        target instanceof PlayerRef,
        protectedFriendly, target.type(), !AdaptationDamageTargets.allows(target, true)
    );
  }

  private void selectTargetOwnerOwned(CandidateBatch batch) {
    SearchPass pass = batch.pass();
    LanceChain chain = pass.chain();
    if (!isCurrent(chain) || !isOwnerEligible(chain.owner()) || getActiveLevel(chain.owner()) <= 0) {
      finishChain(chain);
      return;
    }

    TargetSnapshot selected = null;
    double bestDistanceSquared = chain.range() * chain.range();
    for (TargetSnapshot candidate : batch.targets()) {
      if (!preference(chain.owner(), TragoulPreferences.TARGETS).accepts(candidate.type())
          || (preferenceEnabled(chain.owner(), TragoulPreferences.PASSIVE) && candidate.passive())
          || !candidate.location().world().equals(pass.source().world())
          || isCaster(chain.ownerId(), candidate.entityId())
          || chain.hitIds().contains(candidate.entityId()) || !canDamageSnapshotOwned(
          chain.owner(), candidate.player(), candidate.protectedFriendly(), null, candidate.location())) {
        continue;
      }
      double distanceSquared = pass.source().distanceSquared(candidate.location());
      if (distanceSquared <= bestDistanceSquared) {
        selected = candidate;
        bestDistanceSquared = distanceSquared;
      }
    }

    if (selected == null) {
      finishChain(chain);
      return;
    }

    chain.hitIds().add(selected.entityId());
    playLanceLaunch(pass.source());
    playLanceTravel(pass.source(), selected.location(), chain.delayTicks());
    TargetSnapshot target = selected;
    if (!Tasks.runEntity(target.entity(), () -> prepareImpactTargetOwned(pass, target), chain.delayTicks())) {
      finishChain(chain);
    }
  }

  private void prepareImpactTargetOwned(SearchPass pass, TargetSnapshot snapshot) {
    LanceChain chain = pass.chain();
    if (!isCurrent(chain)) {
      return;
    }

    TargetSnapshot current = captureTargetOwned(chain.ownerId(), snapshot.entity());
    if (current == null || isCaster(chain.ownerId(), current.entityId()) || current.protectedFriendly()
        || !current.entityId().equals(snapshot.entityId())
        || !current.location().world().equals(pass.source().world())
        || current.location().distanceSquared(pass.source()) > chain.range() * chain.range()) {
      finishChain(chain);
      return;
    }

    if (!Tasks.runEntity(chain.owner(), () -> validateImpactPolicyOwnerOwned(pass, current))) {
      finishChain(chain);
    }
  }

  private void validateImpactPolicyOwnerOwned(SearchPass pass, TargetSnapshot approved) {
    LanceChain chain = pass.chain();
    if (!isCurrent(chain) || !isOwnerEligible(chain.owner())
        || isCaster(chain.ownerId(), approved.entityId()) || !canDamageSnapshotOwned(
        chain.owner(), approved.player(), approved.protectedFriendly(), null, approved.location())) {
      finishChain(chain);
      return;
    }

    if (!Tasks.runEntity(approved.entity(), () -> applyTargetOwned(pass, approved))) {
      finishChain(chain);
    }
  }

  private void applyTargetOwned(SearchPass pass, TargetSnapshot approved) {
    LanceChain chain = pass.chain();
    if (!isCurrent(chain)) {
      return;
    }

    TargetSnapshot current = captureTargetOwned(chain.ownerId(), approved.entity());
    if (current == null || isCaster(chain.ownerId(), current.entityId()) || current.protectedFriendly()
        || !current.entityId().equals(approved.entityId())
        || !current.location().world().equals(approved.location().world())
        || current.location().distanceSquared(approved.location()) > IMPACT_MOVE_TOLERANCE_SQUARED) {
      finishChain(chain);
      return;
    }

    LivingRef target = current.entity();
    double healthBefore = target.health() + target.absorption();
    target.damage(pass.damage(), chain.owner());
    double healthAfter = target.isDead() ? 0D : target.health() + target.absorption();
    boolean successful = healthAfter < healthBefore;
    boolean killed = target.isDead();
    Loc impact = target.location();
    if (successful) {
      int generation = chain.hitIds().size() - 1;
      float pitch = (float) Math.min(2.0, 1.0 + (generation * 0.1));
      fx(target, FxPriority.COMBAT)
          .burst(Particles.CRIT, 8, 0.4)
          .dustBurst(LANCE_MAROON, 6, 0.4, 1.0F)
          .chord(Sounds.ITEM_TRIDENT_HIT, 0.7F, pitch, Sounds.ENTITY_ARROW_HIT, 0.4F, 0.7F);
    }

    if (!Tasks.runEntity(chain.owner(), () -> completeHitOwnerOwned(pass, impact, successful, killed))) {
      admission.complete(chain.ownerId(), chain.token());
    }
  }

  private void completeHitOwnerOwned(SearchPass pass, Loc impact, boolean successful, boolean killed) {
    LanceChain chain = pass.chain();
    PlayerRef owner = chain.owner();
    if (!isCurrent(chain) || !isOwnerEligible(owner) || !successful) {
      finishChain(chain);
      return;
    }

    if (chain.activated().compareAndSet(false, true)) {
      cooldowns.mark(chain.ownerId(), COOLDOWN_MILLIS);
    }
    if (killed) {
      addStat(owner, "tragoul.lance.lance-kills", 1);
    }
    addStat(owner, "tragoul.lance.lances-spawned", 1);

    double selfDamage = chain.selfDamage();
    if (selfDamage > 0D && owner.isValid() && !owner.isDead()) {
      applyPlayerDamage(owner, selfDamage);
    }

    if (canContinueChain(owner, killed, pass.remainingHits())) {
      requestSearchOwnerOwned(chain, impact, pass.damage() * 0.5D, pass.remainingHits() - 1);
    } else {
      finishChain(chain);
    }
  }

  static PlayerRef resolvePlayerDamager(EntityRef damager) {
    if (damager instanceof PlayerRef player) {
      return player;
    }
    if (damager instanceof ProjectileRef projectile && projectile.shooter() instanceof PlayerRef player) {
      return player;
    }
    return null;
  }

  static boolean isCaster(UUID ownerId, UUID targetId) {
    return ownerId.equals(targetId);
  }

  static boolean isOwnerEligible(PlayerRef owner) {
    return owner != null && owner.isOnline() && owner.isValid() && !owner.isDead();
  }

  static boolean canContinueChain(PlayerRef owner, boolean killed, int remainingHits) {
    return killed && remainingHits > 1 && isOwnerEligible(owner);
  }

  private boolean canDamageSnapshotOwned(PlayerRef owner, boolean player, boolean protectedFriendly,
                                         UUID tameOwnerId, Loc target) {
    if (protectedFriendly || owner.uuid().equals(tameOwnerId)) {
      return false;
    }
    return player ? canPVP(owner, target) : canPVE(owner, target);
  }

  private boolean isCurrent(LanceChain chain) {
    return acceptingChains && admission.isCurrent(chain.ownerId(), chain.token(), System.currentTimeMillis());
  }

  private void finishChain(LanceChain chain) {
    admission.complete(chain.ownerId(), chain.token());
  }

  private int getSeekerDelay() {
    return Math.max(1, Math.min(40, getConfig().seekerDelay));
  }

  private double getSeekerDamageMultiplier() {
    double configured = getConfig().seekerDamageMultiplier;
    return Double.isFinite(configured) ? Math.max(0D, Math.min(4D, configured)) : 0D;
  }

  private double getSelfDamage(int level) {
    return selfDamageForLevel(
        level,
        getMaxLevel(),
        getConfig().selfDamageAtFirstLevel,
        getConfig().selfDamageAtMaxLevel
    );
  }

  private double getUnarmoredDamageMultiplier() {
    double configured = getConfig().unarmoredDamageMultiplier;
    return Double.isFinite(configured) ? Math.max(1D, Math.min(10D, configured)) : 1D;
  }

  static boolean isUnarmored(ItemRef[] armorContents) {
    if (armorContents == null) {
      return true;
    }
    for (ItemRef armor : armorContents) {
      if (armor != null && !armor.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  static double lanceDamage(double killingDamage, double baseMultiplier,
                            double unarmoredMultiplier, boolean unarmored) {
    if (!Double.isFinite(killingDamage) || !Double.isFinite(baseMultiplier)
        || !Double.isFinite(unarmoredMultiplier)) {
      return 0D;
    }
    double armorMultiplier = unarmored ? Math.max(1D, unarmoredMultiplier) : 1D;
    return Math.max(0D, killingDamage) * Math.max(0D, baseMultiplier) * armorMultiplier;
  }

  static double selfDamageForLevel(int level, int maxLevel, double firstLevelDamage,
                                   double maxLevelDamage) {
    if (level <= 0 || maxLevel <= 0 || !Double.isFinite(firstLevelDamage)
        || !Double.isFinite(maxLevelDamage)) {
      return 0D;
    }

    double first = Math.max(0D, firstLevelDamage);
    double last = Math.max(0D, Math.min(first, maxLevelDamage));
    if (maxLevel == 1) {
      return first;
    }

    int clampedLevel = Math.max(1, Math.min(maxLevel, level));
    double progress = (clampedLevel - 1D) / (maxLevel - 1D);
    return first + ((last - first) * progress);
  }

  private void playLanceLaunch(Loc origin) {
    fx(origin.add(0, 1.0, 0), FxPriority.COMBAT)
        .burst(Particles.SCULK_SOUL, 6, 0.3)
        .chord(Sounds.ITEM_TRIDENT_RIPTIDE_1, 0.6F, 1.3F, Sounds.ENTITY_WITHER_SHOOT, 0.3F, 1.8F);
  }

  private void playLanceTravel(Loc origin, Loc target, int delayTicks) {
    Loc start = origin.add(0, 1.0, 0);
    double dx = target.x() - start.x();
    double dy = (target.y() + 1.0) - start.y();
    double dz = target.z() - start.z();
    timeline(start)
        .duration(Math.max(4, delayTicks))
        .priority(FxPriority.TRAIL)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          fx.particle(Particles.DUST, 2, dx * progress, dy * progress, dz * progress, 0.025D, 0D, LANCE_TIP);
          double tail = Math.max(0D, progress - 0.08D);
          fx.particle(Particles.END_ROD, 2, dx * tail, dy * tail, dz * tail, 0.02D, 0D);
        })
        .start();
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TragoulPreferences.PASSIVE, TragoulPreferences.TARGETS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(TragoulMessages.LANCE_LORE1));
    v.addLore(C.YELLOW + AdaptLanguage.text(TragoulMessages.LANCE_LORE2));
    v.addLore(C.YELLOW + AdaptLanguage.text(
        TragoulMessages.LANCE_MAX,
        trusted("level", level)
    ));
  }

  @ConfigDescription("Killing an enemy spawns a lance that seeks and damages a nearby enemy.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from every seeking lance and chain hit.", impact = "When enabled, lances skip protected mobs and can seek hostile mobs farther away. Direct attacks and player targeting are unchanged.")
    boolean ignorePassiveMobs = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Seeker Delay for the Tragoul Lance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int seekerDelay = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Seeker Damage Multiplier for the Tragoul Lance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double seekerDamageMultiplier = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Flat damage dealt to the caster when a level-one lance hits.", impact = "Higher values make early corpse lances cost more after absorption, armor, and effects.")
    double selfDamageAtFirstLevel = 6.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Flat damage dealt to the caster when a max-level lance hits.", impact = "Lower values make leveling reduce the mitigated corpse lance cost further.")
    double selfDamageAtMaxLevel = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Damage multiplier applied when the owner has no armor equipped.", impact = "Higher values reward fighting without any equipped armor.")
    double unarmoredDamageMultiplier = 3.0;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }

  private record SourceSnapshot(Loc location, boolean player, boolean protectedFriendly,
                                UUID tameOwnerId, double damage) {
  }

  private record TargetSnapshot(LivingRef entity, UUID entityId, Loc location,
                                boolean player, boolean protectedFriendly, Id type, boolean passive) {
  }

  private record SearchPass(LanceChain chain, Loc source, double damage, int remainingHits) {
  }

  private record LanceChain(PlayerRef owner, UUID ownerId, long token, double range,
                            double selfDamage, int delayTicks,
                            Set<UUID> hitIds, AtomicBoolean activated) {
    private LanceChain(PlayerRef owner, UUID ownerId, long token, double range,
                       double selfDamage, int delayTicks) {
      this(owner, ownerId, token, range, selfDamage, delayTicks,
          ConcurrentHashMap.newKeySet(), new AtomicBoolean());
    }
  }

  private final class CandidateBatch {
    private final SearchPass pass;
    private final ConcurrentLinkedQueue<TargetSnapshot> targets = new ConcurrentLinkedQueue<>();
    private final AtomicInteger remaining;
    private final AtomicBoolean dispatched = new AtomicBoolean();

    private CandidateBatch(SearchPass pass, int remaining) {
      this.pass = pass;
      this.remaining = new AtomicInteger(remaining);
    }

    private SearchPass pass() {
      return pass;
    }

    private ConcurrentLinkedQueue<TargetSnapshot> targets() {
      return targets;
    }

    private void complete(TargetSnapshot target) {
      if (target != null) {
        targets.add(target);
      }
      if (remaining.decrementAndGet() == 0) {
        dispatch();
      }
    }

    private void armTimeout() {
      if (!Tasks.runEntity(pass.chain().owner(), this::dispatch, 10)) {
        finishChain(pass.chain());
      }
    }

    private void dispatch() {
      if (!dispatched.compareAndSet(false, true)) {
        return;
      }
      if (!Tasks.runEntity(pass.chain().owner(), () -> selectTargetOwnerOwned(this))) {
        finishChain(pass.chain());
      }
    }
  }

  static final class LanceWorkBudget {
    private final int searchLimit;
    private final long windowMillis;
    private long windowStartedAt = Long.MIN_VALUE;
    private int searches;

    LanceWorkBudget(int searchLimit, long windowMillis) {
      this.searchLimit = Math.max(1, searchLimit);
      this.windowMillis = Math.max(1L, windowMillis);
    }

    synchronized boolean trySearch(long now) {
      if (windowStartedAt == Long.MIN_VALUE || now < windowStartedAt || now - windowStartedAt >= windowMillis) {
        windowStartedAt = now;
        searches = 0;
      }
      if (searches >= searchLimit) {
        return false;
      }
      searches++;
      return true;
    }
  }

  static final class LanceAdmission {
    private final int capacity;
    private final Map<UUID, PendingChain> pending = new HashMap<>();
    private long nextToken;

    LanceAdmission(int capacity) {
      this.capacity = Math.max(1, capacity);
    }

    synchronized long admit(UUID ownerId, long now, long timeoutMillis) {
      PendingChain existing = pending.get(ownerId);
      if (existing != null && existing.expiresAt() > now) {
        return -1L;
      }
      if (existing != null) {
        pending.remove(ownerId);
      }
      if (pending.size() >= capacity) {
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        if (pending.size() >= capacity) {
          return -1L;
        }
      }

      long token = ++nextToken;
      pending.put(ownerId, new PendingChain(token, deadline(now, timeoutMillis)));
      return token;
    }

    synchronized boolean isCurrent(UUID ownerId, long token, long now) {
      PendingChain chain = pending.get(ownerId);
      if (chain == null || chain.token() != token) {
        return false;
      }
      if (chain.expiresAt() <= now) {
        pending.remove(ownerId);
        return false;
      }
      return true;
    }

    synchronized boolean complete(UUID ownerId, long token) {
      PendingChain chain = pending.get(ownerId);
      if (chain == null || chain.token() != token) {
        return false;
      }
      pending.remove(ownerId);
      return true;
    }

    synchronized void cancel(UUID ownerId) {
      pending.remove(ownerId);
    }

    synchronized int size() {
      return pending.size();
    }

    synchronized void clear() {
      pending.clear();
    }

    private static long deadline(long now, long timeoutMillis) {
      long timeout = Math.max(1L, timeoutMillis);
      return now > Long.MAX_VALUE - timeout ? Long.MAX_VALUE : now + timeout;
    }

    private record PendingChain(long token, long expiresAt) {
    }
  }
}
