package art.arcane.adapt.util.decree.handlers;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.adapt.util.decree.DecreeParameterHandler;
import art.arcane.adapt.util.decree.context.AdaptationListingHandler;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;

public class AdaptationListHandler implements DecreeParameterHandler<AdaptationListingHandler.AdaptationList> {
    @Override
    public KList<AdaptationListingHandler.AdaptationList> getPossibilities() {
        return AdaptationListingHandler.getAdaptionListings();
    }

    @Override
    public String toString(AdaptationListingHandler.AdaptationList adaptationList) {
        return adaptationList.name();
    }

    @Override
    public AdaptationListingHandler.AdaptationList parse(String in, boolean force) throws DecreeParsingException {
        return new AdaptationListingHandler.AdaptationList(in);
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(AdaptationListingHandler.AdaptationList.class);
    }
}
