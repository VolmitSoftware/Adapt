package art.arcane.adapt.api.ability;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.Protector;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

class PublicSurfacePurityTest {
  private static final String PACKAGE = "art.arcane.adapt.api.ability";

  private static final List<String> FORBIDDEN_ON_THE_ABILITY_PACKAGE = List.of("art.arcane.adapt.api.ability.internal",
      "art.arcane.adapt.api.adaptation", "art.arcane.adapt.content", "art.arcane.adapt.service",
      "art.arcane.adapt.util", "art.arcane.volmlib", "net.kyori", "com.francobm", "com.fren_gor", "fr.skytasul",
      "com.jeff_media", "net.byteflux");

  @Test
  void theAbilityApiPackageNeverNamesAnInternalOrRelocatedType() {
    List<Class<?>> types = publicAbilityTypes();

    assertThat(types).hasSizeGreaterThanOrEqualTo(18);

    Set<String> offenders = new TreeSet<>();

    for (Class<?> type : types) {
      for (Class<?> referenced : referencedTypes(type)) {
        String name = referenced.getName();

        for (String forbidden : FORBIDDEN_ON_THE_ABILITY_PACKAGE) {
          if (name.startsWith(forbidden + ".")) {
            offenders.add(type.getName() + " -> " + name);
          }
        }
      }
    }

    assertThat(offenders).isEmpty();
  }

  @Test
  void thePublicInterfacesThisApiExtendedNeverNameATypeFromAnInternalPackage() {
    Set<String> offenders = new TreeSet<>();

    for (Class<?> type : List.of(Adaptation.class, Protector.class)) {
      for (Class<?> referenced : referencedTypes(type)) {
        if (referenced.getName().contains(".internal.")) {
          offenders.add(type.getName() + " -> " + referenced.getName());
        }
      }
    }

    assertThat(offenders).isEmpty();
  }

  private static List<Class<?>> publicAbilityTypes() {
    Set<String> names = new TreeSet<>();
    for (Class<?> anchor : List.of(AbilityUsePolicy.class, AbilityOutcome.class)) {
      names.addAll(classNames(location(anchor)));
    }

    assertThat(names).isNotEmpty();

    List<Class<?>> types = new ArrayList<>(names.size());

    for (String name : names) {
      try {
        Class<?> type = Class.forName(name);

        if (Modifier.isPublic(type.getModifiers())) {
          types.add(type);
        }
      } catch (ClassNotFoundException error) {
        throw new IllegalStateException("compiled class " + name + " is not loadable", error);
      }
    }

    return types;
  }

  private static Set<String> classNames(File location) {
    String prefix = PACKAGE.replace('.', '/') + "/";
    Set<String> names = new TreeSet<>();
    if (location.isDirectory()) {
      File[] files = new File(location, prefix).listFiles((ignored, name) -> name.endsWith(".class"));
      if (files != null) {
        for (File file : files) {
          names.add(PACKAGE + "." + file.getName().substring(0, file.getName().length() - ".class".length()));
        }
      }
      return names;
    }
    try (JarFile jar = new JarFile(location)) {
      jar.stream()
          .map(JarEntry::getName)
          .filter(entry -> entry.startsWith(prefix) && entry.endsWith(".class") && entry.indexOf('/', prefix.length()) < 0)
          .forEach(entry -> names.add(entry.substring(0, entry.length() - ".class".length()).replace('/', '.')));
    } catch (IOException error) {
      throw new IllegalStateException("cannot read " + location, error);
    }
    return names;
  }

  private static File location(Class<?> anchor) {
    try {
      return new File(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
    } catch (Exception error) {
      throw new IllegalStateException("the ability API classes must be on the classpath for this gate", error);
    }
  }

  private static Set<Class<?>> referencedTypes(Class<?> type) {
    Set<Class<?>> out = new LinkedHashSet<>();
    Set<Type> visited = new LinkedHashSet<>();

    for (Method method : type.getDeclaredMethods()) {
      if (!visible(method.getModifiers()) || method.isSynthetic()) {
        continue;
      }

      collect(method.getGenericReturnType(), out, visited);
      collectAll(method, out, visited);
    }

    for (Constructor<?> constructor : type.getDeclaredConstructors()) {
      if (visible(constructor.getModifiers())) {
        collectAll(constructor, out, visited);
      }
    }

    for (Field field : type.getDeclaredFields()) {
      if (visible(field.getModifiers()) && !field.isSynthetic()) {
        collect(field.getGenericType(), out, visited);
      }
    }

    return out;
  }

  private static void collectAll(Executable executable, Set<Class<?>> out, Set<Type> visited) {
    for (Type parameter : executable.getGenericParameterTypes()) {
      collect(parameter, out, visited);
    }

    for (Type thrown : executable.getGenericExceptionTypes()) {
      collect(thrown, out, visited);
    }
  }

  private static boolean visible(int modifiers) {
    return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
  }

  private static void collect(Type type, Set<Class<?>> out, Set<Type> visited) {
    if (!visited.add(type)) {
      return;
    }
    switch (type) {
      case Class<?> raw -> out.add(component(raw));
      case ParameterizedType parameterized -> {
        collect(parameterized.getRawType(), out, visited);

        for (Type argument : parameterized.getActualTypeArguments()) {
          collect(argument, out, visited);
        }
      }
      case GenericArrayType array -> collect(array.getGenericComponentType(), out, visited);
      case WildcardType wildcard -> {
        for (Type bound : wildcard.getUpperBounds()) {
          collect(bound, out, visited);
        }

        for (Type bound : wildcard.getLowerBounds()) {
          collect(bound, out, visited);
        }
      }
      case TypeVariable<?> variable -> {
        for (Type bound : variable.getBounds()) {
          collect(bound, out, visited);
        }
      }
      default -> {
      }
    }
  }

  private static Class<?> component(Class<?> type) {
    Class<?> raw = type;

    while (raw.isArray()) {
      raw = raw.getComponentType();
    }

    return raw;
  }
}
