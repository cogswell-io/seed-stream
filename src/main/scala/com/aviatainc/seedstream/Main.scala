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
import scala.concurrent.Future
import scala.concurrent.Promise
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.ZonedDateTime
import java.time.ZoneId

case class Station(network: String, name: String)
case class Feed(station: Station, selectors: Seq[String])

object Main extends App {
  def timestamp = ZonedDateTime.now()
      .withZoneSameInstant(ZoneId.of("UTC"))
      .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  
  def log(message: String): Unit = {
    println(s"[$timestamp] $message")
  }
  
  
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
    
    val commands: Seq[String] = feeds.flatMap { case Feed(Station(name, network), selectors) =>
      s"STATION $name $network" +: selectors.map(s => s"SELECT $s")
    } :+ "END"
    
    log(s"Commands:\n${commands.mkString("\n")}")
    
    val handshake = commands map { command =>
      (ByteString(s"$command$SEP"), Promise[ByteString])
    } toList
    
    val commandIterator = handshake.iterator
    
    def nextCommand(fail: Option[Throwable]): Unit = {
      if (commandIterator.hasNext) {
        val (command, promise) = commandIterator.next
        
        fail match {
          case None => promise.success(command)
          case Some(cause) => promise.failure(cause)
        }
      }
    }
    
    // Complete the first future to get the ball rolling
    nextCommand(None)
    
    // Handshake commands out-bound to the server.
    val source = Source(handshake)
        .mapAsync[ByteString](1)(_._2.future)
        .map { cmd =>
          log(s"Sending command: ${cmd.decodeString("UTF-8")}")
          cmd
        }
    
    // Records in-bound from the server.
    val flow = Flow[ByteString]
        .map(record => (record.length, record.decodeString("ascii"), record))
    
    val sink = flow
        .to(Sink.foreach { case (len, decoded, raw) =>
          if (decoded startsWith "ERROR") {
            // Handle an error with handshake commands
            log("Command rejected [ERROR]")
            nextCommand(Some(new Exception("Server rejected command.")))
          } else if (decoded startsWith "OK") {
            // Make sure all of the commands sent sequentially
            log("Command accepted [OK]")
            nextCommand(None)
          } else {
            // Process the record feed
            log("Processing a record")
            val recordSummary = if (len <= 40) s":\n$raw\n$decoded" else ""
            log(s"Received $len byte record${recordSummary}")
          }
        })
        
    val connection = Tcp().outgoingConnection("rtserve.iris.washington.edu", 18000)
    
    connection.runWith(source, sink)
  }
  
  seedStream()
}