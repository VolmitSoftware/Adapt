package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.content.adaptation.stealth.upgraded.PhantomDecoy;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stealth leading Swords, replacing Convergence: a melee hit taken while sneaking unhunted is partly parried, and once no
 * later handler cancels it, a free Shadow Step at your Phantom Decoy's level puts you behind the attacker. It needs
 * Shadow Step ready and room behind the attacker, and starts Shadow Step's cooldown.
 */
public final class ShadowRiposte extends Signature<ShadowRiposte.Config> {
  private final Map<UUID, EntityDamageByEntityEvent> hits = new ConcurrentHashMap<>();
  private final Map<UUID, Riposte> pending = new ConcurrentHashMap<>();
  private final Cooldowns ripostes = cooldowns();

  public ShadowRiposte(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ink_sac"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Form.pc(parryShare(level), 0), 1);
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return 0L;
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef player) || !(e.damager() instanceof LivingRef attacker)
        || (e.cause() != DamageCause.ENTITY_ATTACK && e.cause() != DamageCause.ENTITY_SWEEP_ATTACK)
        || e.damage() <= 0D || !player.isSneaking() || getActiveLevel(player) <= 0 || attacker.isDead()
        || !ripostes.isReady(player.uuid(), cooldown()) || pending.containsKey(player.uuid())
        || ShadowSteps.huntedByAnother(player, attacker.uuid(), getConfig().huntRadius)) {
      return;
    }
    hits.put(player.uuid(), e);
    try {
      fire(player, attacker, attacker.location(), false);
    } finally {
      hits.remove(player.uuid());
    }
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.MONITOR)
  public void settled(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef player)) {
      return;
    }
    Riposte riposte = pending.get(player.uuid());
    if (riposte == null || riposte.hit() != e) {
      return;
    }
    pending.remove(player.uuid());
    PhantomDecoy step = ShadowSteps.shadowStep(this);
    if (e.isCancelled() || step == null) {
      return;
    }
    ripostes.mark(player.uuid(), cooldown());
    Loc origin = player.location();
    if (step.stepBehind(player, riposte.attacker(), riposte.decoyLevel(), true)) {
      fx(origin.add(0, 1, 0), FxPriority.COMBAT)
          .burst(Particles.SMOKE, 10, 0.4D)
          .particle(Particles.SWEEP_ATTACK, 1, 0, 0, 0, 0D, 0D);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    hits.remove(e.player().uuid());
    pending.remove(e.player().uuid());
  }

  @Override
  protected Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    LivingRef attacker = use.target();
    EntityDamageByEntityEvent hit = hits.get(player.uuid());
    PhantomDecoy step = ShadowSteps.shadowStep(this);
    if (hit == null || attacker == null || step == null) {
      return Outcome.MISSED;
    }
    int decoyLevel = step.getActiveLevel(player);
    if (decoyLevel <= 0 || !step.canStepBehind(player, attacker, decoyLevel)) {
      return Outcome.MISSED;
    }
    hit.setDamage(hit.damage() * (1D - parryShare(use.level())));
    pending.put(player.uuid(), new Riposte(hit, attacker, decoyLevel));
    return Outcome.INSTANT;
  }

  double parryShare(int level) {
    return Math.min(1D, Math.max(0D, SignatureKit.scaled(level, getMaxLevel(), getConfig().parryAtFirstLevel,
        getConfig().parryAtTopLevel)));
  }

  private long cooldown() {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  private record Riposte(EntityDamageByEntityEvent hit, LivingRef attacker, int decoyLevel) {
  }

  @ConfigDescription("A melee hit taken while sneaking unhunted is half parried and Shadow Steps you behind the attacker.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Share of the hit parried at level 1.", impact = "Higher values take less damage.")
    double parryAtFirstLevel = 0.5D;
    @ConfigDoc(value = "Share of the hit parried at the top level.", impact = "Higher values take less damage.")
    double parryAtTopLevel = 0.7D;
    @ConfigDoc(value = "Reach in blocks within which a mob other than the attacker targeting you stops the riposte.", impact = "Higher values make it harder to stay unhunted.")
    double huntRadius = 16D;
    @ConfigDoc(value = "Cooldown after a riposte, in milliseconds.", impact = "Higher values make ripostes rarer.")
    long cooldownMillis = 15_000L;
  }
}
