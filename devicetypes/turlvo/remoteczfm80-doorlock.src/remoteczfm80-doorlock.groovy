/* Modified Remotec ZFM-80 specific device
 *
 * Variation of the stock SmartThings Relay Switch
 *	--auto re-configure after setting preferences
 *	--preference settings for switch type and automatic shutoff features.
 *
 *		
 * KuKu
 * turlvo@gmail.com
 * 2017-10-02
 *
	change log
 		2015-02-16 added delay between configuration changes, helps with devices further away from the hub.(MikeMaxwell)
        2015-02-21 fixed null error on initial install(MikeMaxwell)
        2015-06-22 added momentary on function for garage door controller(MikeMaxwell)
        2017-10-02 changed DTH type to DoorLock
*/

metadata {

	definition (name: "remotecZFM80_DoorLock", namespace: "turlvo", author: "KuKu") {
		capability "Actuator"
		capability "Lock"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
        
		fingerprint deviceId: "0x1003", inClusters: "0x20, 0x25, 0x27, 0x72, 0x86, 0x70, 0x85"
	}
    preferences {
        input name: "param1", type: "enum", title: "Set external switch mode:", description: "Switch type", required: true, options:["Disabled","Momentary NO","Momentary NC","Toggle NO","Toggle NC"]
       	input name: "param2", type: "enum", title: "Auto shutoff minutes:", description: "Minutes?", required: false, options:["Never","1","5","30","60","90","120","240"]
        input name: "isGD", type: "bool",title: "Enable Momentary on (for garage door controller)", required: false
        input name: "isGDDelay", type: "num",title: "Enable Momentary on dealy time(default 5 seconds)", required: false
    }

	// simulator metadata
	simulator {
		status "locked":  "command: 2003, payload: FF"
		status "unlocked": "command: 2003, payload: 00"

		// reply messages
		reply "2001FF,delay 100,2502": "command: 2503, payload: FF"
		reply "200100,delay 100,2502": "command: 2503, payload: 00"
	}

	// tile definitions
	tiles {
		standardTile("toggle", "device.lock", width: 2, height: 2) {
			state "locked", label:'locked', action:"lock.unlock", icon:"st.locks.lock.locked", backgroundColor:"#00A0DC"
            state "unlocked", label:'unlocked', action:"lock.lock", icon:"st.locks.lock.unlocked", backgroundColor:"#ffffff"
		}
		standardTile("lock", "device.lock", inactiveLabel: false, decoration: "flat") {
			state "default", label:'lock', action:"lock.lock", icon:"st.locks.lock.locked"
		}
		standardTile("unlock", "device.lock", inactiveLabel: false, decoration: "flat") {
			state "default", label:'unlock', action:"lock.unlock", icon:"st.locks.lock.unlocked"
		}

		main "toggle"
		details(["toggle", "lock", "unlock"])
	}
}

def installed() {
	zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
}

def parse(String description) {
	def result = null
	def cmd = zwave.parse(description, [0x20: 1, 0x70: 1])
	if (cmd) {
    	log.debug "parse : " + description
		result = createEvent(zwaveEvent(cmd))
	}
	if (result?.name == 'hail' && hubFirmwareLessThan("000.011.00602")) {
		result = [result, response(zwave.basicV1.basicGet())]
		log.debug "Was hailed: requesting state update"
	} else {
		log.debug "Parse returned ${result?.descriptionText}"
	}
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	[name: "lock", value: cmd.value ? "unlocked" : "locked", type: "physical"]
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	log.debug "zwaveEvent2 : " + cmd.value ? "unlocked" : "locked"
	[name: "lock", value: cmd.value ? "unlocked" : "locked", type: "digital"]
}


def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd) {
	[name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false]
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	if (state.manufacturer != cmd.manufacturerName) {
		updateDataValue("manufacturer", cmd.manufacturerName)
	}

	final relays = [
		[manufacturerId:0x5254, productTypeId: 0x8000, productId: 0x0002, productName: "Remotec ZFM-80"]
	]

	def productName  = null
	for (it in relays) {
		if (it.manufacturerId == cmd.manufacturerId && it.productTypeId == cmd.productTypeId && it.productId == cmd.productId) {
			productName = it.productName
			break
		}
	}

	if (productName) {
		//log.debug "Relay found: $productName"
		updateDataValue("productName", productName)
	}
	else {
		//log.debug "Not a relay, retyping to Z-Wave Switch"
		setDeviceType("Z-Wave Switch")
	}
	[name: "manufacturer", value: cmd.manufacturerName]
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in
	[:]
}

def unlock() {
	log.debug "unlock : "
	if (settings.isGD) {
    	//log.info "isGD: true"
        if (settings.isGDDelay == null || settings.isGDDelay == "" ) settings.isGDDelay = 5
    	delayBetween([zwave.basicV1.basicSet(value: 0xFF).format(),zwave.basicV1.basicSet(value: 0x00).format(),zwave.switchBinaryV1.switchBinaryGet().format()], (Integer.parseInt(settings.isGDDelay) * 1000))	
    } else {
    	//log.info "isGD: false"
		delayBetween([zwave.basicV1.basicSet(value: 0xFF).format(),zwave.switchBinaryV1.switchBinaryGet().format()])
    }
}

def lock() {
log.debug "lock : "
	delayBetween([zwave.basicV1.basicSet(value: 0x00).format(),	zwave.switchBinaryV1.switchBinaryGet().format()])
}

def poll() {
	zwave.switchBinaryV1.switchBinaryGet().format()
}

def refresh() {
	delayBetween([
		zwave.switchBinaryV1.switchBinaryGet().format(),
		zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
	])
}
//capture preference changes
def updated() {
    //log.debug "before settings: ${settings.inspect()}, state: ${state.inspect()}" 
    //"Disabled","Momentary NO","Momentary NC","Toggle NO","Toggle NC"
    
    //external switch function settings
    def Short p1 = 0
    switch (settings.param1) {
		case "Disabled":
			p1 = 0
            break
		case "Momentary NO":
			p1 = 1
            break
		case "Momentary NC":
			p1 = 2
            break
		case "Toggle NO":
			p1 = 3
            break
		case "Toggle NC":
			p1 = 4
            break
	}    
    
    
	//auto off
    def Short p2 = 0
    if ("${settings.param2}" == "Never") {
    	p2 = 0
    } else {
    	p2 = (settings.param2 ?: 0).toInteger()
    }
    
    if (p1 != state.param1)	{
        state.param1 = p1 
        return response(zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, configurationValue: [p1]).format())
    }
    
	if (p2 != state.param2)	{
        state.param2 = p2
        if (p2 == 0) {
       		return response (delayBetween([
				zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, configurationValue: [0]).format(),
        		zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, configurationValue: [0]).format(),
        		zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, configurationValue: [0]).format()
			]))
        } else {
        	return response (delayBetween([
				zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, configurationValue: [p2]).format(),
        		zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, configurationValue: [232]).format(),
        		zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, configurationValue: [0]).format()
			]))
        }
    }
	
	//log.debug "after settings: ${settings.inspect()}, state: ${state.inspect()}"
}

def configure() {
	delayBetween([
		zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, configurationValue: [3]).format(),
		zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, configurationValue: [0]).format(),
        zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, configurationValue: [0]).format(),
        zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, configurationValue: [0]).format()
	])
}