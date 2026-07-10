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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

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
  private static final NamespacedKey NOVA_KEY = NamespacedKey.fromString("adapt:tragoul_nova_stamp");
  private static final Color NOVA_CRIMSON = Color.fromRGB(150, 12, 12);
  private static final Color NOVA_BONE = Color.fromRGB(200, 190, 170);
  private final ConcurrentLinkedQueue<NovaPlan> pendingNovas = new ConcurrentLinkedQueue<>();
  private final AtomicInteger pendingNovaCount = new AtomicInteger();
  private final AtomicBoolean novaDrainScheduled = new AtomicBoolean();

  public TragoulCorpseExplosion() {
    super("tragoul-corpse-explosion");
    registerConfiguration(Config.class);
    setIcon(Material.WITHER_ROSE);
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WITHER_ROSE)
        .key("challenge_tragoul_corpse_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERRACK)
            .key("challenge_tragoul_corpse_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_tragoul_corpse_500", "tragoul.corpse-explosion.mobs-detonated", 500, 400);
    registerMilestone("challenge_tragoul_corpse_5k", "tragoul.corpse-explosion.mobs-detonated", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("tragoul.corpse_explosion.lore1"));
    v.addLore(C.GREEN + "+ " + Form.f(getRadius(level), 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.corpse_explosion.lore2"));
    v.addLore(C.GREEN + "+ " + Form.pc(getVictimHealthFraction(level), 0) + C.GRAY + " " + Localizer.dLocalize("tragoul.corpse_explosion.lore3"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    Player killer = e.getEntity().getKiller();
    if (killer == null) {
      return;
    }
    requestDetonation(killer, e.getEntity(), false);
  }

  static void detonateServantKill(TragoulCorpseExplosion nova, Player owner, LivingEntity victim) {
    nova.requestDetonation(owner, victim, true);
  }

  private void requestDetonation(Player owner, LivingEntity victim, boolean servant) {
    if (owner == null || victim == null) {
      return;
    }
    J.runEntity(victim, () -> prepareSourceOwned(owner, victim, servant));
  }

  private void prepareSourceOwned(Player owner, LivingEntity victim, boolean servant) {
    long now = System.currentTimeMillis();
    Long novaStamp = victim.getPersistentDataContainer().get(NOVA_KEY, PersistentDataType.LONG);
    if (novaStamp != null && now - novaStamp < getConfig().chainSuppressionMillis) {
      return;
    }

    IAttribute attribute = Version.get().getAttribute(victim, Attributes.GENERIC_MAX_HEALTH);
    double maxHealth = attribute == null ? 20D : attribute.getValue();
    ProtectionSnapshot protection = captureProtectionOwned(victim);
    NovaSourceSnapshot source = new NovaSourceSnapshot(victim.getLocation(), maxHealth,
        victim instanceof Player, protection.protectedFriendly(), protection.tameOwnerId());
    J.runEntity(owner, () -> prepareNovaOwnerOwned(owner, source, servant));
  }

  private void prepareNovaOwnerOwned(Player owner, NovaSourceSnapshot source, boolean servant) {
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
      J.s(this::drainNovas, 1);
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
      J.runAt(plan.source(), () -> collectCandidatesOwned(plan));
      drained++;
    }
    novaDrainScheduled.set(false);
    if (!pendingNovas.isEmpty()) {
      scheduleNovaDrain();
    }
  }

  private void collectCandidatesOwned(NovaPlan plan) {
    List<Monster> candidates = new ArrayList<>(plan.candidateLimit());
    Location source = plan.source();
    double radius = plan.radius();
    for (Entity entity : source.getWorld().getNearbyEntities(source, radius, radius, radius)) {
      if (entity instanceof Monster monster) {
        candidates.add(monster);
        if (candidates.size() >= plan.candidateLimit()) {
          break;
        }
      }
    }

    if (candidates.isEmpty()) {
      return;
    }

    NovaBatch batch = new NovaBatch(plan, candidates.size());
    for (Monster candidate : candidates) {
      if (!J.runEntity(candidate, () -> batch.complete(captureTargetOwned(candidate)))) {
        batch.complete(null);
      }
    }
    batch.armTimeout();
  }

  private NovaTargetSnapshot captureTargetOwned(Monster target) {
    if (!target.isValid() || target.isDead()) {
      return null;
    }
    Location location = target.getLocation();
    ProtectionSnapshot protection = captureProtectionOwned(target);
    return new NovaTargetSnapshot(target, location, protection.protectedFriendly(), protection.tameOwnerId());
  }

  private ProtectionSnapshot captureProtectionOwned(LivingEntity target) {
    if (target instanceof ArmorStand stand && stand.isMarker()) {
      return new ProtectionSnapshot(true, null);
    }
    if (target.isInvulnerable() || target.hasMetadata("NPC") || TragoulSkeletalServant.isServant(target)) {
      return new ProtectionSnapshot(true, null);
    }
    if (target instanceof Tameable tameable && tameable.isTamed()) {
      AnimalTamer tamer = tameable.getOwner();
      return new ProtectionSnapshot(false, tamer == null ? null : tamer.getUniqueId());
    }
    return new ProtectionSnapshot(false, null);
  }

  private void completeBatchOwnerOwned(NovaBatch batch) {
    NovaPlan plan = batch.plan();
    Player owner = plan.owner();
    if (!owner.isOnline() || getActiveLevel(owner) <= 0) {
      return;
    }

    List<NovaTargetSnapshot> selected = new ArrayList<>(plan.maxTargets());
    for (NovaTargetSnapshot target : batch.targets()) {
      if (canDamageSnapshotOwned(owner, false, target.protectedFriendly(), target.tameOwnerId(), target.location())) {
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
    for (NovaTargetSnapshot target : selected) {
      J.runEntity(target.entity(), () -> applyNovaTargetOwned(owner, target, plan.damage(), now));
    }
    playNova(plan.source(), plan.radius(), plan.servant() ? NOVA_BONE : NOVA_CRIMSON);
    addStat(owner, "tragoul.corpse-explosion.mobs-detonated", selected.size());
    xp(owner, selected.size() * plan.xpPerMobHit());
  }

  private void applyNovaTargetOwned(Player owner, NovaTargetSnapshot target, double damage, long now) {
    Monster monster = target.entity();
    if (!monster.isValid() || monster.isDead()) {
      return;
    }
    monster.getPersistentDataContainer().set(NOVA_KEY, PersistentDataType.LONG, now);
    monster.damage(damage, owner);
    fx(monster.getLocation().add(0, 1.0D, 0), FxPriority.COMBAT)
        .particle(Particle.DAMAGE_INDICATOR, 2, 0, 0, 0, 0.2D, 0.02D);
  }

  private boolean canDamageSnapshotOwned(Player owner, boolean player, boolean protectedFriendly,
                                         UUID tameOwnerId, Location target) {
    if (protectedFriendly || owner.getUniqueId().equals(tameOwnerId)) {
      return false;
    }
    return player ? canPVP(owner, target) : canPVE(owner, target);
  }

  private void playNova(Location center, double radius, Color tint) {
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
            fx.particle(Particle.DAMAGE_INDICATOR, 8, 0, 1.0, 0, 0.4, 0.04);
            fx.column(Particle.SOUL, 8, 2.0);
            fx.chord(Sound.ENTITY_GENERIC_EXPLODE, 0.55F, 1.35F, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.5F, 0.8F);
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

  private record NovaSourceSnapshot(Location location, double maxHealth, boolean player,
                                    boolean protectedFriendly, UUID tameOwnerId) {
  }

  private record NovaTargetSnapshot(Monster entity, Location location, boolean protectedFriendly,
                                    UUID tameOwnerId) {
  }

  private record NovaPlan(Player owner, Location source, double radius, double damage,
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
      J.runEntity(plan.owner(), this::dispatch, 10);
    }

    private void dispatch() {
      if (dispatched.compareAndSet(false, true)) {
        J.runEntity(plan.owner(), () -> completeBatchOwnerOwned(this));
      }
    }
  }


  @ConfigDescription("Mobs you kill detonate in a blood nova that damages nearby hostile mobs.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base nova radius before level scaling.", impact = "Higher values damage hostile mobs further from the corpse.")
    double radiusBase = 3.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional nova radius granted at max level.", impact = "Higher values increase the level-scaled radius growth.")
    double radiusFactor = 3.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Flat nova damage applied to every hostile mob hit.", impact = "Higher values increase the guaranteed damage portion of the nova.")
    double baseDamage = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of the victim's max health added to nova damage before level scaling.", impact = "Higher values make tanky kills detonate harder.")
    double victimHealthFractionBase = 0.10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional victim max-health fraction granted at max level.", impact = "Higher values increase the level-scaled damage growth.")
    double victimHealthFractionFactor = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on nova damage per mob.", impact = "Prevents extreme bosses from producing one-shot novas.")
    double maxDamage = 16.0;
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
