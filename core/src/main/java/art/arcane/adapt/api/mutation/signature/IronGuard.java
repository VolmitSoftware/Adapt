package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.content.adaptation.blocking.BlockingCounterGuard;
import art.arcane.adapt.content.adaptation.blocking.BlockingMirror;
import art.arcane.adapt.content.adaptation.blocking.BlockingMirrorBlock;
import art.arcane.adapt.content.adaptation.blocking.BlockingParry;
import art.arcane.adapt.content.adaptation.blocking.BlockingPerfectGuard;
import art.arcane.adapt.content.adaptation.blocking.BlockingRetaliation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unarmed and Blocking, either leading, replacing Convergence: raising empty hands just before a hit parries it like
 * Mirror Guard without a shield, sends half of it back, staggers the attacker and loads the next bare-hand hit.
 */
public final class IronGuard extends Signature<IronGuard.Config> {
  private final Map<UUID, Long> raised = playerState();
  private final BlockingMirror mirror = new BlockingMirror(this, "iron-guard-damage-factor", this::reflected);
  private final BlockingRetaliation retaliation = new BlockingRetaliation(this);
  private final Map<UUID, EntityDamageByEntityEvent> pending = new ConcurrentHashMap<>();
  private final Map<UUID, Long> ripostes = playerState();

  public IronGuard(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_bars"));
  }

  @Override
  protected void onRuntimeActivated() {
    mirror.activate();
    retaliation.activate();
  }

  @Override
  public void unregister() {
    mirror.close();
    retaliation.close();
    pending.clear();
    super.unregister();
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, C.YELLOW, "* ", Form.duration(windowMillis(level), 2), 1);
    statLore(v, Form.pc(getConfig().reflectShare, 0), 2);
    statLore(v, Form.pc(riposteBonus(level), 0), 3);
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return Math.max(0L, Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().cooldownMillisAtFirstLevel, getConfig().cooldownMillisAtTopLevel)));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action == InteractAction.RIGHT_CLICK_AIR || action == InteractAction.RIGHT_CLICK_BLOCK) {
      raise(e.player());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEntityEvent e) {
    raise(e.player());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (retaliation.consume(e.damager(), e.entity())) {
      return;
    }
    if (e.damager() instanceof ProjectileRef projectile) {
      mirror.applyReflectedDamage(e, projectile);
    }
    riposte(e);
    if (!(e.entity() instanceof PlayerRef defender) || e.isCancelled() || !bareHanded(defender)) {
      return;
    }
    int level = getActiveLevel(defender);
    if (level <= 0 || !timed(defender, windowMillis(level), System.currentTimeMillis())
        || !BlockingParry.faces(defender, e.damager(), getConfig().minFacingAlignment)) {
      return;
    }
    pending.put(defender.uuid(), e);
    try {
      fire(defender, BlockingParry.attacker(e.damager()), null, false);
    } finally {
      pending.remove(defender.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    pending.remove(e.player().uuid());
  }

  @Override
  protected Outcome run(BlockUse use) {
    PlayerRef defender = use.player();
    EntityDamageByEntityEvent hit = pending.remove(defender.uuid());
    if (hit == null || hit.isCancelled()) {
      return Outcome.MISSED;
    }
    double damage = hit.damage();
    hit.setCancelled(true);
    raised.remove(defender.uuid());
    ripostes.put(defender.uuid(), System.currentTimeMillis());
    BlockingPerfectGuard.parryFlash(this, defender);
    LivingRef attacker = use.target();
    if (attacker != null) {
      BlockingPerfectGuard.stagger(attacker, defender.location(), staggerTicks(use.level()), 0, getConfig().knockback);
    }
    double share = getConfig().reflectShare;
    if (hit.damager() instanceof ProjectileRef projectile) {
      if (mirror.accepting() && !BlockingMirror.reflected(projectile)) {
        mirror.reflect(defender, projectile, new BlockingMirror.Shot(1D, share, 0L, 0.08D, 1.2D));
      }
    } else if (attacker != null && retaliation.accepting()) {
      retaliation.strike(defender, attacker, damage * share, () -> {
      }, (Loc at) -> BlockingCounterGuard.reflectDischarge(this, defender, at));
    }
    return Outcome.INSTANT;
  }

  private void raise(PlayerRef p) {
    if (bareHanded(p) && getActiveLevel(p) > 0) {
      raised.put(p.uuid(), System.currentTimeMillis());
    }
  }

  private boolean timed(PlayerRef p, long windowMillis, long now) {
    Long at = raised.get(p.uuid());
    return at != null && now - at <= windowMillis;
  }

  private void riposte(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef striker) || e.isCancelled()) {
      return;
    }
    Long parried = ripostes.get(striker.uuid());
    if (parried == null || !bareHanded(striker)) {
      return;
    }
    ripostes.remove(striker.uuid());
    int level = getActiveLevel(striker);
    if (level <= 0 || !usable() || System.currentTimeMillis() - parried > getConfig().riposteWindowMillis
        || !canDamageTarget(striker, e.entity())) {
      return;
    }
    e.setDamage(e.damage() * (1D + riposteBonus(level)));
  }

  private void reflected(PlayerRef defender, BlockingMirror.Reflection reflection) {
    BlockingMirrorBlock.mirrorFlash(this, defender);
  }

  static boolean bareHanded(PlayerRef p) {
    return p.inventory().mainHand().isEmpty() && p.inventory().offHand().isEmpty();
  }

  long windowMillis(int level) {
    return Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().windowMillisAtFirstLevel, getConfig().windowMillisAtTopLevel));
  }

  double riposteBonus(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().riposteBonusAtFirstLevel, getConfig().riposteBonusAtTopLevel);
  }

  int staggerTicks(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().staggerTicksAtFirstLevel, getConfig().staggerTicksAtTopLevel));
  }

  @ConfigDescription("Raising empty hands just before a hit parries it, sends half back and loads the next punch.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Time after raising empty hands that a hit is still parried at level 1, in milliseconds; half of Mirror Guard's.", impact = "Higher values make the parry easier to land.")
    double windowMillisAtFirstLevel = 175;
    @ConfigDoc(value = "Parry window at the top level, in milliseconds.", impact = "Higher values make the parry easier to land.")
    double windowMillisAtTopLevel = 275;
    @ConfigDoc(value = "Lowest dot product between your view and the attacker's direction that still counts as facing it.", impact = "Higher values require the attacker to be more directly in front of you.")
    double minFacingAlignment = 0.15;
    @ConfigDoc(value = "Share of a parried hit sent back to the attacker or on the returned projectile.", impact = "Higher values make parries hurt more.")
    double reflectShare = 0.5;
    @ConfigDoc(value = "Ticks a parried attacker is slowed and weakened at level 1.", impact = "Higher values leave a longer opening.")
    double staggerTicksAtFirstLevel = 20;
    @ConfigDoc(value = "Ticks a parried attacker is slowed and weakened at the top level.", impact = "Higher values leave a longer opening.")
    double staggerTicksAtTopLevel = 40;
    @ConfigDoc(value = "Push given to a parried attacker.", impact = "Higher values knock attackers further back.")
    double knockback = 0.45;
    @ConfigDoc(value = "Extra damage on the first bare-hand hit after a parry at level 1.", impact = "Higher values make counters hit harder.")
    double riposteBonusAtFirstLevel = 0.25;
    @ConfigDoc(value = "Extra damage on the first bare-hand hit after a parry at the top level.", impact = "Higher values make counters hit harder.")
    double riposteBonusAtTopLevel = 0.55;
    @ConfigDoc(value = "Time after a parry that the counter stays loaded, in milliseconds.", impact = "Higher values give more time to answer.")
    long riposteWindowMillis = 3000;
    @ConfigDoc(value = "Time between parries at level 1, in milliseconds.", impact = "Higher values make parries rarer.")
    double cooldownMillisAtFirstLevel = 3000;
    @ConfigDoc(value = "Time between parries at the top level, in milliseconds.", impact = "Higher values make parries rarer.")
    double cooldownMillisAtTopLevel = 2000;
  }
}
