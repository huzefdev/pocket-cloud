package dev.huzef.pocketcloud.core;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;

public final class GlassTheme {

    public static final int BG_VOID = Color.parseColor("#0B0B0D");
    public static final int BG_CARD = Color.parseColor("#16161A");
    public static final int BG_CARD_ELEVATED = Color.parseColor("#1E1D22");
    public static final int ACCENT_AMBER = Color.parseColor("#EA580C");
    public static final int ACCENT_AMBER_GLOW = Color.parseColor("#F97316");
    public static final int ACCENT_AMBER_SOFT = Color.parseColor("#33EA580C");
    public static final int EMERALD_LIVE = Color.parseColor("#10B981");
    public static final int ROSE_OFFLINE = Color.parseColor("#F43F5E");
    public static final int TEXT_PRIMARY = Color.parseColor("#F8FAFC");
    public static final int TEXT_SECONDARY = Color.parseColor("#9CA3AF");
    public static final int TEXT_MUTED = Color.parseColor("#6B7280");
    public static final int BORDER_SUBTLE = Color.parseColor("#26FFFFFF");
    public static final int BORDER_WARM = Color.parseColor("#55EA580C");

    private GlassTheme() {}

    public static int dp(Context ctx, float dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    public static Drawable createRootAmbientBackground() {
        GradientDrawable base = new GradientDrawable();
        base.setColor(BG_VOID);

        GradientDrawable topWarmGlow = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#2EEB5E10"), Color.parseColor("#0A16161A"), Color.TRANSPARENT}
        );
        topWarmGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        topWarmGlow.setGradientRadius(900f);
        topWarmGlow.setGradientCenter(0.18f, 0.04f);

        return new LayerDrawable(new Drawable[]{base, topWarmGlow});
    }

    public static Drawable createFrostedCard(Context ctx, float radiusDp, boolean warmCornerGlow) {
        int r = dp(ctx, radiusDp);
        GradientDrawable fill = new GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                warmCornerGlow
                        ? new int[]{Color.parseColor("#CC141417"), Color.parseColor("#D919181D"), Color.parseColor("#42EA580C")}
                        : new int[]{Color.parseColor("#CC141417"), Color.parseColor("#E61A1A20")}
        );
        fill.setCornerRadius(r);
        fill.setStroke(dp(ctx, 1f), warmCornerGlow ? BORDER_WARM : BORDER_SUBTLE);
        return fill;
    }

    public static Drawable createActiveHighlightCard(Context ctx, float radiusDp) {
        int r = dp(ctx, radiusDp);
        GradientDrawable fill = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{ACCENT_AMBER_GLOW, ACCENT_AMBER}
        );
        fill.setCornerRadius(r);
        fill.setStroke(dp(ctx, 1f), Color.parseColor("#66FFFFFF"));
        return fill;
    }

    public static Drawable createPillButton(Context ctx, int fillColor, int strokeColor) {
        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadius(dp(ctx, 999f));
        pill.setColor(fillColor);
        if (strokeColor != Color.TRANSPARENT) {
            pill.setStroke(dp(ctx, 1f), strokeColor);
        }
        return pill;
    }

    public static Drawable createCircleBadge(Context ctx, int fillColor, int strokeColor) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(fillColor);
        if (strokeColor != Color.TRANSPARENT) {
            circle.setStroke(dp(ctx, 1f), strokeColor);
        }
        return circle;
    }

    public static void animateGlassEntry(View view) {
        if (view == null) return;
        AnimationSet set = new AnimationSet(true);
        set.setInterpolator(new DecelerateInterpolator(1.8f));
        set.setDuration(230L);

        AlphaAnimation fade = new AlphaAnimation(0.0f, 1.0f);
        TranslateAnimation slide = new TranslateAnimation(
                TranslateAnimation.RELATIVE_TO_SELF, 0f,
                TranslateAnimation.RELATIVE_TO_SELF, 0f,
                TranslateAnimation.ABSOLUTE, 26f,
                TranslateAnimation.ABSOLUTE, 0f
        );
        set.addAnimation(fade);
        set.addAnimation(slide);
        view.startAnimation(set);
    }

    public static void attachSpringPress(final View target) {
        if (target == null) return;
        target.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int act = event.getAction();
                if (act == MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.965f).scaleY(0.965f).alpha(0.9f).setDuration(90L).start();
                } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(140L).start();
                }
                return false;
            }
        });
    }
}
