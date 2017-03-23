/**
 *  T View Sense CO/Smoke Sensor
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
	definition (name: "T View Sense CO/Smoke Sensor", namespace: "KuKu", author: "KuKu") {
		capability "Battery"
		capability "Configuration"
        capability "Smoke Detector"
		capability "Sensor"
        
        command "test"
   		command "clear"
        
        fingerprint endpointId: "1", profileId: "0104", inClusters: "0000, 0500, 0003", manufacturer: "LGI", deviceJoinName: "T View Sense CO Sensor"
        fingerprint endpointId: "1", profileId: "0104", inClusters: "0000, 0B03, 0003", manufacturer: "LGI", deviceJoinName: "T View Sense Smoke Sensor"
	}



	simulator {
		// TODO: define status and reply messages here
	}

// UI tile definitions
	tiles {
        multiAttributeTile(name:"smoke", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.smoke", key: "PRIMARY_CONTROL") {
                attributeState("clear", label:"CLEAR", icon:"st.alarm.smoke.clear", backgroundColor:"#ffffff")
                attributeState("detected", label:"SMOKE", icon:"st.alarm.smoke.smoke", backgroundColor:"#e86d13")
                attributeState("tested", label:"TEST", icon:"st.alarm.smoke.test", backgroundColor:"#e86d13")
                attributeState("replacement required", label:"REPLACE", icon:"st.alarm.smoke.test", backgroundColor:"#FFFF66")
                attributeState("unknown", label:"UNKNOWN", icon:"st.alarm.smoke.test", backgroundColor:"#ffffff")
        	}
        }

 		standardTile("reset", "device.smoke", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'Clear', action:"clear"
		}
        
 		standardTile("test", "device.smoke", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'Test', action:"test"
		}  

        main "smoke"
		details(["smoke", "reset", "test"])
	}
}

def parse(String description) {
	log.debug "description: $description"
    
	def event = zigbee.getEvent(description)
    if (event) {
    	sendEvent(event)
    } else {    	
        if (description?.startsWith('catchall: 0104 0500')) {			// For CO Sensor
        	// CO detected data is endsWith below value
            // 6400, 9600, 1E00, FA00, C800, 9600, 5E01, 9001, 2C01
            if (description?.endsWith("64B0")
            	|| description?.endsWith("0000")) {
            	log.info "smoke clear"                
                sendEvent(name: "smoke", value: "clear")
            } else {
                log.info "smoke detected"
                def lastState = device.currentValue("smoke")
                if (lastState != "detected") {
                	sendEvent(name: "smoke", value: "detected")
                }
            }
        } else if (description?.startsWith("read attr -")) {			// For Smoke Sensor
        	// Smoke detected data is '0001', clear data is other data '0b04', '0000'
            def descMap = parseDescriptionAsMap(description)  
            log.warn "clusterId : " + descMap.cluster
            log.warn "attributeId : " + descMap.attrId
            log.warn "data : " + descMap.value
            if (descMap.cluster == "0B03" && descMap.attrId == "0000") {
            	if (descMap.value == "0001") {
                	log.info "smoke detected"
                	sendEvent(name: "smoke", value: "detected")
                } else {
                	log.info "smoke clear"   
                    def lastState = device.currentValue("smoke")
                    if (lastState != "clear") {
                		sendEvent(name: "smoke", value: "clear")
                    }
                }
            }
        }
    }  	

}
 
def test() {
	log.debug "test()"
	sendEvent(name: "smoke", value: "detected", descriptionText: "$device.displayName smoke detected!")
}


def clear() {
	log.debug "clear()"
	sendEvent(name: "smoke", value: "clear", descriptionText: "$device.displayName clear")
}

def parseDescriptionAsMap(description) {
	(description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
}