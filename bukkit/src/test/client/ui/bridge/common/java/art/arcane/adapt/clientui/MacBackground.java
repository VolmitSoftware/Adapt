package art.arcane.adapt.clientui;

import org.lwjgl.system.JNI;
import org.lwjgl.system.macosx.ObjCRuntime;
import org.slf4j.LoggerFactory;

public final class MacBackground {
    private static final long ACCESSORY_POLICY = 1L;

    private MacBackground() {
    }

    public static boolean hidden() {
        return Boolean.parseBoolean(System.getProperty("adapt.ui.hidden", "true"));
    }

    public static void apply() {
        if (!hidden() || org.lwjgl.system.Platform.get() != org.lwjgl.system.Platform.MACOSX) {
            return;
        }
        try {
            long send = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
            long application = JNI.invokePPP(ObjCRuntime.objc_getClass("NSApplication"), ObjCRuntime.sel_getUid("sharedApplication"), send);
            JNI.invokePPPV(application, ObjCRuntime.sel_getUid("setActivationPolicy:"), ACCESSORY_POLICY, send);
        } catch (RuntimeException | LinkageError failure) {
            LoggerFactory.getLogger("AdaptUiBridge").warn("Could not move the client to the background", failure);
        }
    }
}
