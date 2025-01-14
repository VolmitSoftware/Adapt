package com.volmit.adapt.content.adaptation.pickaxe;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.collection.KList;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

public class PickaxeSilkSpawner extends SimpleAdaptation<PickaxeSilkSpawner.Config> {

    public PickaxeSilkSpawner() {
        super("pickaxe-silk-spawner");
        registerConfiguration(PickaxeSilkSpawner.Config.class);
        setDescription(Localizer.dLocalize("pickaxe", "silkspawner", "description"));
        setDisplayName(Localizer.dLocalize("pickaxe", "silkspawner", "name"));
        setIcon(Material.SPAWNER);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(8444);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        var player = event.getPlayer();
        var block = event.getBlock();
        if (!event.isDropItems() || !hasAdaptation(player) || block.getType() != Material.SPAWNER || !canBlockBreak(player, event.getBlock().getLocation()))
            return;
        var level = getLevel(player);
        if (level == 1 && !player.getInventory().getItemInMainHand().getEnchantments().containsKey(Enchantment.SILK_TOUCH)) {
            return;
        } else if (level > 1 && !player.isSneaking()) {
            return;
        }

        event.setDropItems(false);
        var items = new KList<Item>();
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.SPAWNER), items::add);

        var dropEvent = new BlockDropItemEvent(block, block.getState(), player, items);
        Bukkit.getPluginManager().callEvent(dropEvent);
        if (dropEvent.isCancelled() && !items.isEmpty()) {
            items.forEach(Item::remove);
            items.clear();
        }
    }

    @Override
    public boolean isEnabled() {
        return !getConfig().enabled;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("pickaxe", "silkspawner", "lore" + (level < 2 ? 1 : 2)));
    }

    @Override
    public void onTick() {
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        final boolean permanent = false;
        final boolean enabled = true;
        final int baseCost = 6;
        final int maxLevel = 2;
        final int initialCost = 4;
        final double costFactor = 2.325;
    }
}
