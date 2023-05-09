import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.fail

@Timeout(30, unit = TimeUnit.SECONDS)
class EventHandlerTest {
    sealed class State {
        object Initial : State()
        object Final : State()
    }

    sealed class Event {
        object Start : Event()
        object Complete : Event()
        object Error : Event()
        object Finished : Event()
    }

    sealed class SideEffect {
        object Finished : SideEffect()
    }

    data class Context<T, E>(
        var result: T? = null,
        var error: E? = null,
        var exception: Exception? = null
    )

    data class Error(val code: Int)

    private class ValidEventHandler(
        private val handler: StateMachine<State, Event, SideEffect, Context<Int, Error>>.Controller.(Context<Int, Error>, State, Event) -> Unit
    ) : EventHandler<State, Event, SideEffect, Context<Int, Error>>() {
        override fun validate(
            controller: StateMachine<State, Event, SideEffect, Context<Int, Error>>.Controller,
            context: Context<Int, Error>,
            state: State,
            event: Event
        ): Result = Result.Valid

        override suspend fun handle(
            controller: StateMachine<State, Event, SideEffect, Context<Int, Error>>.Controller,
            context: Context<Int, Error>,
            state: State,
            event: Event
        ) {
            handler(controller, context, state, event)
        }

        override fun error(
            controller: StateMachine<State, Event, SideEffect, Context<Int, Error>>.Controller,
            context: Context<Int, Error>,
            state: State,
            event: Event
        ) {
            fail("This event handler shall not run error callback")
        }

        override fun exception(
            controller: StateMachine<State, Event, SideEffect, Context<Int, Error>>.Controller,
            e: Exception,
            context: Context<Int, Error>
        ) {
            fail("This event handler shall not throw exceptions", e)
        }

        companion object {
            fun build(
                handler: StateMachine<State, Event, SideEffect, Context<Int, Error>>.Controller.(Context<Int, Error>, State, Event) -> Unit
            ) = ValidEventHandler(handler)
        }
    }

    private class ExceptionEventHandler(
        private val exception: Exception
    ) : EventHandler<State, Event, SideEffect, Context<Int, Error>>() {
        override fun validate(
            controller: StateMachine<State, Event, SideEffect, Context<Int, Error>>.Controller,
            context: Context<Int, Error>,
            state: State,
            event: Event
        ): Result = Result.Valid

        override suspend fun handle(
            controller: StateMachine<State, Event, SideEffect, Context<Int, Error>>.Controller,
            context: Context<Int, Error>,
            state: State,
            event: Event
        ) {
            throw exception
        }

        override fun error(
            controller: StateMachine<State, Event, SideEffect, Context<Int, Error>>.Controller,
            context: Context<Int, Error>,
            state: State,
            event: Event
        ) {
            fail("This event handler shall not run error callback")
        }

        override fun exception(
            controller: StateMachine<State, Event, SideEffect, Context<Int, Error>>.Controller,
            e: Exception,
            context: Context<Int, Error>
        ) {
            context.exception = e
        }

        companion object {
            fun build(exception: Exception) = ExceptionEventHandler(exception)
        }
    }

    private class InvalidEventHandler(
        private val errorCode: Int
    ) : EventHandler<State, Event, SideEffect, Context<Int, Error>>() {
        override fun validate(
            controller: StateMachine<State, Event, SideEffect, Context<Int, Error>>.Controller,
            context: Context<Int, Error>,
            state: State,
            event: Event
        ): Result = Result.Invalid

        override suspend fun handle(
            controller: StateMachine<State, Event, SideEffect, Context<Int, Error>>.Controller,
            context: Context<Int, Error>,
            state: State,
            event: Event
        ) {
            fail("This handler should not be executed!")
        }

        override fun error(
            controller: StateMachine<State, Event, SideEffect, Context<Int, Error>>.Controller,
            context: Context<Int, Error>,
            state: State,
            event: Event
        ) {
            context.error = Error(errorCode)
        }

        override fun exception(
            controller: StateMachine<State, Event, SideEffect, Context<Int, Error>>.Controller,
            e: Exception,
            context: Context<Int, Error>
        ) {
            fail("This event handler should not throw exceptions", e)
        }

        companion object {
            fun build(errorCode: Int) = InvalidEventHandler(errorCode)
        }
    }

    @Test
    @DisplayName("Should execute the handler on event")
    fun shouldExecuteHandling() {
        val fsm = buildUnitFsm(ValidEventHandler.build { context, _, _ ->
            context.result = 10
        }) { context ->
            assertNotNull(context.result)
            assertEquals(10, context.result)
        }

        fsm.start(Event.Start, buildInitialContext())
    }

    @Test
    @DisplayName("Should call error callback")
    fun shouldCallError() {
        val fsm = buildUnitFsm(InvalidEventHandler.build(400)) { context ->
            assertNotNull(context.error)
            assertEquals(400, context.error!!.code)
        }

        fsm.start(Event.Start, buildInitialContext())
    }

    @Test
    @DisplayName("Should throw exception")
    fun shouldThrowException() {
        val fsm = buildUnitFsm(ExceptionEventHandler.build(ClassCastException())) { context ->
            assertNotNull(context.exception)
            assertIs<ClassCastException>(context.exception)
        }

        fsm.start(Event.Start, buildInitialContext())
    }

    companion object {
        private const val TAG = "EventHandlerTest"

        private fun buildInitialContext() = Context<Int, Error>()

        private fun buildUnitFsm(
            handler: EventHandler<State, Event, SideEffect, Context<Int, Error>>,
            onFinish: (Context<Int, Error>) -> Unit
        ) =
            StateMachine.build<State, Event, SideEffect, Context<Int, Error>>(TAG, State.Initial) {
                from(State.Initial) {
                    on(Event.Start) {
                        execute(handler)

                        finishOn(State.Final, SideEffect.Finished)
                    }
                }

                onTransition { _, _, _, sideEffect, context ->
                    when(sideEffect) {
                        SideEffect.Finished -> onFinish(context)
                    }
                }

                build()
            }
    }
}