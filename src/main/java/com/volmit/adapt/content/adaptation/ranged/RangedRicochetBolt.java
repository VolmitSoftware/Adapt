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

package com.volmit.adapt.content.adaptation.ranged;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;

public class RangedRicochetBolt extends SimpleAdaptation<RangedRicochetBolt.Config> {
    private static final String RICOCHET_COUNT_META = "adapt-ricochet-count";
    private static final String RICOCHET_MAX_META = "adapt-ricochet-max";
    private static final String BONUS_DAMAGE_META = "adapt-ricochet-bonus-damage";

    public RangedRicochetBolt() {
        super("ranged-ricochet-bolt");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("ranged.ricochet_bolt.description"));
        setDisplayName(Localizer.dLocalize("ranged.ricochet_bolt.name"));
        setIcon(Material.SPECTRAL_ARROW);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1400);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SPECTRAL_ARROW)
                .key("challenge_ranged_ricochet_kills_50")
                .title(Localizer.dLocalize("advancement.challenge_ranged_ricochet_kills_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_ranged_ricochet_kills_50.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.SPECTRAL_ARROW)
                        .key("challenge_ranged_ricochet_kills_500")
                        .title(Localizer.dLocalize("advancement.challenge_ranged_ricochet_kills_500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_ranged_ricochet_kills_500.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_ricochet_kills_50").goal(50).stat("ranged.ricochet-bolt.ricochet-kills").reward(500).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_ranged_ricochet_kills_500").goal(500).stat("ranged.ricochet-bolt.ricochet-kills").reward(2000).build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getMaxRicochets(level) + C.GRAY + " " + Localizer.dLocalize("ranged.ricochet_bolt.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getSpeedBonusPerRicochet(level), 0) + C.GRAY + " " + Localizer.dLocalize("ranged.ricochet_bolt.lore2"));
        v.addLore(C.GREEN + "+ " + Form.f(getDamageBonusPerRicochet(level), 2) + C.GRAY + " " + Localizer.dLocalize("ranged.ricochet_bolt.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player p) || !hasAdaptation(p)) {
            return;
        }

        if (e.getHitBlock() == null) {
            return;
        }

        int level = getLevel(p);
        int ricochetCount = Math.max(0, getMetadataInt(arrow, RICOCHET_COUNT_META, 0));
        int maxRicochets = Math.max(1, getMetadataInt(arrow, RICOCHET_MAX_META, getMaxRicochets(level)));
        if (ricochetCount >= maxRicochets) {
            return;
        }

        Vector incoming = resolveIncomingVector(arrow);
        if (incoming.lengthSquared() < getConfig().minRicochetVelocitySquared) {
            return;
        }

        BlockFace hitFace = resolveHitFace(e, incoming);
        if (hitFace == null) {
            return;
        }

        Vector reflectedDir = reflect(incoming.clone().normalize(), hitFace);
        if (reflectedDir.lengthSquared() <= 0.0000001) {
            return;
        }

        reflectedDir.normalize();
        double nextSpeed = Math.max(getConfig().minimumPostBounceSpeed, incoming.length()) * (1D + getSpeedBonusPerRicochet(level));
        if (nextSpeed <= 0) {
            return;
        }

        Vector ricochetVelocity = reflectedDir.clone().multiply(nextSpeed);
        int nextRicochetCount = ricochetCount + 1;
        double bonusDamage = getMetadataDouble(arrow, BONUS_DAMAGE_META, 0D) + getDamageBonusPerRicochet(level);

        Location bounceLocation = arrow.getLocation().clone()
                .add(hitFace.getDirection().normalize().multiply(getConfig().spawnOffsetFromSurface))
                .add(reflectedDir.clone().multiply(getConfig().spawnOffsetAlongDirection));
        AbstractArrow ricochet = spawnRicochetArrow(arrow, bounceLocation, ricochetVelocity, p);
        if (ricochet == null) {
            return;
        }

        ricochet.setMetadata(RICOCHET_COUNT_META, new FixedMetadataValue(Adapt.instance, nextRicochetCount));
        ricochet.setMetadata(RICOCHET_MAX_META, new FixedMetadataValue(Adapt.instance, maxRicochets));
        ricochet.setMetadata(BONUS_DAMAGE_META, new FixedMetadataValue(Adapt.instance, bonusDamage));

        Location fx = e.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
        arrow.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, fx, Math.max(1, getConfig().sparkParticleCount),
                getConfig().sparkSpread, getConfig().sparkSpread, getConfig().sparkSpread, 0.02);
        arrow.getWorld().spawnParticle(Particle.CRIT, fx, Math.max(1, getConfig().critParticleCount),
                getConfig().critSpread, getConfig().critSpread, getConfig().critSpread, 0.08);
        SoundPlayer sp = SoundPlayer.of(arrow.getWorld());
        sp.play(fx, Sound.BLOCK_ANVIL_HIT, 0.85f, (float) Math.max(0.4, getConfig().bouncePitchBase - (nextRicochetCount * getConfig().bouncePitchDropPerRicochet)));
        sp.play(fx, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.9f, (float) Math.min(2.0, getConfig().sparkPitchBase + (nextRicochetCount * getConfig().sparkPitchRaisePerRicochet)));
        xp(p, getConfig().xpPerRicochet + (nextRicochetCount * getConfig().xpPerRicochetStep));
        getPlayer(p).getData().addStat("ranged.ricochet-bolt.total-ricochets", 1);
        arrow.remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Projectile projectile) || !(projectile.getShooter() instanceof Player p) || !projectile.hasMetadata(BONUS_DAMAGE_META)) {
            return;
        }

        if (e.getEntity() instanceof Player victim) {
            if (!canPVP(p, victim.getLocation())) {
                return;
            }
        } else if (!canPVE(p, e.getEntity().getLocation())) {
            return;
        }

        double bonusDamage = getMetadataDouble(projectile, BONUS_DAMAGE_META, 0D);
        if (bonusDamage > 0) {
            e.setDamage(e.getDamage() + bonusDamage);
        }
    }

    @EventHandler
    public void on(EntityDeathEvent e) {
        if (e.getEntity().getKiller() instanceof Player p && hasAdaptation(p)) {
            if (e.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent dmg
                    && dmg.getDamager() instanceof Projectile projectile
                    && projectile.hasMetadata(RICOCHET_COUNT_META)
                    && projectile.getShooter() instanceof Player) {
                getPlayer(p).getData().addStat("ranged.ricochet-bolt.ricochet-kills", 1);
            }
        }
    }

    private AbstractArrow spawnRicochetArrow(AbstractArrow original, Location spawnLocation, Vector velocity, Player shooter) {
        Vector dir = velocity.clone().normalize();
        float speed = (float) Math.max(0.2, velocity.length());
        if (original instanceof SpectralArrow sourceSpectral) {
            SpectralArrow spectral = original.getWorld().spawn(spawnLocation, SpectralArrow.class);
            spectral.setVelocity(velocity);
            spectral.setShooter(shooter);
            spectral.setGlowingTicks(sourceSpectral.getGlowingTicks());
            spectral.setDamage(original.getDamage());
            spectral.setPierceLevel(original.getPierceLevel());
            spectral.setCritical(original.isCritical());
            spectral.setKnockbackStrength(original.getKnockbackStrength());
            spectral.setFireTicks(original.getFireTicks());
            return spectral;
        }

        Arrow arrow = original.getWorld().spawnArrow(spawnLocation, dir, speed, 0f);
        arrow.setShooter(shooter);
        arrow.setDamage(original.getDamage());
        arrow.setCritical(original.isCritical());
        arrow.setKnockbackStrength(original.getKnockbackStrength());
        arrow.setPierceLevel(original.getPierceLevel());
        arrow.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
        arrow.setFireTicks(original.getFireTicks());

        if (original instanceof Arrow sourceArrow) {
            arrow.setBasePotionType(sourceArrow.getBasePotionType());
            sourceArrow.getCustomEffects().forEach(effect -> arrow.addCustomEffect(effect, true));
        }

        return arrow;
    }

    private Vector reflect(Vector incoming, BlockFace face) {
        Vector normal = face.getDirection().normalize();
        double dot = incoming.dot(normal);
        return incoming.clone().subtract(normal.multiply(2D * dot));
    }

    private Vector resolveIncomingVector(AbstractArrow arrow) {
        Vector liveVelocity = arrow.getVelocity().clone();
        if (liveVelocity.lengthSquared() >= getConfig().minimumLiveVelocitySquared) {
            return liveVelocity;
        }

        Vector facing = arrow.getLocation().getDirection().clone();
        if (facing.lengthSquared() > 0.0000001) {
            return facing.normalize().multiply(Math.max(getConfig().minimumPostBounceSpeed, liveVelocity.length()));
        }

        return liveVelocity;
    }

    private BlockFace resolveHitFace(ProjectileHitEvent e, Vector incoming) {
        if (e.getHitBlockFace() != null) {
            return e.getHitBlockFace();
        }

        double ax = Math.abs(incoming.getX());
        double ay = Math.abs(incoming.getY());
        double az = Math.abs(incoming.getZ());

        if (ay >= ax && ay >= az) {
            return incoming.getY() > 0 ? BlockFace.DOWN : BlockFace.UP;
        }

        if (ax >= az) {
            return incoming.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
        }

        return incoming.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    private int getMetadataInt(Projectile projectile, String key, int fallback) {
        for (MetadataValue value : projectile.getMetadata(key)) {
            if (value.getOwningPlugin() == Adapt.instance) {
                return value.asInt();
            }
        }

        return fallback;
    }

    private double getMetadataDouble(Projectile projectile, String key, double fallback) {
        for (MetadataValue value : projectile.getMetadata(key)) {
            if (value.getOwningPlugin() == Adapt.instance) {
                return value.asDouble();
            }
        }

        return fallback;
    }

    private int getMaxRicochets(int level) {
        return Math.max(1, (int) Math.round(getConfig().maxRicochetsBase + (getLevelPercent(level) * getConfig().maxRicochetsFactor)));
    }

    private double getSpeedBonusPerRicochet(int level) {
        return Math.min(getConfig().maxSpeedBonusPerRicochet,
                getConfig().speedBonusPerRicochetBase + (getLevelPercent(level) * getConfig().speedBonusPerRicochetFactor));
    }

    private double getDamageBonusPerRicochet(int level) {
        return Math.min(getConfig().maxDamageBonusPerRicochet,
                getConfig().damageBonusPerRicochetBase + (getLevelPercent(level) * getConfig().damageBonusPerRicochetFactor));
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
    @ConfigDescription("Arrows ricochet from block impacts with chained bounces, scaling speed, and bonus damage.")
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
        double costFactor = 0.74;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Ricochets Base for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxRicochetsBase = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Ricochets Factor for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxRicochetsFactor = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Speed Bonus Per Ricochet Base for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double speedBonusPerRicochetBase = 0.08;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Speed Bonus Per Ricochet Factor for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double speedBonusPerRicochetFactor = 0.27;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Speed Bonus Per Ricochet for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxSpeedBonusPerRicochet = 0.4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Per Ricochet Base for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageBonusPerRicochetBase = 0.55;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Per Ricochet Factor for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageBonusPerRicochetFactor = 2.55;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Damage Bonus Per Ricochet for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxDamageBonusPerRicochet = 3.65;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Min Ricochet Velocity Squared for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double minRicochetVelocitySquared = 0.09;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Minimum Live Velocity Squared for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double minimumLiveVelocitySquared = 0.0004;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Minimum Post Bounce Speed for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double minimumPostBounceSpeed = 0.45;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Spawn Offset From Surface for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double spawnOffsetFromSurface = 0.22;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Spawn Offset Along Direction for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double spawnOffsetAlongDirection = 0.14;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Spark Particle Count for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int sparkParticleCount = 18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Spark Spread for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double sparkSpread = 0.18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Crit Particle Count for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int critParticleCount = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Crit Spread for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double critSpread = 0.14;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bounce Pitch Base for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bouncePitchBase = 1.35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bounce Pitch Drop Per Ricochet for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bouncePitchDropPerRicochet = 0.08;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Spark Pitch Base for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double sparkPitchBase = 1.05;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Spark Pitch Raise Per Ricochet for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double sparkPitchRaisePerRicochet = 0.07;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Ricochet for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerRicochet = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Ricochet Step for the Ranged Ricochet Bolt adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerRicochetStep = 2;
    }
}
