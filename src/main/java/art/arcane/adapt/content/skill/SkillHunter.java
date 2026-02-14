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
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.content.adaptation.hunter.*;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.reflect.registries.Attributes;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SkillHunter extends SimpleSkill<SkillHunter.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillHunter() {
        super("hunter", Localizer.dLocalize("skill.hunter.icon"));
        registerConfiguration(Config.class);
        setColor(C.RED);
        setDescription(Localizer.dLocalize("skill.hunter.description"));
        setDisplayName(Localizer.dLocalize("skill.hunter.name"));
        setInterval(4150);
        setIcon(Material.BONE);
        cooldowns = new HashMap<>();
        registerAdaptation(new HunterAdrenaline());
        registerAdaptation(new HunterRegen());
        registerAdaptation(new HunterInvis());
        registerAdaptation(new HunterJumpBoost());
        registerAdaptation(new HunterLuck());
        registerAdaptation(new HunterSpeed());
        registerAdaptation(new HunterStrength());
        registerAdaptation(new HunterResistance());
        registerAdaptation(new HunterDropToInventory());
        registerAdaptation(new HunterTrophySkinner());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.TURTLE_EGG)
                .key("horrible_person")
                .title(Localizer.dLocalize("advancement.horrible_person.title"))
                .description(Localizer.dLocalize("advancement.horrible_person.description"))
                .model(CustomModel.get(Material.TURTLE_EGG, "advancement", "hunter", "horrible_person"))
                .frame(AdaptAdvancementFrame.GOAL)
                .visibility(AdvancementVisibility.HIDDEN)
                .build()
        );
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.TURTLE_EGG)
                .key("challenge_turtle_egg_smasher")
                .title(Localizer.dLocalize("advancement.challenge_turtle_egg_smasher.title"))
                .description(Localizer.dLocalize("advancement.challenge_turtle_egg_smasher.description"))
                .model(CustomModel.get(Material.TURTLE_EGG, "advancement", "hunter", "challenge_turtle_egg_smasher"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.TURTLE_EGG)
                        .key("challenge_turtle_egg_annihilator")
                        .title(Localizer.dLocalize("advancement.challenge_turtle_egg_annihilator.title"))
                        .description(Localizer.dLocalize("advancement.challenge_turtle_egg_annihilator.description"))
                        .model(CustomModel.get(Material.TURTLE_EGG, "advancement", "hunter", "challenge_turtle_egg_annihilator"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BONE)
                .key("challenge_novice_hunter")
                .title(Localizer.dLocalize("advancement.challenge_novice_hunter.title"))
                .description(Localizer.dLocalize("advancement.challenge_novice_hunter.description"))
                .model(CustomModel.get(Material.BONE, "advancement", "hunter", "challenge_novice_hunter"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.IRON_SWORD)
                        .key("challenge_intermediate_hunter")
                        .title(Localizer.dLocalize("advancement.challenge_intermediate_hunter.title"))
                        .description(Localizer.dLocalize("advancement.challenge_intermediate_hunter.description"))
                        .model(CustomModel.get(Material.IRON_SWORD, "advancement", "hunter", "challenge_intermediate_hunter"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.DIAMOND_SWORD)
                                .key("challenge_advanced_hunter")
                                .title(Localizer.dLocalize("advancement.challenge_advanced_hunter.title"))
                                .description(Localizer.dLocalize("advancement.challenge_advanced_hunter.description"))
                                .model(CustomModel.get(Material.DIAMOND_SWORD, "advancement", "hunter", "challenge_advanced_hunter"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.CREEPER_HEAD)
                .key("challenge_creeper_conqueror")
                .title(Localizer.dLocalize("advancement.challenge_creeper_conqueror.title"))
                .description(Localizer.dLocalize("advancement.challenge_creeper_conqueror.description"))
                .model(CustomModel.get(Material.CREEPER_HEAD, "advancement", "hunter", "challenge_creeper_conqueror"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.TNT)
                        .key("challenge_creeper_annihilator")
                        .title(Localizer.dLocalize("advancement.challenge_creeper_annihilator.title"))
                        .description(Localizer.dLocalize("advancement.challenge_creeper_annihilator.description"))
                        .model(CustomModel.get(Material.TNT, "advancement", "hunter", "challenge_creeper_annihilator"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BONE)
                .key("challenge_kills_500")
                .title(Localizer.dLocalize("advancement.challenge_kills_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_kills_500.description"))
                .model(CustomModel.get(Material.BONE, "advancement", "hunter", "challenge_kills_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.WITHER_SKELETON_SKULL)
                        .key("challenge_kills_5k")
                        .title(Localizer.dLocalize("advancement.challenge_kills_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_kills_5k.description"))
                        .model(CustomModel.get(Material.WITHER_SKELETON_SKULL, "advancement", "hunter", "challenge_kills_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.DRAGON_HEAD)
                .key("challenge_boss_1")
                .title(Localizer.dLocalize("advancement.challenge_boss_1.title"))
                .description(Localizer.dLocalize("advancement.challenge_boss_1.description"))
                .model(CustomModel.get(Material.DRAGON_HEAD, "advancement", "hunter", "challenge_boss_1"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.NETHER_STAR)
                        .key("challenge_boss_10")
                        .title(Localizer.dLocalize("advancement.challenge_boss_10.title"))
                        .description(Localizer.dLocalize("advancement.challenge_boss_10.description"))
                        .model(CustomModel.get(Material.NETHER_STAR, "advancement", "hunter", "challenge_boss_10"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());

        registerMilestone("horrible_person", "killed.turtleeggs", 1, getConfig().turtleEggKillXP);
        registerMilestone("challenge_turtle_egg_smasher", "killed.turtleeggs", 100, getConfig().turtleEggKillXP*10);
        registerMilestone("challenge_turtle_egg_annihilator", "killed.turtleeggs", 1000, getConfig().turtleEggKillXP*10);
        registerMilestone("challenge_novice_hunter", "killed.monsters", 100, getConfig().turtleEggKillXP*3);
        registerMilestone("challenge_intermediate_hunter", "killed.monsters", 1000, getConfig().turtleEggKillXP*3);
        registerMilestone("challenge_advanced_hunter", "killed.monsters", 10000, getConfig().turtleEggKillXP*3);
        registerMilestone("challenge_creeper_conqueror", "killed.creepers", 100, getConfig().turtleEggKillXP*3);
        registerMilestone("challenge_creeper_annihilator", "killed.creepers", 1000, getConfig().turtleEggKillXP*3);
        registerMilestone("challenge_kills_500", "killed.kills", 500, getConfig().killsChallengeReward);
        registerMilestone("challenge_kills_5k", "killed.kills", 5000, getConfig().killsChallengeReward * 5);
        registerMilestone("challenge_boss_1", "hunter.boss.kills", 1, getConfig().bossKillReward);
        registerMilestone("challenge_boss_10", "hunter.boss.kills", 10, getConfig().bossKillReward * 5);
    }

    private void handleCooldownAndXp(Player p, double xpAmount) {
        handleCooldownAndXp(p, xpAmount, null);
    }

    private void handleCooldownAndXp(Player p, double xpAmount, String rewardKey) {
        Long cooldown = cooldowns.get(p);
        if (cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis())
            return;
        cooldowns.put(p, System.currentTimeMillis());
        xp(p, xpAmount, rewardKey);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (e.getBlock().getType().equals(Material.TURTLE_EGG)) {
                handleCooldownAndXp(p, getConfig().turtleEggKillXP, "hunter:turtle-egg:break");
                getPlayer(p).getData().addStat("killed.turtleeggs", 1);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerInteractEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (e.getAction().equals(Action.PHYSICAL) && e.getClickedBlock() != null && e.getClickedBlock().getType().equals(Material.TURTLE_EGG)) {
                handleCooldownAndXp(p, getConfig().turtleEggKillXP, "hunter:turtle-egg:step");
                getPlayer(p).getData().addStat("killed.turtleeggs", 1);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) {
            return;
        }
        Player p = e.getEntity().getKiller();

        if (!getConfig().getXpForAttackingWithTools) {
            return;
        }

        shouldReturnForPlayer(p, () -> {
            if (e.getEntity().getType().equals(EntityType.CREEPER)) {
                double cmult = getConfig().creeperKillMultiplier;
                var attribute = Version.get().getAttribute(e.getEntity(), Attributes.GENERIC_MAX_HEALTH);
                double xpAmount = (attribute == null ? 1 : attribute.getValue()) * getConfig().killMaxHealthXPMultiplier * cmult;
                if (e.getEntity().getPortalCooldown() > 0) {
                    xpAmount *= getConfig().spawnerMobReductionXpMultiplier;
                }
                getPlayer(p).getData().addStat("killed.kills", 1);
                handleCooldownAndXp(p, xpAmount, "hunter:kill:creeper");
            } else {
                handleEntityKill(p, e.getEntity());
            }
            EntityType type = e.getEntity().getType();
            if (type == EntityType.ENDER_DRAGON || type == EntityType.WITHER || type == EntityType.ELDER_GUARDIAN || type == EntityType.WARDEN) {
                getPlayer(p).getData().addStat("hunter.boss.kills", 1);
            }
        });
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void on(CreatureSpawnEvent e) {
        if (!isEnabled() || e.isCancelled()) {
            return;
        }
        if (e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.SPAWNER)) {
            Entity ent = e.getEntity();
            ent.setPortalCooldown(630726000);
        }
    }

    private void handleEntityKill(Player p, Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            var attribute = Version.get().getAttribute(livingEntity, Attributes.GENERIC_MAX_HEALTH);
            double xpAmount = (attribute == null ? 1 : attribute.getValue()) * getConfig().killMaxHealthXPMultiplier;
            if (entity.getPortalCooldown() > 0) {
                xpAmount *= getConfig().spawnerMobReductionXpMultiplier;
            }
            getPlayer(p).getData().addStat("killed.kills", 1);
            String rewardKey = "hunter:kill:" + entity.getType().name().toLowerCase(Locale.ROOT);
            handleCooldownAndXp(p, xpAmount, rewardKey);
        }
    }


    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        checkStatTrackersForOnlinePlayers();
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Get Xp For Attacking With Tools for the Hunter skill.", impact = "True enables this behavior and false disables it.")
        boolean getXpForAttackingWithTools = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Turtle Egg Kill XP for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double turtleEggKillXP = 100;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Creeper Kill Multiplier for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double creeperKillMultiplier = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Kill Max Health XPMultiplier for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double killMaxHealthXPMultiplier = 3.0;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long cooldownDelay = 1000;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spawner Mob Reduction Xp Multiplier for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double spawnerMobReductionXpMultiplier = 0.3;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Kills Challenge Reward for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double killsChallengeReward = 500;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Boss Kill Reward for the Hunter skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bossKillReward = 1000;
    }
}
