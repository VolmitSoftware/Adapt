package art.arcane.adapt.util.decree;

import art.arcane.volmlib.util.decree.context.DecreeContextHandlers;
import art.arcane.volmlib.util.decree.context.DecreeContextHandlerType;
import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.plugin.VolmitSender;

import java.util.Map;

public interface DecreeContextHandler<T> extends DecreeContextHandlerType<T, VolmitSender> {
    Map<Class<?>, DecreeContextHandler<?>> contextHandlers = buildContextHandlers();

    static Map<Class<?>, DecreeContextHandler<?>> buildContextHandlers() {
        return DecreeContextHandlers.buildOrEmpty(
                Adapt.initialize("art.arcane.adapt.util.decree.context"),
                DecreeContextHandler.class,
                h -> ((DecreeContextHandler<?>) h).getType(),
                Throwable::printStackTrace);
    }
}
