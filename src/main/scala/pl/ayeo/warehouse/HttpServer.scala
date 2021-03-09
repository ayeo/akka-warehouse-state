package pl.ayeo.warehouse

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import pl.ayeo.warehouse.ItemActor._
import akka.pattern.ask
import akka.util.Timeout
import pl.ayeo.warehouse.StockUpdateActor.{StockUpdateFailed, StockUpdated}

import scala.concurrent.duration._
import scala.concurrent.Future


object HttpServer extends App
  with ItemModelJsonProtocol
  with SprayJsonSupport {
  implicit var timeout = Timeout(2.seconds)
  implicit val system = ActorSystem("HighLevelExample")

  import system.dispatcher

  val itemAccess = system.actorOf(Props(new ItemAccess()))

  val routes = {
    path("item" / Segment) { sku: String =>

      post {
        val resultFuture = itemAccess ? ItemAccess.Pass(sku, Create(sku))
        val y = resultFuture.map {
          case ItemCreated(sku) => HttpResponse(StatusCodes.OK)
          case ItemCreatingFailed(message) => HttpResponse(StatusCodes.BadRequest)
          case a@_ => {
            print(a)
            HttpResponse(StatusCodes.NotFound)
          }
        }
        complete(y)
      } ~
        get {
          val itemFuture = itemAccess ? ItemAccess.Pass(sku, ItemActor.GetItem)
          val i: Future[Option[Item]] = itemFuture.mapTo[Option[Item]]
          val response: Future[ToResponseMarshallable] = i.map {
            case Some(i: Item) => ToResponseMarshallable(i)
            case None => HttpResponse(StatusCodes.NotFound)
          }
          complete(response)
        }
    } ~
      path("item" / Segment / "add" / IntNumber) { (sku, quantity) =>
        val stockUpdater = system.actorOf(Props(new StockUpdateActor)) //todo: some child actor here
        val result = stockUpdater ? StockUpdateActor.AddStock("bober", sku, quantity)
        val response: Future[ToResponseMarshallable] = result.map {
          case StockUpdated => HttpResponse(StatusCodes.OK)
          case StockUpdateFailed => HttpResponse(StatusCodes.BadRequest)
        }
        complete(response)

      }
  }

  Http().bindAndHandle(routes, "localhost", 8082)
}
