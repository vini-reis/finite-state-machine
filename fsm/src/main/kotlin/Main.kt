sealed class MyStates {
    object Initial : State()
    object State1: State()
    object State2: State()
    object State3: State()
    object State4: State()
    object Final: State()
}

sealed class MyEvents {
    object Event1: Event()
    object Event2: Event()
    object Event3: Event()
    object Event4: Event()
}

sealed class MySideEffects {
    object Effect1 : SideEffect()
    object Effect2 : SideEffect()
    object Effect3 : SideEffect()
    object Effect4 : SideEffect()
    object Effect5 : SideEffect()
}

fun main(args: Array<String>) {
    // CANNOT WORK
    StateMachine.New("")

    StateMachine.build("test") {
        initialState(MyStates.Initial) {
            finalState(MyStates.Final) {
                state(MyStates.State1) {
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

                }
            }
        }
    }
}