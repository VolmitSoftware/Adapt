package art.arcane.adapt.util.decree.handlers;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.adapt.util.decree.DecreeParameterHandler;
import art.arcane.adapt.util.decree.context.AdaptationListingHandler;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;

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
