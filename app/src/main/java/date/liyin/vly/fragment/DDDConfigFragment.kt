package date.liyin.vly.fragment

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.SceneView
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.ux.FootprintSelectionVisualizer
import com.google.ar.sceneform.ux.TransformableNode
import com.google.ar.sceneform.ux.TransformationSystem
import date.liyin.vly.MainActivity
import date.liyin.vly.R
import date.liyin.vly.ktutils.streamTo
import date.liyin.vly.utils.AppDatabase
import date.liyin.vly.utils.ModelSetting
import date.liyin.vly.virtualrender.AnimationRepeat
import date.liyin.vly.virtualrender.renderer.Pure3DRenderer
import java.io.File

//3D 安装界面
class DDDConfigFragment : Fragment() {
    lateinit var fileInstallCache: File //安装缓存
    private lateinit var mRender: Pure3DRenderer //3D 渲染器
    private lateinit var sceneView: SceneView //非 AR 渲染界面
    private lateinit var renderable: Renderable //模型
    private lateinit var transformationSystem: TransformationSystem //创建变换系统
    private lateinit var swEnableAnimation: Switch //是否开启模型全局动画
    private lateinit var sbSpeed: SeekBar //步行速度
    private lateinit var btnWalkAnimation: Button //步行动画
    private lateinit var btnBaseAnimation: Button //基础动画（默认值）
    private var needCopy = true //是否需要复制模型
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_d_d_d_config, container, false)
        val internal = requireArguments().getBoolean("installInternal") //获取是否使用内置模型
        val path = requireArguments().getParcelable<Uri>("install") //是否是安装模式
        val editUid = requireArguments().getLong("uid") //获得 UID
        val isEdit = requireArguments().containsKey("uid")
        swEnableAnimation = view.findViewById(R.id.sw_3d_animation)
        sbSpeed = view.findViewById(R.id.sb_walkspeed)
        btnBaseAnimation = view.findViewById(R.id.btn_3d_idle_animation)
        btnWalkAnimation = view.findViewById(R.id.btn_3d_walkanimation)
        sceneView = view.findViewById(R.id.sv_3d_install_preview)
        swEnableAnimation.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                btnWalkAnimation.isEnabled = true
                if (btnWalkAnimation.text != getString(R.string.select_no_action)) {
                    btnBaseAnimation.isEnabled = true
                    sbSpeed.isEnabled = true
                }
            } else {
                btnBaseAnimation.isEnabled = false
                btnWalkAnimation.isEnabled = false
                sbSpeed.isEnabled = false
            }
        }
        btnWalkAnimation.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setItems(
                    mRender.getAnimationList().toMutableList()
                        .apply { this.add(0, getString(R.string.select_no_action)) }
                        .toTypedArray() //在列表中插入空选项
                ) { _, pos ->
                    if (pos == 0) {
                        btnWalkAnimation.text = getString(R.string.select_no_action)
                        sbSpeed.isEnabled = false
                        btnBaseAnimation.isEnabled = false
                        mRender.stopLastAnimation()
                    } else {
                        btnWalkAnimation.text = mRender.getAnimationList().toTypedArray()[pos - 1]
                        sbSpeed.isEnabled = true
                        btnBaseAnimation.isEnabled = true
                        mRender.playAnimation(
                            btnWalkAnimation.text.toString(),
                            null,
                            AnimationRepeat.INFINITE()
                        ) { }
                    }
                }
                .create().show()
        }
        btnBaseAnimation.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setItems(
                    mRender.getAnimationList().toMutableList()
                        .apply { this.add(0, getString(R.string.select_no_action)) }
                        .toTypedArray() //在列表中插入空选项
                ) { _, pos ->
                    if (pos == 0) {
                        btnBaseAnimation.text = getString(R.string.select_no_action)
                    } else {
                        btnBaseAnimation.text = mRender.getAnimationList().toTypedArray()[pos - 1]
                    }
                }
                .create().show()
        }
        sceneView.setBackgroundColor(Color.GRAY)
        if (internal) { //如果使用内置模型
            setupInternal() //加载内置模型
            btnBaseAnimation.isEnabled = false
            btnWalkAnimation.isEnabled = false
            sbSpeed.isEnabled = false
            view.findViewById<Button>(R.id.btn_install_3d).let {
                it.setText(R.string.dialog_btn_install)
                it.setOnClickListener { install() }
            }
        } else { //非内置模型
            if (path == null) {
                if (isEdit) { //是否为编辑模式
                    setupEdit(view, editUid) //设置为编辑模式
                    Thread {
                        val model = AppDatabase.getInstance(requireContext())._3dModelDao()
                            .loadAllModelWithUID(editUid)?.first()
                        requireActivity().runOnUiThread {
                            swEnableAnimation.isSelected = model!!.useAnimation
                            if (model.useAnimation) {
                                if (model.allowWalkAnimation) {
                                    btnBaseAnimation.text = model.baseAnimation
                                    btnWalkAnimation.text = model.walkAnimation
                                    sbSpeed.progress = (model.walkActionSpeed * 1000f).toInt()
                                } else {
                                    btnBaseAnimation.isEnabled = false
                                    btnWalkAnimation.isEnabled = false
                                    sbSpeed.isEnabled = false
                                }
                            } else {
                                btnBaseAnimation.isEnabled = false
                                btnWalkAnimation.isEnabled = false
                                sbSpeed.isEnabled = false
                            }
                        }
                    }.start()
                    view.findViewById<Button>(R.id.btn_install_3d).let {
                        it.setText(R.string.dialog_btn_edit)
                        it.setOnClickListener { edit(editUid) }
                    }
                } else {
                    findNavController().navigate(R.id.action_goto_welcome)
                    Toast.makeText(
                        requireContext(),
                        R.string.application_not_expect,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                setupInstall(view, path)
                view.findViewById<Button>(R.id.btn_install_3d).let {
                    it.setText(R.string.dialog_btn_install)
                    it.setOnClickListener { install() }
                }
            }
        }
        return view
    }

    //编辑模式
    private fun setupEdit(view: View, uid: Long) {
        Thread {
            val p3dDao = AppDatabase.getInstance(requireContext())._3dModelDao()
            val p3d = p3dDao.loadAllModelWithUID(uid)!!.first()!!
            if (p3d.type == "INTERNAL") {
                requireActivity().runOnUiThread {
                    setupInternal()
                }
            } else {
                requireActivity().runOnUiThread {
                    transformationSystem = createTransformSystem()
                    fileInstallCache =
                        File(requireContext().getExternalFilesDir(null), "${p3d.uid}.glb")
                    mRender = Pure3DRenderer(requireContext(), fileInstallCache, loadEnded)
                    mRender.loadModel {
                        renderable = it
                        val anchor = AnchorNode()
                        anchor.setParent(sceneView.scene)
                        val node = TransformableNode(transformationSystem)
                        node.localPosition = Vector3(0f, -0.8f, -1.5f);
                        node.setParent(anchor)
                        node.renderable = renderable
                    }
                }
            }
        }.let {
            it.start()
            it.join()
        }
    }

    //设置内置模型
    private fun setupInternal() {
        needCopy = false //不需要复制
        transformationSystem = createTransformSystem()
        mRender = Pure3DRenderer(requireContext(), loadEnded)
        mRender.loadModel {
            renderable = it
            val anchor = AnchorNode()
            anchor.setParent(sceneView.scene)
            val node = TransformableNode(transformationSystem)
            node.localPosition = Vector3(0f, -0.8f, -1.5f)
            node.localRotation = Quaternion.axisAngle(Vector3.up(), 180f)
            node.setParent(anchor)
            node.renderable = renderable
        }
    }

    //设置安装模式
    private fun setupInstall(view: View, path: Uri) {
        transformationSystem = createTransformSystem()
        fileInstallCache = File(requireContext().externalCacheDir, "install.glb")
        requireContext().contentResolver.openInputStream(path)?.use {
            it.streamTo(fileInstallCache)
        }
        mRender = Pure3DRenderer(requireContext(), fileInstallCache, loadEnded)
        mRender.loadModel {
            renderable = it
            val anchor = AnchorNode()
            anchor.setParent(sceneView.scene)
            val node = TransformableNode(transformationSystem)
            node.localPosition = Vector3(0f, -0.8f, -1.5f);
            node.setParent(anchor)
            node.renderable = renderable
        }
    }

    //创建变换世界
    private fun createTransformSystem(): TransformationSystem {
        val selectionVisualizer = FootprintSelectionVisualizer()
        val transformationSystem =
            TransformationSystem(resources.displayMetrics, selectionVisualizer)
        setupSelectionRenderable(selectionVisualizer)
        return transformationSystem
    }

    private fun setupSelectionRenderable(selectionVisualizer: FootprintSelectionVisualizer) {
        ModelRenderable.builder()
            .setSource(activity, com.google.ar.sceneform.ux.R.raw.sceneform_footprint)
            .build()
            .thenAccept { renderable: ModelRenderable? ->
                // If the selection visualizer already has a footprint renderable, then it was set to
                // something custom. Don't override the custom visual.
                if (selectionVisualizer.footprintRenderable == null) {
                    selectionVisualizer.footprintRenderable = renderable
                }
            }
            .exceptionally { _: Throwable? ->
                val toast = Toast.makeText(
                    context, "Unable to load footprint renderable", Toast.LENGTH_LONG
                )
                toast.setGravity(Gravity.CENTER, 0, 0)
                toast.show()
                null
            }
    }

    private val loadEnded: (() -> Unit) = {

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (requireActivity() as MainActivity).currentFragment = this
    }

    override fun onDetach() {
        super.onDetach()
        if (needCopy) {
            fileInstallCache.let { //如果安装临时文件存在 则删除
                if (it.exists()) it.delete()
            }
        }
    }

    //应用编辑
    private fun edit(uid: Long) {
        Thread {
            val p3dDao = AppDatabase.getInstance(requireContext())._3dModelDao()
            val p3d = p3dDao.loadAllModelWithUID(uid)!!.first()!!
            if (swEnableAnimation.isChecked) {
                p3d.useAnimation = true
                if (btnWalkAnimation.text.toString() == getString(R.string.select_no_action)) {
                    p3d.allowWalkAnimation = false
                    p3d.baseAnimation = ""
                    p3d.walkAnimation = ""
                } else {
                    p3d.allowWalkAnimation = true
                    p3d.walkAnimation = btnWalkAnimation.text.toString()
                    p3d.walkActionSpeed = (sbSpeed.progress / 1000.0)
                    if (btnBaseAnimation.text.toString() == getString(R.string.select_no_action)) {
                        p3d.baseAnimation = ""
                    } else {
                        p3d.baseAnimation = btnBaseAnimation.text.toString()
                    }
                }
            } else {
                p3d.allowWalkAnimation = false
                p3d.baseAnimation = ""
                p3d.walkAnimation = ""
                p3d.useAnimation = false
            }
            p3dDao.updateModel(p3d)
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), R.string.status_installed, Toast.LENGTH_SHORT)
                    .show()
                findNavController().navigate(R.id.action_goto_welcome)
            }
        }.start()
    }

    //应用安装
    private fun install() {
        AlertDialog.Builder(requireContext()).apply {
            val input = EditText(requireContext())
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            input.layoutParams = lp
            this.setTitle(R.string.dialog_install)
            this.setMessage(R.string.dialog_install_msg)
            this.setNegativeButton(R.string.dialog_btn_back) { dialog, _ ->
                dialog.dismiss()
            }
            this.setPositiveButton(R.string.dialog_btn_install) { dialog, _ ->
                Thread {
                    if (input.text.trim().toString().isBlank()) {
                        requireActivity().runOnUiThread {
                            Toast.makeText(
                                requireContext(),
                                R.string.status_not_contain_necessary,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        dialog.dismiss()
                    } else {
                        val prebuildSetting = mRender.getModelSetting()
                        val modelDao = AppDatabase.getInstance(requireContext()).modelDao()
                        val p3dDao = AppDatabase.getInstance(requireContext())._3dModelDao()
                        val affect = modelDao.insertModel(
                            ModelSetting(
                                name = input.text.trim().toString(),
                                type = "3D"
                            )
                        )
                        prebuildSetting.uid = affect
                        prebuildSetting.modelName = input.text.toString().trim()
                        /* TODO Set 3D */
                        if (swEnableAnimation.isChecked) {
                            prebuildSetting.useAnimation = true
                            if (btnWalkAnimation.text.toString() == getString(R.string.select_no_action)) {
                                prebuildSetting.allowWalkAnimation = false
                                prebuildSetting.baseAnimation = ""
                                prebuildSetting.walkAnimation = ""
                            } else {
                                prebuildSetting.allowWalkAnimation = true
                                prebuildSetting.walkAnimation = btnWalkAnimation.text.toString()
                                prebuildSetting.walkActionSpeed = (sbSpeed.progress / 1000.0)
                                if (btnBaseAnimation.text.toString() == getString(R.string.select_no_action)) {
                                    prebuildSetting.baseAnimation = ""
                                } else {
                                    prebuildSetting.baseAnimation =
                                        btnBaseAnimation.text.toString()
                                }
                            }
                        } else {
                            prebuildSetting.allowWalkAnimation = false
                            prebuildSetting.baseAnimation = ""
                            prebuildSetting.walkAnimation = ""
                            prebuildSetting.useAnimation = false
                        }
                        p3dDao.insertModel(prebuildSetting)
                        if (needCopy) {
                            fileInstallCache.inputStream().streamTo(
                                File(
                                    requireContext().getExternalFilesDir(null),
                                    "$affect.${if (fileInstallCache.endsWith(".glb")) "glb" else "gltf"}"
                                )
                            )
                            fileInstallCache.delete()
                        }
                        requireActivity().runOnUiThread {
                            Toast.makeText(
                                requireContext(),
                                R.string.status_installed,
                                Toast.LENGTH_SHORT
                            ).show()
                            findNavController().navigate(R.id.action_goto_welcome)
                        }
                    }
                }.start()
            }
            this.setView(input)
        }.create().show()

    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<SceneView>(R.id.sv_3d_install_preview).resume()
    }

    override fun onPause() {
        super.onPause()
        requireActivity().findViewById<SceneView>(R.id.sv_3d_install_preview).pause()
    }
}
