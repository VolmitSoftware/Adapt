package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Chronos leading Swords, replacing Convergence: every third sword hit strikes again a second later. */
public final class EchoStrike extends Signature<EchoStrike.Config> {
  private static final Id ECHO_DAMAGE = Id.minecraft("magic");
  private final Map<UUID, Integer> hits = new ConcurrentHashMap<>();
  private final Map<UUID, Double> lastHit = new ConcurrentHashMap<>();

  public EchoStrike(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("echo_shard"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Format.pc(share(level), 0), 1);
    statLore(v, getConfig().hitsPerEcho, 2);
  }

  @Override
  public long cooldownMillis(int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = e.cause() == DamageCause.ENTITY_ATTACK ? resolveMeleeContext(e, ItemKinds::isSword) : null;
    if (combat == null) {
      return;
    }
    UUID id = combat.attacker().uuid();
    int count = hits.merge(id, 1, Integer::sum);
    if (count < getConfig().hitsPerEcho) {
      return;
    }
    hits.remove(id);
    lastHit.put(id, e.finalDamage());
    fire(combat.attacker(), combat.target(), null, false);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    hits.remove(e.player().uuid());
    lastHit.remove(e.player().uuid());
  }

  @Override
  public Outcome run(BlockUse use) {
    LivingRef target = use.target();
    Double dealt = lastHit.remove(use.player().uuid());
    if (target == null || dealt == null || dealt <= 0D) {
      return Outcome.MISSED;
    }
    double echo = dealt * share(use.level());
    int delay = Math.max(1, getConfig().delayTicks);
    PlayerRef player = use.player();
    AdaptApi.scheduler().runEntity(target, () -> {
      if (!target.isValid() || target.isDead() || !player.isOnline()) {
        return;
      }
      target.damage(echo, ECHO_DAMAGE);
      fx(target.location().add(0, 1, 0), FxPriority.COMBAT).particle(Particles.SWEEP_ATTACK, 1, 0, 0, 0, 0D, 0D);
    }, delay);
    return Outcome.lasting(target, delay);
  }

  double share(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().damageShareAtFirstLevel, getConfig().damageShareAtTopLevel);
  }

  @ConfigDescription("Every third sword hit strikes again a second later as an after-image.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Share of the third hit's damage the echo deals at level 1.", impact = "Higher values make the echo hit harder.")
    double damageShareAtFirstLevel = 0.35;
    @ConfigDoc(value = "Share of the third hit's damage the echo deals at the top level.", impact = "Higher values make the echo hit harder.")
    double damageShareAtTopLevel = 0.5;
    @ConfigDoc(value = "Sword hits needed for each echo.", impact = "Lower values echo more often.")
    int hitsPerEcho = 3;
    @ConfigDoc(value = "Ticks between the hit and its echo.", impact = "Higher values make the after-image land later.")
    int delayTicks = 20;
    @ConfigDoc(value = "Cooldown after an echo, in milliseconds.", impact = "Higher values space echoes further apart than every third hit.")
    long cooldownMillis = 0;
  }
}
