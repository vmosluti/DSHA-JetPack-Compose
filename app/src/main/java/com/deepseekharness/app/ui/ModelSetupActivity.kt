package com.deepseekharness.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.luti.dshlauncher.ui.dsha.observeAsState
import com.deepseekharness.app.core.DshModelRepository
import com.deepseekharness.app.util.ModelConfiguration
import com.deepseekharness.app.util.UiText
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.luti.dshlauncher.ui.LocalUiMode
import com.luti.dshlauncher.ui.UiMode
import com.luti.dshlauncher.ui.dsha.DshaAction
import com.luti.dshlauncher.ui.dsha.DshaActionDialog
import com.luti.dshlauncher.ui.dsha.DshaCard
import com.luti.dshlauncher.ui.dsha.DshaChoiceSheet
import com.luti.dshlauncher.ui.dsha.DshaContentDialog
import com.luti.dshlauncher.ui.dsha.DshaDialogAction
import com.luti.dshlauncher.ui.dsha.DshaDropdownRow
import com.luti.dshlauncher.ui.dsha.DshaEntry
import com.luti.dshlauncher.ui.dsha.DshaMessageDialog
import com.luti.dshlauncher.ui.dsha.DshaNote
import com.luti.dshlauncher.ui.dsha.DshaPageScaffold
import com.luti.dshlauncher.ui.dsha.DshaSecondaryButton
import com.luti.dshlauncher.ui.dsha.DshaSwitchRow
import com.luti.dshlauncher.ui.dsha.DshaTextField
import com.luti.dshlauncher.ui.dsha.dshaEntryCard
import com.luti.dshlauncher.ui.dsha.setDshaContent
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首次配置与快捷入口共用的模型编辑器（Compose 版），读写 DSH 的实际配置。
 *
 * 草稿保存在 [Draft]（ViewModel），旋转重建不丢编辑内容；输入框直接写回草稿，
 * 因此不再需要原 View 版的 capture()/onPause 回收。
 */
class ModelSetupActivity : androidx.fragment.app.FragmentActivity() {

    /** 编辑草稿；字段以 @JvmField 暴露给 ModelSetupDraftTest。 */
    class Draft : ViewModel() {
        @JvmField var entry: JsonObject? = null
        @JvmField var original: JsonObject? = null
        @JvmField var value: JsonObject? = null
        @JvmField var extra: JsonObject? = null
        @JvmField var modelList: JsonArray? = null
        @JvmField var headers: JsonArray? = null
        @JvmField var revision = 0L
        @JvmField var generation = 0L
        @JvmField var handledDiscovery = 0L
        @JvmField var custom = false
        @JvmField var route = ""
        @JvmField var name = ""
        @JvmField var endpoint = ""
        @JvmField var protocol = ""
        @JvmField var key = ""

        fun shouldShowDiscovery(serial: Long): Boolean = entry != null && serial > handledDiscovery
        fun consumeDiscovery(serial: Long) { handledDiscovery = maxOf(handledDiscovery, serial) }
        fun clear() {
            entry = null; original = null; value = null; extra = null
            modelList = null; headers = null; key = ""
        }
        override fun onCleared() { key = "" }
    }

    private lateinit var repository: DshModelRepository
    private lateinit var draft: Draft
    private var current: JsonObject? = null
    private var observedSave = 0L

    /** 草稿是普通字段；每次改动草稿后递增，驱动 Compose 重组。 */
    private val tick = mutableIntStateOf(0)
    private var status by mutableStateOf("")
    private var discardVisible by mutableStateOf(false)
    private var providerSheet by mutableStateOf(false)
    /** null=未打开；-1=新增；>=0 编辑对应下标。 */
    private var modelEditor by mutableStateOf<Int?>(null)
    private var advancedVisible by mutableStateOf(false)

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        repository = ViewModelProvider(this)[DshModelRepository::class.java]
        draft = ViewModelProvider(this)[Draft::class.java]
        observedSave = repository.savedRevision.value ?: 0L
        current = repository.data.value

        setDshaContent {
            val data by repository.data.observeAsState()
            val message by repository.message.observeAsState("")
            val busy by repository.busy.observeAsState(false)
            val revision by repository.savedRevision.observeAsState(0L)
            val discovery by repository.modelDiscovery.observeAsState()
            tick.intValue // 订阅草稿变更

            LaunchedEffect(data) { current = data; bump() }
            LaunchedEffect(message) { status = message.orEmpty() }
            LaunchedEffect(revision) {
                if ((revision ?: 0L) > observedSave) {
                    observedSave = revision ?: 0L
                    draft.clear()
                    bump()
                }
            }
            BackHandler { leave(busy == true) }

            val data0 = current
            when {
                data0 == null -> Disconnected()
                draft.entry != null -> Editor(data0, busy == true)
                else -> Directory(data0)
            }

            discovery?.let { result ->
                if (draft.shouldShowDiscovery(result.serial)) DiscoveryDialog(result)
            }
            DshaActionDialog(
                title = t("放弃本次编辑？", "Discard these edits?"),
                message = "",
                visible = discardVisible,
                onDismiss = { discardVisible = false },
                actions = listOf(
                    DshaDialogAction(t("继续编辑", "Keep editing")) {},
                    DshaDialogAction(t("放弃", "Discard")) { draft.clear(); bump() },
                ),
            )
        }
        if (current == null) repository.load()
    }

    private fun bump() { tick.intValue++ }

    private fun leave(busy: Boolean) {
        if (busy && draft.entry != null) {
            status = t("正在处理，请等待结果。", "Working. Please wait for the result.")
            return
        }
        if (draft.entry != null) { discardVisible = true; return }
        if (intent.getBooleanExtra("first_run", false)) {
            startActivity(Intent(this, OnboardingReadyActivity::class.java))
        }
        finish()
    }

    // ---------------- 页面 ----------------

    @Composable
    private fun Disconnected() {
        DshaPageScaffold(
            title = t("模型配置", "Model settings"),
            subtitle = SUBTITLE,
            onBack = { leave(false) },
            footer = listOf(DshaAction(t("重新连接", "Reconnect"), primary = true) { repository.load() }),
        ) {
            if (status.isNotEmpty()) item { DshaNote(status) }
        }
    }

    @Composable
    private fun Directory(data: JsonObject) {
        val writable = data.getAsJsonObject("settings").get("writable").asBoolean
        val configured = buildList {
            for (item in data.getAsJsonArray("providers")) {
                val entry = item.asJsonObject
                val ns = namespace(s(entry, "settingsNs"))
                val path = entry.getAsJsonArray("settingsPath")
                val value = ModelConfiguration.at(ns.get("value"), path)
                if (path.size() > 0 && value.isJsonNull) continue
                val title = s(entry, "displayName").ifEmpty { s(entry, "provider") }
                val detail = s(ModelConfiguration.`object`(value), "baseURL")
                    .ifEmpty { t("使用服务商默认地址", "Uses provider default endpoint") }
                add(DshaEntry(title, detail) { if (writable) open(entry, false) })
            }
        }
        val footer = if (intent.getBooleanExtra("first_run", false)) {
            listOf(DshaAction(t("继续进入 DSHA", "Continue to DSHA"), primary = true) { leave(false) })
        } else emptyList()
        DshaPageScaffold(
            title = t("模型配置", "Model settings"),
            subtitle = SUBTITLE,
            onBack = { leave(false) },
            footer = footer,
        ) {
            if (status.isNotEmpty()) item { DshaNote(status) }
            if (configured.isNotEmpty()) item { SectionLabel(t("已配置的服务商", "Configured providers")) }
            dshaEntryCard(configured)
            dshaEntryCard(
                listOf(
                    DshaEntry(
                        t("添加第三方模型", "Add third-party models"),
                        t(
                            "填写服务商 API 地址、密钥与模型 ID，支持 OpenAI / Anthropic 等协议",
                            "Enter an endpoint, API key and model ID. Supports OpenAI, Anthropic and other protocols",
                        ),
                    ) { if (writable) openCustom() },
                    DshaEntry(
                        t("选择内置服务商", "Choose a built-in provider"),
                        t("使用 DSH 已支持的服务商配置", "Use a provider supported by DSH"),
                    ) { if (writable) providerSheet = true },
                ),
            )
            item { DshaSecondaryButton(t("重新读取配置", "Reload settings")) { repository.load() } }
        }
        val providers = data.getAsJsonArray("providers")
        DshaChoiceSheet(
            title = t("添加服务商", "Add a provider"),
            items = listOf(t("自定义提供方", "Custom provider")) +
                providers.map { s(it.asJsonObject, "displayName") },
            visible = providerSheet,
            onDismiss = { providerSheet = false },
            onSelect = { index ->
                if (index == 0) openCustom() else open(providers.get(index - 1).asJsonObject, false)
            },
        )
    }

    @Composable
    private fun Editor(data: JsonObject, busy: Boolean) {
        val entry = draft.entry ?: return
        val deepseek = deepseek()
        val protocolPath = entry.getAsJsonArray("settingsPath").deepCopy().apply { add(if (deepseek) "protocol" else "api") }
        val protocols = buildList {
            add(t("服务商默认", "Provider default"))
            addAll(ModelConfiguration.choices(namespace(s(entry, "settingsNs")).getAsJsonObject("schema"), protocolPath))
            if (draft.protocol.isNotEmpty() && !contains(draft.protocol)) add(draft.protocol)
        }
        DshaPageScaffold(
            title = t("模型配置", "Model settings"),
            subtitle = SUBTITLE,
            onBack = { leave(busy) },
            footer = listOf(DshaAction(t("保存并同步", "Save and sync"), primary = true, enabled = !busy) { save(data) }),
        ) {
            if (status.isNotEmpty()) item { DshaNote(status) }
            item {
                tick.intValue
                DshaCard {
                    SectionLabel(if (draft.custom) t("自定义提供方", "Custom provider") else s(entry, "displayName"))
                    if (draft.custom) {
                        DshaTextField(
                            title = t("唯一标识（小写英文）", "Provider ID (lowercase)"),
                            value = draft.route, hint = "my-provider",
                            onValueChange = { draft.route = it.trim(); bump() },
                        )
                    }
                    if (!deepseek) {
                        DshaTextField(
                            title = t("显示名称", "Display name"),
                            value = draft.name, hint = t("可选", "Optional"),
                            onValueChange = { draft.name = it; bump() },
                        )
                    }
                    DshaTextField(
                        title = t("API 地址", "API endpoint"),
                        value = draft.endpoint,
                        hint = if (deepseek) t("留空使用 DeepSeek 官方地址", "Leave blank for the official DeepSeek endpoint")
                        else "https://api.example.com/v1",
                        keyboardType = KeyboardType.Uri,
                        onValueChange = { draft.endpoint = it.trim(); bump() },
                    )
                    DshaDropdownRow(
                        title = t("连接协议", "Protocol"),
                        items = protocols,
                        selectedIndex = maxOf(0, protocols.indexOf(draft.protocol)),
                        onSelectedIndexChange = { index ->
                            draft.protocol = if (index <= 0) "" else protocols[index]
                            bump()
                        },
                    )
                    DshaTextField(
                        title = "API Key",
                        value = draft.key,
                        hint = t("留空保留已有密钥", "Leave blank to keep the existing key"),
                        summary = t(
                            "密钥不会在此回显。新密钥通过 DSH 凭据服务保存。",
                            "Existing keys are never shown here. New keys are saved through DSH credentials.",
                        ),
                        password = true,
                        keyboardType = KeyboardType.Password,
                        onValueChange = { draft.key = it.trim(); bump() },
                    )
                }
            }
            if (!deepseek) item { tick.intValue; HeadersCard() }
            item { tick.intValue; ModelsCard(busy) }
            item {
                DshaSecondaryButton(t("高级配置", "Advanced settings")) { advancedVisible = true }
            }
        }
        modelEditor?.let { ModelDialog(it) }
        if (advancedVisible) AdvancedDialog()
    }

    @Composable
    private fun HeadersCard() {
        val rows = draft.headers ?: JsonArray().also { draft.headers = it }
        DshaCard {
            SectionLabel(t("自定义请求头", "Custom request headers"))
            for (i in 0 until rows.size()) {
                val row = rows.get(i).asJsonObject
                DshaTextField(
                    title = t("名称", "Name"),
                    value = s(row, "name"), hint = "x-opencode-session",
                    onValueChange = { row.addProperty("name", it); bump() },
                )
                DshaTextField(
                    title = t("值", "Value"),
                    value = s(row, "value"), hint = t("服务商要求的值", "Value required by the provider"),
                    password = true,
                    onValueChange = { row.addProperty("value", it); bump() },
                )
                CardButton(t("移除此请求头", "Remove header")) { rows.remove(i); bump() }
            }
            CardButton(t("添加请求头", "Add header")) {
                if (rows.size() < 32) { rows.add(JsonObject()); bump() }
            }
            DshaNote(
                t(
                    "仅发送给此服务商；保存后用于模型请求和目录查询。",
                    "Sent only to this provider. Save to apply to model requests and model discovery.",
                ),
            )
        }
    }

    @Composable
    private fun ModelsCard(busy: Boolean) {
        val list = draft.modelList ?: JsonArray().also { draft.modelList = it }
        DshaCard {
            SectionLabel(t("模型目录", "Models"))
            for (i in 0 until list.size()) {
                val model = list.get(i).asJsonObject
                EntryRow(
                    s(model, "id"),
                    s(model, "name").ifEmpty { t("编辑模型与容量", "Edit model and capacity") },
                ) { modelEditor = i }
            }
            if ("llm-pi-ai" == s(draft.entry!!, "settingsNs")) {
                CardButton(t("获取可用模型", "Fetch available models"), enabled = !busy) { fetchModels() }
            }
            CardButton(t("添加模型", "Add model")) { modelEditor = -1 }
            DshaNote(
                t(
                    "不修改目录会保留服务商原有模型和能力。",
                    "Unchanged catalogs retain the provider's models and capabilities.",
                ),
            )
        }
    }

    // ---------------- 对话框 ----------------

    @Composable
    private fun DiscoveryDialog(result: DshModelRepository.ModelDiscovery) {
        val serial = result.serial
        val found = result.models
        val consume = { draft.consumeDiscovery(serial); bump() }
        if (found == null || found.isEmpty) {
            DshaMessageDialog(
                title = t("未发现模型", "No models found"),
                message = t(
                    "服务商没有返回可用模型。现有模型目录保持不变。",
                    "The provider returned no available models. The current catalog is unchanged.",
                ),
                visible = true,
                onDismiss = consume,
            )
            return
        }
        val known = (draft.modelList ?: JsonArray()).filter { it.isJsonObject }.map { s(it.asJsonObject, "id") }.toSet()
        val selected = remember(serial) { mutableStateListOf(*Array(found.size()) { true }) }
        DshaContentDialog(
            title = t("选择可用模型", "Choose available models"),
            visible = true,
            onDismiss = consume,
            onClose = {},
            actions = listOf(
                DshaDialogAction(t("取消", "Cancel")) { consume() },
                DshaDialogAction(t("合并所选模型", "Add selected models")) {
                    val picked = LinkedHashSet<String>()
                    for (i in 0 until found.size()) if (selected[i]) picked.add(s(found.get(i).asJsonObject, "id"))
                    val before = draft.modelList?.size() ?: 0
                    draft.modelList = ModelConfiguration.mergeDiscoveredModels(draft.modelList ?: JsonArray(), found, picked)
                    val added = draft.modelList!!.size() - before
                    draft.consumeDiscovery(serial)
                    status = if (added == 0) {
                        t(
                            "所选模型已在目录中，现有详情保持不变。",
                            "The selected models are already in the catalog. Existing details were preserved.",
                        )
                    } else {
                        t("已将 ", "Added ") + added +
                            t(" 个模型合并到草稿；保存后同步到 Web UI。", " models to the draft. Save to sync them with Web UI.")
                    }
                    bump()
                },
            ),
        ) {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                for (i in 0 until found.size()) {
                    val candidate = found.get(i).asJsonObject
                    val id = s(candidate, "id")
                    val display = s(candidate, "name")
                    val label = id + (if (display.isEmpty() || display == id) "" else " · $display")
                    DshaSwitchRow(
                        title = label,
                        summary = if (id in known) t("已在目录", "Already in catalog") else null,
                        checked = selected[i],
                        onCheckedChange = { selected[i] = it },
                    )
                }
            }
        }
    }

    @Composable
    private fun ModelDialog(index: Int) {
        val original = remember(index) {
            if (index < 0) JsonObject() else draft.modelList!!.get(index).asJsonObject.deepCopy()
        }
        val inputField = if (deepseek()) "inputModalities" else "input"
        val prior = original.has(inputField) && original.get(inputField).toString().contains("\"image\"")
        var id by remember(index) { mutableStateOf(s(original, "id")) }
        var display by remember(index) { mutableStateOf(s(original, "name")) }
        var context by remember(index) { mutableStateOf(s(original, "contextWindow")) }
        var output by remember(index) { mutableStateOf(s(original, "maxTokens")) }
        var vision by remember(index) { mutableStateOf(prior) }
        var error by remember(index) { mutableStateOf<String?>(null) }
        val close = { modelEditor = null }
        val actions = buildList {
            if (index >= 0) {
                add(DshaDialogAction(t("从目录移除此模型", "Remove from catalog")) {
                    draft.modelList!!.remove(index); close(); bump()
                })
            }
            add(DshaDialogAction(t("取消", "Cancel")) { close() })
            add(DshaDialogAction(t("保存模型", "Save model")) {
                try {
                    val next = original.deepCopy()
                    next.addProperty("id", id.trim())
                    setOptional(next, "name", display.trim())
                    for ((field, raw) in listOf("contextWindow" to context, "maxTokens" to output)) {
                        val value = raw.trim()
                        if (value.isEmpty()) next.remove(field) else next.addProperty(field, value.toLong())
                    }
                    if (vision != prior || index < 0) {
                        next.add(
                            inputField,
                            if (vision) ModelConfiguration.path("text", "image") else ModelConfiguration.path("text"),
                        )
                    }
                    val checked = draft.modelList!!.deepCopy()
                    if (index < 0) checked.add(next) else checked.set(index, next)
                    ModelConfiguration.validateModels(checked.toString(), true)
                    draft.modelList = checked
                    close(); bump()
                } catch (_: RuntimeException) {
                    error = t("请检查模型 ID、重复项和正整数容量。", "Check model ID, duplicates and positive integer capacities.")
                }
            })
        }
        DshaContentDialog(
            title = t("模型详情", "Model details"),
            visible = true,
            onDismiss = close,
            onClose = {},
            actions = actions,
        ) {
            DshaTextField(title = "模型 ID / Model ID", value = id, hint = "model-name", onValueChange = { id = it })
            DshaTextField(title = t("显示名称", "Display name"), value = display, hint = t("可选", "Optional"), onValueChange = { display = it })
            DshaTextField(
                title = t("上下文窗口", "Context window"), value = context,
                hint = t("留空使用默认值", "Leave blank for default"),
                keyboardType = KeyboardType.Number,
                onValueChange = { context = it.filter(Char::isDigit) },
            )
            DshaTextField(
                title = t("最大输出 Token", "Maximum output tokens"), value = output,
                hint = t("留空使用默认值", "Leave blank for default"),
                keyboardType = KeyboardType.Number,
                onValueChange = { output = it.filter(Char::isDigit) },
            )
            DshaSwitchRow(title = t("支持图片输入", "Supports image input"), checked = vision, onCheckedChange = { vision = it })
            error?.let { DshaNote(it) }
        }
    }

    @Composable
    private fun AdvancedDialog() {
        val initial = remember {
            val extra = (draft.extra ?: draft.original ?: JsonObject()).deepCopy()
            MANAGED.forEach { extra.remove(it) }
            GsonBuilder().setPrettyPrinting().create().toJson(extra)
        }
        var json by remember { mutableStateOf(initial) }
        var error by remember { mutableStateOf<String?>(null) }
        val close = { advancedVisible = false }
        DshaContentDialog(
            title = t("高级配置", "Advanced settings"),
            visible = true,
            onDismiss = close,
            onClose = {},
            actions = listOf(
                DshaDialogAction(t("取消", "Cancel")) { close() },
                DshaDialogAction(t("应用到草稿", "Apply to draft")) {
                    try {
                        val value = JsonParser.parseString(json).asJsonObject
                        if (MANAGED.any { value.has(it) }) throw IllegalArgumentException()
                        draft.extra = value
                        close()
                    } catch (_: RuntimeException) {
                        error = t(
                            "请输入 JSON 对象，连接、请求头、模型和密钥请在主表单修改。",
                            "Enter a JSON object. Edit connection, headers, models and credentials in the main form.",
                        )
                    }
                },
            ),
        ) {
            DshaNote(
                t(
                    "只编辑此服务商的附加字段；模型目录和密钥由主表单管理。",
                    "Edit additional provider fields. Models and credentials use the main form.",
                ),
            )
            DshaTextField(title = "JSON", value = json, hint = "{}", lines = 6, onValueChange = { json = it; error = null })
            error?.let { DshaNote(it) }
        }
    }

    // ---------------- 小组件 ----------------

    @Composable
    private fun SectionLabel(text: String) {
        when (LocalUiMode.current) {
            UiMode.Miuix -> MiuixText(
                text = text,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            )
            UiMode.Material -> Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            )
        }
    }

    @Composable
    private fun EntryRow(title: String, summary: String, onClick: () -> Unit) {
        when (LocalUiMode.current) {
            UiMode.Miuix -> BasicComponent(title = title, summary = summary, onClick = onClick)
            UiMode.Material -> androidx.compose.material3.ListItem(
                headlineContent = { Text(title) },
                supportingContent = { Text(summary) },
                modifier = Modifier.clickable(onClick = onClick),
            )
        }
    }

    @Composable
    private fun CardButton(title: String, enabled: Boolean = true, onClick: () -> Unit) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            DshaSecondaryButton(title, enabled, onClick)
        }
    }

    // ---------------- 逻辑（与原 Java 版一致） ----------------

    private fun namespace(name: String): JsonObject {
        current?.getAsJsonObject("settings")?.getAsJsonArray("namespaces")?.forEach { item ->
            if (name == s(item.asJsonObject, "ns")) return item.asJsonObject
        }
        return JsonObject()
    }

    private fun openCustom() {
        val entry = JsonObject()
        entry.addProperty("provider", "")
        entry.addProperty("settingsNs", "llm-pi-ai")
        entry.add("settingsPath", ModelConfiguration.path("providers", ""))
        open(entry, true)
        if (draft.entry != null && draft.custom) { draft.protocol = "openai-completions"; bump() }
    }

    private fun open(entry: JsonObject, custom: Boolean) {
        val data = current ?: return
        val ns = namespace(s(entry, "settingsNs"))
        if (!ns.has("revision")) {
            status = t("此服务商组件尚不可用，请检查插件。", "Provider component unavailable. Check plugins.")
            return
        }
        draft.entry = entry.deepCopy()
        draft.custom = custom
        draft.revision = ns.get("revision").asLong
        draft.generation = data.get("generation").asLong
        draft.extra = null
        val path = entry.getAsJsonArray("settingsPath")
        draft.original = ModelConfiguration.`object`(ModelConfiguration.at(ns.get("user"), path)).deepCopy()
        val value = ModelConfiguration.`object`(ModelConfiguration.at(ns.get("value"), path)).deepCopy()
        draft.value = value
        draft.route = s(entry, "provider")
        draft.name = s(value, "displayName")
        draft.endpoint = s(value, "baseURL")
        draft.protocol = s(value, if (deepseek()) "protocol" else "api")
        draft.key = ""
        draft.headers = ModelConfiguration.headerRows(ModelConfiguration.`object`(value.get("headers")))
        draft.modelList = if (value.has("models")) value.getAsJsonArray("models").deepCopy() else JsonArray()
        status = t("编辑完成后，点击下方保存。", "Save below when your edits are ready.")
        bump()
    }

    private fun deepseek(): Boolean = "llm-deepseek" == s(draft.entry ?: JsonObject(), "settingsNs")

    private fun fetchModels() {
        try {
            if (draft.endpoint.isEmpty() && draft.route.isEmpty()) throw IllegalArgumentException("URL")
            ModelConfiguration.validateUrl(draft.endpoint, false)
            if (draft.key.isNotEmpty() && !draft.key.matches(KEY_PATTERN)) throw IllegalArgumentException("KEY")
            repository.discoverModels(s(draft.entry!!, "settingsNs"), draft.route, draft.endpoint, draft.protocol, draft.key)
        } catch (invalid: RuntimeException) {
            status = if (invalid.message == "KEY") {
                t("API Key 不应包含空格或换行。", "API keys must not contain whitespace.")
            } else {
                t("请先填写有效的 HTTP/HTTPS API 地址。", "Enter a valid HTTP/HTTPS endpoint first.")
            }
        }
    }

    private fun save(data: JsonObject) {
        val entry = draft.entry ?: return
        try {
            if (draft.custom) {
                ModelConfiguration.validateRoute(draft.route)
                for (item in data.getAsJsonArray("providers")) {
                    if (draft.route == s(item.asJsonObject, "provider")) throw IllegalArgumentException("ROUTE_TAKEN")
                }
            }
            val modelList = draft.modelList ?: JsonArray()
            ModelConfiguration.validateUrl(draft.endpoint, draft.custom)
            ModelConfiguration.validateModels(modelList.toString(), draft.custom)
            if (draft.key.isNotEmpty() && !draft.key.matches(KEY_PATTERN)) throw IllegalArgumentException("KEY")
            if (draft.custom && draft.protocol.isEmpty()) throw IllegalArgumentException("PROTOCOL")
            val deepseek = deepseek()
            val value = draft.value ?: JsonObject()
            val next = (draft.original ?: JsonObject()).deepCopy()
            draft.extra?.let { extra ->
                for (k in ArrayList(next.keySet())) if (k !in MANAGED) next.remove(k)
                extra.entrySet().forEach { next.add(it.key, it.value) }
            }
            if (!deepseek) {
                val headers = ModelConfiguration.headers(draft.headers ?: JsonArray())
                if (headers != ModelConfiguration.`object`(value.get("headers"))) next.add("headers", headers)
            }
            updateField(next, value, "baseURL", draft.endpoint)
            updateField(next, value, if (deepseek) "protocol" else "api", draft.protocol)
            if (!deepseek) updateField(next, value, "displayName", draft.name)
            if (draft.custom || (value.get("models") != modelList && (value.has("models") || !modelList.isEmpty))) {
                next.add("models", modelList.deepCopy())
            }
            var ref = s(value, "apiKeyEnv")
            if (draft.key.isNotEmpty()) {
                ref = ModelConfiguration.keyReference(draft.route)
                next.addProperty("apiKeyEnv", ref)
            }
            val path = if (draft.custom) ModelConfiguration.path("providers", draft.route) else entry.getAsJsonArray("settingsPath")
            var ops = ModelConfiguration.diff(path, draft.original ?: JsonObject(), next)
            if (draft.custom || (ops.isEmpty && !deepseek &&
                    ModelConfiguration.at(namespace(s(entry, "settingsNs")).get("value"), path).isJsonNull)
            ) {
                val op = JsonObject()
                op.addProperty("op", "set")
                op.add("path", path)
                op.add("value", next)
                ops = JsonArray().apply { add(op) }
            }
            repository.save(s(entry, "settingsNs"), ops, draft.revision, draft.generation, ref, draft.key)
        } catch (invalid: RuntimeException) {
            val code = invalid.message.toString()
            status = when {
                code.startsWith("HEADERS") -> t(
                    "请检查请求头：名称不能重复，值不能包含换行，不可覆盖连接与传输字段。",
                    "Check headers: unique names, no line breaks, and no connection or transport fields.",
                )
                code.startsWith("ROUTE") -> t(
                    "提供方标识需以小写字母开头，用短横线连接，且不能重复。",
                    "Provider IDs must start with a lowercase letter, use hyphens and be unique.",
                )
                code == "URL" -> t("请填写有效的 HTTP/HTTPS API 地址。", "Enter a valid HTTP/HTTPS endpoint.")
                code == "KEY" -> t("API Key 不应包含空格或换行。", "API keys must not contain whitespace.")
                code == "PROTOCOL" -> t("请选择连接协议。", "Select a protocol.")
                else -> t(
                    "请检查模型目录：ID 不能重复，容量必须是正整数。",
                    "Check models: unique IDs and positive integer capacities are required.",
                )
            }
        }
    }

    private fun updateField(next: JsonObject, value: JsonObject, key: String, text: String) {
        if (text != s(value, key)) setOptional(next, key, text)
    }

    private companion object {
        /** 连接、凭据与目录字段由主表单管理，高级 JSON 不得改写。 */
        val MANAGED = listOf("baseURL", "api", "protocol", "displayName", "apiKeyEnv", "models", "headers")
        val KEY_PATTERN = Regex("[\\x21-\\x7E]+")
        val SUBTITLE: String
            get() = t(
                "服务商、连接方式与模型目录。保存后与 Web UI 同步。",
                "Providers, connections and models. Saved settings sync with Web UI.",
            )

        fun setOptional(target: JsonObject, key: String, value: String) {
            if (value.isEmpty()) target.remove(key) else target.addProperty(key, value)
        }

        fun s(target: JsonObject, key: String): String = ModelConfiguration.text(target, key)
        fun t(zh: String, en: String): String = UiText.choose(zh, en)
    }
}