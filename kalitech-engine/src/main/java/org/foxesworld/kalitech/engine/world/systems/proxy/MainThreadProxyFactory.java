package org.foxesworld.kalitech.engine.world.systems.proxy;

// Author: KΛYLΛ

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MainThreadProxyFactory
 * <p>
 * Creates dynamic proxies for engine/api objects so worker-runtime can call them safely:
 * - On world thread: direct call
 * - On worker thread: dispatch to main via {@link MainThreadDispatcher} (sync)
 * <p>
 * The important detail: returned interface values are also wrapped based on the
 * declared return type of the invoked method, so chained calls remain safe:
 * <p>
 * engine.physics().addBody(...)  // physics() must not leak a raw (non-proxied) sub-api
 */
public final class MainThreadProxyFactory {

    private final MainThreadDispatcher dispatcher;

    // Avoid wrapping the same instance repeatedly (identity cache).
    private final Map<Object, Object> cache = new IdentityHashMap<>();

    // Optional denylist: block some calls from worker even via dispatch (hard sandbox at API level).
    // You can keep it empty to only enforce threading.
    private volatile MethodDenylist denylist = MethodDenylist.none();

    public MainThreadProxyFactory(MainThreadDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    public void setDenylist(MethodDenylist denylist) {
        this.denylist = (denylist == null) ? MethodDenylist.none() : denylist;
    }

    @SuppressWarnings("unchecked")
    public <T> T wrap(T target, Class<T> iface) {
        if (target == null) return null;
        Objects.requireNonNull(iface, "iface");
        if (!iface.isInterface()) throw new IllegalArgumentException("Not an interface: " + iface.getName());
        if (!iface.isInstance(target)) {
            throw new IllegalArgumentException("Target does not implement: " + iface.getName());
        }

        // Don't wrap on world thread unnecessarily.
        if (dispatcher.isWorldThread()) return target;

        // If it's already our proxy, return as-is.
        if (Proxy.isProxyClass(target.getClass())) {
            InvocationHandler ih = Proxy.getInvocationHandler(target);
            if (ih instanceof Handler) return target;
        }

        synchronized (cache) {
            Object existing = cache.get(target);
            if (existing != null) return (T) existing;

            InvocationHandler h = new Handler(target, iface);
            Object p = Proxy.newProxyInstance(
                    iface.getClassLoader(),
                    new Class<?>[]{iface},
                    h
            );
            cache.put(target, p);
            return (T) p;
        }
    }

    /**
     * API-level hard sandbox hook.
     * Keep it empty (none) if you only want thread confinement.
     */
    public interface MethodDenylist {
        static MethodDenylist none() {
            return (i, m) -> false;
        }

        static MethodDenylist byNamePrefix(String... forbiddenPrefixes) {
            final String[] fp = (forbiddenPrefixes == null) ? new String[0] : forbiddenPrefixes.clone();
            return (iface, method) -> {
                String n = method.getName();
                for (String p : fp) {
                    if (p != null && !p.isEmpty() && n.startsWith(p)) return true;
                }
                return false;
            };
        }

        boolean isDenied(Class<?> iface, Method method);
    }

    private final class Handler implements InvocationHandler {
        private final Object target;
        private final Class<?> iface;

        Handler(Object target, Class<?> iface) {
            this.target = target;
            this.iface = iface;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Object methods local
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(target, args);
            }

            // Hard sandbox rule (optional)
            if (denylist.isDenied(iface, method)) {
                throw new SecurityException("[sandbox] denied call: " + iface.getSimpleName() + "." + method.getName());
            }

            // On world thread -> direct call
            if (dispatcher.isWorldThread()) {
                Object r = method.invoke(target, args);
                return wrapReturn(method, r);
            }

            // From worker -> marshal to main synchronously
            Object r = dispatcher.call(() -> {
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException ite) {
                    // Re-throw the original cause so JS sees the real error.
                    Throwable c = ite.getCause();
                    if (c != null) {
                        if (c instanceof RuntimeException re) throw re;
                        if (c instanceof Error er) throw er;
                        throw new RuntimeException(c);
                    }
                    throw ite;
                }
            });

            return wrapReturn(method, r);
        }

        private Object wrapReturn(Method method, Object r) {
            if (r == null) return null;

            // Already our proxy -> safe.
            if (Proxy.isProxyClass(r.getClass())) {
                try {
                    InvocationHandler ih = Proxy.getInvocationHandler(r);
                    if (ih instanceof Handler) return r;
                } catch (IllegalArgumentException ignored) {
                }
            }

            // Wrap based on DECLARED return type (this closes the leak).
            Class<?> rt = method.getReturnType();
            if (rt != null && rt.isInterface() && rt.isInstance(r)) {
                @SuppressWarnings("unchecked")
                Class<Object> ifaceRt = (Class<Object>) rt;
                return MainThreadProxyFactory.this.wrap(r, ifaceRt);
            }

            return r;
        }
    }
}