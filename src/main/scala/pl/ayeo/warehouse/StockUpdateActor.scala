package pl.ayeo.warehouse

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill}
import pl.ayeo.warehouse.ItemActor.{LocationRegistered, RegisterLocation, StockAdded, UnknownLocation}
import pl.ayeo.warehouse.StockUpdateActor._

class StockUpdateActor extends Actor with ActorLogging {
  def ready(originalSender: ActorRef, item: ActorRef, quantity: Quantity): Receive = {
    case UnknownLocation(location) => item ! RegisterLocation(location)
    case LocationRegistered(location) => item ! ItemActor.AddStock(location, quantity)
    case StockAdded(_, _) => {
      originalSender ! StockUpdated
      self ! PoisonPill
    }
    case None => {
      originalSender ! StockUpdateFailed
      self ! PoisonPill
    }
  }

  override def receive: Receive = {
    case AddStock(warehouseId, sku, quantity) => {
      val item = context.actorOf(ItemActor.props(sku))
      item ! ItemActor.AddStock("m2", quantity)
      context.become(ready(sender(), item, quantity))
    }
  }
}

object StockUpdateActor {
  case class AddStock(warehouseId: WarehouseID, sku: SKU, quantity: Quantity)
  case class RemoveStock(warehouseId: WarehouseID, sku: SKU, quantity: Quantity)

  case object StockUpdated
  case object StockUpdateFailed
}
