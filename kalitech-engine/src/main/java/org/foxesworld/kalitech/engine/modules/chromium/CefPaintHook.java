package org.foxesworld.kalitech.engine.modules.chromium;

import org.cef.browser.CefBrowser;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Attaches JCEF OSR paint listener via reflection.
 */
public final class CefPaintHook {

    private CefPaintHook() {
    }

    public static void attach(CefBrowser browser, PaintCallback callback) {
        Objects.requireNonNull(browser, "browser");
        Objects.requireNonNull(callback, "callback");

        try {
            Method add = findAddOnPaintListener(browser.getClass());
            add.setAccessible(true);

            Class<?> listenerType = add.getParameterTypes()[0];
            Object listener = Proxy.newProxyInstance(
                    listenerType.getClassLoader(),
                    new Class<?>[]{listenerType},
                    new PaintInvocationHandler(callback)
            );

            add.invoke(browser, listener);
            System.out.println("[chromium] paint listener attached");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to attach OSR paint listener via reflection", e);
        }
    }

    private static Method findAddOnPaintListener(Class<?> browserClass) throws NoSuchMethodException {
        for (Method m : browserClass.getMethods()) {
            if (m.getName().equals("addOnPaintListener") && m.getParameterCount() == 1) {
                return m;
            }
        }
        for (Method m : browserClass.getDeclaredMethods()) {
            if (m.getName().equals("addOnPaintListener") && m.getParameterCount() == 1) {
                return m;
            }
        }
        throw new NoSuchMethodException(browserClass.getName() + "#addOnPaintListener(*) not found");
    }

    private static final class PaintInvocationHandler implements InvocationHandler {

        private final PaintCallback callback;

        private PaintInvocationHandler(PaintCallback callback) {
            this.callback = callback;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (args == null) {
                return null;
            }

            ByteBuffer buf = null;
            int w = -1;
            int h = -1;

            int intCount = 0;
            for (Object a : args) {
                if (a instanceof ByteBuffer) {
                    buf = (ByteBuffer) a;
                } else if (a instanceof Integer) {
                    if (intCount == 0) w = (Integer) a;
                    if (intCount == 1) h = (Integer) a;
                    intCount++;
                }
            }

            if (buf != null && w > 0 && h > 0) {
                callback.onPaint(buf, w, h);
            }
            return null;
        }
    }

    @FunctionalInterface
    public interface PaintCallback {
        void onPaint(ByteBuffer bgraPixels, int width, int height);
    }
}