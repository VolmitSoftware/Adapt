package com.volmit.adapt.util.decree.handlers;

import com.volmit.adapt.util.collection.KList;
import com.volmit.adapt.util.decree.DecreeParameterHandler;
import com.volmit.adapt.util.decree.context.AdaptationListingHandler;
import com.volmit.adapt.util.decree.exceptions.DecreeParsingException;

public class AdaptationSkillListHandler implements DecreeParameterHandler<AdaptationListingHandler.AdaptationSkillList> {
    @Override
    public KList<AdaptationListingHandler.AdaptationSkillList> getPossibilities() {
        return AdaptationListingHandler.getAdaptionSkillListings();
    }

    @Override
    public String toString(AdaptationListingHandler.AdaptationSkillList adaptationSkillList) {
        return adaptationSkillList.name();
    }

    @Override
    public AdaptationListingHandler.AdaptationSkillList parse(String in, boolean force) throws DecreeParsingException {
        return new AdaptationListingHandler.AdaptationSkillList(in);
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(AdaptationListingHandler.AdaptationSkillList.class);
    }
}
