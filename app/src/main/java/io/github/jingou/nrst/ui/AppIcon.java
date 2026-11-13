package io.github.jingou.nrst.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Shows or hides the launcher entry.
 *
 * <p>The launcher icon is a separate {@code activity-alias}, so it can be disabled without touching
 * {@link MainActivity}: the activity stays exported and can still be started from LSPosed or adb
 * while the icon is gone. That is the only way back in, by design — there is no secret dial code
 * and no notification.
 */
public final class AppIcon {

    /** Must match {@code activity-alias android:name} in the manifest. */
    private static final String ALIAS = "io.github.jingou.nrst.ui.LauncherAlias";

    private AppIcon() {
    }

    private static ComponentName alias(Context context) {
        return new ComponentName(context.getPackageName(), ALIAS);
    }

    /**
     * Whether the launcher entry is currently enabled.
     *
     * <p>Reads the component state from PackageManager rather than a preference: the component
     * state is the single source of truth and survives reboots, so the two can never disagree.
     */
    public static boolean isVisible(Context context) {
        int state = context.getPackageManager().getComponentEnabledSetting(alias(context));
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
    }

    public static void setVisible(Context context, boolean visible) {
        context.getPackageManager().setComponentEnabledSetting(
                alias(context),
                visible
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                // Do not kill our own process: the user is looking at this screen.
                PackageManager.DONT_KILL_APP);
    }
}
