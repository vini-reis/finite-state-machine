import model.Context
import model.Event
import model.SideEffect
import model.State
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit
import kotlin.test.*

@Timeout(30, unit = TimeUnit.SECONDS)
class BuilderTest {
    @Test
    @DisplayName("Should throw IllegalArgumentException when building a FSM without transitions")
    fun shouldThrowExceptionOnEmptyMachine() {
        assertThrows<IllegalArgumentException> {
            StateMachine.build<State, Event, SideEffect, Context>(TAG, State.Initial) {
                build()
            }
        }
    }

    @Test
    @DisplayName("Should throw IllegalStateException when building a FSM without final states")
    fun shouldThrowExceptionOnNoFinalState() {
        assertThrows<IllegalStateException> {
            StateMachine.build<State, Event, SideEffect, Context>(TAG, State.Initial) {
                from(State.Initial) {
                    on(Event.Start) {
                        goTo(State.State1)
                    }
                }

                build()
            }
        }
    }

    companion object {
        private const val TAG = "BuilderTest"
    }
}