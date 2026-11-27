package art.arcane.adapt.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the published surface: every public top-level type declares exactly one stability tier, and no public or
 * protected signature of a stable or experimental type names an internal type or anything outside the API packages.
 * The tier annotations are class-retention, so tiers are read from the sources and signatures from the compiled classes.
 */
class ApiSurfaceTest {
  private static final Path SOURCES = Paths.get("src", "main", "java");
  private static final String INTERNAL_PACKAGE = "art.arcane.adapt.api.internal";
  private static final Pattern DECLARATION = Pattern.compile(
      "(?m)^((?:public|abstract|final|sealed|non-sealed|static|strictfp)\\s+)*(class|interface|enum|record|@interface)\\s+(\\w+)");
  private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;");
  private static final Pattern TIER = Pattern.compile("@ApiStatus\\.(AvailableSince|Experimental|Internal)\\b");
  private static final Pattern COMMENT = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\n]*");

  private static final Map<String, List<String>> TIERS = new HashMap<>();
  private static final Set<String> PUBLIC_TYPES = new HashSet<>();

  @BeforeAll
  static void readSources() throws IOException {
    List<Path> files;
    try (Stream<Path> walk = Files.walk(SOURCES)) {
      files = walk.filter(path -> path.toString().endsWith(".java"))
          .filter(path -> !path.getFileName().toString().equals("package-info.java"))
          .toList();
    }
    for (Path file : files) {
      String source = Files.readString(file, StandardCharsets.UTF_8);
      Matcher packageMatch = PACKAGE.matcher(source);
      Matcher declaration = DECLARATION.matcher(source);
      if (!packageMatch.find() || !declaration.find()) {
        continue;
      }
      String name = packageMatch.group(1) + "." + declaration.group(3);
      String head = source.substring(0, declaration.start());
      String annotations = COMMENT.matcher(head.substring(head.lastIndexOf(';') + 1)).replaceAll(" ");
      List<String> tiers = new ArrayList<>();
      Matcher tier = TIER.matcher(annotations);
      while (tier.find()) {
        tiers.add(tier.group(1));
      }
      TIERS.put(name, tiers);
      String modifiers = source.substring(declaration.start(), declaration.start(2));
      if (modifiers.matches("(?s).*\\bpublic\\b.*")) {
        PUBLIC_TYPES.add(name);
      }
    }
  }

  @Test
  void everyPublicTypeDeclaresExactlyOneTier() {
    Set<String> missing = new TreeSet<>();
    for (String name : PUBLIC_TYPES) {
      List<String> tiers = TIERS.get(name);
      if (tiers.size() != 1) {
        missing.add(name + " " + tiers);
      }
    }

    assertThat(PUBLIC_TYPES).hasSizeGreaterThan(400);
    assertThat(missing)
        .as("public types need exactly one of @ApiStatus.AvailableSince(\"1.0.0\"), @ApiStatus.Experimental, @ApiStatus.Internal")
        .isEmpty();
  }

  @Test
  void typesInTheInternalPackageAreInternal() {
    Set<String> wrong = new TreeSet<>();
    for (Map.Entry<String, List<String>> entry : TIERS.entrySet()) {
      if (entry.getKey().startsWith(INTERNAL_PACKAGE + ".") && !entry.getValue().equals(List.of("Internal"))) {
        wrong.add(entry.getKey() + " " + entry.getValue());
      }
    }

    assertThat(wrong).isEmpty();
  }

  @Test
  void publishedSignaturesExposeOnlyPublishedTypes() throws IOException, URISyntaxException, ClassNotFoundException {
    Path classes = Paths.get(AdaptApi.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    List<String> names;
    try (Stream<Path> walk = Files.walk(classes)) {
      names = walk.filter(path -> path.toString().endsWith(".class"))
          .map(path -> classes.relativize(path).toString())
          .map(path -> path.substring(0, path.length() - ".class".length()).replace('/', '.').replace('\\', '.'))
          .filter(name -> !name.endsWith("package-info"))
          .sorted()
          .toList();
    }
    ClassLoader loader = AdaptApi.class.getClassLoader();
    Set<String> leaks = new TreeSet<>();
    int checked = 0;
    for (String name : names) {
      Class<?> type = Class.forName(name, false, loader);
      if (!isExported(type) || isInternal(type)) {
        continue;
      }
      checked++;
      String owner = type.getName();
      check(leaks, owner + " extends", type.getGenericSuperclass());
      for (Type supertype : type.getGenericInterfaces()) {
        check(leaks, owner + " implements", supertype);
      }
      for (TypeVariable<?> variable : type.getTypeParameters()) {
        check(leaks, owner + "<" + variable.getName() + ">", variable);
      }
      for (Field field : type.getDeclaredFields()) {
        if (visible(field)) {
          check(leaks, owner + "." + field.getName(), field.getGenericType());
        }
      }
      for (Constructor<?> constructor : type.getDeclaredConstructors()) {
        if (visible(constructor)) {
          for (Type parameter : constructor.getGenericParameterTypes()) {
            check(leaks, owner + ".<init>", parameter);
          }
        }
      }
      for (Method method : type.getDeclaredMethods()) {
        if (!visible(method) || method.isBridge()) {
          continue;
        }
        String where = owner + "." + method.getName() + "()";
        check(leaks, where, method.getGenericReturnType());
        for (Type parameter : method.getGenericParameterTypes()) {
          check(leaks, where, parameter);
        }
        for (Type thrown : method.getGenericExceptionTypes()) {
          check(leaks, where, thrown);
        }
        for (TypeVariable<?> variable : method.getTypeParameters()) {
          check(leaks, where, variable);
        }
      }
    }

    assertThat(checked).isGreaterThan(400);
    assertThat(leaks).as("public or protected signatures of published types name unpublished types").isEmpty();
  }

  private static boolean visible(Member member) {
    return !member.isSynthetic() && (Modifier.isPublic(member.getModifiers()) || Modifier.isProtected(member.getModifiers()));
  }

  private static boolean isExported(Class<?> type) {
    if (type.isAnonymousClass() || type.isLocalClass() || type.isSynthetic()) {
      return false;
    }
    Class<?> enclosing = type.getDeclaringClass();
    if (enclosing == null) {
      return Modifier.isPublic(type.getModifiers());
    }
    int modifiers = type.getModifiers();
    return (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) && isExported(enclosing);
  }

  private static Class<?> topLevel(Class<?> type) {
    Class<?> current = type;
    while (current.getEnclosingClass() != null) {
      current = current.getEnclosingClass();
    }
    return current;
  }

  private static boolean isInternal(Class<?> type) {
    Class<?> top = topLevel(type);
    if (top.getName().startsWith(INTERNAL_PACKAGE + ".")) {
      return true;
    }
    List<String> tiers = TIERS.get(top.getName());
    return tiers != null && tiers.contains("Internal");
  }

  private static boolean isPublished(Class<?> type) {
    if (type.isPrimitive()) {
      return true;
    }
    String name = type.getName();
    if (name.startsWith("java.")) {
      return true;
    }
    if (!name.startsWith("art.arcane.adapt.api.") && !name.startsWith("art.arcane.adapt.platform.")) {
      return false;
    }
    return isExported(type) && !isInternal(type);
  }

  private static void check(Set<String> leaks, String where, Type type) {
    collect(leaks, where, type, new HashSet<>());
  }

  private static void collect(Set<String> leaks, String where, Type type, Set<Type> seen) {
    if (type == null || !seen.add(type)) {
      return;
    }
    if (type instanceof Class<?> raw) {
      Class<?> element = raw;
      while (element.isArray()) {
        element = element.getComponentType();
      }
      if (!isPublished(element)) {
        leaks.add(where + " -> " + element.getName());
      }
    } else if (type instanceof ParameterizedType parameterized) {
      collect(leaks, where, parameterized.getRawType(), seen);
      collect(leaks, where, parameterized.getOwnerType(), seen);
      for (Type argument : parameterized.getActualTypeArguments()) {
        collect(leaks, where, argument, seen);
      }
    } else if (type instanceof WildcardType wildcard) {
      for (Type bound : wildcard.getUpperBounds()) {
        collect(leaks, where, bound, seen);
      }
      for (Type bound : wildcard.getLowerBounds()) {
        collect(leaks, where, bound, seen);
      }
    } else if (type instanceof TypeVariable<?> variable) {
      for (Type bound : variable.getBounds()) {
        collect(leaks, where, bound, seen);
      }
    } else if (type instanceof GenericArrayType array) {
      collect(leaks, where, array.getGenericComponentType(), seen);
    }
  }
}
