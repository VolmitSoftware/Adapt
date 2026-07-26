package art.arcane.adapt.api.ability;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.Protector;
import org.junit.jupiter.api.Test;

import java.io.File;
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
    File directory = new File(location(), PACKAGE.replace('.', '/'));
    File[] files = directory.listFiles((ignored, name) -> name.endsWith(".class"));

    assertThat(files).isNotNull();

    List<Class<?>> types = new ArrayList<>(files.length);

    for (File file : files) {
      String name = PACKAGE + "." + file.getName().substring(0, file.getName().length() - ".class".length());

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

  private static File location() {
    try {
      return new File(AbilityUsePolicy.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    } catch (Exception error) {
      throw new IllegalStateException("the ability API classes must be on a directory classpath for this gate", error);
    }
  }

  private static Set<Class<?>> referencedTypes(Class<?> type) {
    Set<Class<?>> out = new LinkedHashSet<>();

    for (Method method : type.getDeclaredMethods()) {
      if (!visible(method.getModifiers()) || method.isSynthetic()) {
        continue;
      }

      collect(method.getGenericReturnType(), out);
      collectAll(method, out);
    }

    for (Constructor<?> constructor : type.getDeclaredConstructors()) {
      if (visible(constructor.getModifiers())) {
        collectAll(constructor, out);
      }
    }

    for (Field field : type.getDeclaredFields()) {
      if (visible(field.getModifiers()) && !field.isSynthetic()) {
        collect(field.getGenericType(), out);
      }
    }

    return out;
  }

  private static void collectAll(Executable executable, Set<Class<?>> out) {
    for (Type parameter : executable.getGenericParameterTypes()) {
      collect(parameter, out);
    }

    for (Type thrown : executable.getGenericExceptionTypes()) {
      collect(thrown, out);
    }
  }

  private static boolean visible(int modifiers) {
    return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
  }

  private static void collect(Type type, Set<Class<?>> out) {
    switch (type) {
      case Class<?> raw -> out.add(component(raw));
      case ParameterizedType parameterized -> {
        collect(parameterized.getRawType(), out);

        for (Type argument : parameterized.getActualTypeArguments()) {
          collect(argument, out);
        }
      }
      case GenericArrayType array -> collect(array.getGenericComponentType(), out);
      case WildcardType wildcard -> {
        for (Type bound : wildcard.getUpperBounds()) {
          collect(bound, out);
        }

        for (Type bound : wildcard.getLowerBounds()) {
          collect(bound, out);
        }
      }
      case TypeVariable<?> variable -> {
        for (Type bound : variable.getBounds()) {
          collect(bound, out);
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
