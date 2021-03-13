import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.{Cluster, Join}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import akka.persistence.testkit.scaladsl.{EventSourcedBehaviorTestKit, PersistenceTestKit}
import pl.ayeo.warehouse.ItemActor.AddStock
import pl.ayeo.warehouse.WarehouseActor.RegisterLocation
import pl.ayeo.warehouse.{ItemActor, WarehouseActor}

class ItemActorSpec
  extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config.withFallback(
    ConfigFactory.parseString(
      """
        | akka.actor.provider = "cluster"
      """.stripMargin
  )))
    with AnyWordSpecLike
    with BeforeAndAfterEach {

  val cluster = Cluster(testKit.system)
  cluster.manager ! Join(cluster.selfMember.address)
  implicit val sharding = ClusterSharding(testKit.system)
  WarehouseActor.init
  ItemActor.init

  val persistenceTestKit = PersistenceTestKit(testKit.system)

  override def beforeEach(): Unit = {
    persistenceTestKit.clearAll()
  }

  "Item" must {
    "reject adding stock to unknown location" in {
      val probe = testKit.createTestProbe[ItemActor.Event]()
      ItemActor.entityRef("23", "100-10") ! AddStock("LC-10", 10, probe.ref)
      probe.expectMessage(ItemActor.InvalidLocation("LC-10"))
    }

    "update stock on valid location" in {
      WarehouseActor.entityRef("23") ! RegisterLocation("LC-10", testKit.system.ignoreRef)

      val probe = testKit.createTestProbe[ItemActor.Event]()
      ItemActor.entityRef("23", "100-10") ! AddStock("LC-10", 10, probe.ref)
      probe.expectMessage(ItemActor.StockUpdated("LC-10", 10))
    }
  }
}