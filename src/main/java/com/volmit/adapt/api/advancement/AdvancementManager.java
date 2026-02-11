package com.volmit.adapt.api.advancement;

import com.fren_gor.ultimateAdvancementAPI.AdvancementMain;
import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.J;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.volmit.adapt.Adapt.instance;

public class AdvancementManager {
    private final AdvancementMain main;
    private final Map<String, Advancement> advancements;
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(false);

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
        advancements = new HashMap<>();
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
        if (!AdaptConfig.get().isAdvancements() || !enabled.get() || p == null || !p.isOnline()) return;
        Advancement advancement = advancements.get(key);
        try {
            J.s(() -> {
                if (!p.isOnline()) return;
                advancement.grant(player.getPlayer(), true);
            }, 5);
        } catch (Exception e) {
            Adapt.error("Failed to grant advancement " + key);
        }

        if (toast) {
            try {
                advancement.displayToastToPlayer(p);
            } catch (Exception e) {
                Adapt.error("Failed to grant advancement " + key + " Reattaching!");
            }
        }
    }

    public void unlockExisting(AdaptPlayer player) {
        if (!AdaptConfig.get().isAdvancements() || !enabled.get()) return;
        J.s(() -> {
            instance.getAdaptServer()
                    .getSkillRegistry()
                    .getSkills()
                    .stream()
                    .map(Skill::buildAdvancements)
                    .forEach(aa -> unlockExisting(player, aa));

            player.getAdvancementHandler().setReady(true);
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
            return;
        }

        main.disable();
        enabled.set(false);
        loaded.set(false);
    }
}
