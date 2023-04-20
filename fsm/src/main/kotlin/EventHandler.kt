import java.util.logging.Logger

abstract class EventHandler<S : Any, E : Any, SE : Any, C : Any> {
    abstract fun validate(scope: StateMachine<S, E, SE, C>.ExecutionBuilder, context: C) : Result
    abstract fun handle(scope: StateMachine<S, E, SE, C>.ExecutionBuilder, context: C)
    abstract fun error(scope: StateMachine<S, E, SE, C>.ExecutionBuilder, context: C)
    abstract fun exception(scope: StateMachine<S, E, SE, C>.ExecutionBuilder, e: Exception, context: C)

    sealed class Result {
        object Valid : Result()
        object Invalid : Result()
    }

    internal fun execute(scope: StateMachine<S, E, SE, C>.ExecutionBuilder, context: C){
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
    }
}