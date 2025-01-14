package com.volmit.adapt.util.decree.context;

import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.skill.SkillRegistry;
import com.volmit.adapt.util.collection.KList;

public class AdaptationListingHandler {

    private static KList<AdaptationList> adaptationLists;
    private static KList<AdaptationSkillList> adaptationSkillLists;
    private static KList<AdaptationProvider> adaptationProviders;
    private static KList<SkillProvider> skillProviders;


    public static void initializeAdaptationListings() {
        adaptationLists = new KList<>();
        adaptationSkillLists = new KList<>();
        adaptationProviders = new KList<>();
        skillProviders = new KList<>();

        getAdaptionListings();
        getAdaptionSkillListings();
        getAdaptationProviders();
        getSkillProvider();
    }

    public static KList<AdaptationList> getAdaptionListings() {
        if (adaptationLists.isNotEmpty()) return adaptationLists;

        AdaptationList main = new AdaptationList("[Main]");
        adaptationLists.add(main);

        for (Skill<?> skill : SkillRegistry.skills.sortV()) {
            AdaptationList skillList = new AdaptationList("[Skill]-" + skill.getName());
            adaptationLists.add(skillList);

            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                AdaptationList adaptationList = new AdaptationList("[Adaptation]-" + adaptation.getName());
                adaptationLists.add(adaptationList);
            }
        }
        return adaptationLists;
    }

    public static KList<AdaptationSkillList> getAdaptionSkillListings() {
        if (adaptationSkillLists.isNotEmpty()) return adaptationSkillLists;

        AdaptationSkillList t1 = new AdaptationSkillList("[all]");
        adaptationSkillLists.add(t1);
        AdaptationSkillList t2 = new AdaptationSkillList("[random]");
        adaptationSkillLists.add(t2);
        for (Skill<?> skill : SkillRegistry.skills.sortV()) {
            AdaptationSkillList t3 = new AdaptationSkillList(skill.getName());
            adaptationSkillLists.add(t3);
        }
        return adaptationSkillLists;
    }

    public static KList<AdaptationProvider> getAdaptationProviders() {
        if (adaptationProviders.isNotEmpty()) return adaptationProviders;

        for (Skill<?> skill : SkillRegistry.skills.sortV()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                AdaptationProvider suggestion = new AdaptationProvider(skill.getName() + ":" +adaptation.getName());
                adaptationProviders.add(suggestion);
            }
        }
        return adaptationProviders;
    }

    public static KList<SkillProvider> getSkillProvider() {
        if (skillProviders.isNotEmpty()) return skillProviders;

        for (Skill<?> skill : SkillRegistry.skills.sortV()) {
            SkillProvider t1 = new SkillProvider(skill.getName());
            skillProviders.add(t1);
        }
        return skillProviders;
    }

    public record AdaptationList(String name) {
        public boolean startsWith(String prefix) {
            return name.startsWith(prefix);
        }

        public boolean equals(String prefix) {
            return name.equalsIgnoreCase(prefix);
        }
    }

    public record AdaptationSkillList(String name) {
        public boolean startsWith(String prefix) {
            return name.startsWith(prefix);
        }

        public boolean equals(String prefix) {
            return name.equalsIgnoreCase(prefix);
        }
    }

    public record AdaptationProvider(String name) {
        public boolean startsWith(String prefix) {
            return name.startsWith(prefix);
        }

        public boolean equals(String prefix) {
            return name.equalsIgnoreCase(prefix);
        }
    }

    public record SkillProvider(String name) {
        public boolean startsWith(String prefix) {
            return name.startsWith(prefix);
        }

        public boolean equals(String prefix) {
            return name.equalsIgnoreCase(prefix);
        }
    }
}
