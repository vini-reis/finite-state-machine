package model

sealed class Event {
    object Start: Event()
    object Complete: Event()

    override fun toString() = this::class.simpleName ?: super.toString()
}