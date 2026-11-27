package art.arcane.adapt.localization.catalog;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.SimpleComponent;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.engine.skill.LegacyContentNames;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.localization.AdaptKeys;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDocumentation;
import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Text for configuration fields, for any config class. A core field at {@code core.<field>} is labelled and described
 * by {@code adapt.config.fields.core.<field>.label} and {@code .description}; a content field at
 * {@code skills|adaptations.<id>.<field>} by {@link TextKeys#skillConfigField} or {@link TextKeys#adaptationConfigField}
 * plus {@code .label} and {@code .description}. Without a
 * declared key a field shows its name and its {@link art.arcane.adapt.api.config.ConfigDoc}. The engine declares the
 * English of the core fields and, through {@link #declaredBy}, of every registered skill's and adaptation's fields, all
 * read from their {@code ConfigDoc} annotations, so translations of any namespace have English keys to translate.
 */
public final class ConfigFieldMessages {
  private static final String PREFIX = AdaptKeys.id("config.fields.");

  private ConfigFieldMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    for (FieldMessages field : createFields()) {
      builder.add(field.label());
      builder.add(field.description());
    }
  }

  /** Whether a bundle declares a label for {@code path}. */
  public static boolean declaresLabel(String path) {
    return declared(path, ".label") != null;
  }

  /** The label of the field or section at {@code path}, in the viewer's language. */
  public static String label(String path) {
    MessageKey declared = declared(path, ".label");
    return declared != null ? AdaptLanguage.text(declared) : humanize(leaf(path));
  }

  /** The description lines of {@code field} at {@code path}, in the viewer's language. */
  public static List<String> description(String path, Field field) {
    MessageKey declared = declared(path, ".description");
    return declared != null
        ? List.of(AdaptLanguage.text(declared).split("\\n"))
        : ConfigDocumentation.buildFieldComments(sourceTag(path), path, field, null);
  }

  private static MessageKey declared(String path, String suffix) {
    String key = key(path);
    return key == null ? null : AdaptLanguage.declared(key + suffix);
  }

  /**
   * The message key of the field at {@code path} without its {@code .label} or {@code .description} suffix: the
   * convention key for {@code skills.<id>.<field>} and {@code adaptations.<id>.<field>}, else
   * {@code adapt.config.fields.<path>}. Null for a content path that names no id or field.
   */
  static String key(String path) {
    String[] parts = path.split("\\.", 3);
    boolean skill = parts[0].equals("skills");
    if (!skill && !parts[0].equals("adaptations")) {
      return PREFIX + path;
    }
    if (parts.length < 3 || parts[2].isEmpty() || parts[2].endsWith(".") || parts[2].contains("..")) {
      return null;
    }
    Id id = parts[1].indexOf(':') < 0 ? null : Id.tryParse(parts[1]);
    return id == null ? null : contentKey(skill, id, parts[2]);
  }

  private static String contentKey(boolean skill, Id id, String field) {
    return skill ? TextKeys.skillConfigField(id, field) : TextKeys.adaptationConfigField(id, field);
  }

  /** The documentation source a path belongs to: {@code skill:<id>}, {@code adaptation:<id>} or the core config. */
  static String sourceTag(String path) {
    String[] parts = path.split("\\.", 3);
    if (parts.length >= 2 && parts[0].equals("skills")) {
      return "skill:" + parts[1];
    }
    if (parts.length >= 2 && parts[0].equals("adaptations")) {
      return "adaptation:" + parts[1];
    }
    return "core-config";
  }

  private static String leaf(String path) {
    int dot = path.lastIndexOf('.');
    return dot < 0 ? path : path.substring(dot + 1);
  }

  private static String humanize(String name) {
    String label = name.replace('_', ' ').replace('-', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
    return label.isEmpty() ? label : Character.toUpperCase(label.charAt(0)) + label.substring(1);
  }

  /**
   * The English label and description of every config field of the skills, adaptations, upgrades and mutation blocks
   * in {@code namespace} among {@code skills}, read from the fields and their {@code ConfigDoc} annotations. A key the
   * namespace's own messages declare in English replaces the one given here.
   */
  public static List<MessageKey> declaredBy(String namespace, Collection<? extends Skill<?>> skills) {
    Map<String, MessageKey> keys = new LinkedHashMap<>();
    for (Skill<?> skill : skills) {
      if (namespace.equals(skill.getId().namespace())) {
        addComponent(keys, true, skill.getId(), skill);
      }
      for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
        if (namespace.equals(adaptation.getId().namespace())) {
          addComponent(keys, false, adaptation.getId(), adaptation);
        }
      }
    }
    return List.copyOf(keys.values());
  }

  private static void addComponent(Map<String, MessageKey> keys, boolean skill, Id id, Object component) {
    if (!(component instanceof SimpleComponent<?> simple) || simple.getConfigurationClass() == null) {
      return;
    }
    List<FieldMessages> fields = new ArrayList<>();
    String kind = skill ? "skill" : "adaptation";
    addFields(fields, field -> contentKey(skill, id, field), kind + "s." + id, kind + ":" + LegacyContentNames.of(id),
        simple.getConfigurationClass());
    for (FieldMessages field : fields) {
      keys.putIfAbsent(field.label().id(), field.label());
      keys.putIfAbsent(field.description().id(), field.description());
    }
  }

  private static List<FieldMessages> createFields() {
    List<FieldMessages> fields = new ArrayList<>();
    addFields(fields, field -> PREFIX + "core." + field, "core", "core-config", AdaptConfig.class);
    return List.copyOf(fields);
  }

  private static void addFields(List<FieldMessages> fields, UnaryOperator<String> keys, String path, String source,
                                Class<?> type) {
    addFields(fields, keys, path, "", source, type);
  }

  private static void addFields(List<FieldMessages> fields, UnaryOperator<String> keys, String path, String parentField,
                                String source, Class<?> type) {
    Class<?> parent = type.getSuperclass();
    if (parent != null && parent != Object.class) {
      addFields(fields, keys, path, parentField, source, parent);
    }
    for (Field field : type.getDeclaredFields()) {
      int modifiers = field.getModifiers();
      if (field.isSynthetic() || Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
        continue;
      }
      String fieldPath = parentField.isEmpty() ? field.getName() : parentField + "." + field.getName();
      String id = keys.apply(fieldPath);
      String docPath = path + "." + fieldPath;
      fields.add(new FieldMessages(TextKey.of(id + ".label", humanize(field.getName())),
          LinesKey.of(id + ".description", ConfigDocumentation.buildFieldComments(source, docPath, field, null))));
      Class<?> fieldType = field.getType();
      if (isSection(fieldType)) {
        addFields(fields, keys, path, fieldPath, source, fieldType);
      }
    }
  }

  /** A field of a config's own class type is a section whose fields are documented in turn. */
  private static boolean isSection(Class<?> type) {
    return !type.isPrimitive() && !type.isEnum() && !type.isArray() && !type.isInterface()
        && !Collection.class.isAssignableFrom(type) && !Map.class.isAssignableFrom(type)
        && !type.getName().startsWith("java.");
  }

  public record FieldMessages(TextKey label, LinesKey description) {
  }
}
