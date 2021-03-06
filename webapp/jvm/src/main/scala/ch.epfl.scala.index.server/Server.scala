package ch.epfl.scala.index
package server

import api._
import model._
import data.cleanup.SemanticVersionParser

import upickle.default.{read => uread}

import akka.http.scaladsl._
import akka.http.scaladsl.model._, Uri._, StatusCodes._

import com.softwaremill.session._
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.{Properties, Try}

object Server {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("scaladex")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val production = Try(Properties.envOrElse("production", "false").toBoolean).getOrElse(false)

    val github = new Github

    val sessionConfig = SessionConfig.default("c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe")
    implicit val sessionManager = new SessionManager[UserState](sessionConfig)
    implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[UserState] {
      def log(msg: String) = println(msg)
    }

    val sharedApi = new ApiImplementation(github, None)
    val rest = new RestApi(sharedApi)

    def reuseSharedApi(userState: Option[UserState]) =
      if(userState.isDefined) new ApiImplementation(github, userState)
      else sharedApi

    def frontPage(userInfo: Option[UserInfo]) = {
      for {
        keywords <- sharedApi.keywords()
        latestProjects <- sharedApi.latestProjects()
        latestReleases <- sharedApi.latestReleases()
      } yield views.html.frontpage(keywords, latestProjects, latestReleases, userInfo, production)
    }

    def artifactPage(reference: Artifact.Reference, version: Option[SemanticVersion], user: Option[UserInfo]) = {
      def selectedRelease(project: Project): List[Release] = {
        def latest(artifact: Artifact): Option[SemanticVersion] = {
          version match {
            case Some(v) => Some(v)
            case None => artifact.releases.map(_.reference.version).sorted.reverse.headOption
          }
        }

        for {
          artifact <- project.artifacts.filter(_.reference == reference)
          v <- latest(artifact).toList
          release <- artifact.releases.filter(_.reference.version == v)
        } yield release
      }

      sharedApi.projectPage(reference).map(project =>
        project.map(p => (OK, views.html.artifact(p, reference, version, selectedRelease(p), user, production)))
               .getOrElse((NotFound, views.html.notfound(user, production)))
      )
    }

    val route = {
      import akka.http.scaladsl._
      import server.Directives._
      import TwirlSupport._

      post {
        path("autowire" / Segments){ s ⇒
          entity(as[String]) { e ⇒
            optionalSession(refreshable, usingCookies) { userState =>
              complete {
                AutowireServer.route[Api](reuseSharedApi(userState))(
                  autowire.Core.Request(s, uread[Map[String, String]](e))
                )
              }
            }
          }
        }
      } ~
      rest.route ~
      get {
        path("login") {
          redirect(Uri("https://github.com/login/oauth/authorize").withQuery(Query(
            "client_id" -> github.clientId
          )),TemporaryRedirect)
        } ~
        path("logout") {
          requiredSession(refreshable, usingCookies) { _ =>
            invalidateSession(refreshable, usingCookies) { ctx =>
              ctx.complete(frontPage(None))
            }
          }
        } ~
        pathPrefix("callback") {
          path("done") {
            complete("OK")
          } ~
          pathEnd {
            parameter('code) { code =>
              val userState = Await.result(github.info(code), 10.seconds)
              setSession(refreshable, usingCookies, userState) {
                setNewCsrfToken(checkHeader) { ctx =>
                  ctx.complete(frontPage(Some(userState.user)))
                }
              }
            }
          }
        } ~
        path("assets" / Remaining) { path ⇒
          getFromResource(path)
        } ~
        path("search") {
          optionalSession(refreshable, usingCookies) { userState =>
            parameters('q, 'page.as[Int] ? 1, 'sort.?, 'you.?) { (query, page, sorting, you) =>
              complete(sharedApi.find(query, page, sorting, you.flatMap(_ => userState.map(_.repos))).map{ case (pagination, projects) => 
                views.html.searchresult(query, sorting, pagination, projects, userState.map(_.user), production)
              })
            }
          }
        } ~
        path(Segment / Segment) { (owner, artifactName) =>
          optionalSession(refreshable, usingCookies) { userState =>
            val reference = Artifact.Reference(owner, artifactName)
            complete(artifactPage(reference, version = None, userState.map(_.user)))
          }
        } ~
        path(Segment / Segment / Segment) { (owner, artifactName, version) =>
          optionalSession(refreshable, usingCookies) { userState =>
            val reference = Artifact.Reference(owner, artifactName)
            complete(artifactPage(reference, SemanticVersionParser(version), userState.map(_.user)))
          }
        } ~
        path(Segment) { owner =>
          parameters('artifact, 'version.?){ (artifact, version) =>
            val rest = version match {
              case Some(v) if !v.isEmpty => "/" + v
              case _ => ""
            }
            redirect(s"/$owner/$artifact$rest", StatusCodes.PermanentRedirect)
          }
        } ~
        path("edit" / Segment / Segment) { (owner, artifactName) =>
          optionalSession(refreshable, usingCookies) { userState =>
            val reference = Artifact.Reference(owner, artifactName)
            complete(
              sharedApi.projectPage(reference).map(project =>
                project.map(p => views.html.editproject(p, reference, version = None, userState.map(_.user), production))
              )
            )
          }
        } ~
        pathSingleSlash {
          optionalSession(refreshable, usingCookies) { userState =>
            complete(frontPage(userState.map(_.user)))
          }
        }
      }
    }

    val setup = for {
      _ <- ApiImplementation.setup
      _ <- Http().bindAndHandle(route, "0.0.0.0", 8080)
    } yield ()
    Await.result(setup, 20.seconds)

    ()
  }
}