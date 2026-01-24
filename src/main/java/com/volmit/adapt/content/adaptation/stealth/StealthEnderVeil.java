package com.volmit.adapt.content.adaptation.stealth;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.reflect.events.api.ReflectiveHandler;
import com.volmit.adapt.util.reflect.events.api.entity.EndermanAttackPlayerEvent;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

public class StealthEnderVeil extends SimpleAdaptation<StealthEnderVeil.Config> {

    public StealthEnderVeil() {
        super("stealth-enderveil");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("stealth.ender_veil.description"));
        setDisplayName(Localizer.dLocalize("stealth.ender_veil.name"));
        setIcon(Material.CARVED_PUMPKIN);
        setBaseCost(getConfig().baseCost);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInterval(9182);
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(Localizer.dLocalize("stealth.ender_veil.lore.level_" + (level < 2 ? 1 : 2)));
    }

    @Override
    public void onTick() {

    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        var target = event.getTarget();
        if (target == null
                || target.getType() != EntityType.PLAYER
                || event.getEntityType() != EntityType.ENDERMAN
                || !(event.getTarget() instanceof Player player)
                || !hasAdaptation(player)) {
            return;
        }

        if (getLevel(player) > 1 || player.isSneaking()) {
            event.setCancelled(true);
        }
    }

    @ReflectiveHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTarget(EndermanAttackPlayerEvent event) {
        var player = event.getPlayer();
        if (!hasAdaptation(player)) {
            return;
        }

        if (getLevel(player) > 1 || player.isSneaking()) {
            event.setCancelled(true);
        }
    }

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 6;
        int maxLevel = 2;
        int initialCost = 4;
        double costFactor = 2.325;
    }
}
