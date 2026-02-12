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
import com.volmit.adapt.api.version.Version;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.rift.*;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.M;
import com.volmit.adapt.util.collection.KMap;
import com.volmit.adapt.util.reflect.registries.Attributes;
import com.volmit.adapt.util.reflect.registries.EntityTypes;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class SkillRift extends SimpleSkill<SkillRift.Config> {
    private final KMap<Player, Long> lasttp;

    public SkillRift() {
        super("rift", Localizer.dLocalize("skill.rift.icon"));
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("skill.rift.description"));
        setDisplayName(Localizer.dLocalize("skill.rift.name"));
        setColor(C.DARK_PURPLE);
        setInterval(1154);
        setIcon(Material.ENDER_EYE);
        registerAdaptation(new RiftResist());
        registerAdaptation(new RiftAccess());
        registerAdaptation(new RiftEnderchest());
        registerAdaptation(new RiftGate());
        registerAdaptation(new RiftBlink());
        registerAdaptation(new RiftDescent());
        registerAdaptation(new RiftVisage());
        lasttp = new KMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ENDER_PEARL)
                .key("challenge_rift_50")
                .title(Localizer.dLocalize("advancement.challenge_rift_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_rift_50.description"))
                .model(CustomModel.get(Material.ENDER_PEARL, "advancement", "rift", "challenge_rift_50"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ENDER_EYE)
                        .key("challenge_rift_500")
                        .title(Localizer.dLocalize("advancement.challenge_rift_500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_rift_500.description"))
                        .model(CustomModel.get(Material.ENDER_EYE, "advancement", "rift", "challenge_rift_500"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.END_CRYSTAL)
                                .key("challenge_rift_5k")
                                .title(Localizer.dLocalize("advancement.challenge_rift_5k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_rift_5k.description"))
                                .model(CustomModel.get(Material.END_CRYSTAL, "advancement", "rift", "challenge_rift_5k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_rift_50").goal(50).stat("rift.teleports").reward(getConfig().challengeRiftReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_rift_500").goal(500).stat("rift.teleports").reward(getConfig().challengeRiftReward * 2).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_rift_5k").goal(5000).stat("rift.teleports").reward(getConfig().challengeRiftReward * 5).build());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerTeleportEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            getPlayer(p).getData().addStat("rift.teleports", 1);
            if (!lasttp.containsKey(p)) {
                xpSilent(p, getConfig().teleportXP);
                lasttp.put(p, M.ms());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ProjectileLaunchEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!(e.getEntity().getShooter() instanceof Player p)) {
            return;
        }
        shouldReturnForPlayer(p, e, () -> {
            if (e.getEntity() instanceof EnderPearl) {
                xp(p, getConfig().throwEnderpearlXP);
            } else if (e.getEntity() instanceof EnderSignal) {
                xp(p, getConfig().throwEnderEyeXP);
            }
        });
    }

    private void handleEntityDamageByEntity(Entity entity, Player p, double damage) {
        if (entity instanceof LivingEntity living) {
            var attribute = Version.get().getAttribute(living, Attributes.GENERIC_MAX_HEALTH);
            double baseHealth = attribute == null ? 1 : attribute.getBaseValue();
            double multiplier = switch (entity.getType()) {
                case ENDERMAN -> getConfig().damageEndermanXPMultiplier;
                case ENDERMITE -> getConfig().damageEndermiteXPMultiplier;
                case ENDER_DRAGON -> getConfig().damageEnderdragonXPMultiplier;
                default -> 0;
            };
            double xp = multiplier * Math.min(damage, baseHealth);
            if (xp > 0) xp(p, xp);
        } else if (entity.getType() == EntityTypes.ENDER_CRYSTAL) {
            xp(p, getConfig().damageEndCrystalXP);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Player p) {
            shouldReturnForPlayer(p, e, () -> handleEntityDamageByEntity(e.getEntity(), p, e.getDamage()));
        } else if (e.getDamager() instanceof Projectile j && j.getShooter() instanceof Player p) {
            shouldReturnForPlayer(p, e, () -> handleEntityDamageByEntity(e.getEntity(), p, e.getDamage()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDeathEvent e) {
        if (e.getEntity() instanceof EnderCrystal && e.getEntity().getKiller() != null) {
            Player p = e.getEntity().getKiller();
            shouldReturnForPlayer(p, () -> xp(e.getEntity().getKiller(), getConfig().destroyEndCrystalXP));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        lasttp.remove(p);
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        for (Player i : lasttp.k()) {
            shouldReturnForPlayer(i, () -> {
                if (M.ms() - lasttp.get(i) > getConfig().teleportXPCooldown) {
                    lasttp.remove(i);
                }
            });
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Destroy End Crystal XP for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double destroyEndCrystalXP = 350;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage End Crystal XP for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageEndCrystalXP = 110;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Enderman XPMultiplier for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageEndermanXPMultiplier = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Endermite XPMultiplier for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageEndermiteXPMultiplier = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Enderdragon XPMultiplier for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageEnderdragonXPMultiplier = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Throw Enderpearl XP for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double throwEnderpearlXP = 105;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Throw Ender Eye XP for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double throwEnderEyeXP = 45;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Teleport XP for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double teleportXP = 15;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Teleport XPCooldown for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double teleportXPCooldown = 60000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Rift Reward for the Rift skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeRiftReward = 500;
    }
}
