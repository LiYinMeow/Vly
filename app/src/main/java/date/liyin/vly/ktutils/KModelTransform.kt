package date.liyin.vly.ktutils
//DSL 设定
@DslMarker
annotation class KModelTransform

@KModelTransform
class KModelTransformConfig {
    private var scale_min = 0.5f
    private var scale_max = 2.0f
    private var rotate = false
    private var translation = false
    private var scale = false
    private var fakeShadow = false
    fun SCALE(min: Float = scale_min, max: Float = scale_max) {
        scale = true
        scale_min = min
        scale_max = max
    }

    fun ROTATE() {
        rotate = true
    }

    fun TRANSLATION() {
        translation = true
    }

    fun FAKESHADOW() {
        fakeShadow = true
    }

    fun build() = ModelTransformConfig(scale_min, scale_max, rotate, translation, scale, fakeShadow)
}

data class ModelTransformConfig(
    val scale_min: Float,
    val scale_max: Float,
    val rotate: Boolean,
    val translation: Boolean,
    val scale: Boolean,
    val fakeShadow: Boolean
)

fun modelTransfromConfig(setup: KModelTransformConfig.() -> Unit): ModelTransformConfig =
    KModelTransformConfig().apply { this.setup() }.build()