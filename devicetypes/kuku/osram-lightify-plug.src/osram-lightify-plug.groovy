/**
 *  Copyright 2015 SmartThings
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
    definition (name: "OSRAM Lightify Plug", namespace: "KuKu", author: "KuKu") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Power Meter"
        capability "Sensor"
        capability "Switch"

        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0B04"
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0702"                
        fingerprint profileId: "C05E", inClusters: "02, 08, 0000, 0003, 0004, 0005, 0006, 0B04, FC0F", outClusters: "0019",manufacturer: "OSRAM", model: "Plug 01", deviceJoinName: "OSRAM Lightify Plug"
    }

    tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#79b821", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#79b821", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
            }
            tileAttribute ("power", key: "SECONDARY_CONTROL") {
                attributeState "power", label:'${currentValue} kW'
            }
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        main "switch"
        details(["switch", "refresh"])
    }
}

// Parse incoming device messages to generate events
def parse(String description) {
    log.debug "description is $description"
 	def name = null
	def value = null   
    if (description?.startsWith("read attr -")) {
        def descMap = parseDescriptionAsMap(description)  
        log.warn "clusterId : " + descMap.cluster
        log.warn "data : " + descMap.value
        
        if (descMap.cluster == "0006" && descMap.attrId == "0000") {
			name = "switch"
			value = descMap.value.endsWith("01") ? "on" : "off"
        } 
    } else if (description?.startsWith("on/off:")) {
        log.debug "Switch command"
		name = "switch"
		value = description?.endsWith(" 1") ? "on" : "off"
    
    } else if (description?.startsWith("catchall:")) {
        def msg = zigbee.parse(description)
    	log.debug msg     
        log.warn "data: $msg.clusterId"
    	if (msg.clusterId == 32801) { 
			log.warn "data: $msg.data"
            name = "power"
            value = msg.data           
        }
    }
    
    def result = createEvent(name: name, value: value)
	log.debug "Parse returned ${result?.descriptionText}"
	return result

}

def off() {
    zigbee.off()
}

def on() {
    zigbee.on()
}

def updated() {
    log.debug "in updated()"
    // updated() doesn't have it's return value processed as hub commands, so we have to send them explicitly
    //def cmds = configureHealthCheck()
    cmds.each{ sendHubCommand(new physicalgraph.device.HubAction(it)) }
}

def ping() {
    return zigbee.onOffRefresh()
}


def refresh() {
    Integer reportIntervalMinutes = 5
    zigbee.onOffRefresh() + zigbee.simpleMeteringPowerRefresh() + zigbee.electricMeasurementPowerRefresh() + zigbee.onOffConfig(0,reportIntervalMinutes * 60) + zigbee.simpleMeteringPowerConfig() + zigbee.electricMeasurementPowerConfig()
}


def configure() {
    log.debug "in configure()"
    return configureHealthCheck()
}

def configureHealthCheck() {
    Integer hcIntervalMinutes = 12
    sendEvent(name: "checkInterval", value: hcIntervalMinutes * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    return refresh()
}

def parseDescriptionAsMap(description) {
	(description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
}

private getEndpointId() {
	new BigInteger(device.endpointId, 16).toString()
}