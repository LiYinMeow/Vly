package date.liyin.vly.virtualrender.renderer

import android.content.Context
import android.widget.Toast
import com.google.ar.sceneform.rendering.Renderable
import date.liyin.vly.R
import date.liyin.vly.ktutils.FloatingConfig
import date.liyin.vly.ktutils.ModelTransformConfig
import date.liyin.vly.ktutils.floatConfig
import date.liyin.vly.ktutils.modelTransfromConfig
import date.liyin.vly.virtualrender.PlaceMode
import date.liyin.vly.virtualrender.VirtualRender

class NoneRender(context: Context, uid: Long, loadEnded: () -> Unit) :
    VirtualRender(context, uid, loadEnded) {
    override fun getPlaceMode(): PlaceMode =
        PlaceMode.HORIZONTAL_UPWARD_AND_VERTICAL_DIRECT

    override fun getFloatingConfig(): FloatingConfig = floatConfig { }

    override fun getModelTransformConfig(): ModelTransformConfig = modelTransfromConfig { }

    override fun loadModel(func: (Renderable) -> Unit) {
        Toast.makeText(context, R.string.application_not_expect, Toast.LENGTH_SHORT).show()
    }
}