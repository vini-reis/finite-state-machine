package builder

import StateMachine
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.logging.Logger

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
            "test",
            State.Initial
        ) {
            context {
                Context(1, "Example")
            }

            from(State.Initial) {
                on(Event.Start) {
                    execute { context ->
                        logger.info("I am running before this transition is made")

                        context.three = 10L

                        trigger(Event.Complete)
                    }

                    transitTo(State.State1, SideEffect.FinishedStep1)
                }
            }

            from(State.State1) {
                on(Event.Complete) {
                    finishOn(State.Final, SideEffect.Finished)
                }
            }

            onException { context, state, event, exception ->
                logger.severe("Oops! Something went wrong during step $state on event $event...")
                exception.printStackTrace()
            }

            onTransition { _, _, _, effect: SideEffect, _ ->
                when(effect) {
                    is SideEffect.FinishedStep1 -> { logger.info("Step 1 finished") }
                    is SideEffect.Finished -> { logger.info("Did something before this machine finishes.") }
                }
            }
        }

        stateMachine.start(Event.Start)
    }

    companion object {
        private val logger = Logger.getLogger("Main")
    }
}