package art.arcane.adapt.engine.player;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.player.MutationResult;
import art.arcane.adapt.api.player.OrphanedLine;
import art.arcane.adapt.api.player.SkillProgress;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.command.CommandAdapt;
import art.arcane.adapt.command.CommandContext;
import art.arcane.adapt.command.CommandSource;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerAdaptation;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * A mutation of a built-in and an addon skill while the addon comes and goes: suspended and uncounted while it is
 * gone, split by an admin back into the surviving parent, or resumed unchanged when the addon returns.
 */
class SuspendedMutationTest {
  private static final Id SWORDS = Id.adapt("swords");
  private static final Id SMITHING = Id.of("forge", "smithing");
  private static final SkillPair PAIR = new SkillPair(SWORDS, SMITHING);
  private static final Id GRAFT = PAIR.child("graft");

  @TempDir
  Path data;

  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer runtime;
  private EnginePlayerProfile profile;
  private AddonHandle forge;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Smith", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class));
    forge = AdaptApi.addons().install(new Forge());
    runtime = adapt.join(player);
    runtime.getData().setMasterXp(XP.getXpForLevel(40));
    profile = PlayerApi.directory().require(player);
    profile.skill(SWORDS).setLevel(20);
    profile.skill(SMITHING).setLevel(20);
    assertThat(profile.mutate(SWORDS, SMITHING).status()).isEqualTo(MutationResult.Status.MUTATED);
    PlayerSkillLine merged = runtime.getData().getSkillLine(PAIR.key());
    merged.setKnowledge(9L);
    PlayerAdaptation graft = new PlayerAdaptation();
    graft.setId(GRAFT.toString());
    graft.setLevel(2);
    merged.getAdaptations().put(GRAFT.toString(), graft);
  }

  @AfterEach
  void stop() {
    CommandContext.remove();
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aMutationWithAnUninstalledParentIsSuspendedUncountedAndResumesUnchanged() {
    PlayerSkillLine merged = runtime.getData().getSkillLine(PAIR.key());
    double xp = merged.getXp();
    int level = merged.getLevel();
    assertThat(profile.skills()).extracting(SkillProgress::skill).containsExactly(SWORDS);
    assertThat(profile.power()).isPositive();

    forge.uninstall();

    assertThat(profile.mutations()).containsExactly(PAIR);
    assertThat(profile.orphans()).extracting(OrphanedLine::line, OrphanedLine::namespace)
        .containsExactly(tuple(new OrphanedLine.Pair(PAIR), "forge"));
    assertThat(profile.skills()).isEmpty();
    assertThat(profile.power()).isZero();
    assertThat(profile.skill(SWORDS).isAvailable()).isFalse();
    assertThat(runtime.getData().getSkillLineNullable(PAIR.key()).getAdaptationLevel(GRAFT.toString())).isEqualTo(2);

    forge = AdaptApi.addons().install(new Forge());

    assertThat(profile.orphans()).isEmpty();
    assertThat(profile.mutations()).containsExactly(PAIR);
    assertThat(profile.skills()).extracting(SkillProgress::skill).containsExactly(SWORDS);
    PlayerSkillLine resumed = runtime.getData().getSkillLine(PAIR.key());
    assertThat(resumed.getXp()).isEqualTo(xp);
    assertThat(resumed.getLevel()).isEqualTo(level);
    assertThat(resumed.getKnowledge()).isEqualTo(9L);
    assertThat(AdaptApi.content().adaptation(GRAFT).orElseThrow().getLevel(player)).isEqualTo(2);
    assertThat(profile.power()).isPositive();
  }

  @Test
  void anAdminUnmutateOfASuspendedMutationRestoresTheSurvivingParent() {
    double swordsXp = XP.getXpForLevel(20);
    forge.uninstall();

    assertThat(profile.unmutate(SWORDS).status()).isEqualTo(MutationResult.Status.UNMUTATED);

    assertThat(profile.mutations()).isEmpty();
    assertThat(profile.skill(SWORDS).isAvailable()).isTrue();
    assertThat(profile.skill(SWORDS).xp()).isGreaterThanOrEqualTo(swordsXp);
    assertThat(profile.skill(SWORDS).level()).isEqualTo(20);
    assertThat(profile.orphans()).extracting(OrphanedLine::line).containsExactly(new OrphanedLine.Line(SMITHING));
  }

  @Test
  void theUnmutateCommandSplitsASuspendedMutation() {
    forge.uninstall();
    CommandContext.touch(new Console());

    new CommandAdapt().unmutate(player, "swords");

    assertThat(profile.mutations()).isEmpty();
    assertThat(profile.skill(SWORDS).isAvailable()).isTrue();
    assertThat(profile.skill(SWORDS).level()).isEqualTo(20);

    forge = AdaptApi.addons().install(new Forge());

    assertThat(profile.orphans()).isEmpty();
    assertThat(profile.skill(SMITHING).level()).isEqualTo(20);
  }

  static final class Forge implements AdaptAddon {
    @Override
    public String namespace() {
      return "forge";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.messages(MessageBundle.builder()
          .message("skill.smithing.name", "Smithing")
          .message("skill.smithing.description", "Shape metal.")
          .message("adaptation.smithing.temper.name", "Temper")
          .message("adaptation.smithing.temper.description", "Harden a blade.")
          .build());
      registrar.skill(SMITHING, Smithing::new);
    }
  }

  public static final class Smithing extends SimpleSkill<Smithing.Config> {
    public Smithing() {
      super(SMITHING);
      registerConfiguration(Config.class);
      setColor(Rgb.of(0x8A6D3B));
      setIcon(Id.minecraft("anvil"));
      Id temper = Id.of("forge", "smithing/temper");
      registerAdaptation(temper, () -> new Temper(temper));
    }

    public static final class Config extends SkillConfig {
    }
  }

  public static final class Temper extends SimpleAdaptation<AdaptationConfig> {
    public Temper(Id id) {
      super(id);
      registerConfiguration(AdaptationConfig.class);
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }

  private static final class Console implements CommandSource {
    @Override
    public String name() {
      return "Console";
    }

    @Override
    public Optional<PlayerRef> player() {
      return Optional.empty();
    }

    @Override
    public boolean hasPermission(String node) {
      return true;
    }

    @Override
    public boolean isOp() {
      return true;
    }

    @Override
    public void sendSection(String sectionText) {
    }

    @Override
    public void sendLiteral(String plainText) {
    }

    @Override
    public void sendMarkup(String markup) {
    }
  }
}
