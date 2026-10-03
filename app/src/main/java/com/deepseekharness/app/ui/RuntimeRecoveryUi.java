package com.deepseekharness.app.ui;

import android.content.Context;
import android.content.Intent;

/**
 * 回退页面已改为 Compose（{@code RuntimeRollbackPage}）；
 * 这里保留既有 Java 调用点需要的入口，转到承载它的 {@link UpdateActivity}。
 */
final class RuntimeRecoveryUi {
    static void show(Context context) {
        Intent intent = new Intent(context, UpdateActivity.class).putExtra(UpdateActivity.EXTRA_ROLLBACK, true);
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}