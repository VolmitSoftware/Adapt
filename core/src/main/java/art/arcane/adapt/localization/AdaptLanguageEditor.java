package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Icons of the language editor: each message shows the model of the skill or adaptation that owns its key. */
public final class AdaptLanguageEditor {
  private final Supplier<SkillRegistry> registry;
  private volatile IconCatalog catalog;

  AdaptLanguageEditor(Supplier<SkillRegistry> registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  public static AdaptLanguageEditor running() {
    return new AdaptLanguageEditor(AdaptLanguageEditor::registry);
  }

  public Optional<ItemRef> icon(String messageId) {
    SkillRegistry current = registry.get();
    if (current == null) {
      return Optional.empty();
    }
    long revision = current.getCatalogRevision();
    IconCatalog active = catalog;
    if (active == null || active.registry() != current || active.revision() != revision) {
      active = index(current, revision);
      catalog = active;
    }
    String prefix = messageId;
    while (true) {
      Supplier<ItemRef> icon = active.icons().get(prefix);
      if (icon != null) {
        return Optional.of(icon.get());
      }
      int separator = prefix.lastIndexOf('.');
      if (separator < 0) {
        return Optional.empty();
      }
      prefix = prefix.substring(0, separator);
    }
  }

  private static SkillRegistry registry() {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    return server == null ? null : server.getSkillRegistry();
  }

  private IconCatalog index(SkillRegistry registry, long revision) {
    Map<String, Supplier<ItemRef>> icons = new HashMap<>();
    for (Skill<?> skill : registry.getAllSkills()) {
      Supplier<ItemRef> skillIcon = () -> GuiConfig.skillModel(
          skill.getName(), skill.getIcon(), skill.getModel()).toItemStack();
      icons.put(skill.getName(), skillIcon);
      if (skill instanceof SimpleSkill<?> simple) {
        String name = simple.getPresentation().name().id();
        icons.put(name.substring(0, name.lastIndexOf('.')), skillIcon);
      }
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (adaptation instanceof SimpleAdaptation<?> simple) {
          String prefix = simple.getLocalizationKey();
          icons.put(prefix, () -> GuiConfig.adaptationModel(
              adaptation.getName(), adaptation.getIcon(), adaptation.getModel()).toItemStack());
          int separator = prefix.indexOf('.');
          if (separator > 0) {
            icons.putIfAbsent(prefix.substring(0, separator), skillIcon);
          }
        }
      }
    }
    return new IconCatalog(registry, revision, Map.copyOf(icons));
  }

  private record IconCatalog(SkillRegistry registry, long revision, Map<String, Supplier<ItemRef>> icons) {
  }
}
