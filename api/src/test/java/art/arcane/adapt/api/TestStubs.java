package art.arcane.adapt.api;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.Function;

/** Interface stubs that answer by method name and refuse everything else. */
public final class TestStubs {
  private TestStubs() {
  }

  public static <T> T stub(Class<T> type, Map<String, Function<Object[], Object>> answers) {
    Object stub = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
        (Object proxy, java.lang.reflect.Method method, Object[] args) -> {
          Function<Object[], Object> answer = answers.get(method.getName());
          if (answer != null) {
            return answer.apply(args);
          }
          return switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> type.getSimpleName() + "Stub";
            default -> throw new UnsupportedOperationException(type.getSimpleName() + "." + method.getName());
          };
        });
    return type.cast(stub);
  }
}
