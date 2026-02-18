package art.arcane.adapt.util.director.handlers;

import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

public class AdaptationSkillListHandler implements DirectorParameterHandler<AdaptationListingHandler.AdaptationSkillList> {
  @Override
  public KList<AdaptationListingHandler.AdaptationSkillList> getPossibilities() {
    return AdaptationListingHandler.getAdaptionSkillListings();
  }

  @Override
  public String toString(AdaptationListingHandler.AdaptationSkillList adaptationSkillList) {
    return adaptationSkillList.name();
  }

  @Override
  public AdaptationListingHandler.AdaptationSkillList parse(String in, boolean force) throws DirectorParsingException {
    return new AdaptationListingHandler.AdaptationSkillList(in);
  }

  @Override
  public boolean supports(Class<?> type) {
    return type.equals(AdaptationListingHandler.AdaptationSkillList.class);
  }
}
