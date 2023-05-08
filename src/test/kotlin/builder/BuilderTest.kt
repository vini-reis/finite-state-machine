package builder

import StateMachine
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.logging.Logger
import kotlin.test.assertEquals

class BuilderTest {
    sealed class State {
        object Initial : State()
        object State1: State()
        object Final: State()

        override fun toString() = this::class.simpleName ?: super.toString()
    }

    sealed class Event {
        object Start: Event()
        object Complete: Event()

        override fun toString() = this::class.simpleName ?: super.toString()
    }

    sealed class SideEffect {
        object FinishedStep1 : SideEffect()
        object Finished : SideEffect()

        override fun toString() = this::class.simpleName ?: super.toString()
    }

    data class Context(
        val one: Int,
        val two: String,
    ) {
        var three: Long? = null
    }

    @Test
    @DisplayName("Build and run a complete state machine")
    fun buildAndRun() {
        val stateMachine = StateMachine.build<State, Event, SideEffect, Context>(
            "CompleteService",
            State.Initial
        ) {
            context {
                Context(1, "Example")
            }

            from(State.Initial) {
                on(Event.Start) {
                    execute { context ->
                        logger.info("I am running before this transition is made")

                        trigger(Event.Complete)
                    }

                    transitTo(State.State1, SideEffect.FinishedStep1)
                }
            }

            from(State.State1) {
                on(Event.Complete) {
                    execute { context ->
                        context.three = 10L
                    }

                    finishOn(State.Final, SideEffect.Finished)
                }
            }

            onException { _, state, event, exception ->
                logger.severe("Oops! Something went wrong during step $state on event $event...")
                exception.printStackTrace()
            }

            onTransition { current: State, on: Event, target: State, effect: SideEffect, context: Context ->
                when(effect) {
                    is SideEffect.FinishedStep1 -> {
                        assertEquals(State.Initial, current)
                        assertEquals(Event.Start, on)
                        assertEquals(State.State1, target)
                        assertEquals(null, context.three)
                    }
                    is SideEffect.Finished -> {
                        assertEquals(State.State1, current)
                        assertEquals(Event.Complete, on)
                        assertEquals(State.Final, target)
                        assertEquals(10L, context.three)
                    }
                }
            }
        }

        stateMachine.start(Event.Start)
    }

    companion object {
        private val logger = Logger.getLogger("Main")
    }
}