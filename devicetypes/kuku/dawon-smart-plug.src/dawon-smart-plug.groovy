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
    definition (name: "DAWON Smart Plug", namespace: "KuKu", author: "KuKu") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Power Meter"
        capability "Sensor"
        capability "Switch"
        capability "Health Check"
        
        command "identify"

        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0B04"
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0702"
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0702, 0B05", outClusters: "0003, 000A, 0019", manufacturer: "Jasco Products", model: "45853", deviceJoinName: "GE ZigBee Plug-In Switch"
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0702, 0B05", outClusters: "000A, 0019", manufacturer: "Jasco Products", model: "45856", deviceJoinName: "GE ZigBee In-Wall Switch"
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 000F, 0B04", outClusters: "0019", manufacturer: "SmartThings", model: "outletv4", deviceJoinName: "Outlet"
        fingerprint profileId: "0104", inClusters: "0000, 0004, 0003, 0006, 0019, 0702, 0B04", manufacturer: "DAWON_DNS", model: "PM-B430-ZB", deviceJoinName: "DAWON Smart Plug"
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
 
        standardTile("identify", "device.identify", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"identify", icon:"st.Lighting.light11"
        }
        main "switch"
        details(["switch", "refresh", "identify"])
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
        log.warn "attributeId : " + descMap.attrId
        log.warn "data : " + descMap.value
        if (descMap.cluster == "0702" && descMap.attrId == "0000") {
        	name = "power"			            
			value = String.format("%5.3f",zigbee.convertHexToInt(descMap.value) / 1000)
            log.warn "power : " + value
        } else if (descMap.cluster == "0702" && descMap.attrId == "0200") {
        	if (value == "80") {
            	log.warn "***********"
            }
        } else if (descMap.cluster == "0006" && descMap.attrId == "0000") {
			name = "switch"
			value = descMap.value.endsWith("01") ? "on" : "off"
        }

    } else if (description?.startsWith("on/off:")) {
        log.debug "Switch command"
		name = "switch"
		value = description?.endsWith(" 1") ? "on" : "off"
    
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

def refresh() {
	log.debug "refresh()"
    zigbee.onOffRefresh() + zigbee.readAttribute(0x0702, 0x0000)
}

def configure() {
    log.debug "Configuring Reporting and Bindings."
    zigbee.onOffConfig(0, 300) + zigbee.configureReporting(0x0702, 0x0000, 0x25, 0, 600, 1)+zigbee.configureReporting(0x0702, 0x0200, 0x18, 0, 36000, 1)
}

def identify() {
	log.debug "identify()"    
    zigbee.command(0x0003, 0x00, "0a00")
}

def parseDescriptionAsMap(description) {
	(description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
}