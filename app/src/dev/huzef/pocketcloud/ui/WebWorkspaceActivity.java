package dev.huzef.pocketcloud.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import dev.huzef.pocketcloud.server.JellyfinEngineManager;
import dev.huzef.pocketcloud.service.PocketCloudService;

public class WebWorkspaceActivity extends Activity {

    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_HINT = "extra_hint";
    private static final int FILE_CHOOSER_REQ = 9001;
    private static final String PREF_DESKTOP_MODE = "pref_universal_desktop_mode";

    private static final String UA_MOBILE = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final String UA_DESKTOP = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private FrameLayout contentStack;
    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvSubHeader;
    private TextView btnDesktopToggle;
    private View setupWizardOverlay;
    private ValueCallback<Uri[]> filePathCallback;
    private String targetUrl;
    private String fallbackHint;
    private boolean isFileBrowserMode = false;
    private boolean isMediaMode = false;
    private boolean desktopModeEnabled = false;
    private boolean autoLoginAttempted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(GlassTheme.BG_VOID);
        w.setNavigationBarColor(GlassTheme.BG_VOID);

        final String title = getIntent().getStringExtra(EXTRA_TITLE);
        targetUrl = getIntent().getStringExtra(EXTRA_URL);
        fallbackHint = getIntent().getStringExtra(EXTRA_HINT);
        isFileBrowserMode = targetUrl != null && targetUrl.contains(":8080");
        isMediaMode = targetUrl != null && targetUrl.contains(":8096");

        SharedPreferences prefs = getSharedPreferences("pocketcloud_prefs", MODE_PRIVATE);
        desktopModeEnabled = prefs.getBoolean(PREF_DESKTOP_MODE, false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(GlassTheme.BG_VOID);

        // Top Cyber-Glass Toolbar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(GlassTheme.dp(this, 12), GlassTheme.dp(this, 10), GlassTheme.dp(this, 12), GlassTheme.dp(this, 10));
        topBar.setBackgroundColor(Color.parseColor("#121216"));

        TextView btnBack = new TextView(this);
        btnBack.setText("←");
        btnBack.setTextColor(GlassTheme.TEXT_PRIMARY);
        btnBack.setTextSize(18f);
        btnBack.setGravity(Gravity.CENTER);
        btnBack.setBackground(GlassTheme.createCircleBadge(this, GlassTheme.BG_CARD_ELEVATED, GlassTheme.BORDER_SUBTLE));
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(GlassTheme.dp(this, 36), GlassTheme.dp(this, 36)));
        GlassTheme.attachSpringPress(btnBack);
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
        titleLp.leftMargin = GlassTheme.dp(this, 10);
        titleCol.setLayoutParams(titleLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title != null ? title : "Service Workspace");
        tvTitle.setTextColor(GlassTheme.TEXT_PRIMARY);
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        titleCol.addView(tvTitle);

        tvSubHeader = new TextView(this);
        updateSubHeaderText();
        tvSubHeader.setTextColor(GlassTheme.ACCENT_AMBER_GLOW);
        tvSubHeader.setTextSize(10f);
        tvSubHeader.setTypeface(Typeface.MONOSPACE);
        titleCol.addView(tvSubHeader);
        topBar.addView(titleCol);

        // Universal PC Desktop Mode Pill (Works across :8080 FileBrowser, :8096 Media, :8384 Syncthing)
        btnDesktopToggle = new TextView(this);
        btnDesktopToggle.setTextSize(11f);
        btnDesktopToggle.setTypeface(Typeface.MONOSPACE);
        btnDesktopToggle.setPadding(GlassTheme.dp(this, 10), GlassTheme.dp(this, 7), GlassTheme.dp(this, 10), GlassTheme.dp(this, 7));
        GlassTheme.attachSpringPress(btnDesktopToggle);
        btnDesktopToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                desktopModeEnabled = !desktopModeEnabled;
                getSharedPreferences("pocketcloud_prefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_DESKTOP_MODE, desktopModeEnabled)
                        .apply();
                applyDesktopViewportSettings(true);
                Toast.makeText(
                        WebWorkspaceActivity.this,
                        desktopModeEnabled ? "PC Desktop Mode ON (1-Tap Open Ready)" : "Mobile View Restored",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
        topBar.addView(btnDesktopToggle);

        if (isFileBrowserMode || isMediaMode) {
            TextView btnVaultKey = new TextView(this);
            btnVaultKey.setText("KEY");
            btnVaultKey.setTextColor(GlassTheme.TEXT_PRIMARY);
            btnVaultKey.setTextSize(11f);
            btnVaultKey.setTypeface(Typeface.MONOSPACE);
            btnVaultKey.setPadding(GlassTheme.dp(this, 10), GlassTheme.dp(this, 7), GlassTheme.dp(this, 10), GlassTheme.dp(this, 7));
            btnVaultKey.setBackground(GlassTheme.createPillButton(this, GlassTheme.ACCENT_AMBER_SOFT, GlassTheme.BORDER_WARM));
            LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            keyLp.leftMargin = GlassTheme.dp(this, 6);
            btnVaultKey.setLayoutParams(keyLp);
            GlassTheme.attachSpringPress(btnVaultKey);
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
        btnReload.setTextSize(15f);
        btnReload.setGravity(Gravity.CENTER);
        btnReload.setBackground(GlassTheme.createCircleBadge(this, GlassTheme.ACCENT_AMBER, Color.TRANSPARENT));
        LinearLayout.LayoutParams relLp = new LinearLayout.LayoutParams(GlassTheme.dp(this, 34), GlassTheme.dp(this, 34));
        relLp.leftMargin = GlassTheme.dp(this, 6);
        btnReload.setLayoutParams(relLp);
        GlassTheme.attachSpringPress(btnReload);
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
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        // Disable WebView double-tap zoom hijack so clicks & double-clicks go straight to the web app
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);

        applyDesktopViewportSettings(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onReceivedError(final WebView view, int errorCode, String description, final String failingUrl) {
                if (isMediaMode && failingUrl != null && failingUrl.contains(":8096")) {
                    String warmingHtml = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                            + "<style>"
                            + "*{box-sizing:border-box;}"
                            + "body{margin:0;background:radial-gradient(circle at 20% 10%,rgba(234,88,12,0.22),transparent 55%),#0B0B0D;color:#F8FAFC;font-family:system-ui,-apple-system,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;padding:20px;}"
                            + ".glass{width:100%;max-width:410px;background:rgba(22,22,26,0.78);backdrop-filter:blur(22px);-webkit-backdrop-filter:blur(22px);border:1px solid rgba(234,88,12,0.42);border-radius:26px;padding:26px 22px;box-shadow:0 24px 60px rgba(0,0,0,0.65);animation:fadeUp .35s cubic-bezier(.16,1,.3,1);}"
                            + "@keyframes fadeUp{from{opacity:0;transform:translateY(14px) scale(.98);}to{opacity:1;transform:translateY(0) scale(1);}}"
                            + ".top{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;}"
                            + ".badge{font-family:monospace;font-size:11px;font-weight:700;color:#F97316;background:rgba(234,88,12,0.16);border:1px solid rgba(234,88,12,0.4);padding:4px 10px;border-radius:99px;}"
                            + ".timer{font-family:monospace;font-size:13px;font-weight:800;color:#10B981;background:rgba(16,185,129,0.12);border:1px solid rgba(16,185,129,0.35);padding:4px 11px;border-radius:99px;}"
                            + "h2{margin:0 0 6px 0;font-size:20px;font-weight:800;letter-spacing:-.2px;}"
                            + ".sub{margin:0 0 18px 0;color:#9CA3AF;font-size:12.5px;line-height:1.45;}"
                            + ".bar-bg{width:100%;height:7px;background:rgba(255,255,255,0.08);border-radius:99px;overflow:hidden;margin-bottom:18px;}"
                            + ".bar-fill{height:100%;width:12%;background:linear-gradient(90deg,#EA580C,#F97316,#10B981);border-radius:99px;transition:width .45s ease;}"
                            + ".steps{display:flex;flex-direction:column;gap:9px;margin-bottom:14px;}"
                            + ".st{display:flex;align-items:center;gap:10px;padding:10px 12px;border-radius:14px;background:rgba(255,255,255,0.03);border:1px solid rgba(255,255,255,0.07);font-size:12px;color:#9CA3AF;transition:all .25s ease;}"
                            + ".st.on{background:rgba(234,88,12,0.12);border-color:rgba(234,88,12,0.45);color:#F8FAFC;font-weight:600;}"
                            + ".st.done{background:rgba(16,185,129,0.10);border-color:rgba(16,185,129,0.35);color:#10B981;}"
                            + ".dot{width:8px;height:8px;border-radius:50%;background:#6B7280;flex-shrink:0;}"
                            + ".st.on .dot{background:#F97316;box-shadow:0 0 10px #F97316;}"
                            + ".st.done .dot{background:#10B981;}"
                            + ".foot{font-family:monospace;font-size:11px;color:#6B7280;text-align:center;}"
                            + "</style></head><body>"
                            + "<div class='glass'>"
                            + "<div class='top'><span class='badge'>JELLYFIN // COLD BOOT</span><span class='timer' id='tmr'>00:00 / ~00:25</span></div>"
                            + "<h2>Warming Up Cinema Server</h2>"
                            + "<p class='sub'>First-time .NET Kestrel &amp; SQLite database initialization runs once and stays instant in background.</p>"
                            + "<div class='bar-bg'><div class='bar-fill' id='bf'></div></div>"
                            + "<div class='steps'>"
                            + "<div class='st done' id='s1'><span class='dot'></span><span>1. ARM64 Glibc &amp; PRoot Runtime Ready</span></div>"
                            + "<div class='st on' id='s2'><span class='dot'></span><span>2. Initializing .NET Core &amp; SQLite Schema</span></div>"
                            + "<div class='st' id='s3'><span class='dot'></span><span>3. Binding HTTP Cinema Port :8096</span></div>"
                            + "</div>"
                            + "<div class='foot' id='ft'>Probing localhost:8096...</div>"
                            + "</div>"
                            + "<script>"
                            + "var k='jf_boot_t0',t0=parseInt(sessionStorage.getItem(k)||'0');"
                            + "if(!t0||Date.now()-t0>90000){t0=Date.now();sessionStorage.setItem(k,String(t0));}"
                            + "function pad(n){return n<10?'0'+n:String(n);}"
                            + "function tick(){"
                            + "var sec=Math.floor((Date.now()-t0)/1000);"
                            + "var pct=Math.min(95,12+sec*3);"
                            + "document.getElementById('tmr').textContent=pad(Math.floor(sec/60))+':'+pad(sec%60)+' / ~00:25';"
                            + "document.getElementById('bf').style.width=pct+'%';"
                            + "if(sec>=12){document.getElementById('s2').className='st done';document.getElementById('s3').className='st on';document.getElementById('ft').textContent='Finalizing WebUI & Kestrel listener...';}"
                            + "}"
                            + "tick();setInterval(tick,1000);"
                            + "setInterval(function(){"
                            + "fetch(" + JSONObject.quote(failingUrl) + ",{mode:'no-cors'}).then(function(){sessionStorage.removeItem(k);location.replace(" + JSONObject.quote(failingUrl) + ");}).catch(function(){});"
                            + "},2000);"
                            + "</script></body></html>";
                    view.loadDataWithBaseURL(null, warmingHtml, "text/html", "utf-8", null);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (desktopModeEnabled) {
                    injectDesktopViewportAndTouchBridge(view);
                }
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

        if (isFileBrowserMode && !VaultConfigManager.isVaultConfigured(this)) {
            setupWizardOverlay = buildBeginnerVaultSetupCard();
            contentStack.addView(setupWizardOverlay);
            GlassTheme.animateGlassEntry(setupWizardOverlay);
        } else if (isMediaMode) {
            checkAndLaunchRealJellyfin();
        } else if (targetUrl != null) {
            webView.loadUrl(targetUrl);
        }

        root.addView(contentStack);
        setContentView(root);
    }

    private void applyDesktopViewportSettings(boolean reloadAfter) {
        if (webView == null) return;
        WebSettings ws = webView.getSettings();
        if (desktopModeEnabled) {
            ws.setUserAgentString(UA_DESKTOP);
            ws.setUseWideViewPort(true);
            ws.setLoadWithOverviewMode(true);
            webView.setInitialScale(85);
        } else {
            ws.setUserAgentString(UA_MOBILE);
            ws.setUseWideViewPort(true);
            ws.setLoadWithOverviewMode(true);
            webView.setInitialScale(0);
        }
        updateDesktopToggleUi();
        if (reloadAfter) {
            autoLoginAttempted = false;
            webView.reload();
        }
    }

    private void updateDesktopToggleUi() {
        if (btnDesktopToggle == null) return;
        btnDesktopToggle.setText(desktopModeEnabled ? "PC ON" : "PC");
        btnDesktopToggle.setTextColor(desktopModeEnabled ? GlassTheme.TEXT_PRIMARY : GlassTheme.TEXT_SECONDARY);
        btnDesktopToggle.setBackground(GlassTheme.createPillButton(
                this,
                desktopModeEnabled ? GlassTheme.ACCENT_AMBER : GlassTheme.BG_CARD_ELEVATED,
                desktopModeEnabled ? GlassTheme.BORDER_WARM : GlassTheme.BORDER_SUBTLE
        ));
    }

    private void injectDesktopViewportAndTouchBridge(WebView view) {
        // Forces 1120px desktop layout + bridges single-tap on FileBrowser desktop items to dblclick so folders open on 1 tap
        String js = "(function(){"
                + "var meta=document.querySelector('meta[name=\"viewport\"]');"
                + "if(!meta){meta=document.createElement('meta');meta.name='viewport';document.head.appendChild(meta);}"
                + "meta.content='width=1120, initial-scale=0.85, maximum-scale=1.0, user-scalable=no';"
                + "if(!window.__pcTouchBridge){"
                + "window.__pcTouchBridge=true;"
                + "var sx=0,sy=0;"
                + "document.addEventListener('touchstart',function(e){"
                + "if(e.touches.length===1){sx=e.touches[0].clientX;sy=e.touches[0].clientY;}"
                + "},{passive:true,capture:true});"
                + "document.addEventListener('touchend',function(e){"
                + "if(e.changedTouches.length!==1)return;"
                + "var dx=Math.abs(e.changedTouches[0].clientX-sx);"
                + "var dy=Math.abs(e.changedTouches[0].clientY-sy);"
                + "if(dx>12||dy>12)return;"
                + "var item=e.target.closest('.item');"
                + "if(item && !e.target.closest('button,input,a,[role=\"button\"]')){"
                + "setTimeout(function(){"
                + "item.dispatchEvent(new MouseEvent('dblclick',{bubbles:true,cancelable:true,view:window}));"
                + "},40);"
                + "}"
                + "},{passive:true,capture:true});"
                + "}"
                + "})();";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            view.evaluateJavascript(js, null);
        }
    }


    private void checkAndLaunchRealJellyfin() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final JellyfinEngineManager mgr = PocketCloudService.jellyfinManager != null
                        ? PocketCloudService.jellyfinManager
                        : new JellyfinEngineManager(WebWorkspaceActivity.this);
                final boolean alive = HardwareTelemetry.pingLocalPort(8096, 180) > 0;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (alive) {
                            if (targetUrl != null) webView.loadUrl(targetUrl);
                        } else if (mgr.isInstalled()) {
                            // Already installed: auto-boot in background & show Glassmorphic Live Timer card
                            setupWizardOverlay = buildLiveJellyfinWarmupCard(mgr);
                            contentStack.addView(setupWizardOverlay);
                            GlassTheme.animateGlassEntry(setupWizardOverlay);
                        } else {
                            setupWizardOverlay = buildRealJellyfinInstallerCard();
                            contentStack.addView(setupWizardOverlay);
                            GlassTheme.animateGlassEntry(setupWizardOverlay);
                        }
                    }
                });
            }
        }).start();
    }

    private View buildLiveJellyfinWarmupCard(final JellyfinEngineManager mgr) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(GlassTheme.createRootAmbientBackground());

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER);
        outer.setPadding(GlassTheme.dp(this, 22), GlassTheme.dp(this, 22), GlassTheme.dp(this, 22), GlassTheme.dp(this, 22));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(GlassTheme.createFrostedCard(this, 26f, true));
        card.setPadding(GlassTheme.dp(this, 22), GlassTheme.dp(this, 22), GlassTheme.dp(this, 22), GlassTheme.dp(this, 22));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView pill = new TextView(this);
        pill.setText("JELLYFIN // ENGINE BOOT");
        pill.setTextColor(GlassTheme.ACCENT_AMBER_GLOW);
        pill.setTextSize(11f);
        pill.setTypeface(Typeface.MONOSPACE);
        pill.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        topRow.addView(pill);

        final TextView tvTimer = new TextView(this);
        tvTimer.setText("00:00 / ~00:25");
        tvTimer.setTextColor(GlassTheme.EMERALD_LIVE);
        tvTimer.setTextSize(12f);
        tvTimer.setTypeface(Typeface.MONOSPACE);
        tvTimer.setPadding(GlassTheme.dp(this, 10), GlassTheme.dp(this, 4), GlassTheme.dp(this, 10), GlassTheme.dp(this, 4));
        tvTimer.setBackground(GlassTheme.createPillButton(this, Color.parseColor("#1F10B981"), Color.parseColor("#5510B981")));
        topRow.addView(tvTimer);
        card.addView(topRow);

        TextView title = new TextView(this);
        title.setText("Warming Up Cinema Server");
        title.setTextColor(GlassTheme.TEXT_PRIMARY);
        title.setTextSize(21f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = GlassTheme.dp(this, 12);
        title.setLayoutParams(tLp);
        card.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Starting official .NET Kestrel & SQLite media catalog on :8096. Runs once and stays fast in background.");
        sub.setTextColor(GlassTheme.TEXT_SECONDARY);
        sub.setTextSize(13f);
        sub.setLineSpacing(0f, 1.35f);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = GlassTheme.dp(this, 6);
        sLp.bottomMargin = GlassTheme.dp(this, 16);
        sub.setLayoutParams(sLp);
        card.addView(sub);

        final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(15);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, GlassTheme.dp(this, 6));
        bLp.bottomMargin = GlassTheme.dp(this, 16);
        bar.setLayoutParams(bLp);
        card.addView(bar);

        final TextView tvStage1 = new TextView(this);
        tvStage1.setText("✓  1. ARM64 Glibc & PRoot Loader Ready");
        tvStage1.setTextColor(GlassTheme.EMERALD_LIVE);
        tvStage1.setTextSize(12f);
        tvStage1.setPadding(GlassTheme.dp(this, 12), GlassTheme.dp(this, 10), GlassTheme.dp(this, 12), GlassTheme.dp(this, 10));
        tvStage1.setBackground(GlassTheme.createFrostedCard(this, 14f, false));
        card.addView(tvStage1);

        final TextView tvStage2 = new TextView(this);
        tvStage2.setText("●  2. Initializing .NET Runtime & Database...");
        tvStage2.setTextColor(GlassTheme.TEXT_PRIMARY);
        tvStage2.setTextSize(12f);
        tvStage2.setPadding(GlassTheme.dp(this, 12), GlassTheme.dp(this, 10), GlassTheme.dp(this, 12), GlassTheme.dp(this, 10));
        tvStage2.setBackground(GlassTheme.createFrostedCard(this, 14f, true));
        LinearLayout.LayoutParams st2Lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        st2Lp.topMargin = GlassTheme.dp(this, 8);
        tvStage2.setLayoutParams(st2Lp);
        card.addView(tvStage2);

        final TextView tvLiveLog = new TextView(this);
        tvLiveLog.setText("Spawning Jellyfin Kestrel listener...");
        tvLiveLog.setTextColor(GlassTheme.TEXT_MUTED);
        tvLiveLog.setTextSize(11f);
        tvLiveLog.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        logLp.topMargin = GlassTheme.dp(this, 14);
        tvLiveLog.setLayoutParams(logLp);
        card.addView(tvLiveLog);

        outer.addView(card);
        scroll.addView(outer);

        // Kick off background start + 1-second live timer & port poll
        new Thread(new Runnable() {
            @Override
            public void run() {
                mgr.startIfInstalled();
            }
        }).start();

        final long t0 = System.currentTimeMillis();
        final Handler h = new Handler(Looper.getMainLooper());
        final Runnable ticker = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || setupWizardOverlay == null) return;
                long sec = (System.currentTimeMillis() - t0) / 1000L;
                long mm = sec / 60L;
                long ss = sec % 60L;
                tvTimer.setText(String.format(java.util.Locale.US, "%02d:%02d / ~00:25", mm, ss));
                bar.setProgress((int) Math.min(95, 15 + sec * 3));
                if (sec >= 10) {
                    tvStage1.setText("✓  1. ARM64 Glibc & PRoot Loader Ready");
                    tvStage2.setText("●  2. Binding Official Jellyfin WebUI (:8096)...");
                }
                String last = mgr.getLastProcessLine();
                if (last != null && !last.isEmpty()) {
                    tvLiveLog.setText(last.length() > 90 ? last.substring(0, 90) + "..." : last);
                }

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final boolean up = HardwareTelemetry.pingLocalPort(8096, 120) > 0;
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                if (up && setupWizardOverlay != null) {
                                    contentStack.removeView(setupWizardOverlay);
                                    setupWizardOverlay = null;
                                    if (targetUrl != null) webView.loadUrl(targetUrl);
                                }
                            }
                        });
                    }
                }).start();

                h.postDelayed(this, 1000L);
            }
        };
        h.postDelayed(ticker, 500L);
        return scroll;
    }

    private View buildRealJellyfinInstallerCard() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(GlassTheme.createRootAmbientBackground());

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER);
        outer.setPadding(GlassTheme.dp(this, 22), GlassTheme.dp(this, 22), GlassTheme.dp(this, 22), GlassTheme.dp(this, 22));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(GlassTheme.createFrostedCard(this, 24f, true));
        card.setPadding(GlassTheme.dp(this, 22), GlassTheme.dp(this, 22), GlassTheme.dp(this, 22), GlassTheme.dp(this, 22));

        TextView pill = new TextView(this);
        pill.setText("OFFICIAL JELLYFIN ENGINE  •  ZERO-TERMUX");
        pill.setTextColor(GlassTheme.ACCENT_AMBER_GLOW);
        pill.setTextSize(11f);
        pill.setTypeface(Typeface.MONOSPACE);
        card.addView(pill);

        TextView title = new TextView(this);
        title.setText("Deploy Real Jellyfin Server");
        title.setTextColor(GlassTheme.TEXT_PRIMARY);
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = GlassTheme.dp(this, 8);
        title.setLayoutParams(tLp);
        card.addView(title);

        TextView desc = new TextView(this);
        desc.setText("Runs the genuine upstream Jellyfin Media Server + Web UI + FFmpeg natively on your phone without Termux or Ubuntu PRoot.");
        desc.setTextColor(GlassTheme.TEXT_SECONDARY);
        desc.setTextSize(13f);
        desc.setLineSpacing(0f, 1.35f);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = GlassTheme.dp(this, 8);
        dLp.bottomMargin = GlassTheme.dp(this, 16);
        desc.setLayoutParams(dLp);
        card.addView(desc);

        LinearLayout guideBox = new LinearLayout(this);
        guideBox.setOrientation(LinearLayout.VERTICAL);
        guideBox.setBackground(GlassTheme.createFrostedCard(this, 16f, false));
        guideBox.setPadding(GlassTheme.dp(this, 14), GlassTheme.dp(this, 12), GlassTheme.dp(this, 14), GlassTheme.dp(this, 12));

        TextView guideTitle = new TextView(this);
        guideTitle.setText("BEGINNER SETUP GUIDE (AFTER BOOT):");
        guideTitle.setTextColor(GlassTheme.EMERALD_LIVE);
        guideTitle.setTextSize(11f);
        guideTitle.setTypeface(Typeface.MONOSPACE);
        guideBox.addView(guideTitle);

        TextView guideSteps = new TextView(this);
        guideSteps.setText("1. Choose Language & create your Jellyfin Admin password\n2. Tap 'Add Media Library' -> Content type: Movies\n3. Enter folder path:  /media/Movies  (or /sdcard/Movies)");
        guideSteps.setTextColor(GlassTheme.TEXT_SECONDARY);
        guideSteps.setTextSize(12f);
        guideSteps.setLineSpacing(0f, 1.35f);
        LinearLayout.LayoutParams gsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gsLp.topMargin = GlassTheme.dp(this, 6);
        guideSteps.setLayoutParams(gsLp);
        guideBox.addView(guideSteps);

        TextView btnCopyPath = new TextView(this);
        btnCopyPath.setText("Copy Library Path (/media/Movies)");
        btnCopyPath.setTextColor(GlassTheme.ACCENT_AMBER_GLOW);
        btnCopyPath.setTextSize(11f);
        btnCopyPath.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams cpLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cpLp.topMargin = GlassTheme.dp(this, 8);
        btnCopyPath.setLayoutParams(cpLp);
        btnCopyPath.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Jellyfin Media Path", "/media/Movies"));
                    Toast.makeText(WebWorkspaceActivity.this, "Copied /media/Movies", Toast.LENGTH_SHORT).show();
                }
            }
        });
        guideBox.addView(btnCopyPath);
        card.addView(guideBox);

        final ProgressBar installBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        installBar.setMax(100);
        installBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, GlassTheme.dp(this, 6));
        barLp.topMargin = GlassTheme.dp(this, 16);
        installBar.setLayoutParams(barLp);
        card.addView(installBar);

        final TextView tvStatus = new TextView(this);
        tvStatus.setTextColor(GlassTheme.TEXT_SECONDARY);
        tvStatus.setTextSize(12f);
        tvStatus.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = GlassTheme.dp(this, 8);
        stLp.bottomMargin = GlassTheme.dp(this, 16);
        tvStatus.setLayoutParams(stLp);
        card.addView(tvStatus);

        final JellyfinEngineManager mgr = PocketCloudService.jellyfinManager != null
                ? PocketCloudService.jellyfinManager
                : new JellyfinEngineManager(this);

        final TextView btnAction = new TextView(this);
        btnAction.setText(mgr.isInstalling() ? "Installing Jellyfin Engine..." : "Install & Start Real Jellyfin →");
        btnAction.setTextColor(GlassTheme.TEXT_PRIMARY);
        btnAction.setTextSize(15f);
        btnAction.setTypeface(Typeface.DEFAULT_BOLD);
        btnAction.setGravity(Gravity.CENTER);
        btnAction.setPadding(GlassTheme.dp(this, 20), GlassTheme.dp(this, 15), GlassTheme.dp(this, 20), GlassTheme.dp(this, 15));
        btnAction.setBackground(GlassTheme.createPillButton(this, GlassTheme.ACCENT_AMBER, GlassTheme.BORDER_WARM));
        GlassTheme.attachSpringPress(btnAction);
        card.addView(btnAction);

        btnAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnAction.setEnabled(false);
                installBar.setVisibility(View.VISIBLE);
                btnAction.setText("Installing Jellyfin Engine...");
                mgr.installOfficialJellyfinAsync(new JellyfinEngineManager.InstallProgressListener() {
                    @Override
                    public void onProgress(final int percent, final String statusText) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                installBar.setIndeterminate(false);
                                installBar.setProgress(percent);
                                tvStatus.setText(statusText);
                            }
                        });
                    }

                    @Override
                    public void onComplete(final boolean success, final String message) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                tvStatus.setText(message);
                                if (success) {
                                    if (setupWizardOverlay != null) {
                                        contentStack.removeView(setupWizardOverlay);
                                        setupWizardOverlay = null;
                                    }
                                    if (targetUrl != null) webView.loadUrl(targetUrl);
                                } else {
                                    btnAction.setEnabled(true);
                                    btnAction.setText("Retry Jellyfin Install");
                                }
                            }
                        });
                    }
                });
            }
        });

        outer.addView(card);
        scroll.addView(outer);
        return scroll;
    }

    private void updateSubHeaderText() {
        if ((isFileBrowserMode && VaultConfigManager.isVaultConfigured(this)) || (isMediaMode && VaultConfigManager.isMediaConfigured(this))) {
            String lanIp = HardwareTelemetry.detectLocalIpv4();
            int p = isMediaMode ? 8096 : 8080;
            tvSubHeader.setText("http://" + lanIp + ":" + p + " • " + VaultConfigManager.getUsername(this));
        } else {
            tvSubHeader.setText((targetUrl != null ? targetUrl : "") + (fallbackHint != null && !fallbackHint.isEmpty() ? " • " + fallbackHint : ""));
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
        heading.setText(isMediaMode ? "Setup Jellyfin Cinema" : "Create Your Cloud Password");
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
        GlassTheme.attachSpringPress(btnCreate);
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
                if (isMediaMode) VaultConfigManager.setMediaConfigured(WebWorkspaceActivity.this, true);
                updateSubHeaderText();

                Intent svc = new Intent(WebWorkspaceActivity.this, PocketCloudService.class);
                svc.setAction(PocketCloudService.ACTION_REINIT_FILEBROWSER);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc);
                } else {
                    startService(svc);
                }

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
        final String lanUrl = "http://" + HardwareTelemetry.detectLocalIpv4() + (isMediaMode ? ":8096" : ":8080");

        String msg = "LAN Access URL (PC / Laptop Browser):\n" + lanUrl
                + "\n\nUsername: " + user
                + "\nPassword: " + (pass.isEmpty() ? "(Not set yet)" : pass)
                + "\n\nTip: PC mode supports 1-tap folder opening on touchscreens.";

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
                            GlassTheme.animateGlassEntry(setupWizardOverlay);
                        }
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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
