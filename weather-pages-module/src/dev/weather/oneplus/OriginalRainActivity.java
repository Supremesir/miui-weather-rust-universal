package dev.weather.oneplus;
import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;

/**
 * Legacy minute-rain page.
 *
 * <p>On a phone this is the original portrait-locked full-screen page. On a pad it is a
 * centred card over the live weather screen, matching the Dart 15-day forecast sheet; see
 * {@link PadPageCard} and the WeatherOriginalPageDialog theme declared in the manifest.</p>
 */
public final class OriginalRainActivity extends com.miui.weather2map.ActivityMinuteRain {
    /** com.miui.core:style/WeatherLandPage — transparent window background, translucent. */
    private static final int LAND_PAGE_THEME = 0x7f130223;
    /** Themes the original onCreate sets for itself; all paint an opaque page. */
    private static final int MINUTE_RAIN_THEME = 0x7f130225;
    private static final int DAY_NIGHT_FLOATING = 0x7f1301ee;
    private static final int DAY_NIGHT_NO_TITLE = 0x7f1301f0;

    private final RainMapGeocodeFallback geocodeFallback = new RainMapGeocodeFallback(this);
    private boolean padCard;

    @Override protected void attachBaseContext(Context context) {
        padCard = PadPageCard.isPad(context);
        WeatherPagesRuntime.install(context);
        super.attachBaseContext(context);
        WeatherPagesRuntime.install(this);
    }

    @Override public void setTheme(int resid) {
        // The original page resets its theme during onCreate. On a pad an opaque page theme
        // would hide the weather screen, so those are redirected to the translucent landscape
        // page theme.
        if (padCard && (resid == MINUTE_RAIN_THEME || resid == DAY_NIGHT_FLOATING
                || resid == DAY_NIGHT_NO_TITLE)) {
            super.setTheme(LAND_PAGE_THEME);
            return;
        }
        super.setTheme(resid);
    }

    @Override protected void onCreate(Bundle state) {
        WeatherPagesRuntime.prepareIntent(this);
        if (!padCard) setTheme(MINUTE_RAIN_THEME);
        super.onCreate(state);
        if (padCard) PadPageCard.install(this);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackPressed);
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        // The original fragment creates its map controller after Activity.onCreate.
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) geocodeFallback.install();
        return super.dispatchTouchEvent(event);
    }
}
