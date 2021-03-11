package pl.ayeo.typed

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import pl.ayeo.warehouse.ItemActor._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future


object HttpServer extends App
  //with ItemModelJsonProtocol
  with SprayJsonSupport {
  implicit var timeout = Timeout(2.seconds)
  implicit val system = ActorSystem("HighLevelExample")

  import system.dispatcher

  //val itemAccess = system.actorOf(Props(new ItemAccess()))

  val routes = {
    path("item" / Segment) { sku: String =>
        get {
//          val itemFuture = itemAccess ? ItemAccess.Pass(sku, ItemActor.GetItem)
//          val i: Future[Option[Item]] = itemFuture.mapTo[Option[Item]]
//          val response: Future[ToResponseMarshallable] = i.map {
//            case Some(i: Item) => ToResponseMarshallable(i)
//            case None => HttpResponse(StatusCodes.NotFound)
//          }
          val response = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Bober")
          complete(response)
        }
    }
  }

  Http().bindAndHandle(routes, "localhost", 8082)
}
