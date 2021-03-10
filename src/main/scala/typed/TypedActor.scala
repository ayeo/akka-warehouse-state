package typed

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityRef, EntityTypeKey}
import akka.cluster.typed.Cluster
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.PersistenceId
import typed.MyPersistentBehavior.Attack
import akka.persistence.typed.scaladsl.Effect
import typed.RootActor.Message

object MyPersistentBehavior {

  sealed trait Command
  case object Attack extends Command

  sealed trait Event
  case class Attacked(count: Int) extends Event

  final case class State(counter: Int = 0) {
    def increase(amount: Int): State = State(counter + amount)
  }

  val commandHandler: (EntityContext[Command], ActorContext[Command]) => (State, Command) => Effect[Event, State] = { (ex, context) =>
    (state, command) =>
      command match {
        case Attack =>
          context.log.info(s"${ex.entityId}: Attack counter: ${state.counter}")
          Effect.persist(Attacked(state.counter + 1))
      }
  }

  val eventHandler: ActorContext[Command] => (State, Event) => State = { context =>
    (state, event) =>
      event match {
        case Attacked(count) => State(count)
      }
  }

  def apply(ex: EntityContext[Command]): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId("abc"),
        emptyState = State(),
        commandHandler = commandHandler(ex, context),
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

  val TypeKey = EntityTypeKey[MyPersistentBehavior.Command]("Vitcim")
  val shardRegion = sharding.init(Entity(TypeKey)(x => MyPersistentBehavior(x)))


  val a = sharding.entityRefFor(TypeKey, "bocian12-1")
  a ! Attack
}
