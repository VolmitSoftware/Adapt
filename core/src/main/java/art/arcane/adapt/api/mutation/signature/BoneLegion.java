package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemWorkStatus;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.ServantFrame;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TragOul and Golemancy, either leading, replacing Convergence: a skeletal servant of yours takes the golem core you
 * hand it and works as a bone golem. Its lifetime only runs while it is idle; when it runs out the servant crumbles and
 * drops its core. Legionnaires still count toward your servant cap.
 */
public final class BoneLegion extends Signature<BoneLegion.Config> {
  private static final DataKey<Long> REMAINING = DataKey.adapt("bone-legion-remaining", DataCodecs.LONG);
  private static final long PERSIST_STEP_MILLIS = 5000L;
  private static final Set<BoneLegion> COPIES = ConcurrentHashMap.newKeySet();
  private static final Set<GolemWorkStatus> WORKING = EnumSet.of(GolemWorkStatus.MOVING, GolemWorkStatus.WORKING,
      GolemWorkStatus.DELIVERING, GolemWorkStatus.FETCHING_TOOL);
  private final Map<UUID, Legionnaire> legion = new ConcurrentHashMap<>();
  private final Map<UUID, MobRef> enlisting = playerState();
  private volatile long lastSurvey;

  public BoneLegion(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bone"));
    setInterval(1000);
    COPIES.add(this);
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, cap(level), 1);
    statLore(v, Form.duration(lifeMillis(level), 0), 2);
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
  public boolean hasTickDemand() {
    return !legion.isEmpty() || super.hasTickDemand();
  }

  @Override
  public void unregister() {
    legion.keySet().forEach(ServantFrame::discharge);
    legion.clear();
    COPIES.remove(this);
    super.unregister();
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerInteractEntityEvent e) {
    PlayerRef player = e.player();
    if (e.hand() != Hand.MAIN_HAND || player.isSneaking() || !(e.clicked() instanceof MobRef servant)
        || getActiveLevel(player) <= 0
        || GolemItems.coreOf(player.inventory().mainHand()).isEmpty()
        || !player.uuid().equals(TragoulSkeletalServant.getServantOwnerId(servant))) {
      return;
    }
    enlisting.put(player.uuid(), servant);
    fire(player, servant, null, false);
    enlisting.remove(player.uuid());
  }

  @Override
  protected Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    MobRef servant = enlisting.get(player.uuid());
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    ItemRef held = player.inventory().mainHand();
    Optional<GolemCoreType> core = GolemItems.coreOf(held);
    if (servant == null || registry.isEmpty() || core.isEmpty()
        || ServantFrame.legion(player.uuid()) >= cap(use.level())
        || registry.get().bindBlock(player, servant, GolemBody.BONE, core.get()).isPresent()
        || !ServantFrame.surrender(servant)) {
      return Outcome.MISSED;
    }
    GolemHandle handle;
    try {
      handle = registry.get().bind(player, servant, GolemBody.BONE, core.get());
    } catch (RuntimeException refused) {
      ServantFrame.crumble(servant);
      return Outcome.MISSED;
    }
    if (player.gameMode() != GameMode.CREATIVE) {
      held.setAmount(held.amount() - 1);
      player.inventory().setMainHand(held.amount() <= 0 ? AdaptCore.platform().host().items().empty() : held);
    }
    long life = lifeMillis(use.level());
    servant.data().set(REMAINING, life);
    ServantFrame.enlist(handle.entityId(), player.uuid());
    legion.put(handle.entityId(), new Legionnaire(handle, player.uuid(), life, System.currentTimeMillis(), life));
    fx(servant.location().add(0, 1, 0), FxPriority.GAMEPLAY).burst(Particles.SOUL, 10, 0.4D);
    return Outcome.lasting(servant, 20);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    if (now - lastSurvey >= getConfig().surveyMillis) {
      lastSurvey = now;
      survey(now);
    }
    for (Legionnaire soldier : legion.values()) {
      MobRef body = soldier.handle().entity();
      if (soldier.handle().isRetired() || body == null || !body.isValid() || body.isDead()) {
        if (legion.remove(soldier.handle().entityId(), soldier)) {
          ServantFrame.discharge(soldier.handle().entityId());
        }
        continue;
      }
      Tasks.runEntity(body, () -> age(soldier, now));
    }
  }

  /**
   * Adopts loaded legionnaires that no copy of this signature runs yet, such as ones loaded from disk, so a restart
   * never makes one permanent.
   */
  private void survey(long now) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      return;
    }
    for (GolemHandle handle : registry.get().loaded()) {
      MobRef body = handle.entity();
      Long remaining = body == null || handle.body() != GolemBody.BONE ? null : body.data().get(REMAINING);
      if (remaining != null && ServantFrame.enlist(handle.entityId(), handle.owner())) {
        legion.put(handle.entityId(), new Legionnaire(handle, handle.owner(), remaining, now, remaining));
      }
    }
  }

  /** Idle time runs the lifetime down; working time does not. An owner who lost the signature loses the legion. */
  private void age(Legionnaire soldier, long now) {
    GolemHandle handle = soldier.handle();
    PlayerRef owner = AdaptCore.platform().host().player(soldier.owner());
    boolean lost = owner != null && !held(owner);
    boolean working = WORKING.contains(handle.workStatus());
    long remaining = working ? soldier.remaining() : soldier.remaining() - Math.max(0L, now - soldier.checkedAt());
    if (!lost && (working || remaining > 0L)) {
      long persisted = soldier.persisted();
      if (persisted - remaining >= PERSIST_STEP_MILLIS) {
        handle.entity().data().set(REMAINING, remaining);
        persisted = remaining;
      }
      legion.put(handle.entityId(), new Legionnaire(handle, soldier.owner(), remaining, now, persisted));
      return;
    }
    legion.remove(handle.entityId());
    dismiss(handle);
  }

  private void dismiss(GolemHandle handle) {
    ServantFrame.discharge(handle.entityId());
    MobRef body = handle.entity();
    body.data().remove(REMAINING);
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isPresent() && registry.get().release(handle)) {
      return;
    }
    for (GolemCoreType core : handle.state().cores()) {
      body.world().dropItemNaturally(body.location().position(), GolemItems.core(core));
    }
    ServantFrame.crumble(body);
  }

  /** Whether the player holds Bone Legion in either order. */
  private static boolean held(PlayerRef player) {
    for (BoneLegion copy : COPIES) {
      if (copy.getActiveLevel(player) > 0) {
        return true;
      }
    }
    return false;
  }

  int cap(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().capAtFirstLevel, getConfig().capAtTopLevel));
  }

  long lifeMillis(int level) {
    return (long) SignatureKit.scaled(level, getMaxLevel(), getConfig().lifeMillisAtFirstLevel, getConfig().lifeMillisAtTopLevel);
  }

  private record Legionnaire(GolemHandle handle, UUID owner, long remaining, long checkedAt, long persisted) {
  }

  @ConfigDescription("Your skeletal servants take golem cores and work as bone golems; their lifetime only runs while idle.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Most legionnaires at level 1. They still count toward your servant cap.", impact = "Higher values field a bigger legion.")
    double capAtFirstLevel = 1;
    @ConfigDoc(value = "Most legionnaires at the top level. They still count toward your servant cap.", impact = "Higher values field a bigger legion.")
    double capAtTopLevel = 3;
    @ConfigDoc(value = "Idle lifetime of a legionnaire at level 1, in milliseconds.", impact = "Higher values keep idle legionnaires longer.")
    double lifeMillisAtFirstLevel = 30000;
    @ConfigDoc(value = "Idle lifetime of a legionnaire at the top level, in milliseconds.", impact = "Higher values keep idle legionnaires longer.")
    double lifeMillisAtTopLevel = 60000;
    @ConfigDoc(value = "Time between searches for legionnaires loaded from disk, in milliseconds.", impact = "Lower values find them sooner but search more often.")
    long surveyMillis = 10000;
    @ConfigDoc(value = "Cooldown between two enlistments, in milliseconds.", impact = "Higher values space enlistments further apart.")
    long cooldownMillis = 0;
  }
}
