package miui.os;
/** Configuration flags consumed by the China-edition weather module. */
public final class Build {
    public static boolean IS_INTERNATIONAL_BUILD = false;
    public static boolean IS_STABLE_VERSION = true;
    public static boolean IS_DEVELOPMENT_VERSION = false;
    // The original pages read these reflectively. A 600dp smallest width is
    // Android's own tablet threshold, so large screens get their pad layout
    // (phone-sized windows stay unchanged).
    public static boolean IS_TABLET =
            android.content.res.Resources.getSystem().getConfiguration().smallestScreenWidthDp >= 600;
    public static boolean IS_MIPAD = IS_TABLET;
    public static boolean IS_ALPHA_BUILD = false;
    public static boolean IS_MIUI_LITE_VERSION = false;
    public static boolean checkRegion(String region) { return "CN".equalsIgnoreCase(region); }
}
