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

import art.arcane.spatial.matter.SpatialMatter;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.data.WorldData;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.content.adaptation.brewing.BrewingLingering;
import com.volmit.adapt.content.adaptation.brewing.BrewingSuperHeated;
import com.volmit.adapt.content.matter.BrewingStandOwner;
import com.volmit.adapt.content.matter.BrewingStandOwnerMatter;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.meta.PotionMeta;

public class SkillBrewing extends SimpleSkill<SkillBrewing.Config> {
    public SkillBrewing() {
        super("brewing", Adapt.dLocalize("skill", "brewing", "icon"));
        registerConfiguration(Config.class);
        setColor(C.LIGHT_PURPLE);
        setDescription(Adapt.dLocalize("skill", "brewing", "description"));
        setDisplayName(Adapt.dLocalize("skill", "brewing", "name"));
        setInterval(5851);
        setIcon(Material.LINGERING_POTION);
        registerAdaptation(new BrewingLingering());
        registerAdaptation(new BrewingSuperHeated());
        SpatialMatter.registerSliceType(new BrewingStandOwnerMatter());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerItemConsumeEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR))) {
            return;
        }
        Adapt.verbose(e.getItem().toString());
        if (e.getItem().getItemMeta() instanceof PotionMeta o
                && !e.getItem().toString().contains("potion-type=minecraft:water")
                && !e.getItem().toString().contains("potion-type=minecraft:mundane")
                && !e.getItem().toString().contains("potion-type=minecraft:thick")
                && !e.getItem().toString().contains("potion-type=minecraft:awkward")) {
            xp(p, p.getLocation(),
                    getConfig().splashXP
                            + (getConfig().splashMultiplier * o.getCustomEffects().stream().mapToDouble(i -> (i.getAmplifier() + 1) * (i.getDuration() / 20D)).sum())
                            + (getConfig().splashMultiplier * (o.getBasePotionData().isUpgraded() ? 50 : 25)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PotionSplashEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        if (e.getPotion().getShooter() instanceof Player p) {
            if (e.isCancelled()) {
                return;
            }
            if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
                return;
            }
            if (!AdaptConfig.get().isXpInCreative() && (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR))) {
                return;
            }
            AdaptPlayer a = getPlayer(p);
            getPlayer(p).getData().addStat("brewing.splashes", 1);
            xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().splashXP + (getConfig().splashMultiplier * e.getPotion().getEffects().stream().mapToDouble(i -> (i.getAmplifier() + 1) * (i.getDuration() / 20D)).sum()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockPlaceEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR))) {
            return;
        }
        if (e.getBlock().getType().equals(Material.BREWING_STAND)) {
            if (!e.isCancelled()) {
                WorldData.of(e.getBlock().getWorld()).getMantle().set(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ(), new BrewingStandOwner(p.getUniqueId()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockBreakEvent e) {
        if (!this.isEnabled()) {
            return;
        }
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR))) {
            return;
        }
        if (!e.isCancelled()) {
            if (e.getBlock().getType().equals(Material.BREWING_STAND)) {
                WorldData.of(e.getBlock().getWorld()).getMantle().remove(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ(), BrewingStandOwner.class);
            }
        }
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        double splashXP = 115;
        double splashMultiplier = 0.25;
    }
}
