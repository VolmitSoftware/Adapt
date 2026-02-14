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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.volmlib.util.format.Form;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import art.arcane.adapt.util.common.inventorygui.Window;

public class SwordsRiposteWindow extends SimpleAdaptation<SwordsRiposteWindow.Config> {
    private final Map<UUID, Long> riposteUntil = new HashMap<>();

    public SwordsRiposteWindow() {
        super("sword-riposte-window");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("sword.riposte_window.description"));
        setDisplayName(Localizer.dLocalize("sword.riposte_window.name"));
        setIcon(Material.GOLDEN_CHESTPLATE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2100);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_SWORD)
                .key("challenge_swords_riposte_200")
                .title(Localizer.dLocalize("advancement.challenge_swords_riposte_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_swords_riposte_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DIAMOND_SWORD)
                        .key("challenge_swords_riposte_2500")
                        .title(Localizer.dLocalize("advancement.challenge_swords_riposte_2500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_swords_riposte_2500.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_swords_riposte_200", "swords.riposte.ripostes-landed", 200, 400);
        registerMilestone("challenge_swords_riposte_2500", "swords.riposte.ripostes-landed", 2500, 1500);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SHIELD)
                .key("challenge_swords_riposte_3in5")
                .title(Localizer.dLocalize("advancement.challenge_swords_riposte_3in5.title"))
                .description(Localizer.dLocalize("advancement.challenge_swords_riposte_3in5.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.duration(getWindowMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("sword.riposte_window.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getDamageBonus(level), 0) + C.GRAY + " " + Localizer.dLocalize("sword.riposte_window.lore2"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        riposteUntil.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player defender) {
            armRiposte(defender);
        }

        if (!(e.getDamager() instanceof Player attacker) || !hasAdaptation(attacker) || !isSword(attacker.getInventory().getItemInMainHand())) {
            return;
        }

        long now = System.currentTimeMillis();
        long until = riposteUntil.getOrDefault(attacker.getUniqueId(), 0L);
        if (until < now) {
            return;
        }

        if (!(e.getEntity() instanceof LivingEntity target)) {
            return;
        }

        if (target instanceof Player victim) {
            if (!canPVP(attacker, victim.getLocation())) {
                return;
            }
        } else if (!canPVE(attacker, target.getLocation())) {
            return;
        }

        e.setDamage(e.getDamage() * (1D + getDamageBonus(getLevel(attacker))));
        riposteUntil.remove(attacker.getUniqueId());
        if (areParticlesEnabled()) {
            attacker.getWorld().spawnParticle(Particle.SWEEP_ATTACK, attacker.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
        }
        SoundPlayer sp = SoundPlayer.of(attacker.getWorld());
        sp.play(attacker.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.7f, 1.6f);
        sp.play(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.2f);
        xp(attacker, e.getDamage() * getConfig().xpPerBuffedDamage);
        getPlayer(attacker).getData().addStat("swords.riposte.ripostes-landed", 1);

        // Special achievement: 3 ripostes within 5 seconds
        long riposteWindowStart = getStorageLong(attacker, "riposteWindowStart", 0L);
        int riposteWindowCount = getStorageInt(attacker, "riposteWindowCount", 0);
        if (now - riposteWindowStart > 5000L) {
            riposteWindowStart = now;
            riposteWindowCount = 1;
        } else {
            riposteWindowCount++;
        }
        setStorage(attacker, "riposteWindowStart", riposteWindowStart);
        setStorage(attacker, "riposteWindowCount", riposteWindowCount);
        if (riposteWindowCount >= 3 && AdaptConfig.get().isAdvancements() && !getPlayer(attacker).getData().isGranted("challenge_swords_riposte_3in5")) {
            getPlayer(attacker).getAdvancementHandler().grant("challenge_swords_riposte_3in5");
        }
    }

    private void armRiposte(Player defender) {
        boolean hasShield = defender.getInventory().getItemInOffHand().getType() == Material.SHIELD
                || defender.getInventory().getItemInMainHand().getType() == Material.SHIELD;
        if (!hasAdaptation(defender) || !defender.isBlocking() || !hasShield) {
            return;
        }

        int level = getLevel(defender);
        riposteUntil.put(defender.getUniqueId(), System.currentTimeMillis() + getWindowMillis(level));
        SoundPlayer.of(defender.getWorld()).play(defender.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.6f, 0.9f);
    }

    private long getWindowMillis(int level) {
        return Math.max(150L, (long) Math.round(getConfig().windowMillisBase + (getLevelPercent(level) * getConfig().windowMillisFactor)));
    }

    private double getDamageBonus(int level) {
        return getConfig().damageBonusBase + (getLevelPercent(level) * getConfig().damageBonusFactor);
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
    @ConfigDescription("Blocking with a shield arms a short riposte window for your next sword strike.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.71;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Window Millis Base for the Swords Riposte Window adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double windowMillisBase = 350;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Window Millis Factor for the Swords Riposte Window adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double windowMillisFactor = 550;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Base for the Swords Riposte Window adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageBonusBase = 0.22;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Factor for the Swords Riposte Window adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageBonusFactor = 0.75;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Buffed Damage for the Swords Riposte Window adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerBuffedDamage = 1.8;
    }
}
