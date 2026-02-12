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

package com.volmit.adapt.content.adaptation.tragoul;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TragoulBoneHarvest extends SimpleAdaptation<TragoulBoneHarvest.Config> {
    private static final PotionEffectType[] BONE_EFFECT_POOL = {
            PotionEffectType.SPEED,
            PotionEffectType.REGENERATION,
            PotionEffectType.RESISTANCE,
            PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.ABSORPTION,
            PotionEffectType.JUMP_BOOST,
            PotionEffectType.NIGHT_VISION
    };

    private final Set<UUID> bloodGlobes = new HashSet<>();
    private final Set<UUID> boneGlobes = new HashSet<>();

    public TragoulBoneHarvest() {
        super("tragoul-bone-harvest");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("tragoul.bone_harvest.description"));
        setDisplayName(Localizer.dLocalize("tragoul.bone_harvest.name"));
        setIcon(Material.BONE_BLOCK);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2000);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BONE)
                .key("challenge_tragoul_bone_500")
                .title(Localizer.dLocalize("advancement.challenge_tragoul_bone_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_tragoul_bone_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.BONE_BLOCK)
                        .key("challenge_tragoul_bone_5k")
                        .title(Localizer.dLocalize("advancement.challenge_tragoul_bone_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_tragoul_bone_5k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_tragoul_bone_500").goal(500).stat("tragoul.bone-harvest.orbs-collected").reward(300).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_tragoul_bone_5k").goal(5000).stat("tragoul.bone-harvest.orbs-collected").reward(1000).build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getGlobeChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("tragoul.bone_harvest.lore1"));
        v.addLore(C.GREEN + "+ " + Form.duration(getGlobeLifetimeTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.bone_harvest.lore2"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null || !hasAdaptation(killer) || !canPVE(killer, e.getEntity().getLocation())) {
            return;
        }

        int level = getLevel(killer);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() > getGlobeChance(level)) {
            return;
        }

        spawnGlobe(killer, e, random.nextBoolean(), level);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || !hasAdaptation(p)) {
            return;
        }

        UUID id = e.getItem().getUniqueId();
        boolean blood = bloodGlobes.contains(id);
        boolean bone = boneGlobes.contains(id);
        if (!blood && !bone) {
            return;
        }

        e.setCancelled(true);
        e.getItem().remove();
        bloodGlobes.remove(id);
        boneGlobes.remove(id);
        applyBuff(p, blood, getLevel(p));
        getPlayer(p).getData().addStat("tragoul.bone-harvest.orbs-collected", 1);
    }

    private void spawnGlobe(Player owner, EntityDeathEvent e, boolean blood, int level) {
        ItemStack item = new ItemStack(blood ? Material.MAGMA_CREAM : Material.SNOWBALL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((blood ? C.RED : C.WHITE) + (blood ? "Blood Globe" : "Bone Globe"));
            item.setItemMeta(meta);
        }

        Item dropped = e.getEntity().getWorld().dropItemNaturally(e.getEntity().getLocation().add(0, 0.35, 0), item);
        dropped.setPickupDelay(10);
        if (blood) {
            bloodGlobes.add(dropped.getUniqueId());
        } else {
            boneGlobes.add(dropped.getUniqueId());
        }

        int life = getGlobeLifetimeTicks(level);
        J.s(() -> {
            bloodGlobes.remove(dropped.getUniqueId());
            boneGlobes.remove(dropped.getUniqueId());
            if (dropped.isValid()) {
                dropped.remove();
            }
        }, life);
        xp(owner, getConfig().xpPerGlobeSpawned);
    }

    private void applyBuff(Player p, boolean blood, int level) {
        if (blood) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, getConfig().bloodBuffTicks, getConfig().bloodBuffAmplifier, false, true, true), true);
            SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        } else {
            applyRandomBoneBuffs(p, level);
            SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.ENTITY_SKELETON_HURT, 0.7f, 1.2f);
        }
    }

    private void applyRandomBoneBuffs(Player p, int level) {
        int buffs = Math.max(1, (int) Math.round(getConfig().boneBuffCountBase + (getLevelPercent(level) * getConfig().boneBuffCountFactor)));
        List<PotionEffectType> pool = new ArrayList<>(List.of(BONE_EFFECT_POOL));
        Collections.shuffle(pool);
        buffs = Math.min(pool.size(), buffs);

        int duration = getConfig().boneBuffTicks;
        int amp = Math.max(0, getConfig().boneBuffAmplifier);
        for (int i = 0; i < buffs; i++) {
            PotionEffectType type = pool.get(i);
            int a = type == PotionEffectType.ABSORPTION && getLevelPercent(level) >= 0.75 ? amp + 1 : amp;
            p.addPotionEffect(new PotionEffect(type, duration, a, false, true, true), true);
        }
    }

    private double getGlobeChance(int level) {
        return Math.min(getConfig().maxGlobeChance, getConfig().globeChanceBase + (getLevelPercent(level) * getConfig().globeChanceFactor));
    }

    private int getGlobeLifetimeTicks(int level) {
        return Math.max(20, (int) Math.round(getConfig().globeLifetimeTicksBase + (getLevelPercent(level) * getConfig().globeLifetimeTicksFactor)));
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Kills can spawn temporary blood and bone globes that grant short buffs when picked up.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.72;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Globe Chance Base for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double globeChanceBase = 0.16;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Globe Chance Factor for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double globeChanceFactor = 0.42;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Globe Chance for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxGlobeChance = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Globe Lifetime Ticks Base for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double globeLifetimeTicksBase = 120;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Globe Lifetime Ticks Factor for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double globeLifetimeTicksFactor = 220;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Blood Buff Ticks for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int bloodBuffTicks = 80;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Blood Buff Amplifier for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int bloodBuffAmplifier = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bone Buff Ticks for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int boneBuffTicks = 100;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bone Buff Amplifier for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int boneBuffAmplifier = 0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bone Buff Count Base for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double boneBuffCountBase = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bone Buff Count Factor for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double boneBuffCountFactor = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Globe Spawned for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerGlobeSpawned = 8;
    }
}
