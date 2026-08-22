package art.arcane.adapt.util.director.handlers;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.Sound;

public class SoundHandler implements DirectorParameterHandler<Sound> {
  @Override
  public KList<Sound> getPossibilities() {
    return new KList<>(Sound.values());
  }

  @Override
  public String toString(Sound sound) {
    return sound.name();
  }

  @Override
  public Sound parse(String in, boolean force) throws DirectorParsingException {
    try {
      return Sound.valueOf(in);
    } catch (IllegalArgumentException e) {
      throw new DirectorParsingException("Invalid sound: " + in);
    }
  }

  @Override
  public boolean supports(Class<?> type) {
    return type.equals(Sound.class);
  }
}
