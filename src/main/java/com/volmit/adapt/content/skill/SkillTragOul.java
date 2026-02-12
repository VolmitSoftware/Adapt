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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.content.adaptation.tragoul.TragoulGlobe;
import com.volmit.adapt.content.adaptation.tragoul.TragoulHealing;
import com.volmit.adapt.content.adaptation.tragoul.TragoulLance;
import com.volmit.adapt.content.adaptation.tragoul.TragoulThorns;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.reflect.registries.Particles;
import de.slikey.effectlib.effect.CloudEffect;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Map;

public class SkillTragOul extends SimpleSkill<SkillTragOul.Config> {
    private final Map<Player, Long> cooldowns;

    public SkillTragOul() {
        super("tragoul", Localizer.dLocalize("skill.tragoul.icon"));
        registerConfiguration(Config.class);
        setColor(C.AQUA);
        setDescription(Localizer.dLocalize("skill.tragoul.description"));
        setDisplayName(Localizer.dLocalize("skill.tragoul.name"));
        setInterval(2755);
        setIcon(Material.CRIMSON_ROOTS);
        cooldowns = new HashMap<>();
        registerAdaptation(new TragoulThorns());
        registerAdaptation(new TragoulGlobe());
        registerAdaptation(new TragoulHealing());
        registerAdaptation(new TragoulLance());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.CRIMSON_ROOTS)
                .key("challenge_trag_1k")
                .title(Localizer.dLocalize("advancement.challenge_trag_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_trag_1k.description"))
                .model(CustomModel.get(Material.CRIMSON_ROOTS, "advancement", "tragoul", "challenge_trag_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.CRIMSON_STEM)
                        .key("challenge_trag_10k")
                        .title(Localizer.dLocalize("advancement.challenge_trag_10k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_trag_10k.description"))
                        .model(CustomModel.get(Material.CRIMSON_STEM, "advancement", "tragoul", "challenge_trag_10k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.NETHER_STAR)
                                .key("challenge_trag_100k")
                                .title(Localizer.dLocalize("advancement.challenge_trag_100k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_trag_100k.description"))
                                .model(CustomModel.get(Material.NETHER_STAR, "advancement", "tragoul", "challenge_trag_100k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_trag_1k").goal(1000).stat("trag.damage").reward(getConfig().challengeTragReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_trag_10k").goal(10000).stat("trag.damage").reward(getConfig().challengeTragReward * 2).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_trag_100k").goal(100000).stat("trag.damage").reward(getConfig().challengeTragReward * 5).build());

        // Chain 2 - Hits Received
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ROTTEN_FLESH)
                .key("challenge_trag_hits_500")
                .title(Localizer.dLocalize("advancement.challenge_trag_hits_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_trag_hits_500.description"))
                .model(CustomModel.get(Material.ROTTEN_FLESH, "advancement", "tragoul", "challenge_trag_hits_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.BONE)
                        .key("challenge_trag_hits_5k")
                        .title(Localizer.dLocalize("advancement.challenge_trag_hits_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_trag_hits_5k.description"))
                        .model(CustomModel.get(Material.BONE, "advancement", "tragoul", "challenge_trag_hits_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_trag_hits_500").goal(500).stat("trag.hitsrecieved").reward(getConfig().challengeTragReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_trag_hits_5k").goal(5000).stat("trag.hitsrecieved").reward(getConfig().challengeTragReward).build());

        // Chain 3 - Deaths
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SKELETON_SKULL)
                .key("challenge_trag_deaths_10")
                .title(Localizer.dLocalize("advancement.challenge_trag_deaths_10.title"))
                .description(Localizer.dLocalize("advancement.challenge_trag_deaths_10.description"))
                .model(CustomModel.get(Material.SKELETON_SKULL, "advancement", "tragoul", "challenge_trag_deaths_10"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.WITHER_SKELETON_SKULL)
                        .key("challenge_trag_deaths_100")
                        .title(Localizer.dLocalize("advancement.challenge_trag_deaths_100.title"))
                        .description(Localizer.dLocalize("advancement.challenge_trag_deaths_100.description"))
                        .model(CustomModel.get(Material.WITHER_SKELETON_SKULL, "advancement", "tragoul", "challenge_trag_deaths_100"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_trag_deaths_10").goal(10).stat("trag.deaths").reward(getConfig().challengeTragReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_trag_deaths_100").goal(100).stat("trag.deaths").reward(getConfig().challengeTragReward).build());

        // Chain 4 - Fire Damage
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BLAZE_POWDER)
                .key("challenge_trag_fire_500")
                .title(Localizer.dLocalize("advancement.challenge_trag_fire_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_trag_fire_500.description"))
                .model(CustomModel.get(Material.BLAZE_POWDER, "advancement", "tragoul", "challenge_trag_fire_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.MAGMA_CREAM)
                        .key("challenge_trag_fire_5k")
                        .title(Localizer.dLocalize("advancement.challenge_trag_fire_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_trag_fire_5k.description"))
                        .model(CustomModel.get(Material.MAGMA_CREAM, "advancement", "tragoul", "challenge_trag_fire_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_trag_fire_500").goal(500).stat("trag.fire.damage").reward(getConfig().challengeTragReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_trag_fire_5k").goal(5000).stat("trag.fire.damage").reward(getConfig().challengeTragReward).build());

        // Chain 5 - Fall Damage
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.FEATHER)
                .key("challenge_trag_fall_500")
                .title(Localizer.dLocalize("advancement.challenge_trag_fall_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_trag_fall_500.description"))
                .model(CustomModel.get(Material.FEATHER, "advancement", "tragoul", "challenge_trag_fall_500"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.HAY_BLOCK)
                        .key("challenge_trag_fall_5k")
                        .title(Localizer.dLocalize("advancement.challenge_trag_fall_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_trag_fall_5k.description"))
                        .model(CustomModel.get(Material.HAY_BLOCK, "advancement", "tragoul", "challenge_trag_fall_5k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_trag_fall_500").goal(500).stat("trag.fall.damage").reward(getConfig().challengeTragReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_trag_fall_5k").goal(5000).stat("trag.fall.damage").reward(getConfig().challengeTragReward).build());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        shouldReturnForPlayer(p, e, () -> {
            if (e.getEntity().isDead()
                    || e.getEntity().isInvulnerable()
                    || p.isInvulnerable()
                    || p.isBlocking()
                    || !checkValidEntity(e.getEntity().getType())) {
                return;
            }
            AdaptPlayer a = getPlayer(p);
            a.getData().addStat("trag.hitsrecieved", 1);
            a.getData().addStat("trag.damage", e.getDamage());
            Long cooldown = cooldowns.get(p);
            if (cooldown != null && cooldown + getConfig().cooldownDelay > System.currentTimeMillis())
                return;
            cooldowns.put(p, System.currentTimeMillis());
            xp(a.getPlayer(), getConfig().damageReceivedXpMultiplier * e.getDamage());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerDeathEvent e) {
        Player p = e.getEntity();
        shouldReturnForPlayer(p, () -> {
            AdaptPlayer a = getPlayer(p);
            a.getData().addStat("trag.deaths", 1);
            if (AdaptConfig.get().isHardcoreResetOnPlayerDeath()) {
                Adapt.info("Resetting " + p.getName() + "'s skills due to death");
                a.delete(p.getUniqueId());
                return;
            }
            if (getConfig().takeAwaySkillsOnDeath) {
                if (getConfig().showParticles) {
                    CloudEffect ce = new CloudEffect(Adapt.instance.adaptEffectManager);
                    ce.mainParticle = Particle.ASH;
                    ce.cloudParticle = Particles.REDSTONE;
                    ce.duration = 10000;
                    ce.iterations = 1000;
                    ce.setEntity(p);
                    ce.start();
                }

                if (this.hasBlacklistPermission(p, this)) {
                    return;
                }

                SoundPlayer sp = SoundPlayer.of(p);
                sp.play(p.getLocation(), Sound.ENTITY_BLAZE_DEATH, 1f, 1f);

                PlayerSkillLine tragoul = a.getData().getSkillLineNullable("tragoul");
                if (tragoul != null) {
                    double xp = tragoul.getXp();
                    if (xp > getConfig().deathXpLoss) {
                        xp(p, getConfig().deathXpLoss);
                    } else {
                        tragoul.setXp(0);
                    }
                    tragoul.setLastXP(xp);

                    for (PlayerAdaptation adapt : tragoul.getAdaptations().values()) {
                        adapt.setLevel(Math.max(adapt.getLevel() - 1, 0));
                    }

                    recalcTotalExp(p);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        shouldReturnForPlayer(p, e, () -> {
            EntityDamageEvent.DamageCause cause = e.getCause();
            if (cause == EntityDamageEvent.DamageCause.FALL) {
                getPlayer(p).getData().addStat("trag.fall.damage", e.getDamage());
            } else if (cause == EntityDamageEvent.DamageCause.FIRE
                    || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                    || cause == EntityDamageEvent.DamageCause.LAVA) {
                getPlayer(p).getData().addStat("trag.fire.damage", e.getDamage());
            }
        });
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        for (Player i : Bukkit.getOnlinePlayers()) {
            shouldReturnForPlayer(i, () -> {
                AdaptPlayer player = getPlayer(i);
                checkStatTrackers(player);
            });
        }
    }


    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Death Xp Loss for the Trag Oul skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double deathXpLoss = -750;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Take Away Skills On Death for the Trag Oul skill.", impact = "True enables this behavior and false disables it.")
        boolean takeAwaySkillsOnDeath = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Trag Oul skill.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Trag Oul skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long cooldownDelay = 1000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Received Xp Multiplier for the Trag Oul skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageReceivedXpMultiplier = 1.75;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Trag Reward for the Trag Oul skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeTragReward = 500;
    }
}
