package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.taming.TamePets;
import art.arcane.adapt.content.adaptation.taming.TameRegeneration;
import art.arcane.adapt.content.golem.GolemAnchor;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemMastery;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityBreedEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Golemancy leading Taming, replacing Convergence: your Rancher golems become keepers. Each round they heal your pets
 * and the animals you fed or bred around their home, and walk those animals back home when they stray. Breeding stays
 * the Rancher's own work, to its keep cap and with food from its deposit nodes.
 */
public final class KennelCore extends Signature<KennelCore.Config> {
  private static final DataKey<String> KEPT_BY = DataKey.adapt("kennel-kept-by", DataCodecs.STRING);
  private final Map<UUID, Long> rounds = playerState();

  public KennelCore(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("lead"));
    setInterval(1000);
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Format.f(radius(level), 0), 1);
    statLore(v, Format.f(heal(level), 1), 2);
    statLore(v, animals(level), 3);
    statLore(v, Format.duration(roundMillis(level), 0), 4);
  }

  @Override
  public long cooldownMillis(int level) {
    return roundMillis(level);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (PlayerProfile data : learnedCandidates(now)) {
      PlayerRef player = data.player();
      int level = player == null ? 0 : getActiveLevel(player);
      if (level <= 0 || now - rounds.getOrDefault(player.uuid(), 0L) < roundMillis(level) || keepers(player).isEmpty()) {
        continue;
      }
      rounds.put(player.uuid(), now);
      AdaptApi.scheduler().runEntity(player, () -> fire(player, null, player.location(), false));
    }
  }

  /** Feeding an animal its breed food marks it as yours to keep, unless another keeper's owner already did. */
  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEntityEvent e) {
    PlayerRef player = e.player();
    EntityRef clicked = e.clicked();
    ItemRef held = player.inventory().hand(e.hand());
    if (getActiveLevel(player) <= 0 || !clicked.has(EntityTrait.ANIMAL) || clicked.tameOwner() != null || held == null
        || held.isEmpty() || !AdaptApi.platform().host().animals().isBreedItem(clicked, held.type())) {
      return;
    }
    keep(clicked, player);
  }

  /** A young animal you bred is yours to keep. */
  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityBreedEvent e) {
    if (!e.isCancelled() && e.breeder() instanceof PlayerRef player && getActiveLevel(player) > 0) {
      keep(e.child(), player);
    }
  }

  private static void keep(EntityRef animal, PlayerRef owner) {
    if (!animal.data().has(KEPT_BY)) {
      animal.data().set(KEPT_BY, owner.uuid().toString());
    }
  }

  /** Each keeper tends its own home on its own thread. */
  @Override
  public Outcome run(BlockUse use) {
    PlayerRef owner = use.player();
    int level = use.level();
    boolean sent = false;
    for (GolemHandle keeper : keepers(owner)) {
      sent |= AdaptApi.scheduler().runEntity(keeper.entity(), () -> tend(owner, keeper, level));
    }
    return sent ? Outcome.INSTANT : Outcome.MISSED;
  }

  /** One keeper's round: heal your pets and the animals you keep around its home, and lead your strays back. */
  private void tend(PlayerRef owner, GolemHandle keeper, int level) {
    MobRef golem = keeper.entity();
    if (golem == null || !golem.isValid() || golem.isDead()) {
      return;
    }
    Loc home = home(keeper);
    double radius = radius(level);
    double reach = radius + getConfig().strayReach;
    String ownerId = owner.uuid().toString();
    int left = animals(level);
    for (EntityRef entity : golem.world().entitiesIn(Box.around(home.position(), reach, reach, reach),
        (EntityRef candidate) -> candidate.has(EntityTrait.ANIMAL))) {
      if (left <= 0) {
        break;
      }
      if (!(entity instanceof MobRef animal) || animal.isDead() || !animal.isValid()) {
        continue;
      }
      boolean pet = TamePets.isOwnedPet(animal, owner.uuid());
      boolean kept = !pet && animal.tameOwner() == null && ownerId.equals(animal.data().get(KEPT_BY));
      if ((!pet && !kept) || !canInteract(owner, animal.location())) {
        continue;
      }
      if (animal.location().distanceSquared(home) <= radius * radius) {
        if (animal.health() < animal.maxHealth()) {
          double amount = heal(level);
          AdaptApi.scheduler().runEntity(animal, () -> {
            TameRegeneration.heal(this, animal, amount);
            fx(animal.location().add(0, animal.height(), 0), FxPriority.GAMEPLAY).burst(Particles.HEART, 2, 0.3D);
          });
          left--;
        }
      } else if (kept) {
        AdaptApi.scheduler().runEntity(animal, () -> animal.pathTo(home, 1.1D));
        left--;
      }
    }
  }

  /** The owner's awake, loaded golems that run a Rancher core. */
  private static List<GolemHandle> keepers(PlayerRef owner) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      return List.of();
    }
    List<GolemHandle> keepers = new ArrayList<>();
    for (GolemHandle handle : registry.get().loadedOf(owner.uuid())) {
      MobRef golem = handle.entity();
      if (!handle.isRetired() && !handle.isDormant() && golem != null && golem.isValid()
          && GolemMastery.cores(handle).contains(GolemCoreType.RANCHER)) {
        keepers.add(handle);
      }
    }
    return keepers;
  }

  private static Loc home(GolemHandle keeper) {
    MobRef golem = keeper.entity();
    GolemAnchor anchor = keeper.state().home();
    if (anchor == null || !anchor.world().equals(golem.world().uid())) {
      return golem.location();
    }
    return new Loc(golem.world(), anchor.x() + 0.5D, anchor.y(), anchor.z() + 0.5D);
  }

  double radius(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().radiusAtFirstLevel, getConfig().radiusAtTopLevel);
  }

  double heal(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().healAtFirstLevel, getConfig().healAtTopLevel);
  }

  int animals(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().animalsAtFirstLevel, getConfig().animalsAtTopLevel));
  }

  long roundMillis(int level) {
    return Math.max(1000L, (long) SignatureKit.scaled(level, getMaxLevel(), getConfig().roundMillisAtFirstLevel,
        getConfig().roundMillisAtTopLevel));
  }

  @ConfigDescription("Your Rancher golems keep your pets and the animals you fed or bred: they heal them around their home and lead strays back.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Keeper reach around its home at level 1, in blocks.", impact = "Higher values tend a wider pasture.")
    double radiusAtFirstLevel = 10;
    @ConfigDoc(value = "Keeper reach around its home at the top level, in blocks.", impact = "Higher values tend a wider pasture.")
    double radiusAtTopLevel = 18;
    @ConfigDoc(value = "How far past its reach a keeper still finds its strays, in blocks.", impact = "Higher values bring back animals that wandered farther.")
    double strayReach = 16;
    @ConfigDoc(value = "Health each tended animal regains per round at level 1.", impact = "Higher values heal more each round.")
    double healAtFirstLevel = 3;
    @ConfigDoc(value = "Health each tended animal regains per round at the top level.", impact = "Higher values heal more each round.")
    double healAtTopLevel = 6;
    @ConfigDoc(value = "Most animals each keeper tends per round at level 1.", impact = "Higher values tend bigger herds.")
    double animalsAtFirstLevel = 6;
    @ConfigDoc(value = "Most animals each keeper tends per round at the top level.", impact = "Higher values tend bigger herds.")
    double animalsAtTopLevel = 16;
    @ConfigDoc(value = "Time between keeper rounds at level 1, in milliseconds.", impact = "Lower values tend more often.")
    double roundMillisAtFirstLevel = 20000;
    @ConfigDoc(value = "Time between keeper rounds at the top level, in milliseconds.", impact = "Lower values tend more often.")
    double roundMillisAtTopLevel = 10000;
  }
}
