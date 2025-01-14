package com.volmit.adapt.util.decree.handlers;

import com.volmit.adapt.util.collection.KList;
import com.volmit.adapt.util.decree.DecreeParameterHandler;
import com.volmit.adapt.util.decree.context.AdaptationListingHandler;
import com.volmit.adapt.util.decree.exceptions.DecreeParsingException;

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
    public AdaptationListingHandler.AdaptationList parse(String in, boolean force) {
        return new AdaptationListingHandler.AdaptationList(in);
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(AdaptationListingHandler.AdaptationList.class);
    }
}
