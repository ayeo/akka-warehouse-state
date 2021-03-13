//import akka.actor.AbstractActor.ActorContext
//import akka.actor.testkit.typed.scaladsl.{LogCapturing, ScalaTestWithActorTestKit}
//import akka.actor.typed.{ActorSystem, Behavior, Extension}
//import akka.actor.typed.scaladsl.Behaviors
//import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext}
//import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
//import akka.cluster.typed.Cluster
//import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
//import com.typesafe.config.ConfigFactory
//import org.scalatest.wordspec.AnyWordSpecLike
//import pl.ayeo.warehouse.{ItemActor, WarehouseActor}
//import pl.ayeo.warehouse.ItemActor.{AddStock, Command, TypeKey}
//
//
//class AccountExampleDocSpec
//  extends ScalaTestWithActorTestKit(ConfigFactory.parseString("{akka: { actor: {provider: cluster}}}"))
//    with AnyWordSpecLike
//    with BeforeAndAfterEach {
//
////  private def eventSourcedTestKit(): EventSourcedBehaviorTestKit[ItemActor.Command, ItemActor.Event, ItemActor.State] = {
////    val cluster = Cluster(testKit.system)
////    implicit val sharding = ClusterSharding(testKit.system)
////
////
////    val ref = sharding.init(Entity(ItemActor.TypeKey)(entityContext => ItemActor(sharding, entityContext)))
////    val warehouse = WarehouseActor.entityRef("23")
////    EventSourcedBehaviorTestKit[ItemActor.Command, ItemActor.Event, ItemActor.State](system, ItemActor.commandHandler(warehouse, testKit))
////  }
//
//
//  override protected def beforeEach(): Unit = {
//    super.beforeEach()
//    //eventSourcedTestKit.clear()
//  }
//
//  "Item" must {
//
//    val cluster = Cluster(testKit.system)
//    implicit val sharding = ClusterSharding(testKit.system)
//
//    "test if work" in {
//
//      ItemActor.init
//      ItemActor.entityRef("23", "234") ! AddStock("LC-10", 10, testKit.system.ignoreRef)
//    }
//  }
//}