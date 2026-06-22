package com.bambuprinterlan.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Model transform + slice settings, consumed by the native slicer. */
data class EditState(
    val scale: Float = 1f,
    val rotateZ: Float = 0f,
    val rotateX: Float = 0f,
    val rotateY: Float = 0f,
    val moveX: Float = 0f,
    val moveY: Float = 0f,
    val center: Boolean = true,
    val layerHeight: Float = 0.2f,
    val infill: Int = 15,
    val walls: Int = 2,
    val brim: Int = 0,
    val skirt: Int = 0,
    val infillPattern: Int = 0,   // 0 line, 1 grid, 2 triangles, 3 star, 4 concentric
    val seam: Int = 0,            // 0 nearest, 1 back, 2 front
    val innerWallsFirst: Boolean = false,
    val ironing: Boolean = false,
    val supportMode: Int = 0,    // 0 off, 1 everywhere, 2 plate-only
    val flowRatio: Float = 1.0f,
    val retractLength: Float = 0.8f,
    val zHop: Float = 0f,
    val nozzleTemp: Int = 220,
    val bedTemp: Int = 60,
)

/**
 * Holds the slice/transform settings. Persisted to SharedPreferences (covered by
 * Auto Backup) so tuned values survive app restarts and reinstalls.
 */
object ModelEditStore {
    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state.asStateFlow()

    private var prefs: SharedPreferences? = null

    /** Call once at startup to load persisted settings. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("slice_settings", Context.MODE_PRIVATE)
        prefs = p
        if (p.contains("layerHeight")) {
            _state.value = EditState(
                scale = p.getFloat("scale", 1f),
                rotateZ = p.getFloat("rotateZ", 0f),
                rotateX = p.getFloat("rotateX", 0f),
                rotateY = p.getFloat("rotateY", 0f),
                moveX = p.getFloat("moveX", 0f),
                moveY = p.getFloat("moveY", 0f),
                center = p.getBoolean("center", true),
                layerHeight = p.getFloat("layerHeight", 0.2f),
                infill = p.getInt("infill", 15),
                walls = p.getInt("walls", 2),
                brim = p.getInt("brim", 0),
                skirt = p.getInt("skirt", 0),
                infillPattern = p.getInt("infillPattern", 0),
                seam = p.getInt("seam", 0),
                innerWallsFirst = p.getBoolean("innerWallsFirst", false),
                ironing = p.getBoolean("ironing", false),
                supportMode = p.getInt("supportMode", 0),
                flowRatio = p.getFloat("flowRatio", 1f),
                retractLength = p.getFloat("retractLength", 0.8f),
                zHop = p.getFloat("zHop", 0f),
                nozzleTemp = p.getInt("nozzleTemp", 220),
                bedTemp = p.getInt("bedTemp", 60),
            )
        }
    }

    fun update(transform: (EditState) -> EditState) {
        val s = transform(_state.value)
        _state.value = s
        prefs?.edit()?.apply {
            putFloat("scale", s.scale); putFloat("rotateZ", s.rotateZ)
            putFloat("rotateX", s.rotateX); putFloat("rotateY", s.rotateY)
            putFloat("moveX", s.moveX); putFloat("moveY", s.moveY); putBoolean("center", s.center)
            putFloat("layerHeight", s.layerHeight); putInt("infill", s.infill); putInt("walls", s.walls)
            putInt("brim", s.brim); putInt("skirt", s.skirt); putInt("infillPattern", s.infillPattern)
            putInt("seam", s.seam); putBoolean("innerWallsFirst", s.innerWallsFirst)
            putBoolean("ironing", s.ironing); putInt("supportMode", s.supportMode)
            putFloat("flowRatio", s.flowRatio)
            putFloat("retractLength", s.retractLength); putFloat("zHop", s.zHop)
            putInt("nozzleTemp", s.nozzleTemp); putInt("bedTemp", s.bedTemp)
            apply()
        }
    }

    /** Build the `key = value` config the native engine reads. */
    fun configIni(): String = with(_state.value) {
        buildString {
            append("scale = ").append(scale).append('\n')
            append("rotate_z = ").append(rotateZ).append('\n')
            append("rotate_x = ").append(rotateX).append('\n')
            append("rotate_y = ").append(rotateY).append('\n')
            append("move_x = ").append(moveX).append('\n')
            append("move_y = ").append(moveY).append('\n')
            append("center = ").append(if (center) 1 else 0).append('\n')
            append("layer_height = ").append(layerHeight).append('\n')
            append("infill_density = ").append(infill).append('\n')
            append("infill_pattern = ").append(infillPattern).append('\n')
            append("seam_position = ").append(seam).append('\n')
            append("wall_order = ").append(if (innerWallsFirst) 1 else 0).append('\n')
            append("ironing = ").append(if (ironing) 1 else 0).append('\n')
            append("support = ").append(supportMode).append('\n')
            append("wall_loops = ").append(walls).append('\n')
            append("brim_loops = ").append(brim).append('\n')
            append("skirt_loops = ").append(skirt).append('\n')
            append("flow_ratio = ").append(flowRatio).append('\n')
            append("retract_length = ").append(retractLength).append('\n')
            append("z_hop = ").append(zHop).append('\n')
            append("nozzle_temp = ").append(nozzleTemp).append('\n')
            append("bed_temp = ").append(bedTemp).append('\n')
        }
    }
}
