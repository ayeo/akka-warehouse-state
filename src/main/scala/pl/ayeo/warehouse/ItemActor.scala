package pl.ayeo.warehouse

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.delivery.ShardingProducerController.EntityId
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityRef, EntityTypeKey}
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.PersistenceId
import spray.json.DefaultJsonProtocol

trait ItemModelJsonProtocol extends DefaultJsonProtocol {
  implicit val itemFormat = jsonFormat4(Item)
}

case class Item(warehouseID: WarehouseID, sku: SKU, totals: Quantity, locations: Map[Location, Quantity] = Map())

object ItemActor {
  sealed trait Command
  case class Create(warehouseID: WarehouseID, SKU: SKU, actorRef: ActorRef[Event]) extends Command
  case class Get(replyTo: ActorRef[Option[Item]]) extends Command
  case class AddStock(location: Location, quantity: Quantity, replyTo: ActorRef[Event]) extends Command
  case class AddLocation(location: Location, replyTo: ActorRef[Event]) extends Command //todo should be private

  sealed trait Event
  case class Created(warehouseID: WarehouseID, SKU: SKU) extends Event
  case class AlreadyInitialized(warehouseID: WarehouseID, SKU: SKU) extends Event
  case object NotInitialized extends Event
  case class InvalidLocation(location: Location) extends Event
  case class LocationAdded(location: Location) extends Event
  case class StockUpdated(location: Location, quantity: Quantity) extends Event

  val name = "WarehouseItem"
  val TypeKey = EntityTypeKey[ItemActor.Command](name)

  def apply(
    implicit warehouseAccess: WarehouseID => EntityRef[WarehouseActor.Command],
    entityContext: EntityContext[Command]
  ): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
        emptyState = Uninitialized,
        (state, cmd) => state.applyCommand(context, cmd),
        (state, event) => state.applyEvent(event)
      )
    }

  def init
    (warehouseAccess: WarehouseID => EntityRef[WarehouseActor.Command])
    (implicit sharding: ClusterSharding)
    : Unit =
      sharding.init(Entity(ItemActor.TypeKey)(entityContext => ItemActor(warehouseAccess, entityContext)))

  def entityRef(entityId: EntityId)(implicit sharding: ClusterSharding): EntityRef[Command] = {
    sharding.entityRefFor(TypeKey, entityId)
  }
}
