package dev.huzef.pocketcloud.core;

import android.content.Context;
import android.content.SharedPreferences;
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

    public static final String PREFS_THEME = "pocketcloud_glass_theme_v1";

    public static class ThemePreset {
        public final String id;
        public final String name;
        public final String tag;
        public final int primary;
        public final int glow;
        public final int soft;
        public final int borderWarm;
        public final int ambientRadial;
        public final int cardCornerGlow;
        public final int bgVoid;
        public final int bgCard;
        public final int bgElevated;

        public ThemePreset(String id, String name, String tag,
                           String primary, String glow, String soft,
                           String borderWarm, String ambientRadial, String cardCornerGlow,
                           String bgVoid, String bgCard, String bgElevated) {
            this.id = id;
            this.name = name;
            this.tag = tag;
            this.primary = Color.parseColor(primary);
            this.glow = Color.parseColor(glow);
            this.soft = Color.parseColor(soft);
            this.borderWarm = Color.parseColor(borderWarm);
            this.ambientRadial = Color.parseColor(ambientRadial);
            this.cardCornerGlow = Color.parseColor(cardCornerGlow);
            this.bgVoid = Color.parseColor(bgVoid);
            this.bgCard = Color.parseColor(bgCard);
            this.bgElevated = Color.parseColor(bgElevated);
        }
    }

    public static final ThemePreset[] PRESETS = new ThemePreset[]{
            new ThemePreset("ember", "Solar Ember", "DEFAULT", "#EA580C", "#F97316", "#33EA580C", "#55EA580C", "#2EEB5E10", "#42EA580C", "#0B0B0D", "#16161A", "#1E1D22"),
            new ThemePreset("cyan", "Cyber Cyan", "ICE GLASS", "#0891B2", "#06B6D4", "#3306B6D4", "#5506B6D4", "#2E06B6D4", "#4206B6D4", "#080C10", "#111820", "#17222D"),
            new ThemePreset("emerald", "Emerald Matrix", "BIO NODE", "#059669", "#10B981", "#3310B981", "#5510B981", "#2E10B981", "#4210B981", "#080E0C", "#111B17", "#172620"),
            new ThemePreset("amethyst", "Royal Amethyst", "NEURAL", "#7E22CE", "#A855F7", "#33A855F7", "#55A855F7", "#2EA855F7", "#42A855F7", "#0C0912", "#171222", "#201930"),
            new ThemePreset("crimson", "Crimson Core", "TACTICAL", "#E11D48", "#F43F5E", "#33F43F5E", "#55F43F5E", "#2EF43F5E", "#42F43F5E", "#0E090B", "#1B1216", "#26181E"),
            new ThemePreset("cobalt", "Cobalt Titanium", "QUANTUM", "#1D4ED8", "#3B82F6", "#333B82F6", "#553B82F6", "#2E3B82F6", "#423B82F6", "#080B12", "#111724", "#182032"),
            new ThemePreset("arctic", "Arctic Frost", "GLACIER", "#0284C7", "#38BDF8", "#3338BDF8", "#5538BDF8", "#2E38BDF8", "#4238BDF8", "#080D12", "#111A22", "#182530"),
            new ThemePreset("gold", "Gold Sovereign", "LUXE", "#CA8A04", "#EAB308", "#33EAB308", "#55EAB308", "#2EEAB308", "#42EAB308", "#0D0B08", "#1A1711", "#242017"),
            new ThemePreset("mint", "Neon Mint", "SYNTH", "#0D9488", "#2DD4BF", "#332DD4BF", "#552DD4BF", "#2E2DD4BF", "#422DD4BF", "#080E0D", "#111C1B", "#172725"),
            new ThemePreset("sunset", "Sunset Copper", "AURA", "#DB2777", "#FB7185", "#33FB7185", "#55FB7185", "#2EFB7185", "#42FB7185", "#0E090C", "#1C1218", "#261821"),
            new ThemePreset("indigo", "Indigo Nebula", "COSMOS", "#4F46E5", "#818CF8", "#33818CF8", "#55818CF8", "#2E818CF8", "#42818CF8", "#090912", "#131324", "#1B1B32"),
            new ThemePreset("obsidian", "Obsidian Slate", "STEALTH", "#64748B", "#CBD5E1", "#3394A3B8", "#5594A3B8", "#2994A3B8", "#3A94A3B8", "#090A0C", "#14161A", "#1D2026")
    };

    public static int BG_VOID = PRESETS[0].bgVoid;
    public static int BG_CARD = PRESETS[0].bgCard;
    public static int BG_CARD_ELEVATED = PRESETS[0].bgElevated;
    public static int ACCENT_AMBER = PRESETS[0].primary;
    public static int ACCENT_AMBER_GLOW = PRESETS[0].glow;
    public static int ACCENT_AMBER_SOFT = PRESETS[0].soft;
    public static int EMERALD_LIVE = Color.parseColor("#10B981");
    public static int ROSE_OFFLINE = Color.parseColor("#F43F5E");
    public static int TEXT_PRIMARY = Color.parseColor("#F8FAFC");
    public static int TEXT_SECONDARY = Color.parseColor("#9CA3AF");
    public static int TEXT_MUTED = Color.parseColor("#6B7280");
    public static int BORDER_SUBTLE = Color.parseColor("#26FFFFFF");
    public static int BORDER_WARM = PRESETS[0].borderWarm;

    private static ThemePreset currentPreset = PRESETS[0];

    private GlassTheme() {}

    public static void applySavedTheme(Context ctx) {
        if (ctx == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE);
        String savedId = prefs.getString("active_theme_id", "ember");
        for (ThemePreset p : PRESETS) {
            if (p.id.equals(savedId)) {
                applyPresetInternal(p);
                return;
            }
        }
        applyPresetInternal(PRESETS[0]);
    }

    public static void setTheme(Context ctx, String themeId) {
        for (ThemePreset p : PRESETS) {
            if (p.id.equals(themeId)) {
                applyPresetInternal(p);
                if (ctx != null) {
                    ctx.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
                            .edit()
                            .putString("active_theme_id", p.id)
                            .apply();
                }
                return;
            }
        }
    }

    public static String getCurrentThemeId() {
        return currentPreset.id;
    }

    public static String getCurrentThemeName() {
        return currentPreset.name;
    }

    private static void applyPresetInternal(ThemePreset p) {
        currentPreset = p;
        BG_VOID = p.bgVoid;
        BG_CARD = p.bgCard;
        BG_CARD_ELEVATED = p.bgElevated;
        ACCENT_AMBER = p.primary;
        ACCENT_AMBER_GLOW = p.glow;
        ACCENT_AMBER_SOFT = p.soft;
        BORDER_WARM = p.borderWarm;
    }

    public static int dp(Context ctx, float dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    public static Drawable createRootAmbientBackground() {
        GradientDrawable base = new GradientDrawable();
        base.setColor(BG_VOID);

        GradientDrawable topGlow = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{currentPreset.ambientRadial, Color.parseColor("#0A16161A"), Color.TRANSPARENT}
        );
        topGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        topGlow.setGradientRadius(950f);
        topGlow.setGradientCenter(0.18f, 0.04f);

        return new LayerDrawable(new Drawable[]{base, topGlow});
    }

    public static Drawable createFrostedCard(Context ctx, float radiusDp, boolean warmCornerGlow) {
        int r = dp(ctx, radiusDp);
        int baseStart = (BG_CARD & 0x00FFFFFF) | 0xCC000000;
        int baseEnd = (BG_CARD_ELEVATED & 0x00FFFFFF) | 0xE2000000;
        GradientDrawable fill = new GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                warmCornerGlow
                        ? new int[]{baseStart, baseEnd, currentPreset.cardCornerGlow}
                        : new int[]{baseStart, baseEnd}
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
