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

package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.content.adaptation.stealth.*;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.format.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillStealth extends SimpleSkill<SkillStealth.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillStealth() {
        super("stealth", Localizer.dLocalize("skill.stealth.icon"));
        registerConfiguration(Config.class);
        setColor(C.DARK_GRAY);
        setInterval(1412);
        setIcon(Material.WITHER_ROSE);
        cooldowns = new HashMap<>();
        setDescription(Localizer.dLocalize("skill.stealth.description"));
        setDisplayName(Localizer.dLocalize("skill.stealth.name"));
        registerAdaptation(new StealthSpeed());
        registerAdaptation(new StealthSnatch());
        registerAdaptation(new StealthGhostArmor());
        registerAdaptation(new StealthSight());
        registerAdaptation(new StealthEnderVeil());
        registerAdaptation(new StealthSilentStep());
        registerAdaptation(new StealthShadowDecoy());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LEATHER_LEGGINGS)
                .key("challenge_sneak_1k")
                .title(Localizer.dLocalize("advancement.challenge_sneak_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_sneak_1k.description"))
                .model(CustomModel.get(Material.LEATHER_LEGGINGS, "advancement", "stealth", "challenge_sneak_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.CHAINMAIL_LEGGINGS)
                        .key("challenge_sneak_5k")
                        .title(Localizer.dLocalize("advancement.challenge_sneak_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_sneak_5k.description"))
                        .model(CustomModel.get(Material.CHAINMAIL_LEGGINGS, "advancement", "stealth", "challenge_sneak_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.NETHERITE_LEGGINGS)
                                .key("challenge_sneak_20k")
                                .title(Localizer.dLocalize("advancement.challenge_sneak_20k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_sneak_20k.description"))
                                .model(CustomModel.get(Material.NETHERITE_LEGGINGS, "advancement", "stealth", "challenge_sneak_20k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerMilestone("challenge_sneak_1k", "move.sneak", 1000, getConfig().challengeSneak1kReward);
        registerMilestone("challenge_sneak_5k", "move.sneak", 5000, getConfig().challengeSneak5kReward);
        registerMilestone("challenge_sneak_20k", "move.sneak", 20000, getConfig().challengeSneak20kReward);

        // Chain 2 - Stealth Damage While Sneaking
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.STONE_SWORD)
                .key("challenge_stealth_dmg_500")
                .title(Localizer.dLocalize("advancement.challenge_stealth_dmg_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_dmg_500.description"))
                .model(CustomModel.get(Material.STONE_SWORD, "advancement", "stealth", "challenge_stealth_dmg_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.NETHERITE_SWORD)
                        .key("challenge_stealth_dmg_5k")
                        .title(Localizer.dLocalize("advancement.challenge_stealth_dmg_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_stealth_dmg_5k.description"))
                        .model(CustomModel.get(Material.NETHERITE_SWORD, "advancement", "stealth", "challenge_stealth_dmg_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_stealth_dmg_500", "stealth.damage.sneaking", 500, getConfig().challengeStealthDmg500Reward);
        registerMilestone("challenge_stealth_dmg_5k", "stealth.damage.sneaking", 5000, getConfig().challengeStealthDmg5kReward);

        // Chain 3 - Stealth Kills While Sneaking
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SKELETON_SKULL)
                .key("challenge_stealth_kills_10")
                .title(Localizer.dLocalize("advancement.challenge_stealth_kills_10.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_kills_10.description"))
                .model(CustomModel.get(Material.SKELETON_SKULL, "advancement", "stealth", "challenge_stealth_kills_10"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.WITHER_ROSE)
                        .key("challenge_stealth_kills_100")
                        .title(Localizer.dLocalize("advancement.challenge_stealth_kills_100.title"))
                        .description(Localizer.dLocalize("advancement.challenge_stealth_kills_100.description"))
                        .model(CustomModel.get(Material.WITHER_ROSE, "advancement", "stealth", "challenge_stealth_kills_100"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_stealth_kills_10", "stealth.kills.sneaking", 10, getConfig().challengeStealthKills10Reward);
        registerMilestone("challenge_stealth_kills_100", "stealth.kills.sneaking", 100, getConfig().challengeStealthKills100Reward);

        // Chain 4 - Stealth Time Spent Sneaking
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LEATHER_BOOTS)
                .key("challenge_stealth_time_1h")
                .title(Localizer.dLocalize("advancement.challenge_stealth_time_1h.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_time_1h.description"))
                .model(CustomModel.get(Material.LEATHER_BOOTS, "advancement", "stealth", "challenge_stealth_time_1h"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.CHAINMAIL_BOOTS)
                        .key("challenge_stealth_time_10h")
                        .title(Localizer.dLocalize("advancement.challenge_stealth_time_10h.title"))
                        .description(Localizer.dLocalize("advancement.challenge_stealth_time_10h.description"))
                        .model(CustomModel.get(Material.CHAINMAIL_BOOTS, "advancement", "stealth", "challenge_stealth_time_10h"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_stealth_time_1h", "stealth.time", 3600, getConfig().challengeStealthTime1hReward);
        registerMilestone("challenge_stealth_time_10h", "stealth.time", 36000, getConfig().challengeStealthTime10hReward);

        // Chain 5 - Stealth Arrows Fired While Sneaking
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BOW)
                .key("challenge_stealth_arrows_50")
                .title(Localizer.dLocalize("advancement.challenge_stealth_arrows_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_arrows_50.description"))
                .model(CustomModel.get(Material.BOW, "advancement", "stealth", "challenge_stealth_arrows_50"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.CROSSBOW)
                        .key("challenge_stealth_arrows_500")
                        .title(Localizer.dLocalize("advancement.challenge_stealth_arrows_500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_stealth_arrows_500.description"))
                        .model(CustomModel.get(Material.CROSSBOW, "advancement", "stealth", "challenge_stealth_arrows_500"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_stealth_arrows_50", "stealth.arrows.sneaking", 50, getConfig().challengeStealthArrows50Reward);
        registerMilestone("challenge_stealth_arrows_500", "stealth.arrows.sneaking", 500, getConfig().challengeStealthArrows500Reward);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Player p && p.isSneaking()) {
            shouldReturnForPlayer(p, e, () -> {
                getPlayer(p).getData().addStat("stealth.damage.sneaking", e.getDamage());
                xp(p, e.getEntity().getLocation(), e.getDamage() * getConfig().sneakCombatXPMultiplier);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) {
            return;
        }
        Player p = e.getEntity().getKiller();
        if (p.isSneaking()) {
            shouldReturnForPlayer(p, () -> {
                getPlayer(p).getData().addStat("stealth.kills.sneaking", 1);
                xp(p, e.getEntity().getLocation(), getConfig().sneakKillXP);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ProjectileLaunchEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!(e.getEntity().getShooter() instanceof Player p)) {
            return;
        }
        if (p.isSneaking()) {
            shouldReturnForPlayer(p, e, () -> {
                getPlayer(p).getData().addStat("stealth.arrows.sneaking", 1);
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
                if (i.isSneaking() && !i.isSwimming() && !i.isSprinting() && !i.isFlying() && !i.isGliding() && (i.getGameMode().equals(GameMode.SURVIVAL) || i.getGameMode().equals(GameMode.ADVENTURE))) {
                    xpSilent(i, getConfig().sneakXP, "stealth:sneak");
                    adaptPlayer.getData().addStat("stealth.time", 1);
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
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sneak1k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeSneak1kReward = 1750;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sneak5k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeSneak5kReward = 3500;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Sneak20k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeSneak20kReward = 8750;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sneak XP for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double sneakXP = 0.4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP multiplier for dealing damage while sneaking.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double sneakCombatXPMultiplier = 3.0;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP awarded for killing while sneaking.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double sneakKillXP = 15;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Dmg 500 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthDmg500Reward = 1500;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Dmg 5k Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthDmg5kReward = 5000;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Kills 10 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthKills10Reward = 1000;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Kills 100 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthKills100Reward = 5000;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Time 1h Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthTime1hReward = 2000;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Time 10h Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthTime10hReward = 7500;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Arrows 50 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthArrows50Reward = 1250;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Stealth Arrows 500 Reward for the Stealth skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeStealthArrows500Reward = 5000;
    }
}
