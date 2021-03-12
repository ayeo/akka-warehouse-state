package pl.ayeo.typed

import akka.actor.typed.{ActorRef, ActorSystem, DispatcherSelector}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.Cluster
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import pl.ayeo.typed.ItemActor.StockIncrease

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}


object HttpServer extends App
  with ItemModelJsonProtocol
  with SprayJsonSupport {

  implicit val system = ActorSystem(Behaviors.empty, "ClusterSystem") //todo: get rid of name here
  val cluster = Cluster(system)
  implicit val sharding = ClusterSharding(system)
  implicit var timeout = Timeout(2.seconds)
  implicit val executionContext = system.dispatchers.lookup(DispatcherSelector.fromConfig("my-dispatcher"))

  ItemActor.init //todo: get rid of this

  val item = ItemActor.entityRef("23", "Skunks")

  val du = item.ask(ref => StockIncrease("m2", 100, ref))
  du.onComplete{
    case Success(value) => println(value)
    case Failure(exception) => println(exception)
  }


//  val routes = {
//    path("item" / Segment) { sku: String =>
//        get {
//          val item = ItemActor.entityRef("23", sku)
//          val itemFuture = item ? ItemActor.Get
//          val i: Future[Option[Item]] = itemFuture.mapTo[Option[Item]]
//          val response: Future[ToResponseMarshallable] = i.map {
//            case Some(i: Item) => ToResponseMarshallable(i)
//            case None => HttpResponse(StatusCodes.NotFound)
//          }
//          complete(response)
//        }
//    }
//  }

  //Http().newServerAt("localhost", 8085).bind(routes)
}
