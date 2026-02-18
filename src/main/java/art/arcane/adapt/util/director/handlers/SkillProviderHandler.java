package art.arcane.adapt.util.director.handlers;

import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

public class SkillProviderHandler implements DirectorParameterHandler<AdaptationListingHandler.SkillProvider> {
  @Override
  public KList<AdaptationListingHandler.SkillProvider> getPossibilities() {
    return AdaptationListingHandler.getSkillProvider();
  }

  @Override
  public String toString(AdaptationListingHandler.SkillProvider skillProvider) {
    return skillProvider.name();
  }

  @Override
  public AdaptationListingHandler.SkillProvider parse(String in, boolean force) throws DirectorParsingException {
    return new AdaptationListingHandler.SkillProvider(in);
  }

  @Override
  public boolean supports(Class<?> type) {
    return type.equals(AdaptationListingHandler.SkillProvider.class);
  }
}
