package dev.linqfy.bigCasares.modules.tideframe;

import java.lang.reflect.Method;

/**
 * Checks, while connecting, that the installed Tideframe has each method BigCasares calls, with the same types. A
 * different API then falls back to BigCasares' menus at once, instead of failing later with a NoSuchMethodError in the
 * middle of a command.
 */
final class TideframeApiShape {
    private TideframeApiShape() {
    }

    static void require(Class<?> type, Class<?> returns, String name, Class<?>... parameters) throws NoSuchMethodException {
        Method method = type.getMethod(name, parameters);
        if (!method.getReturnType().equals(returns)) {
            throw new NoSuchMethodException(type.getName() + "." + name + " returns " + method.getReturnType().getName()
                + ", not " + returns.getName());
        }
    }
}
