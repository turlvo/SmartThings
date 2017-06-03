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
        capability "Switch"
        capability "Health Check"
        
        command "identify"

        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0B04"
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0702"
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0702, 0B05", outClusters: "0003, 000A, 0019", manufacturer: "Jasco Products", model: "45853", deviceJoinName: "GE ZigBee Plug-In Switch"
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0702, 0B05", outClusters: "000A, 0019", manufacturer: "Jasco Products", model: "45856", deviceJoinName: "GE ZigBee In-Wall Switch"
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 000F, 0B04", outClusters: "0019", manufacturer: "SmartThings", model: "outletv4", deviceJoinName: "Outlet"
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0006, 0019, 0702, 0B04", manufacturer: "DAWON_DNS", model: "PM-B430-ZB", deviceJoinName: "DAWON Smart Plug"
    }

    tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#79b821", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#79b821", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
            }
            tileAttribute("device.lastCheckin", key: "SECONDARY_CONTROL") {
                attributeState("default", label:'Last Update: ${currentValue}', icon: "st.Health & Wellness.health9")
            }
        }
        valueTile("energy", "device.energy", decoration: "flat", width: 2, height: 2) {
            state "energy", label:'${currentValue} kW'
        }
        valueTile("power", "device.power", decoration: "flat", width: 2, height: 2) {
            state "power", label:'${currentValue} Watts'
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
 
        standardTile("identify", "device.identify", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"identify", icon:"st.Lighting.light11"
        }
        main "switch"
        details(["switch", "energy", "power", "refresh", "identify"])
    }
}

// Parse incoming device messages to generate events
def parse(String description) {
    log.debug "description is $description"
 	def name = null
	def value = null
    
    def event = zigbee.getEvent(description)
    log.warn event
    
    def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
    sendEvent(name: "lastCheckin", value: now)

    
    if (event) {
    	if (event.name == "power") {
            def powerValue
            powerValue = (event.value as Integer)            //TODO: The divisor value needs to be set as part of configuration
            sendEvent(name: "power", value: powerValue.toString())
        }
        else {
            sendEvent(event)
        }
    } else {    
        if (description?.startsWith("read attr -")) {
            def descMap = parseDescriptionAsMap(description)  
            log.warn "clusterId : " + descMap.cluster
            log.warn "attributeId : " + descMap.attrId
            log.warn "data : " + descMap.value
            if (descMap.cluster == "0702" && descMap.attrId == "0000") {
            	def energy
                name = "energy"			            
                value = String.format("%5.3f",zigbee.convertHexToInt(descMap.value) / 1000)
                energy = value
                log.warn "energy : " + value
                powerRefresh()
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

}

def identify() {
	log.debug "identify()"    
    return zigbee.command(0x0003, 0x00, "0a00")
}


def onOffConfig() {
	[
			"zdo bind 0x${device.deviceNetworkId} 1 ${endpointId} 6 {${device.zigbeeId}} {}", "delay 200",
			"zcl global send-me-a-report 6 0 0x10 0 600 {01}",
			"send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 1500"
	]
}

//power config for devices with min reporting interval as 1 seconds and reporting interval if no activity as 10min (600s)
//min change in value is 05
def powerConfig() {
	[
			//Meter (Power) Reporting
			"zdo bind 0x${device.deviceNetworkId} 1 ${endpointId} 0x0702 {${device.zigbeeId}} {}", "delay 200",
			"zcl global send-me-a-report 0x0702 0x0400 0x2A 0 1 {01}",
			"send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 1500",
            
            //Meter (Power) Reporting			
	//		"zcl global send-me-a-report 0x0702 0x0000 0x25 1 600 {05}",
	//		"send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 1500"                	
    
	]
}



def powerRefresh() {
    log.debug "powerRefresh()"
    zigbee.readAttribute(0x0702, 0x0400)
    //igbee.readAttribute(0x0702, 0x0000) + zigbee.readAttribute(0x0702, 0x0400)
}

def off() {
    zigbee.off()
    //runIn(30, powerRefresh)
}

def on() {
    zigbee.on()
    //runIn(30, powerRefresh)
}

def refresh() {
    //zigbee.onOffRefresh() + powerRefresh() + onOffConfig() + powerConfig()
    log.info "refresh()"
    //[
	//		"st rattr 0x${device.deviceNetworkId} ${endpointId} 6 0", "delay 500",			
	//		"st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0702 0x0400", "delay 500"
	//]
    zigbee.onOffRefresh() + zigbee.readAttribute(0x0702, 0x0400) + configure() 
}

def configure() {
    log.debug "in configure()"
    zigbee.onOffConfig() + zigbee.configureReporting(0x0702, 0x0400, 0x2A, 0, 60, 0x0001)
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



private String swapEndianHex(String hex) {
    reverseArray(hex.decodeHex()).encodeHex()
}

private byte[] reverseArray(byte[] array) {
    int i = 0;
    int j = array.length - 1;
    byte tmp;
    while (j > i) {
        tmp = array[j];
        array[j] = array[i];
        array[i] = tmp;
        j--;
        i++;
    }
    return array
}