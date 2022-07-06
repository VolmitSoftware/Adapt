package com.volmit.adapt.api.world;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.advancements.NameKey;
import com.volmit.adapt.util.advancements.advancement.Advancement;
import com.volmit.adapt.util.advancements.manager.AdvancementManager;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AdvancementHandler {
    private AdvancementManager manager;
    private AdaptPlayer player;
    private Map<Skill<?>, AdaptAdvancement> roots;
    private Map<String, Advancement> real;
    private boolean ready;

    public AdvancementHandler(AdaptPlayer player) {
        this.player = player;
        this.manager = new AdvancementManager(new NameKey("adapt", player.getPlayer().getUniqueId().toString()), player.getPlayer());
        roots = new HashMap<>();
        real = new HashMap<>();
        ready = false;
    }

    public void activate() {
        J.s(() -> {
            removeAllAdvancements();

            for(Skill i : player.getServer().getSkillRegistry().getSkills()) {
                AdaptAdvancement aa = i.buildAdvancements();
                roots.put(i, aa);

                for(Advancement j : aa.toAdvancements().reverse()) {
                    real.put(j.getName().getKey(), j);
                    try {
                        getManager().addAdvancement(j);
                    } catch(Throwable e) {
                        Adapt.error("Failed to register advancement " + j.getName().getKey());
                        e.printStackTrace();
                    }
                }

                unlockExisting(aa);

                J.s(() -> ready = true, 40);
            }
        }, 20);
    }

    public void grant(String key, boolean toast) {
        getPlayer().getData().ensureGranted(key);
        J.s(() -> getManager().grantAdvancement(player.getPlayer(), real.get(key)), 5);

        if(toast) {
            real.get(key).displayToast(getPlayer().getPlayer());
        }
    }

    public void grant(String key) {
        grant(key, true);
    }

    private void unlockExisting(AdaptAdvancement aa) {
        if(aa.getChildren() != null) {
            for(AdaptAdvancement i : aa.getChildren()) {
                unlockExisting(i);
            }
        }

        if(getPlayer().getData().isGranted(aa.getKey())) {
            J.s(() -> grant(aa.getKey(), false), 20);
        }
    }

    public void deactivate() {
        removeAllAdvancements();
    }

    public void removeAllAdvancements() {
        for(Advancement i : getManager().getAdvancements()) {
            getManager().removeAdvancement(i);
        }
    }
}
