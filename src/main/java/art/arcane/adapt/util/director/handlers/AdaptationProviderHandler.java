package art.arcane.adapt.util.director.handlers;

import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

public class AdaptationProviderHandler implements DirectorParameterHandler<AdaptationListingHandler.AdaptationProvider> {

    @Override
    public KList<AdaptationListingHandler.AdaptationProvider> getPossibilities() {
        return AdaptationListingHandler.getAdaptationProviders();
    }

    @Override
    public String toString(AdaptationListingHandler.AdaptationProvider adaptationProvider) {
        return adaptationProvider.name();
    }

    @Override
    public AdaptationListingHandler.AdaptationProvider parse(String in, boolean force) throws DirectorParsingException {
        return new AdaptationListingHandler.AdaptationProvider(in);
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(AdaptationListingHandler.AdaptationProvider.class);
    }
}
