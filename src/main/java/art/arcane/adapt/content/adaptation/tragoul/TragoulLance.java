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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

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
  private static final Color LANCE_MAROON = Color.fromRGB(128, 0, 0);
  private static final Particle.DustOptions LANCE_TIP = new Particle.DustOptions(Color.fromRGB(235, 20, 35), 1.35F);
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
    setIcon(Material.TRIDENT);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_tragoul_lance_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_SWORD)
        .key("challenge_tragoul_lance_kills_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
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

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onEntityDeath(EntityDeathEvent event) {
    LivingEntity victim = event.getEntity();
    if (!(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent damage)) {
      return;
    }
    Player owner = resolvePlayerDamager(damage.getDamager());
    if (owner == null) {
      return;
    }

    Location source = victim.getLocation().clone();
    UUID tameOwnerId = null;
    if (victim instanceof Tameable tameable && tameable.isTamed()) {
      tameOwnerId = PaperCompat.tamedOwnerId(tameable);
    }
    SourceSnapshot snapshot = new SourceSnapshot(
        source,
        victim instanceof Player,
        isProtectedFriendly(null, victim),
        tameOwnerId,
        Math.max(0D, damage.getFinalDamage())
    );
    J.runEntity(owner, () -> prepareChainOwnerOwned(owner, snapshot));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    admission.cancel(event.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent event) {
    admission.cancel(event.getEntity().getUniqueId());
  }

  private void prepareChainOwnerOwned(Player owner, SourceSnapshot source) {
    if (!acceptingChains || !isOwnerEligible(owner)) {
      return;
    }

    int level = getActiveLevel(owner);
    UUID ownerId = owner.getUniqueId();
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
    boolean unarmored = isUnarmored(owner.getInventory().getArmorContents());
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

  private void requestSearchOwnerOwned(LanceChain chain, Location source, double damage, int remainingHits) {
    if (!isCurrent(chain) || !isOwnerEligible(chain.owner()) || remainingHits <= 0
        || !workBudget.trySearch(System.currentTimeMillis())) {
      admission.complete(chain.ownerId(), chain.token());
      return;
    }

    SearchPass pass = new SearchPass(chain, source.clone(), damage, remainingHits);
    if (!J.runAt(source, () -> collectCandidatesRegionOwned(pass))) {
      admission.complete(chain.ownerId(), chain.token());
    }
  }

  private void collectCandidatesRegionOwned(SearchPass pass) {
    if (!isCurrent(pass.chain())) {
      return;
    }

    Location source = pass.source();
    Collection<Entity> nearby = source.getWorld().getNearbyEntities(
        source,
        pass.chain().range(),
        pass.chain().range(),
        pass.chain().range()
    );
    List<LivingEntity> candidates = new ArrayList<>(MAX_CANDIDATE_HANDOFFS);
    int examined = 0;
    for (Entity entity : nearby) {
      if (examined++ >= MAX_CANDIDATES_PER_SEARCH) {
        break;
      }
      if (entity instanceof LivingEntity candidate
          && !isCaster(pass.chain().ownerId(), candidate.getUniqueId())) {
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
    for (LivingEntity candidate : candidates) {
      if (!J.runEntity(candidate, () -> batch.complete(captureTargetOwned(pass.chain().ownerId(), candidate)))) {
        batch.complete(null);
      }
    }
    batch.armTimeout();
  }

  private TargetSnapshot captureTargetOwned(UUID ownerId, LivingEntity target) {
    if (!target.isValid() || target.isDead()) {
      return null;
    }

    UUID targetId = target.getUniqueId();
    if (isCaster(ownerId, targetId)) {
      return null;
    }
    boolean protectedFriendly = isProtectedFriendly(null, target);
    if (!protectedFriendly && target instanceof Tameable tameable && tameable.isTamed()) {
      protectedFriendly = ownerId.equals(PaperCompat.tamedOwnerId(tameable));
    }
    return new TargetSnapshot(
        target,
        targetId,
        target.getLocation().clone(),
        target instanceof Player,
        protectedFriendly
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
      if (candidate.location().getWorld() != pass.source().getWorld()
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
    if (!J.runEntity(target.entity(), () -> prepareImpactTargetOwned(pass, target), chain.delayTicks())) {
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
        || current.location().getWorld() != pass.source().getWorld()
        || current.location().distanceSquared(pass.source()) > chain.range() * chain.range()) {
      finishChain(chain);
      return;
    }

    if (!J.runEntity(chain.owner(), () -> validateImpactPolicyOwnerOwned(pass, current))) {
      finishChain(chain);
    }
  }

  private void validateImpactPolicyOwnerOwned(SearchPass pass, TargetSnapshot approved) {
    LanceChain chain = pass.chain();
    if (!isCurrent(chain) || !isOwnerEligible(chain.owner())
        || isCaster(chain.ownerId(), approved.entityId()) || !canDamageSnapshotOwned(
        chain.owner(), approved.player(), approved.protectedFriendly(), null, approved.location().clone())) {
      finishChain(chain);
      return;
    }

    if (!J.runEntity(approved.entity(), () -> applyTargetOwned(pass, approved))) {
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
        || current.location().getWorld() != approved.location().getWorld()
        || current.location().distanceSquared(approved.location()) > IMPACT_MOVE_TOLERANCE_SQUARED) {
      finishChain(chain);
      return;
    }

    LivingEntity target = current.entity();
    double healthBefore = target.getHealth() + target.getAbsorptionAmount();
    target.damage(pass.damage(), chain.owner());
    double healthAfter = target.isDead() ? 0D : target.getHealth() + target.getAbsorptionAmount();
    boolean successful = healthAfter < healthBefore;
    boolean killed = target.isDead();
    Location impact = target.getLocation().clone();
    if (successful) {
      int generation = chain.hitIds().size() - 1;
      float pitch = (float) Math.min(2.0, 1.0 + (generation * 0.1));
      fx(target, FxPriority.COMBAT)
          .burst(Particle.CRIT, 8, 0.4)
          .dustBurst(LANCE_MAROON, 6, 0.4, 1.0F)
          .chord(Sound.ITEM_TRIDENT_HIT, 0.7F, pitch, Sound.ENTITY_ARROW_HIT, 0.4F, 0.7F);
    }

    if (!J.runEntity(chain.owner(), () -> completeHitOwnerOwned(pass, impact, successful, killed))) {
      admission.complete(chain.ownerId(), chain.token());
    }
  }

  private void completeHitOwnerOwned(SearchPass pass, Location impact, boolean successful, boolean killed) {
    LanceChain chain = pass.chain();
    Player owner = chain.owner();
    if (!isCurrent(chain) || !isOwnerEligible(owner) || !successful) {
      finishChain(chain);
      return;
    }

    if (chain.activated().compareAndSet(false, true)) {
      cooldowns.mark(chain.ownerId());
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

  static Player resolvePlayerDamager(Entity damager) {
    if (damager instanceof Player player) {
      return player;
    }
    if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
      return player;
    }
    return null;
  }

  static boolean isCaster(UUID ownerId, UUID targetId) {
    return ownerId.equals(targetId);
  }

  static boolean isOwnerEligible(Player owner) {
    return owner != null && owner.isOnline() && owner.isValid() && !owner.isDead();
  }

  static boolean canContinueChain(Player owner, boolean killed, int remainingHits) {
    return killed && remainingHits > 1 && isOwnerEligible(owner);
  }

  private boolean canDamageSnapshotOwned(Player owner, boolean player, boolean protectedFriendly,
                                         UUID tameOwnerId, Location target) {
    if (protectedFriendly || owner.getUniqueId().equals(tameOwnerId)) {
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

  static boolean isUnarmored(ItemStack[] armorContents) {
    if (armorContents == null) {
      return true;
    }
    for (ItemStack armor : armorContents) {
      if (armor != null && armor.getType() != Material.AIR) {
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

  private void playLanceLaunch(Location origin) {
    fx(origin.clone().add(0, 1.0, 0), FxPriority.COMBAT)
        .burst(Particle.SCULK_SOUL, 6, 0.3)
        .chord(Sound.ITEM_TRIDENT_RIPTIDE_1, 0.6F, 1.3F, Sound.ENTITY_WITHER_SHOOT, 0.3F, 1.8F);
  }

  private void playLanceTravel(Location origin, Location target, int delayTicks) {
    Location start = origin.clone().add(0, 1.0, 0);
    double dx = target.getX() - start.getX();
    double dy = (target.getY() + 1.0) - start.getY();
    double dz = target.getZ() - start.getZ();
    timeline(start)
        .duration(Math.max(4, delayTicks))
        .priority(FxPriority.TRAIL)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          fx.particle(Particle.DUST, 2, dx * progress, dy * progress, dz * progress, 0.025D, 0D, LANCE_TIP);
          double tail = Math.max(0D, progress - 0.08D);
          fx.particle(Particle.END_ROD, 2, dx * tail, dy * tail, dz * tail, 0.02D, 0D);
        })
        .start();
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(TragoulMessages.LANCE_LORE1));
    v.addLore(C.YELLOW + AdaptLanguage.text(TragoulMessages.LANCE_LORE2));
    v.addLore(C.YELLOW + AdaptLanguage.text(
        TragoulMessages.LANCE_MAX,
        trusted("level", level)
    ));
  }

  @ConfigDescription("Killing an enemy spawns a lance that seeks and damages a nearby enemy.")
  protected static class Config extends AdaptationConfig {
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
      costFactor = 0.72;
      initialCost = 4;
    }
  }

  private record SourceSnapshot(Location location, boolean player, boolean protectedFriendly,
                                UUID tameOwnerId, double damage) {
  }

  private record TargetSnapshot(LivingEntity entity, UUID entityId, Location location,
                                boolean player, boolean protectedFriendly) {
  }

  private record SearchPass(LanceChain chain, Location source, double damage, int remainingHits) {
  }

  private record LanceChain(Player owner, UUID ownerId, long token, double range,
                            double selfDamage, int delayTicks,
                            Set<UUID> hitIds, AtomicBoolean activated) {
    private LanceChain(Player owner, UUID ownerId, long token, double range,
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
      if (!J.runEntity(pass.chain().owner(), this::dispatch, 10)) {
        finishChain(pass.chain());
      }
    }

    private void dispatch() {
      if (!dispatched.compareAndSet(false, true)) {
        return;
      }
      if (!J.runEntity(pass.chain().owner(), () -> selectTargetOwnerOwned(this))) {
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
