package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.events.api.ReflectiveHandler;
import art.arcane.adapt.util.reflect.events.api.entity.EndermanAttackPlayerEvent;
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
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ENDER_EYE)
                .key("challenge_stealth_ender_veil_200")
                .title(Localizer.dLocalize("advancement.challenge_stealth_ender_veil_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_ender_veil_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_stealth_ender_veil_200", "stealth.ender-veil.stares-survived", 200, 300);
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
        v.addLore(C.GRAY + Localizer.dLocalize("stealth.ender_veil.lore" + (level < 2 ? 1 : 2)));
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
            getPlayer(player).getData().addStat("stealth.ender-veil.stares-survived", 1);
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
            getPlayer(player).getData().addStat("stealth.ender-veil.stares-survived", 1);
        }
    }

    @NoArgsConstructor
    @ConfigDescription("Prevent Enderman aggression without wearing a pumpkin.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 6;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1.0;
    }
}
