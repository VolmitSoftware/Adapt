package art.arcane.adapt.util.decree.handlers;

import art.arcane.volmlib.util.decree.handlers.base.WorldHandlerBase;
import art.arcane.adapt.util.decree.DecreeParameterHandler;
import org.bukkit.World;

public class WorldHandler extends WorldHandlerBase implements DecreeParameterHandler<World> {
    @Override
    protected String excludedPrefix() {
        return "adapt/";
    }
}
