package art.arcane.adapt.content;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ContentCatalogLinkageTest {
  private static final String CONTENT_PACKAGE = "art.arcane.adapt.content";
  private static final List<String> REMOVED_PREFIXES = List.of(
      "manifold.",
      "art.arcane.chrono.",
      "art.arcane.spatial.",
      "art.arcane.curse.",
      "art.arcane.amulet.",
      "art.arcane.fukkit.",
      "Fukkit.extensions.",
      "Amulet.extensions."
  );

  @Test
  void everyContentClassLinksWithoutRemovedLibraries() throws Exception {
    List<String> names = contentClassNames();
    List<String> broken = new ArrayList<>();

    for (String name : names) {
      try {
        Class<?> type = load(name);
        type.getDeclaredFields();
        type.getDeclaredMethods();
        type.getDeclaredConstructors();
        type.getGenericSuperclass();
        type.getGenericInterfaces();
      } catch (LinkageError | TypeNotPresentException | ClassNotFoundException error) {
        String missing = String.valueOf(error.getMessage()).replace('/', '.');
        if (REMOVED_PREFIXES.stream().anyMatch(missing::contains)) {
          broken.add(name + " needs " + missing);
        }
      }
    }

    assertThat(names).hasSizeGreaterThan(400);
    assertThat(broken).isEmpty();
  }

  @Test
  void skillRegistryRegistersEveryConcreteSkillExactlyOnce() throws Exception {
    List<String> registered = registeredSkillNames();
    Set<String> concrete = new TreeSet<>();
    for (Class<?> type : contentClasses(Skill.class)) {
      concrete.add(type.getSimpleName());
    }

    assertThat(registered).doesNotHaveDuplicates();
    assertThat(new TreeSet<>(registered)).isEqualTo(concrete);
    assertThat(registered).hasSize(24);
  }

  @Test
  void everyRegisteredSkillHasThePublicNoArgumentConstructorTheRegistryCalls() throws Exception {
    for (String simpleName : registeredSkillNames()) {
      Class<?> type = load(CONTENT_PACKAGE + ".skill." + simpleName);
      Constructor<?> constructor = type.getConstructor();

      assertThat(Skill.class).as(simpleName).isAssignableFrom(type);
      assertThat(Modifier.isPublic(type.getModifiers())).as(simpleName).isTrue();
      assertThat(Modifier.isAbstract(type.getModifiers())).as(simpleName).isFalse();
      assertThat(Modifier.isPublic(constructor.getModifiers())).as(simpleName).isTrue();
      assertThat(configurationType(type)).as("%s configuration", simpleName).isNotNull();
    }
  }

  @Test
  void everyConcreteAdaptationExposesAPublicConstructorAndConfiguration() throws Exception {
    List<Class<?>> adaptations = contentClasses(Adaptation.class);

    assertThat(adaptations).hasSizeGreaterThanOrEqualTo(330);
    for (Class<?> type : adaptations) {
      assertThat(type.getConstructors()).as(type.getName()).isNotEmpty();
      assertThat(configurationType(type)).as("%s configuration", type.getName()).isNotNull();
    }
  }

  private static Class<?> configurationType(Class<?> type) throws NoSuchMethodException {
    Class<?> current = type;
    while (current != null && current != Object.class) {
      Type parent = current.getGenericSuperclass();
      if (parent instanceof ParameterizedType parameterized
          && parameterized.getActualTypeArguments()[0] instanceof Class<?> configuration) {
        configuration.getDeclaredConstructor();
        return configuration;
      }
      current = current.getSuperclass();
    }
    return null;
  }

  private static List<String> registeredSkillNames() {
    List<String> names = new ArrayList<>();
    for (BuiltinAddon.Entry skill : BuiltinAddon.skills()) {
      names.add(skill.type().getSimpleName());
    }
    return names;
  }

  private static List<Class<?>> contentClasses(Class<?> assignableTo) throws Exception {
    List<Class<?>> types = new ArrayList<>();
    for (String name : contentClassNames()) {
      Class<?> type = load(name);
      if (assignableTo.isAssignableFrom(type) && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
        types.add(type);
      }
    }
    return types;
  }

  private static Class<?> load(String name) throws ClassNotFoundException {
    return Class.forName(name, false, ContentCatalogLinkageTest.class.getClassLoader());
  }

  private static List<String> contentClassNames() throws IOException, URISyntaxException {
    Set<String> names = new TreeSet<>();
    for (Class<?> anchor : List.of(Adapt.class, BuiltinAddon.class)) {
      Path location = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
      if (Files.isDirectory(location)) {
        collect(location, names);
        continue;
      }
      try (FileSystem jar = FileSystems.newFileSystem(location)) {
        collect(jar.getPath("/"), names);
      }
    }
    return List.copyOf(names);
  }

  private static void collect(Path classes, Set<String> names) throws IOException {
    Path content = classes.resolve(CONTENT_PACKAGE.replace('.', '/'));
    if (!Files.isDirectory(content)) {
      return;
    }
    try (Stream<Path> files = Files.walk(content)) {
      files.filter(path -> path.toString().endsWith(".class"))
          .map(path -> classes.relativize(path).toString())
          .map(path -> path.substring(0, path.length() - ".class".length()).replace(path.contains("\\") ? '\\' : '/', '.'))
          .forEach(names::add);
    }
  }
}
