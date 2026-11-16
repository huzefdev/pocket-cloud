package dev.huzef.pocketcloud.core;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

public final class GlassTheme {

    // Pitch-Black & Warm Amber-Copper Glass Palette (Matching Reference Blueprint)
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
}
