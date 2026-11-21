package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiModifiers;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * A number with minus and plus tabs around an editable field. What a step or a typed value means is up to the
 * {@link Listener}; {@link #ranged} gives the usual clamped integer behaviour.
 */
public class NumberStepper extends UiNode {
    private static final int MAX_LENGTH = 32;

    private final Button minus;
    private final Button plus;
    private final TextField field;
    private final Listener listener;

    public NumberStepper(String id, String value, Listener listener) {
        super(id);
        this.listener = Objects.requireNonNull(listener, "listener");
        minus = add(new Button(id + ".minus", UiTome.Glyph.MINUS, () -> this.listener.step(-1, services().modifiers())));
        field = add(new TextField(id + ".field", MAX_LENGTH));
        plus = add(new Button(id + ".plus", UiTome.Glyph.PLUS, () -> this.listener.step(1, services().modifiers())));
        field.setText(value);
        field.setOnCommit(this.listener::commit);
    }

    /** An integer stepper clamped to [min, max]; Shift steps by ten; typed values that do not parse are reverted. */
    public static NumberStepper ranged(String id, int min, int max, int value, IntConsumer onChange) {
        Ranged ranged = new Ranged(min, max, Math.max(min, Math.min(max, value)), Objects.requireNonNull(onChange, "onChange"));
        ranged.stepper = new NumberStepper(id, Integer.toString(ranged.current), ranged);
        return ranged.stepper;
    }

    @Override
    public String kind() {
        return "stepper";
    }

    @Override
    public String label() {
        return field.text();
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("value", field.text());
    }

    public String value() {
        return field.text();
    }

    /** Shows {@code value}, unless the user is in the middle of typing a new one. */
    public void setValue(String value) {
        if (field.isFocused() && field.dirty()) {
            return;
        }
        if (!field.text().equals(value)) {
            field.setText(value);
        }
    }

    public TextField field() {
        return field;
    }

    public Button minus() {
        return minus;
    }

    public Button plus() {
        return plus;
    }

    @Override
    public int preferredWidth(UiDraw metrics) {
        int control = services().theme().controlHeight();
        return control * 2 + Math.max(control * 2, metrics.textWidth(field.text()) + 10) + services().theme().gap() * 2;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return services().theme().controlHeight();
    }

    @Override
    protected void layout(UiDraw metrics) {
        int control = services().theme().controlHeight();
        List<UiRect> cells = UiLayout.row(bounds(), services().theme().gap(), control, 0, control);
        minus.place(cells.get(0), metrics);
        field.place(cells.get(1), metrics);
        plus.place(cells.get(2), metrics);
    }

    public interface Listener {
        /** A minus (-1) or plus (+1) press with the modifiers held at the time. */
        void step(int direction, UiModifiers modifiers);

        /** Enter in the field. */
        void commit(String text);
    }

    private static final class Ranged implements Listener {
        private final int min;
        private final int max;
        private final IntConsumer onChange;
        private int current;
        private NumberStepper stepper;

        private Ranged(int min, int max, int current, IntConsumer onChange) {
            this.min = min;
            this.max = max;
            this.current = current;
            this.onChange = onChange;
        }

        @Override
        public void step(int direction, UiModifiers modifiers) {
            set(current + direction * (modifiers.shift() ? 10 : 1));
        }

        @Override
        public void commit(String text) {
            try {
                set(Integer.parseInt(text.trim()));
            } catch (NumberFormatException invalid) {
                stepper.field().setText(Integer.toString(current));
            }
        }

        private void set(int requested) {
            int clamped = Math.max(min, Math.min(max, requested));
            stepper.field().setText(Integer.toString(clamped));
            if (clamped != current) {
                current = clamped;
                onChange.accept(clamped);
            }
        }
    }
}
