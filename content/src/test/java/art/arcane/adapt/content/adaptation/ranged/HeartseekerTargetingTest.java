package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.EngineTestBase;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.engine.adaptation.EntityGuardPort;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeartseekerTargetingTest extends EngineTestBase {
  private RangedHeartseeker adaptation;
  private FakeProjectile arrow;
  private EntityDamageByEntityEvent event;
  private FakeLiving target;
  private boolean shieldTarget;

  @BeforeEach
  void installEntityGuard() {
    AdaptPorts.install(EntityGuardPort.class, new EntityGuardPort() {
      @Override
      public boolean isShielded(EntityRef entity) {
        return shieldTarget && entity.equals(target);
      }
    });
  }

  @AfterEach
  void uninstallEntityGuard() {
    AdaptPorts.uninstall(EntityGuardPort.class);
  }

  @BeforeEach
  void prepare() throws ReflectiveOperationException {
    adaptation = TestContent.adaptation(Id.adapt("ranged"), RangedHeartseeker::new);
    adaptation.getConfig().ignorePassiveMobs = true;
    FakeWorld world = new FakeWorld("world");
    arrow = new FakeProjectile(Id.minecraft("arrow"), new Loc(world, 0D, 64D, 0D));
    PlayerRef owner = mock(PlayerRef.class);
    when(owner.uuid()).thenReturn(UUID.randomUUID());
    arrow.setShooter(owner);
    seeking().set(arrow, true);
    target = new FakeLiving(Id.minecraft("cow"), new Loc(world, 2D, 64D, 0D));
    target.trait(EntityTrait.ANIMAL);
    event = new EntityDamageByEntityEvent(target, arrow, owner, DamageCause.PROJECTILE, Id.minecraft("arrow"), 4D,
        false, FinalDamage.IDENTITY);
  }

  @Test
  void seekingArrowCannotDamageProtectedCow() {
    adaptation.protectPassiveMobs(event);
    assertThat(event.isCancelled()).isTrue();
  }

  @Test
  void ordinaryArrowCanStillDamageCow() throws ReflectiveOperationException {
    seeking().remove(arrow);
    adaptation.protectPassiveMobs(event);
    assertThat(event.isCancelled()).isFalse();
  }

  @Test
  void disabledProtectionPreservesSeekingArrowDamage() {
    adaptation.getConfig().ignorePassiveMobs = false;
    adaptation.protectPassiveMobs(event);
    assertThat(event.isCancelled()).isFalse();
  }

  @Test
  void passiveTargetsAreRejectedBeforeLockAndReseekSnapshots() throws ReflectiveOperationException {
    LivingRef passive = mock(LivingRef.class);
    Method capture = RangedHeartseeker.class.getDeclaredMethod("captureTargetOwned", UUID.class, LivingRef.class);
    capture.setAccessible(true);
    assertThat(capture.invoke(adaptation, UUID.randomUUID(), passive)).isNull();
    verify(passive, never()).location();
  }

  @Test
  void summonedMobIsProtectedWithToggleDisabled() {
    adaptation.getConfig().ignorePassiveMobs = false;
    shieldTarget = true;
    adaptation.protectPassiveMobs(event);
    assertThat(event.isCancelled()).isTrue();
  }

  @SuppressWarnings("unchecked")
  private static ProjectileMemo<Boolean> seeking() throws ReflectiveOperationException {
    Field field = RangedHeartseeker.class.getDeclaredField("SEEKING");
    field.setAccessible(true);
    return (ProjectileMemo<Boolean>) field.get(null);
  }
}
