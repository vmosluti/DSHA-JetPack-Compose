package com.deepseekharness.app;

import androidx.core.content.FileProvider;

/**
 * DSHA 更新包 / 备份分享用的 FileProvider（authority = ${applicationId}.updates）。
 * Compose 外壳已占用 androidx FileProvider 本类（.fileprovider），同类不能在清单里注册两次。
 */
public final class UpdatesFileProvider extends FileProvider {
}
