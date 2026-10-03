package com.deepseekharness.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

/**
 * DSHA 引擎全局初始化。
 *
 * <p>移植后清单里的 Application 是 Compose 外壳的 {@code com.luti.dshlauncher.TemplateApplication}，
 * 它在 onCreate 中调用 {@link #initialize(Application)}。初始化顺序与原 DshaApp.onCreate 完全一致。
 * 保留 DshaApp 子类本身，便于单独测试或回退。
 */
public class DshaApp extends Application {

    private static boolean initialized;

    @Override
    public void onCreate() {
        super.onCreate();
        initialize(this);
    }

    /** 幂等；必须在主线程、任何 DSHA 组件使用前调用。 */
    public static synchronized void initialize(Application app) {
        if (initialized) return;
        initialized = true;
        // 必须早于界面 Locale.setDefault；保留系统原始语言供「跟随系统」使用。
        com.deepseekharness.app.util.SystemLanguage.initialize(app);
        com.deepseekharness.app.data.PortableSettings.initialize(app);
        com.deepseekharness.app.ui.LanguageController.apply(app);
        ShizukuShell.init(app);
        com.deepseekharness.app.ui.ThemeController.apply(app);
        com.deepseekharness.app.core.RuntimeTasks.initialize(app);
        final android.content.Context runtimeApp = app.getApplicationContext();
        com.deepseekharness.app.runtime.RuntimeHostPorts.shared().install(
                new com.deepseekharness.app.runtime.RuntimeHostPorts.Provider() {
                    @Override public com.deepseekharness.app.runtime.RuntimeHostPorts.Settings snapshot() {
                        return new com.deepseekharness.app.core.ConfigStore(runtimeApp).runtimeSettingsSnapshot();
                    }
                    @Override public void stage(String value) {
                        com.deepseekharness.app.core.ColdInstallDiagnostics.stage(runtimeApp, value);
                    }
                    @Override public void record(String kind, String detail) {
                        com.deepseekharness.app.core.ColdInstallDiagnostics.record(runtimeApp, kind, detail);
                    }
                    @Override public void failure(Throwable error) {
                        com.deepseekharness.app.core.ColdInstallDiagnostics.failure(runtimeApp, error);
                    }
                });
        com.deepseekharness.app.backup.AutomaticBackups.schedule(app);
        com.deepseekharness.app.backup.PostUpgradeCleanupService.schedule(app);
        com.deepseekharness.app.core.DiagnosticLog.installCrashHandler(app);
        app.registerActivityLifecycleCallbacks(new com.deepseekharness.app.ui.ModernAndroidUi());
        app.registerActivityLifecycleCallbacks(new ForegroundActivity());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(
                    "dsh_task_channel", com.deepseekharness.app.util.UiText.text("任务通知"), NotificationManager.IMPORTANCE_LOW));
        }
    }
}