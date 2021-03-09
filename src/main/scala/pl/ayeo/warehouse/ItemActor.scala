package pl.ayeo.warehouse

import akka.actor.{Actor, ActorLogging, Props}
import akka.persistence.PersistentActor
import pl.ayeo.warehouse.ItemActor.{Create, Item}
import spray.json.DefaultJsonProtocol

trait ItemModelJsonProtocol extends DefaultJsonProtocol {
  implicit val itemFormat = jsonFormat2(Item)
}

class ItemActor(val sku: String) extends Actor with ActorLogging with PersistentActor
{
  import ItemActor._

  var locations: scala.collection.mutable.Map[Location, Quantity] = scala.collection.mutable.Map()

  override def persistenceId: String = sku

  override def receiveRecover: Receive = {
    case ItemCreated(sku) => context.become(initialized)
    case StockAdded(location, quantity) => {
      if (locations.contains(location)) { //todo: ugly
        locations(location) += quantity
      } else {
        locations(location) = quantity
      }
    }
  }

  override def receiveCommand: Receive = uninitialized

  def uninitialized: Receive = {
    case Create(sku) => {
      val replyTo = sender()
      persist(ItemCreated(sku)) { event =>
        context.become(initialized)
        replyTo ! ItemCreated(event.sku)
      }
    }
    case a @ _ => {
      log.info(s"Item actor not initialized $a")
      sender() ! None
    }
  }

  def initialized: Receive = {
    case GetItem => sender() ! Some(Item(sku, locations.foldLeft(0) { case (a, (k, v)) => a + v }))
    case Create => {
      log.info("Item actor created")
      sender() ! ItemCreatingFailed("Already exists")
    }
    case AddStock(location, quantity) => {
      if (locations.contains(location)) {
        locations(location) = locations(location) + quantity
        val replyTo = sender()
        persist(StockAdded(location, quantity)) { event =>
          replyTo ! event
        }
      } else {
        sender() ! UnknownLocation(location)
      }
    }
    case RegisterLocation(location: Location) => {
      //todo: check if exists
      locations(location) = 0
      sender() ! LocationRegistered(location)
    }
  }
}

object ItemActor
{
  case object GetItem
  case class Create(sku: String)
  case class AddStock(location: Location, quantity: Quantity)
  case class RegisterLocation(location: Location)

  case class Item(sku: SKU, quantity: Quantity)

  case class ItemCreated(sku: String)
  case class ItemCreatingFailed(message: String)
  case class UnknownLocation(location: Location)
  case class StockAdded(location: Location, quantity: Quantity)
  case class LocationRegistered(location: Location)


  def props(sku: String): Props = Props(new ItemActor(sku))
}
