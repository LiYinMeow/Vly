package date.liyin.vly.virtualrender
//动画循环
enum class AnimationRepeat(val i: Int) {
    INFINITE(-1);

    operator fun invoke() = i
}