import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger

typealias InitialStateScope<S, E, SE, C> = StateMachine<S, E, SE, C>.InitialStateBuilder.() -> StateMachine<S, E, SE, C>
typealias FinalStateScope<S, E, SE, C> = StateMachine<S, E, SE, C>.FinalStateBuilder.() -> StateMachine<S, E, SE, C>
typealias StateScope<S, E, SE, C> = StateMachine<S, E, SE, C>.StatesBuilder.() -> StateMachine<S, E, SE, C>
typealias EventScope<S, E, SE, C> = StateMachine<S, E, SE, C>.OnEventBuilder.() -> StateMachine.Transition.Valid<S, E, SE, C>
typealias TransitionScope<S, E, SE, C> = StateMachine<S, E, SE, C>.TransitionBuilder.() -> StateMachine.Transition.Valid<S, E, SE, C>
typealias ExecutionScope<S, E, SE, C> = StateMachine<S, E, SE, C>.ExecutionBuilder.(C) -> Unit
typealias OnTransition<S, E, SE, C> = (from: S, on: E, to: S, effect: SE, context: C) -> Unit

// TODO: Use DSLMarker to implement type inference
@DslMarker
annotation class StateMachineMarker

/**
 * A type safe Finite State Machine that might be used to manage processes. The [State] type represents a super type
 * or an interface used to represent the states. All states must be inherited from [State]. The same goes to
 * [Event] and [SideEffect].
 */
class StateMachine<State : Any, Event : Any, SideEffect : Any, Context : Any> private constructor(){
    lateinit var name: String
    private lateinit var initialState: State
    private lateinit var finalState: State
    private lateinit var context: Context
    private lateinit var onTransition: OnTransition<State, Event, SideEffect, Context>
    private val currentStateRef: AtomicReference<State> = AtomicReference()
    private var onException: (context: Context, e: Exception) -> Unit = { _, _ -> }
    private val callbacks: LinkedHashMap<State?, MutableList<Transition.Valid<State, Event, SideEffect, Context>>> =
        linkedMapOf()

    /**
     * Object that maps the transitions on FSM
     */
    sealed class Transition<S : Any, E : Any> {
        /**
         * Valid transition that will change the FSM from current State on event [on] to [to] state
         * and trigger a side effect [effect]. All those parameters will be passed to [onTransition] callback.
         */
        data class Valid<S : Any, E : Any, SE : Any, C : Any>(
            val exceptions: Set<S> = setOf(),
            val on: Set<E>,
            val run: List<ExecutionScope<S, E, SE, C>>,
            val handlers: List<EventHandler<S, E, SE, C>> = listOf(),
            val to: S,
            val effect: SE? = null,
        ) : Transition<S, E>()

        /**
         * An invalid transition indicates that from state [from] when event [on] is fired, there's no state to go
         * or side effect to trigger.
         */
        data class Invalid<S : Any, E : Any>(
            val from: S,
            val on: E,
        ) : Transition<S, E>()
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
            build: FinalStateScope<State, Event, SideEffect, Context>
        ): StateMachine<State, Event, SideEffect, Context> {
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
            build: StateScope<State, Event, SideEffect, Context>
        ): StateMachine<State, Event, SideEffect, Context> {
            finalState = state

            return build(StatesBuilder())
        }
    }

    /**
     * Scope used to build all states and a callback to when any transition is done.
     */
    open inner class StatesBuilder internal constructor() : FinalStateBuilder()
    {
        fun context(builder: () -> Context) { context = builder() }

        /**
         * Adds the [state] to the FSM using the provided [build] scope to build the transitions in a type safe way.
         */
        fun from(state: State, build: EventScope<State, Event, SideEffect, Context>) {
            callbacks.getOrPut(state) { mutableListOf() }.add(build(OnEventBuilder()))
        }

        fun fromAll(vararg exceptions: State, builder: OnEventBuilder.() -> Transition.Valid<State, Event, SideEffect, Context>) {
            callbacks.getOrPut(null) { mutableListOf() }.add(
                builder(OnEventBuilder(exceptions.toSet()))
            )
        }

        /**
         * This lambda will be executed to treat caught during handlers execution.
         */
        fun onException(handler: (Context, Exception) -> Unit) {
            onException = handler
        }

        /**
         * Adds the [execute] callback when any valid transition is completed.
         */
        fun onTransition(
            execute: OnTransition<State, Event, SideEffect, Context>
        ): StateMachine<State, Event, SideEffect, Context> {
            onTransition = execute

            return this@StateMachine
        }
    }

    /**
     * Scope used to build all transitions when determined event is fired when the outer state is active.
     */
    inner class OnEventBuilder(private val exceptions: Set<State> = setOf()) {
        /**
         * Stated that if the outer event is active, when any of the [events] is fired a callback might be executed, and
         * some transition must be configured.
         */
        fun on(
            vararg events: Event,
            build: TransitionScope<State, Event, SideEffect, Context>
        ): Transition.Valid<State, Event, SideEffect, Context> {
            return build(TransitionBuilder(exceptions, events.toSet()))
        }
    }

    /**
     * Scope used to build transitions callbacks and ensure that all states will be added with a state to go to,
     * and a side effect to trigger.
     */
    inner class TransitionBuilder(
        private val exceptions: Set<State> = setOf(),
        private val on: Set<Event>
    ){
        private val executions: MutableList<ExecutionBuilder.(Context) -> Unit> = mutableListOf()
        private val handlers = mutableListOf<EventHandler<State, Event, SideEffect, Context>>()

        /**
         * Execute an anonymous [callback] after some transition is done.
         */
        fun execute(callback: ExecutionBuilder.(Context) -> Unit) { executions.add(callback) }

        /**
         * Adds [handler] to [on] event.
         */
        fun handler(handler: EventHandler<State, Event, SideEffect, Context>){ handlers.add(handler) }

        /**
         * Sets up a state [to] to FSM go to after the callbacks are executed. The side effect [effect], if not null,
         * will also be sent to [onTransition] callback.
         */
        fun transitTo(to: State, effect: SideEffect? = null): Transition.Valid<State, Event, SideEffect, Context> =
            Transition.Valid(exceptions, on, executions, handlers, to, effect)
    }

    /**
     * Execution scope to allow FSM call [trigger] function only.
     */
    inner class ExecutionBuilder {
        /**
         * Triggers an [event] inside the outer state machine.
         */
        fun trigger(event: Event) {
            this@StateMachine.trigger(event)
        }
    }

    /**
     *  FSM internal initializer.
     */
    private fun <S : State, E : Event, SE : SideEffect, C : Context> initialize(
        name: String,
        builder: InitialStateBuilder.() -> StateMachine<S, E, SE, C>
    ) = builder(InitialStateBuilder(name))

    /**
     * Triggers the event [event] on the FSM.
     */
    fun trigger(event: Event): Transition<State, Event> =
        findTransition(currentStateRef.get(), event)?.let { transition ->
            logger.info("Event ${event::class.simpleName} fired!")

            logger.info("Running on event blocks...")
            transition.run.forEach {
                try {
                    it(ExecutionBuilder(), context)
                } catch (e: Exception) {
                    onException(context, e)
                }
            }

            transition.handlers.forEach { handler ->
                logger.info("Calling handler ${handler::class.simpleName}")

                try {
                    handler.execute(ExecutionBuilder(), context)
                } catch (e: Exception) {
                    onException(context, e)
                }
            }

            logger.info(
                "Transiting to ${currentStateRef.get()::class.simpleName} -> ${transition.to::class.simpleName}"
            )

            currentStateRef.set(transition.to)

            transition.effect?.let { sideEffect ->
                logger.info("Triggering side effect ${sideEffect::class.simpleName}...")
                onTransition(currentStateRef.get(), event, transition.to, sideEffect, context)
                logger.info("Side effect ${transition.effect::class.simpleName} finished!")
            }

            if (currentStateRef.get() == finalState){
                finish()
            }

            transition
        } ?: run {
            logger.warning(
                "No transition found for state " +
                        "${currentStateRef.get()::class.simpleName} on event ${event::class.simpleName}"
            )

            Transition.Invalid(currentStateRef.get(), event)
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
    private fun findTransition(from: State, event: Event): Transition.Valid<State, Event, SideEffect, Context>? =
        callbacks.getOrElse(from) { listOf() }.firstOrNull { it.on == event }

    companion object {
        private const val TAG = "StateMachine"
        private val logger = Logger.getLogger(TAG)

        /**
         * Builds an FSM with determined [name].
         */
        fun <S : Any, E : Any, SE : Any, C : Any> build(
            name: String,
            init: InitialStateScope<S, E, SE, C>
        ) = StateMachine<S, E, SE, C>().initialize(name, init)
    }
}