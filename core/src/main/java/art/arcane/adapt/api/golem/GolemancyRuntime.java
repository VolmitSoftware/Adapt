package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeSettings;
import art.arcane.adapt.api.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.api.golem.node.NodeWork;
import art.arcane.adapt.api.golem.plinth.PlinthHost;
import art.arcane.adapt.api.golem.plinth.PlinthService;
import art.arcane.adapt.api.golem.plinth.PlinthSettings;
import art.arcane.adapt.api.golem.reveal.GolemLabelPolicy;
import art.arcane.adapt.api.golem.reveal.RevealService;
import art.arcane.adapt.api.golem.reveal.RevealSettings;
import art.arcane.adapt.api.insight.EntityInsight;
import art.arcane.adapt.api.insight.GlossInsightPort;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.minion.MinionKind;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.AnnotatedHandlers;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class GolemancyRuntime {
  private static final MinionKind MINION_KIND = new BoundGolemKind();

  private static final GolemTargetPolicy COMPANION = new CompanionTargetPolicy();
  private static final Map<GolemCoreType, GolemTargetPolicy> TARGET_POLICIES = new ConcurrentHashMap<>();
  private static volatile GolemRegistry registry;
  private static volatile ContainerLids lids;
  private static volatile GolemBrains brains;
  private static volatile GolemSettings settings = GolemSettings.defaults();
  private static volatile GolemBrainSettings brainSettings = GolemBrainSettings.defaults();
  private static volatile GolemWorkSettings workSettings = GolemWorkSettings.defaults();
  private static volatile GolemTaskBoards boards;
  private static volatile RevealService reveal;
  private static volatile GolemNames names;
  private static volatile RevealSettings revealSettings = RevealSettings.defaults();
  private static volatile NodeService nodes;
  private static volatile NodeSettings nodeSettings = NodeSettings.defaults();
  private static volatile NodeWeavingPolicy nodeWeaving;
  private static volatile PlinthService plinths;
  private static volatile PlinthSettings plinthSettings = PlinthSettings.defaults();
  private static volatile PlinthHost plinthHost;
  private static volatile Adaptation<?> protectionPolicy;
  private static volatile GolemLabelPolicy labelPolicy;
  private static volatile GolemBindListener bindListener = (owner, handle) -> {
  };

  private GolemancyRuntime() {
  }

  public static synchronized void startRuntime() {
    MinionIdentity.registerKind(MINION_KIND);
    if (registry != null) {
      return;
    }
    AdaptEventBus bus = AdaptCore.get().getBus();
    GolemNames createdNames = new GolemNames(AdaptPorts.get(GlossInsightPort.class, GlossInsightPort.NONE));
    names = createdNames;
    createdNames.start();
    AnnotatedHandlers.register(bus, createdNames);
    ContainerLids createdLids = ContainerLids.create();
    AnnotatedHandlers.register(bus, createdLids);
    lids = createdLids;
    GolemPlatform platform = new LiveGolemPlatform(createdLids);
    GolemTaskBoards createdBoards = new GolemTaskBoards(key -> nodes().flatMap(service -> service.node(key)));
    GolemBrains createdBrains = new GolemBrains(new GolemBrain.Context(platform, GolemancyRuntime::brainSettings, GolemancyRuntime::targetPolicy,
        GolemancyRuntime::workSettings, createdBoards::board), GolemancyRuntime::controllerFor);
    boards = createdBoards;
    GolemRegistry created = new GolemRegistry(platform, GolemancyRuntime::settings, createdBrains);
    created.activateRuntime();
    brains = createdBrains;
    registry = created;
    adoptLoaded(created);
    created.joinOnlineOwners();
    RevealService createdReveal = new RevealService(GolemancyRuntime::revealSettings);
    createdReveal.activateRuntime();
    reveal = createdReveal;
    EntityInsight.register(createdReveal);
    NodeService createdNodes = NodeService.create(GolemancyRuntime::nodeSettings, GolemancyRuntime::revealSettings);
    createdNodes.setScanHook(NodeWork.create(createdBoards, createdNodes));
    createdNodes.activateRuntime();
    nodes = createdNodes;
    PlinthService createdPlinths = PlinthService.create(GolemancyRuntime::plinthSettings, () -> plinthHost);
    createdPlinths.activateRuntime();
    plinths = createdPlinths;
    if (!Tasks.isFoliaThreading()) {
      createdNodes.indexLoadedChunks();
      createdPlinths.indexLoadedChunks();
    }
  }

  public static boolean shutdown(long timeoutMillis) {
    GolemRegistry local;
    ContainerLids localLids;
    RevealService localReveal;
    GolemNames localNames;
    NodeService localNodes;
    PlinthService localPlinths;
    synchronized (GolemancyRuntime.class) {
      MinionIdentity.unregisterKind(MINION_KIND);
      local = registry;
      registry = null;
      localLids = lids;
      lids = null;
      brains = null;
      localReveal = reveal;
      reveal = null;
      localNames = names;
      names = null;
      localNodes = nodes;
      nodes = null;
      localPlinths = plinths;
      plinths = null;
      boards = null;
    }
    AdaptCore core = AdaptCore.current();
    AdaptEventBus bus = core == null ? null : core.getBus();
    if (localLids != null) {
      if (bus != null) {
        bus.unregisterAll(localLids);
      }
      localLids.shutdown();
    }
    if (localPlinths != null) {
      localPlinths.shutdown();
    }
    if (localNodes != null) {
      localNodes.shutdown();
    }
    if (localNames != null && bus != null) {
      bus.unregisterAll(localNames);
    }
    if (localReveal != null) {
      EntityInsight.unregister(localReveal);
      localReveal.shutdown();
    }
    if (local == null) {
      return true;
    }
    boolean flushed;
    try {
      flushed = local.flushAll(timeoutMillis);
    } catch (RuntimeException failure) {
      AdaptLog.warn("Bound golem shutdown flush failed.", failure);
      flushed = false;
    }
    local.unregister();
    return flushed;
  }

  public static Optional<GolemRegistry> registry() {
    return Optional.ofNullable(registry);
  }

  public static Optional<GolemHandle> handle(EntityRef entity) {
    GolemRegistry local = registry;
    return local == null ? Optional.empty() : local.handle(entity);
  }

  public static Optional<GolemBrain> brain(EntityRef entity) {
    GolemBrains local = brains;
    return local == null ? Optional.empty() : local.brain(entity);
  }

  public static Optional<RevealService> reveal() {
    return Optional.ofNullable(reveal);
  }

  public static Optional<GolemNames> names() {
    return Optional.ofNullable(names);
  }

  public static Optional<NodeService> nodes() {
    return Optional.ofNullable(nodes);
  }

  public static Optional<GolemTaskBoards> boards() {
    return Optional.ofNullable(boards);
  }

  public static GolemWorkSettings workSettings() {
    return workSettings;
  }

  public static void configureWork(GolemWorkSettings next) {
    workSettings = Objects.requireNonNull(next);
  }

  public static NodeSettings nodeSettings() {
    return nodeSettings;
  }

  public static void configureNodes(NodeSettings next) {
    nodeSettings = Objects.requireNonNull(next);
  }

  public static Optional<NodeWeavingPolicy> nodeWeaving() {
    return Optional.ofNullable(nodeWeaving);
  }

  public static void setNodeWeaving(NodeWeavingPolicy policy) {
    nodeWeaving = Objects.requireNonNull(policy);
  }

  public static Optional<PlinthService> plinths() {
    return Optional.ofNullable(plinths);
  }

  public static PlinthSettings plinthSettings() {
    return plinthSettings;
  }

  public static void configurePlinths(PlinthSettings next) {
    plinthSettings = Objects.requireNonNull(next);
  }

  public static void setPlinthHost(PlinthHost host) {
    plinthHost = Objects.requireNonNull(host);
  }

  public static GolemSettings settings() {
    return settings;
  }

  public static GolemBrainSettings brainSettings() {
    return brainSettings;
  }

  public static void configureBrain(GolemBrainSettings next) {
    brainSettings = Objects.requireNonNull(next);
  }

  public static GolemTargetPolicy targetPolicy(List<GolemCoreType> cores) {
    for (GolemCoreType core : cores) {
      GolemTargetPolicy policy = TARGET_POLICIES.get(core);
      if (policy != null) {
        return policy;
      }
    }
    return COMPANION;
  }

  public static void setTargetPolicy(GolemCoreType core, GolemTargetPolicy policy) {
    TARGET_POLICIES.put(Objects.requireNonNull(core), Objects.requireNonNull(policy));
  }

  public static void clearTargetPolicy(GolemCoreType core, GolemTargetPolicy policy) {
    TARGET_POLICIES.remove(Objects.requireNonNull(core), Objects.requireNonNull(policy));
  }

  public static RevealSettings revealSettings() {
    return revealSettings;
  }

  public static Optional<Adaptation<?>> protectionPolicy() {
    return Optional.ofNullable(protectionPolicy);
  }

  public static void setProtectionPolicy(Adaptation<?> policy) {
    protectionPolicy = Objects.requireNonNull(policy);
  }

  public static Optional<GolemLabelPolicy> labelPolicy() {
    return Optional.ofNullable(labelPolicy);
  }

  public static void setLabelPolicy(GolemLabelPolicy policy) {
    labelPolicy = Objects.requireNonNull(policy);
  }

  public static GolemBindListener bindListener() {
    return bindListener;
  }

  public static void setBindListener(GolemBindListener listener) {
    bindListener = Objects.requireNonNull(listener);
  }

  public static void configure(GolemSettings next) {
    GolemSettings previous = settings;
    settings = Objects.requireNonNull(next);
    GolemRegistry local = registry;
    if (local != null && next.serverMaxActiveGolems() < previous.serverMaxActiveGolems()) {
      local.enforceCap();
    }
  }

  public static void configureReveal(RevealSettings next) {
    revealSettings = Objects.requireNonNull(next);
    RevealService local = reveal;
    if (local != null) {
      local.setInterval(next.pollMillis());
    }
  }

  static Optional<GolemController> controllerFor(GolemHandle handle) {
    MobRef golem = handle.entity();
    return switch (handle.body()) {
      case COPPER -> GolemBody.COPPER.entityType().equals(golem.type())
          ? Optional.of(new CopperGolemController(golem, () -> brainSettings.copperRepathTicks()))
          : Optional.empty();
      case FROST, IRON, BONE -> goalsOf(golem).map(goals -> new GoalGolemController(golem, handle.body(), goals));
    };
  }

  private static Optional<MobGoalsRef> goalsOf(MobRef golem) {
    try {
      return Optional.of(golem.goals());
    } catch (IllegalStateException unsupported) {
      return Optional.empty();
    }
  }

  private static void adoptLoaded(GolemRegistry target) {
    if (Tasks.isFoliaThreading()) {
      return;
    }
    for (WorldRef world : AdaptCore.platform().host().worlds()) {
      for (EntityRef entity : world.entities()) {
        if (entity instanceof MobRef mob && GolemRegistry.adoptable(mob)) {
          target.adopt(mob);
        }
      }
    }
  }
}
