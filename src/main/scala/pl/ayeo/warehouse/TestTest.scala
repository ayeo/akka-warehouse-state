package pl.ayeo.warehouse

import akka.actor.typed.{ActorSystem, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

sealed trait Command
final case class Add(data: String) extends Command
case object Clear extends Command

sealed trait Event
final case class Added(data: String) extends Event
case object Cleared extends Event

final case class State(history: List[String] = Nil)


object TestTest extends App {

  val commandHandler: (State, Command) => Effect[Event, State] = { (state, command) =>
    command match {
      case Add(data) => Effect.persist(Added(data))
      case Clear     => Effect.persist(Cleared)
    }
  }

  val eventHandler: (State, Event) => State = { (state, event) =>
    event match {
      case Added(data) => {
        println("Klops")
        state.copy((data :: state.history).take(5))
      }
      case Cleared     => State(Nil)
    }
  }

  def kkk(id: String): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = State(Nil),
      commandHandler = commandHandler,
      eventHandler = eventHandler)


  val item = ActorSystem(kkk("3343"), "Kulfon")
  item ! Add("Bober")

}
