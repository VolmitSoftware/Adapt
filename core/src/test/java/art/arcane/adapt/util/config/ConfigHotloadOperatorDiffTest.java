package art.arcane.adapt.util.config;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

class ConfigHotloadOperatorDiffTest extends AdaptTestBase {
  @Test
  void operatorDiffIsParsedOffTheCallingThreadAndSentFromTheServerThread() throws Exception {
    ConfigHotload hotload = core.getHotload();
    Method notify = ConfigHotload.class.getDeclaredMethod("notifyOps", File.class, String.class, String.class);
    notify.setAccessible(true);
    List<Runnable> async = new ArrayList<>();
    List<Runnable> sync = new ArrayList<>();

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(() -> Tasks.a(any(Runnable.class))).thenAnswer(call -> async.add(call.getArgument(0)));
      scheduling.when(() -> Tasks.s(any(Runnable.class))).thenAnswer(call -> sync.add(call.getArgument(0)));

      notify.invoke(hotload, new File(dataFolder, "languages/en_US.toml"), "[a]\nkey = \"old\"\n", "[a]\nkey = \"new\"\n");

      assertThat(async).hasSize(1);
      assertThat(sync).isEmpty();

      async.getFirst().run();

      assertThat(sync).hasSize(1);
    }
  }
}
