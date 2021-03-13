//import akka.Done
//import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
//import akka.persistence.typed.PersistenceId
//import akka.actor.testkit.typed.scaladsl.LogCapturing
//import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
//import akka.pattern.StatusReply
//import org.scalatest.BeforeAndAfterEach
//import org.scalatest.wordspec.AnyWordSpecLike
//
//class AccountExampleDocSpec
//  extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config)
//    with AnyWordSpecLike
//    with BeforeAndAfterEach {
//    //with LogCapturing {
//
//  private val eventSourcedTestKit =
//    EventSourcedBehaviorTestKit[ItemActor.Command, AccountEntity.Event, AccountEntity.Account](
//      system,
//      AccountEntity("1", PersistenceId("Account", "1")))
//
//  override protected def beforeEach(): Unit = {
//    super.beforeEach()
//    eventSourcedTestKit.clear()
//  }
//
//  "Account" must {
//
//    "be created with zero balance" in {
//      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](AccountEntity.CreateAccount(_))
//      result.reply shouldBe StatusReply.Ack
//      result.event shouldBe AccountEntity.AccountCreated
//      result.stateOfType[AccountEntity.OpenedAccount].balance shouldBe 0
//    }
//
//  }
//}