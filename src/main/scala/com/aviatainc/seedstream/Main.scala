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

sealed abstract class InfoLevel(val level: String)
case object IdInfo extends InfoLevel("ID")
case object CapabilitiesInfo extends InfoLevel("CAPABILITIES")
case object StationsInfo extends InfoLevel("STATIONS")
case object StreamsInfo extends InfoLevel("STREAMS")
case object GapsInfo extends InfoLevel("GAPS")
case object ConnectionsInfo extends InfoLevel("CONNECTIONS")
case object AllInfo extends InfoLevel("ALL")

sealed abstract class Command(val cmd: String) {
  val SEP = "\r\n"
  def command = s"$cmd$SEP"
}
case object Hello extends Command("HELLO")
case object Cat extends Command("CAT")
case object Data extends Command("DATA")
case object End extends Command("END")
case class Info(level: InfoLevel) extends Command(s"INFO ${level.level}")
case class Select(pattern: String) extends Command(s"SELECT $pattern")
case class Station(network: String, name: String) extends Command(s"STATION $name $network")

case class Feed(station: Station, pattern: String) {
  val selector: Select = Select(pattern)
}

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
    
    // Target just a few stations
    //*
    val feeds: List[Feed] = Feed(Station("IU", "ADK"), "BH?") ::
      Feed(Station("IU", "COLA"), "BH?") ::
      Feed(Station("IU", "COR"), "BH?") ::
      Feed(Station("IU", "ANMO"), "BH?") ::
      Nil
    //  */
    
    // The firehose (all stations)
    //val feeds: List[Feed] = Nil
    
    val commands: List[Command] = if (feeds.isEmpty) {
        Hello :: Select("BH?") :: Data :: Cat :: Info(AllInfo) :: Nil
      } else {
        Hello +: feeds.flatMap { feed =>
          feed.station :: feed.selector :: Data :: Nil
        } :+ Cat :+ Info(AllInfo) :+ End
      }
    
    val handshake = commands map { command =>
      (command, Promise[Command])
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
    val source = Source.maybe[ByteString]
        .prepend(Source(handshake)
        .mapAsync[Command](1)(_._2.future)
        .map { cmd =>
          log(s"Sending command: ${cmd.cmd}")
          ByteString(cmd.command)
        })
    
    // Records in-bound from the server.
    val flow = Flow[ByteString]
        .map[(Int, Option[String], ByteString)] { record =>
          if (record.length < 512) {
            try {
              (record.length, Some(record.decodeString("ascii")), record)
            } catch {
              case cause: Throwable => {
                println(s"Error parsing non data record: $cause")
                (record.length, None, record)
              }
            }
          } else if (record.length > 512) {
            // TODO: really need to handle sizes of 512, 520, or multiples of
            //       these, and split them up into sub-records.
            (record.length, None, record)
          } else {
            (record.length, None, record)
          }
        }
    
    val sink = flow
        .toMat(Sink.foreach {
          case (len, None, record) => {
            // Make sure all of the commands sent sequentially
            log(s"Received $len byte record\n$record")
            nextCommand(None)
          }
          case (len, Some(decoded), raw) => {
            if (decoded startsWith "SLINFO") {
              // Process the feed record
              log(s"Received info record")
            } else if (decoded startsWith "SL") {
              // Process the feed record
              log(s"Processing record ${decoded.substring(2, 8)}")
              val recordSummary = if (len <= 40) s":\n$raw\n$decoded" else ""
              log(s"Received $len byte record${recordSummary}")
            } else if (decoded startsWith "ERROR") {
              // Handle an error with handshake commands
              log("Command rejected [ERROR]")
              nextCommand(Some(new Exception("Server rejected command.")))
            } else if (decoded startsWith "OK") {
              // Make sure all of the commands sent sequentially
              log("Command accepted [OK]")
              nextCommand(None)
            } else {
              // Make sure all of the commands sent sequentially
              val recordSummary = if (len <= 256) s":\n$decoded" else ""
              log(s"Received $len byte response$recordSummary")
              nextCommand(None)
            }
          }
        })((_, end) => end)
        
    val connection = Tcp().outgoingConnection("rtserve.iris.washington.edu", 18000)
    
    val (start, end) = connection.runWith(source, sink)
    
    end.andThen {
      case Success(_) => log("Stream ended normally.")
      case Failure(error) => log(s"Stream ended in error: $error")
    }
    
    log("Not sure if done, or just in different thread...")
  }
  
  seedStream()
}