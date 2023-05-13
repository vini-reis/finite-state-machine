import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level
import java.util.logging.Logger

typealias TransitionCallback<S, E, SE, C> = (current: S, on: E, target: S, sideEffect: SE, context: C) -> Unit
typealias ExceptionCallback<C, S, E> = (context: C, state: S, on: E, exception: Exception) -> Unit
typealias TransitionsMap<S, E, SE, C> = LinkedHashMap<S?, MutableList<StateMachine.Transition<S, E, SE, C>>>
typealias StatesBuilder<S, E, SE, C> =
        StateMachine<S, E, SE, C>.Builder.OnEventScope.() -> StateMachine.Transition<S, E, SE, C>
typealias TransitionBuilder<S, E, SE, C> =
        StateMachine<S, E, SE, C>.Builder.OnEventScope.TransitionScope.() -> StateMachine.Transition<S, E, SE, C>

@DslMarker
annotation class BuilderDslMarker

/**
 * An async type safe Finite State Machine that might be used to manage processes. The [S] type represents a super type
 * or an interface used to represent the states. All states must be inherited from [S]. The same goes to
 * [E] and [SE]. It also uses a context of type [C] across all event handlers.
 */
@Suppress("UNUSED")
class StateMachine<S : Any, E : Any, SE : Any, C : Any> private constructor(
    val name: String,
    private val initialState: S
){
    private lateinit var context: C
    private lateinit var onTransition: TransitionCallback<S, E, SE, C>
    private val currentStateRef: AtomicReference<S> = AtomicReference()
    private var channel = Channel<E>(Channel.UNLIMITED)
    private val consumer = EventConsumer(channel)
    private var running: Boolean = false
    private var exceptionThrown = false
    private val eventQueue: LinkedBlockingQueue<E> = LinkedBlockingQueue()
    private val transitions: TransitionsMap<S, E, SE, C> = linkedMapOf()
    private var onException: ExceptionCallback<C, S, E> = { _, _, _, exception ->
        logger.log(Level.SEVERE, "State machine $name failed with no exception handlers", exception)
    }
    private val hasFinalState: Boolean
        get() = transitions.any { entry ->
            entry.value.any { transition -> transition.action == Action.Finish }
        }

    init {
        currentStateRef.set(initialState)
    }

    /**
     * This class denotes what the FSM has to do when some transition is made.
     */
    sealed class Action {
        object None : Action()
        object Finish : Action()
    }

    /**
     * Object that maps the transitions on FSM
     */
    data class Transition<S : Any, E : Any, SE : Any, C : Any>(
        val exceptions: Set<S> = setOf(),
        val on: Set<E>,
        val handlers: List<EventHandler<S, E, SE, C>> = listOf(),
        val to: S,
        val effect: SE? = null,
        val action: Action = Action.None
    )

    /**
     * StateMachine type-safe builder.
     */
    @BuilderDslMarker
    inner class Builder {
        /**
         * Scope used to build all transitions when determined event is fired when the outer state is active.
         */
        @BuilderDslMarker
        inner class OnEventScope(private val exceptions: Set<S> = setOf()) {
            /**
             * States that if the outer event is active, when any of the [events] is fired a callback might be
             * executed, and some transition must be configured.
             */
            fun on(
                vararg events: E,
                build: TransitionBuilder<S, E, SE, C>
            ): Transition<S, E, SE, C> {
                return build(TransitionScope(events.toSet()))
            }

            /**
             * Scope used to build transitions callbacks and ensure that all states will be added with a state to go to,
             * and a side effect to trigger.
             */
            @BuilderDslMarker
            inner class TransitionScope(private val on: Set<E>){
                private val handlers = mutableListOf<EventHandler<S, E, SE, C>>()

                /**
                 * Execute an anonymous [handler] after some transition is done.
                 */
                fun execute(handler: Controller.(C, S, E) -> Unit) {
                    handlers.add(EventHandler.build(handler))
                }

                /**
                 * Adds [handler] to [on] event.
                 */
                fun execute(handler: EventHandler<S, E, SE, C>){ handlers.add(handler) }

                /**
                 * Sets up a state [to] to FSM go to after the callbacks are executed. The side effect [effect], if not null,
                 * will also be sent to [onTransition] callback.
                 */
                fun goTo(to: S, effect: SE? = null): Transition<S, E, SE, C> =
                    Transition(this@OnEventScope.exceptions, on, handlers, to, effect)

                /**
                 * Sets up a state [to] as a final state. When the transition finishes the state machine will be
                 * finished.
                 */
                fun finishOn(state: S, effect: SE? = null): Transition<S, E, SE, C> =
                    Transition(this@OnEventScope.exceptions, on, handlers, state, effect, Action.Finish)
            }
        }

        /**
         * Adds the [states] to the FSM using the provided [build] scope to build the transitions in a type safe way.
         */
        fun from(vararg states: S, build: StatesBuilder<S, E, SE, C>) {
            states.forEach { state ->
                transitions.getOrPut(state) { mutableListOf() }.add(build(OnEventScope()))
            }
        }

        /**
         * Adds a transition from any state, except the [exceptions] events.
         */
        fun fromAll(vararg exceptions: S, builder: StatesBuilder<S, E, SE, C>) {
            transitions.getOrPut(null) { mutableListOf() }.add(
                builder(OnEventScope(exceptions.toSet()))
            )
        }

        /**
         * Adds the [execute] callback when any valid transition is completed.
         */
        fun onTransition(execute: TransitionCallback<S, E, SE, C>) {
            onTransition = execute
        }

        /**
         * An exception handler in case any exception is thrown during the event handler execution.
         */
        fun onException(handler: ExceptionCallback<C, S, E>) {
            onException = handler
        }

        /**
         * Finishes DSL build method and returns the build machine ensuring it's not empty.
         */
        fun build(): StateMachine<S, E, SE, C> {
            if(transitions.isEmpty()) throw IllegalArgumentException("No transitions found for FSM $name")
            if(!hasFinalState) throw IllegalStateException("No final state found for FSM $name")

            return this@StateMachine
        }
    }

    /**
     * Controller to send only selected commands to the state machine.
     */
    inner class Controller {
        /**
         * Triggers an [event] inside the outer state machine.
         */
        fun trigger(event: E) {
            logger.info("Enqueuing event $event...")
            eventQueue.add(event)
        }
    }

    /**
     *  FSM internal initializer.
     */
    private fun <S : Any, E : Any, SE : Any, C : Any> initialize(
        builder: Builder.() -> StateMachine<S, E, SE, C>
    ) = builder(Builder())

    /**
     * Triggers the event [event] on the FSM.
     */
    private suspend fun trigger(event: E) =
        findTransition(currentStateRef.get(), event)?.let { transition ->
            running = true
            logger.info("Event ${event::class.simpleName} fired!")

            logger.info("Running on event blocks...")

            run handling@ {
                transition.handlers.forEach { handler ->
                    logger.info("Start handler ${handler::class.simpleName ?: "anonymous"}")

                    try {
                        handler.execute(Controller(), context, currentStateRef.get(), event)
                    } catch (e: Exception) {
                        exceptionThrown = true
                        onException(context, currentStateRef.get(), event, e)

                        return@handling
                    }

                    logger.info("Finishing handler ${handler::class.simpleName ?: "anonymous"}")
                }
            }

            transition.effect?.let { sideEffect ->
                if (!exceptionThrown) {
                    logger.info("Triggering side effect $sideEffect...")
                    onTransition(currentStateRef.get(), event, transition.to, sideEffect, context)
                    logger.info("Side effect ${transition.effect} finished!")
                }
            }

            logger.info("Transiting to ${currentStateRef.get()} -> ${transition.to}")
            currentStateRef.set(transition.to)

            if (exceptionThrown || transition.action is Action.Finish){
                consumer.stop()
            } else {
                eventQueue.poll()?.let {
                    logger.info("Sending event $it...")
                    channel.send(it)
                }
            }

            running = false
        } ?: run {
            logger.warning("No transition found for state ${currentStateRef.get()} on event $event")
        }

    /**
     * Starts the execution of the state machine.
     */
    fun start(event: E, initialContext: C) = runBlocking {
        context = initialContext
        exceptionThrown = false

        launch(coroutineContext) {
            consumer.start(
                onReceive = { trigger(it) },
                onClose = { finish() }
            )
        }

        channel.send(event)
    }

    /**
     * Finished the FSM and returns to initial state
     */
    private fun finish() {
        logger.info("Finishing machine...")
        currentStateRef.set(initialState)
        consumer.stop()
    }

    /**
     * Resets the state machine from beginning.
     */
    fun reset(event: E, context: C) {
        finish()
        logger.info("Resetting state machine...")
        channel = Channel(Channel.UNLIMITED)
        consumer.reset(channel)
        start(event, context)
    }

    /**
     * Looks for a transition with a current event [from] when the event [event] is triggered.
     */
    private fun findTransition(from: S, event: E): Transition<S, E, SE, C>? =
        transitions.getOrElse(from) { listOf() }.firstOrNull { it.on.contains(event) }
            ?: transitions.getOrElse(null) { listOf() }
                .firstOrNull { it.on.contains(event) && !it.exceptions.contains(from) }

    companion object {
        private const val TAG = "StateMachine"
        private val logger = Logger.getLogger(TAG)

        /**
         * Builds an FSM with determined [name].
         */
        fun <S : Any, E : Any, SE : Any, C : Any> build(
            name: String,
            initialState: S,
            builder: StateMachine<S, E, SE, C>.Builder.() -> StateMachine<S, E, SE, C>
        ) = StateMachine<S, E, SE, C>(name, initialState).initialize(builder)
    }
}