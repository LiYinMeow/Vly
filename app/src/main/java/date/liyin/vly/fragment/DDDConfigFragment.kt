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

class DDDConfigFragment : Fragment() {
    lateinit var fileInstallCache: File
    private lateinit var mRender: Pure3DRenderer
    private lateinit var sceneView: SceneView
    private lateinit var renderable: Renderable
    private lateinit var transformationSystem: TransformationSystem
    private lateinit var sw_enable_animation: Switch
    private lateinit var sb_speed: SeekBar
    private lateinit var btn_walkanimation: Button
    private lateinit var btn_baseanimation: Button
    private var needCopy = true
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_d_d_d_config, container, false)
        val internal = requireArguments().getBoolean("installInternal")
        val path = requireArguments().getParcelable<Uri>("install")
        val editUid = requireArguments().getLong("uid")
        val isEdit = requireArguments().containsKey("uid")
        sw_enable_animation = view.findViewById(R.id.sw_3d_animation)
        sb_speed = view.findViewById(R.id.sb_walkspeed)
        btn_baseanimation = view.findViewById(R.id.btn_3d_idle_animation)
        btn_walkanimation = view.findViewById(R.id.btn_3d_walkanimation)
        sceneView = view.findViewById(R.id.sv_3d_install_preview)
        sw_enable_animation.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                btn_walkanimation.isEnabled = true
                if (btn_walkanimation.text != getString(R.string.select_no_action)) {
                    btn_baseanimation.isEnabled = true
                    sb_speed.isEnabled = true
                }
            } else {
                btn_baseanimation.isEnabled = false
                btn_walkanimation.isEnabled = false
                sb_speed.isEnabled = false
            }
        }
        btn_walkanimation.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setItems(
                    mRender.getAnimationList().toMutableList()
                        .apply { this.add(0, getString(R.string.select_no_action)) }.toTypedArray()
                ) { _, pos ->
                    if (pos == 0) {
                        btn_walkanimation.text = getString(R.string.select_no_action)
                        sb_speed.isEnabled = false
                        btn_baseanimation.isEnabled = false
                        mRender.stopLastAnimation()
                    } else {
                        btn_walkanimation.text = mRender.getAnimationList().toTypedArray()[pos - 1]
                        sb_speed.isEnabled = true
                        btn_baseanimation.isEnabled = true
                        mRender.playAnimation(
                            btn_walkanimation.text.toString(),
                            null,
                            AnimationRepeat.INFINITE()
                        ) { }
                    }
                }
                .create().show()
        }
        btn_baseanimation.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setItems(
                    mRender.getAnimationList().toMutableList()
                        .apply { this.add(0, getString(R.string.select_no_action)) }.toTypedArray()
                ) { _, pos ->
                    if (pos == 0) {
                        btn_baseanimation.text = getString(R.string.select_no_action)
                    } else {
                        btn_baseanimation.text = mRender.getAnimationList().toTypedArray()[pos - 1]
                    }
                }
                .create().show()
        }
        sceneView.setBackgroundColor(Color.GRAY)
        if (internal) {
            setupInternal()
            btn_baseanimation.isEnabled = false
            btn_walkanimation.isEnabled = false
            sb_speed.isEnabled = false
            view.findViewById<Button>(R.id.btn_install_3d).let {
                it.setText(R.string.dialog_btn_install)
                it.setOnClickListener { install() }
            }
        } else {
            if (path == null) {
                if (isEdit) {
                    setupEdit(view, editUid)
                    Thread {
                        val model = AppDatabase.getInstance(requireContext())._3dModelDao()
                            .loadAllModelWithUID(editUid)?.first()
                        requireActivity().runOnUiThread {
                            sw_enable_animation.isSelected = model!!.useAnimation
                            if (model.useAnimation) {
                                if (model.allowWalkAnimation) {
                                    btn_baseanimation.text = model.baseAnimation
                                    btn_walkanimation.text = model.walkAnimation
                                    sb_speed.progress = (model.walkActionSpeed * 1000f).toInt()
                                } else {
                                    btn_baseanimation.isEnabled = false
                                    btn_walkanimation.isEnabled = false
                                    sb_speed.isEnabled = false
                                }
                            } else {
                                btn_baseanimation.isEnabled = false
                                btn_walkanimation.isEnabled = false
                                sb_speed.isEnabled = false
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

    private fun setupInternal() {
        needCopy = false
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
            fileInstallCache.let {
                if (it.exists()) it.delete()
            }
        }
    }

    private fun edit(uid: Long) {
        Thread {
            val p3dDao = AppDatabase.getInstance(requireContext())._3dModelDao()
            val p3d = p3dDao.loadAllModelWithUID(uid)!!.first()!!
            if (sw_enable_animation.isChecked) {
                p3d.useAnimation = true
                if (btn_walkanimation.text.toString() == getString(R.string.select_no_action)) {
                    p3d.allowWalkAnimation = false
                    p3d.baseAnimation = ""
                    p3d.walkAnimation = ""
                } else {
                    p3d.allowWalkAnimation = true
                    p3d.walkAnimation = btn_walkanimation.text.toString()
                    p3d.walkActionSpeed = (sb_speed.progress / 1000.0)
                    if (btn_baseanimation.text.toString() == getString(R.string.select_no_action)) {
                        p3d.baseAnimation = ""
                    } else {
                        p3d.baseAnimation = btn_baseanimation.text.toString()
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
                        if (sw_enable_animation.isChecked) {
                            prebuildSetting.useAnimation = true
                            if (btn_walkanimation.text.toString() == getString(R.string.select_no_action)) {
                                prebuildSetting.allowWalkAnimation = false
                                prebuildSetting.baseAnimation = ""
                                prebuildSetting.walkAnimation = ""
                            } else {
                                prebuildSetting.allowWalkAnimation = true
                                prebuildSetting.walkAnimation = btn_walkanimation.text.toString()
                                prebuildSetting.walkActionSpeed = (sb_speed.progress / 1000.0)
                                if (btn_baseanimation.text.toString() == getString(R.string.select_no_action)) {
                                    prebuildSetting.baseAnimation = ""
                                } else {
                                    prebuildSetting.baseAnimation =
                                        btn_baseanimation.text.toString()
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
