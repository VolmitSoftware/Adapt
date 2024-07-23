package com.volmit.adapt.util.decree.context;

import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.skill.SkillRegistry;

import java.util.ArrayList;
import java.util.List;

public class AdaptationListingHandler {

    private static List<AdaptationList> adaptationLists;
    private static List<AdaptationSkillList> adaptationSkillLists;
    private static List<AdaptationProvider> adaptationProviders;
    private static List<SkillProvider> skillProviders;


    public static void initializeAdaptationListings() {
        adaptationLists = new ArrayList<>();
        adaptationSkillLists = new ArrayList<>();
        adaptationProviders = new ArrayList<>();
        skillProviders = new ArrayList<>();

        getAdaptionListings();
        getAdaptionSkillListings();
        getAdaptationProviders();
        getSkillProvider();
    }

    private static List<AdaptationList> getAdaptionListings() {
        if (adaptationLists == null || adaptationLists.isEmpty()) {
            adaptationLists = new ArrayList<>();
            AdaptationList main = new AdaptationList("[Main]");
            adaptationLists.add(main);

            for (Skill<?> skill : SkillRegistry.skills.sortV()) {
                AdaptationList skillList = new AdaptationList("[Skill] " + skill.getName());
                adaptationLists.add(skillList);

                for (Adaptation<?> adaptation : skill.getAdaptations()) {
                    AdaptationList adaptationList = new AdaptationList("[Adaptation] " + adaptation.getName());
                    adaptationLists.add(adaptationList);
                }
            }
        }
        return adaptationLists;
    }

    private static List<AdaptationSkillList> getAdaptionSkillListings() {
        AdaptationSkillList t1 = new AdaptationSkillList("[All]");
        adaptationSkillLists.add(t1);
        AdaptationSkillList t2 = new AdaptationSkillList("[random]");
        adaptationSkillLists.add(t2);
        for (Skill<?> skill : SkillRegistry.skills.sortV()) {
            AdaptationSkillList t3 = new AdaptationSkillList(skill.getName());
            adaptationSkillLists.add(t3);
        }
        return adaptationSkillLists;
    }

    private static List<AdaptationProvider> getAdaptationProviders() {
        for (Skill<?> skill : SkillRegistry.skills.sortV()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                AdaptationProvider suggestion = new AdaptationProvider(skill.getName() + ":" +adaptation.getName());
                adaptationProviders.add(suggestion);
            }
        }
        return adaptationProviders;
    }

    private static List<SkillProvider> getSkillProvider() {
        for (Skill<?> skill : SkillRegistry.skills.sortV()) {
            SkillProvider t1 = new SkillProvider(skill.getName());
            skillProviders.add(t1);
        }
        return skillProviders;
    }

    public static class AdaptationList {
        private String name;

        public AdaptationList(String name) {
            this.name = name;
        }

        public boolean startsWith(String prefix) {
            return name.startsWith(prefix);
        }

        public boolean equals(String prefix) {
            return name.startsWith(prefix);
        }
    }

    public static class AdaptationSkillList {
        private String name;

        public AdaptationSkillList(String name) {
            this.name = name;
        }

        public boolean startsWith(String prefix) {
            return name.startsWith(prefix);
        }

        public boolean equals(String prefix) {
            return name.startsWith(prefix);
        }
    }

    public static class AdaptationProvider {
        private String name;

        public AdaptationProvider(String name) {
            this.name = name;
        }

        public boolean startsWith(String prefix) {
            return name.startsWith(prefix);
        }

        public boolean equals(String prefix) {
            return name.startsWith(prefix);
        }

    }

    public static class SkillProvider {
        private String name;

        public SkillProvider(String name) {
            this.name = name;
        }

        public boolean startsWith(String prefix) {
            return name.startsWith(prefix);
        }

        public boolean equals(String prefix) {
            return name.startsWith(prefix);
        }

    }
}
