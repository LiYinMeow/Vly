package date.liyin.vly.fragment

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import date.liyin.vly.MainActivity
import date.liyin.vly.R
import date.liyin.vly.ktutils.streamTo
import date.liyin.vly.live2d.JniBridgeJava
import date.liyin.vly.live2d.L2DRenderer
import date.liyin.vly.utils.AppDatabase
import date.liyin.vly.utils.IKeyPassthough
import date.liyin.vly.utils.ModelSetting
import date.liyin.vly.utils._L2DModelSetting
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile


//Live2D 安装界面
class Live2DConfigFragment : Fragment(), JniBridgeJava.FileGetter, IKeyPassthough {
    companion object { //默认画布大小
        const val defaultCanvasW = 1150
        const val defaultCanvasH = 1440
    }

    lateinit var fileInstallCache: File //安装缓存
    private val mRenderer = L2DRenderer() //Live2D 渲染器
    lateinit var zipFile: ZipFile
    val scheduledExecutorService = Executors.newScheduledThreadPool(1)
    var modelJSON = ""
    private lateinit var model_x: SeekBar
    private lateinit var model_y: SeekBar
    private lateinit var model_scale: SeekBar
    private lateinit var model_height: EditText
    private val texture = SurfaceTexture(0).apply { //创建离屏渲染界面
        this.setDefaultBufferSize(defaultCanvasW, defaultCanvasH)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fileInstallCache = File(requireContext().externalCacheDir, "install.zip")
        val view = inflater.inflate(R.layout.fragment_live2_d_config, container, false)
        model_x = view.findViewById(R.id.sk_l2d_x)
        model_y = view.findViewById(R.id.sk_l2d_y)
        model_scale = view.findViewById(R.id.sk_l2d_scale)
        model_height = view.findViewById(R.id.ed_l2d_height)
        model_x.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                JniBridgeJava.nativeSetModelX((progress / 200.0f) - 1.0f)
                println("X: ${(progress / 200.0f) - 1.0f}")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        model_y.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                JniBridgeJava.nativeSetModelY((progress / 200.0f) - 1.0f)
                println("Y: ${(progress / 200.0f) - 1.0f}")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        model_scale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                JniBridgeJava.nativeSetModelScale(progress / 500.0f)
                println("Scale: ${progress / 500.0f}")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val path = requireArguments().getParcelable<Uri>("install") //获得安装模式
        val editUid = requireArguments().getLong("uid") //获得 UID
        val isEdit = requireArguments().containsKey("uid")
        if (path == null) {
            if (isEdit) { //是否是编辑
                setupEdit(view, File(requireContext().getExternalFilesDir(null), "$editUid.zip"))
                Thread {
                    val model = AppDatabase.getInstance(requireContext())._l2dModelDao()
                        .loadAllModelWithUID(editUid)?.first()
                    requireActivity().runOnUiThread {
                        model_x.progress = ((model!!.x + 1.0f) * 200f).toInt()
                        model_y.progress = ((model.y + 1.0f) * 200f).toInt()
                        model_height.setText(model.height.toString())
                        model_scale.progress = (model.scale * 500.0f).toInt()
                    }
                }.start()
                view.findViewById<Button>(R.id.btn_live2d_install).let {
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
            setupInstall(view, path) //设置安装
            view.findViewById<Button>(R.id.btn_live2d_install).let {
                it.setText(R.string.dialog_btn_install)
                it.setOnClickListener { install() }
            }
        }
        return view
    }

    //设置编辑
    private fun setupEdit(view: View, path: File) {
        mRenderer.setActivity(requireActivity())
        mRenderer.setSurfaceTextureSize(defaultCanvasW, defaultCanvasH)
        zipFile = ZipFile(path)
        val e = zipFile.entries()
        val s = mutableListOf<String>()
        while (e.hasMoreElements()) {
            s.add(e.nextElement().name)
        }
        val mjson = s.filter { it.endsWith(".model3.json") }
        if (mjson.count() != 1) {
            findNavController().navigate(R.id.action_goto_welcome)
            Toast.makeText(
                requireContext(),
                R.string.install_l2d_duplicate_json_or_not_have,
                Toast.LENGTH_SHORT
            ).show()
        } else {
            modelJSON = mjson[0]
            JniBridgeJava.nativeSetModelJSONName(modelJSON.replace(".model3.json", ""))
            JniBridgeJava.setFileGetter(this)
            JniBridgeJava.nativeSetConfigMode(true)
            mRenderer.start()
            mRenderer.setSurfaceTexture(texture)
            scheduledExecutorService.scheduleAtFixedRate({
                try {
                    if (mRenderer.lastFrame != null) view.findViewById<ImageView>(R.id.iv_preview)
                        .setImageBitmap(mRenderer.lastFrame)
                } catch (e: Exception) {
                }
            }, 40, 40, TimeUnit.MILLISECONDS)
        }
    }

    //设置安装
    private fun setupInstall(view: View, path: Uri) {
        mRenderer.setActivity(requireActivity())
        mRenderer.setSurfaceTextureSize(defaultCanvasW, defaultCanvasH)
        requireContext().contentResolver.openInputStream(path)?.use {
            it.streamTo(fileInstallCache)
        }
        zipFile = ZipFile(fileInstallCache)
        val e = zipFile.entries()
        val s = mutableListOf<String>()
        while (e.hasMoreElements()) {
            s.add(e.nextElement().name)
        }
        val mjson = s.filter { it.endsWith(".model3.json") }
        if (mjson.count() != 1) {
            findNavController().navigate(R.id.action_goto_welcome)
            Toast.makeText(
                requireContext(),
                R.string.install_l2d_duplicate_json_or_not_have,
                Toast.LENGTH_SHORT
            ).show()
        } else {
            modelJSON = mjson[0]
            JniBridgeJava.nativeSetModelJSONName(modelJSON.replace(".model3.json", ""))
            JniBridgeJava.setFileGetter(this)
            JniBridgeJava.nativeSetConfigMode(true)
            mRenderer.start()
            mRenderer.setSurfaceTexture(texture)
            scheduledExecutorService.scheduleAtFixedRate({
                try {
                    if (mRenderer.lastFrame != null) view.findViewById<ImageView>(R.id.iv_preview)
                        .setImageBitmap(mRenderer.lastFrame)
                } catch (e: Exception) {
                }
            }, 40, 40, TimeUnit.MILLISECONDS)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (requireActivity() as MainActivity).currentFragment = this
    }

    override fun onDetach() {
        super.onDetach()
        scheduledExecutorService.shutdownNow()
        if (modelJSON != "") mRenderer.halt()
        fileInstallCache.let {
            if (it.exists()) it.delete()
        }
    }

    //实现接口以将文件传输给 C++ 层
    override fun requestFile(path: String?): ByteArray {
        return zipFile.getInputStream(zipFile.getEntry(path)).readBytes()
    }

    //编辑
    private fun edit(uid: Long) {
        if (model_height.text.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                R.string.status_not_contain_necessary,
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Thread {
                val l2dDao = AppDatabase.getInstance(requireContext())._l2dModelDao()
                val l2dP = l2dDao.loadAllModelWithUID(uid)!!.first()!!
                l2dP.scale = model_scale.progress.toFloat() / 500.0f
                l2dP.height = model_height.text.trim().toString().toFloat()
                l2dP.x = (model_x.progress / 200.0f) - 1.0f
                l2dP.y = (model_y.progress / 200.0f) - 1.0f
                l2dDao.updateModel(l2dP)
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), R.string.status_installed, Toast.LENGTH_SHORT)
                        .show()
                    findNavController().navigate(R.id.action_goto_welcome)
                }
            }.start()
        }
    }

    //安装
    private fun install() {
        if (model_height.text.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                R.string.status_not_contain_necessary,
                Toast.LENGTH_SHORT
            ).show()
        } else {
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
                            val modelDao = AppDatabase.getInstance(requireContext()).modelDao()
                            val l2dDao = AppDatabase.getInstance(requireContext())._l2dModelDao()
                            val affect = modelDao.insertModel(
                                ModelSetting(
                                    name = input.text.trim().toString(), type = "Live2D"
                                )
                            )
                            l2dDao.insertModel(
                                _L2DModelSetting(
                                    uid = affect,
                                    canvasHeight = defaultCanvasH,
                                    canvasWidth = defaultCanvasW,
                                    modelName = input.text.trim().toString(),
                                    modelJsonName = modelJSON,
                                    x = (model_x.progress / 200.0f) - 1.0f,
                                    y = (model_y.progress / 200.0f) - 1.0f,
                                    scale = model_scale.progress.toFloat() / 500.0f,
                                    height = model_height.text.trim().toString().toFloat()
                                )
                            )
                            fileInstallCache.inputStream().streamTo(
                                File(
                                    requireContext().getExternalFilesDir(null),
                                    "$affect.zip"
                                )
                            )
                            fileInstallCache.delete()
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
    }

    override fun onBackPressed() {
        findNavController().navigate(R.id.action_goto_welcome)
    }
}
