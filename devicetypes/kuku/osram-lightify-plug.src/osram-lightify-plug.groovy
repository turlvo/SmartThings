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
        fingerprint profileId: "C05E", inClusters: "0000, 0003, 0004, 0005, 0006, 0B04, FC0F", manufacturer: "OSRAM", model: "Plug 01", deviceJoinName: "OSRAM Lightify Plug"
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
    			attributeState("default", label:'Last Update: ${currentValue}',icon: "st.Health & Wellness.health9")
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
    def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
    sendEvent(name: "lastCheckin", value: now)
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
//    	log.debug msg     
//        log.warn "data: $msg.clusterId"
//    	if (msg.clusterId == 32801) { 
//			log.warn "data: $msg.data"
//            name = "power"
//            value = msg.data           
//        }
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
    zigbee.onOffRefresh() + powerRefresh()
}

def configure() {
    log.debug "in configure()"
    String zigbeeId = swapEndianHex(device.hub.zigbeeId)
    refresh() + zigbee.onOffConfig(0, 300) + powerConfig()
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

def powerConfig() {
	String zigbeeId = swapEndianHex(device.hub.zigbeeId)
	def configCmds = [  
        //Switch Reporting
        "zcl global send-me-a-report 6 0 0x10 0 3600 {01}", "delay 500",
        "send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 1000",
        
        //Power Reporting
        "zcl global send-me-a-report 0x0B04 0x0502 0x29 10 3600 {01 00 00 00}", "delay 200",
        "send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 1500",
      
        "zdo bind 0x${device.deviceNetworkId} ${endpointId} 1 6 {${device.zigbeeId}} {}", "delay 1000",
		"zdo bind 0x${device.deviceNetworkId} ${endpointId} 1 0x0B04 {${device.zigbeeId}} {}", "delay 200",
	]	
}

def powerRefresh() {
    [	
    "st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0B04 0x0000",
    "st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0B04 0x0304",
    "st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0B04 0x0802",    
    "st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0B04 0x090B",
    "st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0B04 0x0A0B",
	]
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