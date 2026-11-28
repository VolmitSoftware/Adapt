package art.arcane.adapt.util.director.context;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.volmlib.util.collection.KList;

import java.util.List;

public class AdaptationListingHandler {

  private static KList<AdaptationList> adaptationLists;
  private static KList<AdaptationSkillList> adaptationSkillLists;
  private static KList<AdaptationProvider> adaptationProviders;
  private static KList<SkillProvider> skillProviders;
  private static long builtRevision = Long.MIN_VALUE;


  public static void initializeAdaptationListings() {
    builtRevision = catalogRevision();
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
    refreshIfCatalogChanged();
    if (adaptationLists.isNotEmpty()) return adaptationLists;

    AdaptationList main = new AdaptationList("main");
    adaptationLists.add(main);

    for (Skill<?> skill : registry().getSkills()) {
      if (!skill.isEnabled()) {
        continue;
      }
      AdaptationList skillList = new AdaptationList("skill:" + SkillHost.lineKey(skill));
      adaptationLists.add(skillList);

      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (!adaptation.isEnabled()) {
          continue;
        }
        AdaptationList adaptationList = new AdaptationList("adaptation:" + adaptation.getId().toString());
        adaptationLists.add(adaptationList);
      }
    }
    return adaptationLists;
  }

  public static KList<AdaptationSkillList> getAdaptionSkillListings() {
    refreshIfCatalogChanged();
    if (adaptationSkillLists.isNotEmpty()) return adaptationSkillLists;

    AdaptationSkillList t1 = new AdaptationSkillList("all");
    adaptationSkillLists.add(t1);
    AdaptationSkillList t2 = new AdaptationSkillList("random");
    adaptationSkillLists.add(t2);

    for (Skill<?> skill : allSkills()) {
      if (!skill.isEnabled()) {
        continue;
      }
      AdaptationSkillList t3 = new AdaptationSkillList(SkillHost.lineKey(skill));
      adaptationSkillLists.add(t3);
    }

    adaptationSkillLists.removeDuplicates();
    return adaptationSkillLists;
  }

  /** Rebuilds every list when content was registered or removed since they were built. */
  private static void refreshIfCatalogChanged() {
    if (adaptationLists == null || builtRevision != catalogRevision()) {
      initializeAdaptationListings();
    }
  }

  private static long catalogRevision() {
    AdaptCore core = AdaptCore.current();
    return core == null || core.getAdaptServer() == null ? Long.MIN_VALUE : registry().getCatalogRevision();
  }

  private static KList<Skill<?>> allSkills() {
    return new KList<>(registry().getAllSkills());
  }

  private static SkillRegistry registry() {
    return AdaptCore.get().getAdaptServer().getSkillRegistry();
  }

  /**
   * Every enabled adaptation, upgrade and block of the enabled skills, then the own adaptations and signatures of the
   * mutated skills built so far, which change without a catalog revision and so are read each time.
   */
  public static KList<AdaptationProvider> getAdaptationProviders() {
    refreshIfCatalogChanged();
    if (adaptationProviders.isEmpty()) {
      for (Skill<?> skill : registry().getSkills()) {
        if (skill.isEnabled()) {
          addEnabled(SkillHost.ownedAdaptations(skill), adaptationProviders);
        }
      }
    }
    KList<AdaptationProvider> providers = new KList<>(adaptationProviders);
    for (MutatedSkill mutated : registry().getBuiltMutatedSkills()) {
      addEnabled(mutated.builtParts(), providers);
    }
    return providers;
  }

  private static void addEnabled(List<Adaptation<?>> adaptations, KList<AdaptationProvider> providers) {
    for (Adaptation<?> adaptation : adaptations) {
      if (adaptation.isEnabled()) {
        providers.add(new AdaptationProvider(adaptation.getId().toString()));
      }
    }
  }

  public static KList<SkillProvider> getSkillProvider() {
    refreshIfCatalogChanged();
    if (skillProviders.isNotEmpty()) return skillProviders;

    for (Skill<?> skill : registry().getSkills()) {
      if (!skill.isEnabled()) {
        continue;
      }
      SkillProvider t1 = new SkillProvider(SkillHost.lineKey(skill));
      skillProviders.add(t1);
    }
    return skillProviders;
  }

  public record AdaptationList(String name) {
    public boolean startsWith(String prefix) {
      return name.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public boolean equals(String prefix) {
      return name.equalsIgnoreCase(prefix);
    }
  }

  public record AdaptationSkillList(String name) {
    public boolean startsWith(String prefix) {
      return name.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public boolean equals(String prefix) {
      return name.equalsIgnoreCase(prefix);
    }
  }

  public record AdaptationProvider(String name) {
    public boolean startsWith(String prefix) {
      return name.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public boolean equals(String prefix) {
      return name.equalsIgnoreCase(prefix);
    }
  }

  public record SkillProvider(String name) {
    public boolean startsWith(String prefix) {
      return name.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    public boolean equals(String prefix) {
      return name.equalsIgnoreCase(prefix);
    }
  }
}
