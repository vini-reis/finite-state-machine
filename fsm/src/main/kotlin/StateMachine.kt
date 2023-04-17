import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger

/**
 * A type safe Finite State Machine that might be used to manage processes. The [State] type represents a super type
 * or an interface used to represent the states. All states must be inherited from [State]. The same goes to
 * [Event] and [SideEffect].
 */
class StateMachine<State : Any, Event : Any, SideEffect : Any> private constructor(){
    lateinit var name: String
    private lateinit var initialState: State
    private lateinit var finalState: State
    private lateinit var onTransition: (from: State, on: Event, to: State, effect: SideEffect) -> Unit
    private val currentStateRef: AtomicReference<State> = AtomicReference()
    private val onTransitionsMap: HashMap<State, MutableList<Transition.Internal<Event, State, SideEffect, Event>>> = hashMapOf()

    /**
     * Object that maps the transitions on FSM
     */
    sealed class Transition<E> {
        abstract val on: E

        /**
         * Valid transition that will change the FSM [from] current State on event [on] to [to] state
         * and trigger a side effect [effect]. All those parameters will be passed to [onTransition] callback.
         */
        data class Valid<FS, E, TS, SE, TE>(
            val from: FS,
            override val on: E,
            val run: List<() -> Unit>,
            val to: TS,
            val effect: SE? = null,
            val trigger: TE? = null,
        ) : Transition<E>()

        /**
         * An invalid transition indicates that from state [from] when event [on] is fired, there's no state to go
         * or side effect to trigger.
         */
        data class Invalid<FS, E>(
            val from: FS,
            override val on: E,
        ) : Transition<E>()

        /**
         * Internal transitions will be used to build the FSM transitions.
         */
        data class Internal<E, TS, SE, TE>(
            override val on: E,
            val run: List<() -> Unit>,
            val to: TS,
            val effect: SE? = null,
            val trigger: TE? = null,
        ) : Transition<E>()
    }

    /**
     * Scope to add an initial state to ensure that the FSM with not be created without it.
     */
    open inner class InitialStateBuilder internal constructor(open val name: String) {
        /**
         * Receives an initial [state] and all the [build] scope that will build next FSM elements. Must return a StateMachine
         * object so that the build machine operation might be type safe.
         */
        fun <S : State> initialState(
            state: S,
            build: FinalStateBuilder.() -> StateMachine<State, Event, SideEffect>
        ): StateMachine<State, Event, SideEffect> {
            this@StateMachine.name = name
            initialState = state
            currentStateRef.set(state)

            return build(FinalStateBuilder())
        }
    }

    /**
     * Scope to add a final state to ensure that the FSM with not be created without it.
     */
    open inner class FinalStateBuilder internal constructor() : InitialStateBuilder(name)
    {
        /**
         * Receives a final [state] and all the [build] scope that will build next FSM elements. Must return a StateMachine
         * object so that the build machine operation might be type safe.
         */
        fun <S : State> finalState(
            state: S,
            build: StatesBuilder.() -> StateMachine<State, Event, SideEffect>
        ): StateMachine<State, Event, SideEffect> {
            finalState = state

            return build(StatesBuilder())
        }
    }

    /**
     * Scope used to build all states and a callback to when any transition is done.
     */
    open inner class StatesBuilder internal constructor() : FinalStateBuilder()
    {
        /**
         * Adds the [state] to the FSM using the provided [build] scope to build the transitions in a type safe way.
         */
        fun state(state: State, build: OnEventBuilder.() -> Transition.Internal<Event, State, SideEffect, Event>) {
            onTransitionsMap.getOrPut(state) { mutableListOf() }.add(build(OnEventBuilder()))
        }

        /**
         * Adds the [execute] callback when any valid transition is completed.
         */
        fun onTransition(
            execute: (from: State, on: Event, to: State, effect: SideEffect) -> Unit
        ): StateMachine<State, Event, SideEffect> {
            onTransition = execute

            return this@StateMachine
        }
    }

    /**
     * Scope used to build all transitions when determined event is fired when the outer state is active.
     */
    inner class OnEventBuilder {

        /**
         * Stated that if the outer event is active, when the [event] is fired a callback might be executed, and
         * some transition must be configured.
         */
        fun on(
            event: Event,
            build: TransitionBuilder.() -> Transition.Internal<Event, State, SideEffect, Event>
        ): Transition.Internal<Event, State, SideEffect, Event> {
            return build(TransitionBuilder(event))
        }
    }

    /**
     * Scope used to build transitions callbacks and ensure that all states will be added with a state to go to,
     * and a side effect to trigger.
     */
    inner class TransitionBuilder(private val on: Event){
        private var executionList: MutableList<() -> Unit> = mutableListOf()

        /**
         * Adds a callback to be executed when the event [on] is fired. Multiple callbacks might be added, and they will
         * be executed at the added order, one at a time.
         */
        fun execute(callback: () -> Unit){ executionList.add(callback) }

        /**
         * Sets up a state [to] to FSM go to after the callbacks are executed. The side effect [effect], if not null,
         * will also be sent to [onTransition] callback. If [trigger] event is not null, it will be triggered after
         * [onTransition] callback execution.
         */
        fun transitTo(to: State, effect: SideEffect? = null, trigger: Event? = null) =
            Transition.Internal(on, this.executionList.toList(), to, effect, trigger)
    }

    /**
     *  FSM internal initializer.
     */
    private fun <S : State, E : Event, SE : SideEffect> initialize(
        name: String,
        builder: InitialStateBuilder.() -> StateMachine<S, E, SE>
    ) = builder(InitialStateBuilder(name))

    /**
     * Triggers the event [event] on the FSM.
     */
    @Suppress("UNCHECKED_CAST")
    fun trigger(event: Event): Transition<Event> =
        findTransition(currentStateRef.get(), event).let { transition ->
            when(transition) {
                is Transition.Internal<*, *, *, *> ->
                    throw IllegalStateException("Transition could not be internal at this point")
                is Transition.Invalid<*, *> ->
                    logger.warning(
                        "No transition found for state ${currentStateRef.get()::class.simpleName} on event ${transition.on::class.simpleName}"
                    )
                is Transition.Valid<*, *, *, *, *> -> {
                    logger.info("Event ${event::class.simpleName} fired!")

                    logger.info("Running on event blocks...")
                    transition.run.forEach { it() }

                    logger.info("Transiting to ${currentStateRef.get()::class.simpleName} -> ${transition.to!!::class.simpleName}")
                    currentStateRef.set(transition.to as State)

                    transition.effect?.let { sideEffect ->
                        logger.info("Triggering side effect ${sideEffect::class.simpleName}...")
                        onTransition(currentStateRef.get(), event, transition.to, (sideEffect as SideEffect))
                        logger.info("Side effect ${transition.effect::class.simpleName} finished!")
                    }

                    if (currentStateRef.get() == finalState){
                        finish()
                    } else {
                        transition.trigger?.let { event -> trigger(event as Event) }
                    }
                }
            }

            transition
        }

    /**
     * Finished the FSM and returns to initial state
     */
    private fun finish() {
        logger.info("Finishing machine!")
        currentStateRef.set(initialState)
    }

    /**
     * Looks for a transition with a current event [from] when the event [event] is triggered.
     */
    private fun findTransition(from: State, event: Event): Transition<Event> =
        onTransitionsMap.getOrElse(from) { listOf() }.firstOrNull { it.on == event }?.let {
            Transition.Valid(currentStateRef, event, it.run, it.to, it.effect, it.trigger)
        } ?: Transition.Invalid(currentStateRef, event)

    companion object {
        private const val TAG = "StateMachine"
        private val logger = Logger.getLogger(TAG)

        /**
         * Builds an FSM with determined [name].
         */
        fun <S : Any, E : Any, SE : Any> build(
            name: String,
            init: StateMachine<S, E, SE>.InitialStateBuilder.() -> StateMachine<S, E, SE>
        ) = StateMachine<S, E, SE>().initialize(name, init)
    }
}