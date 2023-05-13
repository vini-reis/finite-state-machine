package model

data class Context2<T, E>(
    var result: T? = null,
    var error: E? = null,
    var exception: Exception? = null
)