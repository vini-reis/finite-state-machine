package model

sealed class State {
    object Initial : State()
    object State1: State()
    object Final: State()

    override fun toString() = this::class.simpleName ?: super.toString()
}