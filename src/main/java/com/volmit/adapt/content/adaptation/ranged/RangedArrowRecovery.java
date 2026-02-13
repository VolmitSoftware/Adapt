package com.volmit.adapt.content.adaptation.ranged;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.reflect.registries.Enchantments;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

import static xyz.xenondevs.particle.utils.MathUtils.RANDOM;

public class RangedArrowRecovery extends SimpleAdaptation<RangedArrowRecovery.Config> {
    private final Map<Arrow, Player> shotArrows;

    public RangedArrowRecovery() {
        super("ranged-recovery");
        registerConfiguration(RangedArrowRecovery.Config.class);
        setDescription(Localizer.dLocalize("ranged.arrow_recovery.description"));
        setDisplayName(Localizer.dLocalize("ranged.arrow_recovery.name"));
        setIcon(Material.ARROW);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        shotArrows = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ARROW)
                .key("challenge_ranged_arrow_500")
                .title(Localizer.dLocalize("advancement.challenge_ranged_arrow_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_ranged_arrow_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.SPECTRAL_ARROW)
                        .key("challenge_ranged_arrow_10k")
                        .title(Localizer.dLocalize("advancement.challenge_ranged_arrow_10k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_ranged_arrow_10k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_ranged_arrow_500", "ranged.arrow-recovery.arrows-recovered", 500, 300);
        registerMilestone("challenge_ranged_arrow_10k", "ranged.arrow-recovery.arrows-recovered", 10000, 1000);
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && hasAdaptation(player)) {
            if (!event.getBow().containsEnchantment(Enchantments.ARROW_INFINITE)) {
                if (event.getProjectile() instanceof Arrow arrow) {
                    shotArrows.put(arrow, player);
                }
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow) {
            Player shooter = shotArrows.get(arrow);
            if (shooter != null && hasAdaptation(shooter)) {
                int level = getLevel(shooter);
                double chance = getConfig().hitChance[level - 1] / 100.0;
                if (RANDOM.nextDouble() < chance) {
                    ItemStack arrowStack = new ItemStack(Material.ARROW, 1);
                    shooter.getInventory().addItem(arrowStack);
                    getPlayer(shooter).getData().addStat("ranged.arrow-recovery.arrows-recovered", 1);
                    Adapt.info("Arrow added to inventory.");
                }
            }
            shotArrows.remove(arrow);
        }
    }

    private double chancePerLevel(int level) {
        return (getConfig().hitChance[level - 1] / 100.0);
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public void onTick() {
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("ranged.arrow_recovery.lore1"));
        v.addLore(C.GREEN + Localizer.dLocalize("ranged.arrow_recovery.lore2") + chancePerLevel(level));
    }

    @NoArgsConstructor
    @ConfigDescription("Chance to recover arrows after hitting or killing an enemy.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.78;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Hit Chance for the Ranged Arrow Recovery adaptation.", impact = "Add or remove entries to control which values are included.")
        double[] hitChance = {10, 20, 30, 40, 50, 60, 70, 80};
    }
}