package art.arcane.adapt.util.common.misc;

import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** The one sound each menu action makes, heard by the viewer only; chest menus and book pages both play through it. */
public final class MenuSounds {
  private MenuSounds() {
  }

  public enum Action {
    /** A menu or page opens. */
    OPEN(Sounds.ITEM_BOOK_PAGE_TURN, 0.7F, 1.25F),
    /** Back to the parent menu. */
    BACK(Sounds.ITEM_BOOK_PAGE_TURN, 0.7F, 0.85F),
    /** Next or previous page. */
    TURN(Sounds.ITEM_BOOK_PAGE_TURN, 0.6F, 1.45F),
    /** A setting changed. */
    TOGGLE(Sounds.UI_BUTTON_CLICK, 0.3F, 1.2F),
    /** A permanent action armed, waiting for its confirming click. */
    ARM(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.85F),
    /** An adaptation level unlearned. */
    UNLEARN(Sounds.BLOCK_BEACON_DEACTIVATE, 0.4F, 0.755F),
    /** The click did nothing. */
    REFUSE(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.7F);

    private final Id sound;
    private final float volume;
    private final float pitch;

    Action(Id sound, float volume, float pitch) {
      this.sound = sound;
      this.volume = volume;
      this.pitch = pitch;
    }
  }

  public static void play(PlayerRef viewer, Action action) {
    if (viewer == null || !viewer.isOnline()) {
      return;
    }
    SoundPlayer.of(viewer).play(viewer.location(), action.sound, action.volume, action.pitch);
  }
}
