package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.content.adaptation.rift.RiftTeleports;
import art.arcane.adapt.content.adaptation.rift.upgraded.PhaseStep;
import art.arcane.adapt.content.adaptation.stealth.blocks.StealthState;
import art.arcane.adapt.content.adaptation.stealth.upgraded.PhantomDecoy;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Stealth and Rift, either leading, replacing Convergence: soon after a blink, an empty-handed right-click on a
 * creature that cannot see you Shadow Steps behind it without a Smoke Pellet, at your Phantom Decoy's range and under
 * Shadow Step's own cooldown; and Phase Step blinks leave no particles while no mob hunts you.
 */
public final class VoidStep extends Signature<VoidStep.Config> {
  private final Map<UUID, Long> blinkedAt = new ConcurrentHashMap<>();
  private final Predicate<PlayerRef> quietBlink = this::unhunted;

  public VoidStep(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("crying_obsidian"));
    PhaseStep.quietWhile(quietBlink);
  }

  @Override
  public void unregister() {
    PhaseStep.stopQuiet(quietBlink);
    super.unregister();
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Form.duration(blinkWindowMillis(level), 1), 1);
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return 0L;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    if (e.cause() == TeleportCause.PLUGIN && RiftTeleports.byRift(e) && getActiveLevel(e.player()) > 0) {
      blinkedAt.put(e.player().uuid(), System.currentTimeMillis());
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerInteractEntityEvent e) {
    PlayerRef player = e.player();
    Long blinked = blinkedAt.get(player.uuid());
    if (blinked == null || e.hand() != Hand.MAIN_HAND || !(e.clicked() instanceof MobRef target)) {
      return;
    }
    if (System.currentTimeMillis() - blinked > blinkWindowMillis(getActiveLevel(player))) {
      blinkedAt.remove(player.uuid(), blinked);
      return;
    }
    ItemRef held = player.inventory().mainHand();
    if ((held != null && !held.isEmpty() && !held.type().isAir()) || !ShadowSteps.unseenBy(this, player, target)) {
      return;
    }
    if (fire(player, target, target.location(), false)) {
      e.setCancelled(true);
      blinkedAt.remove(player.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    blinkedAt.remove(e.player().uuid());
  }

  @Override
  protected Outcome run(BlockUse use) {
    LivingRef target = use.target();
    PhantomDecoy step = ShadowSteps.shadowStep(this);
    int decoyLevel = step == null ? 0 : step.getActiveLevel(use.player());
    return target != null && decoyLevel > 0 && step.stepBehind(use.player(), target, decoyLevel, true)
        ? Outcome.INSTANT : Outcome.MISSED;
  }

  long blinkWindowMillis(int level) {
    return Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().blinkWindowMillisAtFirstLevel,
        getConfig().blinkWindowMillisAtTopLevel));
  }

  private boolean unhunted(PlayerRef player) {
    return getActiveLevel(player) > 0 && !StealthState.hunted(player, getConfig().huntRadius);
  }

  @ConfigDescription("Soon after a blink, Shadow Step needs no Smoke Pellet, and unhunted blinks leave no particles.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Time after a blink in which Shadow Step needs no pellet at level 1, in milliseconds.", impact = "Higher values give more time to pick a target.")
    double blinkWindowMillisAtFirstLevel = 3_000D;
    @ConfigDoc(value = "Time after a blink in which Shadow Step needs no pellet at the top level, in milliseconds.", impact = "Higher values give more time to pick a target.")
    double blinkWindowMillisAtTopLevel = 5_000D;
    @ConfigDoc(value = "Reach in blocks within which a mob targeting you counts as seeing your blink.", impact = "Higher values make quiet blinks rarer.")
    double huntRadius = 16D;
  }
}
