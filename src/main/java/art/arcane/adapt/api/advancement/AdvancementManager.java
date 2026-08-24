package art.arcane.adapt.api.advancement;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdvancementHandler;
import art.arcane.adapt.util.common.scheduling.J;
import com.fren_gor.ultimateAdvancementAPI.AdvancementMain;
import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static art.arcane.adapt.Adapt.instance;

public class AdvancementManager {
  private static final int ONLINE_RESTORE_BATCH_SIZE = 32;
  private static final int RESTORE_KEYS_PER_BATCH = 64;
  private static final int RESTORE_SETTLE_DELAY_TICKS = 60;
  private static final int RESTORE_STAGGER_TICKS = 20;
  private static final int USER_LOAD_RETRIES = 12;
  private final AdvancementMain main;
  private final Map<String, Advancement> advancements;
  private final AtomicBoolean loaded = new AtomicBoolean(false);
  private final AtomicBoolean enabled = new AtomicBoolean(false);
  private final AtomicBoolean catalogReady = new AtomicBoolean(false);
  private final AtomicBoolean runtimeSchedulerUnsupported = new AtomicBoolean(false);
  private final AtomicBoolean synchronizationScheduled = new AtomicBoolean(false);
  private final AtomicLong synchronizationRevision = new AtomicLong();
  private final AtomicReference<SynchronizationRequest> pendingSynchronization = new AtomicReference<>();
  private volatile List<String> skillRootKeys = List.of();
  private volatile long registeredCatalogRevision = -1L;

  public AdvancementManager() {
    AdvancementMain loadedMain = null;
    try {
      loadedMain = new AdvancementMain(instance);
      loadedMain.load();
      loaded.set(true);
    } catch (Throwable e) {
      loadedMain = null;
      Adapt.warn("UltimateAdvancementAPI is unavailable: " + e.getMessage() + ". Advancements will be disabled.");
    }

    main = loadedMain;
    advancements = new ConcurrentHashMap<>();
  }

  AdvancementTab createAdvancementTab(String namespace) {
    if (main == null) {
      throw new IllegalStateException("UltimateAdvancementAPI is unavailable");
    }

    return main.createAdvancementTab(instance, "adapt_" + namespace);
  }

  public boolean grant(AdaptPlayer player, String key, boolean toast) {
    if (player == null || key == null || key.isBlank() || !AdaptConfig.get().isAdvancements()) {
      return false;
    }
    Advancement advancement = advancements.get(key);
    if (!player.getData().ensureGranted(key)) {
      return false;
    }
    if (!canRenderGrant(
        enabled.get(),
        catalogReady.get(),
        advancement != null
    )) {
      return true;
    }

    long catalogRevision = registeredCatalogRevision;
    if (!isCatalogReady(catalogRevision)) {
      return true;
    }
    Player p = player.getPlayer();
    if (runtimeSchedulerUnsupported.get() || p == null || !p.isOnline()) {
      return true;
    }
    J.runEntity(p, () -> {
      if (!p.isOnline()) {
        return;
      }

      attemptGrant(p, advancement, key, catalogRevision, toast, true, true, USER_LOAD_RETRIES);
    }, 5);
    return true;
  }

  private void attemptGrant(Player player, Advancement advancement, String key, long catalogRevision,
                            boolean toast, boolean allowRetryOnGlobal, boolean allowRetryOnEntity,
                            int userLoadRetriesRemaining) {
    if (!isCatalogReady(catalogRevision) || player == null || !player.isOnline()) {
      return;
    }

    try {
      advancement.grant(player, true);
    } catch (Throwable t) {
      if (isUserNotLoadedError(t)) {
        if (userLoadRetriesRemaining > 0) {
          J.s(() -> attemptGrant(player, advancement, key, catalogRevision, toast, allowRetryOnGlobal, allowRetryOnEntity, userLoadRetriesRemaining - 1), 5);
          return;
        }

        Adapt.verbose("Skipped advancement grant '" + key + "' because user data is not loaded yet for " + player.getName() + " after retries.");
        return;
      }

      if (isSchedulerContextMismatch(t)) {
        if (allowRetryOnGlobal) {
          J.s(() -> attemptGrant(player, advancement, key, catalogRevision, toast, false, allowRetryOnEntity, userLoadRetriesRemaining), 1);
          return;
        }

        if (allowRetryOnEntity && J.runEntity(player, () -> attemptGrant(player, advancement, key, catalogRevision, toast, false, false, userLoadRetriesRemaining), 1)) {
          return;
        }

        markRuntimeSchedulerUnsupported(t);
        return;
      }

      Adapt.warn("Failed to grant advancement '" + key + "' for " + player.getName() + ": " + summarizeThrowable(t));
      Adapt.error(t);
      return;
    }

    if (shouldDisplayToast(toast, AdaptConfig.get().isAdvancementUnlockToasts())) {
      attemptToast(player, advancement, key, catalogRevision, allowRetryOnGlobal, allowRetryOnEntity, userLoadRetriesRemaining);
    }
  }

  static boolean shouldDisplayToast(boolean requested, boolean enabled) {
    return requested && enabled;
  }

  private void attemptToast(Player player, Advancement advancement, String key, long catalogRevision,
                            boolean allowRetryOnGlobal, boolean allowRetryOnEntity,
                            int userLoadRetriesRemaining) {
    if (!isCatalogReady(catalogRevision) || player == null || !player.isOnline()) {
      return;
    }

    try {
      advancement.displayToastToPlayer(player);
    } catch (Throwable t) {
      if (isUserNotLoadedError(t)) {
        if (userLoadRetriesRemaining > 0) {
          J.s(() -> attemptToast(player, advancement, key, catalogRevision, allowRetryOnGlobal, allowRetryOnEntity, userLoadRetriesRemaining - 1), 5);
          return;
        }

        Adapt.verbose("Skipped advancement toast '" + key + "' because user data is not loaded yet for " + player.getName() + " after retries.");
        return;
      }

      if (isSchedulerContextMismatch(t)) {
        if (allowRetryOnGlobal) {
          J.s(() -> attemptToast(player, advancement, key, catalogRevision, false, allowRetryOnEntity, userLoadRetriesRemaining), 1);
          return;
        }

        if (allowRetryOnEntity && J.runEntity(player, () -> attemptToast(player, advancement, key, catalogRevision, false, false, userLoadRetriesRemaining), 1)) {
          return;
        }

        markRuntimeSchedulerUnsupported(t);
        return;
      }

      Adapt.warn("Failed to display advancement toast '" + key + "' for " + player.getName() + ": " + summarizeThrowable(t));
      Adapt.error(t);
    }
  }

  private void markRuntimeSchedulerUnsupported(Throwable throwable) {
    if (!runtimeSchedulerUnsupported.compareAndSet(false, true)) {
      return;
    }

    Adapt.info("UltimateAdvancementAPI live packet grants/toasts are unavailable on this Folia runtime; stored advancement grants will continue without live packets/toasts.");
    if (throwable != null) {
      Adapt.warn("UltimateAdvancementAPI live packet fallback cause: " + summarizeThrowable(throwable));
      Adapt.verbose("UltimateAdvancementAPI fallback cause: " + summarizeThrowable(throwable));
    }
  }

  private boolean isUserNotLoadedError(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if ("UserNotLoadedException".equals(current.getClass().getSimpleName())) {
        return true;
      }

      current = current.getCause();
    }

    return false;
  }

  private boolean isSchedulerContextMismatch(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof UnsupportedOperationException) {
        return true;
      }

      String message = current.getMessage();
      if (message != null) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("thread")
            || lower.contains("scheduler")
            || lower.contains("region")
            || lower.contains("primary thread")
            || lower.contains("asynchronously")) {
          return true;
        }
      }

      current = current.getCause();
    }

    return false;
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

    Player target = player.getPlayer();
    if (target == null || !target.isOnline()) {
      return;
    }

    if (runtimeSchedulerUnsupported.get()) {
      handler.setReady(true);
      return;
    }

    long catalogRevision = registeredCatalogRevision;
    List<String> roots = skillRootKeys;
    J.runEntity(target, () -> {
      if (!ensureRootsIfCurrent(player, roots, catalogRevision)) {
        return;
      }

      handler.setReady(true);
      List<String> grantedKeys = new ArrayList<>(player.getData().getAdvancements());
      J.runEntity(target, () -> restoreExisting(player, target, grantedKeys, catalogRevision, 0, true, true, USER_LOAD_RETRIES), 5);
    }, restoreInitialDelay(target.getUniqueId()));
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

  private void restoreExisting(AdaptPlayer player, Player target, List<String> registeredKeys,
                               long catalogRevision, int startIndex, boolean allowRetryOnGlobal,
                               boolean allowRetryOnEntity, int userLoadRetriesRemaining) {
    if (!isCatalogReady(catalogRevision) || runtimeSchedulerUnsupported.get() || !target.isOnline()) {
      return;
    }

    int endIndex = Math.min(startIndex + RESTORE_KEYS_PER_BATCH, registeredKeys.size());
    for (int index = startIndex; index < endIndex; index++) {
      if (runtimeSchedulerUnsupported.get()) {
        return;
      }
      String key = registeredKeys.get(index);
      if (!player.getData().isGranted(key)) {
        continue;
      }

      Advancement advancement = advancements.get(key);
      if (advancement == null) {
        continue;
      }

      try {
        advancement.grant(target, true);
      } catch (Throwable t) {
        if (isUserNotLoadedError(t)) {
          if (userLoadRetriesRemaining > 0) {
            int retryIndex = index;
            J.s(() -> restoreExisting(player, target, registeredKeys, catalogRevision, retryIndex, allowRetryOnGlobal, allowRetryOnEntity, userLoadRetriesRemaining - 1), 5);
          } else {
            Adapt.verbose("Stopped restoring advancements because user data is not loaded yet for " + target.getName() + " after retries.");
          }
          return;
        }

        if (isSchedulerContextMismatch(t)) {
          int retryIndex = index;
          if (allowRetryOnGlobal) {
            J.s(() -> restoreExisting(player, target, registeredKeys, catalogRevision, retryIndex, false, allowRetryOnEntity, userLoadRetriesRemaining), 1);
            return;
          }

          if (allowRetryOnEntity && J.runEntity(target, () -> restoreExisting(player, target, registeredKeys, catalogRevision, retryIndex, false, false, userLoadRetriesRemaining), 1)) {
            return;
          }

          markRuntimeSchedulerUnsupported(t);
          return;
        }

        Adapt.warn("Failed to restore advancement '" + key + "' for " + target.getName() + ": " + summarizeThrowable(t));
        Adapt.error(t);
      }
    }

    if (endIndex < registeredKeys.size()) {
      J.runEntity(target, () -> restoreExisting(player, target, registeredKeys, catalogRevision,
          endIndex, allowRetryOnGlobal, allowRetryOnEntity, userLoadRetriesRemaining), 1);
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
      J.s(this::runPendingSynchronization);
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
        main != null,
        enabled.get(),
        catalogReady.get(),
        registeredCatalogRevision,
        requestedCatalogRevision
    );

    if (action == SynchronizationAction.DISABLE) {
      shutdownMain(null);
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
      shutdownMain(null);
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
      J.s(this::runPendingSynchronization);
    }
  }

  private boolean enableCatalog(long requestedCatalogRevision) {
    runtimeSchedulerUnsupported.set(false);
    catalogReady.set(false);
    if (!AdaptConfig.get().isAdvancements() || !enabled.compareAndSet(false, true)) {
      return false;
    }

    try {
      if (loaded.compareAndSet(false, true)) {
        main.load();
      }

      advancements.clear();
      List<String> roots = new ArrayList<>();

      if (AdaptConfig.get().getSql().isEnabled()) {
        AdaptConfig.SqlSettings sql = AdaptConfig.get().getSql();
        main.enableMySQL(sql.getUsername(), sql.getPassword(), sql.getDatabase(), sql.getHost(), sql.getPort(), sql.getPoolSize(), sql.getConnectionTimeout());
      } else {
        main.enableSQLite(instance.getDataFile("data", "advancements.db"));
      }

      for (Skill<?> i : instance.getAdaptServer().getSkillRegistry().getSkills()) {
        AdaptAdvancement aa = i.buildAdvancements();
        String rootKey = aa.getKey();
        if (rootKey != null && !rootKey.isBlank() && rootKey.startsWith("skill_")) {
          roots.add(rootKey);
        }
        Set<BaseAdvancement> set = new HashSet<>();
        RootAdvancement root = null;

        for (Advancement a : aa.toAdvancements().reverse()) {
          advancements.put(a.getKey().getKey(), a);
          if (a instanceof RootAdvancement r && root == null) {
            root = r;
          } else if (a instanceof BaseAdvancement b) {
            set.add(b);
          }
        }

        if (root == null) {
          Adapt.error("Root advancement not found for " + i.getId());
          continue;
        }

        root.getAdvancementTab().registerAdvancements(root, set);
      }
      skillRootKeys = List.copyOf(roots);
      registeredCatalogRevision = requestedCatalogRevision;
      catalogReady.set(true);
      return true;
    } catch (Throwable t) {
      Adapt.warn("UltimateAdvancementAPI failed during enable: " + summarizeThrowable(t) + ". Advancements will be disabled.");
      Adapt.error(t);
      shutdownMain(t);
      return false;
    }
  }

  private void restoreOnlinePlayers(long revision, long requestedCatalogRevision, Runnable afterReady) {
    List<AdaptPlayer> players = List.copyOf(instance.getAdaptServer().getOnlineAdaptPlayerSnapshot());
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
      J.s(() -> restoreOnlinePlayerBatch(players, endIndex, revision, requestedCatalogRevision, afterReady), 1);
      return;
    }
    runAfterReady(revision, requestedCatalogRevision, afterReady, RESTORE_SETTLE_DELAY_TICKS);
  }

  private void runAfterReady(long revision, long requestedCatalogRevision, Runnable afterReady, int delayTicks) {
    if (afterReady == null) {
      return;
    }
    J.s(() -> {
      if (isSynchronizationCurrent(revision, requestedCatalogRevision)) {
        afterReady.run();
      }
    }, delayTicks);
  }

  private boolean isSynchronizationCurrent(long revision, long requestedCatalogRevision) {
    return synchronizationRevision.get() == revision && isCatalogReady(requestedCatalogRevision);
  }

  private long currentCatalogRevision() {
    if (instance == null || instance.getAdaptServer() == null
        || instance.getAdaptServer().getSkillRegistry() == null) {
      return 0L;
    }
    return instance.getAdaptServer().getSkillRegistry().getCatalogRevision();
  }

  private void reconcileCurrentCatalog() {
    if (instance != null && instance.getAdaptServer() != null
        && instance.getAdaptServer().getSkillRegistry() != null) {
      instance.getAdaptServer().getSkillRegistry().reconcileStatTrackersForOnlinePlayers();
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

  public synchronized void disable() {
    synchronizationRevision.incrementAndGet();
    pendingSynchronization.set(null);
    synchronizationScheduled.set(false);
    if (main == null) {
      resetState();
      return;
    }

    shutdownMain(null);
  }

  private void shutdownMain(Throwable cause) {
    advancements.clear();

    try {
      main.disable();
    } catch (Throwable t) {
      if (isPartialInitialisationError(t)) {
        Adapt.verbose("Skipped UltimateAdvancementAPI disable cleanup after partial initialisation: " + summarizeThrowable(t));
      } else {
        Adapt.warn("UltimateAdvancementAPI disable failed: " + summarizeThrowable(t));
        Adapt.error(t);
        if (cause != null) {
          Adapt.verbose("UltimateAdvancementAPI original enable failure: " + summarizeThrowable(cause));
        }
      }
    } finally {
      resetState();
    }
  }

  private boolean isPartialInitialisationError(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof IllegalStateException) {
        String message = current.getMessage();
        if (message != null && message.contains("has not been initialised yet")) {
          return true;
        }
      }

      current = current.getCause();
    }

    return false;
  }

  private void resetState() {
    skillRootKeys = List.of();
    registeredCatalogRevision = -1L;
    catalogReady.set(false);
    enabled.set(false);
    loaded.set(false);
    runtimeSchedulerUnsupported.set(false);
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
