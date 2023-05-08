import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger
/**
 * A type safe Finite State Machine that might be used to manage processes. The [S] type represents a super type
 * or an interface used to represent the states. All states must be inherited from [S]. The same goes to
 * [E] and [SE].
 */
@Suppress("UNUSED")
class StateMachine<S : Any, E : Any, SE : Any, C : Any> private constructor(
    val name: String,
    private val initialState: S
){
    private lateinit var context: C
    private lateinit var onTransition: (S, E, S, SE, C) -> Unit
    private var onException: (C, S, E, Exception) -> Unit = { _, _, _, _ -> }
    private val currentStateRef: AtomicReference<S> = AtomicReference()
    private var running: Boolean = false
    private var channel: Channel<E>? = null
    private val eventQueue: LinkedBlockingQueue<E> = LinkedBlockingQueue()
    private val transitions: LinkedHashMap<S?, MutableList<Transition.Valid<S, E, SE, C>>> =
        linkedMapOf()

    init {
        currentStateRef.set(initialState)
    }

    sealed class Action {
        object None : Action()
        object Finish : Action()
    }

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
            val handlers: List<EventHandler<S, E, SE, C>> = listOf(),
            val to: S,
            val effect: SE? = null,
            val action: Action = Action.None
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
     * Scope used to build all states and a callback to when any transition is done.
     */
    @BuilderDslMarker
    open inner class Builder {
        fun context(builder: () -> C) { context = builder() }

        /**
         * Scope used to build all transitions when determined event is fired when the outer state is active.
         */
        @BuilderDslMarker
        inner class OnEventScope(private val exceptions: Set<S> = setOf()) {
            /**
             * Stated that if the outer event is active, when any of the [events] is fired a callback might be executed, and
             * some transition must be configured.
             */
            fun on(
                vararg events: E,
                build: TransitionScope.() -> Transition.Valid<S, E, SE, C>
            ): Transition.Valid<S, E, SE, C> {
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
                fun execute(handler: Controller.(C) -> Unit) {
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
                fun transitTo(to: S, effect: SE? = null): Transition.Valid<S, E, SE, C> =
                    Transition.Valid(this@OnEventScope.exceptions, on, handlers, to, effect)

                fun finishOn(state: S, effect: SE? = null): Transition.Valid<S, E, SE, C> =
                    Transition.Valid(this@OnEventScope.exceptions, on, handlers, state, effect, Action.Finish)
            }
        }

        /**
         * Adds the [states] to the FSM using the provided [build] scope to build the transitions in a type safe way.
         */
        fun from(vararg states: S, build: OnEventScope.() -> Transition.Valid<S, E, SE, C>) {
            states.forEach { state ->
                transitions.getOrPut(state) { mutableListOf() }.add(build(OnEventScope()))
            }
        }

        fun fromAll(vararg exceptions: S, builder: OnEventScope.() -> Transition.Valid<S, E, SE, C>) {
            transitions.getOrPut(null) { mutableListOf() }.add(
                builder(OnEventScope(exceptions.toSet()))
            )
        }

        /**
         * Adds the [execute] callback when any valid transition is completed.
         */
        fun onTransition(
            execute: (S, E, S, SE, C) -> Unit
        ): StateMachine<S, E, SE, C> {
            onTransition = execute

            return this@StateMachine
        }

        fun onException(handler: (C, S, E, Exception) -> Unit) {
            onException = handler
        }
    }

    /**
     * Controller to send only selected commands to the state machine.
     */
    inner class Controller {
        /**
         * Triggers an [event] inside the outer state machine.
         */
        fun trigger(event: E) = runBlocking {
            channel?.send(event)
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
    private fun trigger(event: E): Transition<S, E> = runBlocking {
        findTransition(currentStateRef.get(), event)?.let { transition ->
            logger.info("Event ${event::class.simpleName} fired!")

            logger.info("Running on event blocks...")

            transition.handlers.forEach { handler ->
                logger.info("Calling handler ${handler::class.simpleName}")

                try {
                    handler.execute(Controller(), context)
                } catch (e: Exception) {
                    onException(context, currentStateRef.get(), event, e)
                }
            }

            logger.info(
                "Transiting to ${currentStateRef.get()::class.simpleName} -> ${transition.to::class.simpleName}"
            )

            currentStateRef.set(transition.to)

            transition.effect?.let { sideEffect ->
                logger.info("Triggering side effect $sideEffect...")
                onTransition(currentStateRef.get(), event, transition.to, sideEffect, context)
                logger.info("Side effect ${transition.effect} finished!")
            }

            if (transition.action is Action.Finish){
                finish()
            }

            transition
        } ?: run {
            logger.warning(
                "No transition found for state ${currentStateRef.get()} on event $event"
            )

            Transition.Invalid(currentStateRef.get(), event)
        }
    }

    fun start(event: E) = runBlocking {
        launch(Dispatchers.IO) {
            running = true
            channel.send(event)

            try {
                while(true) {
                    val result = channel.tryReceive()

                    if (result.isSuccess) {
                        result.getOrNull()?.let { trigger(it) }
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                logger.warning("Channel closed to receive events!")
            } catch (e: Exception) {
                logger.warning("Something when wrong on channel...")
                e.printStackTrace()
            } finally {
                logger.info("Machine stopped")
                running = false
            }
        }
    }

    /**
     * Finished the FSM and returns to initial state
     */
    private fun finish() {
        logger.info("Finishing machine...")
        currentStateRef.set(initialState)
    }

    /**
     * Looks for a transition with a current event [from] when the event [event] is triggered.
     */
    private fun findTransition(from: S, event: E): Transition.Valid<S, E, SE, C>? =
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

@DslMarker
annotation class BuilderDslMarker

