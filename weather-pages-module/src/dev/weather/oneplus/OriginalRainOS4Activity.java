package dev.weather.oneplus;
import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;

/**
 * OS4 (HyperOS) variant of the minute-rain page. Same pad presentation as
 * {@link OriginalRainActivity}; this class exists because the OS4 original activity
 * consumes different intent extras.
 */
public final class OriginalRainOS4Activity extends com.miui.weather2map.ActivityMinuteRainOS4 {
    /** com.miui.core:style/WeatherLandPage — transparent window background, translucent. */
    private static final int LAND_PAGE_THEME = 0x7f130223;
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
        if (padCard && (resid == MINUTE_RAIN_THEME || resid == DAY_NIGHT_FLOATING
                || resid == DAY_NIGHT_NO_TITLE)) {
            super.setTheme(LAND_PAGE_THEME);
            return;
        }
        super.setTheme(resid);
    }

    @Override protected void onCreate(Bundle state) {
        WeatherPagesRuntime.prepareIntent(this);
        super.onCreate(state);
        if (padCard) PadPageCard.install(this);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackPressed);
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) geocodeFallback.install();
        return super.dispatchTouchEvent(event);
    }
}
