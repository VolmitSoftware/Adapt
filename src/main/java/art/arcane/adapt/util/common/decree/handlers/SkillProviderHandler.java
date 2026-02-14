package art.arcane.adapt.util.decree.handlers;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.adapt.util.decree.DecreeParameterHandler;
import art.arcane.adapt.util.decree.context.AdaptationListingHandler;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;

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
    public AdaptationListingHandler.SkillProvider parse(String in, boolean force) throws DecreeParsingException {
        return new AdaptationListingHandler.SkillProvider(in);
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(AdaptationListingHandler.SkillProvider.class);
    }
}
