/**
* SMART Dehumidifier
* Author: CM PAGANI
* Date: 2015-05-14
*/
/* Revised 2016-12-17, Tarsier Blip
	- "Full tank" functionality added (if the switch has this capability)
	- Scheduling fixed to accurately operate inside scheduled times
	- Notification system fixed 
*/

definition(
	name: "SMART Dehumidifier",
	namespace: "SMART-D",
	author: "CMPAGANI",
	description: "When the humidity level goes above a certain value, within a specified time interval, turn on a switched dehumidifier. When that value drops below a separate threshold, turn off the dehumidifier.",
	category: "Convenience",
	iconUrl: "http://zizaza.com/cache/icon_256/iconset/580464/580468/PNG/512/weather/weather_flat_icon_weather_icon_png_humidity_humidity_png_humidity_icon.png",
	iconX2Url: "http://zizaza.com/cache/icon_256/iconset/580464/580468/PNG/512/weather/weather_flat_icon_weather_icon_png_humidity_humidity_png_humidity_icon.png"
	)

preferences 
	{
	section("Monitor the humidity...")
		{
		input "humiditySensor1", "capability.relativeHumidityMeasurement", title: "Humidity Sensor?", required: true, multiple: true
		}
	section("Choose a Switch that controls a dehumidifier...")
		{
		input "outlet1", "capability.switch", title: "Dehumidifier Location?", required: true, multiple: false
		}
	section("Turn dehumidifier on when the humidity is above:") 
		{
		input "humidityUP", "number", title: "Humidity Upper Threshold (%)?"
		}
	section("Turn dehumidifier off when the humidity returns below:") 
		{
		input "humidityDW", "number", title: "Humidity Lower Threshold (%)?"
		}
	section("Turn on only during this timeframe")
		{
		input "starting", "time", title: "Start time", required: true
		input "ending", "time", title: "End time", required: true
		}
	section("Send this message when humidity is too high (optional, sends standard status message if not specified)")
		{
		input "messageHighHumidity", "text", title: "Message Text High Humidity", required: false
		}
	section("Send this message when humidity is back to normal (optional, sends standard status message if not specified)")
		{
		input "messageOKHumidity", "text", title: "Message Text Humidity OK",  required: false
		}
	section("Send this message when water tank is full (optional, sends standard status message if not specified)")
		{
		input "messageTankFull", "text", title: "Message Text Tank Full", required: false
		}
	section("Via a push notification and/or an SMS message")
		{
		input("recipients", "contact", title: "Send notifications to") 
			{
			input "pushAndPhone", "enum", title: "Enable push notifications?", required: true, options: ["Yes", "No"]
			input "phone", "phone", title: "Main Phone Number (use sign + for country code)", required: false
			}
		}
	}

def installed() 
	{
	subscribe(humiditySensor1, "humidity", humidityHandler)
	schedule(starting, humidityHandler)
	schedule(ending, humidityHandler)
	log.debug "Installed with settings: ${settings}"
	}

def updated() 
	{
	unsubscribe()
	subscribe(humiditySensor1, "humidity", humidityHandler)
	schedule(starting, humidityHandler)
	schedule(ending, humidityHandler)
	log.debug "Updated with settings: ${settings}"
	}

def humidityHandler(evt) 
	{
	def humNum = 0
	double tooHumid = humidityUP
	double OKHumid = humidityDW
	boolean OKRun = false	// Variable to indicate whether we are within the time range set
	def timeNow = now()

	// If there is an event; we get humidity from the event information
	if ( evt )
		{
		log.debug "Humidity: $evt.value, $evt.unit, $evt.name, $evt"
		humNum = Double.parseDouble(evt.value.replace("%", ""))
		}
	// ... otherwise, we use whatever the last reported humidity was.
	else
		{
		humNum = humiditySensor1.currentValue("humidity")
		}

	log.debug("Current time is ${(new Date(timeNow)).format("EEE MMM dd yyyy HH:mm z", location.timeZone)}")
	log.debug "Current Humidity: $humNum, Low humidity limit: $OKHumid, High humidity limit: $tooHumid"

	// If end time is before start time ...
	if (timeToday(starting, location.timeZone).time > timeToday(ending, location.timeZone).time)
		{
		//log.debug("Times go overnight; using alternate method.")
		// If we are earlier or later than the two times set, then we should be running
		if (now() > timeToday(starting, location.timeZone).time || now() < timeToday(ending, location.timeZone).time)
			{
			OKRun = true
			}
		}
	else
		{
		//log.debug("Times go during the day; using standard method.")
		// If we are between the two times set, then we should be running
		if (now() >= timeToday(starting, location.timeZone).time && now() < timeToday(ending, location.timeZone).time)
			{
			OKRun = true
			}
		}

	if ( OKRun )
		{
		//log.debug("Operation is within set times.")
		}

	if ( outlet1.hasAttribute("power") )
		{
		int avgPower = 0		// Variable to hold the average power draw, if available
		// My outlet randomly reports odd wattages (sometimes 0); we quickly check it 5 times, then average them.
		// Due to the drastic difference between "running" and "idle; tank full", even a few misreads would not make a difference.
		for (int ii = 0; ii<5; ii++)
			{
			//log.debug(outlet1.currentValue("power"))
			avgPower += outlet1.currentValue("power")
			pause(250)
			}
		avgPower /= 5
		//log.debug("Dehumidifier is drawing $avgPower watts.")

		if ( outlet1.currentValue("switch") == "on" && avgPower < 30 )
			{
			//log.debug("Tank Full")
			if ( !messageTankFull )
				{ sendMessage(evt, "Dehumidifier tank appears to be full.", "Force") }
			else
				{ sendMessage(evt, messageTankFull, "Force") }
			}
		else
			{
			//log.debug("Tank Not Full")
			}
		}

	if ( !OKRun && outlet1.currentValue("switch") == "on" )
		{
		log.debug "Operation ran out of time range; turning off device."
		outlet1.off()   
		sendMessage(evt, "Operation ran out of time range; turning off device.")
		}
	else if ( OKRun && humNum >= tooHumid )
		{
		log.debug "Humidity is over the setpoint of $tooHumid"
		if ( outlet1.currentValue("switch") == "off" ) 
			{
			outlet1.on()   
			if ( !messageHighHumidity )
				{ sendMessage(evt, "Humidity level is $humNum% (above the $tooHumid threshold); activating dehumidifier.") }
			else
				{ sendMessage(evt, messageHighHumidity) }
			}
		}
	else if ( OKRun && humNum <= OKHumid )
		{
		log.debug "Humidity is below the setpoint of $OKHumid"
		if ( outlet1.currentValue("switch") == "on" ) 
			{
			if ( !messageOKHumidity )
				{ sendMessage(evt, "Humidity level is $humNum% (below the $OKHumid threshold); deactivating dehumidifier.") }
		else
			{ sendMessage(evt, messageOKHumidity) }
			outlet1.off()   
			}
		}
	}

private sendMessage(evt, msg)
	{
	msg = msg ?: evt.descriptionText
	if ( evt ) 
		{
	//log.debug "$evt.name:$evt.value, pushAndPhone:$pushAndPhone, '$msg'"
	}
	if (location.contactBookEnabled) 
		{
		sendNotificationToContacts(msg, recipients)
		} 
	else 
		{
		if ( pushAndPhone == "Yes" || overrride == "Force" ) 
			{
			log.debug "sending push"
			sendPushMessage(msg)
			}
		if ( phone ) 
			{
			log.debug "sending SMS"
			sendSmsMessage(phone, msg)
			}
		}
	}
