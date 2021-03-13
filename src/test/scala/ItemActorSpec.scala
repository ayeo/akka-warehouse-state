import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.Cluster
import akka.persistence.testkit.PersistenceTestKitPlugin
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import akka.persistence.testkit.scaladsl.{EventSourcedBehaviorTestKit, PersistenceTestKit}
import pl.ayeo.warehouse.ItemActor.{AddStock, StockUpdated}
import pl.ayeo.warehouse.WarehouseActor.RegisterLocation
import pl.ayeo.warehouse.{ItemActor, WarehouseActor}

class ItemActorSpec
  extends ScalaTestWithActorTestKit(PersistenceTestKitPlugin.config.withFallback(
    ConfigFactory.parseString(
      """
        |{
        | akka: {
        |   actor: {
        |     provider: cluster,
        |     allow-java-serialization:true
        |    }
        |  }
        |}
      """.stripMargin
  )))
    with AnyWordSpecLike
    with BeforeAndAfterEach {

  implicit val system2 = ActorSystem(Behaviors.empty, "ClusterSystem") //todo: get rid of name here
  //val cluster = Cluster(testKit.system)
  implicit val sharding = ClusterSharding(system2)
  WarehouseActor.init
  ItemActor.init

  val persistenceTestKit = PersistenceTestKit(system)

  override def beforeEach(): Unit = {
    persistenceTestKit.clearAll()
  }


  "Item" must {
    "test if work" in {
      val probe = testKit.createTestProbe[ItemActor.Event]()
      ItemActor.entityRef("23", "100-10") ! AddStock("LC-10", 10, probe.ref)
      probe.expectMessage(ItemActor.InvalidLocation("LC-10"))
    }

    "test if work 2" in {
      WarehouseActor.entityRef("23") ! RegisterLocation("LC-10", testKit.system.ignoreRef)

      val probe = testKit.createTestProbe[ItemActor.Event]()
      ItemActor.entityRef("23", "100-10") ! AddStock("LC-10", 10, probe.ref)
      probe.expectMessage(ItemActor.StockUpdated("LC-10", 10))
    }
  }
}