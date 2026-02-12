package com.volmit.adapt.content.adaptation.stealth;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
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
        setDescription(Localizer.dLocalize("stealth", "enderveil", "description"));
        setDisplayName(Localizer.dLocalize("stealth", "enderveil", "name"));
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
        v.addLore(C.GRAY + Localizer.dLocalize("stealth", "enderveil",  "lore" + (level < 2 ? 1 : 2)));
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 2.325;
    }
}
