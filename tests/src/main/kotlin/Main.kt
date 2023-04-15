import java.util.logging.Logger

interface State
interface Event
interface SideEffect

sealed class MyStates {
    object Initial : State
    object State1: State
    object State2: State
    object State3: State
    object State4: State
    object Final: State
}

sealed class MyEvents {
    object Event1: Event
    object Event2: Event
    object Event3: Event
    object Event4: Event
}

sealed class MySideEffects {
    object Effect1 : SideEffect
    object Effect2 : SideEffect
    object Effect3 : SideEffect
    object Effect4 : SideEffect
    object Effect5 : SideEffect
}

private val logger = Logger.getLogger("Main")

fun main(args: Array<String>) {
    val stateMachine = StateMachine.build<State, Event, SideEffect>("test") {
        initialState(MyStates.Initial) {
            finalState(MyStates.Final) {
                state(MyStates.Initial) {
                    on(MyEvents.Event1) {
                        transitTo(MyStates.State2, MySideEffects.Effect1)
                    }
                }

                state(MyStates.State1) {
                    on(MyEvents.Event1) {
                        transitTo(MyStates.State2, MySideEffects.Effect2)
                    }
                }

                onTransition {
                    when(it) {
                        is MySideEffects.Effect1 -> { logger.info("Effect1 execution") }
                        is MySideEffects.Effect2 -> { logger.info("Effect2 execution") }
                        is MySideEffects.Effect3 -> { logger.info("Effect3 execution") }
                        is MySideEffects.Effect4 -> { logger.info("Effect4 execution") }
                        is MySideEffects.Effect5 -> { logger.info("Effect5 execution") }
                    }
                }
            }
        }
    }

    stateMachine.fire(MyEvents.Event1)
}