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

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.volmlib.util.format.Form;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChronosTemporalEcho extends SimpleAdaptation<ChronosTemporalEcho.Config> {
    private static final String ECHO_META = "adapt-chronos-temporal-echo";
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ChronosTemporalEcho() {
        super("chronos-temporal-echo");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("chronos.temporal_echo.description"));
        setDisplayName(Localizer.dLocalize("chronos.temporal_echo.name"));
        setIcon(Material.AMETHYST_CLUSTER);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1600);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SPECTRAL_ARROW)
                .key("challenge_chronos_echo_200")
                .title(Localizer.dLocalize("advancement.challenge_chronos_echo_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_chronos_echo_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_chronos_echo_200", "chronos.temporal-echo.echo-hits", 200, 400);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.duration(getEchoDelayTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("chronos.temporal_echo.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getEchoVelocityFactor(level), 0) + C.GRAY + " " + Localizer.dLocalize("chronos.temporal_echo.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("chronos.temporal_echo.lore3"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p) || !hasAdaptation(p) || e.getEntity().hasMetadata(ECHO_META)) {
            return;
        }

        EchoType echoType = getEchoType(e.getEntity());
        if (echoType == null) {
            return;
        }

        int level = getLevel(p);
        long now = System.currentTimeMillis();
        if (now < cooldowns.getOrDefault(p.getUniqueId(), 0L)) {
            return;
        }

        cooldowns.put(p.getUniqueId(), now + getCooldownMillis(level));
        Projectile original = e.getEntity();
        Vector originalVelocity = original.getVelocity().clone();
        int delay = getEchoDelayTicks(level);
        J.s(() -> spawnEcho(p, echoType, originalVelocity, level), delay);
    }

    private void spawnEcho(Player p, EchoType type, Vector velocity, int level) {
        if (!p.isOnline() || p.isDead()) {
            return;
        }

        Projectile echo = switch (type) {
            case ARROW -> p.getWorld().spawnArrow(p.getEyeLocation().add(p.getLocation().getDirection().normalize().multiply(0.35)),
                    p.getLocation().getDirection(), 0.6f, 0f);
            case SNOWBALL -> p.getWorld().spawn(p.getEyeLocation().add(p.getLocation().getDirection().normalize().multiply(0.35)), Snowball.class);
            case EGG -> p.getWorld().spawn(p.getEyeLocation().add(p.getLocation().getDirection().normalize().multiply(0.35)), Egg.class);
            case ENDER_PEARL -> p.getWorld().spawn(p.getEyeLocation().add(p.getLocation().getDirection().normalize().multiply(0.35)), EnderPearl.class);
        };
        echo.setShooter(p);
        echo.setVelocity(velocity.multiply(getEchoVelocityFactor(level)));
        echo.setMetadata(ECHO_META, new FixedMetadataValue(Adapt.instance, true));
        SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.75f, 1.35f);
        getPlayer(p).getData().addStat("chronos.temporal-echo.echo-hits", 1);
        xp(p, getConfig().xpPerEcho);
    }

    private EchoType getEchoType(Projectile projectile) {
        if (projectile instanceof Arrow) {
            return EchoType.ARROW;
        }

        if (projectile instanceof Snowball) {
            return EchoType.SNOWBALL;
        }

        if (projectile instanceof Egg) {
            return EchoType.EGG;
        }

        if (projectile instanceof EnderPearl) {
            return EchoType.ENDER_PEARL;
        }

        return null;
    }

    private int getEchoDelayTicks(int level) {
        return Math.max(1, (int) Math.round(getConfig().echoDelayTicksBase - (getLevelPercent(level) * getConfig().echoDelayTicksFactor)));
    }

    private double getEchoVelocityFactor(int level) {
        return Math.min(getConfig().maxEchoVelocityFactor, getConfig().echoVelocityFactorBase + (getLevelPercent(level) * getConfig().echoVelocityFactorFactor));
    }

    private long getCooldownMillis(int level) {
        return Math.max(500L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
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
    @ConfigDescription("Replays your last projectile action shortly after firing at reduced power.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.75;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Echo Delay Ticks Base for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double echoDelayTicksBase = 18;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Echo Delay Ticks Factor for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double echoDelayTicksFactor = 10;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Echo Velocity Factor Base for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double echoVelocityFactorBase = 0.45;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Echo Velocity Factor Factor for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double echoVelocityFactorFactor = 0.45;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Echo Velocity Factor for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxEchoVelocityFactor = 0.92;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisBase = 5000;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisFactor = 2600;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Echo for the Chronos Temporal Echo adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerEcho = 12;
    }

    private enum EchoType {
        ARROW,
        SNOWBALL,
        EGG,
        ENDER_PEARL
    }
}
