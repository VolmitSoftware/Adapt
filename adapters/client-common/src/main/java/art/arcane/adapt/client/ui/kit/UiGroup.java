package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A container that places its visible children with a {@link UiLayoutPolicy} inside its padded bounds. */
public class UiGroup extends UiNode {
    private UiLayoutPolicy policy;
    private int padding;

    public UiGroup(String id) {
        this(id, UiLayoutPolicy.fill());
    }

    public UiGroup(String id, UiLayoutPolicy policy) {
        super(id);
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public String kind() {
        return "group";
    }

    public UiGroup setLayout(UiLayoutPolicy layout) {
        policy = Objects.requireNonNull(layout, "layout");
        services().requestLayout();
        return this;
    }

    public UiGroup setPadding(int amount) {
        padding = Math.max(0, amount);
        services().requestLayout();
        return this;
    }

    /** The area the children are placed in. */
    public UiRect contentBounds() {
        return bounds().inset(padding);
    }

    /** Vertical space taken by everything around the content area. */
    protected int chromeHeight() {
        return padding * 2;
    }

    /** Horizontal space taken by everything around the content area. */
    protected int chromeWidth() {
        return padding * 2;
    }

    protected final List<UiNode> visibleChildren() {
        List<UiNode> shown = new ArrayList<>();
        for (UiNode child : children()) {
            if (child.visible()) {
                shown.add(child);
            }
        }
        return shown;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        int content = policy.preferredHeight(visibleChildren(), Math.max(0, width - chromeWidth()), metrics);
        return content == 0 ? 0 : content + chromeHeight();
    }

    @Override
    protected void layout(UiDraw metrics) {
        policy.layout(visibleChildren(), contentBounds(), metrics);
    }
}
