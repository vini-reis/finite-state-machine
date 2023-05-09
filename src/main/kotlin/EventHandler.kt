import java.util.logging.Logger

abstract class EventHandler<S : Any, E : Any, SE : Any, C : Any> {
    abstract fun validate(controller: StateMachine<S, E, SE, C>.Controller, context: C, state: S, event: E) : Result
    abstract suspend fun handle(controller: StateMachine<S, E, SE, C>.Controller, context: C, state: S, event : E)
    abstract fun error(controller: StateMachine<S, E, SE, C>.Controller, context: C, state: S, event: E)
    abstract fun exception(controller: StateMachine<S, E, SE, C>.Controller, e: Exception, context: C)

    sealed class Result {
        object Valid : Result()
        object Invalid : Result()
    }

    internal suspend fun execute(scope: StateMachine<S, E, SE, C>.Controller, context: C, state: S, event: E){
        try {
            when(validate(scope, context, state, event)) {
                is Result.Valid -> handle(scope, context, state, event)
                is Result.Invalid -> error(scope, context, state, event)
            }
        } catch (e: Exception) {
            logger.severe(e.message)
            exception(scope, e, context)
        }
    }

    companion object {
        private val logger = Logger.getLogger(this::class.simpleName)

        fun <S : Any, E : Any, SE : Any, C : Any> build(
            handler: suspend (StateMachine<S, E, SE, C>.Controller, C, S, E) -> Unit
        ) = object : EventHandler<S, E, SE, C>() {
            override fun validate(
                controller: StateMachine<S, E, SE, C>.Controller,
                context: C,
                state: S,
                event: E
            ): Result = Result.Valid

            override suspend fun handle(
                controller: StateMachine<S, E, SE, C>.Controller,
                context: C,
                state: S,
                event: E
            ) {
                handler(controller, context, state, event)
            }

            override fun error(
                controller: StateMachine<S, E, SE, C>.Controller,
                context: C,
                state: S,
                event: E
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