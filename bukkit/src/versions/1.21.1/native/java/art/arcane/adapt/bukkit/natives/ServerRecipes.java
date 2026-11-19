package art.arcane.adapt.bukkit.natives;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

/**
 * Crafting recipes with {@link IdentityChoice} ingredients. The server matches them by the choices' tests (by hand, in a
 * Crafter, by shift-click) and the recipe book places every stack a choice accepts; the client still receives a plain
 * shaped or shapeless recipe showing each choice's representative.
 */
public final class ServerRecipes {
  private ServerRecipes() {
  }

  /**
   * The recipe to hand to {@code Server.addRecipe}: {@code recipe} itself unless it is a shaped or shapeless recipe with
   * an {@link IdentityChoice}, in which case a server recipe that registers identity matching.
   */
  public static Recipe withIdentity(Recipe recipe) {
    Recipe registered;
    if (recipe instanceof ShapedRecipe shaped && hasIdentity(shaped.getChoiceMap().values())) {
      registered = IdentityRecipes.shaped(shaped);
    } else if (recipe instanceof ShapelessRecipe shapeless && hasIdentity(shapeless.getChoiceList())) {
      registered = IdentityRecipes.shapeless(shapeless);
    } else {
      return recipe;
    }
    listenToRecipeBook();
    return registered;
  }

  private static boolean hasIdentity(Collection<RecipeChoice> choices) {
    for (RecipeChoice choice : choices) {
      if (choice instanceof IdentityChoice) {
        return true;
      }
    }
    return false;
  }

  private static void listenToRecipeBook() {
    for (RegisteredListener listener : PlayerRecipeBookClickEvent.getHandlerList().getRegisteredListeners()) {
      if (listener.getListener() instanceof RecipeBookClicks) {
        return;
      }
    }
    Plugin plugin = JavaPlugin.getProvidingPlugin(ServerRecipes.class);
    plugin.getServer().getPluginManager().registerEvents(new RecipeBookClicks(), plugin);
  }

  /** Recipe book clicks on identity recipes, placed here instead of by the server's exact-component placement. */
  static final class RecipeBookClicks implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRecipeBookClick(PlayerRecipeBookClickEvent event) {
      if (IdentityRecipes.place(event.getPlayer(), event.getRecipe(), event.isMakeAll())) {
        event.setCancelled(true);
      }
    }
  }
}
