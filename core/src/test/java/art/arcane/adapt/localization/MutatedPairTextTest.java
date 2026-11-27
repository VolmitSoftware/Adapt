package art.arcane.adapt.localization;

import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.CatalogViews;
import art.arcane.adapt.client.view.ShortNameView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.mutation.blocks.MutatedText;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Mutation text and catalog views for pairs with addon skills: kit words, conventional words, no kit, a named pair. */
class MutatedPairTextTest {
  private static final String LAB = "lab";
  private static final Id SWORDS = Id.adapt("swords");
  private static final Id ALCHEMY = Id.of(LAB, "alchemy");
  private static final Id BOTANY = Id.of(LAB, "botany");
  private static final Id GEOLOGY = Id.of(LAB, "geology");
  private static final SoundPalette SOUNDS = SoundPalette.of(Id.minecraft("entity.player.attack.strong"),
      Id.minecraft("block.anvil.use"), Id.minecraft("entity.player.levelup"), Id.minecraft("block.note_block.hat"));

  @TempDir
  Path dataFolder;

  @Test
  void aBuiltInAndAnAddonSkillComposeFromBothKits() {
    try (TestAdapt adapt = start()) {
      SkillRegistry registry = registry(adapt);
      MutatedText.Parents swordsAlchemy = parents(registry, SWORDS, ALCHEMY);
      MutatedText.Parents alchemySwords = parents(registry, ALCHEMY, SWORDS);

      assertThat(MutatedText.mutationName(MutatedText.ENGLISH, swordsAlchemy)).isEqualTo("Gilded Duelist");
      assertThat(MutatedText.mutationName(MutatedText.ENGLISH, alchemySwords)).isEqualTo("Bladed Transmuter");
      assertThat(MutatedText.adaptationName(MutatedText.ENGLISH, swordsAlchemy, Recipe.GRAFT)).isEqualTo("Gilded Graft");
      assertThat(MutatedText.shortName(MutatedText.ENGLISH, swordsAlchemy, Recipe.GRAFT)).isEqualTo("Graft");
      assertThat(MutatedText.skillDescription(MutatedText.ENGLISH, swordsAlchemy))
          .startsWith("Swords and Alchemy in one skill: Swords leads");
      assertThat(MutatedText.description(MutatedText.ENGLISH, swordsAlchemy, Recipe.GRAFT))
          .isEqualTo("When you land a sword critical hit, the target turns to gold for 2 s.");
      assertThat(MutatedText.description(MutatedText.ENGLISH, alchemySwords, Recipe.ATTUNEMENT))
          .isEqualTo("While you stood by a brewing stand in the last 5 s, every 20 s you gain +15% attack speed for 5 s.");
      assertThat(MutatedText.blockName(MutatedText.ENGLISH, registry.getSkill(ALCHEMY), BlockSlot.FUEL))
          .isEqualTo("Alchemy Fuel");
    }
  }

  @Test
  void twoAddonSkillsUseTheirConventionalWordsAndARegisteredName() {
    try (TestAdapt adapt = start()) {
      SkillRegistry registry = registry(adapt);
      MutatedText.Parents botanyAlchemy = parents(registry, BOTANY, ALCHEMY);
      MutatedText.Parents alchemyBotany = parents(registry, ALCHEMY, BOTANY);

      assertThat(MutatedText.mutationName(MutatedText.ENGLISH, botanyAlchemy)).isEqualTo("Gilded Grower");
      assertThat(MutatedText.mutationName(MutatedText.ENGLISH, alchemyBotany)).isEqualTo("Greenhouse");
      assertThat(AdaptLanguage.render(MutatedText.mutationName(MutatedText.VIEWER, alchemyBotany))).isEqualTo("Greenhouse");
      assertThat(MutatedText.adaptationName(MutatedText.ENGLISH, alchemyBotany, Recipe.ECHO)).isEqualTo("Verdant Echo");
      assertThat(MutatedText.description(MutatedText.ENGLISH, botanyAlchemy, Recipe.ECHO))
          .isEqualTo("When you brew a potion, nearby crops grow one stage.");
    }
  }

  @Test
  void aPairWhoseKitsReserveEveryGestureShowsItsBarterAsUnavailable() {
    try (TestAdapt adapt = start()) {
      SkillRegistry registry = registry(adapt);
      MutatedText.Parents open = parents(registry, ALCHEMY, GEOLOGY);
      MutatedText.Parents reserved = parents(registry, ALCHEMY, BOTANY);

      assertThat(MutatedText.gesture(open)).contains(BarterGesture.SNEAK_LEFT_CLICK);
      assertThat(MutatedText.description(MutatedText.ENGLISH, open, Recipe.BARTER))
          .isEqualTo("Sneak and left-click the air to spend a little hunger: you gain a splash of luck.");
      assertThat(MutatedText.gesture(reserved)).isEmpty();
      assertThat(MutatedText.description(MutatedText.ENGLISH, reserved, Recipe.BARTER))
          .isEqualTo("Unavailable: these two skills use every Barter gesture between them.");
    }
  }

  @Test
  void aSkillWithoutAKitUsesItsNameAndTheGenericPhrases() {
    try (TestAdapt adapt = start()) {
      SkillRegistry registry = registry(adapt);
      MutatedText.Parents geologyBotany = parents(registry, GEOLOGY, BOTANY);
      MutatedText.Parents botanyGeology = parents(registry, BOTANY, GEOLOGY);

      assertThat(MutatedText.mutationName(MutatedText.ENGLISH, geologyBotany)).isEqualTo("Verdant Geology");
      assertThat(MutatedText.mutationName(MutatedText.ENGLISH, botanyGeology)).isEqualTo("Geology Grower");
      assertThat(MutatedText.phrase(MutatedText.ENGLISH, registry.getSkill(GEOLOGY), BlockSlot.STATE))
          .isEqualTo("you earned Geology XP in the last few seconds");
      assertThat(MutatedText.description(MutatedText.ENGLISH, geologyBotany, Recipe.GRAFT))
          .startsWith("When you earn Geology XP, ");
      assertThat(MutatedText.description(MutatedText.ENGLISH, botanyGeology, Recipe.ATTUNEMENT))
          .isEqualTo("While you stood on farmland in the last 5 s, every 20 s you gain a little regeneration and saturation.");
    }
  }

  @Test
  void theCatalogListsAMutatedAddonSkillWithItsNameShortNamesAndRows() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    try (TestAdapt adapt = TestAdapt.start(platform, new BuiltinAddon(), new LabAddon())) {
      SkillRegistry registry = registry(adapt);
      UUID viewer = UUID.randomUUID();
      List<SkillPair> pairs = List.of(new SkillPair(SWORDS, ALCHEMY), new SkillPair(ALCHEMY, BOTANY),
          new SkillPair(GEOLOGY, BOTANY));

      CatalogView catalog = CatalogViews.build(registry, viewer, pairs, false);

      SkillView swordsAlchemy = skill(catalog, "adapt:swords+lab:alchemy");
      assertThat(swordsAlchemy.name()).isEqualTo("Gilded Duelist");
      assertThat(swordsAlchemy.description()).startsWith("Swords and Alchemy in one skill");
      assertThat(swordsAlchemy.adaptations()).extracting(AdaptationView::id)
          .contains("adapt:mutated/swords/lab/alchemy/graft", "adapt:mutated/swords/lab/alchemy/convergence");
      assertThat(swordsAlchemy.rowGroups().get(0))
          .containsExactlyElementsOf(SkillHost.of(registry.getSkill(SWORDS)).getUpgrades().stream()
              .map((Adaptation<?> upgrade) -> upgrade.getId().toString()).toList());
      assertThat(swordsAlchemy.rowGroups().get(swordsAlchemy.rowGroups().size() - 1)).hasSize(Recipe.values().length);
      assertThat(skill(catalog, "lab:alchemy+lab:botany").name()).isEqualTo("Greenhouse");
      assertThat(skill(catalog, "lab:geology+lab:botany").name()).isEqualTo("Verdant Geology");
      assertThat(skill(catalog, "lab:geology+lab:botany").rowGroups()).hasSize(1);

      List<ShortNameView> shortNames = CatalogViews.shortNames(registry, viewer, pairs);
      assertThat(shortNames).contains(new ShortNameView("adapt:mutated/swords/lab/alchemy/graft", "Graft"),
          new ShortNameView("lab:mutated/geology/lab/botany/echo", "Echo"));
    }
  }

  private TestAdapt start() {
    return TestAdapt.start(TestPlatform.builder(dataFolder).build(), new BuiltinAddon(), new LabAddon());
  }

  private static SkillRegistry registry(TestAdapt adapt) {
    return adapt.core().getAdaptServer().getSkillRegistry();
  }

  private static MutatedText.Parents parents(SkillRegistry registry, Id lead, Id partner) {
    return new MutatedText.Parents(registry.getSkill(lead), registry.getSkill(partner), registry.getPairRegistry());
  }

  private static SkillView skill(CatalogView catalog, String id) {
    return catalog.skills().stream().filter((SkillView view) -> view.id().equals(id)).findFirst().orElseThrow();
  }

  private static MutationKit.Builder kit(Id skill, TriggerKind kind) {
    return MutationKit.builder()
        .trigger(kind, () -> new Hit(skill))
        .strikes(() -> new Strike(skill, StrikeForm.FIGHT), () -> new Strike(skill, StrikeForm.WORK))
        .boon(() -> new Gift(skill))
        .fuel(() -> new Cost(skill))
        .state(() -> new Held(skill))
        .sounds(SOUNDS);
  }

  /** Alchemy has a kit with its own words, Botany a kit with conventional words, Geology no kit. */
  public static final class LabAddon implements AdaptAddon {
    @Override
    public String namespace() {
      return LAB;
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.messages(MessageBundle.builder()
          .message("skill.alchemy.name", "Alchemy")
          .message("skill.alchemy.description", "Turns one thing into another.")
          .message("word.transmuter", "Transmuter")
          .message("word.gilded", "Gilded")
          .message("skill.alchemy.block.trigger", "brew a potion")
          .message("skill.alchemy.block.strike_fight", "the target turns to gold for 2 s")
          .message("skill.alchemy.block.strike_work", "nearby ore glints for 5 s")
          .message("skill.alchemy.block.boon", "a splash of luck")
          .message("skill.alchemy.block.fuel", "one glass bottle")
          .message("skill.alchemy.block.state", "you stood by a brewing stand in the last 5 s")
          .message("skill.botany.name", "Botany")
          .message("skill.botany.description", "Grows things.")
          .message("skill.botany.mutated.noun", "Grower")
          .message("skill.botany.mutated.adjective", "Verdant")
          .message("skill.botany.block.trigger", "plant a seed")
          .message("skill.botany.block.strike_fight", "the target is rooted for 1 s")
          .message("skill.botany.block.strike_work", "nearby crops grow one stage")
          .message("skill.botany.block.boon", "a little saturation")
          .message("skill.botany.block.fuel", "one bone meal")
          .message("skill.botany.block.state", "you stood on farmland in the last 5 s")
          .message("skill.geology.name", "Geology")
          .message("skill.geology.description", "Reads rocks.")
          .message("pair.greenhouse", "Greenhouse")
          .build());
      registrar.skill(ALCHEMY, Alchemy::new);
      registrar.skill(BOTANY, Botany::new);
      registrar.skill(GEOLOGY, Geology::new);
      registrar.pairName(ALCHEMY, BOTANY, Text.key("lab.pair.greenhouse"));
    }
  }

  public static final class Alchemy extends SimpleSkill<LabConfig> {
    public Alchemy() {
      super(ALCHEMY);
      registerConfiguration(LabConfig.class);
      registerAdaptation(ContentIds.child(ALCHEMY, "notes"), () -> new Notes(ContentIds.child(ALCHEMY, "notes")));
      setColor(Rgb.of(0xAA00AA));
      setIcon(Id.minecraft("brewing_stand"));
      registerMutationKit(kit(ALCHEMY, TriggerKind.WORK)
          .words(Text.key("lab.word.transmuter"), Text.key("lab.word.gilded"))
          .reserve(BarterGesture.SNEAK_SWAP, BarterGesture.DOUBLE_SNEAK)
          .tint(Rgb.of(0xE0C040))
          .build());
    }
  }

  public static final class Botany extends SimpleSkill<LabConfig> {
    public Botany() {
      super(BOTANY);
      registerConfiguration(LabConfig.class);
      registerAdaptation(ContentIds.child(BOTANY, "notes"), () -> new Notes(ContentIds.child(BOTANY, "notes")));
      setColor(Rgb.of(0x40AA40));
      setIcon(Id.minecraft("oak_sapling"));
      registerMutationKit(kit(BOTANY, TriggerKind.WORK)
          .reserve(BarterGesture.SNEAK_LEFT_CLICK, BarterGesture.SNEAK_SCROLL)
          .build());
    }
  }

  public static final class Geology extends SimpleSkill<LabConfig> {
    public Geology() {
      super(GEOLOGY);
      registerConfiguration(LabConfig.class);
      registerAdaptation(ContentIds.child(GEOLOGY, "notes"), () -> new Notes(ContentIds.child(GEOLOGY, "notes")));
      setColor(Rgb.of(0x806040));
      setIcon(Id.minecraft("granite"));
    }
  }

  public static final class LabConfig extends SkillConfig {
  }

  /** One plain adaptation, so each skill can be mutated. */
  public static final class Notes extends SimpleAdaptation<Notes.Config> {
    public Notes(Id id) {
      super(id);
      registerConfiguration(Config.class);
      setIcon(Id.minecraft("paper"));
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }

    public static final class Config extends AdaptationConfig {
      public Config() {
        maxLevel = 1;
      }
    }
  }

  public static final class Hit extends TriggerBlock<MutationBlock.Config> {
    public Hit(Id skill) {
      super(skill);
      registerConfiguration(MutationBlock.Config.class);
    }
  }

  public static final class Strike extends StrikeBlock<MutationBlock.Config> {
    public Strike(Id skill, StrikeForm form) {
      super(skill, form);
      registerConfiguration(MutationBlock.Config.class);
    }

    @Override
    public Outcome strike(BlockUse use) {
      return Outcome.MISSED;
    }
  }

  public static final class Gift extends BoonBlock<MutationBlock.Config> {
    public Gift(Id skill) {
      super(skill);
      registerConfiguration(MutationBlock.Config.class);
    }

    @Override
    public Outcome grant(BlockUse use) {
      return Outcome.MISSED;
    }
  }

  public static final class Cost extends FuelBlock<MutationBlock.Config> {
    public Cost(Id skill) {
      super(skill);
      registerConfiguration(MutationBlock.Config.class);
    }

    @Override
    public boolean canPay(BlockUse use) {
      return false;
    }

    @Override
    public boolean pay(BlockUse use) {
      return false;
    }
  }

  public static final class Held extends StateBlock<MutationBlock.Config> {
    public Held(Id skill) {
      super(skill);
      registerConfiguration(MutationBlock.Config.class);
    }

    @Override
    public boolean holds(PlayerRef player) {
      return false;
    }
  }
}
