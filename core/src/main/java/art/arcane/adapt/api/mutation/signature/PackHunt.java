package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.hunter.HunterRamp;
import art.arcane.adapt.content.adaptation.hunter.upgraded.ApexPredator;
import art.arcane.adapt.content.adaptation.hunter.upgraded.StalkersTrail;
import art.arcane.adapt.content.adaptation.hunter.upgraded.TrackersCompass;
import art.arcane.adapt.content.adaptation.taming.TamePackCommand;
import art.arcane.adapt.content.adaptation.taming.TamePets;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hunter and Taming, either leading, replacing Convergence: your pets take up the blood trail or the species your
 * Tracker's Compass follows on their own, never straying past 32 blocks from you, and their bites on that quarry build
 * and use your Apex Predator ramp.
 */
public final class PackHunt extends Signature<PackHunt.Config> {
  private final Map<UUID, LivingRef> quarries = playerState();
  private final Map<UUID, Long> huntedAt = playerState();

  public PackHunt(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("lead"));
    setInterval(1000);
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Form.f(huntRange(level), 0), 1);
    statLore(v, pets(level), 2);
    statLore(v, Form.pc(perStack(level), 1), 3);
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    for (AdaptPlayer data : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef player = data.getPlayer();
      if (player != null) {
        Tasks.runEntity(player, () -> hunt(player));
      }
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    if (e.isCancelled() || !HunterRamp.ramps(e) || !(e.entity() instanceof LivingRef victim)) {
      return;
    }
    EntityRef damager = e.damager();
    UUID ownerId = damager instanceof PlayerRef ? null : damager.tameOwner();
    LivingRef quarry = ownerId == null ? null : quarries.get(ownerId);
    if (quarry == null || !quarry.uuid().equals(victim.uuid())) {
      return;
    }
    PlayerRef owner = AdaptCore.platform().host().player(ownerId);
    int level = owner == null ? 0 : getActiveLevel(owner);
    if (level <= 0 || !TamePets.isOwnedPet(damager, ownerId)) {
      return;
    }
    ApexPredator apex = PairParts.owned("hunter", ApexPredator.class);
    int stacks = apex == null ? 0 : apex.petHit(owner, victim);
    if (stacks > 0) {
      e.setDamage(e.damage() * HunterRamp.damageMultiplier(stacks, perStack(level)));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    quarries.remove(e.player().uuid());
  }

  private void hunt(PlayerRef player) {
    int level = getActiveLevel(player);
    if (level <= 0 || !usable()) {
      quarries.remove(player.uuid());
      return;
    }
    LivingRef current = quarries.get(player.uuid());
    if (current != null) {
      boolean held = huntable(player, current, getConfig().leash);
      if (held) {
        loose(player, current, level);
      } else {
        quarries.remove(player.uuid(), current);
      }
      recall(player, current, held ? getConfig().leash : 0D);
      return;
    }
    long now = System.currentTimeMillis();
    if (now - huntedAt.getOrDefault(player.uuid(), 0L) < getConfig().cooldownMillis) {
      return;
    }
    LivingRef found = find(player, level);
    if (found != null && fire(player, found, null, false)) {
      huntedAt.put(player.uuid(), now);
    }
  }

  @Override
  protected Outcome run(BlockUse use) {
    LivingRef quarry = use.target();
    PlayerRef player = use.player();
    if (quarry == null || loose(player, quarry, use.level()) == 0) {
      return Outcome.MISSED;
    }
    quarries.put(player.uuid(), quarry);
    fx(quarry.location().add(0, quarry.height(), 0), FxPriority.COMBAT).burst(Particles.CRIT, 8, 0.3D);
    return Outcome.INSTANT;
  }

  /** Sends idle pets within the leash at the quarry, up to the level's pack size; how many hunt it now. */
  private int loose(PlayerRef player, LivingRef quarry, int level) {
    int hunting = 0;
    int left = pets(level);
    for (LivingRef pet : TamePets.near(player, getConfig().leash)) {
      if (left <= 0) {
        break;
      }
      if (!(pet instanceof MobRef mob) || !TamePackCommand.isCombatCapableTameable(pet)
          || AdaptCore.platform().host().animals().isSitting(pet)) {
        continue;
      }
      left--;
      LivingRef target = mob.target();
      if (target != null && target.uuid().equals(quarry.uuid())) {
        hunting++;
      } else if (target == null || target.isDead()) {
        Tasks.runEntity(mob, () -> mob.setTarget(quarry));
        hunting++;
      }
    }
    return hunting;
  }

  /** Pets chasing the quarry farther than {@code beyond} blocks from you drop it and come back. */
  private void recall(PlayerRef player, LivingRef quarry, double beyond) {
    Loc at = player.location();
    for (LivingRef pet : TamePets.near(player, getConfig().leash + getConfig().recallMargin)) {
      if (pet instanceof MobRef mob && pet.location().distanceSquared(at) > beyond * beyond && mob.target() != null
          && mob.target().uuid().equals(quarry.uuid())) {
        Tasks.runEntity(mob, () -> {
          mob.setTarget(null);
          mob.pathTo(at, 1.2D);
        });
      }
    }
  }

  private @Nullable LivingRef find(PlayerRef player, int level) {
    StalkersTrail trail = PairParts.owned("hunter", StalkersTrail.class);
    EntityRef trailed = trail == null ? null : trail.trailedPrey(player);
    double range = huntRange(level);
    if (trailed instanceof LivingRef prey && huntable(player, prey, range)) {
      return prey;
    }
    Id kind = TrackersCompass.trackedKind(player);
    if (kind == null) {
      return null;
    }
    LivingRef nearest = null;
    double best = Double.MAX_VALUE;
    Loc at = player.location();
    List<EntityRef> around = player.nearbyEntities(range, range, range);
    for (EntityRef entity : around) {
      if (kind.equals(entity.type()) && entity instanceof LivingRef living && huntable(player, living, range)) {
        double distance = living.location().distanceSquared(at);
        if (distance < best) {
          best = distance;
          nearest = living;
        }
      }
    }
    return nearest;
  }

  /** A living natural creature within {@code range} of the player that they may hurt: no players, pets, named or farmed mobs. */
  private boolean huntable(PlayerRef player, LivingRef target, double range) {
    if (target instanceof PlayerRef || !target.isValid() || target.isDead() || target.tameOwner() != null
        || (target.customName() != null && !target.customName().isBlank()) || MutationPayouts.farmed(player, target)
        || !canPVE(player, target)) {
      return false;
    }
    Loc at = target.location();
    return at.sameWorld(player.location()) && at.distanceSquared(player.location()) <= range * range;
  }

  double huntRange(int level) {
    return Math.min(getConfig().leash, SignatureKit.scaled(level, getMaxLevel(), getConfig().rangeAtFirstLevel, getConfig().rangeAtTopLevel));
  }

  int pets(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().petsAtFirstLevel, getConfig().petsAtTopLevel));
  }

  double perStack(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().perStackAtFirstLevel, getConfig().perStackAtTopLevel);
  }

  @ConfigDescription("Your pets hunt your blood trail or tracked species within 32 blocks of you and share your Apex Predator ramp on it.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "How far from you the pack picks up a quarry at level 1, in blocks.", impact = "Higher values start hunts farther away.")
    double rangeAtFirstLevel = 16;
    @ConfigDoc(value = "How far from you the pack picks up a quarry at the top level, in blocks.", impact = "Higher values start hunts farther away.")
    double rangeAtTopLevel = 32;
    @ConfigDoc(value = "Farthest a hunting pet goes from you, in blocks.", impact = "Higher values let pets chase farther.")
    double leash = 32;
    @ConfigDoc(value = "Extra reach past the leash searched for pets to call back, in blocks.", impact = "Higher values call back pets that ran farther.")
    double recallMargin = 16;
    @ConfigDoc(value = "Pets that join a hunt at level 1.", impact = "Higher values hunt with more of the pack.")
    double petsAtFirstLevel = 2;
    @ConfigDoc(value = "Pets that join a hunt at the top level.", impact = "Higher values hunt with more of the pack.")
    double petsAtTopLevel = 4;
    @ConfigDoc(value = "Pet damage added per Apex Predator ramp stack on the quarry at level 1. 0.05 is 5%.", impact = "Higher values make pets ramp harder.")
    double perStackAtFirstLevel = 0.05;
    @ConfigDoc(value = "Pet damage added per ramp stack on the quarry at the top level.", impact = "Higher values make pets ramp harder.")
    double perStackAtTopLevel = 0.1;
    @ConfigDoc(value = "Cooldown between two hunts, in milliseconds.", impact = "Higher values give the pack longer rests between quarries.")
    long cooldownMillis = 5000;
  }
}
