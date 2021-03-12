package pl.ayeo.warehouse

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityRef, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

object WarehouseActor {
  sealed trait Command
  case class RegisterLocation(location: Location, replyTo: ActorRef[Event]) extends Command
  case class ConfirmLocation(location: Location, replyTo: ActorRef[Event]) extends Command

  sealed trait Event
  case class LocationAdded(location: Location) extends Event
  case class LocationConfirmation(location: Location) extends Event
  case class UnknownLocation(location: Location) extends Event
  case class WrappedItemActorEvent(e: ItemActor.Event) extends Event

  final case class State(warehouseID: WarehouseID, locations: List[Location] = Nil) {
    def addLocation(location: Location): State = {
      val value = locations :+ location
      State(warehouseID, value)
    }
  }

  val commandHandler: (State, Command) => Effect[Event, State] = { (state, command) =>
    command match {
        case ConfirmLocation(location, replyTo) => {
          if (!location.contains(location)) replyTo ! UnknownLocation(location)
          else replyTo ! LocationConfirmation(location)
          Effect.none
        }
        case RegisterLocation(location: Location, replyTo) => {
          val event = LocationAdded(location)
          Effect.persist(event).thenRun(_ => replyTo ! event)
        }
    }
  }

  val eventHandler: (State, Event) => State = {
      (state, event) => { event match {
        case LocationAdded(location) => state.addLocation(location)
      }
    }
  }

  val name = "Warehouse"
  val TypeKey = EntityTypeKey[WarehouseActor.Command](name)

  def apply(sharding: ClusterSharding, entityContext: EntityContext[Command]): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
        emptyState = State(entityContext.entityId),
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
    }

  def init(implicit sharding: ClusterSharding): Unit = //todo: make sure it is used
    sharding.init(Entity(WarehouseActor.TypeKey)(entityContext => WarehouseActor(sharding, entityContext)))

  def entityRef(warehouseID: WarehouseID)(implicit sharding: ClusterSharding): EntityRef[WarehouseActor.Command] = {
    sharding.entityRefFor(TypeKey, warehouseID)
  }
}
