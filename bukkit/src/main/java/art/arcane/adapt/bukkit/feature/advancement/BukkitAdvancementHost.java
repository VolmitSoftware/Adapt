package art.arcane.adapt.bukkit.feature.advancement;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.bukkit.platform.BukkitText;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.advancement.AdvancementNode;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import com.fren_gor.ultimateAdvancementAPI.AdvancementMain;
import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType;
import com.fren_gor.ultimateAdvancementAPI.advancement.multiParents.AbstractMultiParentsAdvancement;
import com.fren_gor.ultimateAdvancementAPI.database.TeamProgression;
import com.fren_gor.ultimateAdvancementAPI.util.AdvancementUtils;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Advancement tabs through UltimateAdvancementAPI, built the way {@code AdaptAdvancement} builds them: one tab per
 * root named by {@link AdvancementCatalog#tabName}, the root grant shows the tab, children follow Adapt's visibility rules. The
 * host owns the {@link AdvancementMain} lifecycle: a catalog restarts an enabled UAAPI, a non-empty one then opens
 * its database (MySQL when Adapt's SQL is enabled, SQLite in {@code data/advancements.db} otherwise) and registers
 * the tabs, and {@link #shutdown()} disables it. Grants and toasts retry while UAAPI has not loaded the player.
 */
public final class BukkitAdvancementHost implements AdvancementHost {
  /** Runs work on a player's owning thread after a delay; returns false when the player is gone. */
  @FunctionalInterface
  public interface PlayerScheduler {
    boolean run(Player player, Runnable task, int delayTicks);
  }

  /** Opens UAAPI's progression database on an enabled main. */
  @FunctionalInterface
  public interface DatabaseOpener {
    void open(AdvancementMain main);
  }

  private static final int USER_LOAD_RETRIES = 12;
  private static final int USER_LOAD_RETRY_TICKS = 5;

  private final Plugin plugin;
  private final Server server;
  private final AdvancementMain main;
  private final DatabaseOpener database;
  private final PlayerScheduler scheduler;
  private final PlatformLog log;
  private final Map<Id, Advancement> advancements = new ConcurrentHashMap<>();
  private final Map<UUID, Map<Id, Integer>> progress = new ConcurrentHashMap<>();
  private final AtomicBoolean schedulerUnsupported = new AtomicBoolean(false);
  private volatile AdvancementCatalog catalog = AdvancementCatalog.EMPTY;
  private boolean loaded;
  private boolean enabled;

  /** {@code main} must already be loaded, or null when UAAPI is unavailable. */
  public BukkitAdvancementHost(Plugin plugin, Server server, AdvancementMain main, DatabaseOpener database,
                               PlayerScheduler scheduler, PlatformLog log) {
    this.plugin = plugin;
    this.server = server;
    this.main = main;
    this.database = database;
    this.scheduler = scheduler;
    this.log = log;
    this.loaded = main != null;
  }

  /** Loads UAAPI for the plugin; advancements stay disabled when it cannot load. */
  public static BukkitAdvancementHost create(Plugin plugin, PlayerScheduler scheduler, PlatformLog log) {
    AdvancementMain loadedMain;
    try {
      loadedMain = new AdvancementMain(plugin);
      loadedMain.load();
    } catch (Throwable failure) {
      loadedMain = null;
      log.warn("UltimateAdvancementAPI is unavailable: " + failure.getMessage() + ". Advancements will be disabled.");
    }
    return new BukkitAdvancementHost(plugin, plugin.getServer(), loadedMain, BukkitAdvancementHost::openConfiguredDatabase,
        scheduler, log);
  }

  private static void openConfiguredDatabase(AdvancementMain main) {
    AdaptConfig.SqlSettings sql = AdaptConfig.get().getSql();
    if (sql.isEnabled()) {
      main.enableMySQL(sql.getUsername(), sql.getPassword(), sql.getDatabase(), sql.getHost(), sql.getPort(),
          sql.getPoolSize(), sql.getConnectionTimeout());
    } else {
      main.enableSQLite(AdaptEnvironment.dataFile("data", "advancements.db"));
    }
  }

  @Override
  public synchronized void setCatalog(List<AdvancementNode> roots) {
    AdvancementCatalog next = AdvancementCatalog.build(List.copyOf(roots));
    catalog = next;
    if (main == null) {
      advancements.clear();
      return;
    }
    if (enabled) {
      shutdownMain();
    } else {
      advancements.clear();
    }
    if (next.roots().isEmpty()) {
      return;
    }
    try {
      if (!loaded) {
        main.load();
        loaded = true;
      }
      database.open(main);
      enabled = true;
      for (AdvancementCatalog.Entry root : next.roots()) {
        register(next, root);
      }
    } catch (Throwable failure) {
      shutdownMain();
      throw failure;
    }
  }

  /** Disables UAAPI; called when the platform stops. */
  public synchronized void shutdown() {
    catalog = AdvancementCatalog.EMPTY;
    progress.clear();
    if (main == null) {
      advancements.clear();
      return;
    }
    shutdownMain();
  }

  private void shutdownMain() {
    advancements.clear();
    schedulerUnsupported.set(false);
    if (!loaded && !enabled) {
      return;
    }
    try {
      main.disable();
    } catch (Throwable failure) {
      if (partialInitialisation(failure)) {
        log.verbose("Skipped UltimateAdvancementAPI disable cleanup after partial initialisation: " + summarize(failure));
      } else {
        log.warn("UltimateAdvancementAPI disable failed: " + summarize(failure));
      }
    } finally {
      loaded = false;
      enabled = false;
    }
  }

  private void register(AdvancementCatalog next, AdvancementCatalog.Entry root) {
    AdvancementTab tab = main.createAdvancementTab(plugin, root.tab());
    AdvancementNode rootNode = root.node();
    TabRoot rootAdvancement = new TabRoot(tab, key(rootNode), display(rootNode), rootNode.background().toString(), rootNode.maxProgress());
    advancements.put(rootNode.key(), rootAdvancement);
    Set<BaseAdvancement> children = new HashSet<>();
    for (AdvancementCatalog.Entry entry : next.tree(root.node().key())) {
      Advancement parent = advancements.get(entry.parent());
      AdvancementNode node = entry.node();
      Child child = new Child(key(node), display(node), parent, node.maxProgress(), node.visibility());
      advancements.put(node.key(), child);
      children.add(child);
    }
    tab.registerAdvancements(rootAdvancement, children);
  }

  @Override
  public boolean isRegistered(Id key) {
    return catalog.contains(key);
  }

  @Override
  public boolean grant(PlayerRef player, Id key, boolean toast) {
    AdvancementCatalog.Entry entry = catalog.get(key);
    if (entry == null) {
      return false;
    }
    progress.computeIfAbsent(player.uuid(), (UUID id) -> new ConcurrentHashMap<>()).put(key, entry.node().maxProgress());
    Advancement advancement = advancement(key);
    Player target = server.getPlayer(player.uuid());
    if (advancement == null || target == null) {
      return true;
    }
    scheduler.run(target, () -> attempt(target, key, () -> {
      advancement.grant(target, true);
      if (toast) {
        advancement.displayToastToPlayer(target);
      }
    }, USER_LOAD_RETRIES), 0);
    return true;
  }

  @Override
  public void revoke(PlayerRef player, Id key) {
    Map<Id, Integer> known = progress.get(player.uuid());
    if (known != null) {
      known.remove(key);
    }
    Advancement advancement = advancement(key);
    Player target = server.getPlayer(player.uuid());
    if (advancement != null && target != null) {
      scheduler.run(target, () -> attempt(target, key, () -> advancement.revoke(target), USER_LOAD_RETRIES), 0);
    }
  }

  @Override
  public void setProgress(PlayerRef player, Id key, int value) {
    AdvancementCatalog.Entry entry = catalog.get(key);
    if (entry == null) {
      return;
    }
    int clamped = Math.max(0, Math.min(entry.node().maxProgress(), value));
    Map<Id, Integer> known = progress.computeIfAbsent(player.uuid(), (UUID id) -> new ConcurrentHashMap<>());
    if (clamped == 0) {
      known.remove(key);
    } else {
      known.put(key, clamped);
    }
    Advancement advancement = advancement(key);
    Player target = server.getPlayer(player.uuid());
    if (advancement != null && target != null) {
      scheduler.run(target, () -> attempt(target, key, () -> advancement.setProgression(target, clamped, true), USER_LOAD_RETRIES), 0);
    }
  }

  @Override
  public int progress(PlayerRef player, Id key) {
    Map<Id, Integer> known = progress.get(player.uuid());
    Integer value = known == null ? null : known.get(key);
    return value == null ? 0 : value;
  }

  @Override
  public void restore(PlayerRef player, Collection<Id> granted) {
    List<Id> keys = new ArrayList<>(granted.size());
    Map<Id, Integer> known = progress.computeIfAbsent(player.uuid(), (UUID id) -> new ConcurrentHashMap<>());
    for (Id key : granted) {
      AdvancementCatalog.Entry entry = catalog.get(key);
      if (entry != null) {
        known.put(key, entry.node().maxProgress());
        keys.add(key);
      }
    }
    Player target = server.getPlayer(player.uuid());
    if (target == null || keys.isEmpty()) {
      return;
    }
    scheduler.run(target, () -> restoreFrom(target, keys, 0, USER_LOAD_RETRIES), 0);
  }

  private void restoreFrom(Player target, List<Id> keys, int start, int retries) {
    for (int index = start; index < keys.size(); index++) {
      if (!target.isOnline() || schedulerUnsupported.get()) {
        return;
      }
      Advancement advancement = advancement(keys.get(index));
      if (advancement == null) {
        continue;
      }
      try {
        advancement.grant(target, true);
      } catch (RuntimeException failure) {
        if (userNotLoaded(failure) && retries > 0) {
          int resume = index;
          scheduler.run(target, () -> restoreFrom(target, keys, resume, retries - 1), USER_LOAD_RETRY_TICKS);
          return;
        }
        if (schedulerContextMismatch(failure)) {
          markSchedulerUnsupported(failure);
          return;
        }
        if (!userNotLoaded(failure)) {
          log.warn("Failed to restore advancement " + keys.get(index) + " for " + target.getName() + ": " + failure.getMessage());
        }
      }
    }
  }

  @Override
  public void toast(PlayerRef player, ItemRef icon, String titleMarkup, AdvancementFrame frame) {
    Player target = server.getPlayer(player.uuid());
    if (target == null) {
      return;
    }
    ItemStack stack = icon(icon);
    String title = BukkitText.legacy(Objects.requireNonNullElse(titleMarkup, ""));
    scheduler.run(target, () -> {
      if (target.isOnline()) {
        AdvancementUtils.displayToast(target, stack, title, frame(frame));
      }
    }, 0);
  }

  @Override
  public void forget(PlayerRef player) {
    progress.remove(player.uuid());
  }

  private void attempt(Player target, Id key, Runnable action, int retries) {
    if (!target.isOnline() || schedulerUnsupported.get()) {
      return;
    }
    try {
      action.run();
    } catch (RuntimeException failure) {
      if (userNotLoaded(failure)) {
        if (retries > 0) {
          scheduler.run(target, () -> attempt(target, key, action, retries - 1), USER_LOAD_RETRY_TICKS);
        }
        return;
      }
      if (schedulerContextMismatch(failure)) {
        markSchedulerUnsupported(failure);
        return;
      }
      log.warn("Advancement update " + key + " failed for " + target.getName() + ": " + failure.getMessage());
    }
  }

  private Advancement advancement(Id key) {
    return advancements.get(key);
  }

  private void markSchedulerUnsupported(Throwable failure) {
    if (schedulerUnsupported.compareAndSet(false, true)) {
      log.info("UltimateAdvancementAPI live packet grants/toasts are unavailable on this Folia runtime; stored advancement grants will continue without live packets/toasts.");
      log.warn("UltimateAdvancementAPI live packet fallback cause: " + summarize(failure));
    }
  }

  static boolean schedulerContextMismatch(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof UnsupportedOperationException) {
        return true;
      }
      String message = current.getMessage();
      if (message != null) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("thread") || lower.contains("scheduler") || lower.contains("region")
            || lower.contains("primary thread") || lower.contains("asynchronously")) {
          return true;
        }
      }
      current = current.getCause() == current ? null : current.getCause();
    }
    return false;
  }

  private static boolean partialInitialisation(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof IllegalStateException) {
        String message = current.getMessage();
        if (message != null && message.contains("has not been initialised yet")) {
          return true;
        }
      }
      current = current.getCause() == current ? null : current.getCause();
    }
    return false;
  }

  private static String summarize(Throwable failure) {
    Throwable root = failure;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    StringBuilder summary = new StringBuilder(failure.getClass().getSimpleName());
    if (failure.getMessage() != null && !failure.getMessage().isBlank()) {
      summary.append(": ").append(failure.getMessage());
    }
    if (root != failure) {
      summary.append(" | cause=").append(root.getClass().getSimpleName());
      if (root.getMessage() != null && !root.getMessage().isBlank()) {
        summary.append(": ").append(root.getMessage());
      }
    }
    return summary.toString();
  }

  static boolean userNotLoaded(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if ("UserNotLoadedException".equals(current.getClass().getSimpleName())) {
        return true;
      }
      current = current.getCause() == current ? null : current.getCause();
    }
    return false;
  }

  private static String key(AdvancementNode node) {
    return node.key().path();
  }

  private static AdvancementDisplay display(AdvancementNode node) {
    return new AdvancementDisplay.Builder(icon(node.icon()), BukkitText.legacy(node.title()))
        .description(BukkitText.legacy(node.description()))
        .frame(frame(node.frame()))
        .showToast(node.toast())
        .announceChat(node.announce())
        .x(node.x())
        .y(node.y())
        .build();
  }

  private static ItemStack icon(ItemRef icon) {
    ItemStack stack = icon == null ? null : BukkitRefs.item(icon);
    return stack == null || stack.getType().isAir() ? new ItemStack(Material.EMERALD) : stack.clone();
  }

  static AdvancementFrameType frame(AdvancementFrame frame) {
    return switch (frame) {
      case TASK -> AdvancementFrameType.TASK;
      case GOAL -> AdvancementFrameType.GOAL;
      case CHALLENGE -> AdvancementFrameType.CHALLENGE;
    };
  }

  static boolean visible(AdvancementVisibility visibility, Advancement advancement, TeamProgression progression) {
    if (visibility == AdvancementVisibility.ALWAYS) {
      return true;
    }
    if (advancement.getProgression(progression) > 0) {
      return true;
    }
    return switch (visibility) {
      case ALWAYS -> true;
      case HIDDEN -> false;
      case PARENT_GRANTED -> parentGranted(advancement, progression);
      case VANILLA -> parentOrGrandparentGranted(advancement, progression);
    };
  }

  private static boolean parentGranted(Advancement advancement, TeamProgression progression) {
    if (advancement instanceof AbstractMultiParentsAdvancement multiParent) {
      return multiParent.isAnyParentGranted(progression);
    }
    if (advancement instanceof BaseAdvancement base) {
      return base.getParent().isGranted(progression);
    }
    return false;
  }

  private static boolean parentOrGrandparentGranted(Advancement advancement, TeamProgression progression) {
    if (advancement instanceof AbstractMultiParentsAdvancement multiParent) {
      return multiParent.isAnyGrandparentGranted(progression);
    }
    if (!(advancement instanceof BaseAdvancement base)) {
      return false;
    }
    Advancement parent = base.getParent();
    if (parent.isGranted(progression)) {
      return true;
    }
    if (parent instanceof AbstractMultiParentsAdvancement multiParent) {
      return multiParent.isAnyParentGranted(progression);
    }
    if (parent instanceof BaseAdvancement baseParent) {
      return baseParent.getParent().isGranted(progression);
    }
    return false;
  }

  private final class TabRoot extends RootAdvancement {
    private TabRoot(AdvancementTab tab, String key, AdvancementDisplay display, String background, int maxProgression) {
      super(tab, key, display, background, maxProgression);
    }

    @Override
    public void grant(@NotNull Player player, boolean giveRewards) {
      super.grant(player, giveRewards);
      try {
        getAdvancementTab().showTab(player);
      } catch (RuntimeException failure) {
        log.verbose("Could not show advancement tab " + getKey() + " for " + player.getName() + ": " + failure.getMessage());
      }
    }

    @Override
    public void revoke(@NotNull Player player) {
      super.revoke(player);
      try {
        getAdvancementTab().hideTab(player);
      } catch (RuntimeException failure) {
        log.verbose("Could not hide advancement tab " + getKey() + " for " + player.getName() + ": " + failure.getMessage());
      }
    }
  }

  private static final class Child extends BaseAdvancement {
    private final AdvancementVisibility visibility;

    private Child(String key, AdvancementDisplay display, Advancement parent, int maxProgression, AdvancementVisibility visibility) {
      super(key, display, parent, maxProgression);
      this.visibility = visibility;
    }

    @Override
    public boolean isVisible(@NotNull TeamProgression progression) {
      return visible(visibility, this, progression);
    }
  }
}
