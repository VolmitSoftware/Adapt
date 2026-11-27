package art.arcane.adapt.localization;

import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.api.text.MessageSink;
import art.arcane.adapt.localization.catalog.ConfigFieldMessages;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.volmlib.util.localization.MessageKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** An addon's config field English comes from its {@code @ConfigDoc} annotations and its translations translate it. */
class AddonConfigTextTest {
  private static final String LABEL = "cfgtext.config.fields.skills.forge.heat.label";
  private static final String DESCRIPTION = "cfgtext.config.fields.skills.forge.heat.description";

  @TempDir
  Path data;

  @Test
  void theEnglishIsTheAnnotationAndATranslationWithoutEnglishIsKept() throws Exception {
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(data).build(), new ForgeAddon(Map.of()))) {
      Field heat = ForgeSkill.Config.class.getDeclaredField("heat");
      String path = "skills.cfgtext:forge.heat";

      assertThat(ConfigFieldMessages.declaresLabel(path)).isTrue();
      assertThat(ConfigFieldMessages.label(path)).isEqualTo("Heat");
      assertThat(ConfigFieldMessages.description(path, heat))
          .containsExactly("Heat added per strike.", "Effect: Higher values melt ore faster.");
      TextBundle bundle = bundle();
      assertThat(bundle.problems()).isEmpty();
      assertThat(bundle.translations().get("de_DE").values()).containsKey(LABEL);
    }
  }

  @Test
  void anEnglishEntryOfTheAddonReplacesTheAnnotation() {
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(data).build(),
        new ForgeAddon(Map.of("config.fields.skills.forge.heat.label", "Forge Heat")))) {
      assertThat(ConfigFieldMessages.label("skills.cfgtext:forge.heat")).isEqualTo("Forge Heat");
      assertThat(bundle().keys()).extracting(MessageKey::id).contains(LABEL, DESCRIPTION);
    }
  }

  private static TextBundle bundle() {
    for (TextBundle bundle : AdaptLanguage.texts().bundles()) {
      if (bundle.namespace().equals("cfgtext")) {
        return bundle;
      }
    }
    throw new AssertionError("no bundle of cfgtext");
  }

  private record ForgeAddon(Map<String, String> english) implements AdaptAddon {
    @Override
    public String namespace() {
      return "cfgtext";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.skill(ForgeSkill.ID, ForgeSkill::new);
      registrar.messages(new MessageBundle() {
        @Override
        public List<String> locales() {
          return List.of(MessageBundle.DEFAULT_LOCALE, "de_DE");
        }

        @Override
        public void load(String locale, MessageSink sink) {
          if (locale.equals("de_DE")) {
            sink.message("config.fields.skills.forge.heat.label", "Hitze");
            return;
          }
          sink.message("skill.forge.name", "Forge");
          english.forEach(sink::message);
        }
      });
    }
  }

  public static final class ForgeSkill extends SimpleSkill<ForgeSkill.Config> {
    public static final Id ID = Id.of("cfgtext", "forge");

    public ForgeSkill() {
      super(ID);
      registerConfiguration(Config.class);
      setIcon(Id.minecraft("anvil"));
    }

    public static final class Config extends SkillConfig {
      @ConfigDoc(value = "Heat added per strike.", impact = "Higher values melt ore faster.")
      public double heat = 2.0D;
    }
  }
}
