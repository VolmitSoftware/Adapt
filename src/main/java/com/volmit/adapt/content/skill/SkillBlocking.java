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

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.blocking.BlockingChainArmorer;
import com.volmit.adapt.content.adaptation.blocking.BlockingHorseArmorer;
import com.volmit.adapt.content.adaptation.blocking.BlockingMultiArmor;
import com.volmit.adapt.content.adaptation.blocking.BlockingSaddlecrafter;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Localizer;
import eu.endercentral.crazy_advancements.advancement.AdvancementDisplay;
import eu.endercentral.crazy_advancements.advancement.AdvancementVisibility;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillBlocking extends SimpleSkill<SkillBlocking.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillBlocking() {
        super("blocking", Localizer.dLocalize("skill", "blocking", "icon"));
        registerConfiguration(Config.class);
        setColor(C.DARK_GRAY);
        setDescription(Localizer.dLocalize("skill", "blocking", "description"));
        setDisplayName(Localizer.dLocalize("skill", "blocking", "name"));
        setInterval(5000);
        setIcon(Material.SHIELD);
        registerAdaptation(new BlockingMultiArmor());
        registerAdaptation(new BlockingChainArmorer());
        registerAdaptation(new BlockingSaddlecrafter());
        registerAdaptation(new BlockingHorseArmorer());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LEATHER_CHESTPLATE).key("challenge_block_1k")
                .title(Localizer.dLocalize("advancement", "challenge_block_1k", "title"))
                .description(Localizer.dLocalize("advancement", "challenge_block_1k", "description"))
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                        .icon(Material.CHAINMAIL_CHESTPLATE)
                        .key("challenge_block_5k")
                        .title(Localizer.dLocalize("advancement", "challenge_block_5k", "title"))
                        .description(Localizer.dLocalize("advancement", "challenge_block_5k", "description"))
                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.IRON_CHESTPLATE)
                                .key("challenge_block_50k")
                                .title(Localizer.dLocalize("advancement", "challenge_block_50k", "title"))
                                .description(Localizer.dLocalize("advancement", "challenge_block_50k", "description"))
                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                        .icon(Material.GOLDEN_CHESTPLATE)
                                        .key("challenge_block_500k")
                                        .title(Localizer.dLocalize("advancement", "challenge_block_500k", "title"))
                                        .description(Localizer.dLocalize("advancement", "challenge_block_500k", "description"))
                                        .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                                .icon(Material.DIAMOND_CHESTPLATE)
                                                .key("challenge_block_5m")
                                                .title(Localizer.dLocalize("advancement", "challenge_block_5m", "title"))
                                                .description(Localizer.dLocalize("advancement", "challenge_block_5m", "description"))
                                                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_block_1k").goal(1000).stat("blocked.hits").reward(getConfig().challengeBlock1kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_block_5k").goal(5000).stat("blocked.hits").reward(getConfig().challengeBlock1kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_block_50k").goal(50000).stat("blocked.hits").reward(getConfig().challengeBlock5kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_block_500k").goal(500000).stat("blocked.hits").reward(getConfig().challengeBlock5kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_block_5m").goal(5000000).stat("blocked.hits").reward(getConfig().challengeBlock5kReward).build());
        cooldowns = new HashMap<>();
    }

    private void handleCooldown(Player p, Runnable runnable) {
        Long cooldown = cooldowns.get(p);
        if (cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis())
            return;
        cooldowns.put(p, System.currentTimeMillis());
        runnable.run();
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity() instanceof Player p) {
            shouldReturnForPlayer(p, e, () -> {
                if (p.isBlocking()) {
                    AdaptPlayer adaptPlayer = getPlayer(p);
                    adaptPlayer.getData().addStat("blocked.hits", 1);
                    adaptPlayer.getData().addStat("blocked.damage", e.getDamage());

                    handleCooldown(p, () -> {
                        xp(p, getConfig().xpOnBlockedAttack);
                        p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.5f, 0.77f);
                        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.5f, 0.77f);
                    });
                }
            });
        }
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }

        for (Player i : Bukkit.getOnlinePlayers()) {
            AdaptPlayer adaptPlayer = getPlayer(i);
            shouldReturnForPlayer(i, () -> {
                checkStatTrackers(adaptPlayer);
                if (i.getPlayer() != null && (i.getPlayer().getInventory().getItemInOffHand().getType().equals(Material.SHIELD) || i.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.SHIELD))) {
                    xpSilent(i, getConfig().passiveXpForUsingShield);
                }
            });
        }
    }


    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        double xpOnBlockedAttack = 10;
        double challengeBlock1kReward = 500;
        double challengeBlock5kReward = 2000;
        long cooldownDelay = 3000;
        long passiveXpForUsingShield = 1;
    }
}
