import java.util.logging.Logger

abstract class EventHandler<S : Any, E : Any, SE : Any, C : Any> {
    abstract fun validate(controller: StateMachine<S, E, SE, C>.Controller, context: C) : Result
    abstract fun handle(controller: StateMachine<S, E, SE, C>.Controller, context: C)
    abstract fun error(controller: StateMachine<S, E, SE, C>.Controller, context: C)
    abstract fun exception(controller: StateMachine<S, E, SE, C>.Controller, e: Exception, context: C)

    sealed class Result {
        object Valid : Result()
        object Invalid : Result()
    }

    internal fun execute(scope: StateMachine<S, E, SE, C>.Controller, context: C){
        try {
            when(validate(scope, context)) {
                is Result.Valid -> handle(scope, context)
                is Result.Invalid -> error(scope, context)
            }
        } catch (e: Exception) {
            logger.severe(e.message)
            e.printStackTrace()
            exception(scope, e, context)
        }
    }

    companion object {
        private val logger = Logger.getLogger(this::class.simpleName)

        fun <S : Any, E : Any, SE : Any, C : Any> build(
            handler: (StateMachine<S, E, SE, C>.Controller, C) -> Unit
        ) = object : EventHandler<S, E, SE, C>() {
            override fun validate(
                controller: StateMachine<S, E, SE, C>.Controller,
                context: C
            ): Result = Result.Valid

            override fun handle(
                controller: StateMachine<S, E, SE, C>.Controller,
                context: C
            ) {
                handler(controller, context)
            }

            override fun error(
                controller: StateMachine<S, E, SE, C>.Controller,
                context: C
            ) {
                throw IllegalStateException("This event handler should not fail!")
            }

            override fun exception(
                controller: StateMachine<S, E, SE, C>.Controller,
                e: Exception,
                context: C
            ) {
                throw IllegalStateException(
                    "This event handler should not throw nor handle exception!",
                    e
                )
            }
        }
    }
}