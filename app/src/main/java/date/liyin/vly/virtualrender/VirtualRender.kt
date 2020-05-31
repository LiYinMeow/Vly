package date.liyin.vly.virtualrender

import android.app.Activity
import android.content.Context
import com.google.ar.core.Anchor
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.ux.TransformableNode
import date.liyin.vly.ktutils.FloatingConfig
import date.liyin.vly.ktutils.ModelTransformConfig

abstract class VirtualRender(val context: Context, val uid: Long, val loadEnded: () -> Unit) {
    lateinit var activity: Activity
    abstract fun getPlaceMode(): PlaceMode
    abstract fun getFloatingConfig(): FloatingConfig
    abstract fun getModelTransformConfig(): ModelTransformConfig
    abstract fun loadModel(func: (Renderable) -> Unit)
    open fun getAnimationList(): Set<String> = emptySet()
    open fun hasAnimation(animationName: String): Boolean = false
    open fun playAnimation(
        animationName: String,
        animationDuration: Long?,
        repeatCount: Int?,
        doOnEnd: (() -> Unit)?
    ) = Unit

    open fun setModelToAnimationStartOrEnd() = Unit
    open fun stopLastAnimation() = Unit
    open fun getFaceList(): Set<String> = emptySet()
    open fun hasFace(faceId: String) = false
    open fun setFace(faceId: String) = Unit
    open fun end() = Unit
    open fun onUpdate(
        arSceneView: ArSceneView,
        frameTime: FrameTime?,
        anchor: Anchor?,
        anchorNode: AnchorNode?,
        nodeTransformableNode: TransformableNode?,
        fakeShadow: TransformableNode?
    ) {
    }

    open fun getProp(prop: String, defaultValue: Any?): Any? = defaultValue
    operator fun get(prop: String, defaultValue: Any?) = getProp(prop, defaultValue)
    operator fun contains(animationName: String): Boolean = hasAnimation(animationName)
}