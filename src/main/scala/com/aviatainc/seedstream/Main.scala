package com.aviatainc.seedstream

import akka.stream.ActorMaterializer
import akka.util.ByteString
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Tcp
import scala.util.Failure
import scala.util.Success
import akka.stream.scaladsl.Framing
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink

case class Station(network: String, name: String)
case class Feed(station: Station, selectors: Seq[String])

object Main extends App {
  def seedStream(): Unit = {
    implicit val system = ActorSystem()
    import system.dispatcher
    implicit val materializer = ActorMaterializer()
    
    val SEP = "\r\n"
    
    val feeds = Seq(
      //Feed(Station("IU", "ADK"), Seq("BH?")),
      Feed(Station("IU", "COLA"), Seq("BH?")),
      Feed(Station("IU", "COR"), Seq("BH?")),
      Feed(Station("IU", "ANMO"), Seq("BH?"))
    )
    
    val feedStrings = feeds.map { case Feed(Station(name, network), selectors) =>
      val selectorsString = selectors.map(s => s"SELECT $s").mkString(SEP)
      s"STATION $name $network${SEP}$selectorsString${SEP}DATA"
    }
    
    val handshake = feedStrings.mkString(SEP) + s"${SEP}END${SEP}"
    
    println(s"Handshake:\n$handshake")
    
    val source = Source(ByteString(handshake) :: Nil)
    val sink = Flow[ByteString]
        .map(record => (record.length, record.decodeString("ascii"), record))
        .to(Sink.foreach { case (len, decoded, raw) =>
          val recordSummary = if (len <= 40) s":\n$raw\n$decoded" else ""
          println(s"Received $len byte record${recordSummary}")
        })
    val connection = Tcp().outgoingConnection("rtserve.iris.washington.edu", 18000)
    
    connection.runWith(source, sink)
  }
  
  seedStream()
}