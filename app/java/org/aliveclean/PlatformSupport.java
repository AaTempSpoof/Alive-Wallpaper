package org.aliveclean;

import android.content.Context;
import android.os.Build;
import java.util.Locale;

/** Small capability checks used by the settings flow, never by the render loop. */
final class PlatformSupport {
    private static final String OPLUS_AOD = "com.oplus.aod.AodMachineHelperProvider";

    static boolean isXiaomiFamily() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        String brand = Build.BRAND == null ? "" : Build.BRAND.toLowerCase(Locale.ROOT);
        return manufacturer.matches(".*(xiaomi|redmi|poco).*") || brand.matches(".*(xiaomi|redmi|poco).*");
    }

    static boolean hasColorOsAod(Context context) {
        return context.getPackageManager().resolveContentProvider(OPLUS_AOD, 0) != null;
    }

    private PlatformSupport() {}
}
