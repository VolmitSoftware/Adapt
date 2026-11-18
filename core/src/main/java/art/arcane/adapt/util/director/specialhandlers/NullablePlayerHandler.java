package art.arcane.adapt.util.director.specialhandlers;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.director.handlers.PlayerHandler;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

public class NullablePlayerHandler extends PlayerHandler implements DirectorParameterHandler<PlayerRef> {
  @Override
  public PlayerRef parse(String in, boolean force) throws DirectorParsingException {
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
