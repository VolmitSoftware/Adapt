package com.volmit.adapt.util.decree.handlers;

import com.volmit.adapt.util.collection.KList;
import com.volmit.adapt.util.decree.DecreeParameterHandler;
import com.volmit.adapt.util.decree.context.AdaptationListingHandler;

public class SkillProviderHandler implements DecreeParameterHandler<AdaptationListingHandler.SkillProvider> {
    @Override
    public KList<AdaptationListingHandler.SkillProvider> getPossibilities() {
        return AdaptationListingHandler.getSkillProvider();
    }

    @Override
    public String toString(AdaptationListingHandler.SkillProvider skillProvider) {
        return skillProvider.name();
    }

    @Override
    public AdaptationListingHandler.SkillProvider parse(String in, boolean force) {
        return new AdaptationListingHandler.SkillProvider(in);
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(AdaptationListingHandler.SkillProvider.class);
    }
}
