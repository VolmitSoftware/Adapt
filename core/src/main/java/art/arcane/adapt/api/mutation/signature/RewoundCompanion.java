package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.content.adaptation.taming.TamePets;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chronos with Taming, replacing Convergence: a pet's killing blow is undone by rewinding it a few seconds, to where it
 * stood and the health it had, once per pet per cooldown. Its past is read from the hits it took, so idle pets cost
 * nothing.
 */
public final class RewoundCompanion extends Signature<RewoundCompanion.Config> {
  private static final int MAX_MARKS = 16;
  private static final int MAX_TRACKED_PETS = 256;
  private final Map<UUID, Deque<Mark>> marks = new ConcurrentHashMap<>();
  private final Map<UUID, Long> rewoundAt = new ConcurrentHashMap<>();
  private final Map<UUID, Mark> pending = new ConcurrentHashMap<>();

  public RewoundCompanion(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("lead"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Form.duration(getConfig().rewindMillis, 0), 1);
    statLore(v, Form.duration(petCooldownMillis(level), 0), 2);
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return 0L;
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof LivingRef pet) || pet instanceof PlayerRef || pet.tameOwner() == null) {
      return;
    }
    PlayerRef owner = owner(pet);
    if (owner == null || getActiveLevel(owner) <= 0) {
      return;
    }
    long now = System.currentTimeMillis();
    Mark before = new Mark(pet.location(), pet.health(), now);
    if (pet.health() - e.finalDamage() > 0D) {
      remember(pet.uuid(), before, now);
      return;
    }
    Mark back = rewindTarget(pet.uuid(), before, now);
    pending.put(pet.uuid(), back);
    try {
      if (fire(owner, pet, back.at(), false)) {
        e.setCancelled(true);
      }
    } finally {
      pending.remove(pet.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    marks.remove(e.entity().uuid());
  }

  @Override
  protected Outcome run(BlockUse use) {
    LivingRef pet = use.target();
    Mark back = pet == null ? null : pending.get(pet.uuid());
    if (back == null) {
      return Outcome.MISSED;
    }
    long now = System.currentTimeMillis();
    Long last = rewoundAt.get(pet.uuid());
    if (last != null && now - last < petCooldownMillis(use.level())) {
      return Outcome.MISSED;
    }
    rewoundAt.put(pet.uuid(), now);
    rewoundAt.values().removeIf((Long at) -> now - at >= petCooldownMillis(1));
    marks.remove(pet.uuid());
    pet.setHealth(Math.max(1D, Math.min(pet.maxHealth(), back.health())));
    fx(pet.location().add(0D, 0.6D, 0D), FxPriority.COMBAT).particle(Particles.REVERSE_PORTAL, 24, 0.3D, 0.5D, 0.3D, 0.05D, 0D);
    if (back.at().sameWorld(pet.location()) && back.at().distanceSquared(pet.location()) > 0.25D) {
      pet.teleport(back.at(), TeleportCause.PLUGIN);
    }
    return Outcome.INSTANT;
  }

  private void remember(UUID pet, Mark mark, long now) {
    if (marks.size() >= MAX_TRACKED_PETS) {
      marks.values().removeIf((Deque<Mark> stale) -> {
        synchronized (stale) {
          return stale.isEmpty() || now - stale.peekLast().time() > keepMillis();
        }
      });
    }
    Deque<Mark> history = marks.computeIfAbsent(pet, (UUID id) -> new ArrayDeque<>());
    synchronized (history) {
      history.addLast(mark);
      while (history.size() > MAX_MARKS || (!history.isEmpty() && now - history.peekFirst().time() > keepMillis())) {
        history.removeFirst();
      }
    }
  }

  /** The oldest state within the rewind, or the state just before this blow when it took no other hit since. */
  private Mark rewindTarget(UUID pet, Mark before, long now) {
    Deque<Mark> history = marks.get(pet);
    if (history == null) {
      return before;
    }
    synchronized (history) {
      for (Mark mark : history) {
        if (now - mark.time() <= getConfig().rewindMillis) {
          return mark;
        }
      }
    }
    return before;
  }

  private long keepMillis() {
    return getConfig().rewindMillis + 1000L;
  }

  private static PlayerRef owner(LivingRef pet) {
    UUID ownerId = pet.tameOwner();
    if (ownerId == null || !TamePets.isOwnedPet(pet, ownerId)) {
      return null;
    }
    PlayerRef owner = AdaptCore.platform().host().player(ownerId);
    return owner != null && owner.isOnline() ? owner : null;
  }

  long petCooldownMillis(int level) {
    return (long) (SignatureKit.scaled(level, getMaxLevel(), getConfig().petCooldownMinutesAtFirstLevel,
        getConfig().petCooldownMinutesAtTopLevel) * 60_000D);
  }

  private record Mark(Loc at, double health, long time) {
  }

  @ConfigDescription("A pet that would die rewinds a few seconds instead, to where it stood with the health it had.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "How far back a dying pet rewinds, in milliseconds.", impact = "Higher values return pets from further back.")
    long rewindMillis = 4_000L;
    @ConfigDoc(value = "Minutes before the same pet can rewind again, at level 1.", impact = "Higher values make rewinds rarer.")
    double petCooldownMinutesAtFirstLevel = 10;
    @ConfigDoc(value = "Minutes before the same pet can rewind again, at the top level.", impact = "Higher values make rewinds rarer.")
    double petCooldownMinutesAtTopLevel = 5;
  }
}
