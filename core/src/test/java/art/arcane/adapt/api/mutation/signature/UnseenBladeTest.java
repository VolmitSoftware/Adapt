package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.sword.upgraded.Witherblade;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Swords leading Stealth replaces Convergence with Unseen Blade. */
class UnseenBladeTest {
  private static final Id INVISIBILITY = Id.minecraft("invisibility");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Duelist", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillStealth.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void itReplacesSwordsLeadingStealthOnly() {
    kit.mutate("swords", "stealth");
    MutationAdaptation convergence = kit.adaptation("swords", "stealth", Recipe.CONVERGENCE);

    assertThat(convergence.getSignature()).isInstanceOf(UnseenBlade.class);
    assertThat(C.stripColor(convergence.getDisplayName())).isEqualTo("Unseen Blade");
    assertThat(convergence.getCooldownMillis(1)).isEqualTo(20_000L);
    assertThat(kit.mutated("stealth", "swords").getMutationAdaptation(Recipe.CONVERGENCE).getSignature())
        .isNotInstanceOf(UnseenBlade.class);
  }

  @Test
  void aCritFromStealthStopsHealingBleedsAndTheKillHidesYou() {
    kit.mutate("swords", "stealth");
    kit.learn("swords", "stealth", Recipe.CONVERGENCE, 3);
    StealthSignatureMob mob = StealthSignatureMob.at(platform.world(), 2.5D, 61D, 0.5D);
    player.setSneaking(true);

    kit.hit(player, mob, 6D, true);
    platform.scheduler().advance(20);

    assertThat(mob.damageTaken()).isGreaterThan(0D);
    assertThat(adapt.post(new EntityRegainHealthEvent(mob, 2D, EntityRegainHealthEvent.Reason.REGEN)).isCancelled()).isTrue();

    adapt.post(new EntityDeathEvent(mob, player, player, player, DamageCause.ENTITY_ATTACK, List.of(), 0));

    assertThat(player.hasEffect(INVISIBILITY)).isTrue();
    StealthSignatureMob other = StealthSignatureMob.at(platform.world(), -2.5D, 61D, 0.5D);
    assertThat(adapt.post(new EntityTargetEvent(other, player, EntityTargetEvent.Reason.CLOSEST_PLAYER)).isCancelled())
        .isTrue();
  }

  @Test
  void aSeenCritDoesNothing() {
    kit.mutate("swords", "stealth");
    kit.learn("swords", "stealth", Recipe.CONVERGENCE, 3);
    StealthSignatureMob mob = StealthSignatureMob.at(platform.world(), 2.5D, 61D, 0.5D);
    player.setSneaking(false);

    kit.hit(player, mob, 6D, true);
    platform.scheduler().advance(20);

    assertThat(mob.damageTaken()).isZero();
  }

  @Test
  void stealthLeadingSwordsAndAPlayerWithoutTheMutationGetNothing() {
    StealthSignatureMob mob = StealthSignatureMob.at(platform.world(), 2.5D, 61D, 0.5D);
    player.setSneaking(true);
    kit.hit(player, mob, 6D, true);
    kit.mutate("stealth", "swords");
    kit.learn("stealth", "swords", Recipe.CONVERGENCE, 3);
    kit.hit(player, mob, 6D, true);
    platform.scheduler().advance(20);

    assertThat(mob.damageTaken()).isZero();
  }

  @Test
  void aSecondCritWithinTheCooldownDoesNothing() {
    kit.mutate("swords", "stealth");
    kit.learn("swords", "stealth", Recipe.CONVERGENCE, 1);
    StealthSignatureMob first = StealthSignatureMob.at(platform.world(), 2.5D, 61D, 0.5D);
    StealthSignatureMob second = StealthSignatureMob.at(platform.world(), 2.5D, 61D, 2.5D);
    player.setSneaking(true);

    kit.hit(player, first, 6D, true);
    kit.hit(player, second, 6D, true);
    platform.scheduler().advance(20);

    assertThat(first.damageTaken()).isGreaterThan(0D);
    assertThat(second.damageTaken()).isZero();
  }

  @Test
  void theWoundLastsTwiceWitherbladesTimeAtEachLevel() {
    kit.mutate("swords", "stealth");
    Witherblade witherblade = witherblade();

    for (int level = 1; level <= 3; level++) {
      assertThat(UnseenBlade.noHealMillis(witherblade, level)).isEqualTo(witherblade.getDurationMillis(level) * 2L);
    }
    assertThat(UnseenBlade.noHealMillis(witherblade, 3)).isGreaterThan(UnseenBlade.noHealMillis(witherblade, 1));
  }

  @Test
  void withWitherbladeDisabledTheStrikeDoesNothing() {
    kit.mutate("swords", "stealth");
    kit.learn("swords", "stealth", Recipe.CONVERGENCE, 3);
    ((AdaptationConfig) witherblade().getConfig()).enabled = false;
    StealthSignatureMob mob = StealthSignatureMob.at(platform.world(), 2.5D, 61D, 0.5D);
    player.setSneaking(true);

    kit.hit(player, mob, 6D, true);
    platform.scheduler().advance(20);

    assertThat(mob.damageTaken()).isZero();
  }

  @Test
  void witherbladesTargetChoiceLimitsTheStrike() {
    kit.mutate("swords", "stealth");
    kit.learn("swords", "stealth", Recipe.CONVERGENCE, 3);
    profile.getData().getSkillLine("swords").setAdaptation(witherblade(), 1);
    profile.getData().getPreferences().set("upgraded-swords-witherblade", "targets", "NON_PLAYERS");
    FakePlayer rival = platform.player("Rival", 2.5D, 61D, 0.5D);
    player.setSneaking(true);

    kit.hit(player, rival, 6D, true);
    platform.scheduler().advance(20);

    assertThat(rival.damageTaken()).isZero();
  }

  @Test
  void theKillDoesNotHideYouWhileAnotherMobHuntsYou() {
    kit.mutate("swords", "stealth");
    kit.learn("swords", "stealth", Recipe.CONVERGENCE, 3);
    StealthSignatureMob mob = StealthSignatureMob.at(platform.world(), 2.5D, 61D, 0.5D);
    StealthSignatureMob hunter = StealthSignatureMob.at(platform.world(), -3.5D, 61D, 0.5D);
    player.setSneaking(true);

    kit.hit(player, mob, 6D, true);
    hunter.setTarget(player);
    adapt.post(new EntityDeathEvent(mob, player, player, player, DamageCause.ENTITY_ATTACK, List.of(), 0));

    assertThat(player.hasEffect(INVISIBILITY)).isFalse();
  }

  private Witherblade witherblade() {
    return kit.registry().getSkill("swords").getUpgrades().stream().filter(Witherblade.class::isInstance)
        .map(Witherblade.class::cast).findFirst().orElseThrow();
  }
}
