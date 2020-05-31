package date.liyin.vly.virtualrender

enum class AnimationRepeat(val i: Int) {
    INFINITE(-1);

    operator fun invoke() = i
}