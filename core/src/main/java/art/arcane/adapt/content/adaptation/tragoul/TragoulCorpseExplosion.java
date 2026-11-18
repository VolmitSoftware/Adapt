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

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.api.adaptation.EntityGuardPort;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TragoulCorpseExplosion extends SimpleAdaptation<TragoulCorpseExplosion.Config> {
  private static final int HARD_MAX_NOVA_TARGETS = 16;
  private static final int HARD_MAX_NOVA_CANDIDATES = 32;
  private static final int MAX_NOVAS_PER_TICK = 8;
  private static final int MAX_PENDING_NOVAS = 256;
  private static final double HARD_MAX_NOVA_RADIUS = 16D;
  private static final DataKey<Long> NOVA_KEY = DataKey.adapt("tragoul_nova_stamp", DataCodecs.LONG);
  private static final Rgb NOVA_CRIMSON = new Rgb(150, 12, 12);
  private static final Rgb NOVA_BONE = new Rgb(200, 190, 170);
  private final ConcurrentLinkedQueue<NovaPlan> pendingNovas = new ConcurrentLinkedQueue<>();
  private final AtomicInteger pendingNovaCount = new AtomicInteger();
  private final AtomicBoolean novaDrainScheduled = new AtomicBoolean();

  public TragoulCorpseExplosion() {
    super("tragoul-corpse-explosion");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("wither_rose"));
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wither_rose"))
        .key("challenge_tragoul_corpse_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherrack"))
            .key("challenge_tragoul_corpse_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_corpse_500", "tragoul.corpse-explosion.mobs-detonated", 500, 400);
    registerMilestone("challenge_tragoul_corpse_5k", "tragoul.corpse-explosion.mobs-detonated", 5000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TragoulPreferences.PASSIVE, TragoulPreferences.TARGETS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(TragoulMessages.CORPSE_EXPLOSION_LORE1));
    statLore(v, Form.f(getRadius(level), 1), 2);
    statLore(v, Form.pc(getVictimHealthFraction(level), 0), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    PlayerRef killer = e.entity().killer();
    if (killer == null || GolemKillCredit.isGolemKill(e.entity())) {
      return;
    }
    requestDetonation(killer, e.entity(), false);
  }

  static void detonateServantKill(TragoulCorpseExplosion nova, PlayerRef owner, LivingRef victim) {
    nova.requestDetonation(owner, victim, true);
  }

  private void requestDetonation(PlayerRef owner, LivingRef victim, boolean servant) {
    if (owner == null || victim == null) {
      return;
    }
    NovaSourceSnapshot source = captureSourceOwned(victim);
    if (source != null) {
      Tasks.runEntity(owner, () -> prepareNovaOwnerOwned(owner, source, servant));
    }
  }

  private NovaSourceSnapshot captureSourceOwned(LivingRef victim) {
    long now = System.currentTimeMillis();
    Long novaStamp = victim.data().get(NOVA_KEY);
    if (isSuppressed(novaStamp, now, getConfig().chainSuppressionMillis)) {
      return null;
    }

    double attributeMaxHealth = victim.attributeValue(Attributes.MAX_HEALTH);
    double maxHealth = Double.isNaN(attributeMaxHealth) ? 20D : attributeMaxHealth;
    ProtectionSnapshot protection = captureProtectionOwned(victim);
    return new NovaSourceSnapshot(victim.location(), maxHealth,
        victim instanceof PlayerRef, protection.protectedFriendly(), protection.tameOwnerId());
  }

  static boolean isSuppressed(Long novaStamp, long now, long suppressionMillis) {
    return novaStamp != null && now - novaStamp < Math.max(0L, suppressionMillis);
  }

  static boolean isNovaTarget(EntityRef entity) {
    return entity instanceof LivingRef && entity.has(EntityTrait.ENEMY);
  }

  private void prepareNovaOwnerOwned(PlayerRef owner, NovaSourceSnapshot source, boolean servant) {
    if (!owner.isOnline()) {
      return;
    }

    int level = getActiveLevel(owner);
    if (level <= 0 || !canDamageSnapshotOwned(owner, source.player(), source.protectedFriendly(),
        source.tameOwnerId(), source.location())) {
      return;
    }
    double radius = getRadius(level);
    double damage = getNovaDamage(level, source.maxHealth());
    int maxTargets = Math.min(HARD_MAX_NOVA_TARGETS, Math.max(1, getConfig().maxTargets));
    int candidateLimit = Math.min(HARD_MAX_NOVA_CANDIDATES, maxTargets * 2);
    NovaPlan plan = new NovaPlan(owner, source.location(), radius, damage, maxTargets, candidateLimit,
        getConfig().xpPerMobHit, servant);
    enqueueNova(plan);
  }

  private void enqueueNova(NovaPlan plan) {
    if (pendingNovaCount.incrementAndGet() > MAX_PENDING_NOVAS) {
      pendingNovaCount.decrementAndGet();
      return;
    }
    pendingNovas.add(plan);
    scheduleNovaDrain();
  }

  private void scheduleNovaDrain() {
    if (novaDrainScheduled.compareAndSet(false, true)) {
      Tasks.s(this::drainNovas, 1);
    }
  }

  private void drainNovas() {
    int drained = 0;
    while (drained < MAX_NOVAS_PER_TICK) {
      NovaPlan plan = pendingNovas.poll();
      if (plan == null) {
        break;
      }
      pendingNovaCount.decrementAndGet();
      Tasks.runAt(plan.source(), () -> collectCandidatesOwned(plan));
      drained++;
    }
    novaDrainScheduled.set(false);
    if (!pendingNovas.isEmpty()) {
      scheduleNovaDrain();
    }
  }

  private void collectCandidatesOwned(NovaPlan plan) {
    playNova(plan.source(), plan.radius(), plan.servant() ? NOVA_BONE : NOVA_CRIMSON);
    List<LivingRef> candidates = new ArrayList<>(plan.candidateLimit());
    Loc source = plan.source();
    double radius = plan.radius();
    for (EntityRef entity : source.world().nearbyEntities(source.position(), radius, radius, radius)) {
      if (!isNovaTarget(entity) || !AdaptationDamageTargets.allows(entity, getConfig().ignorePassiveMobs)) {
        continue;
      }
      candidates.add((LivingRef) entity);
      if (candidates.size() >= plan.candidateLimit()) {
        break;
      }
    }

    if (candidates.isEmpty()) {
      return;
    }

    NovaBatch batch = new NovaBatch(plan, candidates.size());
    for (LivingRef candidate : candidates) {
      if (!Tasks.runEntity(candidate, () -> batch.complete(captureTargetOwned(candidate)))) {
        batch.complete(null);
      }
    }
    batch.armTimeout();
  }

  private NovaTargetSnapshot captureTargetOwned(LivingRef target) {
    if (!target.isValid() || target.isDead()
        || !AdaptationDamageTargets.allows(target, getConfig().ignorePassiveMobs)) {
      return null;
    }
    Loc location = target.location();
    ProtectionSnapshot protection = captureProtectionOwned(target);
    return new NovaTargetSnapshot(target, location, protection.protectedFriendly(), protection.tameOwnerId(), target.type(), !AdaptationDamageTargets.allows(target, true));
  }

  private ProtectionSnapshot captureProtectionOwned(LivingRef target) {
    if (target.isInvulnerable() || AdaptPorts.get(EntityGuardPort.class, EntityGuardPort.NONE).isShielded(target)
        || MinionIdentity.isMinion(target)) {
      return new ProtectionSnapshot(true, null);
    }
    if (target.has(EntityTrait.TAMEABLE)) {
      UUID tamer = target.tameOwner();
      if (tamer != null) {
        return new ProtectionSnapshot(false, tamer);
      }
    }
    return new ProtectionSnapshot(false, null);
  }

  private void completeBatchOwnerOwned(NovaBatch batch) {
    NovaPlan plan = batch.plan();
    PlayerRef owner = plan.owner();
    if (!owner.isOnline() || getActiveLevel(owner) <= 0) {
      return;
    }

    List<NovaTargetSnapshot> selected = new ArrayList<>(plan.maxTargets());
    for (NovaTargetSnapshot target : batch.targets()) {
      if (preference(owner, TragoulPreferences.TARGETS).accepts(target.type())
          && (!preferenceEnabled(owner, TragoulPreferences.PASSIVE) || !target.passive())
          && canDamageSnapshotOwned(owner, false, target.protectedFriendly(), target.tameOwnerId(), target.location())) {
        selected.add(target);
        if (selected.size() >= plan.maxTargets()) {
          break;
        }
      }
    }
    if (selected.isEmpty()) {
      return;
    }

    long now = System.currentTimeMillis();
    NovaApplicationBatch application = new NovaApplicationBatch(plan, selected.size());
    for (NovaTargetSnapshot target : selected) {
      if (!Tasks.runEntity(target.entity(), () -> applyNovaTargetOwned(application, target, now))) {
        application.complete(false);
      }
    }
    application.armTimeout();
  }

  private void applyNovaTargetOwned(NovaApplicationBatch application, NovaTargetSnapshot target, long now) {
    LivingRef enemy = target.entity();
    if (!enemy.isValid() || enemy.isDead()
        || !AdaptationDamageTargets.allows(enemy, getConfig().ignorePassiveMobs)) {
      application.complete(false);
      return;
    }

    Long previousStamp = enemy.data().get(NOVA_KEY);
    int previousNoDamageTicks = enemy.noDamageTicks();
    double healthBefore = enemy.health() + enemy.absorption();
    enemy.data().set(NOVA_KEY, now);
    enemy.setNoDamageTicks(0);
    enemy.damage(application.plan().damage(), application.plan().owner());
    double healthAfter = enemy.isDead() ? 0D : enemy.health() + enemy.absorption();
    boolean successful = healthAfter < healthBefore;
    if (!successful) {
      enemy.setNoDamageTicks(previousNoDamageTicks);
      if (previousStamp == null) {
        enemy.data().remove(NOVA_KEY);
      } else {
        enemy.data().set(NOVA_KEY, previousStamp);
      }
      application.complete(false);
      return;
    }

    fx(enemy.location().add(0, 1.0D, 0), FxPriority.COMBAT)
        .particle(Particles.DAMAGE_INDICATOR, 2, 0, 0, 0, 0.2D, 0.02D);
    application.complete(true);
  }

  private void finishNovaApplicationOwnerOwned(NovaApplicationBatch application) {
    NovaPlan plan = application.plan();
    PlayerRef owner = plan.owner();
    int hits = application.successes();
    if (!owner.isOnline() || getActiveLevel(owner) <= 0 || hits <= 0) {
      return;
    }
    addStat(owner, "tragoul.corpse-explosion.mobs-detonated", hits);
    xp(owner, hits * plan.xpPerMobHit());
  }

  private boolean canDamageSnapshotOwned(PlayerRef owner, boolean player, boolean protectedFriendly,
                                         UUID tameOwnerId, Loc target) {
    if (protectedFriendly || owner.uuid().equals(tameOwnerId)) {
      return false;
    }
    return player ? canPVP(owner, target) : canPVE(owner, target);
  }

  private void playNova(Loc center, double radius, Rgb tint) {
    double inner = radius * 0.3;
    int ringPoints = (int) Math.max(12, Math.min(28, radius * 6));
    timeline(center)
        .duration(3)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(radius + 8)
        .frame((fx, tick, progress) -> {
          double r = inner + ((radius - inner) * progress);
          fx.dustRing(tint, r, ringPoints, 1.4F);
          if (tick == 0) {
            fx.particle(Particles.DAMAGE_INDICATOR, 8, 0, 1.0, 0, 0.4, 0.04);
            fx.column(Particles.SOUL, 8, 2.0);
            fx.chord(Sounds.ENTITY_GENERIC_EXPLODE, 0.55F, 1.35F, Sounds.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.5F, 0.8F);
          }
        })
        .start();
  }

  private double getRadius(int level) {
    double configured = Math.max(1D, getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor));
    return Math.min(HARD_MAX_NOVA_RADIUS, configured);
  }

  private double getVictimHealthFraction(int level) {
    return Math.max(0, getConfig().victimHealthFractionBase + (getLevelPercent(level) * getConfig().victimHealthFractionFactor));
  }

  private double getNovaDamage(int level, double victimMaxHealth) {
    double damage = getConfig().baseDamage + (victimMaxHealth * getVictimHealthFraction(level));
    return Math.min(getConfig().maxDamage, damage);
  }

  private record ProtectionSnapshot(boolean protectedFriendly, UUID tameOwnerId) {
  }

  private record NovaSourceSnapshot(Loc location, double maxHealth, boolean player,
                                    boolean protectedFriendly, UUID tameOwnerId) {
  }

  private record NovaTargetSnapshot(LivingRef entity, Loc location, boolean protectedFriendly,
                                    UUID tameOwnerId, Id type, boolean passive) {
  }

  private record NovaPlan(PlayerRef owner, Loc source, double radius, double damage,
                          int maxTargets, int candidateLimit, double xpPerMobHit, boolean servant) {
  }

  private final class NovaBatch {
    private final NovaPlan plan;
    private final ConcurrentLinkedQueue<NovaTargetSnapshot> targets = new ConcurrentLinkedQueue<>();
    private final AtomicInteger remaining;
    private final AtomicBoolean dispatched = new AtomicBoolean();

    private NovaBatch(NovaPlan plan, int remaining) {
      this.plan = plan;
      this.remaining = new AtomicInteger(remaining);
    }

    private NovaPlan plan() {
      return plan;
    }

    private ConcurrentLinkedQueue<NovaTargetSnapshot> targets() {
      return targets;
    }

    private void complete(NovaTargetSnapshot target) {
      if (target != null) {
        targets.add(target);
      }
      if (remaining.decrementAndGet() == 0) {
        dispatch();
      }
    }

    private void armTimeout() {
      Tasks.runEntity(plan.owner(), this::dispatch, 10);
    }

    private void dispatch() {
      if (dispatched.compareAndSet(false, true)) {
        Tasks.runEntity(plan.owner(), () -> completeBatchOwnerOwned(this));
      }
    }
  }

  private final class NovaApplicationBatch {
    private final NovaPlan plan;
    private final AtomicInteger remaining;
    private final AtomicInteger successes = new AtomicInteger();
    private final AtomicBoolean dispatched = new AtomicBoolean();

    private NovaApplicationBatch(NovaPlan plan, int remaining) {
      this.plan = plan;
      this.remaining = new AtomicInteger(remaining);
    }

    private NovaPlan plan() {
      return plan;
    }

    private int successes() {
      return successes.get();
    }

    private void complete(boolean successful) {
      if (successful) {
        successes.incrementAndGet();
      }
      if (remaining.decrementAndGet() == 0) {
        dispatch();
      }
    }

    private void armTimeout() {
      Tasks.runEntity(plan.owner(), this::dispatch, 10);
    }

    private void dispatch() {
      if (dispatched.compareAndSet(false, true)) {
        Tasks.runEntity(plan.owner(), () -> finishNovaApplicationOwnerOwned(this));
      }
    }
  }


  @ConfigDescription("Mobs you kill detonate in a blood nova that damages nearby hostile mobs.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Exclude passive and neutral mobs from corpse explosion damage, including servant-triggered explosions.", impact = "When enabled, neutral enemy species remain protected even when provoked.")
    boolean ignorePassiveMobs = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base nova radius before level scaling.", impact = "Higher values damage hostile mobs further from the corpse.")
    double radiusBase = 3.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional nova radius granted at max level.", impact = "Higher values increase the level-scaled radius growth.")
    double radiusFactor = 3.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Flat nova damage applied to every hostile mob hit.", impact = "Higher values increase the guaranteed damage portion of the nova.")
    double baseDamage = 3.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of the victim's max health added to nova damage before level scaling.", impact = "Higher values make tanky kills detonate harder.")
    double victimHealthFractionBase = 0.10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional victim max-health fraction granted at max level.", impact = "Higher values increase the level-scaled damage growth.")
    double victimHealthFractionFactor = 0.40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on nova damage per mob.", impact = "Prevents extreme bosses from producing one-shot novas.")
    double maxDamage = 24.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum hostile mobs damaged per nova, with a hard runtime ceiling of 16.", impact = "Caps per-kill work to protect server performance.")
    int maxTargets = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Window in milliseconds during which a nova-damaged mob cannot trigger another nova.", impact = "Prevents chain-reaction detonations.")
    long chainSuppressionMillis = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per hostile mob hit by a nova.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerMobHit = 6;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
