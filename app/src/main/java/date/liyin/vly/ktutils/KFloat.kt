package date.liyin.vly.ktutils

@DslMarker
annotation class KFloat

@KFloat
class KFloatConfig {
    private var move = false
    private var face = false
    private var action = false
    fun MOVE() {
        move = true
    }

    fun FACE() {
        face = true
    }

    fun ACTION() {
        action = true
    }

    fun build() = FloatingConfig(move, face, action)
}

data class FloatingConfig(var move: Boolean, var face: Boolean, var action: Boolean)

fun floatConfig(setup: KFloatConfig.() -> Unit): FloatingConfig =
    KFloatConfig().apply { this.setup() }.build()