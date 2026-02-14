package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.decree.DecreeExecutor;
import art.arcane.volmlib.util.decree.DecreeOrigin;
import art.arcane.volmlib.util.decree.annotations.Decree;
import art.arcane.volmlib.util.decree.annotations.Param;
import art.arcane.adapt.util.decree.specialhandlers.NullablePlayerHandler;
import org.bukkit.entity.Player;

import art.arcane.adapt.util.common.plugin.Permission;

@Decree(name = "clear", origin = DecreeOrigin.BOTH, description = "Clear player progression data")
public class CommandClear implements DecreeExecutor {

    @Decree(description = "Clear all player data (XP, knowledge, adaptations, stats, discoveries, advancements, wisdom)")
    public void all(
        @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player
    ) {
        Player targetPlayer = resolveTarget(player);
        if (targetPlayer == null) return;

        PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
        data.clearAll();
        FConst.success("Cleared all data for " + targetPlayer.getName()).send(sender());
    }

    @Decree(description = "Clear XP across all skill lines")
    public void xp(
        @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player
    ) {
        Player targetPlayer = resolveTarget(player);
        if (targetPlayer == null) return;

        PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
        data.clearXp();
        FConst.success("Cleared XP for " + targetPlayer.getName()).send(sender());
    }

    @Decree(description = "Clear knowledge across all skill lines")
    public void knowledge(
        @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player
    ) {
        Player targetPlayer = resolveTarget(player);
        if (targetPlayer == null) return;

        PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
        data.clearKnowledge();
        FConst.success("Cleared knowledge for " + targetPlayer.getName()).send(sender());
    }

    @Decree(description = "Unlearn all adaptations across all skill lines")
    public void adaptations(
        @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player
    ) {
        Player targetPlayer = resolveTarget(player);
        if (targetPlayer == null) return;

        PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
        data.clearAdaptations();
        FConst.success("Cleared adaptations for " + targetPlayer.getName()).send(sender());
    }

    @Decree(description = "Clear the stats map")
    public void stats(
        @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player
    ) {
        Player targetPlayer = resolveTarget(player);
        if (targetPlayer == null) return;

        PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
        data.clearStats();
        FConst.success("Cleared stats for " + targetPlayer.getName()).send(sender());
    }

    @Decree(description = "Clear all discovery data (biomes, mobs, foods, items, recipes, etc.)")
    public void discoveries(
        @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player
    ) {
        Player targetPlayer = resolveTarget(player);
        if (targetPlayer == null) return;

        PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
        data.clearDiscoveries();
        FConst.success("Cleared discoveries for " + targetPlayer.getName()).send(sender());
    }

    private Player resolveTarget(Player player) {
        if (!sender().hasPermission("adapt.clear")) {
            FConst.error("You lack the Permission 'adapt.clear'").send(sender());
            return null;
        }

        if (player != null) {
            return player;
        }

        if (sender().isConsole()) {
            FConst.error("You must specify a player when using this command from console.").send(sender());
            return null;
        }

        return player();
    }
}
