/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.os.Build
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.UserDataManager
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceFragment
import org.fcitx.fcitx5.android.ui.common.withLoadingDialog
import org.fcitx.fcitx5.android.ui.main.MainViewModel
import org.fcitx.fcitx5.android.utils.AppUtil
import org.fcitx.fcitx5.android.utils.addPreference
import org.fcitx.fcitx5.android.utils.buildDocumentsProviderIntent
import org.fcitx.fcitx5.android.utils.buildPrimaryStorageIntent
import org.fcitx.fcitx5.android.utils.formatDateTime
import org.fcitx.fcitx5.android.utils.importErrorDialog
import org.fcitx.fcitx5.android.utils.iso8601UTCDateTime
import org.fcitx.fcitx5.android.utils.queryFileName
import org.fcitx.fcitx5.android.utils.toast
import android.util.Log
import java.io.File

class AdvancedSettingsFragment : ManagedPreferenceFragment(AppPrefs.getInstance().advanced) {

    private val viewModel: MainViewModel by activityViewModels()

    private var exportTimestamp = System.currentTimeMillis()

    private lateinit var exportLauncher: ActivityResultLauncher<String>

    private lateinit var importLauncher: ActivityResultLauncher<String>

    private fun getRimeDir(context: android.content.Context): File {
        val TAG = "RimePath"

        // 1. 获取外部存储路径: /storage/emulated/0/Android/data/包名/files/data/rime
        val externalFilesDir = context.getExternalFilesDir(null)
        val externalRimeDir = File(externalFilesDir, "data/rime")

        // 2. 检查外部目录是否存在（即用户是否已经放了配置在里面）
        val finalDir = if (externalRimeDir.exists() && externalRimeDir.isDirectory) {
            Log.d(TAG, "使用外部存储目录")
            externalRimeDir
        } else {
            // 3. 否则使用内部存储路径: /data/user/0/包名/files/data/rime
            Log.d(TAG, "外部目录不存在，回退至内部存储目录")
            File(context.filesDir, "data/rime")
        }

        // 确保目录结构存在，避免后续读写报错
        if (!finalDir.exists()) {
            val created = finalDir.mkdirs()
            Log.d(TAG, "目录不存在，尝试创建: $created")
        }

        // 4. 打印最终生成的全路径
        Log.i(TAG, "最终使用的 Rime 路径: ${finalDir.absolutePath}")

        return finalDir
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri == null) return@registerForActivityResult
                val ctx = requireContext()
                val cr = ctx.contentResolver
                lifecycleScope.withLoadingDialog(ctx) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        val name = cr.queryFileName(uri) ?: return@withContext
                        if (!name.endsWith(".zip")) {
                            ctx.importErrorDialog(R.string.exception_user_data_filename, name)
                            return@withContext
                        }
                        try {
                            // stop fcitx before overwriting files
                            FcitxDaemon.stopFcitx()
                            val inputStream = cr.openInputStream(uri)!!
                            val metadata = UserDataManager.import(inputStream).getOrThrow()
                            lifecycleScope.launch(NonCancellable + Dispatchers.Main) {
                                delay(400L)
                                AppUtil.exit()
                            }
                            withContext(Dispatchers.Main) {
                                AppUtil.showRestartNotification(ctx)
                                val exportTime = formatDateTime(metadata.exportTime)
                                ctx.toast(getString(R.string.user_data_imported, exportTime))
                            }
                        } catch (e: Exception) {
                            // re-start fcitx in case importing failed
                            FcitxDaemon.startFcitx()
                            ctx.importErrorDialog(e)
                        }
                    }
                }
            }
        exportLauncher =
            registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                if (uri == null) return@registerForActivityResult
                val ctx = requireContext()
                lifecycleScope.withLoadingDialog(requireContext()) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        try {
                            val outputStream = ctx.contentResolver.openOutputStream(uri)!!
                            UserDataManager.export(outputStream, exportTimestamp).getOrThrow()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                ctx.toast(e)
                            }
                        }
                    }
                }
            }
    }

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        val ctx = requireContext()
        screen.addPreference(
            R.string.browse_user_data_dir,
            onClick = {
                try {
                    ctx.startActivity(buildDocumentsProviderIntent())
                } catch (e: Exception) {
                    ctx.toast(e)
                }
            },
            onLongClick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ({
                try {
                    ctx.startActivity(buildPrimaryStorageIntent())
                } catch (e: Exception) {
                    ctx.toast(e)
                }
            }) else null
        )
        screen.addPreference(R.string.export_user_data) {
            lifecycleScope.launch {
                lifecycleScope.withLoadingDialog(ctx) {
                    viewModel.fcitx.runOnReady {
                        save()
                    }
                }
                exportTimestamp = System.currentTimeMillis()
                exportLauncher.launch("fcitx5-android_${iso8601UTCDateTime(exportTimestamp)}.zip")
            }
        }
        screen.addPreference(R.string.import_user_data) {
            AlertDialog.Builder(ctx)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.import_user_data)
                .setMessage(R.string.confirm_import_user_data)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    importLauncher.launch("application/zip")
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        screen.addPreference("初始化词库") {
            AlertDialog.Builder(ctx)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle("初始化词库")
                .setMessage("确定要初始化词库吗？这将删除现有的词库构建文件并重新复制词库文件。")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.withLoadingDialog(ctx) {
                        withContext(NonCancellable + Dispatchers.IO) {
                            val TAG = "RimePath"
                            try {
                                Log.i(TAG, ">>> 开始强制覆盖词库初始化...")

                                // 1. 停止服务
                                FcitxDaemon.stopFcitx()
                                Log.d(TAG, "Fcitx 已停止")

                                val rimeDir = getRimeDir(ctx)
                                Log.d(TAG, "工作目标路径: ${rimeDir.absolutePath}")

                                // 2. 清理旧缓存（强制 Rime 重新部署）
                                val buildDir = rimeDir.resolve("build")
                                if (buildDir.exists()) {
                                    val deleted = buildDir.deleteRecursively()
                                    Log.d(TAG, "清理旧 build 目录: $deleted")
                                }

                                // 3. 确保父目录存在
                                if (!rimeDir.exists()) rimeDir.mkdirs()

                                // 4. 定义要覆盖的文件列表
                                val filesToCopy = arrayOf(
                                    "luna_pinyin.extended.dict.yaml",
                                    "t9_pinyin.schema.yaml",
                                    "toughbone.dict.yaml",
                                    "base_high.dict.yaml",
                                    "common_3500.dict.yaml"
                                )

                                val assetManager = ctx.assets
                                filesToCopy.forEach { fileName ->
                                    val outputFile = rimeDir.resolve(fileName)
                                    try {
                                        if (outputFile.exists()) {
                                            val deleted = outputFile.delete()
                                            Log.d(TAG, "删除旧文件 $fileName: $deleted")
                                        }

                                        assetManager.open(fileName).use { input ->
                                            outputFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        Log.d(TAG, "覆盖成功: $fileName")
                                    } catch (e: Exception) {
                                        val errorMsg = "复制 $fileName 失败: ${e.message}"
                                        Log.e(TAG, errorMsg)

                                        // 关键：切换到主线程弹出 Toast
                                        withContext(Dispatchers.Main) {
                                            ctx.toast(errorMsg)
                                        }
                                    }
                                }

                                // 5. 重启服务
                                FcitxDaemon.startFcitx()
                                Log.i(TAG, "<<< 初始化完成，Fcitx 已重启")

                                withContext(Dispatchers.Main) {
                                    ctx.toast("词库已强制更新并重启")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "致命错误: ", e)
                                FcitxDaemon.startFcitx() // 保底重启
                                withContext(Dispatchers.Main) {
                                    ctx.toast("初始化异常: ${e.message}")
                                }
                            }
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
