package org.vaslabs.urlshortener.server

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, PathMatcher1, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.util.Try

trait HttpRouter extends FailFastCirceSupport {
  this: ShortenedUrlApi =>

  import io.circe.generic.auto._

  def extractFromCustomHeader = headerValuePF {
    case t@ApiTokenHeader(token) => t.value()
  }

  def main: Route = {
    get {
      path(ShortenedPathMatchers.urlIds) { urlId =>
        onComplete(this.fetchUrl(urlId)) {
          _.map(url => redirect(Uri(url), StatusCodes.TemporaryRedirect))
            .getOrElse(complete(HttpResponse(StatusCodes.NotFound)))
        }
      }
    } ~ post {
      path("entry") {
        entity(as[ShortenUrlRQ]) { rq =>
          extractFromCustomHeader { headerValue =>
            onComplete(this.shortenUrl(rq, headerValue)) {
              _.map(shortenedUrl =>
                complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, shortenedUrl)))
                .getOrElse(complete(HttpResponse(StatusCodes.InternalServerError)))
            }
          }
        }
      }
    }

  }
}

object ShortenedPathMatchers {
  val urlIds: PathMatcher1[String] =
    PathMatcher("""[a-z0-9]{2,16}""".r)
}

final class ApiTokenHeader(token: String) extends ModeledCustomHeader[ApiTokenHeader] {
  override def renderInRequests = false

  override def renderInResponses = false

  override val companion = ApiTokenHeader

  override def value: String = token
}

object ApiTokenHeader extends ModeledCustomHeaderCompanion[ApiTokenHeader] {
  override val name = "X_SPARROW_AUTH"

  override def parse(value: String) = Try(new ApiTokenHeader(value))
}


