import sbt._
import sbt.StringUtilities._

class ScalangProject(info : ProjectInfo) extends DefaultProject(info) {
  val codaRepo = "Coda's Repo" at "http://repo.codahale.com"
  val jetlangRepo = "Jet Lang Repository" at "http://jetlang.googlecode.com/svn/repo/"
  val nettyRepo = "JBoss Netty Repository" at "http://repository.jboss.org/nexus/content/groups/public/"
  override def managedStyle = ManagedStyle.Maven
  val boundaryPublic = "Boundary Public Repo" at "http://maven.boundary.com/artifactory/repo"
  
/*  val publishTo = "Boundary Public Repo (Publish)" at "http://maven.boundary.com/artifactory/external"*/
  val publishTo = "Boundary Private Repo" at "http://maven.eng.boundary.com/artifactory/external"
  
  
  val logula = "com.codahale" %% "logula" % "2.1.2"
  val netty = "org.jboss.netty" % "netty" % "3.2.4.Final"
  val jetlang = "org.jetlang" % "jetlang" % "0.2.5"
  val overlock = "com.boundary" %% "overlock" % "0.4"
  val specs = "org.scala-tools.testing" %% "specs" % "1.6.7" % "test"
  
  Credentials(Path.userHome / ".ivy2" / ".credentials-internal", log)
}