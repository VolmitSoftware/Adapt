package com.volmit.adapt.content.adaptation.excavation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.content.item.OmniTool;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ExcavationOmniTool extends SimpleAdaptation<ExcavationOmniTool.Config> {
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
                .ingredient(Material.IRON_SWORD)
                .ingredient(Material.HOPPER)
                .ingredient(Material.ENDER_PEARL)
                .result(OmniTool.io.withData(new OmniTool.Data(new ArrayList<>())))
                .build());
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        if (!hasAdaptation(e.getPlayer())) {
            return;
        }
        Player p = e.getPlayer();
        if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK) || e.getAction().equals(Action.RIGHT_CLICK_AIR)) {
            if (!p.isSneaking()) {
                return;
            }
            String lore0 = getLore(p);

            if (lore0.contains("OMNITOOL-305")) {
                openOmnitoolInventory(p, p.getInventory().getItemInMainHand());
            }
        }
    }

    @EventHandler
    public void on(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player) {
            Player p = (Player) e.getPlayer();
            Inventory inv = e.getInventory();
            String lore0 = getLore(p);
            if (hasAdaptation(p) && lore0.contains("OMNITOOL-305")) {
                ItemStack omnitool = OmniTool.io.withData(new OmniTool.Data(List.of(inv.getContents())));
                p.getInventory().setItemInMainHand(omnitool);
            }
        }
    }

    public void openOmnitoolInventory(Player p, ItemStack omniTool) {
        Inventory inventory = Bukkit.createInventory(p, 9, "An Astral Utility Container");
        OmniTool.getItems(omniTool).forEach(inventory::addItem);
        p.openInventory(inventory);
    }


    public String getLore(Player p) {
        ItemStack mainHandItem = p.getInventory().getItemInMainHand();
        ItemMeta mainHandMeta = mainHandItem.getItemMeta();
        if (mainHandMeta == null) {
            return null;
        }
        if (mainHandItem.getItemMeta().getLore() == null) {
            return null;
        }
        return mainHandItem.getItemMeta().getLore().get(0);
    }


    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + "This is a morphing tool that holds all the tools, switching when needed");
        v.addLore(C.GREEN + "" + (level + 3) + C.GRAY + "x Levels of haste when you start mining ANY block with the right tool");
        v.addLore(C.ITALIC + "if you lose this item, you lose all of the items");
    }

    @Override
    public void onTick() {
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
