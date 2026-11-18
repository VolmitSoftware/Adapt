package art.arcane.adapt.util.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import art.arcane.adapt.AdaptTestBase;
import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ConfigMapOrderStabilityTest extends AdaptTestBase {
  private static final String GOLEMANCY = "art.arcane.adapt.content.adaptation.golemancy.";

  static Stream<String> configOwners() throws Exception {
    List<String> owners = new ArrayList<>(List.of("art.arcane.adapt.content.skill.SkillGolemancy"));
    File dir = new File("src/main/java/art/arcane/adapt/content/adaptation/golemancy");
    for (File source : dir.listFiles()) {
      String name = source.getName().replace(".java", "");
      if (name.startsWith("Golem") && Files.readString(source.toPath()).contains("class Config")) {
        owners.add(GOLEMANCY + name);
      }
    }
    return owners.stream().sorted();
  }

  @ParameterizedTest
  @MethodSource("configOwners")
  void defaultConfigIsNotRewrittenOnTheNextLoad(String owner) throws Exception {
    Class<?> type = Class.forName(owner + "$Config");
    Constructor<?> constructor = type.getDeclaredConstructor();
    constructor.setAccessible(true);
    File file = new File(dataFolder, "config.toml");

    writeDefaults(file, constructor.newInstance());
    String written = Files.readString(file.toPath());
    load(file, type, constructor.newInstance());
    String reloaded = Files.readString(file.toPath());
    load(file, type, constructor.newInstance());

    assertEquals(written, reloaded, owner + " was rewritten on its second load");
    assertEquals(reloaded, Files.readString(file.toPath()), owner + " was rewritten on its third load");
  }

  private static void writeDefaults(File file, Object config) throws Exception {
    ConfigFileSupport.writeDefaults(file, config, "adaptation:stability", null);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void load(File file, Class type, Object fallback) throws Exception {
    ConfigFileSupport.load(file, null, type, fallback, true, "adaptation:stability", "created", null, true);
  }
}
