package art.arcane.adapt.util.director.specialhandlers;

import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.adapt.util.director.handlers.PlayerHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.entity.Player;

public class NullablePlayerHandler extends PlayerHandler implements DirectorParameterHandler<Player> {
    @Override
    public Player parse(String in, boolean force) throws DirectorParsingException {
        if (in == null) {
            return null;
        }

        String value = in.trim();
        if (value.isEmpty() || value.equals("---") || value.equalsIgnoreCase("null")) {
            return null;
        }

        return super.parse(value, force);
    }
}
