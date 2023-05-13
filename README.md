# Async type-safe Finite State Machine

This purpose of project is build a structure to be used as base to build Finite State 
Machines that its configuration is made using type safe methods to ensure that no step
will be skipped. My doing that we ensure an FSM will have initial, final and middle
states, all states will have an event to trigger a transition to this state and a 
callback will be configured to read a side effect on each transition.

## Usage example

### Setup states, events and side effects

```kotlin
interface State
interface Event
interface SideEffect

sealed class MyStates {
  object Initial : State
  object State1: State
  object Final: State

  override fun toString() = this::class.simpleName ?: super.toString()
}

sealed class MyEvents {
  object Start: Event
  object Complete: Event

  override fun toString() = this::class.simpleName ?: super.toString()
}

sealed class MySideEffects {
  object FinishedStep1 : SideEffect
  object Finished : SideEffect

  override fun toString() = this::class.simpleName ?: super.toString()
}
```

Create a context that runs across all state handlers executions

```kotlin
data class Context(
  val one: Int,
  val two: String,
) {
  var three: Long? = null
}
```

### Create and start an FSM

```kotlin
val stateMachine = StateMachine.build<State, Event, SideEffect, Context>(
    "test",
    MyStates.Initial
) {
    context {
        Context(1, "Example")
    }

    from(MyStates.Initial) {
        on(MyEvents.Start) {
            execute { context ->
                logger.info("I am running before this transition is made")

                context.three = 10L

                trigger(MyEvents.Complete)
            }

            goTo(MyStates.State1, MySideEffects.FinishedStep1)
        }
    }

    from(MyStates.State1) {
        on(MyEvents.Complete) {
            finishOn(MyStates.Final, MySideEffects.Finished)
        }
    }

    onException { context, state, event, exception ->
        logger.severe("Oops! Something went wrong during step $state on event $event...")
        exception.printStackTrace()
    }

    onTransition { _, _, _, effect: SideEffect, _ ->
        when(effect) {
            is MySideEffects.FinishedStep1 -> { logger.info("Step 1 finished") }
            is MySideEffects.Finished -> { logger.info("Did something before this machine finishes.") }
        }
    }
}

stateMachine.start(MyEvents.Event1)
```

## Next steps
- [X] Develop a way to trigger an event inside the `execute` scope
- [X] Create abstract `EventHandlers` to handle determined events
    - Might receive a generic typed object on handler start
    - Must validate current state to ensure that an operation might be executed
    - Must be able to fire different events from inside (passing events via `varargs`, maybe?)
- [X] Build unit tests of DSL and examples
- [X] Publish in Maven Central repository
- [X] Create GitHub deploy pipeline
- [ ] Use contracts to ensure in compilation time that FSM are
  - not empty
  - not missing final states

## References

- [StateMachine](https://github.com/Tinder/StateMachine) - Tinder
