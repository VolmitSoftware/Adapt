package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.util.command.FConst;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import org.bukkit.entity.Player;

@Director(name = "clear", origin = DirectorOrigin.BOTH, description = "Clear player progression data")
public class CommandClear {

    @Director(description = "Clear all player data (XP, knowledge, adaptations, stats, discoveries, advancements, wisdom)")
    public void all(
        @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player
    ) {
        Player targetPlayer = resolveTarget(player);
        if (targetPlayer == null) return;

        PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
        data.clearAll();
        FConst.success("Cleared all data for " + targetPlayer.getName()).send(BukkitDirectorContext.sender());
    }

    @Director(description = "Clear XP across all skill lines")
    public void xp(
        @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player
    ) {
        Player targetPlayer = resolveTarget(player);
        if (targetPlayer == null) return;

        PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
        data.clearXp();
        FConst.success("Cleared XP for " + targetPlayer.getName()).send(BukkitDirectorContext.sender());
    }

    @Director(description = "Clear knowledge across all skill lines")
    public void knowledge(
        @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player
    ) {
        Player targetPlayer = resolveTarget(player);
        if (targetPlayer == null) return;

        PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
        data.clearKnowledge();
        FConst.success("Cleared knowledge for " + targetPlayer.getName()).send(BukkitDirectorContext.sender());
    }

    @Director(description = "Unlearn all adaptations across all skill lines")
    public void adaptations(
        @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player
    ) {
        Player targetPlayer = resolveTarget(player);
        if (targetPlayer == null) return;

        PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
        data.clearAdaptations();
        FConst.success("Cleared adaptations for " + targetPlayer.getName()).send(BukkitDirectorContext.sender());
    }

    @Director(description = "Clear the stats map")
    public void stats(
        @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player
    ) {
        Player targetPlayer = resolveTarget(player);
        if (targetPlayer == null) return;

        PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
        data.clearStats();
        FConst.success("Cleared stats for " + targetPlayer.getName()).send(BukkitDirectorContext.sender());
    }

    @Director(description = "Clear all discovery data (biomes, mobs, foods, items, recipes, etc.)")
    public void discoveries(
        @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player
    ) {
        Player targetPlayer = resolveTarget(player);
        if (targetPlayer == null) return;

        PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
        data.clearDiscoveries();
        FConst.success("Cleared discoveries for " + targetPlayer.getName()).send(BukkitDirectorContext.sender());
    }

    private Player resolveTarget(Player player) {
        if (!BukkitDirectorContext.hasPermission("adapt.clear")) {
            FConst.error("You lack the Permission 'adapt.clear'").send(BukkitDirectorContext.sender());
            return null;
        }

        if (player != null) {
            return player;
        }

        if (BukkitDirectorContext.isConsole()) {
            FConst.error("You must specify a player when using this command from console.").send(BukkitDirectorContext.sender());
            return null;
        }

        return BukkitDirectorContext.player();
    }
}
