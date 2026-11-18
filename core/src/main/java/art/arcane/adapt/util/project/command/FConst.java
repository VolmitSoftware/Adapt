package art.arcane.adapt.util.command;

import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.util.common.format.C;

import java.util.Objects;

public class FConst {
  public static final C COLOR_ERROR = C.RED;
  public static final C COLOR_SUCCESS = C.GREEN;
  public static final C COLOR_WARNING = C.YELLOW;
  public static final C COLOR_INFO = C.WHITE;
  public static final Feedback TELEPORT = Feedback.builder()
      .sound(SoundFeedback.builder()
          .sound(Sounds.ENTITY_ENDER_EYE_LAUNCH)
          .volume(0.7f)
          .pitch(1.25f)
          .build())
      .build();

  public static Feedback error(String message, Object... args) {
    return Feedback.builder()
        .message(errorText(message, args))
        .sound(SoundFeedback.builder().sound(Sounds.BLOCK_DEEPSLATE_BREAK).pitch(0.5f).volume(1f).build())
        .build();
  }

  public static Feedback success(String message, Object... args) {
    return Feedback.builder()
        .message(successText(message, args))
        .sound(SoundFeedback.builder().sound(Sounds.BLOCK_AMETHYST_BLOCK_PLACE).pitch(1.5f).volume(1f).build())
        .sound(SoundFeedback.builder().sound(Sounds.ITEM_ARMOR_EQUIP_ELYTRA).pitch(1.1f).volume(1f).build())
        .build();
  }

  public static Feedback warning(String message, Object... args) {
    return Feedback.builder()
        .message(warningText(message, args))
        .sound(SoundFeedback.builder().sound(Sounds.ITEM_ARMOR_EQUIP_CHAIN).pitch(0.6f).volume(1f).build())
        .build();
  }

  public static Feedback info(String message, Object... args) {
    return Feedback.builder()
        .message(infoText(message, args))
        .sound(SoundFeedback.builder().sound(Sounds.ITEM_ARMOR_EQUIP_LEATHER).pitch(1.1f).volume(1f).build())
        .build();
  }

  public static String errorText(String message, Object... args) {
    return COLOR_ERROR + text(message, args);
  }

  public static String successText(String message, Object... args) {
    return COLOR_SUCCESS + text(message, args);
  }

  public static String warningText(String message, Object... args) {
    return COLOR_WARNING + text(message, args);
  }

  public static String infoText(String message, Object... args) {
    return COLOR_INFO + text(message, args);
  }

  private static String text(String message, Object... args) {
    return FeedbackText.format(Objects.requireNonNull(message, "message"), args);
  }
}
