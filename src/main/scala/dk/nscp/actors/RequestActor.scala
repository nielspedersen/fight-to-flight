package dk.nscp.actors

import akka.actor.Actor
import dk.nscp.actors.ActorObjects._
import scalaj.http._
import net.liftweb.json._
import net.liftweb.json.DefaultFormats
import java.text.SimpleDateFormat
import org.joda.time.DateTime

class RequestActor extends Actor {
  def receive = {
    case checkFlight: CheckFlight => 
      println(s"$self: requesting...")
      sender() ! getFlightDetails(checkFlight)
  }

  def getFlightDetails(check: CheckFlight): Record = {
    implicit val defaultFormats = DefaultFormats
    val searchTime = new DateTime()

    // Make sure request date format is yyyyMMdd'0000'
    val dateFormatter = org.joda.time.format.DateTimeFormat.forPattern("yyyyMMdd'0000'")

    val outDate = dateFormatter.print(new DateTime(check.outDate))
    val inDate = dateFormatter.print(new DateTime(check.inDate))
    val from = check.from
    val to = check.to
    
    val tokenRequest = (Http("https://www.sas.dk/bin/sas/d360/getOauthToken")
    ).postData("").timeout(connTimeoutMs = 5000, readTimeoutMs = 5000)
    val tokenResponse = tokenRequest.asString
    val authToken = (parse(tokenResponse.body) \ "access_token").extract[String]

    val flightRequest = (Http(s"https://api.flysas.com/offers/flightproducts?outDate=$outDate&inDate=$inDate&adt=1&bookingFlow=REVENUE&lng=GB&pos=DK&from=$from&to=$to&channel=web")
      .headers("Authorization" -> authToken)
      .timeout(connTimeoutMs = 5000, readTimeoutMs = 5000)
    )

    val flightResponse = flightRequest.asString

    val jsonResult = parse(flightResponse.body)

    // outbound is going
    // inbound is returning
    val outbound = jsonResult \ "outboundFlightProducts"
    val inbound = jsonResult \ "inboundFlightProducts"
    
    val outTime = new DateTime(((jsonResult \ "outboundFlights")(0) \ "startTimeInLocal").extract[String])
    val inTime = new DateTime(((jsonResult \ "inboundFlights")(0) \ "endTimeInLocal").extract[String])


    val outPrice = (outbound(0) \ "price" \ "totalPrice").extract[Float]
    val inPrice = (outbound(0) \ "price" \ "totalPrice").extract[Float]

    val totalPrice = outPrice + inPrice

    Record(searchTime, totalPrice, outPrice, inPrice, outTime, inTime)
  }
}
