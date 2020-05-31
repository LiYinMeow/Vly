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
//渲染器抽象
abstract class VirtualRender(val context: Context, val uid: Long, val loadEnded: () -> Unit) {
    lateinit var activity: Activity
    abstract fun getPlaceMode(): PlaceMode //返回放置模式，墙面或者地板或者两者
    abstract fun getFloatingConfig(): FloatingConfig //显示什么按钮（使用 DSL）
    abstract fun getModelTransformConfig(): ModelTransformConfig //是否允许旋转或者缩放什么的（使用 DSL）
    abstract fun loadModel(func: (Renderable) -> Unit) //加载模型回调
    open fun getAnimationList(): Set<String> = emptySet() //获取动画列表
    open fun hasAnimation(animationName: String): Boolean = false //是否具有动画
    open fun playAnimation( //播放动画，如果 animationDuration 非空则使用这个时长代替原有时长
        animationName: String,
        animationDuration: Long?,
        repeatCount: Int?,
        doOnEnd: (() -> Unit)?
    ) = Unit

    open fun setModelToAnimationStartOrEnd() = Unit //在动画结束时重置为默认动作
    open fun stopLastAnimation() = Unit //停止动画
    open fun getFaceList(): Set<String> = emptySet() //获得面部表情列表
    open fun hasFace(faceId: String) = false //是否具有一个表情
    open fun setFace(faceId: String) = Unit //设置为表情
    open fun end() = Unit //停止渲染
    open fun onUpdate( //在每一帧更新
        arSceneView: ArSceneView,
        frameTime: FrameTime?,
        anchor: Anchor?,
        anchorNode: AnchorNode?,
        nodeTransformableNode: TransformableNode?,
        fakeShadow: TransformableNode?
    ) {
    }

    open fun getProp(prop: String, defaultValue: Any?): Any? = defaultValue //自定义值
    operator fun get(prop: String, defaultValue: Any?) =
        getProp(prop, defaultValue) //快速访问。覆盖 [] 操作符等于 getProp

    operator fun contains(animationName: String): Boolean =
        hasAnimation(animationName) //是否具有一个动画，覆盖 in 操作符等于 hasAnimation
}