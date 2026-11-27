package art.arcane.adapt.command;

import art.arcane.adapt.api.player.OrphanedLine;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.event.AdaptationLearnEvent;
import art.arcane.adapt.api.player.LearnOptions;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.player.EnginePlayerProfile;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerAdaptation;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The progress commands, run from the console against a real core, change the player through their profile. */
class CommandProfileTest {
  private static final Id SWORDS = Id.adapt("swords");
  private static final Id STEALTH = Id.adapt("stealth");

  @TempDir
  Path data;

  private final Console console = new Console();
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer runtime;
  private EnginePlayerProfile profile;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Target", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillStealth.class));
    runtime = adapt.join(player);
    runtime.getData().setMasterXp(XP.getXpForLevel(40));
    profile = PlayerApi.directory().require(player);
    CommandContext.touch(console);
  }

  @AfterEach
  void stop() {
    CommandContext.remove();
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void claimSkillSetsTheLevel() {
    new CommandAdapt().claimSkill(new AdaptationListingHandler.SkillProvider("swords"), 9, player);

    assertThat(profile.skill(SWORDS).level()).isEqualTo(9);
    assertThat(console.last()).contains("9");
  }

  @Test
  void skillArgumentsResolveAsIdsAndUnknownOnesStopTheCommand() {
    new CommandAdapt().claimSkill(new AdaptationListingHandler.SkillProvider("swords:swords"), 5, player);
    assertThat(console.last()).contains("Unknown skill: swords:swords.");
    new CommandAdapt().claimSkill(new AdaptationListingHandler.SkillProvider("adapt:nothing"), 5, player);
    assertThat(console.last()).contains("Unknown skill: adapt:nothing.");
    assertThat(profile.skill(SWORDS).level()).isZero();

    new CommandAdapt().claimSkill(new AdaptationListingHandler.SkillProvider("swords"), 5, player);
    assertThat(profile.skill(SWORDS).level()).isEqualTo(5);
    new CommandAdapt().claimSkill(new AdaptationListingHandler.SkillProvider("adapt:swords"), 6, player);
    assertThat(profile.skill(SWORDS).level()).isEqualTo(6);
  }

  @Test
  void adaptationArgumentsResolveAsIdsAndUnknownOnesStopTheCommand() {
    Adaptation<?> adaptation = first();
    String legacy = "swords:" + adaptation.getId().path().replace('/', '-');

    new CommandAdapt().determine(new AdaptationListingHandler.AdaptationProvider(legacy), true, true, 1, player);
    assertThat(console.last()).contains("Unknown adaptation: " + legacy + ".");
    new CommandAdapt().determine(new AdaptationListingHandler.AdaptationProvider("adapt:sword/nothing"), true, true, 1,
        player);
    assertThat(console.last()).contains("Unknown adaptation: adapt:sword/nothing.");
    assertThat(profile.level(adaptation.getId())).isZero();

    new CommandAdapt().determine(new AdaptationListingHandler.AdaptationProvider(adaptation.getId().path()), true, true,
        1, player);
    assertThat(profile.level(adaptation.getId())).isEqualTo(1);
    new CommandAdapt().determine(new AdaptationListingHandler.AdaptationProvider(adaptation.getId().toString()), true,
        true, 2, player);
    assertThat(profile.level(adaptation.getId())).isEqualTo(2);
  }

  @Test
  void determineLearnsThroughTheProfileAndFiresTheLearnEvent() {
    Adaptation<?> adaptation = first();
    List<AdaptationLearnEvent> learned = new ArrayList<>();
    adapt.bus().register(this, AdaptationLearnEvent.class, EventPriority.MONITOR, false, learned::add);

    new CommandAdapt().determine(new AdaptationListingHandler.AdaptationProvider(adaptation.getId().toString()),
        true, true, 2, player);

    assertThat(profile.level(adaptation.getId())).isEqualTo(2);
    assertThat(learned).singleElement().satisfies((AdaptationLearnEvent event) -> {
      assertThat(event.forced()).isTrue();
      assertThat(event.cost()).isZero();
    });

    new CommandAdapt().determine(new AdaptationListingHandler.AdaptationProvider(adaptation.getId().toString()),
        false, true, 2, player);
    assertThat(profile.level(adaptation.getId())).isEqualTo(1);
  }

  @Test
  void aCancelledLearnIsReportedAsNoChange() {
    Adaptation<?> adaptation = first();
    adapt.bus().register(this, AdaptationLearnEvent.class, EventPriority.NORMAL, false,
        (AdaptationLearnEvent event) -> event.setCancelled(true));

    new CommandAdapt().determine(new AdaptationListingHandler.AdaptationProvider(adaptation.getId().toString()),
        true, true, 1, player);

    assertThat(profile.level(adaptation.getId())).isZero();
    assertThat(console.last()).contains(adaptation.getId().toString());
  }

  @Test
  void claimAdaptationChargesUnlessForcedAndSaysWhyItRefuses() {
    Adaptation<?> adaptation = first();
    AdaptationListingHandler.AdaptationProvider target =
        new AdaptationListingHandler.AdaptationProvider(adaptation.getId().toString());

    new CommandAdapt().claimAdaptation(target, 1, false, player);
    assertThat(profile.level(adaptation.getId())).isZero();
    assertThat(console.last()).contains(String.valueOf(adaptation.getCostFor(1, 0)));

    profile.skill(SWORDS).addKnowledge(adaptation.getCostFor(1, 0));
    new CommandAdapt().claimAdaptation(target, 1, false, player);
    assertThat(profile.level(adaptation.getId())).isEqualTo(1);
    assertThat(profile.skill(SWORDS).knowledge()).isZero();

    new CommandAdapt().claimAdaptation(target, 3, true, player);
    assertThat(profile.level(adaptation.getId())).isEqualTo(3);
    new CommandAdapt().claimAdaptation(target, 0, true, player);
    assertThat(profile.level(adaptation.getId())).isZero();
  }

  @Test
  void mutationResetAndUnmutateSplitThroughTheProfile() {
    profile.skill(SWORDS).setLevel(20);
    profile.skill(STEALTH).setLevel(20);
    profile.mutate(SWORDS, STEALTH);

    new CommandAdapt().unmutate(player, "nothing");
    assertThat(profile.mutations()).hasSize(1);
    new CommandAdapt().unmutate(player, "stealth");
    assertThat(profile.mutations()).isEmpty();

    profile.mutate(SWORDS, STEALTH);
    new CommandAdapt().mutationReset(player);
    assertThat(profile.mutations()).isEmpty();
    assertThat(profile.skill(STEALTH).level()).isEqualTo(20);
  }

  @Test
  void boostAddsAPlayerMultiplierAndGlobalBoostAServerOne() {
    int before = runtime.getData().getMultipliers().size();
    new CommandAdapt().boost(30L, 2D, player);
    assertThat(runtime.getData().getMultipliers()).hasSize(before + 1);

    int global = adapt.core().getAdaptServer().getData().getMultipliers().size();
    new CommandAdapt().globalBoost(30L, 2D);
    assertThat(adapt.core().getAdaptServer().getData().getMultipliers()).hasSize(global + 1);
  }

  @Test
  void clearSubcommandsKeepOrphans() {
    Adaptation<?> adaptation = first();
    profile.skill(SWORDS).setLevel(6);
    profile.skill(SWORDS).addKnowledge(30L);
    profile.learn(adaptation.getId(), 1, LearnOptions.forced().quietly());
    profile.addStat("test.counter", 1D);
    orphan("gone:shadow");

    new CommandClear().knowledge(player);
    assertThat(profile.skill(SWORDS).knowledge()).isZero();
    new CommandClear().adaptations(player);
    assertThat(profile.level(adaptation.getId())).isZero();
    new CommandClear().stats(player);
    assertThat(profile.stat("test.counter")).isZero();
    new CommandClear().xp(player);
    assertThat(profile.skill(SWORDS).level()).isZero();
    new CommandClear().all(player);
    assertThat(profile.orphans()).extracting(orphan -> orphan.line()).containsExactly(new OrphanedLine.Line(Id.of("gone", "shadow")));
  }

  @Test
  void orphansListRefundAndPurgeANamespace() {
    profile.skill(SWORDS).setLevel(4);
    PlayerSkillLine swords = runtime.getData().getSkillLine("swords");
    PlayerAdaptation stored = new PlayerAdaptation();
    stored.setId("gone:swords/edge");
    stored.setLevel(2);
    stored.setKnowledge(9L);
    swords.getAdaptations().put("gone:swords/edge", stored);
    orphan("gone:shadow");
    CommandOrphans orphans = new CommandOrphans();

    orphans.list("gone", player);
    assertThat(console.all()).anySatisfy(line -> assertThat(line).contains("gone:swords/edge"))
        .anySatisfy(line -> assertThat(line).contains("gone:shadow"));
    orphans.list("other", null);
    assertThat(console.last()).contains("other");

    orphans.refund("gone", player);
    assertThat(profile.skill(SWORDS).knowledge()).isEqualTo(9L);
    assertThat(profile.orphans()).hasSize(1);

    orphans.purge("gone", null);
    assertThat(profile.orphans()).isEmpty();
  }

  @Test
  void orphansRefundAndPurgeEveryNamespaceForAStar() {
    PlayerSkillLine swords = runtime.getData().getSkillLine("swords");
    PlayerAdaptation stored = new PlayerAdaptation();
    stored.setId("gone:swords/edge");
    stored.setLevel(2);
    stored.setKnowledge(7L);
    swords.getAdaptations().put("gone:swords/edge", stored);
    orphan("gone:shadow");
    orphan("lost:echo");
    CommandOrphans orphans = new CommandOrphans();

    orphans.refund("*", player);
    assertThat(profile.skill(SWORDS).knowledge()).isEqualTo(7L);
    assertThat(profile.orphans()).extracting(orphan -> orphan.line()).containsExactlyInAnyOrder(
        new OrphanedLine.Line(Id.of("gone", "shadow")), new OrphanedLine.Line(Id.of("lost", "echo")));

    orphans.purge("*", player);
    assertThat(profile.orphans()).isEmpty();
  }

  @Test
  void orphansNeedThePermission() {
    console.denied = true;
    orphan("gone:shadow");

    new CommandOrphans().purge("gone", player);

    assertThat(profile.orphans()).hasSize(1);
    assertThat(console.last()).contains(CommandOrphans.PERMISSION);
  }

  private void orphan(String key) {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine(key);
    line.setXp(50D);
    line.settleWithoutRewards();
    runtime.getData().getSkillLines().put(key, line);
  }

  private Adaptation<?> first() {
    return adapt.core().getAdaptServer().getSkillRegistry().getSkill(SWORDS).getAdaptations().getFirst();
  }

  private static final class Console implements CommandSource {
    private final List<String> messages = new ArrayList<>();
    private boolean denied;

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
      return !denied;
    }

    @Override
    public boolean isOp() {
      return true;
    }

    @Override
    public void sendSection(String sectionText) {
      messages.add(C.stripColor(sectionText));
    }

    @Override
    public void sendLiteral(String plainText) {
      messages.add(plainText);
    }

    @Override
    public void sendMarkup(String markup) {
      messages.add(C.stripColor(markup));
    }

    List<String> all() {
      return List.copyOf(messages);
    }

    String last() {
      assertThat(messages).isNotEmpty();
      return messages.getLast();
    }
  }
}
