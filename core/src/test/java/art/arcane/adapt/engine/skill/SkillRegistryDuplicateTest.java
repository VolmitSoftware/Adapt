package art.arcane.adapt.engine.skill;

import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.engine.addon.AddonContent;
import art.arcane.adapt.engine.mutation.pair.PairClaims;
import art.arcane.adapt.engine.addon.SkillSource;
import art.arcane.adapt.engine.bridge.ComponentBuilder;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillRegistryDuplicateTest {
  private static final Id SKILL = Id.of("dupes", "skill");
  private static final Id OTHER_SKILL = Id.of("dupes", "other");
  private static final Id SHARED = Id.of("dupes", "skill/shared");

  @TempDir
  Path dataFolder;

  private TestAdapt adapt;

  @AfterEach
  void stop() {
    if (adapt != null) {
      adapt.close();
    }
  }

  @Test
  void aSecondOwnerOfATakenSkillIdIsRejectedNamingBoth() {
    SkillRegistry registry = start();

    assertThatThrownBy(() -> registry.install(content("impostors", new SkillSource(SKILL, "impostors", () -> ComponentBuilder.buildSkill(Impostor::new)))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SKILL.toString())
        .hasMessageContaining("dupes")
        .hasMessageContaining("impostors");
    assertThat(registry.getSkill(SKILL)).isInstanceOf(Original.class);
  }

  @Test
  void anAdaptationIdAnotherSkillHasIsRejectedNamingBoth() {
    SkillRegistry registry = start();

    assertThatThrownBy(() -> registry.install(content("dupes", new SkillSource(OTHER_SKILL, "dupes", () -> ComponentBuilder.buildSkill(Borrower::new)))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SHARED.toString())
        .hasMessageContaining(SKILL.toString())
        .hasMessageContaining(OTHER_SKILL.toString());
    assertThat(registry.getSkill(OTHER_SKILL)).isNull();
    assertThat(registry.isKnownSkill(OTHER_SKILL)).isFalse();
    assertThat(registry.getAdaptation(SHARED).getSkill()).isInstanceOf(Original.class);
  }

  @Test
  void refreshingTheAddonRebuildsTheSkillFromItsSupplier() {
    SkillRegistry registry = start();
    Skill<?> first = registry.getSkill(SKILL);

    adapt.addons().get(0).refresh();

    assertThat(registry.getSkill(SKILL)).isInstanceOf(Original.class).isNotSameAs(first);
    assertThat(registry.getSkill("dupes:skill")).isSameAs(registry.getSkill(SKILL));
  }

  private static AddonContent content(String namespace, SkillSource skill) {
    return new AddonContent(namespace, List.of(skill), List.of(), List.of(), Optional.empty(), List.of(),
        List.of(), List.of(), PairClaims.none(namespace), List.of(), List.of());
  }

  private SkillRegistry start() {
    adapt = TestAdapt.start(TestPlatform.builder(dataFolder).build(), List.of(Original.class));
    return adapt.core().getAdaptServer().getSkillRegistry();
  }


  public static final class Original extends SimpleSkill<SkillConfig> {
    public static final Id ID = SKILL;

    public Original() {
      super(SKILL);
      registerConfiguration(SkillConfig.class);
      registerAdaptation(SHARED, Shared::new);
    }

    @Override
    public boolean isEnabled() {
      return true;
    }
  }

  public static final class Impostor extends SimpleSkill<SkillConfig> {
    public Impostor() {
      super(SKILL);
      registerConfiguration(SkillConfig.class);
    }

    @Override
    public boolean isEnabled() {
      return true;
    }
  }

  public static final class Borrower extends SimpleSkill<SkillConfig> {
    public Borrower() {
      super(OTHER_SKILL);
      registerConfiguration(SkillConfig.class);
      registerAdaptation(SHARED, Shared::new);
    }

    @Override
    public boolean isEnabled() {
      return true;
    }
  }

  public static final class Shared extends SimpleAdaptation<AdaptationConfig> {
    public Shared() {
      super(SHARED);
      registerConfiguration(AdaptationConfig.class);
      setLocalizationKey("pickaxe.chisel");
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }
}
