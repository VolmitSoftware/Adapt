package com.volmit.adapt.content.adaptation.excavation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.content.item.multiItems.OmniTool;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ExcavationOmniTool extends SimpleAdaptation<ExcavationOmniTool.Config> {
    private static final OmniTool omniTool = new OmniTool();

    public ExcavationOmniTool() {
        super("excavation-omnitool");
        registerConfiguration(ExcavationOmniTool.Config.class);
        setDisplayName("Trusty T.O.O.L.");
        setDescription("A Craftable Leatherman Tool, This will swap based on needs.");
        setIcon(Material.DISC_FRAGMENT_5);
        setInterval(20202);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerRecipe(AdaptRecipe.shapeless()
                .key("excavation-omnitool")
                .ingredient(Material.IRON_SHOVEL)
                .ingredient(Material.IRON_PICKAXE)
                .ingredient(Material.IRON_AXE)
                .ingredient(Material.IRON_HOE)
                .ingredient(Material.DIAMOND_BLOCK)
                .ingredient(Material.ENDER_PEARL)
                .result(new ItemStack(Material.DISC_FRAGMENT_5))
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + "This is a morphing tool that holds all the tools, switching when needed");
        v.addLore(C.GREEN + "" + (level + 3) + C.GRAY + "x Levels of haste when you start mining ANY block with the right tool");
        v.addLore(C.ITALIC + "if you lose this item, you lose all of the items");
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public void onTick() {
    }



    @EventHandler
    public void on(PlayerInteractEvent e) {
        if(e.getAction().equals(Action.LEFT_CLICK_AIR) && e.getPlayer().isSneaking() && omniTool.isMultiItem(e.getItem())) {
            J.s(() -> e.getPlayer().getInventory().setItemInMainHand(omniTool.nextTool(e.getItem())));
            e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
        }
    }

    @EventHandler
    public void on(InventoryClickEvent e) {
        e.getWhoClicked().sendMessage(e.getClick().toString() +" -> "+ e.getAction().toString());
        if(e.getClick().equals(ClickType.SHIFT_LEFT) && e.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY))
        {
            ItemStack cursor = e.getWhoClicked().getItemOnCursor().clone();
            ItemStack clicked = e.getClickedInventory().getItem(e.getSlot()).clone();

            if(cursor != null && clicked != null && !cursor.getType().isAir() && !clicked.getType().isAir() && omniTool.supportsItem(cursor) && omniTool.supportsItem(clicked)) {
                e.getWhoClicked().sendMessage("Combined " + cursor.getType() + " with " + clicked.getType());
                e.setCancelled(true);
                e.getWhoClicked().setItemOnCursor(new ItemStack(Material.AIR));
                e.getClickedInventory().setItem(e.getSlot(), omniTool.build(cursor, clicked));
                e.getWhoClicked().getWorld().playSound(e.getWhoClicked().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
            }
        }

    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 15;
        int initialCost = 5;
        double costFactor = 1;
        int maxLevel = 1;
    }
}
