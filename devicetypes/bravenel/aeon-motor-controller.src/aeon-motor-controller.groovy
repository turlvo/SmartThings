/**
*  Aeon Motor Controller
*
*  Copyright 2015 Bruce Ravenel
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
    definition (name: "Aeon Motor Controller", namespace: "bravenel", author: "Bruce Ravenel") {
        capability "Refresh"
        capability "Actuator"
        capability "Switch"
        capability "Switch Level"
        capability "Window Shade"
        capability "Health Check"

        command "up"
        command "down"
        command "stop"

        fingerprint deviceId: "0x1107", inClusters: "0x25 0x26 0x70 0x85 0x72 0x86 0xEF 0x82"
    }

    simulator {
        status "up":   "command: 2604, payload: FF"
        status "down": "command: 2604, payload: 00"
        status "stop": "command: 2605, payload: FE"

        ["FF", "FE", "00"].each { val ->
            reply "2001$val,delay 100,2602": "command: 2603, payload: $val"
        }
    }
    
    preferences {
        input title: "Operation Time", description: "The After execute up/down blind", displayDuringSetup: false, type: "paragraph", element: "paragraph"
        input "operationTime", "number", title: "Time(Seconds)", description: "", range: "*..*", displayDuringSetup: false
	}
    
    tiles {
        multiAttributeTile(name:"switch", type: "device.switch", width: 3, height: 2, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "default", label:'STOPPED', icon:"st.Transportation.transportation13", backgroundColor:"#79b821"
                attributeState "open", label:'open', action:"switch.off", icon:"st.doors.garage.garage-opening", backgroundColor:"#53a7c0"
                attributeState "closed", label:'closed', action:"switch.on", icon:"st.doors.garage.garage-closing", backgroundColor:"#ff0d00"
                attributeState "stopUp", label:'STOPPED', icon:"st.Transportation.transportation13", backgroundColor:"#79b821"
                attributeState "stopDn", label:'STOPPED', icon:"st.Transportation.transportation13", backgroundColor:"#79b821"
                attributeState "opening", label:'opening', icon:"st.doors.garage.garage-opening", backgroundColor:"#53a7c0"
                attributeState "closing", label:'closing', icon:"st.doors.garage.garage-closing", backgroundColor:"#ff0d00"
            }
            tileAttribute("device.lastCheckin", key: "SECONDARY_CONTROL") {
                attributeState("default", label:'Last Update: ${currentValue}', icon: "st.Health & Wellness.health9")
            }
        }

        standardTile("on", "device.switch",decoration: "flat", width: 2, height: 2) {
            state("default",label: "Up", action: "switch.on", icon:"http://cdn.device-icons.smartthings.com/thermostat/thermostat-up@2x.png")
        }
        standardTile("off", "device.switch", decoration: "flat", width: 2, height: 2) {
            state ("default", label: "Down", action: "switch.off", icon:"http://cdn.device-icons.smartthings.com/thermostat/thermostat-down@2x.png")
        }
        standardTile("stop", "device.switch",decoration: "flat", width: 2, height: 2) {
            state("default", label:"", action: "stop", icon:"http://cdn.device-icons.smartthings.com/sonos/stop-btn@2x.png")
        }
        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state ("default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh")
        }

        main(["switch"])
        details([ "switch", "on","off","stop","refresh",])
    }
}
// parse events into attributes
def parse(String description) {
    def result = []
    def cmd = zwave.parse(description,[0x20: 1, 0x26: 1])

    def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
    sendEvent(name: "lastCheckin", value: now)
    if (cmd) {
        result = zwaveEvent(cmd)
        log.debug("'$description' parsed to ${result[0]}")
        if (result[0].value == "closing" || result[0].value == "opening") {
        	log.debug "it's up/down event"
            runIn(30, done)
        }
    } else {
        log.debug("Couldn't zwave.parse '$description'")
    }
    return result
}


def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd) {
	log.debug " zwaveEvent: cmd: $cmd"
    def result = []
    if (state.stp==false){
        if(cmd.value == 0) {
            result << createEvent(name: "switch", value: "closing")
        }
        else if(cmd.value == 255 || cmd.value == 99) {
            result << createEvent(name: "switch", value: "opening")
        }
    }
    else {
        def stopVal = state.up ? "open" : "closed"
        result << createEvent(name: "switch", value: stopVal)
    }
    return result
}

def refresh() {
    delayBetween([
        zwave.switchMultilevelV1.switchMultilevelGet().format(),
    ], 2000)
}

def stop() {
	log.debug ("stop")
    state.stp=true
    delayBetween([
        zwave.switchMultilevelV1.switchMultilevelStopLevelChange().format(),
        zwave.switchMultilevelV1.switchMultilevelGet().format(),
    ], 2000)

}

def done() {
	log.debug ("done")
    if (state.stp==false){
        zwave.switchMultilevelV1.switchMultilevelStopLevelChange().format()
        if (state.up == true) {
            sendEvent(name: "switch", value: "open")        
        } else {
            sendEvent(name: "switch", value: "closed")
        }
    }
}

def on() {
log.debug ("on")
    state.up = true
    state.stp=false
    delayBetween([
        zwave.switchMultilevelV1.switchMultilevelSet(value: 0xFF).format(),
        zwave.switchMultilevelV1.switchMultilevelGet().format()
    ], 2000)
}

def off() {
log.debug ("off")
    state.up = false
    state.stp=false
    delayBetween([
        zwave.switchMultilevelV1.switchMultilevelSet(value: 0x00).format(),
        zwave.switchMultilevelV1.switchMultilevelGet().format()
    ], 2000)
}