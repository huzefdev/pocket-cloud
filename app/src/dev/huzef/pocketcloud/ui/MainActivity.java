package dev.huzef.pocketcloud.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import dev.huzef.pocketcloud.core.GlassTheme;
import dev.huzef.pocketcloud.core.HardwareTelemetry;
import dev.huzef.pocketcloud.model.ServiceNode;
import dev.huzef.pocketcloud.model.TelemetrySnapshot;
import dev.huzef.pocketcloud.service.PocketCloudService;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "pocketcloud_prefs";
    private static final String KEY_ONBOARDED = "onboarded_v1";
    private static final String XMR_ADDRESS = "48edfHu7V9Z84YzzMa6fUueoELZ9ZRXq9VetWzYGzKt52XU5xvqgzYnDK9URnRoJMk1j8nLwEVsaSWJ4fhdUyZijBGUicoD";

    private FrameLayout rootContainer;
    private View splashScreenView;
    private LinearLayout mainShellView;
    private FrameLayout tabContentContainer;

    private int activeTab = 0; // 0 = Home Bento, 1 = Services Timeline, 2 = Engine Logs & Patronage
    private final TextView[] navIcons = new TextView[3];

    // Live UI bindings (Dashboard Bento)
    private TextView tvNodeSubtitle;
    private TextView tvHeroUri;
    private TextView tvHeroStatusPill;
    private TextView tvEngineToggleBtn;
    private TextView tvStatActiveCount;
    private TextView tvStatActiveLabel;
    private TextView tvStatThermalVal;
    private TextView tvStatThermalLabel;
    private TextView tvStatRamVal;
    private TextView tvStatRamLabel;
    private TextView tvStatNetVal;
    private TextView tvStatNetLabel;

    // Live UI bindings (Services Timeline - Screen 3 layout)
    private int selectedPortFocus = 8080;
    private final List<ServiceNode> serviceNodes = new ArrayList<ServiceNode>();
    private LinearLayout timelineCardsHolder;
    private TextView tvConsoleOutput;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private TelemetrySnapshot latestTelemetry = new TelemetrySnapshot();

    private final Runnable telemetryLoop = new Runnable() {
        @Override
        public void run() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int onlineCount = 0;
                    for (ServiceNode node : serviceNodes) {
                        long ping = HardwareTelemetry.pingLocalPort(node.port, 160);
                        node.online = ping > 0;
                        node.latencyMs = ping;
                        if (node.online) onlineCount++;
                    }
                    final TelemetrySnapshot snap = HardwareTelemetry.collect(
                            MainActivity.this,
                            PocketCloudService.isEngineActive,
                            PocketCloudService.isWakeLockActive,
                            onlineCount
                    );
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            latestTelemetry = snap;
                            bindTelemetryToUi();
                        }
                    });
                }
            }).start();
            uiHandler.postDelayed(this, 2000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(GlassTheme.BG_VOID);
        w.setNavigationBarColor(GlassTheme.BG_VOID);

        initServiceNodes();

        rootContainer = new FrameLayout(this);
        rootContainer.setBackground(GlassTheme.createRootAmbientBackground());

        buildMainShell();
        rootContainer.addView(mainShellView);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ONBOARDED, false)) {
            buildSplashOnboarding();
            rootContainer.addView(splashScreenView);
        }

        setContentView(rootContainer);
        requestRuntimePermissionsIfNeeded();
    }

    private void initServiceNodes() {
        serviceNodes.clear();
        serviceNodes.add(new ServiceNode(
                "filebrowser",
                "FileBrowser Cloud",
                "Drive & Storage Vault • admin / admin",
                "PORT :8080",
                8080,
                "Login: admin / admin"
        ));
        serviceNodes.add(new ServiceNode(
                "pocketmedia",
                "PocketMedia Server",
                "Zero-PRoot Cinema & Audio Streamer",
                "PORT :8096",
                8096,
                "Direct HTTP Range Stream"
        ));
        serviceNodes.add(new ServiceNode(
                "syncthing",
                "Syncthing P2P Mesh",
                "Auto Camera & Folder Sync Node",
                "PORT :8384",
                8384,
                "0.0.0.0 Mesh Listener"
        ));
    }

    // =========================================================================
    // SCREEN 1: SPLASH / ONBOARDING (Fibonacci Particle Sphere + Get Started)
    // =========================================================================
    private void buildSplashOnboarding() {
        LinearLayout splash = new LinearLayout(this);
        splash.setOrientation(LinearLayout.VERTICAL);
        splash.setBackground(GlassTheme.createRootAmbientBackground());
        splash.setPadding(GlassTheme.dp(this, 28), GlassTheme.dp(this, 36), GlassTheme.dp(this, 28), GlassTheme.dp(this, 42));

        // Center 3D Particle Sphere
        ParticleSphereView sphere = new ParticleSphereView(this);
        LinearLayout.LayoutParams sphereLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.35f
        );
        sphere.setLayoutParams(sphereLp);
        splash.addView(sphere);

        // Editorial Typography Block (matching left card in reference image)
        TextView tvHeadline = new TextView(this);
        tvHeadline.setText("Host your\nprivate cloud");
        tvHeadline.setTextColor(GlassTheme.TEXT_PRIMARY);
        tvHeadline.setTextSize(34f);
        tvHeadline.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeadline.setLineSpacing(0f, 1.08f);
        splash.addView(tvHeadline);

        TextView tvDesc = new TextView(this);
        tvDesc.setText("Zero-Termux bare-metal NAS, Media Server, and P2P Sync running natively on your Android device.");
        tvDesc.setTextColor(GlassTheme.TEXT_SECONDARY);
        tvDesc.setTextSize(14f);
        tvDesc.setLineSpacing(0f, 1.35f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descLp.topMargin = GlassTheme.dp(this, 14);
        descLp.bottomMargin = GlassTheme.dp(this, 28);
        tvDesc.setLayoutParams(descLp);
        splash.addView(tvDesc);

        // Signature Glowing Circle + Pill CTA ("Get Started")
        LinearLayout ctaRow = new LinearLayout(this);
        ctaRow.setOrientation(LinearLayout.HORIZONTAL);
        ctaRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout ctaPill = new FrameLayout(this);
        ctaPill.setBackground(GlassTheme.createPillButton(this, GlassTheme.ACCENT_AMBER, GlassTheme.BORDER_WARM));
        ctaPill.setPadding(GlassTheme.dp(this, 28), GlassTheme.dp(this, 16), GlassTheme.dp(this, 28), GlassTheme.dp(this, 16));

        TextView tvCta = new TextView(this);
        tvCta.setText("Get Started");
        tvCta.setTextColor(GlassTheme.TEXT_PRIMARY);
        tvCta.setTextSize(16f);
        tvCta.setTypeface(Typeface.DEFAULT_BOLD);
        ctaPill.addView(tvCta);

        ctaPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_ONBOARDED, true)
                        .apply();
                if (splashScreenView != null) {
                    rootContainer.removeView(splashScreenView);
                    splashScreenView = null;
                }
                toggleCloudEngine(true);
            }
        });
        ctaRow.addView(ctaPill);
        splash.addView(ctaRow);

        splashScreenView = splash;
    }

    // =========================================================================
    // MAIN SHELL (Top Bar + Dynamic View Container + Floating Bottom Nav)
    // =========================================================================
    private void buildMainShell() {
        mainShellView = new LinearLayout(this);
        mainShellView.setOrientation(LinearLayout.VERTICAL);

        tabContentContainer = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        );
        tabContentContainer.setLayoutParams(contentLp);
        mainShellView.addView(tabContentContainer);

        // Bottom Navigation Bar (4 minimal architectural icons like reference)
        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(GlassTheme.dp(this, 24), GlassTheme.dp(this, 10), GlassTheme.dp(this, 24), GlassTheme.dp(this, 16));
        bottomNav.setBackgroundColor(Color.parseColor("#E60B0B0D"));

        String[] labels = new String[]{"⌂  Node", "◫  Services", "⌘  Console"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView tabBtn = new TextView(this);
            tabBtn.setText(labels[i]);
            tabBtn.setGravity(Gravity.CENTER);
            tabBtn.setTextSize(13f);
            tabBtn.setTypeface(Typeface.DEFAULT_BOLD);
            tabBtn.setPadding(GlassTheme.dp(this, 16), GlassTheme.dp(this, 10), GlassTheme.dp(this, 16), GlassTheme.dp(this, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = GlassTheme.dp(this, 4);
            lp.rightMargin = GlassTheme.dp(this, 4);
            tabBtn.setLayoutParams(lp);
            tabBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchTab(idx);
                }
            });
            navIcons[i] = tabBtn;
            bottomNav.addView(tabBtn);
        }

        mainShellView.addView(bottomNav);
        switchTab(0);
    }

    private void switchTab(int index) {
        activeTab = index;
        for (int i = 0; i < navIcons.length; i++) {
            boolean selected = (i == index);
            navIcons[i].setTextColor(selected ? GlassTheme.ACCENT_AMBER_GLOW : GlassTheme.TEXT_MUTED);
            navIcons[i].setBackground(selected
                    ? GlassTheme.createPillButton(this, GlassTheme.ACCENT_AMBER_SOFT, GlassTheme.BORDER_WARM)
                    : null);
        }

        tabContentContainer.removeAllViews();
        if (index == 0) {
            tabContentContainer.addView(buildDashboardBentoTab());
        } else if (index == 1) {
            tabContentContainer.addView(buildServicesTimelineTab());
        } else {
            tabContentContainer.addView(buildConsoleAndPatronageTab());
        }
        bindTelemetryToUi();
    }

    // =========================================================================
    // SCREEN 2: GLASS DASHBOARD & STAGGERED 2x2 BENTO GRID (Reference Center Card)
    // =========================================================================
    private View buildDashboardBentoTab() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(GlassTheme.dp(this, 22), GlassTheme.dp(this, 18), GlassTheme.dp(this, 22), GlassTheme.dp(this, 20));

        // Top Bar: Equalizer Bars + Avatar Badge
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvBars = new TextView(this);
        tvBars.setText("▁ ▄ ▇");
        tvBars.setTextColor(GlassTheme.TEXT_PRIMARY);
        tvBars.setTextSize(16f);
        LinearLayout.LayoutParams barsLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvBars.setLayoutParams(barsLp);
        topBar.addView(tvBars);

        TextView avatarBadge = new TextView(this);
        avatarBadge.setText("PC");
        avatarBadge.setTextColor(GlassTheme.BG_VOID);
        avatarBadge.setTextSize(13f);
        avatarBadge.setTypeface(Typeface.DEFAULT_BOLD);
        avatarBadge.setGravity(Gravity.CENTER);
        avatarBadge.setBackground(GlassTheme.createCircleBadge(this, GlassTheme.ACCENT_AMBER_GLOW, GlassTheme.BORDER_SUBTLE));
        avatarBadge.setLayoutParams(new LinearLayout.LayoutParams(GlassTheme.dp(this, 38), GlassTheme.dp(this, 38)));
        avatarBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStoragePermissionGuide();
            }
        });
        topBar.addView(avatarBadge);
        col.addView(topBar);

        // Greeting / Node Title
        TextView tvNodeTitle = new TextView(this);
        tvNodeTitle.setText("PocketCloud Node");
        tvNodeTitle.setTextColor(GlassTheme.TEXT_PRIMARY);
        tvNodeTitle.setTextSize(26f);
        tvNodeTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = GlassTheme.dp(this, 18);
        tvNodeTitle.setLayoutParams(titleLp);
        col.addView(tvNodeTitle);

        tvNodeSubtitle = new TextView(this);
        tvNodeSubtitle.setText("3 Daemons • Scanning LAN...");
        tvNodeSubtitle.setTextColor(GlassTheme.TEXT_SECONDARY);
        tvNodeSubtitle.setTextSize(13f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = GlassTheme.dp(this, 4);
        subLp.bottomMargin = GlassTheme.dp(this, 18);
        tvNodeSubtitle.setLayoutParams(subLp);
        col.addView(tvNodeSubtitle);

        // Hero Frosted Card (Active LAN Endpoint + Master Power Toggle)
        LinearLayout heroCard = new LinearLayout(this);
        heroCard.setOrientation(LinearLayout.VERTICAL);
        heroCard.setBackground(GlassTheme.createFrostedCard(this, 22f, true));
        heroCard.setPadding(GlassTheme.dp(this, 20), GlassTheme.dp(this, 18), GlassTheme.dp(this, 20), GlassTheme.dp(this, 18));

        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setOrientation(LinearLayout.HORIZONTAL);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvHeroLabel = new TextView(this);
        tvHeroLabel.setText("NODE // POCKET-CLOUD-01");
        tvHeroLabel.setTextColor(GlassTheme.TEXT_PRIMARY);
        tvHeroLabel.setTextSize(15f);
        tvHeroLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeroLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        heroTop.addView(tvHeroLabel);

        tvHeroStatusPill = new TextView(this);
        tvHeroStatusPill.setText("STANDBY");
        tvHeroStatusPill.setTextColor(GlassTheme.ROSE_OFFLINE);
        tvHeroStatusPill.setTextSize(11f);
        tvHeroStatusPill.setTypeface(Typeface.MONOSPACE);
        heroTop.addView(tvHeroStatusPill);
        heroCard.addView(heroTop);

        tvHeroUri = new TextView(this);
        tvHeroUri.setText("http://127.0.0.1");
        tvHeroUri.setTextColor(GlassTheme.TEXT_SECONDARY);
        tvHeroUri.setTextSize(13f);
        tvHeroUri.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams uriLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        uriLp.topMargin = GlassTheme.dp(this, 6);
        uriLp.bottomMargin = GlassTheme.dp(this, 14);
        tvHeroUri.setLayoutParams(uriLp);
        heroCard.addView(tvHeroUri);

        LinearLayout heroActions = new LinearLayout(this);
        heroActions.setOrientation(LinearLayout.HORIZONTAL);
        heroActions.setGravity(Gravity.CENTER_VERTICAL);

        TextView btnCopyUri = new TextView(this);
        btnCopyUri.setText("Copy Direct URI");
        btnCopyUri.setTextColor(GlassTheme.TEXT_PRIMARY);
        btnCopyUri.setTextSize(12f);
        btnCopyUri.setTypeface(Typeface.DEFAULT_BOLD);
        btnCopyUri.setPadding(GlassTheme.dp(this, 14), GlassTheme.dp(this, 8), GlassTheme.dp(this, 14), GlassTheme.dp(this, 8));
        btnCopyUri.setBackground(GlassTheme.createPillButton(this, Color.parseColor("#24FFFFFF"), GlassTheme.BORDER_SUBTLE));
        btnCopyUri.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String uri = "http://" + latestTelemetry.localIp;
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Node LAN URI", uri));
                    Toast.makeText(MainActivity.this, "Copied " + uri, Toast.LENGTH_SHORT).show();
                }
            }
        });
        heroActions.addView(btnCopyUri);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        heroActions.addView(spacer);

        tvEngineToggleBtn = new TextView(this);
        tvEngineToggleBtn.setText("BOOT ENGINE");
        tvEngineToggleBtn.setTextColor(GlassTheme.TEXT_PRIMARY);
        tvEngineToggleBtn.setTextSize(12f);
        tvEngineToggleBtn.setTypeface(Typeface.DEFAULT_BOLD);
        tvEngineToggleBtn.setPadding(GlassTheme.dp(this, 16), GlassTheme.dp(this, 9), GlassTheme.dp(this, 16), GlassTheme.dp(this, 9));
        tvEngineToggleBtn.setBackground(GlassTheme.createPillButton(this, GlassTheme.ACCENT_AMBER, Color.TRANSPARENT));
        tvEngineToggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleCloudEngine(!PocketCloudService.isEngineActive);
            }
        });
        heroActions.addView(tvEngineToggleBtn);

        heroCard.addView(heroActions);
        col.addView(heroCard);

        // Section Header: "System Telemetry" + Glowing Action Circle Button (matching "Monthly Review" row)
        LinearLayout sectionRow = new LinearLayout(this);
        sectionRow.setOrientation(LinearLayout.HORIZONTAL);
        sectionRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secLp.topMargin = GlassTheme.dp(this, 22);
        secLp.bottomMargin = GlassTheme.dp(this, 14);
        sectionRow.setLayoutParams(secLp);

        TextView tvSecTitle = new TextView(this);
        tvSecTitle.setText("System Telemetry");
        tvSecTitle.setTextColor(GlassTheme.TEXT_PRIMARY);
        tvSecTitle.setTextSize(18f);
        tvSecTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvSecTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sectionRow.addView(tvSecTitle);

        TextView btnQuickLaunch = new TextView(this);
        btnQuickLaunch.setText("↗");
        btnQuickLaunch.setTextColor(GlassTheme.TEXT_PRIMARY);
        btnQuickLaunch.setTextSize(16f);
        btnQuickLaunch.setTypeface(Typeface.DEFAULT_BOLD);
        btnQuickLaunch.setGravity(Gravity.CENTER);
        btnQuickLaunch.setBackground(GlassTheme.createCircleBadge(this, GlassTheme.ACCENT_AMBER, Color.TRANSPARENT));
        btnQuickLaunch.setLayoutParams(new LinearLayout.LayoutParams(GlassTheme.dp(this, 38), GlassTheme.dp(this, 38)));
        btnQuickLaunch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(1);
            }
        });
        sectionRow.addView(btnQuickLaunch);
        col.addView(sectionRow);

        // Asymmetric Staggered 2-Column Bento Grid (Exact Reference Proportions)
        LinearLayout bentoRow = new LinearLayout(this);
        bentoRow.setOrientation(LinearLayout.HORIZONTAL);

        // Left Column (Tall card on top, compact card below)
        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        leftLp.rightMargin = GlassTheme.dp(this, 7);
        leftCol.setLayoutParams(leftLp);

        tvStatActiveCount = new TextView(this);
        tvStatActiveLabel = new TextView(this);
        leftCol.addView(createBentoMetricCard(tvStatActiveCount, tvStatActiveLabel, "0/3", "Daemons Live", 156, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(1);
            }
        }));

        tvStatRamVal = new TextView(this);
        tvStatRamLabel = new TextView(this);
        View ramCard = createBentoMetricCard(tvStatRamVal, tvStatRamLabel, "40%", "RAM Load", 122, true, null);
        ((LinearLayout.LayoutParams) ramCard.getLayoutParams()).topMargin = GlassTheme.dp(this, 14);
        leftCol.addView(ramCard);

        // Right Column (Compact card on top, tall card below)
        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightLp.leftMargin = GlassTheme.dp(this, 7);
        rightCol.setLayoutParams(rightLp);

        tvStatThermalVal = new TextView(this);
        tvStatThermalLabel = new TextView(this);
        rightCol.addView(createBentoMetricCard(tvStatThermalVal, tvStatThermalLabel, "31°C", "Optimal Thermal", 122, true, null));

        tvStatNetVal = new TextView(this);
        tvStatNetLabel = new TextView(this);
        View netCard = createBentoMetricCard(tvStatNetVal, tvStatNetLabel, "0 KB/s", "WakeLock Active", 156, true, null);
        ((LinearLayout.LayoutParams) netCard.getLayoutParams()).topMargin = GlassTheme.dp(this, 14);
        rightCol.addView(netCard);

        bentoRow.addView(leftCol);
        bentoRow.addView(rightCol);
        col.addView(bentoRow);

        scroll.addView(col);
        return scroll;
    }

    private View createBentoMetricCard(TextView bigVal, TextView subLabel, String defVal, String defSub, int heightDp, boolean warmGlow, View.OnClickListener clickListener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(GlassTheme.createFrostedCard(this, 24f, warmGlow));
        card.setPadding(GlassTheme.dp(this, 14), GlassTheme.dp(this, 14), GlassTheme.dp(this, 14), GlassTheme.dp(this, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, GlassTheme.dp(this, heightDp));
        card.setLayoutParams(lp);

        bigVal.setText(defVal);
        bigVal.setTextColor(GlassTheme.TEXT_PRIMARY);
        bigVal.setTextSize(26f);
        bigVal.setTypeface(Typeface.DEFAULT_BOLD);
        bigVal.setGravity(Gravity.CENTER);
        card.addView(bigVal);

        subLabel.setText(defSub);
        subLabel.setTextColor(GlassTheme.TEXT_SECONDARY);
        subLabel.setTextSize(12f);
        subLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = GlassTheme.dp(this, 6);
        subLabel.setLayoutParams(subLp);
        card.addView(subLabel);

        if (clickListener != null) {
            card.setOnClickListener(clickListener);
        }
        return card;
    }

    // =========================================================================
    // SCREEN 3: SERVICES TIMELINE & EMBEDDED WEBVIEW LAUNCHER (Reference Right Card)
    // =========================================================================
    private View buildServicesTimelineTab() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(GlassTheme.dp(this, 22), GlassTheme.dp(this, 18), GlassTheme.dp(this, 22), GlassTheme.dp(this, 20));

        // Top Navigation Header
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView btnBackHome = new TextView(this);
        btnBackHome.setText("←");
        btnBackHome.setTextColor(GlassTheme.TEXT_PRIMARY);
        btnBackHome.setTextSize(16f);
        btnBackHome.setGravity(Gravity.CENTER);
        btnBackHome.setBackground(GlassTheme.createCircleBadge(this, GlassTheme.BG_CARD_ELEVATED, GlassTheme.BORDER_SUBTLE));
        btnBackHome.setLayoutParams(new LinearLayout.LayoutParams(GlassTheme.dp(this, 38), GlassTheme.dp(this, 38)));
        btnBackHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTab(0);
            }
        });
        topRow.addView(btnBackHome);

        TextView tvHeader = new TextView(this);
        tvHeader.setText("Cloud Services");
        tvHeader.setTextColor(GlassTheme.TEXT_PRIMARY);
        tvHeader.setTextSize(18f);
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeader.setGravity(Gravity.CENTER);
        tvHeader.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        topRow.addView(tvHeader);

        TextView btnStartAll = new TextView(this);
        btnStartAll.setText("⚡");
        btnStartAll.setTextColor(GlassTheme.BG_VOID);
        btnStartAll.setTextSize(15f);
        btnStartAll.setGravity(Gravity.CENTER);
        btnStartAll.setBackground(GlassTheme.createCircleBadge(this, GlassTheme.ACCENT_AMBER_GLOW, Color.TRANSPARENT));
        btnStartAll.setLayoutParams(new LinearLayout.LayoutParams(GlassTheme.dp(this, 38), GlassTheme.dp(this, 38)));
        btnStartAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleCloudEngine(true);
            }
        });
        topRow.addView(btnStartAll);
        col.addView(topRow);

        // Port Selector Strip (Styled after the 7-day calendar strip in Screen 3)
        LinearLayout portStrip = new LinearLayout(this);
        portStrip.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams stripLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stripLp.topMargin = GlassTheme.dp(this, 20);
        stripLp.bottomMargin = GlassTheme.dp(this, 22);
        portStrip.setLayoutParams(stripLp);

        String[][] slots = new String[][]{
                {"DRIVE", "8080"},
                {"MEDIA", "8096"},
                {"SYNC", "8384"}
        };
        for (final String[] slot : slots) {
            final int portNum = Integer.parseInt(slot[1]);
            boolean isFocused = (selectedPortFocus == portNum);
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(GlassTheme.dp(this, 10), GlassTheme.dp(this, 12), GlassTheme.dp(this, 10), GlassTheme.dp(this, 12));
            if (isFocused) {
                chip.setBackground(GlassTheme.createActiveHighlightCard(this, 18f));
            } else {
                chip.setBackground(GlassTheme.createFrostedCard(this, 18f, false));
            }
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            chipLp.leftMargin = GlassTheme.dp(this, 4);
            chipLp.rightMargin = GlassTheme.dp(this, 4);
            chip.setLayoutParams(chipLp);

            TextView tvTop = new TextView(this);
            tvTop.setText(slot[0]);
            tvTop.setTextColor(isFocused ? GlassTheme.TEXT_PRIMARY : GlassTheme.TEXT_MUTED);
            tvTop.setTextSize(11f);
            tvTop.setTypeface(Typeface.MONOSPACE);
            chip.addView(tvTop);

            TextView tvBot = new TextView(this);
            tvBot.setText(":" + slot[1]);
            tvBot.setTextColor(GlassTheme.TEXT_PRIMARY);
            tvBot.setTextSize(15f);
            tvBot.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams botLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            botLp.topMargin = GlassTheme.dp(this, 4);
            tvBot.setLayoutParams(botLp);
            chip.addView(tvBot);

            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedPortFocus = portNum;
                    switchTab(1);
                }
            });
            portStrip.addView(chip);
        }
        col.addView(portStrip);

        TextView tvOngoing = new TextView(this);
        tvOngoing.setText("Ongoing Daemons");
        tvOngoing.setTextColor(GlassTheme.TEXT_PRIMARY);
        tvOngoing.setTextSize(18f);
        tvOngoing.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams ongLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ongLp.bottomMargin = GlassTheme.dp(this, 14);
        tvOngoing.setLayoutParams(ongLp);
        col.addView(tvOngoing);

        timelineCardsHolder = new LinearLayout(this);
        timelineCardsHolder.setOrientation(LinearLayout.VERTICAL);
        col.addView(timelineCardsHolder);

        renderServiceTimelineRows();

        scroll.addView(col);
        return scroll;
    }

    private void renderServiceTimelineRows() {
        if (timelineCardsHolder == null) return;
        timelineCardsHolder.removeAllViews();

        for (final ServiceNode node : serviceNodes) {
            boolean isHighlighted = (node.port == selectedPortFocus);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = GlassTheme.dp(this, 14);
            row.setLayoutParams(rowLp);

            // Left Port Time-Axis Label (like "9 AM / 10 AM / 11 AM" in Screen 3)
            TextView tvAxis = new TextView(this);
            tvAxis.setText(":" + node.port + "\n" + (node.online ? (node.latencyMs + "ms") : "IDLE"));
            tvAxis.setTextColor(node.online ? GlassTheme.EMERALD_LIVE : GlassTheme.TEXT_MUTED);
            tvAxis.setTextSize(11f);
            tvAxis.setTypeface(Typeface.MONOSPACE);
            LinearLayout.LayoutParams axisLp = new LinearLayout.LayoutParams(GlassTheme.dp(this, 56), ViewGroup.LayoutParams.WRAP_CONTENT);
            tvAxis.setLayoutParams(axisLp);
            row.addView(tvAxis);

            // Right Glass / Highlight Action Card
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(GlassTheme.dp(this, 18), GlassTheme.dp(this, 16), GlassTheme.dp(this, 18), GlassTheme.dp(this, 16));
            card.setBackground(isHighlighted
                    ? GlassTheme.createActiveHighlightCard(this, 20f)
                    : GlassTheme.createFrostedCard(this, 20f, false));
            card.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvTitle = new TextView(this);
            tvTitle.setText(node.title);
            tvTitle.setTextColor(GlassTheme.TEXT_PRIMARY);
            tvTitle.setTextSize(16f);
            tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(tvTitle);

            TextView tvSub = new TextView(this);
            tvSub.setText(node.subtitle);
            tvSub.setTextColor(isHighlighted ? Color.parseColor("#FDE68A") : GlassTheme.TEXT_SECONDARY);
            tvSub.setTextSize(12f);
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sLp.topMargin = GlassTheme.dp(this, 4);
            sLp.bottomMargin = GlassTheme.dp(this, 12);
            tvSub.setLayoutParams(sLp);
            card.addView(tvSub);

            LinearLayout bottomLine = new LinearLayout(this);
            bottomLine.setOrientation(LinearLayout.HORIZONTAL);
            bottomLine.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvState = new TextView(this);
            tvState.setText(node.online ? "● LIVE IN-APP" : "○ TAP TO LAUNCH");
            tvState.setTextColor(isHighlighted ? GlassTheme.TEXT_PRIMARY : (node.online ? GlassTheme.EMERALD_LIVE : GlassTheme.ACCENT_AMBER_GLOW));
            tvState.setTextSize(11f);
            tvState.setTypeface(Typeface.MONOSPACE);
            tvState.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            bottomLine.addView(tvState);

            TextView tvOpen = new TextView(this);
            tvOpen.setText("OPEN UI →");
            tvOpen.setTextColor(GlassTheme.TEXT_PRIMARY);
            tvOpen.setTextSize(11f);
            tvOpen.setTypeface(Typeface.DEFAULT_BOLD);
            bottomLine.addView(tvOpen);

            card.addView(bottomLine);

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedPortFocus = node.port;
                    if (!PocketCloudService.isEngineActive) {
                        toggleCloudEngine(true);
                    }
                    String targetUrl = "http://127.0.0.1:" + node.port;
                    Intent i = new Intent(MainActivity.this, WebWorkspaceActivity.class);
                    i.putExtra(WebWorkspaceActivity.EXTRA_TITLE, node.title);
                    i.putExtra(WebWorkspaceActivity.EXTRA_URL, targetUrl);
                    i.putExtra(WebWorkspaceActivity.EXTRA_HINT, node.credentialsHint);
                    startActivity(i);
                }
            });

            row.addView(card);
            timelineCardsHolder.addView(row);
        }
    }

    // =========================================================================
    // TAB 3: LIVE DAEMON CONSOLE & OPEN-SOURCE PATRONAGE
    // =========================================================================
    private View buildConsoleAndPatronageTab() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(GlassTheme.dp(this, 22), GlassTheme.dp(this, 18), GlassTheme.dp(this, 22), GlassTheme.dp(this, 20));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Daemon Telemetry & Patronage");
        tvTitle.setTextColor(GlassTheme.TEXT_PRIMARY);
        tvTitle.setTextSize(20f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(tvTitle);

        // Live Daemon Console Box
        LinearLayout logCard = new LinearLayout(this);
        logCard.setOrientation(LinearLayout.VERTICAL);
        logCard.setBackground(GlassTheme.createFrostedCard(this, 20f, false));
        logCard.setPadding(GlassTheme.dp(this, 16), GlassTheme.dp(this, 16), GlassTheme.dp(this, 16), GlassTheme.dp(this, 16));
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, GlassTheme.dp(this, 220));
        logLp.topMargin = GlassTheme.dp(this, 14);
        logCard.setLayoutParams(logLp);

        ScrollView logScroll = new ScrollView(this);
        tvConsoleOutput = new TextView(this);
        tvConsoleOutput.setTextColor(GlassTheme.EMERALD_LIVE);
        tvConsoleOutput.setTextSize(11f);
        tvConsoleOutput.setTypeface(Typeface.MONOSPACE);
        logScroll.addView(tvConsoleOutput);
        logCard.addView(logScroll);
        col.addView(logCard);

        // Open-Source Patronage Panel
        LinearLayout patronCard = new LinearLayout(this);
        patronCard.setOrientation(LinearLayout.VERTICAL);
        patronCard.setBackground(GlassTheme.createFrostedCard(this, 22f, true));
        patronCard.setPadding(GlassTheme.dp(this, 18), GlassTheme.dp(this, 18), GlassTheme.dp(this, 18), GlassTheme.dp(this, 18));
        LinearLayout.LayoutParams patLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        patLp.topMargin = GlassTheme.dp(this, 16);
        patronCard.setLayoutParams(patLp);

        TextView tvPatTitle = new TextView(this);
        tvPatTitle.setText("Open-Source Patronage // Huzef Dev");
        tvPatTitle.setTextColor(GlassTheme.TEXT_PRIMARY);
        tvPatTitle.setTextSize(15f);
        tvPatTitle.setTypeface(Typeface.DEFAULT_BOLD);
        patronCard.addView(tvPatTitle);

        TextView tvPatSub = new TextView(this);
        tvPatSub.setText("Self-hosted freedom on bare-metal Android. Star the repository or support independent R&D.");
        tvPatSub.setTextColor(GlassTheme.TEXT_SECONDARY);
        tvPatSub.setTextSize(12f);
        LinearLayout.LayoutParams pSubLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pSubLp.topMargin = GlassTheme.dp(this, 6);
        pSubLp.bottomMargin = GlassTheme.dp(this, 14);
        tvPatSub.setLayoutParams(pSubLp);
        patronCard.addView(tvPatSub);

        patronCard.addView(createPatronButton("★  GitHub: huzefdev/pocket-cloud", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openExternalUrl("https://github.com/huzefdev/pocket-cloud");
            }
        }));

        patronCard.addView(createPatronButton("◈  Discord Community Support", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openExternalUrl("https://discord.gg/GcGkW5fvDh");
            }
        }));

        patronCard.addView(createPatronButton("₿  Copy Monero (XMR) Address", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Monero XMR Wallet", XMR_ADDRESS));
                    Toast.makeText(MainActivity.this, "Monero (XMR) address copied!", Toast.LENGTH_LONG).show();
                }
            }
        }));

        col.addView(patronCard);
        scroll.addView(col);
        return scroll;
    }

    private View createPatronButton(String label, View.OnClickListener listener) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextColor(GlassTheme.TEXT_PRIMARY);
        btn.setTextSize(13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(GlassTheme.dp(this, 16), GlassTheme.dp(this, 12), GlassTheme.dp(this, 16), GlassTheme.dp(this, 12));
        btn.setBackground(GlassTheme.createPillButton(this, Color.parseColor("#1EFFFFFF"), GlassTheme.BORDER_SUBTLE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = GlassTheme.dp(this, 8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        return btn;
    }

    // =========================================================================
    // ENGINE CONTROL & TELEMETRY BINDING
    // =========================================================================
    private void toggleCloudEngine(boolean start) {
        Intent svc = new Intent(this, PocketCloudService.class);
        svc.setAction(start ? PocketCloudService.ACTION_START_ENGINE : PocketCloudService.ACTION_STOP_ENGINE);
        if (start && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        Toast.makeText(this, start ? "Booting PocketCloud Daemons..." : "Stopping Cloud Engine...", Toast.LENGTH_SHORT).show();
    }

    private void bindTelemetryToUi() {
        TelemetrySnapshot s = latestTelemetry;
        if (tvNodeSubtitle != null) {
            tvNodeSubtitle.setText(s.activeDaemons + "/3 Daemons Active  •  " + (s.wakeLockHeld ? "WakeLock Locked" : "CPU Unlocked"));
        }
        if (tvHeroUri != null) {
            tvHeroUri.setText("http://" + s.localIp + "  (:8080 | :8096 | :8384)");
        }
        if (tvHeroStatusPill != null) {
            tvHeroStatusPill.setText(s.engineRunning ? "● ONLINE" : "○ STANDBY");
            tvHeroStatusPill.setTextColor(s.engineRunning ? GlassTheme.EMERALD_LIVE : GlassTheme.ROSE_OFFLINE);
        }
        if (tvEngineToggleBtn != null) {
            tvEngineToggleBtn.setText(s.engineRunning ? "STOP ENGINE" : "BOOT ENGINE");
            tvEngineToggleBtn.setBackground(GlassTheme.createPillButton(
                    this,
                    s.engineRunning ? Color.parseColor("#2EE11D48") : GlassTheme.ACCENT_AMBER,
                    s.engineRunning ? GlassTheme.ROSE_OFFLINE : Color.TRANSPARENT
            ));
        }
        if (tvStatActiveCount != null) {
            tvStatActiveCount.setText(s.activeDaemons + "/3");
            tvStatActiveLabel.setText(s.engineRunning ? "Services Online" : "Engine Standby");
        }
        if (tvStatThermalVal != null) {
            tvStatThermalVal.setText(String.format(Locale.US, "%.0f°C", s.batteryTempC));
            tvStatThermalLabel.setText(s.batteryTempC >= 41.0f ? "Thermal High" : "Optimal • " + s.batteryLevel + "%");
        }
        if (tvStatRamVal != null) {
            tvStatRamVal.setText(s.ramUsedPercent + "%");
            tvStatRamLabel.setText(s.ramUsedMb + " / " + s.ramTotalMb + " MB");
        }
        if (tvStatNetVal != null) {
            tvStatNetVal.setText((s.rxKbps + s.txKbps) + " KB/s");
            tvStatNetLabel.setText("↓" + s.rxKbps + "  ↑" + s.txKbps + " KB/s");
        }
        if (activeTab == 1) {
            renderServiceTimelineRows();
        }
        if (activeTab == 2 && tvConsoleOutput != null) {
            List<String> logs = PocketCloudService.getLogsSnapshot();
            if (logs.isEmpty()) {
                tvConsoleOutput.setText("[SYS] Engine idle. Tap BOOT ENGINE on Node tab to start standalone daemons.");
            } else {
                StringBuilder sb = new StringBuilder();
                for (String line : logs) {
                    sb.append(line).append("\n");
                }
                tvConsoleOutput.setText(sb.toString());
            }
        }
    }

    private void requestRuntimePermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 101);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showStoragePermissionGuide();
            }
        } else if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 102);
        }
    }

    private void showStoragePermissionGuide() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            new AlertDialog.Builder(this)
                    .setTitle("All Files Access Required")
                    .setMessage("Grant 'All files access' so FileBrowser (:8080) and PocketMedia (:8096) can serve your /sdcard storage without Termux.")
                    .setPositiveButton("Grant Access", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            } catch (Exception e) {
                                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                            }
                        }
                    })
                    .setNegativeButton("Later", null)
                    .show();
        }
    }

    private void openExternalUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, url, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        uiHandler.post(telemetryLoop);
    }

    @Override
    protected void onPause() {
        uiHandler.removeCallbacks(telemetryLoop);
        super.onPause();
    }
}
