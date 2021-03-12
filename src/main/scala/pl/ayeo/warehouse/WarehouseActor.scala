package pl.ayeo.warehouse

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object WarehouseActor {
  sealed trait Command
  case class RegisterLocation(SKU: SKU) extends Command
  case class ConfirmLocation(location: Location, replyTo: ActorRef[Event]) extends Command

  sealed trait Event
  case class LocationConfirmation(location: Location) extends Event
  case class UnknownLocation(location: Location) extends Event

  case class WrappedItemActorEvent(e: ItemActor.Event) extends Event

  final case class State(warehouseID: WarehouseID, locations: List[Location] = Nil)

  val commandHandler: Behavior[Command] = Behaviors.receive { (context, command) =>
        command match {
          case ConfirmLocation(location, replyTo) => {
            context.log.info(s"[called] WarehouseActor::ConfirmLocation $location")
            replyTo ! LocationConfirmation(location)
            Behaviors.same
          }
        }
    }

}
