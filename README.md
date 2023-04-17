# Type safe configurable Finite State Machine

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
}

sealed class MyEvents {
    object Event1: Event
    object Event2: Event
}

sealed class MySideEffects {
    object Effect1 : SideEffect
    object Effect2 : SideEffect
}
```

### Create and start an FSM

```kotlin
val stateMachine = StateMachine.build<State, Event, SideEffect>("test") {
  initialState(MyStates.Initial) {
    finalState(MyStates.Final) {
      state(MyStates.Initial) {
        on(MyEvents.Event1) {
          execute {
            logger.info("I am running before this transition is made")
          }

          transitTo(MyStates.State1, MySideEffects.Effect1, MyEvents.Event2)
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
```

## TODOs

- Create abstract `EventHandlers` to handle determined events
  - Might receive a generic typed object on handler start
  - Must validate current state to ensure that an operation might be executed
  - Must be able to fire different events from inside (passing events via `varargs`, maybe?)
- Build a working test to show it's proof of concept
- Build unit tests DSL and examples
- Publish in Maven Central repository

## References

- [StateMachine](https://github.com/Tinder/StateMachine) - Tinder
