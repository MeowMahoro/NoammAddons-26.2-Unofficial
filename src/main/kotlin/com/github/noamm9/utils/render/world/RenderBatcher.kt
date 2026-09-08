package com.github.noamm9.utils.render.world

import com.github.noamm9.utils.render.world.batches.FilledBatch
import com.github.noamm9.utils.render.world.batches.LineBatch
import com.github.noamm9.utils.render.world.batches.TextRenderState
import com.github.noamm9.utils.render.world.feature.NoammFeatureRenderers
import gg.essential.universal.UGraphics
import gg.essential.universal.render.URenderPipeline
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import org.joml.Matrix4f
import org.joml.Vector3f

object RenderBatcher {
    private val filledBatches = mutableMapOf<URenderPipeline, FilledBatch>()
    private val lineBatches = mutableMapOf<URenderPipeline, LineBatch>()
    private val texts = ArrayList<TextRenderState>()

    val tmpVec = Vector3f()
    val tmpDir = Vector3f()

    fun filledBatch(phase: Boolean) = filledBatch(if (phase) NoammRenderPipelines.FILLED_THROUGH_WALLS else NoammRenderPipelines.FILLED, UGraphics.DrawMode.TRIANGLES)
    fun circleBatch(phase: Boolean) = filledBatch(if (phase) NoammRenderPipelines.CIRCLE_FILLED_THROUGH_WALLS else NoammRenderPipelines.CIRCLE_FILLED, UGraphics.DrawMode.TRIANGLE_STRIP)
    fun lineBatch(phase: Boolean): LineBatch {
        val pipeline = if (phase) NoammRenderPipelines.LINES_THROUGH_WALLS else NoammRenderPipelines.LINES
        return lineBatches.getOrPut(pipeline) { LineBatch(pipeline) }
    }

    internal fun addText(matrix: Matrix4f, text: String, xOff: Float, yOff: Float, argb: Int, seeThrough: Boolean) {
        texts.add(TextRenderState(Matrix4f(matrix), text, xOff, yOff, argb, seeThrough))
    }

    /**
     * Called at the end of level submit collection. Data was recorded during [RenderWorldEvent]
     * dispatch; we hand pending geometry to the feature-renderer pipeline here.
     *
     * TODO(26.2): filled geometry + world text still need their own feature renderers (vanilla
     * exposes POSITION_COLOR QUADS/TRIANGLE_FAN pipelines and TEXT pipelines, but vertex layout is
     * quad-based, so fills require re-batching to 4-vertex quads first).
     */
    internal fun flush(ctx: LevelRenderContext) {
        if (filledBatches.isEmpty() && lineBatches.isEmpty() && texts.isEmpty()) return

        val pendingFills = filledBatches.values.toList().also { filledBatches.clear() }
        if (pendingFills.isNotEmpty()) {
            val submits = pendingFills.mapNotNull { batchData ->
                val throughWalls = when (batchData.pipeline) {
                    NoammRenderPipelines.FILLED,
                    NoammRenderPipelines.CIRCLE_FILLED -> false
                    NoammRenderPipelines.FILLED_THROUGH_WALLS,
                    NoammRenderPipelines.CIRCLE_FILLED_THROUGH_WALLS -> true
                    else -> null
                } ?: return@mapNotNull null
                throughWalls to batchData.data
            }
            NoammFeatureRenderers.submitFills(ctx, submits)
        }

        val pendingTexts = texts.toList().also { texts.clear() }
        if (pendingTexts.isNotEmpty()) NoammFeatureRenderers.submitTexts(ctx, pendingTexts)

        val pendingLines = lineBatches.values.toList().also { lineBatches.clear() }
        if (pendingLines.isNotEmpty()) {
            val submits = pendingLines.map { batchData ->
                val throughWalls = batchData.pipeline == NoammRenderPipelines.LINES_THROUGH_WALLS
                throughWalls to batchData.data
            }
            NoammFeatureRenderers.submitLines(ctx, submits)
        }
    }

    private fun filledBatch(pipeline: URenderPipeline, mode: UGraphics.DrawMode) = filledBatches.getOrPut(pipeline) { FilledBatch(pipeline, mode) }
}
