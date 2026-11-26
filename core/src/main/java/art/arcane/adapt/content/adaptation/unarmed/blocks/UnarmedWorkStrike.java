package art.arcane.adapt.content.adaptation.unarmed.blocks;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedMeditation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.Map;
import java.util.UUID;

/**
 * Readies a meditation: within the window, sneaking still and empty-handed for 3 s grants Absorption, judged still by
 * Meditation's own rule. One meditation per cooldown.
 */
public class UnarmedWorkStrike extends StrikeBlock<UnarmedWorkStrike.Config> {
  private static final Id ABSORPTION = Id.minecraft("absorption");
  private final Map<UUID, Ready> ready = playerState();
  private final Map<UUID, Loc> sitting = playerState();
  private final Cooldowns meditations = cooldowns();

  public UnarmedWorkStrike() {
    super("block-unarmed-strike-work");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("amethyst_cluster"));
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    if (!meditations.isReady(player.uuid(), getConfig().cooldownMillis)) {
      return Outcome.MISSED;
    }
    int amplifier = (int) Math.round(use.scaled(getConfig().absorptionAmplifierAtFirstLevel,
        getConfig().absorptionAmplifierAtTopLevel));
    int ticks = (int) (use.scaled(getConfig().absorptionMillisAtFirstLevel, getConfig().absorptionMillisAtTopLevel) / 50D);
    ready.put(player.uuid(), new Ready(amplifier, ticks, System.currentTimeMillis() + getConfig().readyMillis));
    return Outcome.INSTANT;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef player = e.player();
    if (!e.isSneaking()) {
      sitting.remove(player.uuid());
      return;
    }
    Ready primed = ready.get(player.uuid());
    if (primed == null) {
      return;
    }
    if (System.currentTimeMillis() > primed.expiresAt()) {
      ready.remove(player.uuid());
      return;
    }
    Loc start = player.location();
    sitting.put(player.uuid(), start);
    Tasks.runEntity(player, () -> settle(player, start), getConfig().stillTicks);
  }

  private void settle(PlayerRef player, Loc start) {
    UUID id = player.uuid();
    Ready primed = ready.get(id);
    if (primed == null || sitting.get(id) != start || !player.isOnline() || player.isDead() || !player.isSneaking()
        || isItem(player.inventory().mainHand()) || isItem(player.inventory().offHand())
        || !UnarmedMeditation.isStill(start, player.location(), getConfig().stationaryEpsilonSquared)
        || !meditations.isReady(id, getConfig().cooldownMillis)) {
      return;
    }
    ready.remove(id);
    sitting.remove(id);
    meditations.mark(id, getConfig().cooldownMillis);
    player.addEffect(new EffectInstance(ABSORPTION, primed.ticks(), primed.amplifier(), false, true, true));
    fx(player.location().add(0D, 1.1D, 0D), FxPriority.TRANSITION)
        .column(Particles.END_ROD, 4, 1.0D)
        .particle(Particles.ENCHANTMENT_TABLE, 8, 0D, 0D, 0D, 0.5D, 0.05D);
  }

  /** Whether this skill's work strike has a meditation ready for the player, so the kept Meditation stands down. */
  public static boolean readies(Adaptation<?> original, PlayerRef player) {
    if (original == null || original.getSkill() == null || player == null) {
      return false;
    }
    for (MutationBlock<?> block : original.getSkill().getBlocks()) {
      if (block instanceof UnarmedWorkStrike strike) {
        Ready primed = strike.ready.get(player.uuid());
        return primed != null && System.currentTimeMillis() <= primed.expiresAt();
      }
    }
    return false;
  }

  private record Ready(int amplifier, int ticks, long expiresAt) {
  }

  @ConfigDescription("Sneaking still for 3 s within 30 s gives 2 absorption hearts.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Absorption amplifier at level 1; 0 is 2 hearts, 1 is 4, 2 is 6.", impact = "Higher values give more absorption.")
    double absorptionAmplifierAtFirstLevel = 0D;
    @ConfigDoc(value = "Absorption amplifier at the top level; 0 is 2 hearts, 1 is 4, 2 is 6.", impact = "Higher values give more absorption.")
    double absorptionAmplifierAtTopLevel = 2D;
    @ConfigDoc(value = "How long the absorption lasts at level 1, in milliseconds.", impact = "Higher values keep the hearts longer.")
    double absorptionMillisAtFirstLevel = 60_000D;
    @ConfigDoc(value = "How long the absorption lasts at the top level, in milliseconds.", impact = "Higher values keep the hearts longer.")
    double absorptionMillisAtTopLevel = 120_000D;
    @ConfigDoc(value = "How long the meditation stays ready after the strike, in milliseconds.", impact = "Higher values give more time to sit down.")
    long readyMillis = 30_000L;
    @ConfigDoc(value = "How long you must sneak still, in ticks.", impact = "Higher values make the meditation slower.")
    int stillTicks = 60;
    @ConfigDoc(value = "Most squared movement still counted as still.", impact = "Higher values forgive more drift.")
    double stationaryEpsilonSquared = 0.01D;
    @ConfigDoc(value = "Time between meditations, in milliseconds.", impact = "Higher values make meditations rarer.")
    long cooldownMillis = 30_000L;
  }
}
