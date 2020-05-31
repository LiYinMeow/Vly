package date.liyin.vly.fragment

import android.Manifest
import android.animation.ObjectAnimator
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.*
import android.provider.MediaStore
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.setMargins
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.QuaternionEvaluator
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.math.Vector3Evaluator
import com.google.ar.sceneform.rendering.FixedWidthViewSizer
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.lzf.easyfloat.EasyFloat
import com.lzf.easyfloat.interfaces.OnInvokeView
import date.liyin.vly.MainActivity
import date.liyin.vly.R
import date.liyin.vly.utils.GlobalSetting
import date.liyin.vly.utils.IKeyPassthough
import date.liyin.vly.utils.VideoRecorder
import date.liyin.vly.virtualrender.AnimationRepeat
import date.liyin.vly.virtualrender.PlaceMode
import date.liyin.vly.virtualrender.VirtualRender
import date.liyin.vly.virtualrender.renderer.Live2DRenderer
import date.liyin.vly.virtualrender.renderer.NoneRender
import date.liyin.vly.virtualrender.renderer.Pure3DRenderer
import java.io.File
import java.io.IOException
import kotlin.math.sqrt

class ArRoomFragment : ArFragment(), IKeyPassthough {
    //    class ModelSetting {
//        companion object {
////            const val enableWalkAnimation = true //启用移动动作动画
////            const val walkDurationOnce : Double = 1.5 //移动动画步长（决定运动速度）
////            const val walkSpeedRaw : Long = 500 //移动动画时长（毫秒）
////            fun animationFilter(name: String) : Boolean = !name.contains(".001")
////            val walkAnimationName : String? = "Armature|PostTestAni" //移动动画名
////            val baseAnimationName : String? = "Armature|Leg" //基态名 运动结束会自动返回基态 如果为 null 则不返回基态
////            val animationDuration = mapOf<String, Long>(Pair("Armature|Leg", 2000)) //覆盖定义动画时长
//        }
//    }
    private lateinit var modelRenderable: Renderable
    private lateinit var fakeShadowRenderable: Renderable
    private var placeMode = true
    private var lastAnchor: Anchor? = null
    private var lastModelTransformable: TransformableNode? = null
    private lateinit var videoRecorder: VideoRecorder
    private val captureThread = HandlerThread("CaptureThread")
    private var lastAnchorNode: AnchorNode? = null
    private var fakeShadowTransformable: TransformableNode? = null
    private lateinit var waitingLayout: View
    private lateinit var fabLayout: FloatingActionButton
    private var moving = false

    private lateinit var virInstance: VirtualRender
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        virInstance = when (args.getString("type", "None")) {
            "Live2D" -> Live2DRenderer(requireContext(), args.getLong("uid"), loadEnded)
            "3D" -> Pure3DRenderer(requireContext(), args.getLong("uid"), loadEnded)
            else -> NoneRender(requireContext(), 0, loadEnded)
        }
        virInstance.activity = requireActivity()
        initCaptureSystem()
        injectLayout()
        loadFakeShadow()
        virInstance.loadModel {
            modelRenderable = it
        }
        setOnTapArPlaneListener { hitResult, plane, _ ->
            if (placeMode) {
                when (virInstance.getPlaceMode()) {
                    PlaceMode.HORIZONTAL_UPWARD_ONLY -> if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING) return@setOnTapArPlaneListener
                    PlaceMode.VERTICAL_ONLY -> if (plane.type != Plane.Type.VERTICAL) return@setOnTapArPlaneListener
                    else -> {
                    }
                }
                val hanchor = hitResult.createAnchor()
                lastAnchor = hanchor
                val anchorNode = AnchorNode(lastAnchor)
                anchorNode.setParent(arSceneView.scene)
                lastAnchorNode = anchorNode
                val modelTransformable = TransformableNode(transformationSystem)
                val config = virInstance.getModelTransformConfig()
                modelTransformable.translationController.isEnabled = config.translation
                modelTransformable.rotationController.isEnabled = config.rotate
                modelTransformable.scaleController.isEnabled = config.scale
                if (config.rotate) {
                    modelTransformable.scaleController.maxScale = config.scale_max
                    modelTransformable.scaleController.minScale = config.scale_min
                }
                if ((virInstance.getPlaceMode() == PlaceMode.VERTICAL_ONLY_ROTATE || virInstance.getPlaceMode() == PlaceMode.HORIZONTAL_UPWARD_AND_VERTICAL_ROTATE) && plane.type == Plane.Type.VERTICAL) {
                    modelTransformable.setLookDirection(Vector3.up())
                }
                modelTransformable.renderable = modelRenderable
                lastModelTransformable = modelTransformable
                if (config.fakeShadow) {
                    fakeShadowTransformable = TransformableNode(transformationSystem).apply {
                        this.translationController.isEnabled = false
                        this.scaleController.isEnabled = false
                        this.rotationController.isEnabled = false
                        this.renderable = fakeShadowRenderable
                        this.localRotation = Quaternion.axisAngle(Vector3(-1f, 0f, 0f), 90f)
                    }
                    modelTransformable.setParent(fakeShadowTransformable)
                    fakeShadowTransformable!!.setParent(anchorNode)
                } else {
                    modelTransformable.setParent(anchorNode)
                }
                transformationSystem.selectNode(null)
                placeMode = false
                EasyFloat.getFloatView()!!.findViewById<ToggleButton>(R.id.sw_floor)?.isChecked =
                    false
//                getDefaultFloating(this.activity).findViewById<ToggleButton>(R.id.sw_floor)?.isChecked = false
            } else {
                if (/*getDefaultFloating(this.activity)*/EasyFloat.getFloatView()!!
                        .findViewById<ToggleButton>(R.id.sw_walkmode)!!.isChecked
                ) {
                    if (moving) return@setOnTapArPlaneListener
                    if (virInstance["allowWalkAnimation", false] as Boolean) if (virInstance["walkAnimation", null] == null || (virInstance["walkAnimation", null] as String) !in virInstance || (virInstance["baseAnimation", null] != null && (virInstance["baseAnimation", null] as String) !in virInstance)) {
                        Toast.makeText(
                            this.context,
                            R.string.status_walk_notfound,
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnTapArPlaneListener
                    }
                    val destAnchor = hitResult.createAnchor()
                    val destAnchorNode = AnchorNode(destAnchor)
                    destAnchorNode.setParent(arSceneView.scene)
                    val shouldMove =
                        if (virInstance.getModelTransformConfig().fakeShadow) fakeShadowTransformable!! else lastModelTransformable!!
                    shouldMove.setParent(destAnchorNode)
                    shouldMove.worldPosition = lastAnchorNode!!.worldPosition
                    val direction = Vector3.subtract(
                        destAnchorNode.worldPosition,
                        lastAnchorNode!!.worldPosition
                    ).apply {
                        if (GlobalSetting.doDifferenceSufaceCorrection) this.y = 0f
                    }
                    val look = Quaternion.lookRotation(direction, Vector3.up())
                    ObjectAnimator().apply {
                        this.setObjectValues(
                            lastAnchorNode!!.localRotation,
                            look
                        )
                        this.setPropertyName("localRotation")
                        this.target = shouldMove
                        this.duration = 2000
                        this.setEvaluator(QuaternionEvaluator())
                        this.doOnStart {
                            moving = true
                            EasyFloat.getFloatView()!!.let {
                                it.findViewById<ToggleButton>(R.id.sw_walkmode)?.isEnabled = false
                                it.findViewById<Button>(R.id.btn_action)?.isEnabled = false
                                it.findViewById<Button>(R.id.btn_remove)?.isEnabled = false
                            }
                        }
                        this.doOnEnd {
                            val vstart = shouldMove.localPosition
                            val vend = Vector3.zero()
                            val distance =
                                sqrt(vstart.x * vstart.x + vstart.y * vstart.y + vstart.z * vstart.z)
                            ObjectAnimator().apply {
                                this.setObjectValues(vstart, vend)
                                this.setPropertyName("localPosition")
                                this.target = shouldMove
                                this.duration =
                                    (distance * virInstance["walkActionSpeed", 1.0] as Double * 1000).toLong()
                                this.setEvaluator(Vector3Evaluator())
                                this.doOnStart {
                                    if (virInstance["allowWalkAnimation", false] as Boolean) {
                                        virInstance.stopLastAnimation()
                                        virInstance.playAnimation(
                                            (virInstance["walkAnimation", ""] as String),
                                            if (virInstance["walkAnimationSpeed", (-1).toDouble()] as Double > 0) ((virInstance["walkAnimationSpeed", (-1).toDouble()] as Double) * 1000).toLong() else null,
                                            AnimationRepeat.INFINITE(),
                                            null
                                        )
                                    }
                                }
                                this.doOnEnd {
                                    if (virInstance["allowWalkAnimation", false] as Boolean) {
                                        virInstance.stopLastAnimation()
                                        virInstance.setModelToAnimationStartOrEnd()
                                    }
                                    EasyFloat.getFloatView()!!.let {
                                        it.findViewById<ToggleButton>(R.id.sw_walkmode)?.isEnabled =
                                            true
                                        it.findViewById<Button>(R.id.btn_action)?.isEnabled = true
                                        it.findViewById<Button>(R.id.btn_remove)?.isEnabled = true
                                    }
                                    moving = false
                                    lastAnchor!!.detach()
                                    lastAnchor = destAnchor
                                    lastAnchorNode = destAnchorNode
                                } // Stop Move Animation
                            }.start()
                        }
                    }.start()
                }
            }
        }
        arSceneView.planeRenderer.isShadowReceiver = true
    }

    private fun loadFakeShadow() {
        ViewRenderable.builder()
            .setSizer(FixedWidthViewSizer(0.5f))
            .setHorizontalAlignment(ViewRenderable.HorizontalAlignment.CENTER)
            .setVerticalAlignment(ViewRenderable.VerticalAlignment.CENTER)
            .setView(requireContext(), R.layout.ar_imageview)
            .build()
            .thenAccept {
                it.isShadowCaster = false
                it.isShadowReceiver = false
                it.view.findViewById<ImageView>(R.id.ar_iv).setImageResource(R.drawable.fakeshadow)
                fakeShadowRenderable = it
            }
    }

    private val loadEnded: () -> Unit = {
        requireActivity().runOnUiThread {
            setupFloating()
            waitingLayout.visibility = View.GONE
            fabLayout.show()
        }
    }

    private fun initCaptureSystem() {
        videoRecorder = VideoRecorder(requireActivity()).apply {
            val orientation = resources.configuration.orientation
            this.setVideoQuality(android.media.CamcorderProfile.QUALITY_2160P, orientation)
            this.setSceneView(arSceneView)
        }
        captureThread.start()
    }

    private fun injectLayout() {
        fabLayout = FloatingActionButton(requireContext()).apply {
            this.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                this.gravity = Gravity.END or Gravity.BOTTOM
                this.setMargins(context.resources.let {
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        16f,
                        it.displayMetrics
                    )
                }.toInt())
            }
            this.isClickable = true
            this.isFocusable = true
            this.setBackgroundResource(TypedValue().apply {
                this@ArRoomFragment.requireContext().theme!!.resolveAttribute(
                    android.R.attr.selectableItemBackground,
                    this,
                    true
                )
            }.resourceId)
            this.elevation = 8f
            this.setImageResource(R.drawable.ic_settings_black_24dp)
            this.setOnClickListener {
                if (EasyFloat.isShow(this@ArRoomFragment.requireActivity())!!) {
                    EasyFloat.hide(this@ArRoomFragment.requireActivity())
                } else {
                    EasyFloat.show(this@ArRoomFragment.requireActivity())
                }
            }
            this.hide()
        }
        (arSceneView.parent as FrameLayout).addView(fabLayout)
        waitingLayout = View.inflate(requireContext(), R.layout.layout_waiting, null).apply {
            this.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                this.gravity = Gravity.CENTER
            }
        }
        (arSceneView.parent as FrameLayout).addView(waitingLayout)
    }


    private fun setupFloating() {
        EasyFloat.with(this.requireActivity())
            .setLayout(R.layout.arcontroll_container, OnInvokeView {
                val swBroadcast = it.findViewById<ToggleButton>(R.id.sw_boardcast)
                val swFloor = it.findViewById<ToggleButton>(R.id.sw_floor)
                val btnFace = it.findViewById<Button>(R.id.btn_face)
                val btnAction = it.findViewById<Button>(R.id.btn_action)
                val btnRemove = it.findViewById<Button>(R.id.btn_remove)
                val swPermission = it.findViewById<ToggleButton>(R.id.sw_permission)
                val swAudio = it.findViewById<ToggleButton>(R.id.sw_audio)
                val swWalk = it.findViewById<ToggleButton>(R.id.sw_walkmode)
                swWalk.isChecked = GlobalSetting.walk
                if (this.requireActivity()
                        .checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                ) {
                    swAudio.isChecked = false
                } else {
                    swAudio.isChecked = GlobalSetting.walk
                }
                if (this.requireActivity()
                        .checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                ) {
                    swPermission.isChecked = true
                    swPermission.isEnabled = false
                }
                swPermission.setOnClickListener {
                    this.requireActivity().requestPermissions(
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        201
                    )
                }
                swWalk.setOnCheckedChangeListener { _, isChecked ->
                    GlobalSetting.walk = isChecked
                }
                swAudio.setOnCheckedChangeListener { _, isChecked ->
                    GlobalSetting.audio = isChecked
                }
                swAudio.setOnClickListener {
                    if (this.requireActivity()
                            .checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                    )
                        this.requireActivity()
                            .requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 202)
                }
                swFloor.setOnCheckedChangeListener { _, isChecked ->
                    setPlaneRender(isChecked)
                }
                btnRemove.setOnClickListener {
                    lastAnchor?.detach()
                    lastAnchor = null
                    lastAnchorNode = null
                    placeMode = true
                    swFloor.isChecked = true
                }
                btnFace.setOnLongClickListener {
                    val builder = AlertDialog.Builder(requireContext())
                    builder.setTitle(R.string.dialog_select_face)
                    builder.setItems(virInstance.getFaceList().toTypedArray()) { _, which ->
                        btnFace.text = virInstance.getFaceList().toTypedArray()[which]
                    }
                    builder.create().show()
                    true
                }
                btnFace.setOnClickListener {
                    if (btnFace.text == getString(R.string.btn_facial_expression)) {
                        Toast.makeText(
                            requireContext(),
                            R.string.status_face_notselect,
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }
                    if (lastAnchor == null) {
                        Toast.makeText(
                            requireContext(),
                            R.string.status_action_nomodel,
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }
                    if (virInstance.hasFace(btnFace.text.toString())) {
                        virInstance.setFace(btnFace.text.toString())
                    } else {
                        Toast.makeText(
                            requireContext(),
                            R.string.status_action_error,
                            Toast.LENGTH_SHORT
                        ).show()
                        btnFace.setText(R.string.btn_facial_expression)
                        return@setOnClickListener
                    }
                }
                btnAction.setOnLongClickListener {
                    val builder = AlertDialog.Builder(requireContext())
                    builder.setTitle(R.string.dialog_select_action)
                    builder.setItems(virInstance.getAnimationList().toTypedArray()) { _, which ->
                        btnAction.text = virInstance.getAnimationList().toTypedArray()[which]
                    }
                    builder.create().show()
                    true
                }
                btnAction.setOnClickListener {
                    if (btnAction.text == getString(R.string.btn_action)) {
                        Toast.makeText(
                            requireContext(),
                            R.string.status_action_notselect,
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }
                    if (lastAnchor == null) {
                        Toast.makeText(
                            requireContext(),
                            R.string.status_action_nomodel,
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }
                    if (virInstance.hasAnimation(btnAction.text.toString())) {
                        virInstance.stopLastAnimation()
                        val speed =
                            (virInstance["animationSpeed", mapOf<String, Long>()] as Map<*, *>)[btnAction.text]
                        virInstance.playAnimation(
                            btnAction.text.toString(),
                            if (speed != null) speed as Long else null,
                            null,
                            null
                        )
                    } else {
                        Toast.makeText(
                            requireContext(),
                            R.string.status_action_error,
                            Toast.LENGTH_SHORT
                        ).show()
                        btnAction.setText(R.string.btn_action)
                        return@setOnClickListener
                    }
                }
                var sync = true
                swBroadcast.isEnabled = false
                swBroadcast.setOnCheckedChangeListener { _, isChecked ->
                    if (!sync) {
                        sync = true
                        return@setOnCheckedChangeListener
                    }
                    val status = videoRecorder.onToggleRecord()
                    if (status != isChecked) {
                        sync = false
                        swBroadcast.isChecked = status
                    }
                    if (status) {
                        Toast.makeText(this.context, "Recording", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this.context, "Stop", Toast.LENGTH_SHORT).show()
                    }
                }
                virInstance.getFloatingConfig().let { config ->
                    if (config.move) {
                        swWalk.isChecked = true
                        swWalk.visibility = View.VISIBLE
                    } else {
                        swWalk.isChecked = false
                        swWalk.visibility = View.GONE
                    }
                    btnFace.visibility = if (config.face) View.VISIBLE else View.GONE
                    btnAction.visibility = if (config.action) View.VISIBLE else View.GONE
                }
            })
            .setAnimator(null)
            .setLocation(100, 100)
            .show()
        EasyFloat.hide(this.requireActivity())
    }

    override fun onUpdate(frameTime: FrameTime?) {
        super.onUpdate(frameTime)
        if (waitingLayout.visibility != View.VISIBLE) virInstance.onUpdate(
            arSceneView,
            frameTime,
            lastAnchor,
            lastAnchorNode,
            lastModelTransformable,
            fakeShadowTransformable
        )
        if (fakeShadowTransformable != null) {
            lastModelTransformable?.worldRotation = fakeShadowTransformable!!.worldRotation
            lastModelTransformable?.worldScale = fakeShadowTransformable!!.worldScale
            lastModelTransformable?.worldPosition = fakeShadowTransformable!!.worldPosition
        }
    }

    override fun getSessionConfiguration(session: Session?) = Config(session).apply {
        this.focusMode = Config.FocusMode.AUTO
        this.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        this.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
    }

    private fun setPlaneRender(planeRender: Boolean) {
        arSceneView.planeRenderer.isVisible = planeRender
    }

    override fun onResume() {
        super.onResume()
        if (this.requireActivity()
                .checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            EasyFloat.getFloatView()?.findViewById<ToggleButton>(R.id.sw_permission).let {
                it?.isEnabled = false
                it?.isChecked = true
            }
        } else {
            EasyFloat.getFloatView()?.findViewById<ToggleButton>(R.id.sw_permission).let {
                it?.isEnabled = true
                it?.isChecked = false
            }
        }
        if (this.requireActivity()
                .checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            EasyFloat.getFloatView()?.findViewById<ToggleButton>(R.id.sw_audio).let {
                it?.isChecked = false
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (!EasyFloat.getFloatView()!!
                .findViewById<ToggleButton>(R.id.sw_permission)!!.isChecked
        ) {
            return false
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                Toast.makeText(this.context, R.string.key_capture_start, Toast.LENGTH_SHORT).show()
                val bitmap = Bitmap.createBitmap(
                    arSceneView.width,
                    arSceneView.height,
                    Bitmap.Config.ARGB_8888
                )
                PixelCopy.request(arSceneView, bitmap, {
                    if (it == PixelCopy.SUCCESS) {
                        val contentValues = ContentValues()
                        contentValues.put(
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            "VSR_" + System.currentTimeMillis() + ".png"
                        )
                        contentValues.put(
                            MediaStore.MediaColumns.MIME_TYPE,
                            "image/png"
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.put(
                                MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_PICTURES + File.separator + "VSR"
                            )
                        }
                        val resolver = requireActivity().contentResolver
                        val contentUri =
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        val uri = resolver.insert(contentUri, contentValues)
                            ?: throw IOException("Failed to create new MediaStore record.")
                        try {
                            resolver.openOutputStream(uri)?.use { os ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                            }
                            Toast.makeText(this.context, R.string.key_captured, Toast.LENGTH_LONG)
                                .show()
                        } catch (e: Exception) {
                            resolver.delete(uri, null, null)
                            Toast.makeText(
                                this.context,
                                R.string.key_capture_failed,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(this.context, R.string.key_capture_failed, Toast.LENGTH_LONG)
                            .show()
                    }
                }, Handler(captureThread.looper))
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                toggleRecord(false)
                true
            }
            else -> false
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (requireActivity() as MainActivity).currentFragment = this
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!EasyFloat.getFloatView()!!
                .findViewById<ToggleButton>(R.id.sw_permission)!!.isChecked
        ) {
            return false
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                toggleRecord(true)
                true
            }
            else -> false
        }
    }

    private fun toggleRecord(keyState: Boolean) {
        if (videoRecorder.isRecording && keyState)
            return
        if (!videoRecorder.isRecording && !keyState)
            return
        when (videoRecorder.onToggleRecord()) {
            true -> {
                EasyFloat.getFloatView()!!.findViewById<ToggleButton>(R.id.sw_audio).let {
                    it?.isEnabled = false
                }
                Toast.makeText(this.context, R.string.key_record_start, Toast.LENGTH_SHORT).show()
            }
            false -> {
                EasyFloat.getFloatView()!!.findViewById<ToggleButton>(R.id.sw_audio).let {
                    it?.isEnabled = true
                }
                Toast.makeText(this.context, R.string.key_record_stop, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        when (requestCode) {
            201 -> {
                if (results[0] == PackageManager.PERMISSION_GRANTED) {
                    EasyFloat.getFloatView()!!.findViewById<ToggleButton>(R.id.sw_permission).let {
                        it?.isEnabled = false
                        it?.isChecked = true
                    }
                } else {
                    EasyFloat.getFloatView()!!.findViewById<ToggleButton>(R.id.sw_permission).let {
                        it?.isEnabled = true
                        it?.isChecked = false
                    }
                }
            }
            202 -> {
                if (results[0] != PackageManager.PERMISSION_GRANTED) {
                    EasyFloat.getFloatView()!!.findViewById<ToggleButton>(R.id.sw_audio).let {
                        it?.isChecked = false
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        virInstance.end()
        this.activity?.finish()
    }

}