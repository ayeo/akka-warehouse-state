package pl.ayeo.typed

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityRef, EntityTypeKey}
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect

object Barbarian {
  sealed trait Command
  case object Attack extends Command

  sealed trait Event
  case class Attacked(count: Int) extends Event

  val name = "Barbarian"
  val TypeKey = EntityTypeKey[Barbarian.Command]("Barbarian")

  final case class State(counter: Int = 0) {
    def increase(amount: Int = 1): State = State(counter + amount)
  }

  val commandHandler: (ActorContext[Command]) => (State, Command) => Effect[Event, State] = { (context) =>
    (state, command) =>
      command match {
        case Attack =>
          context.log.info(s"Attack counter: ${state.counter}")
          Effect.persist(Attacked(state.increase().counter))
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

  def entityRef(implicit sharding: ClusterSharding): EntityRef[Command] = {
    //todo: do not init twice
    sharding.init(Entity(Barbarian.TypeKey)(entityContext => Barbarian(entityContext)))
    sharding.entityRefFor(TypeKey, name)
  }

}

