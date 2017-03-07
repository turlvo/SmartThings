/**
 *  SKT Sense Motion Sensor
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
	definition (name: "T View Sense Motion Sensor", namespace: "KuKu", author: "KuKu") {
		capability "Battery"
		capability "Configuration"		
		capability "Sensor"
        capability "Motion Sensor"
        capability "illuminanceMeasurement"
        
        fingerprint endpointId: "1", profileId: "0104", inClusters: "0000, 0003, 0400, 0406", manufacturer: "LGI", model: "TWSZ_P001N-Fv1.", deviceJoinName: "T View Sense Contact Sensor"
	}


	simulator {
		// TODO: define status and reply messages here
	}

// UI tile definitions
	tiles {
        multiAttributeTile(name:"motion", type: "generic", width: 6, height: 4){
			tileAttribute ("device.motion", key: "PRIMARY_CONTROL") {
				attributeState "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
				attributeState "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
			}
		}        
        
        valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false) {
			state "battery", label:'${currentValue}% battery'
		}


		main (["motion"])
		details(["motion"])
	}
}

// Parse incoming device messages to generate events
def parse(String description) {
    log.debug "description is $description"
 	def name = null
	def value = null   
    
    def event = zigbee.getEvent(description)
    log.warn event
    
    if (event) {
        sendEvent(event)
    } else if (description?.startsWith("illuminance:")) {    	
		def eventValue = description?.endsWith(" 257") ? "active" : "inactive"
        sendEvent(name: "motion", value:eventValue)
        log.debug "Run 10 seconds timer"            
        runIn(10, handler)
    } else {
        log.warn "DID NOT PARSE MESSAGE for description : $description"
        log.debug zigbee.parseDescriptionAsMap(description)
    }

}

def handler(time) {
    sendEvent(name:"motion", value:"inactive")
}

def configure() {
    log.debug "Configuring Reporting and Bindings."
    return zigbee.configureReporting(0x0406, 0x0000, 0x18, 0, 3600, 1)
}

def refresh() {
    log.debug "Refreshing"
    def refreshCmds = [
    	zigbee.readAttribute(0x0400, 0x0000), "delay 200",   
        zigbee.readAttribute(0x0406, 0x0000), "delay 200",   
    ]
    return refreshCmds + configure() //send config as part of the refresh
}

def parseDescriptionAsMap(description) {
	(description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
}