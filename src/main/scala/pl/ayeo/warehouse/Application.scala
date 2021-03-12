//package pl.ayeo.typed
//
//import akka.actor.typed.ActorSystem
//import akka.actor.typed.scaladsl.Behaviors
//import akka.cluster.sharding.typed.scaladsl.ClusterSharding
//import akka.cluster.typed.Cluster
//import pl.ayeo.typed.ItemActor.Attack
//
//object Application extends App {
//    val system = ActorSystem[Any](Behaviors.empty, "ClusterSystem") //todo: get rid of name here
//    val cluster = Cluster(system)
//    implicit val sharding = ClusterSharding(system)
//
//    ItemActor.entityRef("1111") ! Attack
//}
