import model.Context
import model.Event
import model.SideEffect
import model.State
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

@Timeout(30, unit = TimeUnit.SECONDS)
class ExecutionTest {
    @Test
    @DisplayName("Build and run a complete state machine")
    fun buildAndRun() {
        val stateMachine = StateMachine.build<State, Event, SideEffect, Context>(
            "CompleteService",
            State.Initial
        ) {
            from(State.Initial) {
                on(Event.Start) {
                    execute { _, _, _ ->
                        logger.info("I am running before this transition is made")

                        trigger(Event.Complete)
                    }

                    goTo(State.State1, SideEffect.FinishedStep1)
                }
            }

            from(State.State1) {
                on(Event.Complete) {
                    execute { context, _, _ ->
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

            build()
        }

        stateMachine.start(Event.Start, Context(1, "Example"))
    }

    @Test
    @DisplayName("Should add the onException block")
    fun shouldCatchException() {
        val stateMachine = StateMachine.build<State, Event, SideEffect, Context>(
            "WithErrorService",
            State.Initial
        ) {
            from(State.Initial) {
                on(Event.Start) {
                    execute { _, _, _ ->
                        logger.info("I am running before this transition is made")

                        trigger(Event.Complete)
                    }

                    goTo(State.State1, SideEffect.FinishedStep1)
                }
            }

            from(State.State1) {
                on(Event.Complete) {
                    execute { _, _, _ ->
                        throw IllegalStateException("Exception test to be catch by onExceptionBlock")
                    }

                    execute { _, _, _ ->
                        fail("The others event handlers should not be executed!")
                    }

                    finishOn(State.Final, SideEffect.Finished)
                }
            }

            onException { _, state, event, exception ->
                logger.severe("Oops! Something went wrong during step $state on event $event...")

                assertIs<IllegalStateException>(exception)
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
                        fail("The finished side effect should not be run!")
                    }
                }
            }

            build()
        }

        stateMachine.start(Event.Start, Context(1, "Example"))
    }

    @Test
    @DisplayName("Should allow FSM reset and run it twice")
    fun shouldAllowFsmReset() {
        val context = Context(2, "Testing...")
        var finished = false
        val fsm = StateMachine.build<State, Event, SideEffect, Context>(TAG, State.Initial) {
            from(State.Initial) {
                on(Event.Start) {
                    execute { context, _, _ -> context.three = (context.three ?: 0) + 1 }

                    finishOn(State.Final, SideEffect.Finished)
                }
            }

            onTransition { _, _, _, sideEffect, context ->
                when(sideEffect) {
                    SideEffect.Finished -> {
                        if(finished){
                            assertEquals(2L, context.three)
                        } else {
                            finished = true
                            assertEquals(1L, context.three)
                        }
                    }
                    else -> fail("Should not run other side effects")
                }
            }

            build()
        }

        fsm.start(Event.Start, context)

        while(!finished) Thread.sleep(10)

        fsm.reset(Event.Start, context)
    }

    companion object {
        private const val TAG = "ExecutionTest"
        private val logger = Logger.getLogger(TAG)
    }
}