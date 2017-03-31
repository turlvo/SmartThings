/**
 *  T View Sense Temp &amp; Humi
 *
 *  Copyright 2017 KuKu
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "T View Sense Temp and Humi", namespace: "KuKu", author: "KuKu") {

		capability "Configuration"
		capability "Battery"
		capability "Refresh"
		capability "Temperature Measurement"
		capability "Relative Humidity Measurement"
		capability "Health Check"
		capability "Sensor"
		
        fingerprint endpointId: "1", profileId: "0104", inClusters: "0000, 0003, 0402"
		
	}

	simulator {
		status 'H 40': 'catchall: 0104 FC45 01 01 0140 00 D9B9 00 04 C2DF 0A 01 000021780F'
		status 'H 45': 'catchall: 0104 FC45 01 01 0140 00 D9B9 00 04 C2DF 0A 01 0000218911'
		status 'H 57': 'catchall: 0104 FC45 01 01 0140 00 4E55 00 04 C2DF 0A 01 0000211316'
		status 'H 53': 'catchall: 0104 FC45 01 01 0140 00 20CD 00 04 C2DF 0A 01 0000219814'
		status 'H 43': 'read attr - raw: BF7601FC450C00000021A410, dni: BF76, endpoint: 01, cluster: FC45, size: 0C, attrId: 0000, result: success, encoding: 21, value: 10a4'
	}

	preferences {
		input title: "Temperature Offset", description: "This feature allows you to correct any temperature variations by selecting an offset. Ex: If your sensor consistently reports a temp that's 5 degrees too warm, you'd enter \"-5\". If 3 degrees too cold, enter \"+3\".", displayDuringSetup: false, type: "paragraph", element: "paragraph"
		input "tempOffset", "number", title: "Degrees", description: "Adjust temperature by this many degrees", range: "*..*", displayDuringSetup: false
	}

	tiles(scale: 2) {
		multiAttributeTile(name: "temp&humidity", type: "generic", width: 6, height: 4, canChangeIcon: true) {
			tileAttribute("temp&humidity", key: "PRIMARY_CONTROL") {
				attributeState "temp&humidity", label: '${currentValue}',
						backgroundColors: [
								[value: 18, color: "#153591"],
								//[value: 20, color: "#1e9cbb"],
								//[value: 22, color: "#90d2a7"],
								[value: 21, color: "#44b621"],
								[value: 24, color: "#f1d801"],
								[value: 27, color: "#d04e00"],
								[value: 30, color: "#bc2323"]
						]
			}
		}
		valueTile("temperature", "device.temperature", inactiveLabel: false, width: 2, height: 2) {
			state "temperature", label: '${currentValue}°C temperature', unit: ""
		}
		valueTile("humidity", "device.humidity", inactiveLabel: false, width: 2, height: 2) {
			state "humidity", label: '${currentValue}% humidity', unit: ""
		}
		valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label: '${currentValue}% battery'
		}
		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", action: "refresh.refresh", icon: "st.secondary.refresh"
		}

		main "temp&humidity"
		details(["temp&humidity", "temperature", "humidity", "battery"])
	}
}



// Parse incoming device messages to generate events
def parse(String description) {
	log.debug "parse: desc: $description"
	def name = parseName(description)
	def value = parseValue(description)
	def temperatureUnit = getTemperatureScale()
    def zeroUnit = '-'
    def mergedValue
    
    if (name == "temperature" && value == "48") {
    	log.debug "wrong temp: " + value
    } else {    	
    	if (name == "temperature") {
        	log.debug "report temp event>> currentTemp: $value, beforeTemp: $state.beforeTemp"
            log.debug "report temp event>> beforeHumidity: $state.beforeHumidity"
        	state.beforeTemp = value

           	mergedValue = "$value°$temperatureUnit / ${state.beforeHumidity == null ? zeroUnit : state.beforeHumidity}%"
        } else {
        	log.debug "report humidity>> currentHumidity: $value, beforeHumidity: $state.beforeHumidity"
            log.debug "report humidity>> beforeTemp: $state.beforeTemp"        	
        	state.beforeHumidity = value

            mergedValue = "${state.beforeTemp == null ? zeroUnit : state.beforeTemp}°$temperatureUnit / $value%"
        }
        sendEvent(name:"temp&humidity", value: mergedValue, displayed: false)
		def result = createEvent(name: name, value: value, unit: unit)
        //log.debug "Parse returned ${result?.descriptionText}"
        
        return result
    }
	
	
}

private String parseName(String description) {
	if (description?.startsWith("temperature: ")) {
		return "temperature"
	} else if (description?.startsWith("humidity: ")) {
		return "humidity"
	}
	null
}

private String parseValue(String description) {
	if (description?.startsWith("temperature: ")) {
		return zigbee.parseHATemperatureValue(description, "temperature: ", getTemperatureScale())
	} else if (description?.startsWith("humidity: ")) {
		def pct = (description - "humidity: " - "%").trim()
		if (pct.isNumber()) {
			return Math.round(new BigDecimal(pct)).toString()
		}
	}
	null
}

def refresh() {
	log.debug "refresh temperature, humidity, and battery"
	return zigbee.batteryConfig() +
			zigbee.temperatureConfig(30, 3600)
}

def configure() {	
	log.debug "Configuring Reporting and Bindings."
	// temperature minReportTime 30 seconds, maxReportTime 60 min. Reporting interval if no activity
	// battery minReport 30 seconds, maxReportTime 6 hrs by default
    zigbee.batteryConfig() + zigbee.temperatureConfig(30, 3600)
	return refresh()
}

private Map getBatteryResult(rawValue) {
	log.debug 'Battery'
	def linkText = getLinkText(device)

	log.debug rawValue

	def result = [
		name: 'battery',
		value: '--'
	]
    result.descriptionText = "${linkText} battery was ${rawValue}%"

	def volts = rawValue / 10
    log.debug volts
	def descriptionText

	if (rawValue == 0) {}
	else {
		if (volts > 3.5) {
			result.descriptionText = "${linkText} battery has too much power (${volts} volts)."
		}
		else if (volts > 0){
			def minVolts = 2.1
			def maxVolts = 3.0
			def pct = (volts - minVolts) / (maxVolts - minVolts)
			result.value = Math.min(100, (int) pct * 100)
			result.descriptionText = "${linkText} battery was ${result.value}%"
		}
	}

	return result
}
