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
import com.volmit.adapt.content.adaptation.blocking.BlockingChainArmorer;
import com.volmit.adapt.content.adaptation.blocking.BlockingBastionStance;
import com.volmit.adapt.content.adaptation.blocking.BlockingBulwarkBash;
import com.volmit.adapt.content.adaptation.blocking.BlockingCounterGuard;
import com.volmit.adapt.content.adaptation.blocking.BlockingHorseArmorer;
import com.volmit.adapt.content.adaptation.blocking.BlockingMirrorBlock;
import com.volmit.adapt.content.adaptation.blocking.BlockingMultiArmor;
import com.volmit.adapt.content.adaptation.blocking.BlockingSaddlecrafter;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillBlocking extends SimpleSkill<SkillBlocking.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillBlocking() {
        super("blocking", Localizer.dLocalize("skill.blocking.icon"));
        registerConfiguration(Config.class);
        setColor(C.DARK_GRAY);
        setDescription(Localizer.dLocalize("skill.blocking.description"));
        setDisplayName(Localizer.dLocalize("skill.blocking.name"));
        setInterval(5000);
        setIcon(Material.SHIELD);
        registerAdaptation(new BlockingMultiArmor());
        registerAdaptation(new BlockingChainArmorer());
        registerAdaptation(new BlockingSaddlecrafter());
        registerAdaptation(new BlockingHorseArmorer());
        registerAdaptation(new BlockingCounterGuard());
        registerAdaptation(new BlockingBastionStance());
        registerAdaptation(new BlockingMirrorBlock());
        registerAdaptation(new BlockingBulwarkBash());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LEATHER_CHESTPLATE).key("challenge_block_1k")
                .title(Localizer.dLocalize("advancement.challenge_block_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_block_1k.description"))
                .model(CustomModel.get(Material.LEATHER_CHESTPLATE, "advancement", "blocking", "challenge_block_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                        .icon(Material.CHAINMAIL_CHESTPLATE)
                        .key("challenge_block_5k")
                        .title(Localizer.dLocalize("advancement.challenge_block_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_block_5k.description"))
                        .model(CustomModel.get(Material.CHAINMAIL_CHESTPLATE, "advancement", "blocking", "challenge_block_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                .icon(Material.IRON_CHESTPLATE)
                                .key("challenge_block_50k")
                                .title(Localizer.dLocalize("advancement.challenge_block_50k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_block_50k.description"))
                                .model(CustomModel.get(Material.IRON_CHESTPLATE, "advancement", "blocking", "challenge_block_50k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                        .icon(Material.GOLDEN_CHESTPLATE)
                                        .key("challenge_block_500k")
                                        .title(Localizer.dLocalize("advancement.challenge_block_500k.title"))
                                        .description(Localizer.dLocalize("advancement.challenge_block_500k.description"))
                                        .model(CustomModel.get(Material.GOLDEN_CHESTPLATE, "advancement", "blocking", "challenge_block_500k"))
                                        .frame(AdaptAdvancementFrame.CHALLENGE)
                                        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                                                .icon(Material.DIAMOND_CHESTPLATE)
                                                .key("challenge_block_5m")
                                                .title(Localizer.dLocalize("advancement.challenge_block_5m.title"))
                                                .description(Localizer.dLocalize("advancement.challenge_block_5m.description"))
                                                .model(CustomModel.get(Material.DIAMOND_CHESTPLATE, "advancement", "blocking", "challenge_block_5m"))
                                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build());
        registerMilestone("challenge_block_1k", "blocked.hits", 1000, getConfig().challengeBlock1kReward);
        registerMilestone("challenge_block_5k", "blocked.hits", 5000, getConfig().challengeBlock1kReward);
        registerMilestone("challenge_block_50k", "blocked.hits", 50000, getConfig().challengeBlock5kReward);
        registerMilestone("challenge_block_500k", "blocked.hits", 500000, getConfig().challengeBlock5kReward);
        registerMilestone("challenge_block_5m", "blocked.hits", 5000000, getConfig().challengeBlock5kReward);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_CHESTPLATE).key("challenge_block_dmg_1k")
                .title(Localizer.dLocalize("advancement.challenge_block_dmg_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_block_dmg_1k.description"))
                .model(CustomModel.get(Material.IRON_CHESTPLATE, "advancement", "blocking", "challenge_block_dmg_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.NETHERITE_CHESTPLATE)
                        .key("challenge_block_dmg_10k")
                        .title(Localizer.dLocalize("advancement.challenge_block_dmg_10k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_block_dmg_10k.description"))
                        .model(CustomModel.get(Material.NETHERITE_CHESTPLATE, "advancement", "blocking", "challenge_block_dmg_10k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_block_dmg_1k", "blocked.damage", 1000, getConfig().challengeBlock1kReward);
        registerMilestone("challenge_block_dmg_10k", "blocked.damage", 10000, getConfig().challengeBlock5kReward);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ARROW).key("challenge_block_proj_100")
                .title(Localizer.dLocalize("advancement.challenge_block_proj_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_block_proj_100.description"))
                .model(CustomModel.get(Material.ARROW, "advancement", "blocking", "challenge_block_proj_100"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.SPECTRAL_ARROW)
                        .key("challenge_block_proj_1k")
                        .title(Localizer.dLocalize("advancement.challenge_block_proj_1k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_block_proj_1k.description"))
                        .model(CustomModel.get(Material.SPECTRAL_ARROW, "advancement", "blocking", "challenge_block_proj_1k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_block_proj_100", "blocked.projectiles", 100, getConfig().challengeBlock1kReward);
        registerMilestone("challenge_block_proj_1k", "blocked.projectiles", 1000, getConfig().challengeBlock5kReward);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_SWORD).key("challenge_block_melee_500")
                .title(Localizer.dLocalize("advancement.challenge_block_melee_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_block_melee_500.description"))
                .model(CustomModel.get(Material.IRON_SWORD, "advancement", "blocking", "challenge_block_melee_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.NETHERITE_SWORD)
                        .key("challenge_block_melee_5k")
                        .title(Localizer.dLocalize("advancement.challenge_block_melee_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_block_melee_5k.description"))
                        .model(CustomModel.get(Material.NETHERITE_SWORD, "advancement", "blocking", "challenge_block_melee_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_block_melee_500", "blocked.melee", 500, getConfig().challengeBlock1kReward);
        registerMilestone("challenge_block_melee_5k", "blocked.melee", 5000, getConfig().challengeBlock5kReward);

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.DIAMOND_CHESTPLATE).key("challenge_block_heavy_50")
                .title(Localizer.dLocalize("advancement.challenge_block_heavy_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_block_heavy_50.description"))
                .model(CustomModel.get(Material.DIAMOND_CHESTPLATE, "advancement", "blocking", "challenge_block_heavy_50"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.NETHERITE_CHESTPLATE)
                        .key("challenge_block_heavy_500")
                        .title(Localizer.dLocalize("advancement.challenge_block_heavy_500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_block_heavy_500.description"))
                        .model(CustomModel.get(Material.NETHERITE_CHESTPLATE, "advancement", "blocking", "challenge_block_heavy_500"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_block_heavy_50", "blocked.heavy", 50, getConfig().challengeBlock1kReward);
        registerMilestone("challenge_block_heavy_500", "blocked.heavy", 500, getConfig().challengeBlock5kReward);

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
            SoundPlayer sp = SoundPlayer.of(p);
            shouldReturnForPlayer(p, e, () -> {
                if (p.isBlocking()) {
                    AdaptPlayer adaptPlayer = getPlayer(p);
                    adaptPlayer.getData().addStat("blocked.hits", 1);
                    adaptPlayer.getData().addStat("blocked.damage", e.getDamage());
                    if (e.getDamager() instanceof Projectile) {
                        adaptPlayer.getData().addStat("blocked.projectiles", 1);
                    } else {
                        adaptPlayer.getData().addStat("blocked.melee", 1);
                    }
                    if (e.getDamage() > 5) {
                        adaptPlayer.getData().addStat("blocked.heavy", 1);
                    }

                    handleCooldown(p, () -> {
                        xp(p, getConfig().xpOnBlockedAttack);
                        sp.play(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.5f, 0.77f);
                        sp.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.5f, 0.77f);
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

        for (AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player i = adaptPlayer.getPlayer();
            shouldReturnForPlayer(i, () -> {
                checkStatTrackers(adaptPlayer);
                if (getConfig().passiveXpForUsingShield > 0 && (i.getInventory().getItemInOffHand().getType().equals(Material.SHIELD) || i.getInventory().getItemInMainHand().getType().equals(Material.SHIELD))) {
                    xpSilent(i, getConfig().passiveXpForUsingShield, "blocking:shield-hold");
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp On Blocked Attack for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpOnBlockedAttack = 25;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Block1k Reward for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeBlock1kReward = 500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Block5k Reward for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeBlock5kReward = 2000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long cooldownDelay = 1500;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Passive Xp For Using Shield for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long passiveXpForUsingShield = 0;
    }
}
