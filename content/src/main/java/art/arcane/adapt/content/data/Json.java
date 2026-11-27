package art.arcane.adapt.content.data;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The JSON built-in content stores in data entries and files, written the way Gson wrote it before: an object's
 * fields in declaration order, subclass fields first; null fields and null map values left out; numbers as Java prints
 * them; enums by name and UUIDs as text. {@link #write} escapes {@code < > & = '} like {@code Gson.toJson};
 * {@link #writeComponent} does not, like a Gson tree's {@code toString}, for text-component JSON.
 */
public final class Json {
  private Json() {
  }

  /** The value as JSON, HTML characters escaped. */
  public static String write(@Nullable Object value) {
    StringBuilder out = new StringBuilder();
    append(out, value, true);
    return out.toString();
  }

  /** The value as JSON without HTML escaping, for text components. */
  public static String writeComponent(@Nullable Object value) {
    StringBuilder out = new StringBuilder();
    append(out, value, false);
    return out.toString();
  }

  /**
   * The tree {@code text} holds: {@link Map} (in order), {@link List}, {@link String}, {@link Double},
   * {@link Boolean} or null. Null for null, blank or malformed text.
   */
  public static @Nullable Object parse(@Nullable String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return new Parser(text, false).document();
    } catch (IllegalArgumentException | IndexOutOfBoundsException malformed) {
      return null;
    }
  }

  /**
   * Reads {@code text} into a new {@code type}, built by its no-argument constructor; fields the text leaves out keep
   * their defaults. Null for null, blank or {@code null} text.
   *
   * @throws IllegalArgumentException when the text is malformed or does not fit the type
   */
  public static <T> @Nullable T read(@Nullable String text, Class<T> type) {
    if (text == null || text.isBlank()) {
      return null;
    }
    Object tree;
    try {
      tree = new Parser(text, true).document();
    } catch (IndexOutOfBoundsException malformed) {
      throw new IllegalArgumentException("Malformed JSON", malformed);
    }
    return type.cast(convert(tree, type));
  }

  private static void append(StringBuilder out, @Nullable Object value, boolean htmlSafe) {
    if (value == null) {
      out.append("null");
    } else if (value instanceof String text) {
      quote(out, text, htmlSafe);
    } else if (value instanceof Character character) {
      quote(out, String.valueOf(character), htmlSafe);
    } else if (value instanceof Double number) {
      out.append(finite(number));
    } else if (value instanceof Float number) {
      finite(number.doubleValue());
      out.append(number);
    } else if (value instanceof Number || value instanceof Boolean) {
      out.append(value);
    } else if (value instanceof Enum<?> constant) {
      quote(out, constant.name(), htmlSafe);
    } else if (value instanceof UUID id) {
      quote(out, id.toString(), htmlSafe);
    } else if (value instanceof Map<?, ?> map) {
      Map<String, Object> fields = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        fields.put(String.valueOf(entry.getKey()), entry.getValue());
      }
      object(out, fields, htmlSafe);
    } else if (value instanceof Collection<?> collection) {
      List<Object> items = new ArrayList<>(collection);
      array(out, items, htmlSafe);
    } else if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      List<Object> items = new ArrayList<>(length);
      for (int index = 0; index < length; index++) {
        items.add(Array.get(value, index));
      }
      array(out, items, htmlSafe);
    } else {
      object(out, fieldsOf(value), htmlSafe);
    }
  }

  private static String finite(double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(value + " is not a valid JSON number");
    }
    return Double.toString(value);
  }

  private static void object(StringBuilder out, Map<String, Object> fields, boolean htmlSafe) {
    out.append('{');
    boolean first = true;
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      if (!first) {
        out.append(',');
      }
      first = false;
      quote(out, entry.getKey(), htmlSafe);
      out.append(':');
      append(out, entry.getValue(), htmlSafe);
    }
    out.append('}');
  }

  private static void array(StringBuilder out, List<Object> items, boolean htmlSafe) {
    out.append('[');
    for (int index = 0; index < items.size(); index++) {
      if (index > 0) {
        out.append(',');
      }
      append(out, items.get(index), htmlSafe);
    }
    out.append(']');
  }

  private static Map<String, Object> fieldsOf(Object value) {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (Field field : storedFields(value.getClass())) {
      try {
        fields.put(field.getName(), field.get(value));
      } catch (IllegalAccessException denied) {
        throw new IllegalStateException("Cannot read " + field, denied);
      }
    }
    return fields;
  }

  private static List<Field> storedFields(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
          continue;
        }
        field.setAccessible(true);
        fields.add(field);
      }
    }
    return fields;
  }

  private static void quote(StringBuilder out, String text, boolean htmlSafe) {
    out.append('"');
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      switch (character) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\t' -> out.append("\\t");
        case '\b' -> out.append("\\b");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\f' -> out.append("\\f");
        case ' ' -> out.append("\\u2028");
        case ' ' -> out.append("\\u2029");
        case '<', '>', '&', '=', '\'' -> {
          if (htmlSafe) {
            out.append(String.format("\\u%04x", (int) character));
          } else {
            out.append(character);
          }
        }
        default -> {
          if (character < 0x20) {
            out.append(String.format("\\u%04x", (int) character));
          } else {
            out.append(character);
          }
        }
      }
    }
    out.append('"');
  }

  private static @Nullable Object convert(@Nullable Object tree, Type type) {
    if (tree == null) {
      return null;
    }
    Class<?> raw = rawType(type);
    if (raw == String.class) {
      return tree instanceof Map<?, ?> || tree instanceof List<?> ? mismatch(tree, raw) : scalarText(tree);
    }
    if (raw == int.class || raw == Integer.class) {
      return number(tree, raw).intValueExact();
    }
    if (raw == long.class || raw == Long.class) {
      return number(tree, raw).longValueExact();
    }
    if (raw == short.class || raw == Short.class) {
      return number(tree, raw).shortValueExact();
    }
    if (raw == byte.class || raw == Byte.class) {
      return number(tree, raw).byteValueExact();
    }
    if (raw == double.class || raw == Double.class) {
      return number(tree, raw).doubleValue();
    }
    if (raw == float.class || raw == Float.class) {
      return number(tree, raw).floatValue();
    }
    if (raw == boolean.class || raw == Boolean.class) {
      if (tree instanceof Boolean flag) {
        return flag;
      }
      if (tree instanceof String text && (text.equals("true") || text.equals("false"))) {
        return Boolean.parseBoolean(text);
      }
      return mismatch(tree, raw);
    }
    if (raw == UUID.class) {
      return UUID.fromString(String.valueOf(scalarText(tree)));
    }
    if (raw.isEnum()) {
      return enumConstant(raw, String.valueOf(scalarText(tree)));
    }
    if (raw.isArray()) {
      List<?> items = tree instanceof List<?> list ? list : (List<?>) mismatch(tree, raw);
      Object array = Array.newInstance(raw.getComponentType(), items.size());
      for (int index = 0; index < items.size(); index++) {
        Array.set(array, index, convert(items.get(index), raw.getComponentType()));
      }
      return array;
    }
    if (Collection.class.isAssignableFrom(raw)) {
      List<?> items = tree instanceof List<?> list ? list : (List<?>) mismatch(tree, raw);
      Collection<Object> collection = newCollection(raw);
      Type element = argument(type, 0);
      for (Object item : items) {
        collection.add(convert(item, element));
      }
      return collection;
    }
    if (Map.class.isAssignableFrom(raw)) {
      Map<?, ?> object = tree instanceof Map<?, ?> map ? map : (Map<?, ?>) mismatch(tree, raw);
      Map<Object, Object> map = newMap(raw);
      Class<?> keyType = rawType(argument(type, 0));
      Type valueType = argument(type, 1);
      for (Map.Entry<?, ?> entry : object.entrySet()) {
        map.put(convert(entry.getKey(), keyType), convert(entry.getValue(), valueType));
      }
      return map;
    }
    if (raw == Object.class) {
      return tree;
    }
    Map<?, ?> object = tree instanceof Map<?, ?> map ? map : (Map<?, ?>) mismatch(tree, raw);
    Object instance = instantiate(raw);
    for (Field field : storedFields(raw)) {
      if (!object.containsKey(field.getName())) {
        continue;
      }
      Object value = convert(object.get(field.getName()), field.getGenericType());
      if (value == null && field.getType().isPrimitive()) {
        continue;
      }
      try {
        field.set(instance, value);
      } catch (IllegalAccessException denied) {
        throw new IllegalStateException("Cannot write " + field, denied);
      }
    }
    return instance;
  }

  private static Object scalarText(Object tree) {
    return tree instanceof BigDecimal number ? number.toString() : String.valueOf(tree);
  }

  private static BigDecimal number(Object tree, Class<?> type) {
    try {
      if (tree instanceof BigDecimal number) {
        return number;
      }
      if (tree instanceof String text) {
        return new BigDecimal(text.trim());
      }
    } catch (NumberFormatException notNumber) {
      throw new IllegalArgumentException("Expected " + type.getSimpleName() + " but was " + tree, notNumber);
    }
    return (BigDecimal) mismatch(tree, type);
  }

  private static Object mismatch(Object tree, Class<?> type) {
    throw new IllegalArgumentException("Expected " + type.getSimpleName() + " but was " + tree);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Object enumConstant(Class<?> type, String name) {
    try {
      return Enum.valueOf((Class) type, name);
    } catch (IllegalArgumentException unknown) {
      return null;
    }
  }

  private static Collection<Object> newCollection(Class<?> raw) {
    if (!raw.isInterface() && !Modifier.isAbstract(raw.getModifiers())) {
      @SuppressWarnings("unchecked")
      Collection<Object> made = (Collection<Object>) instantiate(raw);
      return made;
    }
    return Set.class.isAssignableFrom(raw) ? new LinkedHashSet<>() : new ArrayList<>();
  }

  private static Map<Object, Object> newMap(Class<?> raw) {
    if (!raw.isInterface() && !Modifier.isAbstract(raw.getModifiers())) {
      @SuppressWarnings("unchecked")
      Map<Object, Object> made = (Map<Object, Object>) instantiate(raw);
      return made;
    }
    return new LinkedHashMap<>();
  }

  private static Object instantiate(Class<?> type) {
    try {
      Constructor<?> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (ReflectiveOperationException unbuildable) {
      throw new IllegalStateException(type.getName() + " needs a no-argument constructor", unbuildable);
    }
  }

  private static Class<?> rawType(Type type) {
    if (type instanceof Class<?> raw) {
      return raw;
    }
    if (type instanceof ParameterizedType parameterized) {
      return (Class<?>) parameterized.getRawType();
    }
    return Object.class;
  }

  private static Type argument(Type type, int index) {
    if (type instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length > index) {
      return parameterized.getActualTypeArguments()[index];
    }
    return Object.class;
  }

  private static final class Parser {
    private final String text;
    private final boolean exactNumbers;
    private int position;

    private Parser(String text, boolean exactNumbers) {
      this.text = text;
      this.exactNumbers = exactNumbers;
    }

    private @Nullable Object document() {
      Object value = value();
      skipSpace();
      if (position != text.length()) {
        throw new IllegalArgumentException("Trailing text at " + position);
      }
      return value;
    }

    private void skipSpace() {
      while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
        position++;
      }
    }

    private @Nullable Object value() {
      skipSpace();
      char next = text.charAt(position);
      if (next == '{') {
        return object();
      }
      if (next == '[') {
        return array();
      }
      if (next == '"') {
        return string();
      }
      if (text.startsWith("true", position)) {
        position += 4;
        return Boolean.TRUE;
      }
      if (text.startsWith("false", position)) {
        position += 5;
        return Boolean.FALSE;
      }
      if (text.startsWith("null", position)) {
        position += 4;
        return null;
      }
      return number();
    }

    private Map<String, Object> object() {
      Map<String, Object> object = new LinkedHashMap<>();
      position++;
      skipSpace();
      if (text.charAt(position) == '}') {
        position++;
        return object;
      }
      while (true) {
        skipSpace();
        if (text.charAt(position) != '"') {
          throw new IllegalArgumentException("Expected a field name at " + position);
        }
        String key = string();
        skipSpace();
        expect(':');
        object.put(key, value());
        skipSpace();
        char next = text.charAt(position++);
        if (next == '}') {
          return object;
        }
        if (next != ',') {
          throw new IllegalArgumentException("Expected , or } at " + (position - 1));
        }
      }
    }

    private List<Object> array() {
      List<Object> array = new ArrayList<>();
      position++;
      skipSpace();
      if (text.charAt(position) == ']') {
        position++;
        return array;
      }
      while (true) {
        array.add(value());
        skipSpace();
        char next = text.charAt(position++);
        if (next == ']') {
          return array;
        }
        if (next != ',') {
          throw new IllegalArgumentException("Expected , or ] at " + (position - 1));
        }
      }
    }

    private String string() {
      expect('"');
      StringBuilder out = new StringBuilder();
      while (true) {
        char character = text.charAt(position++);
        if (character == '"') {
          return out.toString();
        }
        if (character != '\\') {
          out.append(character);
          continue;
        }
        char escaped = text.charAt(position++);
        switch (escaped) {
          case '"', '\\', '/' -> out.append(escaped);
          case 'b' -> out.append('\b');
          case 'f' -> out.append('\f');
          case 'n' -> out.append('\n');
          case 'r' -> out.append('\r');
          case 't' -> out.append('\t');
          case 'u' -> {
            out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
            position += 4;
          }
          default -> throw new IllegalArgumentException("Bad escape at " + (position - 1));
        }
      }
    }

    private Object number() {
      int start = position;
      while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) {
        position++;
      }
      if (start == position) {
        throw new IllegalArgumentException("Unexpected character at " + start);
      }
      String raw = text.substring(start, position);
      try {
        return exactNumbers ? new BigDecimal(raw) : Double.valueOf(raw);
      } catch (NumberFormatException malformed) {
        throw new IllegalArgumentException("Bad number at " + start, malformed);
      }
    }

    private void expect(char wanted) {
      if (text.charAt(position) != wanted) {
        throw new IllegalArgumentException("Expected " + wanted + " at " + position);
      }
      position++;
    }
  }
}
