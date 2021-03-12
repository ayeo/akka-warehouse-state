package pl.ayeo.typed

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityRef, EntityTypeKey}
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import pl.ayeo.typed.ItemAddLocationActor.Run
import pl.ayeo.typed.WarehouseActor.LocationConfirmation
import spray.json.DefaultJsonProtocol

trait ItemModelJsonProtocol extends DefaultJsonProtocol {
  implicit val itemFormat = jsonFormat3(Item)
}

case class Item(sku: SKU, totals: Quantity, locations: Map[Location, Quantity] = Map())

object ItemActor {
  sealed trait Command
  case class Get(replyTo: ActorRef[Option[Item]]) extends Command
  case class StockIncrease(location: Location, quantity: Quantity, replyTo: ActorRef[Event]) extends Command
  case class AddLocation(location: Location, quantity: Quantity, replyTo: ActorRef[Event]) extends Command

  sealed trait Event
  case class InvalidLocation(location: Location) extends Event
  case class LocationAdded(location: Location) extends Event
  case class StockUpdated(location: Location) extends Event

  val name = "Barbarian"
  val TypeKey = EntityTypeKey[ItemActor.Command](name)

  final case class State(sku: SKU, warehouseID: WarehouseID, locations: List[Location] = Nil, counter: Int = 0) {
    def increase(amount: Int = 1): State = State(sku, warehouseID, locations, counter + amount)

    def addLocation(location: Location): State = State(sku, warehouseID, location :: locations, counter)
  }

  val commandHandler: (ActorRef[WarehouseActor.Command], ActorContext[Command]) => (State, Command) => Effect[Event, State] = {
    (warehouse, context) =>
      (state, command) =>

        command match {
          case Get(replyTo) =>
            replyTo ! Some(Item("bober", 12))
            Effect.none
          case StockIncrease(location, quantity, replyTo) => {
            if (state.locations.contains(location)) {
              println("Kozy kurwa srajo!")
              replyTo ! StockUpdated(location)
            } else {
              context.spawn(ItemAddLocationActor(location, quantity, warehouse, context.self, replyTo), "KK")
            }

            Effect.none
          }
          case AddLocation(location, quantity, replyTo) => {
            context.log.info("Location added")

            val added = LocationAdded(location)
            Effect.persist(added).thenRun(newState => {
              replyTo ! added
            })
          }
        }
  }

  val eventHandler: ActorContext[Command] => (State, Event) => State = {
    context => {
      (state, event) => event match {
        case LocationAdded(location) => state.addLocation(location)
      }
    }
  }

  def apply(entityContext: EntityContext[Command]): Behavior[Command] =
    Behaviors.setup { context =>
      val split = entityContext.entityId.split("\\@")
      val warehouseID: WarehouseID = split(0)
      val sku: SKU = split(1)

      val warehouse = context.spawn(WarehouseActor.commandHandler, "Warehius222t")

      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
        emptyState = State(sku, warehouseID),
        commandHandler = commandHandler(warehouse, context),
        eventHandler = eventHandler(context)
      )
    }

  def init(implicit sharding: ClusterSharding): Unit = //todo: make sure it is used
    sharding.init(Entity(ItemActor.TypeKey)(entityContext => ItemActor(entityContext)))

  def entityRef(warehouseID: WarehouseID, sku: String)(implicit sharding: ClusterSharding): EntityRef[Command] = {
    val entityID = s"$warehouseID@$sku"
    sharding.entityRefFor(TypeKey, entityID)
  }
}
