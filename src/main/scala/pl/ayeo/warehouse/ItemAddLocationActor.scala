package pl.ayeo.warehouse

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityRef
import pl.ayeo.warehouse.ItemActor.{AddLocation, InvalidLocation, LocationAdded, StockIncrease}
import pl.ayeo.warehouse.WarehouseActor.{ConfirmLocation, Event, LocationConfirmation, UnknownLocation, WrappedItemActorEvent}

object ItemAddLocationActor {
  def apply(
             location: Location,
             quantity: Quantity,
             warehouseRef: EntityRef[WarehouseActor.Command],
             originalItem: ActorRef[ItemActor.Command],
             requestSender: ActorRef[ItemActor.Event]
 ): Behavior[Event] = {
    Behaviors.setup { context =>
      val itemEventAdapter: ActorRef[ItemActor.Event] = context.messageAdapter(rsp => WrappedItemActorEvent(rsp))
      warehouseRef ! ConfirmLocation(location, context.self)

      def ready(quantity: Quantity): Behavior[Event] = Behaviors.receive {
        (context, message) =>
          message match {
            case LocationConfirmation(location) =>
              originalItem ! AddLocation(location, quantity, itemEventAdapter)
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
