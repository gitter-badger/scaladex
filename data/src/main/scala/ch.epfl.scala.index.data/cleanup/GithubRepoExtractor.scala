package ch.epfl.scala.index
package data
package cleanup

import model.GithubRepo
import maven.PomsReader

import spray.json._
import java.nio.file._

import fastparse.all._
import fastparse.core.Parsed

import scala.util.Success
import scala.util.matching.Regex

class GithubRepoExtractor extends DefaultJsonProtocol {
  private val source = scala.io.Source.fromFile(
    cleanupIndexBase.resolve(Paths.get("claims.json")).toFile
  )
  private val claims = source.mkString.parseJson.convertTo[Map[String, Option[String]]].toList.sorted
  private val matchers = claims.
    map{case (k, v) => v.map((k, _))}.flatten.
    map{case (k, v) =>
      val regex = k.replaceAllLiterally("*", "(.*)").r
      val List(organization, repo) = v.split('/').toList
      (regex, GithubRepo(organization, repo))
    }
  source.close()

  // More info in Rfc3986
  private val Alpha = (CharIn('a' to 'z') | CharIn('A' to 'Z')).!
  private val Digit = CharIn('0' to '9').!
  private val Unreserved = P(Alpha | Digit | "-".! | ".".! | "_".! | "~".! ).!
  private val Segment = P(Unreserved  | SubDelims | ":" | "@").!
  private def SubDelims = CharIn("!$&'()*+,;=").!

  private def removeDotGit(v: String) = 
    if(v.endsWith(".git")) v.dropRight(".git".length)
    else v
  
  private val ScmUrl = P("scm:".? ~ "git:".? ~ ("git@" | "https://" | "git://" | "//") ~ 
                 "github.com" ~ (":" | "/") ~ Segment.rep.! ~ "/" ~ Segment.rep.!.map(removeDotGit))

  private def parseRepo(v: String): Option[GithubRepo] = {
    ScmUrl.parse(v) match {
      case Parsed.Success((organization, repo), _) => Some(GithubRepo(organization, repo))
      case _ => None
    }
  }

  def apply(d: maven.MavenModel): Set[GithubRepo] = {
    import d._
    def matches(m: Regex, s: String): Boolean =
       m.unapplySeq(s).isDefined

    val fromPoms =
      scm match {
        case Some(scmV) => {
          import scmV._
          List(connection, developerConnection, url).
            flatten.
            flatMap(parseRepo).
            filter(g => g.organization != "" && g.repo != "")
        }
        case None => List()
      }
    
    val fromClaims =
      matchers.find{case (m, _) => 
        matches(m, s"$groupId $artifactId $version")
      }.map(_._2).toList

    (fromPoms ++ fromClaims).map{ 
      case GithubRepo(organization, repo) => GithubRepo(organization.toLowerCase, repo.toLowerCase)
    }.toSet
  }

  // script to generate contrib/claims.json
  def run() = {
    val poms = PomsReader
      .load()
      .collect {case Success((pom, _)) => pom}

    val noUrl = poms.filter(p => apply(p).size == 0)
    val notClaimed = noUrl.map{d =>
        import d._
        (s"$groupId $artifactId $version", None)
      }.toMap
    
    val nl = System.lineSeparator

    (notClaimed ++ claims).toList.sorted.map{ case (k, v) =>
      "  \"" + k + "\": " + v.map(x => "\"" + x + "\"").getOrElse("null")
    }.mkString("{" + nl, "," + nl , nl + "}")
  }
}