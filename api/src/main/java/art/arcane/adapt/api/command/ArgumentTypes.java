package art.arcane.adapt.api.command;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** The argument types Adapt's own commands use. */
@ApiStatus.AvailableSince("1.0.0")
public final class ArgumentTypes {
  private static final List<String> BOOLEANS = List.of("true", "false");

  private ArgumentTypes() {
  }

  /** One word, as typed. */
  public static ArgumentType<String> word() {
    return of(String.class, Optional::of, List::of);
  }

  /** Every remaining word, joined by single spaces. Only as the last argument. */
  public static ArgumentType<String> text() {
    return new Simple<>(String.class, Optional::of, List::of, true);
  }

  /** A whole number from {@code min} to {@code max}, both included. */
  public static ArgumentType<Integer> integer(int min, int max) {
    if (min > max) {
      throw new IllegalArgumentException("min " + min + " is above max " + max);
    }
    return of(Integer.class, (String text) -> {
      try {
        int value = Integer.parseInt(text);
        return value < min || value > max ? Optional.empty() : Optional.of(value);
      } catch (NumberFormatException invalid) {
        return Optional.empty();
      }
    }, () -> List.of(String.valueOf(min)));
  }

  /** A finite number from {@code min} to {@code max}, both included. */
  public static ArgumentType<Double> decimal(double min, double max) {
    if (!(min <= max)) {
      throw new IllegalArgumentException("min " + min + " is not at most max " + max);
    }
    return of(Double.class, (String text) -> {
      try {
        double value = Double.parseDouble(text);
        return !Double.isFinite(value) || value < min || value > max ? Optional.empty() : Optional.of(value);
      } catch (NumberFormatException invalid) {
        return Optional.empty();
      }
    }, List::of);
  }

  /** {@code true} or {@code false}, in any case. */
  public static ArgumentType<Boolean> bool() {
    return of(Boolean.class, (String text) -> switch (text.toLowerCase(Locale.ROOT)) {
      case "true" -> Optional.of(true);
      case "false" -> Optional.of(false);
      default -> Optional.empty();
    }, () -> BOOLEANS);
  }

  /** One of fixed words, in any case; the value is the word as declared. */
  public static ArgumentType<String> choice(String first, String... more) {
    List<String> words = new ArrayList<>(more.length + 1);
    words.add(Objects.requireNonNull(first, "first"));
    for (String word : more) {
      words.add(Objects.requireNonNull(word, "word"));
    }
    List<String> fixed = List.copyOf(words);
    return of(String.class, (String text) -> {
      for (String word : fixed) {
        if (word.equalsIgnoreCase(text)) {
          return Optional.of(word);
        }
      }
      return Optional.empty();
    }, () -> fixed);
  }

  /** A constant of {@code type}, typed in any case and offered in lower case. */
  public static <E extends Enum<E>> ArgumentType<E> enumeration(Class<E> type) {
    Objects.requireNonNull(type, "type");
    List<String> names = new ArrayList<>();
    for (E constant : type.getEnumConstants()) {
      names.add(constant.name().toLowerCase(Locale.ROOT));
    }
    List<String> fixed = List.copyOf(names);
    return of(type, (String text) -> {
      for (E constant : type.getEnumConstants()) {
        if (constant.name().equalsIgnoreCase(text)) {
          return Optional.of(constant);
        }
      }
      return Optional.empty();
    }, () -> fixed);
  }

  /** An online player by name. */
  public static ArgumentType<PlayerRef> onlinePlayer() {
    return of(PlayerRef.class, (String text) -> {
      for (PlayerRef player : AdaptApi.platform().host().onlinePlayers()) {
        if (player.name().equalsIgnoreCase(text)) {
          return Optional.of(player);
        }
      }
      return Optional.empty();
    }, () -> {
      List<String> names = new ArrayList<>();
      for (PlayerRef player : AdaptApi.platform().host().onlinePlayers()) {
        names.add(player.name());
      }
      return names;
    });
  }

  /** An installed skill, written {@code namespace:path}; a bare path is in {@code adapt}. */
  public static ArgumentType<Id> skill() {
    return of(Id.class, (String text) -> installed(text, (Id id) -> AdaptApi.content().skill(id).isPresent()), () -> {
      List<String> ids = new ArrayList<>();
      for (Skill<?> skill : AdaptApi.content().skills()) {
        ids.add(skill.getId().toString());
      }
      return ids;
    });
  }

  /** An installed adaptation, written {@code namespace:skill/name}; a bare id is in {@code adapt}. */
  public static ArgumentType<Id> adaptation() {
    return of(Id.class, (String text) -> installed(text, (Id id) -> AdaptApi.content().adaptation(id).isPresent()), () -> {
      List<String> ids = new ArrayList<>();
      for (Adaptation<?> adaptation : AdaptApi.content().adaptations()) {
        ids.add(adaptation.getId().toString());
      }
      return ids;
    });
  }

  private static Optional<Id> installed(String text, Predicate<Id> present) {
    Id id = Id.tryParseAdapt(text.toLowerCase(Locale.ROOT));
    return id != null && present.test(id) ? Optional.of(id) : Optional.empty();
  }

  private static <T> ArgumentType<T> of(Class<T> type, Function<String, Optional<T>> parser,
                                        Supplier<List<String>> suggestions) {
    return new Simple<>(type, parser, suggestions, false);
  }

  private record Simple<T>(Class<T> valueType, Function<String, Optional<T>> parser, Supplier<List<String>> suggestions,
                           boolean consumesRest) implements ArgumentType<T> {
    @Override
    public Optional<T> parse(CommandCaller caller, String text) {
      return parser.apply(text);
    }

    @Override
    public List<String> suggest(CommandCaller caller, String typed) {
      return suggestions.get();
    }
  }
}
