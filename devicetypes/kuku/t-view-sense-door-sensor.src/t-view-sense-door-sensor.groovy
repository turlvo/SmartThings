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
    definition (name: "T View Sense Door Sensor", namespace: "KuKu", author: "KuKu") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Switch"
        capability "Switch Level"
        capability "Health Check"        
       
        fingerprint endpointId: "1", profileId: "0104", inClusters: "0000, 0003, 0006", outClusters: "0003, 0004, 0019", manufacturer: "LGI", model: "TWSZ_D001N-Fv1.", deviceJoinName: "T View Sense Door Sensor Device "
    }

    tiles(scale: 2) {
    	multiAttributeTile(name:"contact", type: "generic", width: 6, height: 4){
			tileAttribute ("device.contact", key: "PRIMARY_CONTROL") {
				attributeState "open", label: '${name}', icon: "st.contact.contact.open", backgroundColor:"#ffa81e"
				attributeState "closed", label: '${name}', icon: "st.contact.contact.closed", backgroundColor:"#79b821"
			}
		} 
        
        valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery'
		}
        main "contact"
        details(["contact", "battery"])
    }
}

// Parse incoming device messages to generate events
def parse(String description) {
    log.debug "description is $description"
	
    def value = (description - "on/off: ") as Integer
    log.debug "value: " + value
    
    if (value > 1) {
    	sendEvent(name:"battery", value:value)	
    } else {
        def event = zigbee.getEvent(description)
        if (event) {
            if (event.name=="switch") {
                log.info "value: " + event.value
                def changedValue
                if (event.value == "on") {
                    changedValue = "open"
                } else {
                    changedValue = "closed"
                }
                sendEvent(name:"contact", value:changedValue)
            } else {
                sendEvent(event)
            }
        } else {        
            log.warn "DID NOT PARSE MESSAGE for description : $description"
            log.debug "${cluster}"
        }    
    }
}


def configure() {
    log.debug "Configuring Reporting and Bindings."
    // Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
    // enrolls with default periodic reporting until newer 5 min interval is confirmed
    sendEvent(name: "checkInterval", value: 2 * 10 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])

    // OnOff minReportTime 0 seconds, maxReportTime 5 min. Reporting interval if no activity
    refresh() + zigbee.onOffConfig(0, 300) + zigbee.levelConfig()
}