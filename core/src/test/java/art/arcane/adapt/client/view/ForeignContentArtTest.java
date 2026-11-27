package art.arcane.adapt.client.view;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.content.XpSource;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.client.theme.ThemeKit;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.asset.AssetCatalog;
import art.arcane.adapt.engine.asset.PngImages;
import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillMenus;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Art for another provider's skill and adaptation is looked up in that provider's namespace, never in {@code adapt}. */
class ForeignContentArtTest {
  private static final Id ALCHEMY = Id.of("alchemist", "alchemy");
  private static final Id TRANSMUTE = Id.of("alchemist", "alchemy/transmute");

  @TempDir
  Path dataFolder;

  @Test
  void foreignSkillAndAdaptationViewsUseTheirOwnNamespace() {
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(dataFolder).build(), new AlchemistAddon())) {
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      CatalogView catalog = CatalogViews.build(registry, UUID.randomUUID(), List.of(), false);
      SkillView skill = catalog.skills().stream().filter((SkillView view) -> view.id().equals(ALCHEMY.toString())).findFirst()
          .orElseThrow();
      assertThat(skill.icon().spriteId()).isEqualTo("alchemist:textures/gui/skill/alchemy.png");
      assertThat(skill.adaptations()).singleElement().satisfies((AdaptationView adaptation) ->
          assertThat(adaptation.icon().spriteId()).isEqualTo("alchemist:textures/gui/adaptation/alchemy/transmute.png"));
      assertThat(ViewIcons.skill(registry.getSkill(ALCHEMY)).spriteId()).isEqualTo(skill.icon().spriteId());

      AssetCatalog assets = adapt.core().assets();
      assertThat(assets.themes()).contains(ALCHEMY);
      ThemeAssets theme = assets.theme(ALCHEMY).orElseThrow();
      assertThat(theme.skill()).isEqualTo(ALCHEMY);
      assertThat(theme.generated()).isTrue();
      assertThat(ThemeKit.dataId(ALCHEMY.namespace(), ALCHEMY.path())).isEqualTo("alchemist:themes/alchemy.json");
      assertThat(ThemeKit.atlasId(ALCHEMY.namespace(), ALCHEMY.path())).isEqualTo("alchemist:textures/gui/theme/alchemy.png");
    }
  }

  @Test
  void aForeignSkillWrittenLikeABuiltInIsTreatedLikeOne() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    FakePlayer player = platform.player("Parity", 0.5D, 61D, 0.5D);
    try (TestAdapt adapt = TestAdapt.start(platform, new BuiltinAddon(), new AlchemistAddon())) {
      adapt.join(player);
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      CatalogView catalog = CatalogViews.build(registry, player.uuid(), List.of(), false);
      for (Id id : List.of(SkillHerbalism.ID, ALCHEMY)) {
        Skill<?> skill = registry.getSkill(id);
        assertThat(AdaptApi.content().skill(id)).containsSame(skill);
        SkillHost host = SkillHost.of(skill);
        assertThat(host.getLineKey()).isEqualTo(id.toString());
        assertThat(skill.isRuntimeRegistered()).isTrue();
        assertThat(skill.getAdaptations()).isNotEmpty();
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          assertThat(adaptation.getSkill()).isSameAs(skill);
          assertThat(AdaptationHost.of(adaptation).skillHost()).isSameAs(host);
          assertThat(AdaptApi.content().adaptation(adaptation.getId())).containsSame(adaptation);
          assertThat(adaptation.isRuntimeRegistered()).isTrue();
        }
        assertThat(skill.getAdaptationGroups()).isNotEmpty();
        assertThat(skill.getXpSources()).isNotEmpty();
        SkillView view = catalog.skills().stream().filter((SkillView shown) -> shown.id().equals(id.toString()))
            .findFirst().orElseThrow();
        assertThat(view.rowGroups()).isEqualTo(SkillHost.adaptationRowGroups(skill));
        assertThat(view.adaptations()).extracting(AdaptationView::skillId).containsOnly(id.toString());
        assertThat(view.name()).isNotBlank();
        assertThat(SkillMenus.adaptationRows(skill, player)).isNotEmpty();
        assertThat(ViewIcons.skill(skill).spriteId()).startsWith(id.namespace() + ":");
      }
    }
  }

  @Test
  void aMutatedTileDrawsEachParentFromItsOwnNamespace() {
    String tile = MutationIcons.tile(new SkillPair(ALCHEMY, Id.adapt("swords")));
    assertThat(IconSprites.layers(tile)).extracting(IconSprites.Layer::spriteId)
        .containsExactly("alchemist:textures/gui/emblem/alchemy.png", "adapt:textures/gui/trim/swords.png");
    String reversed = MutationIcons.tile(new SkillPair(Id.adapt("swords"), ALCHEMY));
    assertThat(IconSprites.layers(reversed)).extracting(IconSprites.Layer::spriteId)
        .containsExactly("adapt:textures/gui/emblem/swords.png", "alchemist:textures/gui/trim/alchemy.png");
  }

  @Test
  void anAddonSkillWithoutArtGetsAGeneratedEmblemAndTrim() {
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(dataFolder).build(), new AlchemistAddon())) {
      AssetCatalog assets = adapt.core().assets();
      byte[] emblem = assets.emblem(ALCHEMY).orElseThrow();
      byte[] trim = assets.trim(ALCHEMY).orElseThrow();

      assertThat(PngImages.size(emblem)).containsExactly(16, 16);
      assertThat(PngImages.size(trim)).containsExactly(16, 16);
      assertThat(emblem).isNotEqualTo(trim);
      assertThat(assets.emblem(Id.of("alchemist", "unknown"))).isEmpty();
      assertThat(assets.emblem(Id.adapt("swords"))).isEmpty();
    }
  }

  /** An addon whose skill is written exactly the way a built-in skill is. */
  public static final class AlchemistAddon implements AdaptAddon {
    @Override
    public String namespace() {
      return ALCHEMY.namespace();
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.messages(MessageBundle.builder()
          .message("skill.alchemy.name", "Alchemy")
          .message("skill.alchemy.description", "Turns one thing into another.")
          .message("skill.alchemy.group.lab", "Laboratory")
          .message("skill.alchemy.xp.transmute", "Transmuting anything")
          .message("adaptation.alchemy.transmute.name", "Transmute")
          .message("adaptation.alchemy.transmute.description", "Turns lead into gold.")
          .message("adaptation.alchemy.transmute.lore1", "Chance")
          .build());
      registrar.skill(Alchemy.ID, Alchemy::new);
    }
  }

  public static final class Alchemy extends SimpleSkill<Alchemy.Config> {
    public static final Id ID = ALCHEMY;
    private final XpSource transmute = xpSource("transmute");

    public Alchemy() {
      super(ID);
      registerConfiguration(Config.class);
      setColor(Rgb.of(0xAA00AA));
      setIcon(Id.minecraft("brewing_stand"));
      registerAdaptation(Transmute.ID, Transmute::new);
      registerGroup(text("group.lab"), List.of(Transmute.ID));
    }

    public static final class Config extends SkillConfig {
    }
  }

  public static final class Transmute extends SimpleAdaptation<Transmute.Config> {
    public static final Id ID = TRANSMUTE;

    public Transmute() {
      super(ID);
      registerConfiguration(Config.class);
      setIcon(Id.minecraft("gold_nugget"));
    }

    @Override
    public void addStats(int level, StatLines lines) {
      statLore(lines, level * 4 + "%", 1);
    }

    public static final class Config extends AdaptationConfig {
      public Config() {
        maxLevel = 3;
      }
    }
  }
}
