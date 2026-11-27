package art.arcane.adapt.engine.addon;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationRequirement;
import art.arcane.adapt.api.content.ContentCatalog;
import art.arcane.adapt.api.content.ContentVeto;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The running core's content as the API sees it: built-in content and every addon's alike. */
final class EngineCatalog implements ContentCatalog {
  private final AddonLifecycle addons;

  EngineCatalog(AddonLifecycle addons) {
    this.addons = addons;
  }

  @Override
  public long revision() {
    SkillRegistry registry = addons.registry();
    return registry == null ? 0L : registry.getCatalogRevision();
  }

  @Override
  public Optional<Skill<?>> skill(Id id) {
    SkillRegistry registry = addons.registry();
    return registry == null ? Optional.empty() : Optional.ofNullable(registry.getAnySkill(id));
  }

  @Override
  public List<Skill<?>> skills() {
    SkillRegistry registry = addons.registry();
    List<Skill<?>> found = new ArrayList<>();
    if (registry != null) {
      found.addAll(registry.getAllSkills());
    }
    return List.copyOf(found);
  }

  @Override
  public Optional<Adaptation<?>> adaptation(Id id) {
    for (Adaptation<?> adaptation : adaptations()) {
      if (adaptation.getId().equals(id)) {
        return Optional.of(adaptation);
      }
    }
    SkillRegistry registry = addons.registry();
    return registry == null ? Optional.empty() : Optional.ofNullable(registry.getAdaptation(id));
  }

  @Override
  public List<Adaptation<?>> adaptations() {
    SkillRegistry registry = addons.registry();
    List<Adaptation<?>> found = new ArrayList<>();
    if (registry != null) {
      for (Skill<?> skill : registry.getAllSkills()) {
        found.addAll(skill.getAdaptations());
      }
    }
    return List.copyOf(found);
  }

  @Override
  public List<CustomItem.Definition<?>> items() {
    Map<Id, CustomItem.Definition<?>> items = new LinkedHashMap<>();
    for (CustomItem.Definition<?> item : addons.registeredItems()) {
      items.putIfAbsent(item.id(), item);
    }
    SkillRegistry registry = addons.registry();
    List<Adaptation<?>> owned = new ArrayList<>();
    if (registry != null) {
      for (Skill<?> skill : registry.getAllSkills()) {
        owned.addAll(SkillHost.ownedAdaptations(skill));
      }
    }
    for (Adaptation<?> adaptation : owned) {
      for (CustomItem.Definition<?> item : adaptation.getItems()) {
        items.putIfAbsent(item.id(), item);
      }
    }
    return List.copyOf(items.values());
  }

  @Override
  public Optional<CustomItem.Definition<?>> item(Id id) {
    for (CustomItem.Definition<?> item : items()) {
      if (item.id().equals(id)) {
        return Optional.of(item);
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<String> owner(Id id) {
    SkillRegistry registry = addons.registry();
    return registry == null ? Optional.empty() : registry.ownerOf(id);
  }

  @Override
  public Optional<Adaptation<?>> mutated(MutationSlot slot) {
    SkillRegistry registry = addons.registry();
    MutatedSkill skill = registry == null ? null : registry.getMutatedSkill(slot.pair());
    MutationAdaptation held = skill == null ? null : skill.getMutationAdaptation(slot.recipe());
    if (held == null) {
      return Optional.empty();
    }
    return Optional.of(held.getSignature() == null ? held : held.getSignature());
  }

  @Override
  public Optional<AdaptationRequirement> recipeUnlock(Id recipe) {
    SkillRegistry registry = addons.registry();
    Adaptation<?> adaptation = registry == null ? null : registry.getRequiredAdaptation(recipe);
    return adaptation == null ? Optional.empty()
        : Optional.of(new AdaptationRequirement(adaptation.getId(), registry.getRequiredRecipeLevel(recipe)));
  }

  @Override
  public List<Signature<?>> signatures() {
    SkillRegistry registry = addons.registry();
    if (registry == null) {
      return List.of();
    }
    List<Signature<?>> signatures = new ArrayList<>();
    for (MutatedSkill mutated : registry.getBuiltMutatedSkills()) {
      signatures.addAll(mutated.getSignatures());
    }
    return List.copyOf(signatures);
  }

  @Override
  public List<ContentVeto> vetoes() {
    return addons.vetoes();
  }

}
