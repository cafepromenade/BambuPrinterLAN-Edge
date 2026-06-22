package com.bambuprinterlan.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.bambuprinterlan.app.Mesh
import com.bambuprinterlan.app.MeshLoader
import com.bambuprinterlan.app.WorkspaceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bambuprinterlan.app.ModelEditStore
import com.bambuprinterlan.core.design.Bi
import com.bambuprinterlan.core.design.BiBody
import com.bambuprinterlan.core.design.BiText

/**
 * Model Edit Lab — transform (scale/rotate/move/center) and slice settings
 * (layer height, infill, walls, temps) fed to the native slicer. Bilingual.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelEditScreen(onBack: () -> Unit = {}) {
    val s by ModelEditStore.state.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back  返回") }
            BiText(Bi("Model Edit Lab", "模型編輯實驗室"),
                enSize = MaterialTheme.typography.headlineSmall.fontSize)
        }
        BiBody(Bi("Drag to orbit · pinch to zoom · applied when you slice.",
            "拖曳旋轉 · 兩指縮放 · 切片時套用。"))

        // 3D viewport of the imported model, reflecting the live transform.
        val ctx = LocalContext.current
        val model by WorkspaceStore.firstModel.collectAsState()
        val mesh by produceState<Mesh?>(initialValue = null, model) {
            val m = model
            value = if (m == null) null else withContext(Dispatchers.IO) {
                runCatching {
                    val ext = m.second.substringAfterLast('.', "")
                    ctx.contentResolver.openInputStream(Uri.parse(m.first))?.use { MeshLoader.load(it, ext) }
                }.getOrNull()
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(280.dp)) {
                val mm = mesh
                if (mm != null) Model3DView(mm, s.scale, s.rotateZ, s.rotateX, s.rotateY, Modifier.fillMaxSize())
                else BiBody(Bi("Import a model in Prepare to view it in 3D.",
                    "喺準備頁匯入模型即可 3D 預覽。"),
                    modifier = Modifier.align(Alignment.Center).padding(16.dp))
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BiText(Bi("Transform", "變形"))
                Labeled(Bi("Scale", "縮放"), "${(s.scale * 100).toInt()}%")
                Slider(s.scale, { v -> ModelEditStore.update { it.copy(scale = v) } }, valueRange = 0.25f..3f)
                Labeled(Bi("Rotate Z (flat)", "旋轉 Z（平面）"), "${s.rotateZ.toInt()}°")
                Slider(s.rotateZ, { v -> ModelEditStore.update { it.copy(rotateZ = v) } }, valueRange = 0f..360f)
                Labeled(Bi("Rotate X (tilt)", "旋轉 X（前後傾）"), "${s.rotateX.toInt()}°")
                Slider(s.rotateX, { v -> ModelEditStore.update { it.copy(rotateX = v) } }, valueRange = 0f..360f)
                Labeled(Bi("Rotate Y (tilt)", "旋轉 Y（左右傾）"), "${s.rotateY.toInt()}°")
                Slider(s.rotateY, { v -> ModelEditStore.update { it.copy(rotateY = v) } }, valueRange = 0f..360f)
                Labeled(Bi("Move X", "移動 X"), "${s.moveX.toInt()} mm")
                Slider(s.moveX, { v -> ModelEditStore.update { it.copy(moveX = v) } }, valueRange = -100f..100f)
                Labeled(Bi("Move Y", "移動 Y"), "${s.moveY.toInt()} mm")
                Slider(s.moveY, { v -> ModelEditStore.update { it.copy(moveY = v) } }, valueRange = -100f..100f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Bi("Center on plate", "置中於打印板").inline, modifier = Modifier.weight(1f))
                    Switch(s.center, { v -> ModelEditStore.update { it.copy(center = v) } })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { ModelEditStore.update {
                        it.copy(scale = 1f, rotateZ = 0f, rotateX = 0f, rotateY = 0f,
                            moveX = 0f, moveY = 0f, center = true)
                    } }) { Text(Bi("Reset", "重設").inline) }
                    val mm = mesh
                    OutlinedButton(enabled = mm != null, onClick = {
                        mm ?: return@OutlinedButton
                        val h = mm.maxZ - mm.minZ; val w = mm.width; val d = mm.depth
                        val smallest = minOf(w, d, h)
                        ModelEditStore.update {
                            when (smallest) {
                                h -> it.copy(rotateX = 0f, rotateY = 0f)   // already shortest up
                                w -> it.copy(rotateX = 0f, rotateY = 90f)  // lay X-axis down
                                else -> it.copy(rotateX = 90f, rotateY = 0f) // lay Y-axis down
                            }
                        }
                    }) { Text(Bi("Lay flat (auto)", "自動平放").inline) }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BiText(Bi("Slice settings", "切片設定"))
                Hint(Bi("Quick presets — or fine-tune below.", "快速預設 — 或喺下面微調。"))
                val presets = listOf(
                    Bi("Draft", "草稿") to Triple(0.28f, 10, 2),
                    Bi("Standard", "標準") to Triple(0.20f, 15, 2),
                    Bi("Quality", "精細") to Triple(0.12f, 15, 3),
                    Bi("Strong", "堅固") to Triple(0.20f, 40, 4),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { (label, vals) ->
                        val (lh, inf, wl) = vals
                        val selected = s.layerHeight == lh && s.infill == inf && s.walls == wl
                        FilterChip(selected = selected, onClick = {
                            ModelEditStore.update { it.copy(layerHeight = lh, infill = inf, walls = wl) }
                        }, label = { Text(label.inline) })
                    }
                }
                Labeled(Bi("Layer height", "層高"), "${s.layerHeight} mm")
                Hint(Bi("Thinner = smoother but slower. 0.2 mm is a good default.",
                    "越薄越平滑但越慢。0.2 毫米適合大多數情況。"))
                Slider(s.layerHeight, { v ->
                    ModelEditStore.update { it.copy(layerHeight = (Math.round(v * 100) / 100f)) }
                }, valueRange = 0.08f..0.32f)
                Labeled(Bi("Infill", "填充"), "${s.infill}%")
                Hint(Bi("How solid inside. 15% is fine for most prints; more = stronger & heavier.",
                    "內部實心程度。15% 適合大多數；越高越堅固越重。"))
                Slider(s.infill.toFloat(), { v -> ModelEditStore.update { it.copy(infill = v.toInt()) } },
                    valueRange = 0f..100f)
                Labeled(Bi("Infill pattern", "填充圖案"), "")
                Hint(Bi("Shape of the inside fill. Grid/Triangles are stronger.",
                    "內部填充嘅形狀。網格／三角形更堅固。"))
                val patterns = listOf(
                    Bi("Lines", "線條"), Bi("Grid", "網格"), Bi("Triangles", "三角"),
                    Bi("Star", "星形"), Bi("Concentric", "同心"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    patterns.forEachIndexed { i, p ->
                        FilterChip(selected = s.infillPattern == i,
                            onClick = { ModelEditStore.update { it.copy(infillPattern = i) } },
                            label = { Text(p.inline) })
                    }
                }
                Labeled(Bi("Walls", "牆數"), "${s.walls}")
                Hint(Bi("Number of outer shells. 2–3 is typical.", "外殼層數。一般 2–3 層。"))
                Slider(s.walls.toFloat(), { v -> ModelEditStore.update { it.copy(walls = v.toInt().coerceIn(1, 5)) } },
                    valueRange = 1f..5f)
                Labeled(Bi("Seam", "接縫"), "")
                Hint(Bi("Where each layer's start mark sits. Back hides it from view.",
                    "每層起點痕跡嘅位置。「後方」可避開視線。"))
                val seams = listOf(Bi("Nearest", "最近"), Bi("Back", "後方"), Bi("Front", "前方"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    seams.forEachIndexed { i, p ->
                        FilterChip(selected = s.seam == i,
                            onClick = { ModelEditStore.update { it.copy(seam = i) } },
                            label = { Text(p.inline) })
                    }
                }
                Text(Bi("Supports", "支撐").inline, style = MaterialTheme.typography.labelLarge)
                Hint(Bi("Auto props under overhangs. Plate-only avoids marks on the model.",
                    "懸空位自動支撐。「只限底板」唔會喺模型上留痕。"))
                val supportModes = listOf(Bi("Off", "關"), Bi("Everywhere", "全部"), Bi("Plate only", "只限底板"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    supportModes.forEachIndexed { i, p ->
                        FilterChip(selected = s.supportMode == i,
                            onClick = { ModelEditStore.update { it.copy(supportMode = i) } },
                            label = { Text(p.inline) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Bi("Ironing (smooth top)", "熨平（平滑頂面）").inline,
                            style = MaterialTheme.typography.labelLarge)
                        Hint(Bi("Extra fine pass over the top for a smoother finish.",
                            "頂面額外幼細掃一次，更平滑。"))
                    }
                    Switch(s.ironing, { v -> ModelEditStore.update { it.copy(ironing = v) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Bi("Inner walls first", "先打內牆").inline,
                            style = MaterialTheme.typography.labelLarge)
                        Hint(Bi("Print inner walls before the outer wall (better dimensions).",
                            "先打內牆再外牆（尺寸更準）。"))
                    }
                    Switch(s.innerWallsFirst, { v -> ModelEditStore.update { it.copy(innerWallsFirst = v) } })
                }
                Labeled(Bi("Brim", "邊緣"), "${s.brim}")
                Hint(Bi("Extra ring attached to the part to stop it lifting. 0 = off.",
                    "貼住部件嘅額外邊圈，防止翹起。0 = 關閉。"))
                Slider(s.brim.toFloat(), { v -> ModelEditStore.update { it.copy(brim = v.toInt().coerceIn(0, 10)) } },
                    valueRange = 0f..10f)
                Labeled(Bi("Skirt", "裙邊"), "${s.skirt}")
                Hint(Bi("A loop around the part to prime the nozzle. 0 = off.",
                    "圍住部件嘅一圈，用嚟順滑出料。0 = 關閉。"))
                Slider(s.skirt.toFloat(), { v -> ModelEditStore.update { it.copy(skirt = v.toInt().coerceIn(0, 5)) } },
                    valueRange = 0f..5f)
                Labeled(Bi("Flow ratio", "流量比"), "${(s.flowRatio * 100).toInt()}%")
                Hint(Bi("Fine-tune how much plastic comes out. Leave at 100% unless tuning.",
                    "微調出料份量。除非校正，否則保持 100%。"))
                Slider(s.flowRatio, { v -> ModelEditStore.update { it.copy(flowRatio = (Math.round(v * 100) / 100f)) } },
                    valueRange = 0.9f..1.1f)
                Labeled(Bi("Nozzle °C", "噴嘴 °C"), "${s.nozzleTemp}")
                Slider(s.nozzleTemp.toFloat(), { v -> ModelEditStore.update { it.copy(nozzleTemp = v.toInt()) } },
                    valueRange = 170f..300f)
                Labeled(Bi("Bed °C", "熱床 °C"), "${s.bedTemp}")
                Slider(s.bedTemp.toFloat(), { v -> ModelEditStore.update { it.copy(bedTemp = v.toInt()) } },
                    valueRange = 0f..120f)
            }
        }
    }
}

@Composable
private fun Hint(text: Bi) {
    Text(text.inline, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Labeled(label: Bi, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label.inline, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}
