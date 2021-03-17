package pl.ayeo.warehouse

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.persistence.typed.scaladsl.Effect
import pl.ayeo.warehouse.ItemActor._
import pl.ayeo.warehouse.WarehouseActor.{ConfirmLocation, LocationConfirmation, UnknownLocation}

sealed trait State {
  def applyCommand(
    implicit clusterSharding: ClusterSharding,
    context: ActorContext[Command],
    command: Command
  ): Effect[Event, State]

  def applyEvent(event: Event): State
}

final case object Uninitialized extends State {
  override def applyCommand(
     implicit clusterSharding: ClusterSharding,
     context: ActorContext[Command],
     command: Command
   ): Effect[Event, State] = command match {
    case Create(warehouseID, sku, replyTo) =>
      val event = Created(warehouseID, sku)
      Effect.persist(event).thenReply(replyTo)(_ => event)
    case AddStock(_, _, replyTo) => {
      replyTo ! NotInitialized
      Effect.none
    }
    case _ => Effect.unhandled.thenNoReply()
  }

  def applyEvent(event: Event): State = event match {
        case Created(warehouseID, sku) => Initialized(sku, warehouseID)
      }
}

final case class Initialized(
  sku: SKU,
  warehouseID: WarehouseID,
  locations: scala.collection.mutable.Map[Location, Quantity] = scala.collection.mutable.Map(), //todo: make me immutable
  counter: Int = 0
) extends State {
  def addLocation(location: Location): State = Initialized(sku, warehouseID, locations + (location -> 0), counter)
  def addStock(location: Location, quantity: Quantity): State = {
    locations(location) = locations(location) + quantity
    Initialized(sku, warehouseID, locations, counter)
  }

  override def applyEvent(event: Event): State = event match {
      case LocationAdded(location) => addLocation(location)
      case StockUpdated(location, quantity) => addStock(location, quantity)
    }

  override def applyCommand(
   implicit clusterSharding: ClusterSharding, //todo: ugly sharding - hard to test
   context: ActorContext[Command],
   command: Command
 ): Effect[Event, State] = command match {
    case Get(replyTo) =>
      replyTo ! Some(Item(warehouseID, sku, 12, locations.toMap))
      Effect.none
    case AddStock(location, quantity, replyTo) => {
      context.log.info("Add stock ")
      if (locations.contains(location)) {
        val event = StockUpdated(location, quantity);
        Effect.persist(event).thenRun(state => {
          replyTo ! event
        })
      } else {
        val warehouse = WarehouseActor.entityRef(warehouseID)
        context.spawn(addLocationProcess(location, quantity, warehouse, context.self, replyTo), "IAL")
        Effect.none
      }
    }
    case AddLocation(location, replyTo) => {
      val added = LocationAdded(location)
      Effect.persist(added).thenRun(_ => {
        replyTo ! added
      })
    }
    case Create(warehouseID, sku, replyTo: ActorRef[Event]) => {
      replyTo ! AlreadyInitialized(warehouseID, sku)
      Effect.none
    }
  }

  private def addLocationProcess(
    location: Location,
    quantity: Quantity,
    warehouseRef: EntityRef[WarehouseActor.Command],
    originalItem: ActorRef[ItemActor.Command],
    requestSender: ActorRef[ItemActor.Event]
  ): Behavior[Any] = {
    Behaviors.setup { context =>
      warehouseRef ! ConfirmLocation(location, context.self)
      def ready(quantity: Quantity): Behavior[Any] = Behaviors.receive {
        (context, message) =>
          message match {
            case LocationConfirmation(location) =>
              originalItem ! AddLocation(location, context.self)
              Behaviors.same
            case UnknownLocation(location: Location) =>
              requestSender ! InvalidLocation(location)
              Behaviors.stopped
            case LocationAdded(location) =>
              originalItem ! AddStock(location, quantity, requestSender)
              Behaviors.stopped
          }
      }

      ready(quantity)
    }
  }
}
