package art.arcane.adapt.engine.advancement;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.server.hud.ClientToasts;
import art.arcane.adapt.client.view.ViewIcons;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.AdvancementHandler;
import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.advancement.AdvancementNode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.localization.LanguageAudience;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds the advancement tabs from the registered skills and hands them to the platform {@link AdvancementHost}.
 * Player data stays the source of truth: grants are stored first, then rendered when the catalog for the current
 * skill registry revision is live.
 */
public class AdvancementManager {
  private static final int ONLINE_RESTORE_BATCH_SIZE = 32;
  private static final int RESTORE_KEYS_PER_BATCH = 64;
  private static final int RESTORE_SETTLE_DELAY_TICKS = 60;
  private static final int RESTORE_STAGGER_TICKS = 20;
  private static final int GRANT_DELAY_TICKS = 5;
  private final Map<String, ServerFrame.Toast> registeredToasts = new ConcurrentHashMap<>();
  private final AtomicBoolean enabled = new AtomicBoolean(false);
  private final AtomicBoolean catalogReady = new AtomicBoolean(false);
  private final AtomicBoolean synchronizationScheduled = new AtomicBoolean(false);
  private final AtomicBoolean quitHandlerRegistered = new AtomicBoolean(false);
  private final AtomicLong synchronizationRevision = new AtomicLong();
  private final AtomicReference<SynchronizationRequest> pendingSynchronization = new AtomicReference<>();
  private volatile List<String> skillRootKeys = List.of();
  private volatile long registeredCatalogRevision = -1L;

  public boolean grant(AdaptPlayer player, String key, boolean toast) {
    if (player == null || key == null || key.isBlank() || !AdaptConfig.get().isAdvancements()) {
      return false;
    }
    if (!player.getData().ensureGranted(key)) {
      return false;
    }
    if (!canRenderGrant(enabled.get(), catalogReady.get(), registeredToasts.containsKey(key))) {
      unlistedToast(player, key, toast);
      return true;
    }

    long catalogRevision = registeredCatalogRevision;
    if (!isCatalogReady(catalogRevision)) {
      return true;
    }
    PlayerRef target = player.getPlayer();
    if (target == null || !target.isOnline()) {
      return true;
    }
    boolean showToast = shouldDisplayToast(toast, AdaptConfig.get().isAdvancementUnlockToasts());
    Tasks.runEntity(target, () -> {
      AdvancementHost host = host();
      if (host == null || !isCatalogReady(catalogRevision) || !target.isOnline()) {
        return;
      }

      try {
        renderGrant(host, target, key, showToast, registeredToasts.get(key));
      } catch (RuntimeException failure) {
        AdaptLog.warn("Failed to grant advancement '" + key + "' for " + target.name() + ": " + summarizeThrowable(failure));
        AdaptLog.error(failure);
      }
    }, GRANT_DELAY_TICKS);
    return true;
  }

  /**
   * The toast a client with native toasts shows the first time a player learns an adaptation that has no advancement:
   * an upgrade, a signature or a mutated skill's own. Players without those toasts see nothing, as before.
   */
  private void unlistedToast(AdaptPlayer player, String key, boolean toast) {
    PlayerRef target = player.getPlayer();
    Id adaptationId = AdaptAdvancement.adaptationOfKey(key);
    if (target == null || adaptationId == null || !enabled.get()
        || !shouldDisplayToast(toast, AdaptConfig.get().isAdvancementUnlockToasts()) || !ClientToasts.capable(target.uuid())) {
      return;
    }
    Adaptation<?> adaptation = server().getSkillRegistry().getAdaptation(adaptationId);
    if (adaptation == null || listed(adaptation)) {
      return;
    }
    ServerFrame.Toast frame = unlockToast(target.uuid(), adaptation);
    Tasks.runEntity(target, () -> ClientToasts.send(target.uuid(), frame), GRANT_DELAY_TICKS);
  }

  /** The unlock toast for {@code adaptation} in {@code viewer}'s language, in its skill's look. */
  public static ServerFrame.Toast unlockToast(UUID viewer, Adaptation<?> adaptation) {
    ToastKind kind = adaptation instanceof Signature<?> ? ToastKind.SIGNATURE : ToastKind.UNLOCK;
    String skill = adaptation.getSkill() == null ? "" : SkillHost.lineKey(adaptation.getSkill());
    return LanguageAudience.call(viewer, () -> ClientToasts.toast(kind, ViewIcons.adaptation(adaptation),
        adaptation.getDisplayName(), ToastQuips.unlockLine(adaptation), skill));
  }

  /** Whether {@code adaptation} has an advancement: an original of a registered skill. */
  static boolean listed(Adaptation<?> adaptation) {
    Skill<?> skill = adaptation.getSkill();
    return skill != null && !(skill instanceof MutatedSkill) && skill.getAdaptations().contains(adaptation);
  }

  /** Grants on the host; a player with client toasts gets the toast frame and no vanilla popup. */
  static void renderGrant(AdvancementHost host, PlayerRef target, String key, boolean showToast,
                          ServerFrame.Toast clientToast) {
    boolean sentToClient = showToast && clientToast != null && ClientToasts.capable(target.uuid())
        && ClientToasts.send(target.uuid(), clientToast);
    host.grant(target, AdaptAdvancement.advancementId(key), showToast && !sentToClient);
  }

  static boolean shouldDisplayToast(boolean requested, boolean enabled) {
    return requested && enabled;
  }

  private String summarizeThrowable(Throwable throwable) {
    if (throwable == null) {
      return "unknown";
    }

    Throwable root = throwable;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }

    StringBuilder summary = new StringBuilder(throwable.getClass().getSimpleName());
    appendMessage(summary, throwable.getMessage());

    if (root != throwable) {
      summary.append(" | cause=").append(root.getClass().getSimpleName());
      appendMessage(summary, root.getMessage());
    }

    return summary.toString();
  }

  private void appendMessage(StringBuilder builder, String message) {
    if (message != null && !message.isBlank()) {
      builder.append(": ").append(message);
    }
  }

  public void unlockExisting(AdaptPlayer player, AdvancementHandler handler) {
    if (player == null || handler == null) {
      return;
    }
    if (!AdaptConfig.get().isAdvancements() || !enabled.get()) {
      handler.setReady(true);
      return;
    }

    PlayerRef target = player.getPlayer();
    if (target == null || !target.isOnline()) {
      return;
    }

    long catalogRevision = registeredCatalogRevision;
    List<String> roots = skillRootKeys;
    Tasks.runEntity(target, () -> {
      if (!ensureRootsIfCurrent(player, roots, catalogRevision)) {
        return;
      }

      handler.setReady(true);
      List<String> grantedKeys = new ArrayList<>(player.getData().getAdvancements());
      Tasks.runEntity(target, () -> restoreExisting(player, target, grantedKeys, catalogRevision, 0), 5);
    }, restoreInitialDelay(target.uuid()));
  }

  private boolean ensureRootsIfCurrent(AdaptPlayer player, List<String> roots, long catalogRevision) {
    if (!isCatalogReady(catalogRevision)) {
      return false;
    }
    for (String root : roots) {
      player.getData().ensureGranted(root);
    }
    return true;
  }

  private void restoreExisting(AdaptPlayer player, PlayerRef target, List<String> grantedKeys,
                               long catalogRevision, int startIndex) {
    AdvancementHost host = host();
    if (host == null || !isCatalogReady(catalogRevision) || !target.isOnline()) {
      return;
    }

    int endIndex = Math.min(startIndex + RESTORE_KEYS_PER_BATCH, grantedKeys.size());
    List<Id> batch = new ArrayList<>(endIndex - startIndex);
    for (int index = startIndex; index < endIndex; index++) {
      String key = grantedKeys.get(index);
      if (player.getData().isGranted(key) && registeredToasts.containsKey(key)) {
        batch.add(AdaptAdvancement.advancementId(key));
      }
    }

    if (!batch.isEmpty()) {
      try {
        host.restore(target, batch);
      } catch (RuntimeException failure) {
        AdaptLog.warn("Failed to restore advancements for " + target.name() + ": " + summarizeThrowable(failure));
        AdaptLog.error(failure);
      }
    }

    if (endIndex < grantedKeys.size()) {
      Tasks.runEntity(target, () -> restoreExisting(player, target, grantedKeys, catalogRevision, endIndex), 1);
    }
  }

  public synchronized void enable() {
    synchronizeCatalogNow(currentCatalogRevision(), this::reconcileCurrentCatalog);
  }

  public synchronized void synchronizeCatalog(long requestedCatalogRevision, Runnable afterReady) {
    if (requestedCatalogRevision != registeredCatalogRevision) {
      catalogReady.set(false);
    }
    pendingSynchronization.set(new SynchronizationRequest(requestedCatalogRevision, afterReady));
    if (synchronizationScheduled.compareAndSet(false, true)) {
      Tasks.s(this::runPendingSynchronization);
    }
  }

  public boolean isCatalogReady(long requestedCatalogRevision) {
    return AdaptConfig.get().isAdvancements()
        && enabled.get()
        && catalogReady.get()
        && registeredCatalogRevision == requestedCatalogRevision;
  }

  private synchronized void synchronizeCatalogNow(long requestedCatalogRevision, Runnable afterReady) {
    long revision = synchronizationRevision.incrementAndGet();
    SynchronizationAction action = selectSynchronizationAction(
        AdaptConfig.get().isAdvancements(),
        host() != null,
        enabled.get(),
        catalogReady.get(),
        registeredCatalogRevision,
        requestedCatalogRevision
    );

    if (action == SynchronizationAction.DISABLE) {
      clearCatalog();
      return;
    }
    if (action == SynchronizationAction.NONE || action == SynchronizationAction.UNAVAILABLE) {
      return;
    }
    if (action == SynchronizationAction.READY) {
      runAfterReady(revision, requestedCatalogRevision, afterReady, 1);
      return;
    }
    if (action == SynchronizationAction.REBUILD) {
      resetState();
    }
    if (!enableCatalog(requestedCatalogRevision)) {
      return;
    }

    restoreOnlinePlayers(revision, requestedCatalogRevision, afterReady);
  }

  private void runPendingSynchronization() {
    SynchronizationRequest request = pendingSynchronization.get();
    if (request != null) {
      synchronizeCatalogNow(request.catalogRevision(), request.afterReady());
    }

    synchronizationScheduled.set(false);
    if (pendingSynchronization.get() != request && synchronizationScheduled.compareAndSet(false, true)) {
      Tasks.s(this::runPendingSynchronization);
    }
  }

  private boolean enableCatalog(long requestedCatalogRevision) {
    catalogReady.set(false);
    if (!AdaptConfig.get().isAdvancements() || !enabled.compareAndSet(false, true)) {
      return false;
    }

    try {
      AdvancementHost host = host();
      if (host == null) {
        throw new IllegalStateException("No advancement host is available");
      }
      List<AdvancementNode> trees = new ArrayList<>();
      List<String> roots = new ArrayList<>();
      Map<String, ServerFrame.Toast> toasts = new HashMap<>();
      for (Skill<?> skill : server().getSkillRegistry().getSkills()) {
        AdaptAdvancement advancement = ComponentHost.of(skill).buildAdvancements();
        String rootKey = advancement.getKey();
        if (AdaptAdvancement.isSkillKey(rootKey)) {
          roots.add(rootKey);
        }
        trees.add(advancement.toNode());
        collectToasts(advancement, toasts, SkillHost.lineKey(skill));
      }

      host.setCatalog(trees);
      registeredToasts.clear();
      registeredToasts.putAll(toasts);
      skillRootKeys = List.copyOf(roots);
      registeredCatalogRevision = requestedCatalogRevision;
      registerQuitHandler();
      catalogReady.set(true);
      return true;
    } catch (Throwable t) {
      AdaptLog.error("Advancements failed during enable: " + summarizeThrowable(t) + ". Advancements will be disabled.", t);
      clearCatalog();
      return false;
    }
  }

  private static void collectToasts(AdaptAdvancement advancement, Map<String, ServerFrame.Toast> toasts, String skill) {
    toasts.put(advancement.getKey(), advancement.clientToast(skill));
    if (advancement.getChildren() != null) {
      for (AdaptAdvancement child : advancement.getChildren()) {
        collectToasts(child, toasts, skill);
      }
    }
  }

  private void registerQuitHandler() {
    AdaptCore core = AdaptCore.current();
    if (core == null || !quitHandlerRegistered.compareAndSet(false, true)) {
      return;
    }
    core.getBus().register(this, PlayerQuitEvent.class, EventPriority.MONITOR, false, (PlayerQuitEvent event) -> {
      AdvancementHost host = host();
      if (host != null) {
        host.forget(event.player());
      }
    });
  }

  private void unregisterQuitHandler() {
    if (!quitHandlerRegistered.compareAndSet(true, false)) {
      return;
    }
    AdaptCore core = AdaptCore.current();
    if (core != null) {
      AdaptEventBus bus = core.getBus();
      bus.unregisterAll(this);
    }
  }

  private void restoreOnlinePlayers(long revision, long requestedCatalogRevision, Runnable afterReady) {
    List<AdaptPlayer> players = List.copyOf(server().getOnlineAdaptPlayerSnapshot());
    if (players.isEmpty()) {
      runAfterReady(revision, requestedCatalogRevision, afterReady, 1);
      return;
    }
    restoreOnlinePlayerBatch(players, 0, revision, requestedCatalogRevision, afterReady);
  }

  private void restoreOnlinePlayerBatch(List<AdaptPlayer> players, int startIndex, long revision,
                                        long requestedCatalogRevision, Runnable afterReady) {
    if (!isSynchronizationCurrent(revision, requestedCatalogRevision)) {
      return;
    }

    int endIndex = Math.min(startIndex + ONLINE_RESTORE_BATCH_SIZE, players.size());
    for (int index = startIndex; index < endIndex; index++) {
      AdaptPlayer player = players.get(index);
      unlockExisting(player, player.getAdvancementHandler());
    }

    if (endIndex < players.size()) {
      Tasks.s(() -> restoreOnlinePlayerBatch(players, endIndex, revision, requestedCatalogRevision, afterReady), 1);
      return;
    }
    runAfterReady(revision, requestedCatalogRevision, afterReady, RESTORE_SETTLE_DELAY_TICKS);
  }

  private void runAfterReady(long revision, long requestedCatalogRevision, Runnable afterReady, int delayTicks) {
    if (afterReady == null) {
      return;
    }
    Tasks.s(() -> {
      if (isSynchronizationCurrent(revision, requestedCatalogRevision)) {
        afterReady.run();
      }
    }, delayTicks);
  }

  private boolean isSynchronizationCurrent(long revision, long requestedCatalogRevision) {
    return synchronizationRevision.get() == revision && isCatalogReady(requestedCatalogRevision);
  }

  private long currentCatalogRevision() {
    AdaptServer server = server();
    if (server == null || server.getSkillRegistry() == null) {
      return 0L;
    }
    return server.getSkillRegistry().getCatalogRevision();
  }

  private void reconcileCurrentCatalog() {
    AdaptServer server = server();
    if (server != null && server.getSkillRegistry() != null) {
      server.getSkillRegistry().reconcileStatTrackersForOnlinePlayers();
    }
  }

  static SynchronizationAction selectSynchronizationAction(boolean advancementsConfigured,
                                                            boolean managerAvailable,
                                                            boolean managerEnabled,
                                                            boolean catalogReady,
                                                            long registeredCatalogRevision,
                                                            long requestedCatalogRevision) {
    if (!advancementsConfigured) {
      return managerEnabled ? SynchronizationAction.DISABLE : SynchronizationAction.NONE;
    }
    if (!managerAvailable) {
      return SynchronizationAction.UNAVAILABLE;
    }
    if (!managerEnabled) {
      return SynchronizationAction.ENABLE;
    }
    if (!catalogReady || registeredCatalogRevision != requestedCatalogRevision) {
      return SynchronizationAction.REBUILD;
    }
    return SynchronizationAction.READY;
  }

  static boolean canRenderGrant(boolean managerEnabled, boolean catalogReady, boolean keyRegistered) {
    return managerEnabled && catalogReady && keyRegistered;
  }

  static int restoreInitialDelay(UUID playerId) {
    if (playerId == null) {
      return 20;
    }
    return 20 + Math.floorMod(playerId.hashCode(), RESTORE_STAGGER_TICKS);
  }

  /**
   * Stops rendering and forgets the catalog without touching the host: on shutdown the platform tears its
   * advancement backend down itself.
   */
  public synchronized void disable() {
    synchronizationRevision.incrementAndGet();
    pendingSynchronization.set(null);
    synchronizationScheduled.set(false);
    unregisterQuitHandler();
    resetState();
  }

  private void clearCatalog() {
    try {
      AdvancementHost host = host();
      if (host != null) {
        host.setCatalog(List.of());
      }
    } catch (Throwable t) {
      AdaptLog.warn("Advancement catalog cleanup failed: " + summarizeThrowable(t));
      AdaptLog.error(t);
    } finally {
      resetState();
    }
  }

  private void resetState() {
    registeredToasts.clear();
    skillRootKeys = List.of();
    registeredCatalogRevision = -1L;
    catalogReady.set(false);
    enabled.set(false);
  }

  private static AdvancementHost host() {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getPlatform().advancements();
  }

  private static AdaptServer server() {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getAdaptServer();
  }

  enum SynchronizationAction {
    NONE,
    ENABLE,
    REBUILD,
    DISABLE,
    READY,
    UNAVAILABLE
  }

  private record SynchronizationRequest(long catalogRevision, Runnable afterReady) {
  }
}
