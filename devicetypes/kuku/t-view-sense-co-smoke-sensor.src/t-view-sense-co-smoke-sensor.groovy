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
        
        
        valueTile("coVal", "device.coVal", width: 2, height: 2) {
            state "coVal", label:'${currentValue}', defaultState: true
        }

 		standardTile("reset", "device.smoke", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'Clear', action:"clear"
		}
        
 		standardTile("test", "device.smoke", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'Test', action:"test"
		}  

        main "smoke"
		details(["smoke", "coVal", "reset", "test"])
	}
}

def parse(String description) {
	log.debug "description: $description"
    
	def event = zigbee.getEvent(description)
    def name = "smoke"
    def value
    def coValue
    
    if (description?.startsWith('catchall: 0104 0500')) {			// For CO Sensor
        // CO detected data is endsWith below value
        // 1E00(30), 3200(50), 6400(100), 9600(150), C800(200), FA00(250), 2C01(300), 5E01(350), 9001(400), E803(1000)
        if (description?.endsWith("64B0")
        	|| description?.endsWith("0000")) {        	
            log.info "smoke clear"                
            value = "clear"
            coValue = 0
        } else {
            log.info "smoke detected"
            def lastState = device.currentValue("smoke")
            value = "detected"
            if (description?.endsWith("1E00")) {
                coValue = 30
            } else if (description?.endsWith("3200")) {
                coValue = 50
            } else if (description?.endsWith("6400")) {
                coValue = 100
            } else if (description?.endsWith("9600")) {
                coValue = 150
            } else if (description?.endsWith("C800")) {
                coValue = 200
            } else if (description?.endsWith("FA00")) {
                coValue = 250
            } else if (description?.endsWith("2C01")) {
                coValue = 300
            } else if (description?.endsWith("5E01")) {
                coValue = 350
            } else if (description?.endsWith("9001")) {
                coValue = 400
            } else if (description?.endsWith("F401")) {
                coValue = 500
            } else if (description?.endsWith("E803")) {
                coValue = 1000
            }
            sendEvent(name: "coVal", value: coValue);
        }
        def result = createEvent(name: "smoke", value: value, descriptionText: "$device.displayName CO is $coValue!")        
        return result
    } else if (description?.startsWith("read attr -")) {			// For Smoke Sensor
        // Smoke detected data is '0001', clear data is other data '0b04', '0000'
        def descMap = parseDescriptionAsMap(description)  
        log.warn "clusterId : " + descMap.cluster
        log.warn "attributeId : " + descMap.attrId
        log.warn "data : " + descMap.value
        if (descMap.cluster == "0B03" && descMap.attrId == "0000") {
            if (descMap.value == "0000") {
                log.info "smoke clear"   
                value = "clear"
            } else if (descMap.value == "b064") {
            	// It looks like clear but ignore: b064
            	return
            } else {
                log.info "smoke detected"
                value = "detected" 
            }
        } else {
        	return
        }
        def result = createEvent(name: "smoke", value: value, descriptionText: "$device.displayName smoke is $value!")        
        return result
    }

 

}
 
def test() {
	log.debug "test()"
    sendEvent(name: "coVal", value: 500);
	sendEvent(name: "smoke", value: "detected", descriptionText: "$device.displayName smoke detected!")
}


def clear() {
	log.debug "clear()"
    sendEvent(name: "coVal", value: 0);
	sendEvent(name: "smoke", value: "clear", descriptionText: "$device.displayName clear")
}

def parseDescriptionAsMap(description) {
	(description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
}

def configure() {
    log.debug "Configuring Reporting and Bindings."
    // Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
    // enrolls with default periodic reporting until newer 5 min interval is confirmed
    sendEvent(name: "checkInterval", value: 2 * 10 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
	
    // OnOff minReportTime 0 seconds, maxReportTime 5 min. Reporting interval if no activity
    refresh() + configureReporting(0x0001, 0x0020, 0x20, 30, 21600, 0x01)
}

def refresh() {
	log.debug "Refreshing Battery"
    def endpointId = 0x01
	[
	    "st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0000 0x0000", "delay 200"
	] //+ enrollResponse()
}