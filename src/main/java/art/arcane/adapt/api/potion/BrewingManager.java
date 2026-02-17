package art.arcane.adapt.api.potion;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.Reflect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BrewingManager implements Listener {

    private static final Map<BrewingRecipe, Set<String>> recipes = new ConcurrentHashMap<>();
    private static final Map<Location, BrewingTask> activeTasks = new ConcurrentHashMap<>();

    public static void registerRecipe(String adaptation, BrewingRecipe recipe) {
        if (adaptation == null || adaptation.isBlank() || recipe == null) {
            return;
        }

        recipes.computeIfAbsent(recipe, unused -> ConcurrentHashMap.newKeySet()).add(adaptation);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getType() != InventoryType.BREWING || e.getView().getTopInventory().getHolder() == null) {
            return;
        }
        Adapt.verbose("Brewing click: " + e.getRawSlot());
        BrewerInventory inv = (BrewerInventory) e.getInventory();
        boolean doTheThing = inv.getIngredient() == null
                && e.getCursor() != null
                && e.getRawSlot() == 3
                && e.getClickedInventory() != null
                && e.getClickedInventory().getType().equals(InventoryType.BREWING)
                && (e.getClick() == ClickType.LEFT);
        if (doTheThing) {
            Adapt.verbose("Brewing Stand Ingredient Clicked");
            e.setCancelled(true);
        }
        Player clicker = (Player) e.getWhoClicked();
        J.runEntity(clicker, () -> {
            if (doTheThing) {
                inv.setIngredient(e.getCursor());
                e.setCursor(null);
            }

            BrewingStand stand = inv.getHolder();
            if (stand == null) {
                return;
            }

            Location standLocation = stand.getLocation();
            AdaptPlayer p = Adapt.instance.getAdaptServer().getPlayer(clicker);
            BrewingRecipe recipe = findMatchingRecipe(standLocation);
            if (recipe == null) {
                BrewingTask removed = activeTasks.remove(standLocation);
                if (removed != null) {
                    removed.cancel();
                }
                return;
            }

            Set<String> requiredAdaptations = recipes.get(recipe);
            BrewingTask active = activeTasks.get(standLocation);
            if (!playerHasRequiredAdaptation(p, requiredAdaptations)) {
                if (active != null && !active.getRecipe().getId().equals(recipe.getId())) {
                    BrewingTask removed = activeTasks.remove(standLocation);
                    if (removed != null) {
                        removed.cancel();
                    }
                }
                return;
            }

            if (active != null && active.getRecipe().getId().equals(recipe.getId())) {
                return;
            }

            if (active != null) {
                BrewingTask removed = activeTasks.remove(standLocation);
                if (removed != null) {
                    removed.cancel();
                }
            }

            activeTasks.put(standLocation, new BrewingTask(recipe, standLocation));
        }, 1);
    }

    @EventHandler
    public void onBrew(BrewEvent e) {
        ItemStack ingredient = e.getContents().getIngredient();
        if (ingredient == null) {
            return;
        }

        Material m = ingredient.getType();
        if (m != Material.GUNPOWDER && m != Material.DRAGON_BREATH) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            ItemStack s = e.getContents().getItem(i);
            if (s == null) continue;
            PotionMeta meta = (PotionMeta) s.getItemMeta();
            var opt = Reflect.getEnum(PotionType.class, "UNCRAFTABLE");
            if (opt.isEmpty() && meta.getBasePotionData() != null)
                continue;
            if (opt.isPresent() && meta.getBasePotionData().getType() == opt.get())
                continue;
            ItemStack newStack = s.clone();
            if (m == Material.GUNPOWDER) {
                newStack.setType(Material.SPLASH_POTION);
            } else {
                newStack.setType(Material.LINGERING_POTION);
                /*PotionMeta meta = (PotionMeta)newStack.getItemMeta();
                List<PotionEffect> newEffects = Lists.newArrayList();
                meta.getCustomEffects().forEach(effect -> newEffects.add(new PotionEffect(effect.getType(), effect.getDuration() / 4, effect.getAmplifier())));
                meta.clearCustomEffects();
                newEffects.forEach(effect -> meta.addCustomEffect(effect, true));
                newStack.setItemMeta(meta);*/
            }
            e.getResults().set(i, newStack);
        }
    }

    private BrewingRecipe findMatchingRecipe(Location standLocation) {
        if (standLocation == null) {
            return null;
        }

        for (BrewingRecipe recipe : recipes.keySet()) {
            if (BrewingTask.isValid(recipe, standLocation)) {
                return recipe;
            }
        }

        return null;
    }

    private boolean playerHasRequiredAdaptation(AdaptPlayer player, Set<String> requiredAdaptations) {
        if (player == null || requiredAdaptations == null || requiredAdaptations.isEmpty()) {
            return false;
        }

        for (String adaptation : requiredAdaptations) {
            if (player.hasAdaptation(adaptation)) {
                return true;
            }
        }

        return false;
    }
}
