package com.deepseekharness.app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.deepseekharness.app.core.ConfigStore;
import com.deepseekharness.app.core.HarnessController;

/**
 * DSHA 的原主界面已由 Compose 外壳（com.luti.dshlauncher.ui.MainActivity）取代。
 *
 * <p>这个类保留原类名，因为引擎层（通知、HttpShellService、DataProtectionService、
 * Extract/Onboarding/StartupRecovery 等）仍以它为「回到主界面」的目标。它只做两件事：
 * 执行与原版一致的启动门禁，然后把全部 extras（open_web / open_plugins / open_terminal /
 * open_install / open_launch / limited_entry）原样转交给 Compose 外壳。
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!gate(this, getIntent())) forward(this, getIntent());
        finish();
    }

    /**
     * 原 MainActivity.onCreate 的启动门禁（AGENTS.md：welcomed 校验不可绕过）。
     *
     * @return true 表示已跳转到 Welcome / Extract，调用方应结束自身。
     */
    public static boolean gate(Activity activity, Intent source) {
        ConfigStore config = new ConfigStore(activity);
        HarnessController controller = HarnessController.get(activity);
        boolean skipExtract = com.luti.dshlauncher.BuildConfig.DEBUG
                && source != null && source.getBooleanExtra("skip_extract", false);
        if (!config.isWelcomed()) {
            activity.startActivity(new Intent(activity, WelcomeActivity.class));
            return true;
        }
        boolean limitedAllowed = source != null && source.getBooleanExtra("limited_entry", false)
                || config.allowsLimitedEntry(controller.proot().environmentIdentity());
        if (!limitedAllowed && (com.deepseekharness.app.core.MaintenanceCoordinator.pending(controller)
                || !skipExtract && (!controller.isEnvironmentReady()
                || com.deepseekharness.app.core.EnvironmentAccess.shouldAttemptRuntimeUpdate(controller)))) {
            activity.startActivity(new Intent(activity, ExtractActivity.class));
            return true;
        }
        return false;
    }

    /** 转交给 Compose 外壳；单任务复用已存在的外壳实例。 */
    public static void forward(Context context, Intent source) {
        Intent target = new Intent(context, com.luti.dshlauncher.ui.MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (source != null && source.getExtras() != null) target.putExtras(source.getExtras());
        target.putExtra(com.luti.dshlauncher.ui.MainActivity.EXTRA_GATE_PASSED, true);
        if (!(context instanceof Activity)) target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(target);
    }

    public static void start(Context ctx) {
        ctx.startActivity(new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }
}