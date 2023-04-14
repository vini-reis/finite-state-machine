abstract class State {
    val transitions: MutableList<Transition<*, *>> = mutableListOf()

    data class Transition<S : State, SE: SideEffect>(val to: S, val effect: SE)

    fun <E : Event> on(event: E, on: E.() -> Transition<*, *>){ transitions.add(on(event)) }
}

abstract class Event {
    fun <S : State, SE: SideEffect> transitTo(to: S, sd: SE) = State.Transition(to, sd)
}
abstract class SideEffect

class StateMachine private constructor(
    val name: String,
    private val initialState: State,
    private val finalState: State,
    private val states: List<State> = listOf(),
    private val onTransition: (SideEffect) -> Unit,
){
    open class New internal constructor(open val name: String) {
        fun initialState(state: State, f: WithInitialState.() -> StateMachine) : StateMachine =
            f(WithInitialState(name, state))
    }

    open class WithInitialState internal constructor(
        override val name: String,
        open val initialState: State,
    ) : New(name) {
        fun finalState(state: State, addStates: WithFinalState.() -> StateMachine): StateMachine =
            addStates(WithFinalState(name, initialState, state))
    }

    open class WithFinalState internal constructor(
        override val name: String,
        override val initialState: State,
        private val finalState: State,
        private val states: MutableList<State> = mutableListOf()
    ) : WithInitialState(name, initialState) {
        fun <S : State> state(state: S, add: S.() -> Unit) {
            if (!states.contains(state))
                states.add(state)

            add(state)
        }

        fun onTransition(execute: (SideEffect) -> Unit) = StateMachine(name, initialState, finalState, states, execute)
    }

    companion object {
        fun build(name: String, init: New.() -> StateMachine): StateMachine = init(New(name))
    }
}