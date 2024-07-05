package com.volmit.adapt.util.decree.context;

import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.skill.SkillRegistry;

import java.util.ArrayList;
import java.util.List;

public class AdaptationListingHandler {

    private static List<AdaptationList> adaptationLists;
    private static List<AdaptationSkillList> adaptationSkillLists;


    public static void initializeAdaptationListings() {
        getAdaptionListings();
        getAdaptionSkillListings();
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
}
