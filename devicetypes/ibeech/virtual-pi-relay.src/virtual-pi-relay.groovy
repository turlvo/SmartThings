/**
 *  Pi Relay Control
 *
 *  Copyright 2016 Tom Beech
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
	definition (name: "Virtual Pi Relay", namespace: "ibeech", author: "ibeech") {
		capability "Switch"
        capability "Refresh"
		capability "Polling"
        capability "Lock"
        capability "Door Control"
        
        command "changeSwitchState", ["string"]
	}

	simulator {
		// TODO: define status and reply messages here
	}

    preferences {
        input name: "momentaryOn", type: "bool",title: "Enable Momentary on (for garage door controller)", required: false
        input name: "momentaryOnDelay", type: "num",title: "Enable Momentary on dealy time(default 3 seconds)", required: false
    }
    
	tiles {    
		standardTile("toggle", "device.lock", width: 2, height: 2) {
			state "locked", label:'locked', action:"lock.unlock", icon:"st.locks.lock.locked", backgroundColor:"#00A0DC"
            state "unlocked", label:'unlocked', action:"lock.lock", icon:"st.locks.lock.unlocked", backgroundColor:"#ffffff"
		}    
        
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 1, height: 1) {
			state("default", label:'refresh', action:"polling.poll", icon:"st.secondary.refresh-icon")
		}

		main "toggle"
		details (["toggle", "refresh"])
	}
}

// parse events into attributes
def parse(String description) {
	log.debug "Virtual siwtch parsing '${description}'"
}

def poll() {
	log.debug "Executing 'poll'"   
        
        def lastState = device.currentValue("switch")
    	sendEvent(name: "switch", value: device.deviceNetworkId + ".refresh")
        sendEvent(name: "lock", value: lastState);
}

def refresh() {
	log.debug "Executing 'refresh'"
    
	poll();
}

def unlock() {
	log.debug "Executing 'on'"	     
  
    sendEvent(name: "lock", value: "unlocked");        
    sendEvent(name: "switch", value: device.deviceNetworkId + ".on");    

    
	if (momentaryOn) {
    	if (settings.momentaryOnDelay == null || settings.momentaryOnDelay == "" ) settings.momentaryOnDelay = 3
    	log.debug "momentaryOnHandler() >> time : " + settings.momentaryOnDelay
    	runIn(Integer.parseInt(settings.momentaryOnDelay), momentaryOnHandler, [overwrite: true])
    }
}

def lock() {
	log.debug "Executing 'off'"
	    
	sendEvent(name: "switch", value: device.deviceNetworkId + ".off");     
    sendEvent(name: "lock", value: "locked");
}

def changeSwitchState(newState) {

	log.trace "Received update that this switch is now $newState"
	switch(newState) {
    	case 1:
			sendEvent(name: "lock", value: "unlocked")
            break;
    	case 0:
        	sendEvent(name: "lock", value: "locked")
            break;
    }
}


def momentaryOnHandler() {
	log.debug "momentaryOnHandler()"
    sendEvent(name: "switch", value: device.deviceNetworkId + ".off");  
    refresh()
}