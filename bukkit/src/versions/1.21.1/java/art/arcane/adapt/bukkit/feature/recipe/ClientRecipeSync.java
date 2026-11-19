package art.arcane.adapt.bukkit.feature.recipe;

import art.arcane.adapt.bukkit.natives.ServerRecipes;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.Recipe;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Recipe changes on servers before 1.21.2, whose clients hold every recipe definition from the join (the recipe book
 * only names recipes by id). A recipe added or removed while players are online reaches them with one resend of all
 * recipes on the next tick, however many changed in between.
 */
final class ClientRecipeSync {
  private static final AtomicBoolean RESEND_PENDING = new AtomicBoolean();

  private ClientRecipeSync() {
  }

  static boolean add(Server server, Recipe recipe) {
    boolean added = server.addRecipe(ServerRecipes.withIdentity(recipe));
    if (added) {
      resend(server);
    }
    return added;
  }

  static void remove(Server server, NamespacedKey key) {
    if (server.removeRecipe(key)) {
      resend(server);
    }
  }

  private static void resend(Server server) {
    if (!server.getOnlinePlayers().isEmpty() && RESEND_PENDING.compareAndSet(false, true)) {
      J.s(() -> {
        RESEND_PENDING.set(false);
        server.updateRecipes();
      });
    }
  }
}
