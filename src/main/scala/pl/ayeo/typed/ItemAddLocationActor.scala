package pl.ayeo.typed

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import pl.ayeo.typed.ItemActor.{AddLocation, InvalidLocation, LocationAdded, StockIncrease}
import pl.ayeo.typed.WarehouseActor.{ConfirmLocation, Event, LocationConfirmation, UnknownLocation, WrappedItemActorEvent}

object ItemAddLocationActor {

  sealed trait Command

  final case object Run extends Command

  def apply(
   location: Location,
   quantity: Quantity,
   warehouseRef: ActorRef[WarehouseActor.Command],
   originalItem: ActorRef[ItemActor.Command],
   requestSender: ActorRef[ItemActor.Event]
 ): Behavior[Event] = {
    Behaviors.setup { context =>
      val backendResponseMapper: ActorRef[ItemActor.Event] = context.messageAdapter(rsp => WrappedItemActorEvent(rsp))
      warehouseRef ! ConfirmLocation(location, context.self)

      def ready(quantity: Quantity): Behavior[Event] = Behaviors.receive {
        (context, message) =>
          message match {
            case LocationConfirmation(location) =>
              originalItem ! AddLocation(location, quantity, backendResponseMapper)
              Behaviors.same
            case UnknownLocation(location: Location) =>
              requestSender ! InvalidLocation(location)
              Behaviors.stopped
            case wrapped: WrappedItemActorEvent => wrapped.e match {
              case LocationAdded(location) =>
                originalItem ! StockIncrease(location, quantity, requestSender)
                Behaviors.stopped
            }
          }
      }

      ready(quantity)
    }
  }
}
