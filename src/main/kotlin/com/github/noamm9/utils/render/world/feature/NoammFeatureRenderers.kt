package com.github.noamm9.utils.render.world.feature

import com.github.noamm9.utils.render.world.batches.FilledBatch
import com.github.noamm9.utils.render.world.batches.LineBatch
import com.github.noamm9.utils.render.world.batches.TextRenderState
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.FabricOrderedSubmitNodeCollector
import net.fabricmc.fabric.api.client.rendering.v1.FeatureRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhases
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.font.TextRenderable
import net.minecraft.client.renderer.feature.FeatureFrameContext
import net.minecraft.client.renderer.feature.FeatureRendererType
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer
import net.minecraft.client.renderer.feature.submit.SubmitNode
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.util.LightCoordsUtil
import org.joml.Matrix4f
import org.joml.Matrix4fc

/** A single submitted batch of world-space lines (vertices are camera-relative). */
class NoammLineSubmit(
    val throughWalls: Boolean,
    val vertices: List<LineBatch.LineRenderState>
): SubmitNode {
    override fun featureType(): FeatureRendererType<out SubmitNode> = NoammFeatureRenderers.LINE_TYPE
}

/** A single submitted batch of world-space filled quads (vertices are camera-relative). */
class NoammFillSubmit(
    val throughWalls: Boolean,
    val vertices: List<FilledBatch.FilledRenderState>
): SubmitNode {
    override fun featureType(): FeatureRendererType<out SubmitNode> = NoammFeatureRenderers.FILL_TYPE
}

/** One world-space text line (pose matrix already applies camera offset / orientation / scale). */
class NoammTextSubmit(
    val pose: Matrix4f,
    val x: Float,
    val y: Float,
    val text: String,
    val color: Int,
    val seeThrough: Boolean
): SubmitNode {
    override fun featureType(): FeatureRendererType<out SubmitNode> = NoammFeatureRenderers.TEXT_TYPE
}

/**
 * Renders submitted geometry through vanilla's public render types.
 *
 * Vanilla 26.2 removed immediate-mode world drawing. The only supported way for mods to draw
 * arbitrary world geometry is to implement [net.minecraft.client.renderer.feature.FeatureRenderer]
 * and submit nodes into the level's collector. We extend the official [RenderTypeFeatureRenderer]
 * which already handles vertex staging + draw execution, so we only build vertices here.
 */
class NoammLineRenderer: RenderTypeFeatureRenderer<NoammLineSubmit>() {
    override fun buildGroup(context: FeatureFrameContext, submits: List<NoammLineSubmit>) {
        for (node in submits) {
            val builder: VertexConsumer = getVertexBuilder(if (node.throughWalls) RenderTypes.linesTranslucent() else RenderTypes.lines())
            for (v in node.vertices) {
                builder.addVertex(v.x.toFloat(), v.y.toFloat(), v.z.toFloat())
                builder.setColor((v.r * 255f).toInt(), (v.g * 255f).toInt(), (v.b * 255f).toInt(), (v.a * 255f).toInt())
                builder.setNormal(v.nx, v.ny, v.nz)
                builder.setLineWidth(v.lineWidth)
            }
        }
    }
}

class NoammFillRenderer: RenderTypeFeatureRenderer<NoammFillSubmit>() {
    override fun buildGroup(context: FeatureFrameContext, submits: List<NoammFillSubmit>) {
        for (node in submits) {
            val builder: VertexConsumer = getVertexBuilder(RenderTypes.debugFilledBox())
            for (v in node.vertices) {
                builder.addVertex(v.x.toFloat(), v.y.toFloat(), v.z.toFloat())
                builder.setColor((v.r * 255f).toInt(), (v.g * 255f).toInt(), (v.b * 255f).toInt(), (v.a * 255f).toInt())
            }
        }
    }
}

class NoammTextRenderer: RenderTypeFeatureRenderer<NoammTextSubmit>() {
    private inner class GlyphVisitorImpl: Font.GlyphVisitor {
        private val pose = Matrix4f()
        private var lightCoords = LightCoordsUtil.FULL_BRIGHT
        private var displayMode = Font.DisplayMode.NORMAL

        fun prepare(submit: NoammTextSubmit) {
            pose.set(submit.pose)
            displayMode = if (submit.seeThrough) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL
        }

        override fun acceptRenderable(renderable: TextRenderable) {
            val builder = getVertexBuilder(renderable.renderType(displayMode))
            renderable.render(pose, builder, lightCoords, false)
        }
    }

    override fun buildGroup(context: FeatureFrameContext, submits: List<NoammTextSubmit>) {
        val glyph = GlyphVisitorImpl()
        for (node in submits) {
            glyph.prepare(node)
            // The String overload parses legacy '§' color codes, matching the old drawInBatch usage.
            context.font().prepareText(node.text, node.x, node.y, node.color, true, 0).visit(glyph)
        }
    }
}

object NoammFeatureRenderers {
    val LINE_TYPE: FeatureRendererType<NoammLineSubmit> = FeatureRendererType.create("noammaddons_lines")
    val FILL_TYPE: FeatureRendererType<NoammFillSubmit> = FeatureRendererType.create("noammaddons_fills")
    val TEXT_TYPE: FeatureRendererType<NoammTextSubmit> = FeatureRendererType.create("noammaddons_text")

    /** Must be called during client init. */
    fun register() {
        FeatureRendererRegistry.register(LINE_TYPE) { NoammLineRenderer() }
        FeatureRendererRegistry.register(FILL_TYPE) { NoammFillRenderer() }
        FeatureRendererRegistry.register(TEXT_TYPE) { NoammTextRenderer() }
    }

    /** Submits all pending line batches into the current level render frame. */
    fun submitLines(context: LevelRenderContext, submits: List<Pair<Boolean, List<LineBatch.LineRenderState>>>) {
        if (submits.isEmpty()) return
        val collector = context.submitNodeCollector() as FabricOrderedSubmitNodeCollector
        for ((throughWalls, vertices) in submits) {
            collector.submitCustom(
                if (throughWalls) SubmitRenderPhases.ALWAYS_ON_TOP else SubmitRenderPhases.AFTER_TERRAIN,
                NoammLineSubmit(throughWalls, vertices)
            )
        }
    }

    /** Submits all pending filled quad batches into the current level render frame. */
    fun submitFills(context: LevelRenderContext, submits: List<Pair<Boolean, List<FilledBatch.FilledRenderState>>>) {
        if (submits.isEmpty()) return
        val collector = context.submitNodeCollector() as FabricOrderedSubmitNodeCollector
        for ((throughWalls, vertices) in submits) {
            collector.submitCustom(
                if (throughWalls) SubmitRenderPhases.ALWAYS_ON_TOP else SubmitRenderPhases.AFTER_TERRAIN,
                NoammFillSubmit(throughWalls, vertices)
            )
        }
    }

    /** Submits pending world text lines into the current level render frame. */
    fun submitTexts(context: LevelRenderContext, submits: List<TextRenderState>) {
        if (submits.isEmpty()) return
        val collector = context.submitNodeCollector() as FabricOrderedSubmitNodeCollector
        // Texts have to blend on top of the world; ALWAYS_ON_TOP keeps them readable.
        for (state in submits) {
            collector.submitCustom(
                SubmitRenderPhases.ALWAYS_ON_TOP,
                NoammTextSubmit(Matrix4f(state.matrix), state.xOff, state.yOff, state.text, state.argb, state.seeThrough)
            )
        }
    }
}
