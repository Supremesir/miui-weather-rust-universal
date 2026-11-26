package dev.weather.oneplus;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

/**
 * Presents an original weather detail page as a centred card on a pad.
 *
 * <p>The activity declares {@code WeatherOriginalPageDialog} in AndroidManifest.xml; the
 * values-sw600dp variant makes that window floating and translucent, which is what lets it
 * be sized to a card instead of the task bounds. This class supplies the card chrome on top:
 * 546dp x 636dp (MIUI's own floating-window size, and the size of the Dart 15-day sheet
 * measured on device), a 36dp corner radius and the #F2F2F2 sheet colour. The 20% black
 * scrim comes from the window's own dim, so it covers the whole display.</p>
 *
 * <p>The slide-in is a window animation declared in that theme, not a view animation here.
 * A view-level animation only starts once the window is already on screen, which showed as a
 * flash of the finished card before it jumped into place.</p>
 *
 * <p>The manifest theme matters for a third, less obvious reason: ActivityManager decides from
 * ActivityInfo.isTranslucentOrFloating() - resolved from that theme before the activity starts -
 * whether the weather activity below may stay alive. When it reads opaque, the activity below is
 * stopped and its SurfaceView destroyed, which is the black background this class exists to
 * replace. A runtime setTheme() cannot influence that decision.</p>
 */
public final class PadPageCard {
    private static final int WIDTH_DP = 546;
    private static final int HEIGHT_DP = 636;
    /** Caps for windows too small to hold the sheet at its natural size. */
    private static final int WIDTH_MAX_PERCENT = 90;
    private static final int HEIGHT_MAX_PERCENT = 80;
    private static final float CORNER_DP = 36f;
    private static final int SHEET_COLOR = 0xfff2f2f2;
    private static final long FRAME_MILLIS = 16L;
    /** Duration of the sheet's slide-in. */
    private static final long SLIDE_MILLIS = 260L;

    private PadPageCard() { }

    /** True on a pad-sized window, matching the test used for the Dart pad layout mode. */
    public static boolean isPad(Context context) {
        return context != null
                && context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    /**
     * Applies the card chrome on a pad, or restores the original portrait-locked full-screen
     * page on a phone. Call after the page's setContentView(), when the decor exists.
     */
    public static void install(Activity activity) {
        if (!isPad(activity)) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return;
        }
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        Window window = activity.getWindow();
        // The original pages set their own opaque page theme during onCreate, so re-assert a
        // translucent surface here; otherwise the window would hide the weather screen.
        window.setFormat(PixelFormat.TRANSLUCENT);

        int width = sheetWidth(activity);
        int height = sheetHeight(activity);
        // A floating window honours an explicit size; a full-screen activity window ignores it.
        window.setLayout(width, height);

        WindowManager.LayoutParams params = window.getAttributes();
        params.gravity = Gravity.CENTER;
        window.setAttributes(params);

        GradientDrawable background = new GradientDrawable();
        background.setColor(SHEET_COLOR);
        background.setCornerRadius(dp(activity, CORNER_DP));

        View decor = window.getDecorView();
        decor.setBackground(background);
        // Clip the page's own layout, map and scrolling to the sheet's rounded corners.
        decor.setClipToOutline(true);
        decor.setOutlineProvider(roundedOutline(activity));

        // Start transparent in the same frame the decor is created, so the very first drawn frame
        // is already "not yet arrived". Without this the window shows at its final position for a
        // frame, which is the flash, and only then animates.
        decor.setAlpha(0f);

        // Centring owns the window's y offset, so the slide starts only once the resting position
        // is final; otherwise the two would fight over the same value.
        centreWhenSized(activity, decor);
    }

    /** Distance the sheet travels: just under the 15-day sheet's own rise. */
    private static float dir(Activity activity) {
        return 36f * activity.getResources().getDisplayMetrics().density;
    }

    /**
     * Slides the sheet up into place.
     *
     * <p>A view animation is used rather than the theme's windowAnimationStyle: on a floating
     * window the activity transition owns the enter animation, and setting one here was measured
     * as having no effect. Starting from a pre-offset, pre-transparent decor means the motion
     * begins from the first visible frame instead of after a jump.</p>
     *
     * <p>Moves the decor with its window offset rather than translating its drawing: a
     * translation is clipped by the window surface, which would cut the top of the sheet off.</p>
     */
    private static void slideIn(final Activity activity, final View decor) {
        final Window window = activity.getWindow();
        final int restY = window.getAttributes().y;
        final int startY = restY + Math.round(dir(activity));
        window.getAttributes().y = startY;
        window.setAttributes(window.getAttributes());
        decor.setAlpha(0f);
        // Wait one frame so the window surface is attached before the animation is timed;
        // otherwise the animator can finish while the window is not yet composited.
        decor.postOnAnimation(new Runnable() {
            @Override public void run() {
                android.animation.ValueAnimator animator = android.animation.ValueAnimator
                        .ofInt(startY, restY);
                animator.setDuration(SLIDE_MILLIS);
                animator.setInterpolator(new DecelerateInterpolator());
                animator.addUpdateListener(animation -> {
                    WindowManager.LayoutParams params = window.getAttributes();
                    params.y = (Integer) animation.getAnimatedValue();
                    window.setAttributes(params);
                });
                animator.start();
                decor.animate().alpha(1f)
                        .setDuration(SLIDE_MILLIS)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }
        });
    }

    /**
     * A floating window is centred inside the safe area, but the 15-day sheet is centred on the
     * whole display. Moves the window itself rather than a view translation: translating a view
     * only shifts its drawing inside the surface, which clips the overflowing edges.
     *
     * <p>Waits for the window manager to settle the final frame - before that the window has no
     * size and sits at the safe-area default. It then runs once more, so a setAttributes() that
     * only landed after the first measurement is still picked up; the second run measures the
     * position the first one produced, so its delta is zero and the offset cannot accumulate.</p>
     */
    private static void centreWhenSized(final Activity activity, final View decor) {
        final boolean[] settled = {false};
        decor.post(new Runnable() {
            @Override public void run() {
                if (decor.getHeight() == 0) {
                    decor.postDelayed(this, FRAME_MILLIS);
                    return;
                }
                centreOnDisplay(activity, decor);
                if (!settled[0]) {
                    settled[0] = true;
                    decor.postDelayed(this, FRAME_MILLIS);
                    return;
                }
                // Centring owns the window's y offset, so the slide starts only once the resting
                // position is final; otherwise the two would fight over the same value.
                slideIn(activity, decor);
            }
        });
    }

    private static void centreOnDisplay(Activity activity, View decor) {
        Rect display = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
        int[] location = new int[2];
        decor.getLocationOnScreen(location);
        Window window = activity.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        // The window manager applies gravity.CENTER and then offsets by x/y, so folding the
        // measured delta into x/y moves the frame without changing the requested size.
        params.x += Math.round(display.exactCenterX() - (location[0] + decor.getWidth() / 2f));
        params.y += Math.round(display.exactCenterY() - (location[1] + decor.getHeight() / 2f));
        window.setAttributes(params);
    }

    private static ViewOutlineProvider roundedOutline(Activity activity) {
        final float radius = dp(activity, CORNER_DP);
        return new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        };
    }

    private static int sheetWidth(Activity activity) {
        int cap = Math.max(1, dp(activity,
                activity.getResources().getConfiguration().screenWidthDp * WIDTH_MAX_PERCENT / 100));
        return Math.min(dp(activity, WIDTH_DP), cap);
    }

    private static int sheetHeight(Activity activity) {
        int cap = Math.max(1, dp(activity,
                activity.getResources().getConfiguration().screenHeightDp * HEIGHT_MAX_PERCENT / 100));
        return Math.min(dp(activity, HEIGHT_DP), cap);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static float dp(Activity activity, float value) {
        return value * activity.getResources().getDisplayMetrics().density;
    }
}
