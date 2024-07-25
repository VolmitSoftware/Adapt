package com.volmit.adapt.util.decree.handlers;

import com.volmit.adapt.util.collection.KList;
import com.volmit.adapt.util.decree.DecreeParameterHandler;
import com.volmit.adapt.util.decree.context.AdaptationListingHandler;
import com.volmit.adapt.util.decree.exceptions.DecreeParsingException;

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
