package dev.weather.oneplus;
import android.content.Context;
import android.os.Bundle;

/**
 * OS4 (HyperOS) variant of the air-quality detail page. Same pad presentation as
 * {@link OriginalAqiActivity}. The OS4 original builds its content lazily through a bottom
 * sheet, so the card is applied on the first layout pass instead of straight after onCreate.
 */
public final class OriginalAqiOS4Activity extends com.miui.weather2map.ActivityAqiDetailOS4 {
    private boolean padCard;

    @Override protected void attachBaseContext(Context context) {
        padCard = PadPageCard.isPad(context);
        WeatherPagesRuntime.install(context);
        super.attachBaseContext(context);
        WeatherPagesRuntime.install(this);
    }

    @Override protected void onCreate(Bundle state) {
        WeatherPagesRuntime.prepareIntent(this);
        super.onCreate(state);
        if (padCard) {
            // The OS4 page wraps itself in a bottom sheet after onCreate; apply the card once
            // that first layout pass has run.
            getWindow().getDecorView().post(() -> PadPageCard.install(this));
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackPressed);
        }
    }
}
