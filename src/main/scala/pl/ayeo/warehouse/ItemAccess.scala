package pl.ayeo.warehouse

import akka.actor.{Actor, ActorLogging}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.ExecutionContext

//abstraction for future cluster sharding
class ItemAccess(implicit val system: ExecutionContext, implicit var timeout: Timeout)
  extends Actor
    with ActorLogging {
  import ItemAccess._

  override def receive: Receive = {
    case Pass(sku, command) => {
      val item = context.actorOf(ItemActor.props(sku))
      val x = item ? command
      x.pipeTo(sender())
    }
  }
}

object ItemAccess {
  case class Pass(sku: String, command: Any)
}
