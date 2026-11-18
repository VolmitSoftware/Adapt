package art.arcane.adapt.bukkit.compat;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Recipe;

public final class WorldBridge {
  private WorldBridge() {
  }

  @ApiBridge(AdaptServer.class)
  public static AdaptPlayer getPlayer(AdaptServer server, Player player) {
    return server.getPlayer(BukkitRefs.player(player));
  }

  @ApiBridge(AdaptServer.class)
  public static void takeSpatial(AdaptServer server, AdaptPlayer player, Location location) {
    server.takeSpatial(player, BukkitRefs.loc(location));
  }

  @ApiBridge(AdaptServer.class)
  public static void openSkillGUI(AdaptServer server, Skill<?> skill, Player player) {
    server.openSkillGUI(skill, BukkitRefs.player(player));
  }

  @ApiBridge(AdaptServer.class)
  public static void openAdaptGui(AdaptServer server, Player player) {
    server.openAdaptGui(BukkitRefs.player(player));
  }

  @ApiBridge(AdaptServer.class)
  public static void openAdaptationGUI(AdaptServer server, Adaptation<?> adaptation, Player player) {
    server.openAdaptationGUI(adaptation, BukkitRefs.player(player));
  }

  @ApiBridge(value = AdaptPlayer.class, name = "getPlayer")
  public static Player player(AdaptPlayer player) {
    return BukkitRefs.player(player.getPlayer());
  }

  @ApiBridge(PlayerData.class)
  public static double computeXpMultiplier(PlayerData data, Player player) {
    return data.computeXpMultiplier(BukkitRefs.player(player));
  }

  @ApiBridge(SkillRegistry.class)
  public static Adaptation<?> getRequiredAdaptation(SkillRegistry registry, Recipe recipe) {
    return registry.getRequiredAdaptation(recipeKey(recipe));
  }

  @ApiBridge(SkillRegistry.class)
  public static int getRequiredRecipeLevel(SkillRegistry registry, Recipe recipe) {
    return registry.getRequiredRecipeLevel(recipeKey(recipe));
  }

  private static Id recipeKey(Recipe recipe) {
    return recipe instanceof Keyed keyed ? BukkitRefs.id(keyed.getKey()) : null;
  }
}
