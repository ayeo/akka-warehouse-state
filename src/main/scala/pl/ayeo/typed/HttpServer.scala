package pl.ayeo.typed

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.Cluster
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.marshalling.ToResponseMarshallable

import scala.concurrent.Future
import scala.concurrent.duration._


object HttpServer extends App
  with ItemModelJsonProtocol
  with SprayJsonSupport {


  val system = ActorSystem[Nothing](Behaviors.empty, "ClusterSystem") //todo: get rid of name here
  val cluster = Cluster(system)
  implicit val sharding = ClusterSharding(system)
  implicit var timeout = Timeout(2.seconds)
  implicit val classicSystem = system.toClassic

  import classicSystem.dispatcher //todo this is not good :D

  ItemActor.init

  val routes = {
    path("item" / Segment) { sku: String =>
        get {
          val item = ItemActor.entityRef(sku)
          val itemFuture = item ? ItemActor.Get
          val i: Future[Option[Item]] = itemFuture.mapTo[Option[Item]]
          val response: Future[ToResponseMarshallable] = i.map {
            case Some(i: Item) => ToResponseMarshallable(i)
            case None => HttpResponse(StatusCodes.NotFound)
          }
          //val response = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Bober")
          complete(response)
        }
    }
  }

  Http().bindAndHandle(routes, "localhost", 8085)
}
