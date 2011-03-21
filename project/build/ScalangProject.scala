import sbt._
import sbt.StringUtilities._

class ScalangProject(info : ProjectInfo) extends DefaultProject(info) {
  val jetlangRepo = "Jet Lang Repository" at "http://jetlang.googlecode.com/svn/repo/"
  val nettyRepo = "JBoss Netty Repository" at "http://repository.jboss.org/nexus/content/groups/public/"
  
  val netty = "org.jboss.netty" % "netty" % "3.2.4.Final"
  val jetlang = "org.jetlang" % "jetlang" % "0.2.5"
  val specs = "org.scala-tools.testing" %% "specs" % "1.6.7" % "test"
  
  //logging
  val log4j = "log4j" % "log4j" % "1.2.16"
  val slf4japi = "org.slf4j" % "slf4j-api" % "1.5.8"
  val slf4j = "org.slf4j" % "slf4j-log4j12" % "1.5.8"
  
  val publishTo = Resolver.ssh("fastipInternal", "apt.dfw2.fastip.com", "/srv/reprepro_internal/ivy")
}