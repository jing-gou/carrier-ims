package io.github.nku100.carrierims.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;
import io.github.nku100.carrierims.App;
import io.github.nku100.carrierims.R;
import io.github.nku100.carrierims.config.CarrierConfigPatch;
import io.github.nku100.carrierims.config.LoadResult;
import io.github.nku100.carrierims.config.Preset;
import io.github.nku100.carrierims.config.ThresholdInput;
import io.github.nku100.carrierims.config.Thresholds;
import io.github.nku100.carrierims.config.Validation;
import io.github.nku100.carrierims.data.ThresholdStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The only screen: four numbers, a live preview of the resulting Android signal levels, and the
 * real state of the hook.
 *
 * <p>Every framework call happens on a background thread; every UI mutation happens on the main
 * thread. Success messages are only shown after the framework acknowledged the write, and the hook
 * status comes from {@code getRunningTargets()} — never from a guess.
 */
public final class MainActivity extends Activity {

    private static final String PHONE_PROCESS = "com.android.phone";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ServiceBridge.Listener bridgeListener = this::onServiceChanged;

    private ServiceBridge bridge;

    private EditText[] inputs;
    private TextView[] ranges;
    private TextView statusChip;
    private TextView messageText;
    private SignalBandView bandView;
    private Button applyButton;
    private Button saveButton;
    private Button restoreButton;
    private Button refreshButton;
    private Switch iconSwitch;
    private TextView iconWarning;
    private RadioGroup presetGroup;

    /** Guards {@link Switch#setChecked} while the UI is being synchronised with the real state. */
    private boolean syncingIconSwitch;

    /** Guards {@link RadioGroup#check(int)} while the preset row mirrors the current values. */
    private boolean syncingPreset;

    private volatile boolean loadInFlight;
    private volatile boolean valuesLoaded;
    private enum HookStatus {
        UNKNOWN,
        FRAMEWORK_UNAVAILABLE,
        ACTIVE,
        INACTIVE,
        RESTART_REQUIRED,
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bridge = ((App) getApplication()).bridge();

        inputs = new EditText[]{
                findViewById(R.id.input1),
                findViewById(R.id.input2),
                findViewById(R.id.input3),
                findViewById(R.id.input4),
        };
        ranges = new TextView[]{
                findViewById(R.id.rangeNone),
                findViewById(R.id.rangePoor),
                findViewById(R.id.rangeModerate),
                findViewById(R.id.rangeGood),
                findViewById(R.id.rangeExcellent),
        };
        statusChip = findViewById(R.id.statusChip);
        messageText = findViewById(R.id.messageText);
        bandView = findViewById(R.id.bandView);
        applyButton = findViewById(R.id.applyButton);
        saveButton = findViewById(R.id.saveButton);
        restoreButton = findViewById(R.id.restoreButton);
        refreshButton = findViewById(R.id.refreshButton);
        iconSwitch = findViewById(R.id.iconSwitch);
        iconWarning = findViewById(R.id.iconWarning);
        presetGroup = findViewById(R.id.presetGroup);

        ((TextView) findViewById(R.id.footnoteText))
                .setText(getString(R.string.footnote, CarrierConfigPatch.KEY));

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updatePreview();
            }
        };
        for (EditText input : inputs) {
            input.addTextChangedListener(watcher);
        }
        inputs[3].setImeOptions(EditorInfo.IME_ACTION_DONE);
        inputs[3].setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard();
                return true;
            }
            return false;
        });

        applyButton.setOnClickListener(v -> submit(true));
        saveButton.setOnClickListener(v -> submit(false));
        restoreButton.setOnClickListener(v -> restoreDefaults());
        refreshButton.setOnClickListener(v -> refreshStatus(true));
        presetGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (syncingPreset) {
                return;
            }
            Preset preset = presetOf(checkedId);
            if (preset == null) {
                return;
            }
            // Presets only fill the fields; nothing is written until the user taps Apply.
            fill(preset.thresholds());
            showMessage(getString(R.string.msg_preset_loaded, presetLabel(preset)),
                    R.color.text_muted);
        });
        iconSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (syncingIconSwitch) {
                return;
            }
            if (isChecked) {
                confirmHideIcon();
            } else {
                setIconVisible(true);
            }
        });

        restoreDefaults(true);
        loadAsync();
    }

    @Override
    protected void onStart() {
        super.onStart();
        bridge.addListener(bridgeListener);
    }

    @Override
    protected void onStop() {
        bridge.removeListener(bridgeListener);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Someone may have re-enabled the alias from outside; the widget must not lie.
        syncIconSwitch();
        refreshStatus(false);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void onServiceChanged() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        refreshStatus(false);
        if (!valuesLoaded) {
            loadAsync();
        }
    }

    // ---------------------------------------------------------------- loading

    private void loadAsync() {
        if (loadInFlight) {
            return;
        }
        loadInFlight = true;
        io.execute(() -> {
            XposedService service = bridge.service();
            HookStatus status = service == null
                    ? HookStatus.FRAMEWORK_UNAVAILABLE
                    : probeStatus(service);
            LoadResult result = null;
            String failure = null;
            if (service == null) {
                failure = getString(R.string.msg_framework_unavailable);
            } else {
                try {
                    ThresholdStore store = new ThresholdStore(
                            service.getRemotePreferences(ThresholdStore.PREFS_GROUP));
                    result = store.load();
                } catch (Throwable t) {
                    failure = t.toString();
                }
            }
            final LoadResult loaded = result;
            final String error = failure;
            final HookStatus probed = status;
            main.post(() -> {
                loadInFlight = false;
                valuesLoaded = true;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setStatus(probed);
                renderLoaded(loaded, error);
            });
        });
    }

    private void renderLoaded(LoadResult result, String failure) {
        if (failure != null) {
            showMessage(failure, R.color.error);
            return;
        }
        if (result == null) {
            showMessage(getString(R.string.msg_framework_unavailable), R.color.error);
            return;
        }
        switch (result.status()) {
            case VALID:
                fill(result.thresholds());
                hideMessage();
                break;
            case ABSENT:
                fill(Thresholds.defaults());
                showMessage(getString(R.string.msg_loaded_absent), R.color.text_muted);
                break;
            case INVALID:
            default:
                fill(Thresholds.defaults());
                showMessage(getString(R.string.msg_stored_invalid, result.message()),
                        R.color.error);
                break;
        }
    }

    // ---------------------------------------------------------------- actions

    private void submit(boolean apply) {
        ThresholdInput.Result parsed = parse();
        if (!parsed.isOk()) {
            showValidationError(parsed.validation());
            return;
        }
        clearFieldErrors();
        final Thresholds thresholds = parsed.thresholds();
        setBusy(true);

        io.execute(() -> {
            XposedService service = bridge.service();
            boolean saved = false;
            String error = null;
            HookStatus status;
            if (service == null) {
                status = HookStatus.FRAMEWORK_UNAVAILABLE;
                error = getString(R.string.msg_framework_unavailable);
            } else {
                status = probeStatus(service);
                ThresholdStore.WriteResult write;
                try {
                    write = new ThresholdStore(
                            service.getRemotePreferences(ThresholdStore.PREFS_GROUP))
                            .save(thresholds, System.currentTimeMillis());
                } catch (Throwable t) {
                    write = null;
                    error = t.toString();
                }
                if (write != null) {
                    saved = write.isOk();
                    error = saved ? null : write.message();
                }
                if (error == null) {
                    error = getString(R.string.msg_commit_failed);
                }
            }

            final boolean ok = saved;
            final String failure = error;
            final HookStatus probed = status;
            final boolean applied = apply;
            main.post(() -> {
                setBusy(false);
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setStatus(probed);
                if (ok) {
                    fill(thresholds);
                    if (applied) {
                        showMessage(getString(R.string.msg_applied), R.color.accent);
                    } else {
                        showMessage(getString(R.string.msg_saved), R.color.accent);
                    }
                    appendStatusGuidance(probed);
                } else {
                    // Not a validation error: the configuration was valid but the write failed.
                    showMessage(failure, R.color.error);
                }
            });
        });
    }

    private void restoreDefaults() {
        restoreDefaults(false);
    }

    private void restoreDefaults(boolean quiet) {
        fill(Thresholds.defaults());
        if (!quiet) {
            showMessage(getString(R.string.msg_defaults_loaded), R.color.text_muted);
        }
    }

    private void refreshStatus(boolean announce) {
        final XposedService service = bridge.service();
        io.execute(() -> {
            HookStatus status = service == null
                    ? HookStatus.FRAMEWORK_UNAVAILABLE
                    : probeStatus(service);
            main.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setStatus(status);
                if (announce) {
                    appendStatusGuidance(status);
                }
            });
        });
    }

    // ---------------------------------------------------------------- helpers

    private HookStatus probeStatus(XposedService service) {
        try {
            if (service.getApiVersion() < XposedService.API_102) {
                return HookStatus.UNKNOWN;
            }
            for (HookedTarget target : service.getRunningTargets()) {
                if (PHONE_PROCESS.equals(target.getProcessName())) {
                    return target.getState() == HookedTarget.State.UP_TO_DATE
                            ? HookStatus.ACTIVE
                            : HookStatus.RESTART_REQUIRED;
                }
            }
            return HookStatus.INACTIVE;
        } catch (Throwable t) {
            return HookStatus.UNKNOWN;
        }
    }

    private void setStatus(HookStatus status) {
        int text;
        int background;
        int foreground;
        switch (status) {
            case ACTIVE:
                text = R.string.status_hook_active;
                background = R.color.chip_active_bg;
                foreground = R.color.accent;
                break;
            case INACTIVE:
                text = R.string.status_hook_inactive;
                background = R.color.chip_error_bg;
                foreground = R.color.error;
                break;
            case RESTART_REQUIRED:
                text = R.string.status_restart_required;
                background = R.color.chip_warn_bg;
                foreground = R.color.warn;
                break;
            case FRAMEWORK_UNAVAILABLE:
                text = R.string.status_framework_unavailable;
                background = R.color.chip_idle;
                foreground = R.color.text_muted;
                break;
            case UNKNOWN:
            default:
                text = R.string.status_unknown;
                background = R.color.chip_idle;
                foreground = R.color.text_muted;
                break;
        }
        statusChip.setText(text);
        statusChip.setTextColor(getColor(foreground));

        GradientDrawable chip = new GradientDrawable();
        chip.setShape(GradientDrawable.RECTANGLE);
        chip.setCornerRadius(getResources().getDisplayMetrics().density * 999f);
        chip.setColor(getColor(background));
        chip.setStroke((int) (getResources().getDisplayMetrics().density), getColor(foreground));
        statusChip.setBackground(chip);
    }

    private void appendStatusGuidance(HookStatus status) {
        String guidance = null;
        switch (status) {
            case INACTIVE:
                guidance = getString(R.string.msg_hook_inactive);
                break;
            case RESTART_REQUIRED:
                guidance = getString(R.string.msg_restart_required);
                break;
            case FRAMEWORK_UNAVAILABLE:
                guidance = getString(R.string.msg_framework_unavailable);
                break;
            default:
                break;
        }
        if (guidance == null) {
            return;
        }
        CharSequence current = messageText.getVisibility() == View.VISIBLE
                ? messageText.getText()
                : "";
        if (current.length() > 0) {
            showMessage(current + "\n" + guidance, R.color.text_muted);
        } else {
            showMessage(guidance, R.color.text_muted);
        }
    }

    private ThresholdInput.Result parse() {
        String[] values = new String[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            values[i] = inputs[i].getText().toString();
        }
        return ThresholdInput.parse(values);
    }

    private void showValidationError(Validation validation) {
        clearFieldErrors();
        showMessage(getString(R.string.msg_invalid) + " " + validation.message(), R.color.error);
        int index = validation.index();
        if (index >= 0 && index < inputs.length) {
            inputs[index].setError(validation.message());
            inputs[index].requestFocus();
        }
    }

    private void clearFieldErrors() {
        for (EditText input : inputs) {
            input.setError(null);
        }
    }

    private void updatePreview() {
        ThresholdInput.Result parsed = parse();
        if (parsed.isOk()) {
            Thresholds thresholds = parsed.thresholds();
            bandView.setThresholds(thresholds.toArray());
            for (int level = Thresholds.LEVEL_NONE; level <= Thresholds.LEVEL_EXCELLENT; level++) {
                ranges[level].setText(thresholds.bandLabel(level));
            }
        } else {
            bandView.setThresholds(null);
            for (TextView range : ranges) {
                range.setText("—");
            }
        }
        updatePresetSelection(parsed);
    }

    /** Mirrors the current values onto the preset row; custom values clear the selection. */
    private void updatePresetSelection(ThresholdInput.Result parsed) {
        Preset match = parsed.isOk() ? Preset.of(parsed.thresholds()) : null;
        syncingPreset = true;
        if (match == null) {
            presetGroup.clearCheck();
        } else {
            presetGroup.check(presetButtonId(match));
        }
        syncingPreset = false;
    }

    private static int presetButtonId(Preset preset) {
        switch (preset) {
            case SAMSUNG:
                return R.id.presetSamsung;
            case VIVO:
                return R.id.presetVivo;
            case HUAWEI:
                return R.id.presetHuawei;
            case OPPO:
            default:
                return R.id.presetOppo;
        }
    }

    private static Preset presetOf(int checkedId) {
        if (checkedId == R.id.presetSamsung) {
            return Preset.SAMSUNG;
        }
        if (checkedId == R.id.presetOppo) {
            return Preset.OPPO;
        }
        if (checkedId == R.id.presetVivo) {
            return Preset.VIVO;
        }
        if (checkedId == R.id.presetHuawei) {
            return Preset.HUAWEI;
        }
        return null;
    }

    private String presetLabel(Preset preset) {
        switch (preset) {
            case SAMSUNG:
                return getString(R.string.preset_samsung);
            case VIVO:
                return getString(R.string.preset_vivo);
            case HUAWEI:
                return getString(R.string.preset_huawei);
            case OPPO:
            default:
                return getString(R.string.preset_oppo);
        }
    }

    private void fill(Thresholds thresholds) {
        for (int i = 0; i < inputs.length; i++) {
            inputs[i].setText(String.valueOf(thresholds.get(i)));
        }
        updatePreview();
    }

    private void showMessage(CharSequence message, int colorRes) {
        messageText.setText(message);
        messageText.setTextColor(getColor(colorRes));
        messageText.setVisibility(View.VISIBLE);
    }

    private void hideMessage() {
        messageText.setVisibility(View.GONE);
    }

    private void setBusy(boolean busy) {
        applyButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
        restoreButton.setEnabled(!busy);
        refreshButton.setEnabled(!busy);
    }

    // ------------------------------------------------------------ launcher icon

    private void syncIconSwitch() {
        syncingIconSwitch = true;
        iconSwitch.setChecked(!AppIcon.isVisible(this));
        syncingIconSwitch = false;
        updateIconWarning();
    }

    private void setIconVisible(boolean visible) {
        try {
            AppIcon.setVisible(this, visible);
        } catch (Throwable t) {
            showMessage(t.toString(), R.color.error);
            syncIconSwitch();
            return;
        }
        updateIconWarning();
        Toast.makeText(this,
                visible ? R.string.icon_restored_toast : R.string.icon_hidden_toast,
                Toast.LENGTH_SHORT).show();
    }

    /** Hiding the icon is the one action here that removes the obvious way back, so ask first. */
    private void confirmHideIcon() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.icon_hide_title)
                .setMessage(R.string.icon_hide_message)
                .setNegativeButton(R.string.icon_hide_cancel, (dialog, which) -> syncIconSwitch())
                .setOnCancelListener(dialog -> syncIconSwitch())
                .setPositiveButton(R.string.icon_hide_confirm,
                        (dialog, which) -> setIconVisible(false))
                .show();
    }

    private void updateIconWarning() {
        boolean visible = AppIcon.isVisible(this);
        iconWarning.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        View focused = getCurrentFocus();
        if (imm != null && focused != null) {
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            focused.clearFocus();
        }
    }
}
