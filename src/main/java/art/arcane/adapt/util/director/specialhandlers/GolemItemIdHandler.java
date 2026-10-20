package art.arcane.adapt.util.director.specialhandlers;

import art.arcane.adapt.command.GolemGiveCatalog;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

public class GolemItemIdHandler implements DirectorParameterHandler<String> {
  @Override
  public KList<String> getPossibilities() {
    return new KList<>(GolemGiveCatalog.completions());
  }

  @Override
  public String toString(String id) {
    return id;
  }

  @Override
  public String parse(String in, boolean force) throws DirectorParsingException {
    return in;
  }

  @Override
  public boolean supports(Class<?> type) {
    return type.equals(String.class);
  }

  @Override
  public String getRandomDefault() {
    return "crook";
  }
}
