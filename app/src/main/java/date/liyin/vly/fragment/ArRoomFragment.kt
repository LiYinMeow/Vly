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
import date.liyin.vly.virtualrender.renderer.NoneRenderer
import date.liyin.vly.virtualrender.renderer.Pure3DRenderer
import java.io.File
import java.io.IOException
import kotlin.math.sqrt

//AR 场景
class ArRoomFragment : ArFragment(), IKeyPassthough {
    private lateinit var modelRenderable: Renderable //模型
    private lateinit var fakeShadowRenderable: Renderable //虚拟影子
    private var placeMode = true //是否处于放置模式
    private var lastAnchor: Anchor? = null //最后一个操作的定标
    private var lastModelTransformable: TransformableNode? = null //最后一个操作的模型变换
    private lateinit var videoRecorder: VideoRecorder //录制库
    private val captureThread = HandlerThread("CaptureThread") //录制线程
    private var lastAnchorNode: AnchorNode? = null //最后一个操作的定标节点
    private var fakeShadowTransformable: TransformableNode? = null //虚拟影子变化节点
    private lateinit var waitingLayout: View //等待画面
    private lateinit var fabLayout: FloatingActionButton //右下角设置按钮
    private var moving = false //是否正在处于移动模式

    private lateinit var virInstance: VirtualRender //渲染器
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        virInstance = when (args.getString("type", "None")) { //按照启动模式来初始化渲染器
            "Live2D" -> Live2DRenderer(requireContext(), args.getLong("uid"), loadEnded)
            "3D" -> Pure3DRenderer(requireContext(), args.getLong("uid"), loadEnded)
            else -> NoneRenderer(requireContext(), 0, loadEnded)
        }
        virInstance.activity = requireActivity()
        initCaptureSystem() //初始化录制
        injectLayout() //注入等待界面
        loadFakeShadow() //初始化虚拟影子
        virInstance.loadModel {//设置在 loadModel 时将渲染器的模型进行接管
            modelRenderable = it
        }
        setOnTapArPlaneListener { hitResult, plane, _ -> //当选中了某个平面
            if (placeMode) { //是否处于放置模式，在放置模式则放置模型，否则处理移动
                when (virInstance.getPlaceMode()) { //获得此渲染器支持的放置模式
                    PlaceMode.HORIZONTAL_UPWARD_ONLY -> if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING) return@setOnTapArPlaneListener
                    PlaceMode.VERTICAL_ONLY -> if (plane.type != Plane.Type.VERTICAL) return@setOnTapArPlaneListener
                    else -> {
                    }
                }
                val hanchor = hitResult.createAnchor() //创建定标
                lastAnchor = hanchor
                val anchorNode = AnchorNode(lastAnchor)
                anchorNode.setParent(arSceneView.scene)
                lastAnchorNode = anchorNode
                val modelTransformable = TransformableNode(transformationSystem)
                val config = virInstance.getModelTransformConfig() //获得渲染器变换设置
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
                if (config.fakeShadow) { //开启虚拟影子 通过在模型和原始定标之间添加一层影子节点定标
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
            } else { //处理移动
                if (EasyFloat.getFloatView()!!
                        .findViewById<ToggleButton>(R.id.sw_walkmode)!!.isChecked //是否处于允许移动模式
                ) {
                    if (moving) return@setOnTapArPlaneListener //如果在移动则跳过
                    if (virInstance["allowWalkAnimation", false] as Boolean) if (virInstance["walkAnimation", null] == null || (virInstance["walkAnimation", null] as String) !in virInstance || (virInstance["baseAnimation", null] != null && (virInstance["baseAnimation", null] as String) !in virInstance)) {
                        //检查渲染器设置是否配置了移动选项
                        Toast.makeText(
                            this.context,
                            R.string.status_walk_notfound,
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnTapArPlaneListener
                    }
                    val destAnchor = hitResult.createAnchor() //目标位置
                    val destAnchorNode = AnchorNode(destAnchor)
                    destAnchorNode.setParent(arSceneView.scene)
                    val shouldMove =
                        if (virInstance.getModelTransformConfig().fakeShadow) fakeShadowTransformable!! else lastModelTransformable!!
                    shouldMove.setParent(destAnchorNode)
                    shouldMove.worldPosition = lastAnchorNode!!.worldPosition
                    //运算移动信息（注意有旋转 bug，待解决）
                    val direction = Vector3.subtract(
                        destAnchorNode.worldPosition,
                        lastAnchorNode!!.worldPosition
                    ).apply {
                        if (GlobalSetting.doDifferenceSufaceCorrection) this.y = 0f
                    }
                    val look = Quaternion.lookRotation(direction, Vector3.up())
                    ObjectAnimator().apply { //开始移动动画
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
                                        virInstance.playAnimation( //播放移动步行动画
                                            (virInstance["walkAnimation", ""] as String),
                                            if (virInstance["walkAnimationSpeed", (-1).toDouble()] as Double > 0) ((virInstance["walkAnimationSpeed", (-1).toDouble()] as Double) * 1000).toLong() else null,
                                            AnimationRepeat.INFINITE(),
                                            null
                                        )
                                    }
                                }
                                this.doOnEnd { //结束时配置
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

    //加载虚拟影子
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

    //结束加载回调
    private val loadEnded: () -> Unit = {
        requireActivity().runOnUiThread {
            setupFloating() //设置悬浮选择窗口
            waitingLayout.visibility = View.GONE //移除等待界面
            fabLayout.show()
        }
    }

    //初始化录制系统
    private fun initCaptureSystem() {
        videoRecorder = VideoRecorder(requireActivity()).apply {
            val orientation = resources.configuration.orientation
            this.setVideoQuality(android.media.CamcorderProfile.QUALITY_2160P, orientation)
            this.setSceneView(arSceneView)
        }
        captureThread.start()
    }

    //注入等待界面和设置按钮
    private fun injectLayout() {
        fabLayout = FloatingActionButton(requireContext()).apply { //设置按钮
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
        waitingLayout = View.inflate(requireContext(), R.layout.layout_waiting, null).apply { //等待界面
            this.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                this.gravity = Gravity.CENTER
            }
        }
        (arSceneView.parent as FrameLayout).addView(waitingLayout)
    }

    //设置悬浮设置窗口
    private fun setupFloating() {
        EasyFloat.with(this.requireActivity())
            .setLayout(R.layout.arcontroll_container, OnInvokeView {
                val swBroadcast =
                    it.findViewById<ToggleButton>(R.id.sw_boardcast) //推流按钮（目前无法解决 RTMP 的问题，已禁用）
                val swFloor = it.findViewById<ToggleButton>(R.id.sw_floor) //显示地板
                val btnFace = it.findViewById<Button>(R.id.btn_face) //面部表情控制
                val btnAction = it.findViewById<Button>(R.id.btn_action) //动作控制
                val btnRemove = it.findViewById<Button>(R.id.btn_remove) //移除已放置的模型
                val swPermission = it.findViewById<ToggleButton>(R.id.sw_permission) //设置录制权限
                val swAudio = it.findViewById<ToggleButton>(R.id.sw_audio) //音频录制开关
                val swWalk = it.findViewById<ToggleButton>(R.id.sw_walkmode) //移动开关
                swWalk.isChecked = GlobalSetting.walk
                if (this.requireActivity()
                        .checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED //检查音频权限
                ) {
                    swAudio.isChecked = false
                } else {
                    swAudio.isChecked = GlobalSetting.walk
                }
                if (this.requireActivity()
                        .checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED //检查外置存储权限
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
                btnRemove.setOnClickListener { //重置
                    lastAnchor?.detach()
                    lastAnchor = null
                    lastAnchorNode = null
                    placeMode = true
                    swFloor.isChecked = true
                }
                btnFace.setOnLongClickListener { //长按时弹出选择界面
                    val builder = AlertDialog.Builder(requireContext())
                    builder.setTitle(R.string.dialog_select_face)
                    builder.setItems(virInstance.getFaceList().toTypedArray()) { _, which ->
                        btnFace.text = virInstance.getFaceList().toTypedArray()[which]
                    }
                    builder.create().show()
                    true
                }
                btnFace.setOnClickListener { //调用渲染器对应功能
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
                btnAction.setOnLongClickListener { //显示动作列表
                    val builder = AlertDialog.Builder(requireContext())
                    builder.setTitle(R.string.dialog_select_action)
                    builder.setItems(virInstance.getAnimationList().toTypedArray()) { _, which ->
                        btnAction.text = virInstance.getAnimationList().toTypedArray()[which]
                    }
                    builder.create().show()
                    true
                }
                btnAction.setOnClickListener { //调用渲染器动作
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
                        virInstance.playAnimation( //播放动画
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
                swBroadcast.isEnabled = false //推流 目前不可用
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
        ) //支持透传 update 给当前渲染器
        if (fakeShadowTransformable != null) {
            lastModelTransformable?.worldRotation = fakeShadowTransformable!!.worldRotation
            lastModelTransformable?.worldScale = fakeShadowTransformable!!.worldScale
            lastModelTransformable?.worldPosition = fakeShadowTransformable!!.worldPosition
        }
    }

    //设定 AR 相机
    override fun getSessionConfiguration(session: Session?) = Config(session).apply {
        this.focusMode = Config.FocusMode.AUTO //自动对焦
        this.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        this.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR //HDR 模式
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

    //录制和拍照
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (!EasyFloat.getFloatView()!!
                .findViewById<ToggleButton>(R.id.sw_permission)!!.isChecked
        ) {
            return false
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> { //音量上键等于拍照
                Toast.makeText(this.context, R.string.key_capture_start, Toast.LENGTH_SHORT).show()
                val bitmap = Bitmap.createBitmap(
                    arSceneView.width,
                    arSceneView.height,
                    Bitmap.Config.ARGB_8888
                )
                //使用 PixelCopy 直接复制
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
                        //使用 SAF 框架
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
            //录制
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
            //停止录制
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                toggleRecord(true)
                true
            }
            else -> false
        }
    }

    //切换录制
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

    //权限回调
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