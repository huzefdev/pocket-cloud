package dev.huzef.pocketcloud.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import dev.huzef.pocketcloud.core.GlassTheme;

public class ThemeStudioSheet {

    public static void show(final Activity activity) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(GlassTheme.dp(activity, 18), GlassTheme.dp(activity, 18), GlassTheme.dp(activity, 18), GlassTheme.dp(activity, 18));
        root.setBackground(GlassTheme.createFrostedCard(activity, 24f, true));

        // Header
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("GLASSMORPHISM THEME STUDIO");
        tvTitle.setTextColor(GlassTheme.TEXT_PRIMARY);
        tvTitle.setTextSize(14.5f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        titleCol.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText("12 Curated Frosted Presets • Active: " + GlassTheme.getCurrentThemeName());
        tvSub.setTextColor(GlassTheme.ACCENT_AMBER_GLOW);
        tvSub.setTextSize(11f);
        titleCol.addView(tvSub);
        header.addView(titleCol);

        TextView btnClose = new TextView(activity);
        btnClose.setText("×");
        btnClose.setTextColor(GlassTheme.TEXT_PRIMARY);
        btnClose.setTextSize(18f);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setBackground(GlassTheme.createCircleBadge(activity, GlassTheme.BG_CARD_ELEVATED, GlassTheme.BORDER_SUBTLE));
        btnClose.setLayoutParams(new LinearLayout.LayoutParams(GlassTheme.dp(activity, 34), GlassTheme.dp(activity, 34)));
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        header.addView(btnClose);
        root.addView(header);

        // 2-column grid of 12 themes
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gLp.topMargin = GlassTheme.dp(activity, 14);
        grid.setLayoutParams(gLp);

        String activeId = GlassTheme.getCurrentThemeId();
        GlassTheme.ThemePreset[] presets = GlassTheme.PRESETS;

        for (int i = 0; i < presets.length; i += 2) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rLp.bottomMargin = GlassTheme.dp(activity, 10);
            row.setLayoutParams(rLp);

            for (int col = 0; col < 2; col++) {
                int idx = i + col;
                if (idx >= presets.length) break;
                final GlassTheme.ThemePreset preset = presets[idx];
                boolean isSelected = preset.id.equals(activeId);

                LinearLayout card = new LinearLayout(activity);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(GlassTheme.dp(activity, 12), GlassTheme.dp(activity, 11), GlassTheme.dp(activity, 12), GlassTheme.dp(activity, 11));
                card.setBackground(GlassTheme.createPillButton(
                        activity,
                        preset.bgCard,
                        isSelected ? preset.glow : Color.parseColor("#26FFFFFF")
                ));
                // Override corner radius cleanly via frosted card look
                android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.BL_TR,
                        new int[]{preset.bgVoid, preset.bgElevated, preset.cardCornerGlow}
                );
                cardBg.setCornerRadius(GlassTheme.dp(activity, 16f));
                cardBg.setStroke(GlassTheme.dp(activity, isSelected ? 1.8f : 1f), isSelected ? preset.glow : Color.parseColor("#2AFFFFFF"));
                card.setBackground(cardBg);

                LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (col == 0) cLp.rightMargin = GlassTheme.dp(activity, 5);
                else cLp.leftMargin = GlassTheme.dp(activity, 5);
                card.setLayoutParams(cLp);

                LinearLayout topSwatchRow = new LinearLayout(activity);
                topSwatchRow.setOrientation(LinearLayout.HORIZONTAL);
                topSwatchRow.setGravity(Gravity.CENTER_VERTICAL);

                View orb = new View(activity);
                orb.setBackground(GlassTheme.createCircleBadge(activity, preset.glow, Color.parseColor("#66FFFFFF")));
                orb.setLayoutParams(new LinearLayout.LayoutParams(GlassTheme.dp(activity, 18), GlassTheme.dp(activity, 18)));
                topSwatchRow.addView(orb);

                TextView tvTag = new TextView(activity);
                tvTag.setText(isSelected ? "ACTIVE" : preset.tag);
                tvTag.setTextColor(isSelected ? preset.glow : GlassTheme.TEXT_MUTED);
                tvTag.setTextSize(9.5f);
                tvTag.setTypeface(Typeface.MONOSPACE);
                tvTag.setGravity(Gravity.END);
                tvTag.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                topSwatchRow.addView(tvTag);
                card.addView(topSwatchRow);

                TextView tvName = new TextView(activity);
                tvName.setText(preset.name);
                tvName.setTextColor(GlassTheme.TEXT_PRIMARY);
                tvName.setTextSize(12.5f);
                tvName.setTypeface(Typeface.DEFAULT_BOLD);
                LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                nLp.topMargin = GlassTheme.dp(activity, 8);
                tvName.setLayoutParams(nLp);
                card.addView(tvName);

                GlassTheme.attachSpringPress(card);
                card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        GlassTheme.setTheme(activity, preset.id);
                        dialog.dismiss();
                        Toast.makeText(activity, "Theme applied: " + preset.name, Toast.LENGTH_SHORT).show();
                        activity.recreate();
                        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    }
                });

                row.addView(card);
            }
            grid.addView(row);
        }

        root.addView(grid);
        scroll.addView(root);
        dialog.setContentView(scroll);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        GlassTheme.animateGlassEntry(root);
        dialog.show();
    }
}
