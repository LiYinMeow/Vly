package date.liyin.vly.virtualrender.renderer

import android.content.Context
import android.graphics.SurfaceTexture
import android.widget.ImageView
import com.google.ar.core.Anchor
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.FixedHeightViewSizer
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.TransformableNode
import date.liyin.vly.R
import date.liyin.vly.ktutils.FloatingConfig
import date.liyin.vly.ktutils.ModelTransformConfig
import date.liyin.vly.ktutils.floatConfig
import date.liyin.vly.ktutils.modelTransfromConfig
import date.liyin.vly.live2d.JniBridgeJava
import date.liyin.vly.live2d.L2DRenderer
import date.liyin.vly.utils.AppDatabase
import date.liyin.vly.utils._L2DModelSetting
import date.liyin.vly.virtualrender.PlaceMode
import date.liyin.vly.virtualrender.VirtualRender
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

class Live2DRenderer(context: Context, uid: Long, loadEnded: () -> Unit) :
    VirtualRender(context, uid, loadEnded), JniBridgeJava.FileGetter {
    private lateinit var renderable: ViewRenderable
    private val mRenderer = L2DRenderer()
    private lateinit var animation: Set<String>
    private lateinit var facial: Set<String>
    private lateinit var zipfile: ZipFile
    private lateinit var modelSetting: _L2DModelSetting
    private val texture = SurfaceTexture(0)

    init {
        Thread {
            val modelDao = AppDatabase.getInstance(context)._l2dModelDao()
            modelSetting = modelDao.loadAllModelWithUID(uid)!![0]!!
            zipfile = ZipFile(File(context.getExternalFilesDir(null), "${modelSetting.uid}.zip"))
            texture.setDefaultBufferSize(modelSetting.canvasWidth, modelSetting.canvasHeight)
            JniBridgeJava.nativeSetModelJSONName(
                modelSetting.modelJsonName.replace(
                    ".model3.json",
                    ""
                )
            )
            JniBridgeJava.nativeSetModelX(modelSetting.x)
            JniBridgeJava.nativeSetModelX(modelSetting.y)
            JniBridgeJava.nativeSetModelScale(modelSetting.scale)
            JniBridgeJava.setFileGetter(this)
        }.let {
            it.start()
            it.join()
        }
    }

    override fun requestFile(path: String?): ByteArray {
        return zipfile.getInputStream(zipfile.getEntry(path)).readBytes()
    }

    override fun getPlaceMode(): PlaceMode = PlaceMode.HORIZONTAL_UPWARD_AND_VERTICAL_DIRECT
    override fun getFloatingConfig(): FloatingConfig = floatConfig {
        FACE()
        ACTION()
    }

    override fun getModelTransformConfig(): ModelTransformConfig = modelTransfromConfig {
        FAKESHADOW()
    }

    @ExperimentalStdlibApi
    override fun loadModel(func: (Renderable) -> Unit) {
        ViewRenderable.builder()
            .setView(context, R.layout.ar_imageview)
            .setVerticalAlignment(ViewRenderable.VerticalAlignment.BOTTOM)
            .setHorizontalAlignment(ViewRenderable.HorizontalAlignment.CENTER)
            .setSizer(FixedHeightViewSizer(modelSetting.height))
            .build()
            .thenAccept { model ->
                model.isShadowCaster = false
                model.isShadowReceiver = false
                func(model)
                this.renderable = model
                mRenderer.setActivity(activity)
                mRenderer.setSurfaceTextureSize(modelSetting.canvasWidth, modelSetting.canvasHeight)
                mRenderer.setOnLoaded {
                    val l2name = JniBridgeJava.nativeGetL2DName()
                    val jsonr = requestFile("$l2name.model3.json").decodeToString()
                    val json = JSONObject(jsonr)
                    val jfr = json.getJSONObject("FileReferences")
                    if (jfr.has("Motions")) {
                        val ani = mutableSetOf<String>()
                        jfr.getJSONObject("Motions").keys().forEach {
                            ani.add(it)
                        }
                        animation = ani.toSet()
                    } else {
                        animation = emptySet()
                    }
//                    animation = JniBridgeJava.nativeGetMotionList().toSet()
                    facial = JniBridgeJava.nativeGetFaceList().toSet()
                    loadEnded()
                }
                JniBridgeJava.nativeSetConfigMode(false)
                mRenderer.start()
                mRenderer.setSurfaceTexture(texture)
            }
            .exceptionally {
                Exception(it).printStackTrace()
                null
            }
    }

    override fun getAnimationList(): Set<String> = animation

    override fun hasAnimation(animationName: String): Boolean = animation.contains(animationName)

    override fun playAnimation(
        animationName: String,
        animationDuration: Long?,
        repeatCount: Int?,
        doOnEnd: (() -> Unit)?
    ) {
        JniBridgeJava.nativePlayAnimation(animationName)
    }

    override fun setModelToAnimationStartOrEnd() {}

    override fun stopLastAnimation() {
        JniBridgeJava.nativeStopAnimation()
    }

    override fun onUpdate(
        arSceneView: ArSceneView,
        frameTime: FrameTime?,
        anchor: Anchor?,
        anchorNode: AnchorNode?,
        nodeTransformableNode: TransformableNode?,
        fakeShadow: TransformableNode?
    ) {
        if (anchor != null && anchorNode != null && fakeShadow != null) {
            val cameraPosition = arSceneView.scene.camera.worldPosition.apply { this.y = 0f }
            val cardPosition = fakeShadow.worldPosition.apply { this.y = 0f }
            val direction = Vector3.subtract(cameraPosition, cardPosition)
            val lookRotation = Quaternion.lookRotation(direction, Vector3.up())
            fakeShadow.worldRotation = lookRotation
            if (mRenderer.lastFrame != null)
                renderable.view.findViewById<ImageView>(R.id.ar_iv)
                    .setImageBitmap(mRenderer.lastFrame)
        }
    }

    override fun end() {
        mRenderer.halt()
    }

    override fun getFaceList(): Set<String> = facial

    override fun hasFace(faceId: String): Boolean = facial.contains(faceId)

    override fun setFace(faceId: String) {
        JniBridgeJava.nativeSetFace(faceId)
    }

}