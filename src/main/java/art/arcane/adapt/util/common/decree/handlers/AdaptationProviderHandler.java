package art.arcane.adapt.util.decree.handlers;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.adapt.util.decree.DecreeParameterHandler;
import art.arcane.adapt.util.decree.context.AdaptationListingHandler;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;

public class AdaptationProviderHandler implements DecreeParameterHandler<AdaptationListingHandler.AdaptationProvider> {

    @Override
    public KList<AdaptationListingHandler.AdaptationProvider> getPossibilities() {
        return AdaptationListingHandler.getAdaptationProviders();
    }

    @Override
    public String toString(AdaptationListingHandler.AdaptationProvider adaptationProvider) {
        return adaptationProvider.name();
    }

    @Override
    public AdaptationListingHandler.AdaptationProvider parse(String in, boolean force) throws DecreeParsingException {
        return new AdaptationListingHandler.AdaptationProvider(in);
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(AdaptationListingHandler.AdaptationProvider.class);
    }
}
