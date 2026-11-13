package io.github.jingou.nrst.ui;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds the libxposed service binder and tells the UI when it appears or dies.
 *
 * <p>The service is the only honest source of truth about whether the hook is running, and the
 * only writable handle on the remote preferences the hook reads.
 */
public final class ServiceBridge implements XposedServiceHelper.OnServiceListener {

    public interface Listener {
        void onServiceChanged();
    }

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile XposedService service;

    @Override
    public void onServiceBind(XposedService service) {
        this.service = service;
        notifyListeners();
    }

    @Override
    public void onServiceDied(XposedService service) {
        if (this.service == service) {
            this.service = null;
        }
        notifyListeners();
    }

    /** The service, or {@code null} while the framework has not delivered one. */
    public XposedService service() {
        return service;
    }

    public void addListener(Listener listener) {
        listeners.addIfAbsent(listener);
        if (service != null) {
            listener.onServiceChanged();
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Listener listener : listeners) {
            listener.onServiceChanged();
        }
    }
}
