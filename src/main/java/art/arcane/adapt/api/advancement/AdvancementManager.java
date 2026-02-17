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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.adapt.Adapt.instance;

public class AdvancementManager {
    private final AdvancementMain main;
    private final Map<String, Advancement> advancements;
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicBoolean runtimeSchedulerUnsupported = new AtomicBoolean(false);

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

    public void grant(AdaptPlayer player, String key, boolean toast) {
        player.getData().ensureGranted(key);
        Player p = player.getPlayer();
        if (!AdaptConfig.get().isAdvancements() || !enabled.get() || runtimeSchedulerUnsupported.get() || p == null || !p.isOnline()) return;
        Advancement advancement = advancements.get(key);
        if (advancement == null) {
            Adapt.verbose("Advancement key '" + key + "' is not registered; skipping grant.");
            return;
        }

        J.runEntity(p, () -> {
            if (!p.isOnline()) {
                return;
            }

            attemptGrant(p, advancement, key, toast, true);
        }, 5);
    }

    private void attemptGrant(Player player, Advancement advancement, String key, boolean toast, boolean allowRetryOnGlobal) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            advancement.grant(player, true);
        } catch (Throwable t) {
            if (isUserNotLoadedError(t)) {
                Adapt.verbose("Skipped advancement grant '" + key + "' because user data is not loaded yet for " + player.getName() + ".");
                return;
            }

            if (isSchedulerContextMismatch(t)) {
                if (J.isFoliaThreading()) {
                    markRuntimeSchedulerUnsupported(t);
                    return;
                }

                if (allowRetryOnGlobal) {
                    J.s(() -> attemptGrant(player, advancement, key, toast, false), 1);
                    return;
                }
            }

            Adapt.warn("Failed to grant advancement '" + key + "' for " + player.getName() + ": " + summarizeThrowable(t));
            return;
        }

        if (!toast) {
            return;
        }

        try {
            advancement.displayToastToPlayer(player);
        } catch (Throwable t) {
            if (isUserNotLoadedError(t)) {
                Adapt.verbose("Skipped advancement toast '" + key + "' because user data is not loaded yet for " + player.getName() + ".");
                return;
            }

            if (isSchedulerContextMismatch(t)) {
                if (J.isFoliaThreading()) {
                    markRuntimeSchedulerUnsupported(t);
                    return;
                }

                if (allowRetryOnGlobal) {
                    J.s(() -> attemptToast(player, advancement, key, false), 1);
                    return;
                }
            }

            Adapt.warn("Failed to display advancement toast '" + key + "' for " + player.getName() + ": " + summarizeThrowable(t));
        }
    }

    private void attemptToast(Player player, Advancement advancement, String key, boolean allowRetryOnGlobal) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            advancement.displayToastToPlayer(player);
        } catch (Throwable t) {
            if (isUserNotLoadedError(t)) {
                Adapt.verbose("Skipped advancement toast '" + key + "' because user data is not loaded yet for " + player.getName() + ".");
                return;
            }

            if (isSchedulerContextMismatch(t)) {
                if (J.isFoliaThreading()) {
                    markRuntimeSchedulerUnsupported(t);
                    return;
                }

                if (allowRetryOnGlobal) {
                    J.s(() -> attemptToast(player, advancement, key, false), 1);
                    return;
                }
            }

            Adapt.warn("Failed to display advancement toast '" + key + "' for " + player.getName() + ": " + summarizeThrowable(t));
        }
    }

    private void markRuntimeSchedulerUnsupported(Throwable throwable) {
        if (!runtimeSchedulerUnsupported.compareAndSet(false, true)) {
            return;
        }

        Adapt.info("UltimateAdvancementAPI live packet grants/toasts are unavailable on this Folia runtime; stored advancement grants will continue without live packets/toasts.");
        if (throwable != null) {
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
        if (!AdaptConfig.get().isAdvancements() || !enabled.get()) return;
        if (player == null || handler == null) {
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

        J.runEntity(target, () -> {
            instance.getAdaptServer()
                    .getSkillRegistry()
                    .getSkills()
                    .stream()
                    .map(Skill::buildAdvancements)
                    .forEach(aa -> unlockExisting(player, aa));

            handler.setReady(true);
        }, 20);
    }

    private void unlockExisting(AdaptPlayer player, AdaptAdvancement aa) {
        if (aa.getChildren() != null) {
            for (AdaptAdvancement i : aa.getChildren()) {
                unlockExisting(player, i);
            }
        }

        if (player.getData().isGranted(aa.getKey())) {
            grant(player, aa.getKey(), false);
        }
    }

    public void enable() {
        if (main == null) {
            return;
        }

        runtimeSchedulerUnsupported.set(false);

        if (loaded.compareAndSet(false, true))
            main.load();

        if (!AdaptConfig.get().isAdvancements() || !enabled.compareAndSet(false, true))
            return;
        if (AdaptConfig.get().isUseSql()) {
            AdaptConfig.SqlSettings sql = AdaptConfig.get().getSql();
            main.enableMySQL(sql.getUsername(), sql.getPassword(), sql.getDatabase(), sql.getHost(), sql.getPort(), sql.getPoolSize(), sql.getConnectionTimeout());
        } else {
            main.enableSQLite(instance.getDataFile("data", "advancements.db"));
        }

        if (J.isFoliaThreading() && isLegacyAsyncSchedulerUnsupported()) {
            markRuntimeSchedulerUnsupported(null);
        }

        for (Skill<?> i : instance.getAdaptServer().getSkillRegistry().getSkills()) {
            AdaptAdvancement aa = i.buildAdvancements();
            Set<BaseAdvancement> set = new HashSet<>();
            RootAdvancement root = null;

            for (var a : aa.toAdvancements().reverse()) {
                advancements.put(a.getKey().getKey(), a);
                if (a instanceof RootAdvancement r && root == null) root = r;
                else if (a instanceof BaseAdvancement b) set.add(b);
            }

            if (root == null) {
                Adapt.error("Root advancement not found for " + i.getId());
                continue;
            }
            root.getAdvancementTab().registerAdvancements(root, set);
        }
    }

    public void disable() {
        if (main == null) {
            enabled.set(false);
            loaded.set(false);
            runtimeSchedulerUnsupported.set(false);
            return;
        }

        main.disable();
        enabled.set(false);
        loaded.set(false);
        runtimeSchedulerUnsupported.set(false);
    }

    private boolean isLegacyAsyncSchedulerUnsupported() {
        try {
            BukkitTask probe = Bukkit.getScheduler().runTaskTimerAsynchronously(instance, () -> {
            }, 1L, 1L);
            probe.cancel();
            return false;
        } catch (UnsupportedOperationException ignored) {
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
