package art.arcane.adapt.content.adaptation.sword.effects;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeEntity;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.RecordingFx;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DamagingBleedEffectVisualTest {
  private static final Id CRIMSON_ROOTS = Id.minecraft("crimson_roots");
  private static final Id LARGE_FERN = Id.minecraft("large_fern");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakeWorld world;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    adapt = TestAdapt.start(platform, List.of());
    world = platform.world();
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void damagingBleedPlaysTheBlockBreakEffectAtTheEyeBlockAndPulsesDamageEachRun() {
    FakePlayer caster = new FakePlayer("Caster", new Loc(world, 0, 64, 0));
    FakeLiving target = new FakeLiving(Id.minecraft("zombie"), new Loc(world, 2.5D, 64D, 0.5D));

    new DamagingBleedEffect(new DamagingBleedEffect.DamageContext(1.25D, target, caster)).start(CRIMSON_ROOTS, 3, 5);

    assertThat(platform.fx().blockBreaks()).isEmpty();
    platform.scheduler().advance(1);
    assertThat(platform.fx().blockBreaks()).hasSize(1);
    assertThat(target.damageTaken()).isEqualTo(1.25D);
    platform.scheduler().advance(4);
    assertThat(platform.fx().blockBreaks()).hasSize(1);
    platform.scheduler().advance(1);
    assertThat(platform.fx().blockBreaks()).hasSize(2);
    platform.scheduler().advance(40);

    List<RecordingFx.BlockBreak> breaks = platform.fx().blockBreaks();
    assertThat(breaks).hasSize(3);
    for (RecordingFx.BlockBreak bleed : breaks) {
      assertThat(bleed.world()).isSameAs(world);
      assertThat(bleed.state().type()).isEqualTo(CRIMSON_ROOTS);
      assertThat(bleed.block().x()).isEqualTo(2);
      assertThat(bleed.block().z()).isEqualTo(0);
      assertThat(bleed.block().y()).isIn(64, 65);
    }
    assertThat(target.damageTaken()).isEqualTo(3.75D);
    assertThat(target.lastDamageType()).isEqualTo(Id.minecraft("magic"));
    assertThat(target.lastDamageSource()).isSameAs(caster);
    assertThat(platform.fx().particles()).isEmpty();
  }

  @Test
  void visualBleedDealsNoDamage() {
    FakeLiving target = new FakeLiving(Id.minecraft("pig"), new Loc(world, 0.5D, 70D, 0.5D));

    DamagingBleedEffect.startVisual(target, LARGE_FERN, 4, 5);
    platform.scheduler().advance(60);

    assertThat(platform.fx().blockBreaks()).hasSize(4)
        .allSatisfy((RecordingFx.BlockBreak bleed) -> assertThat(bleed.state().type()).isEqualTo(LARGE_FERN));
    assertThat(target.damageTaken()).isZero();
  }

  @Test
  void nonLivingTargetsBleedFromTheirFeetPosition() {
    FakeEntity target = new FakeEntity(Id.minecraft("armor_stand"), new Loc(world, 1.5D, 70.2D, 3.5D));

    DamagingBleedEffect.startVisual(target, LARGE_FERN, 2, 5);
    platform.scheduler().advance(20);

    assertThat(platform.fx().blockBreaks()).hasSize(2)
        .allSatisfy((RecordingFx.BlockBreak bleed) -> assertThat(bleed.block().y()).isIn(69, 70));
  }

  @Test
  void bleedStopsOnceTheTargetDies() {
    FakeLiving target = new FakeLiving(Id.minecraft("zombie"), new Loc(world, 0.5D, 64D, 0.5D));

    DamagingBleedEffect.startVisual(target, LARGE_FERN, 10, 5);
    platform.scheduler().advance(6);
    target.setDead(true);
    platform.scheduler().advance(60);

    assertThat(platform.fx().blockBreaks()).hasSize(2);
  }
}
