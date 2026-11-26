package dev.weather.oneplus;
import android.content.Context;
import android.os.Bundle;

/**
 * Air-quality detail page.
 *
 * <p>On a phone this is the original portrait-locked full-screen page. On a pad it is a
 * centred card over the live weather screen, matching the Dart 15-day forecast sheet; see
 * {@link PadPageCard} and the WeatherOriginalPageDialog theme declared in the manifest.</p>
 */
public final class OriginalAqiActivity extends com.miui.weather2map.ActivityAqiDetail {
    /** com.miui.core:style/WeatherAqiTheme. */
    private static final int AQI_THEME = 0x7f130221;

    private boolean padCard;

    @Override protected void attachBaseContext(Context context) {
        padCard = PadPageCard.isPad(context);
        WeatherPagesRuntime.install(context);
        super.attachBaseContext(context);
        WeatherPagesRuntime.install(this);
    }

    @Override protected void onCreate(Bundle state) {
        WeatherPagesRuntime.prepareIntent(this);
        setTheme(AQI_THEME);
        super.onCreate(state);
        if (padCard) PadPageCard.install(this);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackPressed);
        }
    }
}
