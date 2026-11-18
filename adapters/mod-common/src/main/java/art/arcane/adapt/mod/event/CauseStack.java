package art.arcane.adapt.mod.event;

import java.util.function.Supplier;

/**
 * Nested causes for vanilla call sites, opened at a method's head and closed at its returns. A weak
 * cause yields to an enclosing one, so a totem's effects stay TOTEM although they run through the
 * food effect code.
 */
public final class CauseStack<T> {
    private static final int LIMIT = 64;

    private final Object[] causes = new Object[LIMIT];
    private int depth;

    public void push(T cause, boolean weak) {
        if (depth < LIMIT) {
            T outer = current();
            causes[depth] = weak && outer != null ? outer : cause;
        }
        depth++;
    }

    public void pop() {
        if (depth > 0) {
            depth--;
            if (depth < LIMIT) {
                causes[depth] = null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public T current() {
        if (depth == 0) {
            return null;
        }
        return (T) causes[Math.min(depth, LIMIT) - 1];
    }

    public <R> R with(T cause, Supplier<R> action) {
        push(cause, false);
        try {
            return action.get();
        } finally {
            pop();
        }
    }
}
