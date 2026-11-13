package io.github.jingou.nrst.hook;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import io.github.jingou.nrst.config.CarrierConfigPatch;
import io.github.jingou.nrst.config.Thresholds;
import io.github.jingou.nrst.data.ThresholdSource;
import io.github.jingou.nrst.data.ThresholdStore;

import java.lang.reflect.Method;

/**
 * Entry point of the module, loaded by LSPosed into {@code com.android.phone}.
 *
 * <p>Google hardens the write path ({@code CarrierConfigManager.overrideConfig}), not the read
 * path. This module therefore intercepts the single method every carrier config read funnels
 * through — {@code CarrierConfigLoader.getConfigForSubIdWithFeature} — and rewrites exactly one
 * key in the returned bundle. No Shizuku, no {@code MODIFY_PHONE_STATE}, no persistent override
 * state to lose on reboot.
 *
 * <p>The intercepting code is byte-for-byte the same on every Android version; the only version
 * dependent part is finding the method, which {@link HookTargets} does by name and return type.
 */
// Reflecting into com.android.phone's hidden CarrierConfigLoader is the entire point of the module.
@SuppressLint("PrivateApi")
public final class CarrierConfigModule extends XposedModule {

    private static final String TAG = "NrThresholds";
    private static final String PHONE_PACKAGE = "com.android.phone";
    private static final String LOADER_CLASS = "com.android.phone.CarrierConfigLoader";

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!PHONE_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        Class<?> loader;
        try {
            loader = Class.forName(LOADER_CLASS, false, param.getClassLoader());
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "class not found: " + LOADER_CLASS, e);
            return;
        }

        // getConfigForSubId and getConfigSubsetForSubIdWithFeature both delegate to the method with
        // a Feature extra, so hooking it once covers every read entry point. Hooking more than one
        // would inject the same key twice.
        Method target = HookTargets.find(loader, PersistableBundle.class,
                "getConfigForSubIdWithFeature", "getConfigForSubId");
        if (target == null) {
            Log.e(TAG, "no carrier config reader found. " + HookTargets.describe(loader));
            return;
        }

        ThresholdSource source;
        try {
            // Read-only in a hooked process; LSPosed pushes updates, which ThresholdSource listens
            // for so a save in the GUI takes effect without restarting anything.
            SharedPreferences remote = getRemotePreferences(ThresholdStore.PREFS_GROUP);
            source = new ThresholdSource(remote);
        } catch (Throwable t) {
            Log.e(TAG, "remote preferences unavailable; module cannot read its configuration", t);
            return;
        }

        hook(target)
                // A high-priority interceptor sits at the head of the chain and wraps the rest, so
                // its post-processing runs last and wins over anything another module writes.
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                // This method is called by the whole system; an exception from an interceptor can
                // take telephony down with it.
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (!(result instanceof PersistableBundle)) {
                        return result;
                    }
                    Thresholds thresholds = source.get();
                    if (thresholds == null) {
                        // Nothing valid to apply: leave the framework's own value untouched rather
                        // than injecting unverified data.
                        return result;
                    }
                    try {
                        ((PersistableBundle) result)
                                .putIntArray(CarrierConfigPatch.KEY, thresholds.toArray());
                    } catch (Throwable t) {
                        Log.w(TAG, "failed to patch " + CarrierConfigPatch.KEY, t);
                    }
                    return result;
                });

        Log.i(TAG, "hooked " + target + "; patching only " + CarrierConfigPatch.KEY);
    }
}
