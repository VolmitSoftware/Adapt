package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptServer;
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
          SkillHost.lineKey(skill), skill.getIcon().item(), ComponentHost.of(skill).getModel()).toItemStack();
      icons.put(SkillHost.lineKey(skill), skillIcon);
      icons.put(TextKeys.skill(skill.getId()), skillIcon);
      for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
        if (adaptation instanceof SimpleAdaptation<?> simple) {
          String prefix = simple.getLocalizationKey();
          icons.put(prefix, () -> GuiConfig.adaptationModel(
              adaptation.getId().toString(), adaptation.getIcon().item(), ComponentHost.of(adaptation).getModel()).toItemStack());
          icons.putIfAbsent(LanguageEditorGui.group(prefix), skillIcon);
        }
      }
    }
    return new IconCatalog(registry, revision, Map.copyOf(icons));
  }

  private record IconCatalog(SkillRegistry registry, long revision, Map<String, Supplier<ItemRef>> icons) {
  }
}
