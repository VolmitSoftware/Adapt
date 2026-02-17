package art.arcane.adapt.util.decree.specialhandlers;

import art.arcane.adapt.util.decree.DecreeParameterHandler;
import art.arcane.adapt.util.decree.handlers.PlayerHandler;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;
import org.bukkit.entity.Player;

public class NullablePlayerHandler extends PlayerHandler implements DecreeParameterHandler<Player> {
    @Override
    public Player parse(String in, boolean force) throws DecreeParsingException {
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
