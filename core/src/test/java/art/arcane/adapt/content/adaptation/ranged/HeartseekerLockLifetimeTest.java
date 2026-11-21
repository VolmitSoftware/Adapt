package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.content.skill.SkillRanged;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HeartseekerLockLifetimeTest {
  @TempDir
  Path data;

  private TestAdapt adapt;
  private FakePlayer player;
  private RangedHeartseeker heartseeker;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Archer", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillRanged.class));
    adapt.join(player);
    heartseeker = (RangedHeartseeker) adapt.core().getAdaptServer().getSkillRegistry().getSkill("ranged")
        .getAdaptations().stream()
        .filter(RangedHeartseeker.class::isInstance)
        .findFirst()
        .orElseThrow();
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void lockOutlivesTheTimeoutWhileTheBowIsStillDrawn() throws ReflectiveOperationException {
    player.setHandRaised(true);
    player.setActiveItem(FakeItem.of("bow"));
    long acquired = System.currentTimeMillis() - 8_000L;
    putLock(acquired);

    expireLocks();

    assertThat(locks()).containsKey(player.uuid());
    assertThat(stamp(locks().get(player.uuid()))).isGreaterThan(acquired);
  }

  @Test
  void lockExpiresOnceTheBowIsLowered() throws ReflectiveOperationException {
    putLock(System.currentTimeMillis() - 8_000L);

    expireLocks();

    assertThat(locks()).doesNotContainKey(player.uuid());
  }

  private void putLock(long stamp) throws ReflectiveOperationException {
    Class<?> lockType = Class.forName(RangedHeartseeker.class.getName() + "$Lock");
    Constructor<?> constructor = lockType.getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    locks().put(player.uuid(), constructor.newInstance(null, stamp));
    Method enqueue = RangedHeartseeker.class.getDeclaredMethod("enqueueLock", UUID.class);
    enqueue.setAccessible(true);
    enqueue.invoke(heartseeker, player.uuid());
  }

  private void expireLocks() throws ReflectiveOperationException {
    Method expire = RangedHeartseeker.class.getDeclaredMethod("expireLocks");
    expire.setAccessible(true);
    expire.invoke(heartseeker);
  }

  @SuppressWarnings("unchecked")
  private Map<UUID, Object> locks() throws ReflectiveOperationException {
    Field field = RangedHeartseeker.class.getDeclaredField("locks");
    field.setAccessible(true);
    return (Map<UUID, Object>) field.get(heartseeker);
  }

  private long stamp(Object lock) throws ReflectiveOperationException {
    Method stamp = lock.getClass().getDeclaredMethod("stamp");
    stamp.setAccessible(true);
    return (long) stamp.invoke(lock);
  }
}
