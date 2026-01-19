package org.foxesworld.kalitech.engine.modules.ui.chromium;

import org.cef.browser.CefBrowser;
import org.cef.handler.CefRenderHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * CefOsrBinder
 *
 * Binds a CefRenderHandler to a CefBrowser in legacy/limited JCEF builds using reflection.
 */
public final class CefOsrBinder {

    private CefOsrBinder() {}

    /**
     * Ensures that browser.getRenderHandler() becomes non-null by trying internal setters/fields.
     *
     * @param browser the browser instance
     * @param handler the render handler
     * @return true if handler was attached or already present; false otherwise
     */
    public static boolean ensureRenderHandler(CefBrowser browser, CefRenderHandler handler) {
        if (browser == null || handler == null) return false;

        // 0) Already present
        try {
            CefRenderHandler existing = browser.getRenderHandler();
            if (existing != null) return true;
        } catch (Throwable ignored) {
            // some impls may throw here; continue
        }

        // 1) Known common method names (public or non-public)
        String[] names = {
                "setRenderHandler",
                "setRenderHandler_",
                "setRenderHandlerInternal",
                "setOSRHandler",
                "setOsrHandler",
                "setRenderHandlerImpl"
        };
        for (String n : names) {
            if (invokeAnyVisibility(browser, n, handler)) {
                return isBound(browser);
            }
        }

        // 2) Any method with one param assignable from CefRenderHandler, name contains "render"
        if (invokeAnyRenderMethod(browser, handler)) {
            return isBound(browser);
        }

        // 3) Field fallback: set any field that can hold CefRenderHandler and name contains "render"
        if (setAnyRenderField(browser, handler)) {
            return isBound(browser);
        }

        return false;
    }

    private static boolean isBound(CefBrowser browser) {
        try {
            return browser.getRenderHandler() != null;
        } catch (Throwable ignored) {
            return true; // If getter is broken, assume set succeeded to avoid false negatives.
        }
    }

    private static boolean invokeAnyVisibility(Object target, String methodName, CefRenderHandler handler) {
        Class<?> c = target.getClass();
        while (c != null) {
            // declared (private/protected)
            try {
                Method m = c.getDeclaredMethod(methodName, CefRenderHandler.class);
                if (!void.class.equals(m.getReturnType())) return false;
                m.setAccessible(true);
                m.invoke(target, handler);
                return true;
            } catch (NoSuchMethodException ignored) {
                // continue
            } catch (Throwable ignored) {
                return false;
            }

            // public
            try {
                Method m = c.getMethod(methodName, CefRenderHandler.class);
                if (!void.class.equals(m.getReturnType())) return false;
                m.invoke(target, handler);
                return true;
            } catch (NoSuchMethodException ignored) {
                // continue
            } catch (Throwable ignored) {
                return false;
            }

            c = c.getSuperclass();
        }
        return false;
    }

    private static boolean invokeAnyRenderMethod(Object target, CefRenderHandler handler) {
        Method[] methods;
        try {
            methods = target.getClass().getMethods(); // public (includes inherited)
        } catch (Throwable t) {
            return false;
        }

        for (Method m : methods) {
            if (!void.class.equals(m.getReturnType())) continue;
            if (m.getParameterCount() != 1) continue;

            Class<?> p = m.getParameterTypes()[0];
            if (p == Object.class) continue;
            if (!p.isAssignableFrom(handler.getClass()) && !p.isAssignableFrom(CefRenderHandler.class) && !CefRenderHandler.class.isAssignableFrom(p)) {
                continue;
            }

            String n = m.getName().toLowerCase(Locale.ROOT);
            if (!n.contains("render")) continue;
            if (n.equals("equals")) continue;

            // Stronger filter: prefer methods that mention handler
            if (!n.contains("handler") && !n.contains("osr") && !n.contains("render")) {
                continue;
            }

            try {
                m.invoke(target, handler);
                return true;
            } catch (Throwable ignored) {
                // continue scanning
            }
        }

        // Also try declared (private/protected) methods
        Class<?> c = target.getClass();
        while (c != null) {
            Method[] decl;
            try {
                decl = c.getDeclaredMethods();
            } catch (Throwable t) {
                break;
            }
            for (Method m : decl) {
                if (!void.class.equals(m.getReturnType())) continue;
                if (m.getParameterCount() != 1) continue;

                Class<?> p = m.getParameterTypes()[0];
                if (p == Object.class) continue;
                if (!p.isAssignableFrom(handler.getClass()) && !p.isAssignableFrom(CefRenderHandler.class) && !CefRenderHandler.class.isAssignableFrom(p)) {
                    continue;
                }

                String n = m.getName().toLowerCase(Locale.ROOT);
                if (!n.contains("render")) continue;
                if (n.equals("equals")) continue;

                try {
                    m.setAccessible(true);
                    m.invoke(target, handler);
                    return true;
                } catch (Throwable ignored) {
                    // continue
                }
            }
            c = c.getSuperclass();
        }

        return false;
    }

    private static boolean setAnyRenderField(Object target, CefRenderHandler handler) {
        Class<?> c = target.getClass();
        while (c != null) {
            Field[] fields;
            try {
                fields = c.getDeclaredFields();
            } catch (Throwable t) {
                break;
            }

            for (Field f : fields) {
                Class<?> ft = f.getType();
                if (!CefRenderHandler.class.isAssignableFrom(ft)) continue;

                String n = f.getName().toLowerCase(Locale.ROOT);
                if (!n.contains("render")) continue;

                try {
                    f.setAccessible(true);
                    f.set(target, handler);
                    return true;
                } catch (Throwable ignored) {
                    // continue
                }
            }

            c = c.getSuperclass();
        }
        return false;
    }
}