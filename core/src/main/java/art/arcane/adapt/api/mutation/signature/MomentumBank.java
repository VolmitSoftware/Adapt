package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.content.adaptation.kinetics.KineticsWindburst;
import art.arcane.adapt.content.adaptation.kinetics.blocks.KineticsTrigger;
import art.arcane.adapt.content.adaptation.kinetics.upgraded.Comet;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityAttemptSmashAttackEvent;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agility and Kinetics, either leading, replacing Convergence: the run speed you carry into a mace dive is banked,
 * grows Comet's shockwave when the smash releases it (capped at +50%), and is handed back as speed when you land.
 */
public final class MomentumBank extends Signature<MomentumBank.Config> {
  private static final double SPRINT_FACTOR = 1.3D;
  private static final String CARRY_SLOT = "momentum-carry";
  private final Map<UUID, Deposit> deposits = playerState();
  private final Map<UUID, Carry> carries = playerState();
  private final Map<UUID, EntityAttemptSmashAttackEvent> smashes = new ConcurrentHashMap<>();

  public MomentumBank(MutationOverride row) {
    super(row);
    needs(PlatformNeed.item("mace"));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("piston"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Form.pc(shockwaveBonusCap(level), 0), 1);
    statLore(v, Form.duration(carryTicks(level) * 50D, 1), 2);
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return Math.max(0L, Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().cooldownMillisAtFirstLevel,
        getConfig().cooldownMillisAtTopLevel)));
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(PlayerToggleSprintEvent e) {
    if (!e.isSprinting()) {
      deposit(e.player());
    }
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(PlayerJumpEvent e) {
    if (e.player().isSprinting()) {
      deposit(e.player());
    }
  }

  /** Runs before Comet's MONITOR handler, so the banked speed reaches the shockwave of the same smash. */
  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityAttemptSmashAttackEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || !KineticsWindburst.smashLands(e.originalResult(), e.result())) {
      return;
    }
    Deposit deposit = deposits.get(p.uuid());
    if (deposit == null || System.currentTimeMillis() - deposit.at() > getConfig().bankMillis
        || deposit.bonus() < getConfig().minimumBonus || getActiveLevel(p) <= 0) {
      return;
    }
    smashes.put(p.uuid(), e);
    try {
      fire(p, e.target(), p.location(), false);
    } finally {
      smashes.remove(p.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    smashes.remove(e.player().uuid());
  }

  @Override
  protected Outcome run(BlockUse use) {
    PlayerRef p = use.player();
    EntityAttemptSmashAttackEvent smash = smashes.remove(p.uuid());
    Deposit deposit = deposits.remove(p.uuid());
    if (smash == null || deposit == null) {
      return Outcome.MISSED;
    }
    int level = use.level();
    Comet comet = PairParts.owned("kinetics", Comet.class);
    if (comet != null && comet.releases(p)) {
      Comet.strengthen(smash, p, Math.min(shockwaveBonusCap(level), deposit.bonus()));
    }
    KineticsTrigger.smashed(p);
    double carry = Math.min(getConfig().carryCap, deposit.bonus());
    int ticks = carryTicks(level);
    AdaptAttributeService.get().applyTimed(p, getName(), CARRY_SLOT, Attributes.MOVEMENT_SPEED, carry,
        AttributeOperation.ADD_MULTIPLIED_TOTAL, ticks);
    carries.put(p.uuid(), new Carry(carry, System.currentTimeMillis() + (ticks * 50L)));
    return Outcome.lasting(p, ticks);
  }

  private void deposit(PlayerRef p) {
    if (getActiveLevel(p) <= 0) {
      return;
    }
    Carry carry = carries.get(p.uuid());
    double own = carry != null && carry.until() > System.currentTimeMillis() ? carry.share() : 0D;
    double bonus = banked(p.attributeValue(Attributes.MOVEMENT_SPEED) / (1D + own),
        p.attributeBase(Attributes.MOVEMENT_SPEED), p.isSprinting());
    if (bonus >= getConfig().minimumBonus) {
      deposits.put(p.uuid(), new Deposit(bonus, System.currentTimeMillis()));
    }
  }

  /** The share of movement speed above walking (and above vanilla sprinting while sprinting). */
  static double banked(double value, double base, boolean sprinting) {
    if (!(value > 0D) || !(base > 0D)) {
      return 0D;
    }
    return Math.max(0D, (value / (base * (sprinting ? SPRINT_FACTOR : 1D))) - 1D);
  }

  double shockwaveBonusCap(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().shockwaveBonusCapAtFirstLevel,
        getConfig().shockwaveBonusCapAtTopLevel);
  }

  int carryTicks(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().carryTicksAtFirstLevel,
        getConfig().carryTicksAtTopLevel));
  }

  private record Deposit(double bonus, long at) {
  }

  private record Carry(double share, long until) {
  }

  @ConfigDescription("Run speed carried into a mace dive grows Comet's shockwave and comes back as speed on landing.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Most the banked speed grows Comet's shockwave at level 1, as a share.", impact = "Higher values let fast runs hit much harder.")
    double shockwaveBonusCapAtFirstLevel = 0.3;
    @ConfigDoc(value = "Most the banked speed grows Comet's shockwave at the top level, as a share.", impact = "Higher values let fast runs hit much harder.")
    double shockwaveBonusCapAtTopLevel = 0.5;
    @ConfigDoc(value = "Most speed handed back on landing, as a share of walking speed.", impact = "Higher values keep more of the run.")
    double carryCap = 1.0;
    @ConfigDoc(value = "Ticks the speed is kept after landing at level 1.", impact = "Higher values keep the run going longer.")
    double carryTicksAtFirstLevel = 40;
    @ConfigDoc(value = "Ticks the speed is kept after landing at the top level.", impact = "Higher values keep the run going longer.")
    double carryTicksAtTopLevel = 100;
    @ConfigDoc(value = "Least banked speed that counts, as a share of walking speed.", impact = "Higher values need a faster run.")
    double minimumBonus = 0.1;
    @ConfigDoc(value = "Time a bank stays valid after the run ends, in milliseconds.", impact = "Higher values allow longer dives.")
    long bankMillis = 6000;
    @ConfigDoc(value = "Time between banked smashes at level 1, in milliseconds.", impact = "Higher values make them rarer.")
    double cooldownMillisAtFirstLevel = 8000;
    @ConfigDoc(value = "Time between banked smashes at the top level, in milliseconds.", impact = "Higher values make them rarer.")
    double cooldownMillisAtTopLevel = 4000;
  }
}
