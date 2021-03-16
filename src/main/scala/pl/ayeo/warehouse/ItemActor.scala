package pl.ayeo.warehouse

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityRef, EntityTypeKey}
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import pl.ayeo.warehouse.WarehouseActor.{ConfirmLocation, Event, LocationConfirmation, UnknownLocation}
import spray.json.DefaultJsonProtocol

trait ItemModelJsonProtocol extends DefaultJsonProtocol {
  implicit val itemFormat = jsonFormat4(Item)
}

case class Item(warehouseID: WarehouseID, sku: SKU, totals: Quantity, locations: Map[Location, Quantity] = Map())

object ItemActor {

  sealed trait Command
  case class Get(replyTo: ActorRef[Option[Item]]) extends Command
  case class AddStock(location: Location, quantity: Quantity, replyTo: ActorRef[Event]) extends Command
  private case class AddLocation(location: Location, replyTo: ActorRef[Event]) extends Command

  sealed trait Event
  case class InvalidLocation(location: Location) extends Event
  private case class LocationAdded(location: Location) extends Event
  case class StockUpdated(location: Location, quantity: Quantity) extends Event

  val name = "WarehouseItem"
  val TypeKey = EntityTypeKey[ItemActor.Command](name)

  sealed trait State {
    def addLocation(location: Location): State
    def addStock(location: Location, quantity: Quantity): State

    def applyCommand(
      warehouse: EntityRef[WarehouseActor.Command],
      context: ActorContext[Command],
      command: Command
    ): Effect[Event, State]
  }

  final case class Uninitialized(sku: SKU, warehouseID: WarehouseID) extends State {
    override def addLocation(location: Location): State = this
    override def addStock(location: Location, quantity: Quantity): State = this

    override def applyCommand(
     warehouse: EntityRef[WarehouseActor.Command],
     context: ActorContext[Command],
     command: Command
   ): Effect[Event, State] = Effect.none
  }

  final case class Initialized(
    sku: SKU,
    warehouseID: WarehouseID,
    locations: scala.collection.mutable.Map[Location, Quantity] = scala.collection.mutable.Map(),
    counter: Int = 0
  ) extends State {
    def increase(amount: Int = 1): Initialized = Initialized(sku, warehouseID, locations, counter + amount)

    def addLocation(location: Location): State = {
      Initialized(sku, warehouseID, locations + (location -> 0), counter)
    }

    def addStock(location: Location, quantity: Quantity): State = {
      locations(location) = locations(location) + quantity
      Initialized(sku, warehouseID, locations, counter)
    }

    override def applyCommand(
     warehouse: EntityRef[WarehouseActor.Command],
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
    }
  }

  val eventHandler: (State, Event) => State = {
    (state, event) =>
      println("kutas")
        event match {
          case LocationAdded(location) => state.addLocation(location)
          case StockUpdated(location, quantity) => state.addStock(location, quantity)
        }
  }

  def apply(sharding: ClusterSharding, entityContext: EntityContext[Command]): Behavior[Command] =
    Behaviors.setup { context =>
      val split = entityContext.entityId.split("\\@")
      val warehouseID: WarehouseID = split(0)
      val sku: SKU = split(1)

      val warehouse = WarehouseActor.entityRef(warehouseID)(sharding)

      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
        emptyState = Uninitialized(sku, warehouseID),
        (state, cmd) => state.applyCommand(warehouse, context, cmd),
        eventHandler = eventHandler
      )
    }

  def init(implicit sharding: ClusterSharding): Unit = //todo: make sure it is used
    sharding.init(Entity(ItemActor.TypeKey)(entityContext => ItemActor(sharding, entityContext)))

  def entityRef(warehouseID: WarehouseID, sku: String)(implicit sharding: ClusterSharding): EntityRef[Command] = {
    val entityID = s"$warehouseID@$sku"
    sharding.entityRefFor(TypeKey, entityID)
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
