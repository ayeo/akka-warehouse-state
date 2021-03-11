package pl.ayeo.typed

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityRef, EntityTypeKey}
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import spray.json.DefaultJsonProtocol

trait ItemModelJsonProtocol extends DefaultJsonProtocol {
  implicit val itemFormat = jsonFormat3(Item)
}

case class Item(sku: SKU, totals: Quantity, locations: Map[Location, Quantity] = Map())

object ItemActor {
  sealed trait Command
  case object Attack extends Command
  case class Get(replyTo: ActorRef[Option[Item]]) extends Command

  sealed trait Event
  case class Attacked(count: Int) extends Event

  val name = "Barbarian"
  val TypeKey = EntityTypeKey[ItemActor.Command](name)

  final case class State(counter: Int = 0) {
    def increase(amount: Int = 1): State = State(counter + amount)
  }

  val commandHandler: (ActorContext[Command]) => (State, Command) => Effect[Event, State] = { (context) =>
    (state, command) =>
      command match {
        case Attack =>
          context.log.info(s"Attack counter: ${state.counter}")
          Effect.persist(Attacked(state.increase().counter))
        case Get(replyTo) =>
          replyTo ! Some(Item("bober", 12))
          Effect.none
      }
  }

  val eventHandler: ActorContext[Command] => (State, Event) => State = { context =>
    (state, event) =>
      event match {
        case Attacked(count) => State(count)
      }
  }

  def apply(entityContext: EntityContext[Command]): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
        emptyState = State(),
        commandHandler = commandHandler(context),
        eventHandler = eventHandler(context)
      )
    }

  def init(implicit sharding: ClusterSharding): Unit = //todo: make sure it is used
    sharding.init(Entity(ItemActor.TypeKey)(entityContext => ItemActor(entityContext)))

  def entityRef(sku: String)(implicit sharding: ClusterSharding): EntityRef[Command] = {
    sharding.entityRefFor(TypeKey, sku)
  }

}

