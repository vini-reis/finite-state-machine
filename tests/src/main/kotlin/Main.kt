import java.util.logging.Logger

interface State
interface Event
interface SideEffect

sealed class MyStates {
    object Initial : State
    object State1: State
    object Final: State
}

sealed class MyEvents {
    object Event1: Event
    object Event2: Event
}

sealed class MySideEffects {
    object Effect1 : SideEffect
    object Effect2 : SideEffect
}

private val logger = Logger.getLogger("Main")

fun main(args: Array<String>) {
    val stateMachine = StateMachine.build<State, Event, SideEffect>("test") {
        initialState(MyStates.Initial) {
            finalState(MyStates.Final) {
                state(MyStates.Initial) {
                    on(MyEvents.Event1) {
                        execute {
                            logger.info("I am running before this transition is made")
                        }

                        transitTo(MyStates.State1, MySideEffects.Effect1)
                    }
                }

                state(MyStates.State1) {
                    on(MyEvents.Event2) {
                        transitTo(MyStates.Final, MySideEffects.Effect2)
                    }
                }

                onTransition { _, _, _, effect: SideEffect ->
                    when(effect) {
                        is MySideEffects.Effect1 -> { logger.info("Effect1 execution") }
                        is MySideEffects.Effect2 -> { logger.info("Effect2 execution") }
                    }
                }
            }
        }
    }

    stateMachine.trigger(MyEvents.Event1)
}