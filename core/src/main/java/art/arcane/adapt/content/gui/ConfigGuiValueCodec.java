package art.arcane.adapt.content.gui;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ConfigMessages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

final class ConfigGuiValueCodec {
  private ConfigGuiValueCodec() {
  }

  static ConfigGui.ParseResult parseInputValue(Class<?> type, String raw) {
    if (type == null) {
      return ConfigGui.ParseResult.fail(AdaptLanguage.text(ConfigMessages.UNKNOWN_TARGET_TYPE));
    }

    Class<?> normalized = normalizeType(type);
    String trimmed = raw == null ? "" : raw.trim();

    try {
      if (normalized == String.class) {
        return ConfigGui.ParseResult.ok(raw == null ? "" : raw);
      }

      if (normalized == Character.class) {
        if (trimmed.length() != 1) {
          return ConfigGui.ParseResult.fail(AdaptLanguage.text(ConfigMessages.EXPECTED_ONE_CHARACTER));
        }
        return ConfigGui.ParseResult.ok(trimmed.charAt(0));
      }

      if (normalized == Boolean.class) {
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("yes") || trimmed.equalsIgnoreCase("on")) {
          return ConfigGui.ParseResult.ok(true);
        }
        if (trimmed.equalsIgnoreCase("false") || trimmed.equalsIgnoreCase("no") || trimmed.equalsIgnoreCase("off")) {
          return ConfigGui.ParseResult.ok(false);
        }
        return ConfigGui.ParseResult.fail(AdaptLanguage.text(ConfigMessages.EXPECTED_BOOLEAN));
      }

      if (normalized.isEnum()) {
        Object constant = parseEnumConstant(normalized, trimmed);
        if (constant == null) {
          return ConfigGui.ParseResult.fail(AdaptLanguage.text(
              ConfigMessages.EXPECTED_ONE_OF,
              untrusted("values", enumConstants(normalized))
          ));
        }
        return ConfigGui.ParseResult.ok(constant);
      }

      if (normalized == Integer.class) {
        return ConfigGui.ParseResult.ok(Integer.parseInt(trimmed));
      }
      if (normalized == Long.class) {
        return ConfigGui.ParseResult.ok(Long.parseLong(trimmed));
      }
      if (normalized == Double.class) {
        double v = Double.parseDouble(trimmed);
        if (!Double.isFinite(v)) {
          return ConfigGui.ParseResult.fail(AdaptLanguage.text(ConfigMessages.EXPECTED_FINITE_NUMBER));
        }
        return ConfigGui.ParseResult.ok(v);
      }
      if (normalized == Float.class) {
        float v = Float.parseFloat(trimmed);
        if (!Float.isFinite(v)) {
          return ConfigGui.ParseResult.fail(AdaptLanguage.text(ConfigMessages.EXPECTED_FINITE_NUMBER));
        }
        return ConfigGui.ParseResult.ok(v);
      }
      if (normalized == Short.class) {
        return ConfigGui.ParseResult.ok(Short.parseShort(trimmed));
      }
      if (normalized == Byte.class) {
        return ConfigGui.ParseResult.ok(Byte.parseByte(trimmed));
      }
    } catch (Throwable e) {
      return ConfigGui.ParseResult.fail(AdaptLanguage.text(
          ConfigMessages.INVALID_VALUE_FOR_TYPE,
          untrusted("type", typeName(type))
      ));
    }

    return ConfigGui.ParseResult.fail(AdaptLanguage.text(
        ConfigMessages.UNSUPPORTED_INPUT_TYPE,
        untrusted("type", typeName(type))
    ));
  }

  static String typeName(Class<?> type) {
    if (type == null) {
      return AdaptLanguage.text(ConfigMessages.TYPE_UNKNOWN);
    }

    Class<?> normalized = normalizeType(type);
    if (normalized.isEnum()) {
      return AdaptLanguage.text(ConfigMessages.TYPE_ENUM);
    }
    if (normalized == Boolean.class) {
      return AdaptLanguage.text(ConfigMessages.TYPE_BOOLEAN);
    }
    if (normalized == String.class || normalized == Character.class) {
      return AdaptLanguage.text(ConfigMessages.TYPE_TEXT);
    }
    if (isNumericType(normalized)) {
      return AdaptLanguage.text(ConfigMessages.TYPE_NUMBER);
    }
    return normalized.getSimpleName();
  }

  static Object coerceValue(Object value, Class<?> targetType) {
    if (value == null) {
      return null;
    }

    Class<?> normalizedTarget = normalizeType(targetType);
    Class<?> valueType = value.getClass();
    if (normalizedTarget.isAssignableFrom(valueType)) {
      return value;
    }

    ConfigGui.ParseResult parsed = parseInputValue(targetType, String.valueOf(value));
    return parsed.success() ? parsed.value() : value;
  }

  static Class<?> normalizeType(Class<?> type) {
    if (type == null || !type.isPrimitive()) {
      return type;
    }

    if (type == int.class) return Integer.class;
    if (type == long.class) return Long.class;
    if (type == double.class) return Double.class;
    if (type == float.class) return Float.class;
    if (type == short.class) return Short.class;
    if (type == byte.class) return Byte.class;
    if (type == boolean.class) return Boolean.class;
    if (type == char.class) return Character.class;
    return type;
  }

  static boolean isNumericType(Class<?> type) {
    return type == Integer.class
        || type == Long.class
        || type == Double.class
        || type == Float.class
        || type == Short.class
        || type == Byte.class;
  }

  static boolean isSectionType(Class<?> type) {
    Class<?> normalized = normalizeType(type);
    if (normalized == null) {
      return false;
    }

    if (normalized.isPrimitive() || normalized.isEnum()) {
      return false;
    }

    if (normalized == String.class || normalized == Character.class || normalized == Boolean.class || isNumericType(normalized)) {
      return false;
    }

    if (Map.class.isAssignableFrom(normalized) || Collection.class.isAssignableFrom(normalized) || normalized.isArray()) {
      return false;
    }

    return true;
  }

  static Object cycleEnum(Class<?> enumType, Object current, int direction) {
    Class<?> normalized = normalizeType(enumType);
    if (normalized == null || !normalized.isEnum()) {
      return null;
    }

    Object[] constants = normalized.getEnumConstants();
    if (constants == null || constants.length == 0) {
      return null;
    }

    int currentIndex = 0;
    if (current != null) {
      for (int i = 0; i < constants.length; i++) {
        if (Objects.equals(constants[i], current)) {
          currentIndex = i;
          break;
        }
      }
    }

    int nextIndex = currentIndex + direction;
    if (nextIndex < 0) {
      nextIndex = constants.length - 1;
    } else if (nextIndex >= constants.length) {
      nextIndex = 0;
    }
    return constants[nextIndex];
  }

  static Object parseEnumConstant(Class<?> enumType, String value) {
    if (enumType == null || !enumType.isEnum() || value == null) {
      return null;
    }

    for (Object constant : enumType.getEnumConstants()) {
      if (constant == null) {
        continue;
      }

      if (constant.toString().equalsIgnoreCase(value)) {
        return constant;
      }
    }

    return null;
  }

  static String enumConstants(Class<?> enumType) {
    if (enumType == null || !enumType.isEnum()) {
      return "";
    }

    List<String> values = new ArrayList<>();
    for (Object constant : enumType.getEnumConstants()) {
      if (constant == null) {
        continue;
      }
      values.add(constant.toString());
    }
    return String.join(", ", values);
  }
}
