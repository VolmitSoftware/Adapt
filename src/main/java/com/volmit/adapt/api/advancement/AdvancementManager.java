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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.volmit.adapt.Adapt.instance;

public class AdvancementManager {
    private final AdvancementMain main;
    private final Map<String, Advancement> advancements;

    public AdvancementManager() {
        main = new AdvancementMain(instance);
        main.load();
        advancements = new HashMap<>();
    }

    AdvancementTab createAdvancementTab(String namespace) {
        return main.createAdvancementTab(instance, "adapt_" + namespace);
    }

    public void grant(AdaptPlayer player, String key, boolean toast) {
        player.getData().ensureGranted(key);
        Advancement advancement = advancements.get(key);
        try {
            J.s(() -> advancement.grant(player.getPlayer(), true), 5);
        } catch (Exception e) {
            Adapt.error("Failed to grant advancement " + key);
        }

        if (toast) {
            if (player.getPlayer() != null) {
                try {
                    advancement.displayToastToPlayer(player.getPlayer());
                } catch (Exception e) {
                    Adapt.error("Failed to grant advancement " + key + " Reattaching!");
                }
            }
        }
    }

    public void unlockExisting(AdaptPlayer player) {
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
        if (AdaptConfig.get().isUseSql()) {
            AdaptConfig.SqlSettings sql = AdaptConfig.get().getSql();
            main.enableMySQL(sql.getUsername(), sql.getPassword(), sql.getDatabase(), sql.getHost(), sql.getPort(), sql.getPoolSize(), sql.getConnectionTimeout());
        } else {
            main.enableSQLite(instance.getDataFile("data", "advancements.db"));
        }

        for (Skill<?> i : instance.getAdaptServer().getSkillRegistry().getSkills()) {
            AdaptAdvancement aa = i.buildAdvancements();
            Set<BaseAdvancement> set = new HashSet<>();

            for (var a : aa.toAdvancements().reverse()) {
                advancements.put(a.getKey().getKey(), a);
                if (a instanceof RootAdvancement) {
                } else if (a instanceof BaseAdvancement) set.add(null);
            }

            Adapt.error("Root advancement not found for " + i.getId());
        }
    }

    public void disable() {
        main.disable();
    }
}
