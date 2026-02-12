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

package com.volmit.adapt.content.adaptation.agility;

import com.volmit.adapt.AdaptConfig;
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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AgilityRollLanding extends SimpleAdaptation<AgilityRollLanding.Config> {
    private final Map<UUID, Long> rollInputs = new HashMap<>();
    private final Map<UUID, Long> proneUntilMillis = new HashMap<>();

    public AgilityRollLanding() {
        super("agility-roll-landing");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("agility.roll_landing.description"));
        setDisplayName(Localizer.dLocalize("agility.roll_landing.name"));
        setIcon(Material.HAY_BLOCK);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1200);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.HAY_BLOCK)
                .key("challenge_agility_roll_100")
                .title(Localizer.dLocalize("advancement.challenge_agility_roll_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_agility_roll_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.SLIME_BLOCK)
                        .key("challenge_agility_roll_1000")
                        .title(Localizer.dLocalize("advancement.challenge_agility_roll_1000.title"))
                        .description(Localizer.dLocalize("advancement.challenge_agility_roll_1000.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ELYTRA)
                .key("challenge_agility_fearless")
                .title(Localizer.dLocalize("advancement.challenge_agility_fearless.title"))
                .description(Localizer.dLocalize("advancement.challenge_agility_fearless.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.HIDDEN)
                .build());
        registerStatTracker(AdaptStatTracker.builder()
                .advancement("challenge_agility_roll_100")
                .goal(100)
                .stat("agility.roll-landing.damage-prevented")
                .reward(300)
                .build());
        registerStatTracker(AdaptStatTracker.builder()
                .advancement("challenge_agility_roll_1000")
                .goal(1000)
                .stat("agility.roll-landing.damage-prevented")
                .reward(1000)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getFallReduction(level), 0) + C.GRAY + " " + Localizer.dLocalize("agility.roll_landing.lore1"));
        v.addLore(C.GREEN + "+ " + Form.duration(getInputWindowMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("agility.roll_landing.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("agility.roll_landing.lore3"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        rollInputs.remove(e.getPlayer().getUniqueId());
        proneUntilMillis.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (e.isSneaking()) {
            recordRollInput(p, null, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerMoveEvent e) {
        recordRollInput(e.getPlayer(), e.getFrom().getY(), e.getTo() == null ? null : e.getTo().getY());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageEvent e) {
        if (e.isCancelled() || !(e.getEntity() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        if (!hasAdaptation(p) || p.hasCooldown(Material.HAY_BLOCK)) {
            return;
        }

        long now = System.currentTimeMillis();
        long input = rollInputs.getOrDefault(p.getUniqueId(), 0L);
        int level = getLevel(p);
        if (now - input > getInputWindowMillis(level)) {
            return;
        }

        double absorbCap = e.getDamage() * getFallReduction(level);
        int hungerNeeded = (int) Math.ceil(absorbCap * getHungerPerDamage(level));
        if (hungerNeeded <= 0 || p.getFoodLevel() <= 0) {
            return;
        }

        int usableFood = Math.min(p.getFoodLevel(), hungerNeeded);
        double absorbed = usableFood / getHungerPerDamage(level);
        if (absorbed <= 0) {
            return;
        }

        p.setFoodLevel(Math.max(0, p.getFoodLevel() - usableFood));
        e.setDamage(Math.max(0, e.getDamage() - absorbed));
        if (e.getDamage() <= 0.01) {
            e.setCancelled(true);
        }

        p.setCooldown(Material.HAY_BLOCK, getCooldownTicks(level));
        triggerRollPose(p, level);
        SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.ENTITY_PLAYER_SMALL_FALL, 0.8f, 0.7f);
        SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 0.89f);
        SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.BLOCK_WOOL_BREAK, 0.55f, 0.9f);
        getPlayer(p).getData().addStat("agility.roll-landing.damage-prevented", absorbed);
        if (p.getFallDistance() >= 30 && AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_agility_fearless")) {
            getPlayer(p).getAdvancementHandler().grant("challenge_agility_fearless");
        }
        xp(p, absorbed * getConfig().xpPerDamagePrevented);
    }

    private void recordRollInput(Player p, Double fromY, Double toY) {
        if (!hasAdaptation(p) || !p.isSneaking()) {
            return;
        }

        if (p.isOnGround() || p.isFlying() || p.isGliding()) {
            return;
        }

        boolean descending = p.getVelocity().getY() <= getConfig().maxVerticalVelocityForRollInput;
        if (!descending && fromY != null && toY != null) {
            descending = toY < fromY;
        }

        if (!descending) {
            return;
        }

        rollInputs.put(p.getUniqueId(), System.currentTimeMillis());
    }

    private void triggerRollPose(Player p, int level) {
        int proneTicks = getProneTicks(level);
        long until = System.currentTimeMillis() + (proneTicks * 50L);
        UUID id = p.getUniqueId();
        proneUntilMillis.put(id, until);
        p.setSwimming(true);

        J.s(() -> {
            if (!p.isOnline() || p.isDead()) {
                proneUntilMillis.remove(id);
                return;
            }

            long expectedUntil = proneUntilMillis.getOrDefault(id, 0L);
            if (expectedUntil > System.currentTimeMillis()) {
                return;
            }

            proneUntilMillis.remove(id);
            if (!p.isInWater()) {
                p.setSwimming(false);
            }
        }, proneTicks);
    }

    private double getFallReduction(int level) {
        return Math.min(getConfig().maxReduction, getConfig().reductionBase + (getLevelPercent(level) * getConfig().reductionFactor));
    }

    private long getInputWindowMillis(int level) {
        return Math.max(60L, Math.round(getConfig().inputWindowMillisBase + (getLevelPercent(level) * getConfig().inputWindowMillisFactor)));
    }

    private double getHungerPerDamage(int level) {
        return Math.max(0.1, getConfig().hungerPerDamageBase - (getLevelPercent(level) * getConfig().hungerPerDamageReduction));
    }

    private int getCooldownTicks(int level) {
        return Math.max(4, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
    }

    private int getProneTicks(int level) {
        return Math.max(2, (int) Math.round(getConfig().proneTicksBase + (getLevelPercent(level) * getConfig().proneTicksFactor)));
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
    @ConfigDescription("Timed sneak before landing converts part of fall damage into hunger cost.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.62;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reduction Base for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double reductionBase = 0.22;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reduction Factor for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double reductionFactor = 0.43;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Reduction for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxReduction = 0.8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Input Window Millis Base for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double inputWindowMillisBase = 190;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Input Window Millis Factor for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double inputWindowMillisFactor = 260;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Hunger Per Damage Base for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double hungerPerDamageBase = 1.4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Hunger Per Damage Reduction for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double hungerPerDamageReduction = 0.75;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksBase = 22;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksFactor = 12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Vertical Velocity For Roll Input for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxVerticalVelocityForRollInput = -0.08;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Prone Ticks Base for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double proneTicksBase = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Prone Ticks Factor for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double proneTicksFactor = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Damage Prevented for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerDamagePrevented = 4.2;
    }
}
