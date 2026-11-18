package art.arcane.adapt.util.director.handlers;

import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

public class SoundHandler implements DirectorParameterHandler<Id> {
  @Override
  public KList<Id> getPossibilities() {
    return RegistryNames.ids(RegistryKind.SOUND);
  }

  @Override
  public String toString(Id sound) {
    return RegistryNames.name(sound);
  }

  @Override
  public Id parse(String in, boolean force) throws DirectorParsingException {
    Id id = RegistryNames.parse(RegistryKind.SOUND, in);
    if (id == null) {
      throw new DirectorParsingException("Invalid sound: " + in);
    }
    return id;
  }

  @Override
  public boolean supports(Class<?> type) {
    return type.equals(Id.class);
  }
}
