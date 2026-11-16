package dev.huzef.pocketcloud.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import dev.huzef.pocketcloud.core.GlassTheme;
import dev.huzef.pocketcloud.core.HardwareTelemetry;
import dev.huzef.pocketcloud.core.VaultConfigManager;
import dev.huzef.pocketcloud.service.PocketCloudService;

public class WebWorkspaceActivity extends Activity {

    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_HINT = "extra_hint";
    private static final int FILE_CHOOSER_REQ = 9001;

    private FrameLayout contentStack;
    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvSubHeader;
    private View setupWizardOverlay;
    private ValueCallback<Uri[]> filePathCallback;
    private String targetUrl;
    private boolean isFileBrowserMode = false;
    private boolean autoLoginAttempted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(GlassTheme.BG_VOID);
        w.setNavigationBarColor(GlassTheme.BG_VOID);

        final String title = getIntent().getStringExtra(EXTRA_TITLE);
        targetUrl = getIntent().getStringExtra(EXTRA_URL);
        final String hint = getIntent().getStringExtra(EXTRA_HINT);
        isFileBrowserMode = targetUrl != null && targetUrl.contains(":8080");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(GlassTheme.BG_VOID);

        // Top Cyber-Glass Toolbar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(GlassTheme.dp(this, 14), GlassTheme.dp(this, 12), GlassTheme.dp(this, 14), GlassTheme.dp(this, 12));
        topBar.setBackgroundColor(Color.parseColor("#121216"));

        TextView btnBack = new TextView(this);
        btnBack.setText("←");
        btnBack.setTextColor(GlassTheme.TEXT_PRIMARY);
        btnBack.setTextSize(18f);
        btnBack.setGravity(Gravity.CENTER);
        btnBack.setBackground(GlassTheme.createCircleBadge(this, GlassTheme.BG_CARD_ELEVATED, GlassTheme.BORDER_SUBTLE));
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(GlassTheme.dp(this, 38), GlassTheme.dp(this, 38)));
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        topBar.addView(btnBack);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = GlassTheme.dp(this, 12);
        titleCol.setLayoutParams(titleLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title != null ? title : "Service Workspace");
        tvTitle.setTextColor(GlassTheme.TEXT_PRIMARY);
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        titleCol.addView(tvTitle);

        tvSubHeader = new TextView(this);
        updateSubHeaderText(hint);
        tvSubHeader.setTextColor(GlassTheme.ACCENT_AMBER_GLOW);
        tvSubHeader.setTextSize(11f);
        tvSubHeader.setTypeface(Typeface.MONOSPACE);
        titleCol.addView(tvSubHeader);
        topBar.addView(titleCol);

        if (isFileBrowserMode) {
            TextView btnVaultKey = new TextView(this);
            btnVaultKey.setText("KEY");
            btnVaultKey.setTextColor(GlassTheme.TEXT_PRIMARY);
            btnVaultKey.setTextSize(11f);
            btnVaultKey.setTypeface(Typeface.MONOSPACE);
            btnVaultKey.setPadding(GlassTheme.dp(this, 11), GlassTheme.dp(this, 7), GlassTheme.dp(this, 11), GlassTheme.dp(this, 7));
            btnVaultKey.setBackground(GlassTheme.createPillButton(this, GlassTheme.ACCENT_AMBER_SOFT, GlassTheme.BORDER_WARM));
            btnVaultKey.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSavedVaultCredentialsDialog();
                }
            });
            topBar.addView(btnVaultKey);
        }

        TextView btnReload = new TextView(this);
        btnReload.setText("↻");
        btnReload.setTextColor(GlassTheme.TEXT_PRIMARY);
        btnReload.setTextSize(16f);
        btnReload.setGravity(Gravity.CENTER);
        btnReload.setBackground(GlassTheme.createCircleBadge(this, GlassTheme.ACCENT_AMBER, Color.TRANSPARENT));
        LinearLayout.LayoutParams relLp = new LinearLayout.LayoutParams(GlassTheme.dp(this, 36), GlassTheme.dp(this, 36));
        relLp.leftMargin = GlassTheme.dp(this, 8);
        btnReload.setLayoutParams(relLp);
        btnReload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoLoginAttempted = false;
                if (webView != null) webView.reload();
            }
        });
        topBar.addView(btnReload);

        root.addView(topBar);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, GlassTheme.dp(this, 2)));
        progressBar.setMax(100);
        root.addView(progressBar);

        contentStack = new FrameLayout(this);
        contentStack.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        webView = new WebView(this);
        webView.setBackgroundColor(GlassTheme.BG_VOID);
        webView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (isFileBrowserMode && VaultConfigManager.isVaultConfigured(WebWorkspaceActivity.this) && !autoLoginAttempted) {
                    autoLoginAttempted = true;
                    injectAutoLoginScript(view);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                WebWorkspaceActivity.this.filePathCallback = filePathCallback;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQ);
                } catch (Exception e) {
                    WebWorkspaceActivity.this.filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        contentStack.addView(webView);

        // Beginner Setup Wizard if FileBrowser password has not been created by user yet
        if (isFileBrowserMode && !VaultConfigManager.isVaultConfigured(this)) {
            setupWizardOverlay = buildBeginnerVaultSetupCard();
            contentStack.addView(setupWizardOverlay);
        } else if (targetUrl != null) {
            webView.loadUrl(targetUrl);
        }

        root.addView(contentStack);
        setContentView(root);
    }

    private void updateSubHeaderText(String fallbackHint) {
        if (isFileBrowserMode && VaultConfigManager.isVaultConfigured(this)) {
            String lanIp = HardwareTelemetry.detectLocalIpv4();
            tvSubHeader.setText("http://" + lanIp + ":8080  •  User: " + VaultConfigManager.getUsername(this));
        } else {
            tvSubHeader.setText((targetUrl != null ? targetUrl : "") + (fallbackHint != null && !fallbackHint.isEmpty() ? "  •  " + fallbackHint : ""));
        }
    }

    private View buildBeginnerVaultSetupCard() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(GlassTheme.createRootAmbientBackground());

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER);
        outer.setPadding(GlassTheme.dp(this, 24), GlassTheme.dp(this, 24), GlassTheme.dp(this, 24), GlassTheme.dp(this, 24));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(GlassTheme.createFrostedCard(this, 24f, true));
        card.setPadding(GlassTheme.dp(this, 22), GlassTheme.dp(this, 22), GlassTheme.dp(this, 22), GlassTheme.dp(this, 22));

        TextView stepPill = new TextView(this);
        stepPill.setText("STEP 1 OF 1  •  FIRST-TIME VAULT SETUP");
        stepPill.setTextColor(GlassTheme.ACCENT_AMBER_GLOW);
        stepPill.setTextSize(11f);
        stepPill.setTypeface(Typeface.MONOSPACE);
        card.addView(stepPill);

        TextView heading = new TextView(this);
        heading.setText("Create Your Cloud Password");
        heading.setTextColor(GlassTheme.TEXT_PRIMARY);
        heading.setTextSize(22f);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hLp.topMargin = GlassTheme.dp(this, 8);
        heading.setLayoutParams(hLp);
        card.addView(heading);

        TextView guide = new TextView(this);
        guide.setText("Your phone is now a private Google Drive server. Choose an admin username & password below — PocketCloud will configure the database and log you in automatically.");
        guide.setTextColor(GlassTheme.TEXT_SECONDARY);
        guide.setTextSize(13f);
        guide.setLineSpacing(0f, 1.35f);
        LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gLp.topMargin = GlassTheme.dp(this, 8);
        gLp.bottomMargin = GlassTheme.dp(this, 18);
        guide.setLayoutParams(gLp);
        card.addView(guide);

        // Username field
        TextView lblUser = new TextView(this);
        lblUser.setText("ADMIN USERNAME");
        lblUser.setTextColor(GlassTheme.TEXT_MUTED);
        lblUser.setTextSize(11f);
        lblUser.setTypeface(Typeface.MONOSPACE);
        card.addView(lblUser);

        final EditText etUser = new EditText(this);
        etUser.setText("admin");
        etUser.setTextColor(GlassTheme.TEXT_PRIMARY);
        etUser.setHintTextColor(GlassTheme.TEXT_MUTED);
        etUser.setTextSize(15f);
        etUser.setSingleLine(true);
        etUser.setPadding(GlassTheme.dp(this, 14), GlassTheme.dp(this, 12), GlassTheme.dp(this, 14), GlassTheme.dp(this, 12));
        etUser.setBackground(GlassTheme.createFrostedCard(this, 14f, false));
        LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        uLp.topMargin = GlassTheme.dp(this, 6);
        uLp.bottomMargin = GlassTheme.dp(this, 14);
        etUser.setLayoutParams(uLp);
        card.addView(etUser);

        // Password field
        TextView lblPass = new TextView(this);
        lblPass.setText("NEW VAULT PASSWORD (MIN 6 CHARS)");
        lblPass.setTextColor(GlassTheme.TEXT_MUTED);
        lblPass.setTextSize(11f);
        lblPass.setTypeface(Typeface.MONOSPACE);
        card.addView(lblPass);

        final EditText etPass = new EditText(this);
        etPass.setHint("e.g. HuzefCloud@2026");
        etPass.setTextColor(GlassTheme.TEXT_PRIMARY);
        etPass.setHintTextColor(GlassTheme.TEXT_MUTED);
        etPass.setTextSize(15f);
        etPass.setSingleLine(true);
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        etPass.setPadding(GlassTheme.dp(this, 14), GlassTheme.dp(this, 12), GlassTheme.dp(this, 14), GlassTheme.dp(this, 12));
        etPass.setBackground(GlassTheme.createFrostedCard(this, 14f, false));
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pLp.topMargin = GlassTheme.dp(this, 6);
        pLp.bottomMargin = GlassTheme.dp(this, 20);
        etPass.setLayoutParams(pLp);
        card.addView(etPass);

        final TextView btnCreate = new TextView(this);
        btnCreate.setText("Create Vault & Auto-Login →");
        btnCreate.setTextColor(GlassTheme.TEXT_PRIMARY);
        btnCreate.setTextSize(15f);
        btnCreate.setTypeface(Typeface.DEFAULT_BOLD);
        btnCreate.setGravity(Gravity.CENTER);
        btnCreate.setPadding(GlassTheme.dp(this, 20), GlassTheme.dp(this, 15), GlassTheme.dp(this, 20), GlassTheme.dp(this, 15));
        btnCreate.setBackground(GlassTheme.createPillButton(this, GlassTheme.ACCENT_AMBER, GlassTheme.BORDER_WARM));
        card.addView(btnCreate);

        btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String u = etUser.getText().toString().trim();
                String p = etPass.getText().toString().trim();
                if (u.isEmpty()) {
                    Toast.makeText(WebWorkspaceActivity.this, "Please enter a username", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (p.length() < 6) {
                    Toast.makeText(WebWorkspaceActivity.this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                    return;
                }

                btnCreate.setEnabled(false);
                btnCreate.setText("Initializing Vault Database...");

                VaultConfigManager.saveCredentials(WebWorkspaceActivity.this, u, p);
                updateSubHeaderText("");

                Intent svc = new Intent(WebWorkspaceActivity.this, PocketCloudService.class);
                svc.setAction(PocketCloudService.ACTION_REINIT_FILEBROWSER);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc);
                } else {
                    startService(svc);
                }

                // Wait briefly for daemon restart with new password, then auto-login
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (setupWizardOverlay != null) {
                            contentStack.removeView(setupWizardOverlay);
                            setupWizardOverlay = null;
                        }
                        autoLoginAttempted = false;
                        if (targetUrl != null) {
                            webView.loadUrl(targetUrl);
                        }
                    }
                }, 1600L);
            }
        });

        outer.addView(card);
        scroll.addView(outer);
        return scroll;
    }

    private void injectAutoLoginScript(final WebView view) {
        final String user = VaultConfigManager.getUsername(this);
        final String pass = VaultConfigManager.getPassword(this);
        if (pass == null || pass.isEmpty()) return;

        final String js = "(function(){"
                + "setTimeout(function(){"
                + "var u=document.querySelector('input[type=\"text\"]');"
                + "var p=document.querySelector('input[type=\"password\"]');"
                + "var btn=document.querySelector('button[type=\"submit\"],input[type=\"submit\"]');"
                + "if(u && p){"
                + "var setVal=function(el,val){"
                + "var desc=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value');"
                + "desc.set.call(el,val);"
                + "el.dispatchEvent(new Event('input',{bubbles:true}));"
                + "el.dispatchEvent(new Event('change',{bubbles:true}));"
                + "};"
                + "setVal(u," + JSONObject.quote(user) + ");"
                + "setVal(p," + JSONObject.quote(pass) + ");"
                + "if(btn){setTimeout(function(){btn.click();},250);}"
                + "}"
                + "},450);"
                + "})();";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            view.evaluateJavascript(js, null);
        }
    }

    private void showSavedVaultCredentialsDialog() {
        final String user = VaultConfigManager.getUsername(this);
        final String pass = VaultConfigManager.getPassword(this);
        final String lanUrl = "http://" + HardwareTelemetry.detectLocalIpv4() + ":8080";

        String msg = "LAN Access URL (PC / Laptop Browser):\n" + lanUrl
                + "\n\nUsername: " + user
                + "\nPassword: " + (pass.isEmpty() ? "(Not set yet)" : pass)
                + "\n\nTip: Open the LAN URL on any device on your Wi-Fi to access your phone's storage.";

        new AlertDialog.Builder(this)
                .setTitle("FileBrowser Vault Key")
                .setMessage(msg)
                .setPositiveButton("Copy Login Info", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            String clip = "URL: " + lanUrl + " | User: " + user + " | Pass: " + pass;
                            cm.setPrimaryClip(ClipData.newPlainText("PocketCloud Vault Key", clip));
                            Toast.makeText(WebWorkspaceActivity.this, "Vault URL & Credentials copied!", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNeutralButton("Reset Password", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (setupWizardOverlay == null) {
                            setupWizardOverlay = buildBeginnerVaultSetupCard();
                            contentStack.addView(setupWizardOverlay);
                        }
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQ && filePathCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
