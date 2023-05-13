package model

sealed class SideEffect {
    object FinishedStep1 : SideEffect()
    object Finished : SideEffect()

    override fun toString() = this::class.simpleName ?: super.toString()
}