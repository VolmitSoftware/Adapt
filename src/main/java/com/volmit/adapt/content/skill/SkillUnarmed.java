/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.unarmed.UnarmedGlassCannon;
import com.volmit.adapt.content.adaptation.unarmed.UnarmedPower;
import com.volmit.adapt.content.adaptation.unarmed.UnarmedSuckerPunch;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class SkillUnarmed extends SimpleSkill<SkillUnarmed.Config> {
    public SkillUnarmed() {
        super("unarmed", Localizer.dLocalize("skill", "unarmed", "icon"));
        registerConfiguration(Config.class);
        setColor(C.YELLOW);
        setDescription(Localizer.dLocalize("skill", "unarmed", "description"));
        setDisplayName(Localizer.dLocalize("skill", "unarmed", "name"));
        setInterval(2579);
        registerAdaptation(new UnarmedSuckerPunch());
        registerAdaptation(new UnarmedPower());
        registerAdaptation(new UnarmedGlassCannon());
        setIcon(Material.FIRE_CHARGE);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.FIRE_CHARGE)
                .key("challenge_unarmed_100")
                .title(Localizer.dLocalize("advancement", "challenge_unarmed_100", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_unarmed_100", "description"))
                .model(CustomModel.get(Material.FIRE_CHARGE, "advancement", "unarmed", "challenge_unarmed_100"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.BLAZE_POWDER)
                        .key("challenge_unarmed_1k")
                        .title(Localizer.dLocalize("advancement", "challenge_unarmed_1k", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_unarmed_1k", "description"))
                        .model(CustomModel.get(Material.BLAZE_POWDER, "advancement", "unarmed", "challenge_unarmed_1k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.NETHER_STAR)
                                .key("challenge_unarmed_10k")
                                .title(Localizer.dLocalize("advancement", "challenge_unarmed_10k", "title"))
                                .description(Localizer.dLocalize("advancement", "challenge_unarmed_10k", "description"))
                                .model(CustomModel.get(Material.NETHER_STAR, "advancement", "unarmed", "challenge_unarmed_10k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_unarmed_100").goal(100).stat("unarmed.hits").reward(getConfig().challengeUnarmedReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_unarmed_1k").goal(1000).stat("unarmed.hits").reward(getConfig().challengeUnarmedReward * 2).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_unarmed_10k").goal(10000).stat("unarmed.hits").reward(getConfig().challengeUnarmedReward * 5).build());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!(e.getDamager() instanceof Player p)) {
            return;
        }

        shouldReturnForPlayer(p, e, () -> {
            if (e.getEntity().isDead()
                    || e.getEntity().isInvulnerable()
                    || p.isInvulnerable()) {
                return;
            }

            if (!checkValidEntity(e.getEntity().getType())) {
                return;
            }

            AdaptPlayer a = getPlayer(p);
            ItemStack hand = a.getPlayer().getInventory().getItemInMainHand();

            if (!isMelee(hand)) {
                a.getData().addStat("unarmed.hits", 1);
                a.getData().addStat("unarmed.damage", e.getDamage());
                xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().damageXPMultiplier * e.getDamage());
            }
        });
    }


    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        for (Player i : Bukkit.getOnlinePlayers()) {
            shouldReturnForPlayer(i, () -> checkStatTrackers(getPlayer(i)));
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage XPMultiplier for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageXPMultiplier = 8.44;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Unarmed Reward for the Unarmed skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeUnarmedReward = 500;
    }
}
