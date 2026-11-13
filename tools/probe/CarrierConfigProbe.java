import android.os.IBinder;
import android.os.PersistableBundle;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Reads carrier config straight off the binder as the shell uid.
 *
 * <p>Three modes:
 *
 * <ul>
 *   <li>{@code --dump}: print every key as {@code key=value}, sorted. Run it once with the module
 *       disabled and once with it enabled, then diff: the only difference must be the threshold
 *       key. That is how "other CarrierConfig keys are unaffected" is verified on a real device.</li>
 *   <li>{@code --expect=t1,t2,t3,t4}: compare the threshold key against the given values and exit
 *       non-zero on mismatch.</li>
 *   <li>no flags: print the threshold key and whether it is present.</li>
 * </ul>
 *
 * <p>The expected values are passed in rather than hardcoded: after this refactor they are user
 * data, so a hardcoded table would be a tautology that always agreed with itself.
 */
public class CarrierConfigProbe {

    private static final String KEY = "5g_nr_ssrsrp_thresholds_int_array";

    public static void main(String[] args) throws Exception {
        int subId = 1;
        boolean dump = false;
        int[] expect = null;

        for (String arg : args) {
            if ("--dump".equals(arg)) {
                dump = true;
            } else if (arg.startsWith("--expect=")) {
                expect = parseInts(arg.substring("--expect=".length()));
            } else if (!arg.startsWith("--")) {
                subId = Integer.parseInt(arg);
            } else {
                System.out.println("FATAL unknown flag " + arg);
                System.exit(2);
            }
        }

        PersistableBundle config = readConfig(subId);

        if (dump) {
            List<String> keys = new ArrayList<>(config.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                System.out.println(key + "=" + format(config.get(key)));
            }
            System.out.println("# keys=" + keys.size() + " subId=" + subId);
            return;
        }

        int[] actual = config.getIntArray(KEY);
        if (expect == null) {
            System.out.println(KEY + "=" + format(actual));
            System.out.println("present=" + (actual != null));
            return;
        }

        boolean ok = Arrays.equals(expect, actual);
        System.out.printf("%-4s %-40s want=%-24s got=%s%n",
                ok ? "PASS" : "FAIL", KEY, Arrays.toString(expect), format(actual));
        System.out.println(ok ? "RESULT threshold key matches" : "RESULT threshold key mismatch");
        System.exit(ok ? 0 : 1);
    }

    private static PersistableBundle readConfig(int subId) throws Exception {
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "carrier_config");
        if (binder == null) {
            System.out.println("FATAL carrier_config service not found");
            System.exit(2);
        }

        Class<?> stub = Class.forName("com.android.internal.telephony.ICarrierConfigLoader$Stub");
        Object loader = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);

        Method reader = null;
        for (String name : new String[]{"getConfigForSubIdWithFeature", "getConfigForSubId"}) {
            for (Method m : loader.getClass().getMethods()) {
                if (m.getName().equals(name) && m.getReturnType() == PersistableBundle.class
                        && (reader == null || m.getParameterCount() < reader.getParameterCount())) {
                    reader = m;
                }
            }
            if (reader != null) {
                break;
            }
        }
        if (reader == null) {
            System.out.println("FATAL no reader method on " + loader.getClass());
            System.exit(2);
            return null;
        }

        Object[] callArgs = new Object[reader.getParameterCount()];
        callArgs[0] = subId;
        for (int i = 1; i < callArgs.length; i++) {
            callArgs[i] = i == 1 ? "com.android.shell" : null;
        }

        System.err.println("reader " + reader.getName() + "/" + reader.getParameterCount()
                + " subId=" + subId);

        PersistableBundle config = (PersistableBundle) reader.invoke(loader, callArgs);
        if (config == null) {
            System.out.println("FATAL reader returned null");
            System.exit(2);
        }
        return config;
    }

    private static int[] parseInts(String csv) {
        String[] parts = csv.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("expected 4 comma separated values: " + csv);
        }
        int[] values = new int[4];
        for (int i = 0; i < 4; i++) {
            values[i] = Integer.parseInt(parts[i].trim());
        }
        return values;
    }

    private static String format(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof int[]) {
            return Arrays.toString((int[]) value);
        }
        if (value instanceof Object[]) {
            return Arrays.deepToString((Object[]) value);
        }
        return String.valueOf(value);
    }
}
