package art.arcane.adapt.util.director.handlers;

import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

public class AdaptationListHandler implements DirectorParameterHandler<AdaptationListingHandler.AdaptationList> {
  @Override
  public KList<AdaptationListingHandler.AdaptationList> getPossibilities() {
    return AdaptationListingHandler.getAdaptionListings();
  }

  @Override
  public String toString(AdaptationListingHandler.AdaptationList adaptationList) {
    return adaptationList.name();
  }

  @Override
  public AdaptationListingHandler.AdaptationList parse(String in, boolean force) throws DirectorParsingException {
    return new AdaptationListingHandler.AdaptationList(in);
  }

  @Override
  public boolean supports(Class<?> type) {
    return type.equals(AdaptationListingHandler.AdaptationList.class);
  }
}
