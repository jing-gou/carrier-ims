package io.github.nku100.carrierims;

import android.app.Application;

import io.github.libxposed.service.XposedServiceHelper;
import io.github.nku100.carrierims.ui.ServiceBridge;

/**
 * Registers the libxposed service listener exactly once for the process.
 *
 * <p>{@code XposedServiceHelper.registerListener} must only be called once, and the framework may
 * deliver its binder before any activity exists, so this belongs in {@code Application}.
 */
public final class App extends Application {

    private final ServiceBridge bridge = new ServiceBridge();

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(bridge);
    }

    public ServiceBridge bridge() {
        return bridge;
    }
}
