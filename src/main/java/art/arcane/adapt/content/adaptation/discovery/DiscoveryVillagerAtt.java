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

package art.arcane.adapt.content.adaptation.discovery;
import art.arcane.volmlib.util.format.Form;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.collection.KMap;
import de.slikey.effectlib.effect.BleedEffect;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;

public class DiscoveryVillagerAtt extends SimpleAdaptation<DiscoveryVillagerAtt.Config> {
    private final KMap<UUID, Integer> active = new KMap<>();

    public DiscoveryVillagerAtt() {
        super("discovery-villager-att");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("discovery.villager.description"));
        setDisplayName(Localizer.dLocalize("discovery.villager.name"));
        setIcon(Material.GLASS_BOTTLE);
        setInterval(2432);
        setBaseCost(getConfig().baseCost);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.EMERALD)
                .key("challenge_discovery_villager_100")
                .title(Localizer.dLocalize("advancement.challenge_discovery_villager_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_discovery_villager_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.EMERALD_BLOCK)
                        .key("challenge_discovery_villager_2500")
                        .title(Localizer.dLocalize("advancement.challenge_discovery_villager_2500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_discovery_villager_2500.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_discovery_villager_100", "discovery.villager-att.improved-trades", 100, 300);
        registerMilestone("challenge_discovery_villager_2500", "discovery.villager-att.improved-trades", 2500, 1000);
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("discovery.villager.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getEffectiveness(getLevelPercent(level)), 0) + C.GRAY + " " + Localizer.dLocalize("discovery.villager.lore2"));
        v.addLore(C.GREEN + "+ " + getXpTaken(level) + " " + C.GRAY + Localizer.dLocalize("discovery.villager.lore3"));
    }

    private double getEffectiveness(double multiplier) {
        return Math.min(getConfig().maxEffectiveness, multiplier * multiplier + getConfig().effectivenessBase);
    }

    private int getXpTaken(double level) {
        double d = (getConfig().levelCostAdd * getConfig().amplifier) - (level * getConfig().levelDrain);
        return (int) d;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerInteractEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        SoundPlayer sp = SoundPlayer.of(p);
        if (e.getRightClicked() instanceof Villager v && hasAdaptation(p)) {
            if (ThreadLocalRandom.current().nextDouble() <= getEffectiveness(getLevelPercent(getLevel(p)))) {
                if (p.getLevel() - getXpTaken(getLevel(p)) > 0) {
                    BleedEffect blood = new BleedEffect(Adapt.instance.adaptEffectManager);  // Enemy gets blood
                    blood.material = Material.EMERALD;
                    blood.setEntity(v);
                    p.setLevel((p.getLevel() - getXpTaken(getLevel(p))));
                    sp.play(p.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 1f, 1f);
                    sp.play(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    int level = getLevel(p);
                    active.put(p.getUniqueId(), level);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 60, level, true, true));
                    getPlayer(p).getData().addStat("discovery.villager-att.improved-trades", 1);
                } else {
                    BleedEffect blood = new BleedEffect(Adapt.instance.adaptEffectManager);  // Enemy gets blood
                    blood.material = Material.STONE;
                    v.shakeHead();
                    blood.setEntity(v);
                    sp.play(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p)) {
            return;
        }
        int level = active.getOrDefault(p.getUniqueId(), 0);
        if (level == 0) return;

        if (event.isCancelled()) {
            active.remove(p.getUniqueId());
            p.removePotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
        } else {
            p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 60, level, true, true));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p) || !active.containsKey(p.getUniqueId())) {
            return;
        }

        active.remove(p.getUniqueId());
        p.removePotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
    }

    @EventHandler
    public void on(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onTick() {
        J.s(() -> active.forEach((p, lvl) -> {
            var player = Bukkit.getPlayer(p);
            if (player == null) return;
            player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 60, lvl, true, true));
        }));
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
    @ConfigDescription("Get better villager trades at the cost of XP per interaction.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.01;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Effectiveness Base for the Discovery Villager Att adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double effectivenessBase = 0.005;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Effectiveness for the Discovery Villager Att adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxEffectiveness = 100;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Level Drain for the Discovery Villager Att adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int levelDrain = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Level Cost Add for the Discovery Villager Att adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int levelCostAdd = 10;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Amplifier for the Discovery Villager Att adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double amplifier = 1.0;
    }
}
