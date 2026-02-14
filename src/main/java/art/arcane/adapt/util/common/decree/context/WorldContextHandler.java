package art.arcane.adapt.util.decree.context;

import art.arcane.volmlib.util.decree.context.WorldContextHandlerBase;
import art.arcane.adapt.util.common.plugin.VolmitSender;
import art.arcane.adapt.util.decree.DecreeContextHandler;
import org.bukkit.World;

public class WorldContextHandler extends WorldContextHandlerBase<VolmitSender> implements DecreeContextHandler<World> {
    @Override
    protected boolean isPlayer(VolmitSender sender) {
        return sender.isPlayer();
    }

    @Override
    protected World getWorld(VolmitSender sender) {
        return sender.player().getWorld();
    }
}
