package dev.huzef.pocketcloud.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import dev.huzef.pocketcloud.core.GlassTheme;
import dev.huzef.pocketcloud.core.OfflineQrEngine;
import dev.huzef.pocketcloud.security.EncryptedVaultBackup;
import dev.huzef.pocketcloud.security.GuestTokenManager;

public final class NextGenNodeSheet {

    public static final String PREFS_GUARD = "pocketcloud_thermal_guard_v1";

    private NextGenNodeSheet() {}

    public static boolean isThermalGuardEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS_GUARD, Context.MODE_PRIVATE).getBoolean("enabled", true);
    }

    public static void show(final Activity activity) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        ScrollView scroll = new ScrollView(activity);
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(GlassTheme.dp(activity, 18), GlassTheme.dp(activity, 18), GlassTheme.dp(activity, 18), GlassTheme.dp(activity, 18));
        root.setBackground(GlassTheme.createFrostedCard(activity, 24f, true));

        final Runnable[] renderRef = new Runnable[1];
        renderRef[0] = new Runnable() {
            @Override
            public void run() {
                root.removeAllViews();

                // Header
                LinearLayout header = new LinearLayout(activity);
                header.setOrientation(LinearLayout.HORIZONTAL);
                header.setGravity(Gravity.CENTER_VERTICAL);

                LinearLayout titleCol = new LinearLayout(activity);
                titleCol.setOrientation(LinearLayout.VERTICAL);
                titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvTitle = new TextView(activity);
                tvTitle.setText("OFFLINE NODE & SECURITY SUITE");
                tvTitle.setTextColor(GlassTheme.TEXT_PRIMARY);
                tvTitle.setTextSize(14f);
                tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
                titleCol.addView(tvTitle);

                TextView tvSub = new TextView(activity);
                tvSub.setText("100% Zero-Internet LAN • AES-256 Backup • Guest Isolation");
                tvSub.setTextColor(GlassTheme.ACCENT_AMBER_GLOW);
                tvSub.setTextSize(10.5f);
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

                // 1. Offline LAN Matrix & Endpoint Share
                String lanIp = OfflineQrEngine.getOfflineLanIp();
                final String lanUrl = "http://" + lanIp + ":8080";

                LinearLayout lanCard = new LinearLayout(activity);
                lanCard.setOrientation(LinearLayout.HORIZONTAL);
                lanCard.setGravity(Gravity.CENTER_VERTICAL);
                lanCard.setPadding(GlassTheme.dp(activity, 14), GlassTheme.dp(activity, 14), GlassTheme.dp(activity, 14), GlassTheme.dp(activity, 14));
                lanCard.setBackground(GlassTheme.createFrostedCard(activity, 18f, false));
                LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lLp.topMargin = GlassTheme.dp(activity, 14);
                lanCard.setLayoutParams(lLp);

                ImageView ivMatrix = new ImageView(activity);
                ivMatrix.setImageBitmap(OfflineQrEngine.renderEndpointMatrix(lanUrl, GlassTheme.dp(activity, 76), GlassTheme.ACCENT_AMBER_GLOW));
                LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(GlassTheme.dp(activity, 76), GlassTheme.dp(activity, 76));
                mLp.rightMargin = GlassTheme.dp(activity, 12);
                ivMatrix.setLayoutParams(mLp);
                lanCard.addView(ivMatrix);

                LinearLayout lanInfo = new LinearLayout(activity);
                lanInfo.setOrientation(LinearLayout.VERTICAL);
                lanInfo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvLanHead = new TextView(activity);
                tvLanHead.setText("OFFLINE HOTSPOT / LAN NODE");
                tvLanHead.setTextColor(GlassTheme.TEXT_PRIMARY);
                tvLanHead.setTextSize(12f);
                tvLanHead.setTypeface(Typeface.DEFAULT_BOLD);
                lanInfo.addView(tvLanHead);

                TextView tvLanUrl = new TextView(activity);
                tvLanUrl.setText(lanUrl + "\nJellyfin :8096  |  Sync :8384");
                tvLanUrl.setTextColor(GlassTheme.TEXT_SECONDARY);
                tvLanUrl.setTextSize(11f);
                tvLanUrl.setTypeface(Typeface.MONOSPACE);
                lanInfo.addView(tvLanUrl);

                TextView btnCopyLan = new TextView(activity);
                btnCopyLan.setText("COPY OFFLINE URL");
                btnCopyLan.setTextColor(Color.WHITE);
                btnCopyLan.setTextSize(10f);
                btnCopyLan.setTypeface(Typeface.DEFAULT_BOLD);
                btnCopyLan.setPadding(GlassTheme.dp(activity, 10), GlassTheme.dp(activity, 5), GlassTheme.dp(activity, 10), GlassTheme.dp(activity, 5));
                btnCopyLan.setBackground(GlassTheme.createPillButton(activity, GlassTheme.ACCENT_AMBER_SOFT, GlassTheme.BORDER_WARM));
                LinearLayout.LayoutParams clLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clLp.topMargin = GlassTheme.dp(activity, 6);
                btnCopyLan.setLayoutParams(clLp);
                btnCopyLan.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("LAN URL", lanUrl));
                        Toast.makeText(activity, "Copied offline URL: " + lanUrl, Toast.LENGTH_SHORT).show();
                    }
                });
                lanInfo.addView(btnCopyLan);
                lanCard.addView(lanInfo);
                root.addView(lanCard);

                // 2. Scoped Guest Read-Only Token
                LinearLayout guestCard = new LinearLayout(activity);
                guestCard.setOrientation(LinearLayout.VERTICAL);
                guestCard.setPadding(GlassTheme.dp(activity, 14), GlassTheme.dp(activity, 14), GlassTheme.dp(activity, 14), GlassTheme.dp(activity, 14));
                guestCard.setBackground(GlassTheme.createFrostedCard(activity, 18f, false));
                LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                gLp.topMargin = GlassTheme.dp(activity, 10);
                guestCard.setLayoutParams(gLp);

                TextView tvGuestTitle = new TextView(activity);
                tvGuestTitle.setText("SCOPED GUEST PASS (READ-ONLY :8080)");
                tvGuestTitle.setTextColor(GlassTheme.TEXT_PRIMARY);
                tvGuestTitle.setTextSize(12f);
                tvGuestTitle.setTypeface(Typeface.DEFAULT_BOLD);
                guestCard.addView(tvGuestTitle);

                final GuestTokenManager.GuestSession activeGuest = GuestTokenManager.getActiveSession(activity);
                TextView tvGuestBody = new TextView(activity);
                if (activeGuest != null) {
                    tvGuestBody.setText("User: " + activeGuest.username + "  |  Pass: " + activeGuest.password + " (" + activeGuest.remainingMinutes() + "m left)");
                    tvGuestBody.setTextColor(GlassTheme.EMERALD_LIVE);
                } else {
                    tvGuestBody.setText("Share files over hotspot without exposing admin password.");
                    tvGuestBody.setTextColor(GlassTheme.TEXT_SECONDARY);
                }
                tvGuestBody.setTextSize(11f);
                LinearLayout.LayoutParams gbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                gbLp.topMargin = GlassTheme.dp(activity, 4);
                gbLp.bottomMargin = GlassTheme.dp(activity, 8);
                tvGuestBody.setLayoutParams(gbLp);
                guestCard.addView(tvGuestBody);

                LinearLayout guestBtnRow = new LinearLayout(activity);
                guestBtnRow.setOrientation(LinearLayout.HORIZONTAL);

                TextView btnGenGuest = new TextView(activity);
                btnGenGuest.setText(activeGuest != null ? "COPY GUEST" : "GENERATE 60M GUEST");
                btnGenGuest.setTextColor(Color.WHITE);
                btnGenGuest.setTextSize(10f);
                btnGenGuest.setTypeface(Typeface.DEFAULT_BOLD);
                btnGenGuest.setPadding(GlassTheme.dp(activity, 11), GlassTheme.dp(activity, 6), GlassTheme.dp(activity, 11), GlassTheme.dp(activity, 6));
                btnGenGuest.setBackground(GlassTheme.createPillButton(activity, GlassTheme.ACCENT_AMBER, GlassTheme.BORDER_WARM));
                btnGenGuest.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (activeGuest != null) {
                            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(ClipData.newPlainText("Guest", activeGuest.username + " / " + activeGuest.password));
                            }
                            Toast.makeText(activity, "Guest credentials copied!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final GuestTokenManager.GuestSession s = GuestTokenManager.createReadOnlyGuest(activity, 60);
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(activity, "Guest created: " + s.username, Toast.LENGTH_SHORT).show();
                                            renderRef[0].run();
                                        }
                                    });
                                } catch (final Exception e) {
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            }
                        }).start();
                    }
                });
                guestBtnRow.addView(btnGenGuest);

                if (activeGuest != null) {
                    TextView btnRevoke = new TextView(activity);
                    btnRevoke.setText("REVOKE");
                    btnRevoke.setTextColor(Color.WHITE);
                    btnRevoke.setTextSize(10f);
                    btnRevoke.setTypeface(Typeface.DEFAULT_BOLD);
                    btnRevoke.setPadding(GlassTheme.dp(activity, 11), GlassTheme.dp(activity, 6), GlassTheme.dp(activity, 11), GlassTheme.dp(activity, 6));
                    btnRevoke.setBackground(GlassTheme.createPillButton(activity, Color.parseColor("#DC2626"), Color.parseColor("#F87171")));
                    LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rvLp.leftMargin = GlassTheme.dp(activity, 8);
                    btnRevoke.setLayoutParams(rvLp);
                    btnRevoke.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    GuestTokenManager.revokeGuest(activity);
                                    activity.runOnUiThread(renderRef[0]);
                                }
                            }).start();
                        }
                    });
                    guestBtnRow.addView(btnRevoke);
                }
                guestCard.addView(guestBtnRow);
                root.addView(guestCard);

                // 3. Thermal & Battery Guard Toggle
                LinearLayout guardCard = new LinearLayout(activity);
                guardCard.setOrientation(LinearLayout.HORIZONTAL);
                guardCard.setGravity(Gravity.CENTER_VERTICAL);
                guardCard.setPadding(GlassTheme.dp(activity, 14), GlassTheme.dp(activity, 12), GlassTheme.dp(activity, 14), GlassTheme.dp(activity, 12));
                guardCard.setBackground(GlassTheme.createFrostedCard(activity, 18f, false));
                LinearLayout.LayoutParams gdLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                gdLp.topMargin = GlassTheme.dp(activity, 10);
                guardCard.setLayoutParams(gdLp);

                LinearLayout gdCol = new LinearLayout(activity);
                gdCol.setOrientation(LinearLayout.VERTICAL);
                gdCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvGdTitle = new TextView(activity);
                tvGdTitle.setText("THERMAL & BATTERY GUARD");
                tvGdTitle.setTextColor(GlassTheme.TEXT_PRIMARY);
                tvGdTitle.setTextSize(12f);
                tvGdTitle.setTypeface(Typeface.DEFAULT_BOLD);
                gdCol.addView(tvGdTitle);

                TextView tvGdSub = new TextView(activity);
                tvGdSub.setText("Auto-pauses daemons if >44°C or battery <12%");
                tvGdSub.setTextColor(GlassTheme.TEXT_SECONDARY);
                tvGdSub.setTextSize(10.5f);
                gdCol.addView(tvGdSub);
                guardCard.addView(gdCol);

                final boolean enabled = isThermalGuardEnabled(activity);
                TextView btnGuard = new TextView(activity);
                btnGuard.setText(enabled ? "ARMED" : "BYPASSED");
                btnGuard.setTextColor(Color.WHITE);
                btnGuard.setTextSize(10f);
                btnGuard.setTypeface(Typeface.DEFAULT_BOLD);
                btnGuard.setPadding(GlassTheme.dp(activity, 11), GlassTheme.dp(activity, 6), GlassTheme.dp(activity, 11), GlassTheme.dp(activity, 6));
                btnGuard.setBackground(GlassTheme.createPillButton(
                        activity,
                        enabled ? GlassTheme.EMERALD_LIVE : Color.parseColor("#374151"),
                        Color.TRANSPARENT
                ));
                btnGuard.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SharedPreferences p = activity.getSharedPreferences(PREFS_GUARD, Context.MODE_PRIVATE);
                        p.edit().putBoolean("enabled", !enabled).apply();
                        renderRef[0].run();
                    }
                });
                guardCard.addView(btnGuard);
                root.addView(guardCard);

                // 4. Encrypted .pcvault Backup & Restore
                LinearLayout bkpCard = new LinearLayout(activity);
                bkpCard.setOrientation(LinearLayout.VERTICAL);
                bkpCard.setPadding(GlassTheme.dp(activity, 14), GlassTheme.dp(activity, 14), GlassTheme.dp(activity, 14), GlassTheme.dp(activity, 14));
                bkpCard.setBackground(GlassTheme.createFrostedCard(activity, 18f, false));
                LinearLayout.LayoutParams bkLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bkLp.topMargin = GlassTheme.dp(activity, 10);
                bkpCard.setLayoutParams(bkLp);

                TextView tvBkpTitle = new TextView(activity);
                tvBkpTitle.setText("ENCRYPTED VAULT BACKUP (.PCVAULT)");
                tvBkpTitle.setTextColor(GlassTheme.TEXT_PRIMARY);
                tvBkpTitle.setTextSize(12f);
                tvBkpTitle.setTypeface(Typeface.DEFAULT_BOLD);
                bkpCard.addView(tvBkpTitle);

                TextView tvBkpSub = new TextView(activity);
                tvBkpSub.setText("PBKDF2-SHA256 + AES-256-GCM offline file in Downloads");
                tvBkpSub.setTextColor(GlassTheme.TEXT_SECONDARY);
                tvBkpSub.setTextSize(10.5f);
                bkpCard.addView(tvBkpSub);

                LinearLayout bkpRow = new LinearLayout(activity);
                bkpRow.setOrientation(LinearLayout.HORIZONTAL);
                bkpRow.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                brLp.topMargin = GlassTheme.dp(activity, 8);
                bkpRow.setLayoutParams(brLp);

                final EditText etPass = new EditText(activity);
                etPass.setHint("Backup passphrase (6+ chars)");
                etPass.setHintTextColor(GlassTheme.TEXT_MUTED);
                etPass.setTextColor(Color.WHITE);
                etPass.setTextSize(11.5f);
                etPass.setSingleLine(true);
                etPass.setPadding(GlassTheme.dp(activity, 10), GlassTheme.dp(activity, 7), GlassTheme.dp(activity, 10), GlassTheme.dp(activity, 7));
                etPass.setBackground(GlassTheme.createPillButton(activity, Color.parseColor("#12141C"), GlassTheme.BORDER_SUBTLE));
                etPass.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                bkpRow.addView(etPass);

                TextView btnExport = new TextView(activity);
                btnExport.setText("EXPORT");
                btnExport.setTextColor(Color.WHITE);
                btnExport.setTextSize(10f);
                btnExport.setTypeface(Typeface.DEFAULT_BOLD);
                btnExport.setPadding(GlassTheme.dp(activity, 10), GlassTheme.dp(activity, 7), GlassTheme.dp(activity, 10), GlassTheme.dp(activity, 7));
                btnExport.setBackground(GlassTheme.createPillButton(activity, GlassTheme.ACCENT_AMBER, GlassTheme.BORDER_WARM));
                LinearLayout.LayoutParams exLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                exLp.leftMargin = GlassTheme.dp(activity, 6);
                btnExport.setLayoutParams(exLp);
                btnExport.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            File f = EncryptedVaultBackup.exportEncryptedBackup(activity, etPass.getText().toString());
                            Toast.makeText(activity, "Exported: " + f.getName(), Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                bkpRow.addView(btnExport);

                TextView btnImport = new TextView(activity);
                btnImport.setText("RESTORE");
                btnImport.setTextColor(Color.WHITE);
                btnImport.setTextSize(10f);
                btnImport.setTypeface(Typeface.DEFAULT_BOLD);
                btnImport.setPadding(GlassTheme.dp(activity, 10), GlassTheme.dp(activity, 7), GlassTheme.dp(activity, 10), GlassTheme.dp(activity, 7));
                btnImport.setBackground(GlassTheme.createPillButton(activity, GlassTheme.EMERALD_LIVE, Color.TRANSPARENT));
                LinearLayout.LayoutParams imLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                imLp.leftMargin = GlassTheme.dp(activity, 6);
                btnImport.setLayoutParams(imLp);
                btnImport.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            int count = EncryptedVaultBackup.importEncryptedBackup(activity, etPass.getText().toString());
                            Toast.makeText(activity, "Restored " + count + " services from backup!", Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                            activity.recreate();
                        } catch (Exception e) {
                            Toast.makeText(activity, "Restore failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                bkpRow.addView(btnImport);
                bkpCard.addView(bkpRow);
                root.addView(bkpCard);
            }
        };

        renderRef[0].run();
        scroll.addView(root);
        dialog.setContentView(scroll);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        GlassTheme.animateGlassEntry(root);
        dialog.show();
    }
}
