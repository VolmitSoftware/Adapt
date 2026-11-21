package art.arcane.adapt.client.ui;

import com.mojang.blaze3d.platform.InputConstants;

/** Maps the version's key and mouse button codes, whose values differ between GLFW and SDL versions, to Adapt's. */
final class MinecraftUiKeys {
    private MinecraftUiKeys() {
    }

    static UiMouseButton button(int button) {
        return switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> UiMouseButton.LEFT;
            case InputConstants.MOUSE_BUTTON_RIGHT -> UiMouseButton.RIGHT;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> UiMouseButton.MIDDLE;
            default -> UiMouseButton.OTHER;
        };
    }

    static UiKey key(int key) {
        return switch (key) {
            case InputConstants.KEY_ESCAPE -> UiKey.ESCAPE;
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> UiKey.ENTER;
            case InputConstants.KEY_TAB -> UiKey.TAB;
            case InputConstants.KEY_BACKSPACE -> UiKey.BACKSPACE;
            case InputConstants.KEY_DELETE -> UiKey.DELETE;
            case InputConstants.KEY_LEFT -> UiKey.LEFT;
            case InputConstants.KEY_RIGHT -> UiKey.RIGHT;
            case InputConstants.KEY_UP -> UiKey.UP;
            case InputConstants.KEY_DOWN -> UiKey.DOWN;
            case InputConstants.KEY_HOME -> UiKey.HOME;
            case InputConstants.KEY_END -> UiKey.END;
            case InputConstants.KEY_PAGEUP -> UiKey.PAGE_UP;
            case InputConstants.KEY_PAGEDOWN -> UiKey.PAGE_DOWN;
            case InputConstants.KEY_SPACE -> UiKey.SPACE;
            case InputConstants.KEY_A -> UiKey.A;
            case InputConstants.KEY_B -> UiKey.B;
            case InputConstants.KEY_C -> UiKey.C;
            case InputConstants.KEY_D -> UiKey.D;
            case InputConstants.KEY_E -> UiKey.E;
            case InputConstants.KEY_F -> UiKey.F;
            case InputConstants.KEY_G -> UiKey.G;
            case InputConstants.KEY_H -> UiKey.H;
            case InputConstants.KEY_I -> UiKey.I;
            case InputConstants.KEY_J -> UiKey.J;
            case InputConstants.KEY_K -> UiKey.K;
            case InputConstants.KEY_L -> UiKey.L;
            case InputConstants.KEY_M -> UiKey.M;
            case InputConstants.KEY_N -> UiKey.N;
            case InputConstants.KEY_O -> UiKey.O;
            case InputConstants.KEY_P -> UiKey.P;
            case InputConstants.KEY_Q -> UiKey.Q;
            case InputConstants.KEY_R -> UiKey.R;
            case InputConstants.KEY_S -> UiKey.S;
            case InputConstants.KEY_T -> UiKey.T;
            case InputConstants.KEY_U -> UiKey.U;
            case InputConstants.KEY_V -> UiKey.V;
            case InputConstants.KEY_W -> UiKey.W;
            case InputConstants.KEY_X -> UiKey.X;
            case InputConstants.KEY_Y -> UiKey.Y;
            case InputConstants.KEY_Z -> UiKey.Z;
            case InputConstants.KEY_0 -> UiKey.DIGIT_0;
            case InputConstants.KEY_1 -> UiKey.DIGIT_1;
            case InputConstants.KEY_2 -> UiKey.DIGIT_2;
            case InputConstants.KEY_3 -> UiKey.DIGIT_3;
            case InputConstants.KEY_4 -> UiKey.DIGIT_4;
            case InputConstants.KEY_5 -> UiKey.DIGIT_5;
            case InputConstants.KEY_6 -> UiKey.DIGIT_6;
            case InputConstants.KEY_7 -> UiKey.DIGIT_7;
            case InputConstants.KEY_8 -> UiKey.DIGIT_8;
            case InputConstants.KEY_9 -> UiKey.DIGIT_9;
            default -> UiKey.OTHER;
        };
    }
}
