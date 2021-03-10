package typed

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityRef, EntityTypeKey}
import akka.cluster.typed.Cluster
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.PersistenceId
import typed.Barbarian.Attack
import akka.persistence.typed.scaladsl.Effect
import typed.RootActor.Message

object Barbarian {
  sealed trait Command
  case object Attack extends Command

  sealed trait Event
  case class Attacked(count: Int) extends Event

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

}

object RootActor {
  sealed trait Message
  def apply(): Behavior[Message] = Behaviors.empty
}

object TypedActor extends App {
  val system = ActorSystem[Message](RootActor(), "ClusterSystem")
  val cluster = Cluster(system)
  val sharding = ClusterSharding(system)

  val TypeKey = EntityTypeKey[Barbarian.Command]("Barbarian")
  val shardRegion = sharding.init(Entity(TypeKey)(x => Barbarian(x)))


  val a = sharding.entityRefFor(TypeKey, "Gucio The Killa")
  a ! Attack
}
