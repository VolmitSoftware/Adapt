package art.arcane.adapt.util.common.compat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fails when Bukkit-module code outside the Paper boundary links against a class, method or field that Spigot's API
 * lacks. Paper-only code belongs in the compat package or in one of the classes below, each of which is only loaded
 * after {@link PaperApi#isPaper()} returned true (or, for region respawns, on Folia's regionized scheduler).
 */
class SpigotLinkageTest {
  private static final List<String> PAPER_ONLY = List.of(
      "art/arcane/adapt/util/common/compat/",
      "art/arcane/adapt/bukkit/feature/ai/",
      "art/arcane/adapt/bukkit/event/EndermanTranslations",
      "art/arcane/adapt/bukkit/event/Paper121Translations",
      "art/arcane/adapt/bukkit/event/Paper26Translations",
      "art/arcane/adapt/bukkit/event/PaperTranslations",
      "art/arcane/adapt/bukkit/event/RegionRespawns",
      "art/arcane/adapt/bukkit/platform/PaperRefRelease",
      "art/arcane/adapt/bukkit/platform/PaperRegistries",
      "art/arcane/adapt/bukkit/platform/data/PaperPdcView",
      "art/arcane/adapt/bukkit/platform/entity/PaperCopperGolems",
      "art/arcane/adapt/bukkit/platform/entity/PaperTeleports");
  private static final Pattern DESCRIPTOR_CLASS = Pattern.compile("L((?:org/bukkit|com/destroystokyo/paper|io/papermc/paper)/[^;<]+)[;<]");

  @Test
  void codeOutsideThePaperBoundaryLinksAgainstSpigot() throws IOException, URISyntaxException {
    Map<String, ClassModel> spigot = spigotApi();
    Path classes = Path.of(PaperApi.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    List<Path> files;
    try (Stream<Path> walk = Files.walk(classes)) {
      files = walk.filter((Path file) -> file.toString().endsWith(".class")).toList();
    }
    List<String> violations = new ArrayList<>();
    int scanned = 0;
    for (Path file : files) {
      ClassModel model = ClassFile.of().parse(Files.readAllBytes(file));
      String name = model.thisClass().asInternalName();
      if (!name.startsWith("art/arcane/adapt/") || paperOnly(name)) {
        continue;
      }
      scanned++;
      Set<String> missing = missing(model, spigot);
      for (String reference : missing) {
        violations.add(name + " -> " + reference);
      }
    }

    assertThat(spigot).as("Spigot API classes").hasSizeGreaterThan(1000);
    assertThat(scanned).as("scanned Bukkit-module classes").isGreaterThan(300);
    assertThat(violations).as("Paper-only references outside the Paper boundary").isEmpty();
  }

  private static boolean paperOnly(String name) {
    for (String entry : PAPER_ONLY) {
      if (entry.endsWith("/") ? name.startsWith(entry) : name.equals(entry) || name.startsWith(entry + "$")) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> missing(ClassModel model, Map<String, ClassModel> spigot) {
    boolean switchMap = model.fields().stream().anyMatch((FieldModel field) -> field.fieldName().stringValue().startsWith("$SwitchMap$"));
    Set<String> missing = new TreeSet<>();
    for (PoolEntry entry : model.constantPool()) {
      if (entry instanceof ClassEntry type) {
        checkClass(elementName(type.asInternalName()), spigot, missing);
      } else if (entry instanceof MemberRefEntry member) {
        String owner = member.owner().asInternalName();
        boolean guardedEnumConstant = switchMap && member instanceof FieldRefEntry;
        if (api(owner) && spigot.containsKey(owner) && !guardedEnumConstant
            && !hasMember(owner, member.name().stringValue(), member.type().stringValue(), spigot, new HashSet<>())) {
          missing.add(owner + "." + member.name().stringValue() + member.type().stringValue());
        }
      } else if (entry instanceof Utf8Entry text) {
        Matcher matcher = DESCRIPTOR_CLASS.matcher(text.stringValue());
        while (matcher.find()) {
          checkClass(matcher.group(1), spigot, missing);
        }
      }
    }
    return missing;
  }

  private static String elementName(String name) {
    String element = name;
    while (element.startsWith("[")) {
      element = element.substring(1);
    }
    return element.startsWith("L") && element.endsWith(";") ? element.substring(1, element.length() - 1) : element;
  }

  private static void checkClass(String name, Map<String, ClassModel> spigot, Set<String> missing) {
    if ((api(name) || name.startsWith("com/destroystokyo/paper/") || name.startsWith("io/papermc/paper/")) && !spigot.containsKey(name)) {
      missing.add(name);
    }
  }

  private static boolean api(String name) {
    return name.startsWith("org/bukkit/") && !name.startsWith("org/bukkit/craftbukkit/");
  }

  private static boolean hasMember(String owner, String name, String descriptor, Map<String, ClassModel> spigot, Set<String> seen) {
    if (!seen.add(owner)) {
      return false;
    }
    ClassModel model = spigot.get(owner);
    if (model == null) {
      return !api(owner) && jdkHasMember(owner, name, descriptor);
    }
    if (declares(model, name, descriptor)) {
      return true;
    }
    List<String> supertypes = new ArrayList<>();
    model.superclass().ifPresent((ClassEntry superclass) -> supertypes.add(superclass.asInternalName()));
    for (ClassEntry face : model.interfaces()) {
      supertypes.add(face.asInternalName());
    }
    supertypes.add("java/lang/Object");
    for (String supertype : supertypes) {
      if (hasMember(supertype, name, descriptor, spigot, seen)) {
        return true;
      }
    }
    return false;
  }

  private static boolean declares(ClassModel model, String name, String descriptor) {
    for (MethodModel method : model.methods()) {
      if (method.methodName().equalsString(name) && method.methodType().equalsString(descriptor)) {
        return true;
      }
    }
    for (FieldModel field : model.fields()) {
      if (field.fieldName().equalsString(name) && field.fieldType().equalsString(descriptor)) {
        return true;
      }
    }
    return false;
  }

  private static boolean jdkHasMember(String owner, String name, String descriptor) {
    if (!owner.startsWith("java/")) {
      return true;
    }
    try (InputStream in = ClassLoader.getSystemResourceAsStream(owner + ".class")) {
      if (in == null) {
        return true;
      }
      ClassModel model = ClassFile.of().parse(in.readAllBytes());
      if (declares(model, name, descriptor)) {
        return true;
      }
      if (model.superclass().isPresent() && jdkHasMember(model.superclass().get().asInternalName(), name, descriptor)) {
        return true;
      }
      for (ClassEntry face : model.interfaces()) {
        if (jdkHasMember(face.asInternalName(), name, descriptor)) {
          return true;
        }
      }
      return false;
    } catch (IOException unreadable) {
      return true;
    }
  }

  private static Map<String, ClassModel> spigotApi() throws IOException {
    String jar = System.getProperty("adapt.spigotApi");
    assertThat(jar).as("adapt.spigotApi system property").isNotBlank();
    Map<String, ClassModel> classes = new HashMap<>();
    try (ZipFile zip = new ZipFile(jar)) {
      for (ZipEntry entry : Collections.list(zip.entries())) {
        if (!entry.getName().endsWith(".class")) {
          continue;
        }
        try (InputStream in = zip.getInputStream(entry)) {
          ClassModel model = ClassFile.of().parse(in.readAllBytes());
          classes.put(model.thisClass().asInternalName(), model);
        }
      }
    }
    return classes;
  }
}
