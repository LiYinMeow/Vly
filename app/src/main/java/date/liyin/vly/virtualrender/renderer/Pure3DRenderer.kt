package date.liyin.vly.virtualrender.renderer

import android.content.Context
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.net.toUri
import com.google.ar.sceneform.animation.ModelAnimator
import com.google.ar.sceneform.assets.RenderableSource
import com.google.ar.sceneform.rendering.AnimationData
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import date.liyin.vly.INTERNAL
import date.liyin.vly.ktutils.FloatingConfig
import date.liyin.vly.ktutils.ModelTransformConfig
import date.liyin.vly.ktutils.floatConfig
import date.liyin.vly.ktutils.modelTransfromConfig
import date.liyin.vly.utils.AppDatabase
import date.liyin.vly.utils._3DModelSetting
import date.liyin.vly.virtualrender.PlaceMode
import date.liyin.vly.virtualrender.VirtualRender
import java.io.File
//3D 渲染器
class Pure3DRenderer : VirtualRender {
    private val animation = mutableMapOf<String, AnimationData>()
    private var lastPlayedAnimation: ModelAnimator? = null
    private lateinit var renderable: ModelRenderable
    private lateinit var modelSetting: _3DModelSetting
    private var path: File? = null

    constructor(context: Context, loadEnded: () -> Unit) : super(context, -1, loadEnded) {
        modelSetting = _3DModelSetting(
            uid = -1,
            type = "INTERNAL",
            modelName = "",
            useAnimation = false,
            animationFilter = emptySet(),
            baseAnimation = "",
            animationSpeed = mutableMapOf(),
            allowWalkAnimation = false,
            walkAnimation = "",
            walkActionSpeed = 1.0,
            walkAnimationSpeed = -1.0
        )
    }

    constructor(context: Context, uid: Long, loadEnded: () -> Unit) : super(
        context,
        uid,
        loadEnded
    ) {
        Thread {
            val modelDao = AppDatabase.getInstance(context)._3dModelDao()
            modelSetting = modelDao.loadAllModelWithUID(uid)!![0]!!
        }.let {
            it.start()
            it.join()
        }
    }

    constructor(context: Context, path: File, loadEnded: () -> Unit) : super(
        context,
        -1,
        loadEnded
    ) {
        this.path = path
        modelSetting = _3DModelSetting(
            uid = -1,
            type = "GLB",
            modelName = "",
            useAnimation = false,
            animationFilter = emptySet(),
            baseAnimation = "",
            animationSpeed = mutableMapOf(),
            allowWalkAnimation = false,
            walkAnimation = "",
            walkActionSpeed = 1.5,
            walkAnimationSpeed = 500.0
        )
    }

    override fun getPlaceMode(): PlaceMode = PlaceMode.HORIZONTAL_UPWARD_ONLY
    override fun getFloatingConfig(): FloatingConfig = floatConfig {
        MOVE() //允许移动
        if (modelSetting.useAnimation) ACTION() //视情况开启动画按钮
    }

    override fun getModelTransformConfig(): ModelTransformConfig = modelTransfromConfig {
        ROTATE() //允许手动旋转
    }

    fun getModelSetting() = modelSetting
    override fun loadModel(func: (Renderable) -> Unit) {
        ModelRenderable.builder().apply {
            if (modelSetting.type == "INTERNAL") { //内置模型特例
                this.setSource(
                    context,
                    INTERNAL.getInternalModel()!!
                )
            } else
                this.setSource(
                    context,
                    RenderableSource.builder().setSource(
                        context,
                        if (path != null) path!!.toUri() else File(
                            context.getExternalFilesDir(null),
                            "${modelSetting.uid}.glb"
                        ).toUri(),
                        RenderableSource.SourceType.GLB
                    )
                        .setRecenterMode(RenderableSource.RecenterMode.ROOT)
                        .build()
                )
        }.setRegistryId("GLB_ASSETS") //目前 GLB 直接渲染有问题，材质无法显示等待修改（bug）
            .build()
            .thenAccept { model ->
                model.isShadowCaster = true
                model.isShadowReceiver = true
                func(model)
                this.renderable = model
                animation.clear()
                (0 until model.animationDataCount).forEach {
                    val ani = model.getAnimationData(it)
                    if (ani.name !in modelSetting.animationFilter)
                        animation[ani.name] = ani
                }
                loadEnded()
            }
            .exceptionally {
                Exception(it).printStackTrace()
                null
            }
    }

    override fun getAnimationList(): Set<String> = animation.keys

    override fun hasAnimation(animationName: String): Boolean = animation.containsKey(animationName)

    override fun playAnimation(
        animationName: String,
        animationDuration: Long?,
        repeatCount: Int?,
        doOnEnd: (() -> Unit)?
    ) {
        val intentAnimation = ModelAnimator(animation[animationName], renderable)
        if (repeatCount != null) intentAnimation.repeatCount = repeatCount
        if (animationDuration != null) intentAnimation.duration = animationDuration
        intentAnimation.doOnStart {
            lastPlayedAnimation = intentAnimation
        }
        intentAnimation.doOnEnd {
            doOnEnd?.let { it1 -> it1() }
        }
        stopLastAnimation()
        intentAnimation.start()
    }

    override fun setModelToAnimationStartOrEnd() {
        stopLastAnimation()
        ModelAnimator(animation[modelSetting.baseAnimation], renderable).apply {
            this.doOnStart {
                lastPlayedAnimation = this
            }
            this.duration = 1
        }.start()
    }

    override fun stopLastAnimation() {
        if (lastPlayedAnimation != null) if (lastPlayedAnimation!!.isRunning) lastPlayedAnimation?.end()
    }

    override fun getProp(prop: String, defaultValue: Any?): Any? =
        when (prop) {
            "allowWalkAnimation" -> modelSetting.allowWalkAnimation
            "walkActionSpeed" -> modelSetting.walkActionSpeed
            "walkAnimationSpeed" -> modelSetting.walkAnimationSpeed
            "walkAnimation" -> modelSetting.walkAnimation
            "baseAnimation" -> modelSetting.baseAnimation
            "animationSpeed" -> modelSetting.animationSpeed
            else -> super.getProp(prop, defaultValue)
        }

}