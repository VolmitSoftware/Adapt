package art.arcane.adapt.pack.hud;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.pack.link.PackAudience;
import art.arcane.adapt.pack.manifest.PackSprites;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * The advancement toast for pack viewers: the same one-off toast everyone else gets, with the kit's toast icon for a
 * skill's own icon and the sprite's item model otherwise, and the title in the skill's colour. A toast
 * whose icon has no sprite in the pack, or a player without the pack, is left to the caller.
 */
public final class PackToasts {
  private final PackAudience audience;
  private final Function<Id, ItemRef> items;
  private final Supplier<PackHudManifest> manifest;
  private final ToIntFunction<String> skillColours;
  private final Function<String, String> plainTitle;

  /**
   * @param items        a new item of the type, or null when the type is unknown
   * @param skillColours a skill's colour as 0xRRGGBB, or -1 for an unknown skill
   * @param plainTitle   a toast's legacy title as plain text
   */
  public PackToasts(PackAudience audience, Function<Id, ItemRef> items, Supplier<PackHudManifest> manifest,
                    ToIntFunction<String> skillColours, Function<String, String> plainTitle) {
    this.audience = Objects.requireNonNull(audience, "audience");
    this.items = Objects.requireNonNull(items, "items");
    this.manifest = Objects.requireNonNull(manifest, "manifest");
    this.skillColours = Objects.requireNonNull(skillColours, "skillColours");
    this.plainTitle = Objects.requireNonNull(plainTitle, "plainTitle");
  }

  /** The running server's pack toasts; nobody is a viewer while Adapt or pack delivery is not running. */
  public static PackToasts running() {
    AdaptCore core = AdaptCore.current();
    return new PackToasts(PackAudience.DELIVERED,
        (Id type) -> core.getPlatform().host().items().create(type),
        PackHudManifest::current,
        (String skillId) -> {
          Skill<?> skill = core.getAdaptServer().getSkillRegistry().getSkill(skillId);
          return skill == null ? -1 : skill.getColor().packed();
        },
        (String legacy) -> MarkupPort.current().plain(legacy));
  }

  /** The frame vanilla draws for a toast of {@code kind}. */
  public static AdvancementFrame frame(ToastKind kind) {
    return kind == ToastKind.CHALLENGE ? AdvancementFrame.CHALLENGE : AdvancementFrame.TASK;
  }

  /** Shows {@code toast} to a pack viewer through {@code host}; false when the caller keeps its own output. */
  public boolean show(AdvancementHost host, PlayerRef player, ServerFrame.Toast toast, AdvancementFrame frame) {
    if (player == null || !audience.book(player)) {
      return false;
    }
    ItemRef icon = icon(toast.icon());
    if (icon == null) {
      return false;
    }
    host.toast(player, icon, title(toast), frame);
    return true;
  }

  /** The item drawing the icon's sprite, or null when the pack cannot draw it. */
  ItemRef icon(IconRef ref) {
    String sprite = PackSprites.packId(ref.spriteId());
    if (sprite == null) {
      return null;
    }
    PackHudManifest pack = manifest.get();
    Id kitIcon = pack.toastModel(sprite);
    Id model = kitIcon != null ? kitIcon : PackSprites.itemModel(sprite);
    Id type = Id.tryParse(ref.itemId());
    ItemRef item = type == null ? null : items.apply(type);
    if (item != null) {
      item.setItemModel(model);
    }
    return item;
  }

  String title(ServerFrame.Toast toast) {
    String plain = plainTitle.apply(toast.title()).replace("\\", "\\\\").replace("<", "\\<");
    int colour = toast.skillId().isEmpty() ? -1 : skillColours.applyAsInt(toast.skillId());
    return colour < 0 ? plain : String.format(Locale.ROOT, "<color:#%06x>", colour & 0xFFFFFF) + plain;
  }
}
