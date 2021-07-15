
/*
 * 
 *
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change History:
 *
 *    1.0.0 - 07/14/21 - Initial release
 *    1.1.0 - 07/15/21 - Added support for Gas Only measurements
 *    
 *
 */

static String version()	{  return '1.1.0'  }


metadata {
    definition (
		name: "Plaato Keg", 
		namespace: "mjnmixael", 
		author: "Mike Nelson",
	        importUrl:""
	) {
        capability "Sensor"
       
        //attribute "vpinReturn", "string"
		attribute "beerLeft", "decimal"
		attribute "gasLeft", "decimal"
		attribute "temperature", "decimal"
		attribute "leakDetected", "bool"
		attribute "isPouring", "bool"
        
		command "configure"
		command "refresh"
		//command "getPin",[[name:"pin*", type:"STRING", description:"Pin to retrieve"]]
    }   
}

preferences {
    input("auth_token","string", title: "Auth Token", required:true, submitOnChange:true)
	input("timer_pref","number", title: "Refresh Timer", description: "In minutes 1-60", required:false, defaultValue: 5, range: 1..60, submitOnChange:true)
    input("plaatoGas", "bool", title: "Gas measurement only?")
	input("debugEnable", "bool", title: "Enable debug logging?")
    input("security", "bool", title: "Hub Security Enabled", defaultValue: false, submitOnChange: true)
    if (security) { 
        input("username", "string", title: "Hub Security Username", required: false)
        input("password", "password", title: "Hub Security Password", required: false)
    }
}


def installed() {
	log.trace "installed()"
}

def configure() {
    if(debugEnable) log.debug "configure()"
    refresh()
}

def updateAttr(aKey, aValue){
    sendEvent(name:aKey, value:aValue)
}

def updateAttr(aKey, aValue, aUnit){
    sendEvent(name:aKey, value:aValue, unit:aUnit)
}

def initialize(){
	refresh()
}


def refresh(){
		timer = (timer_pref * 60)
		if(debugEnable) log.debug "Scheduling refresh in ${timer} seconds"
		runIn(timer, refresh)
		if(debugEnable) log.debug "Fetching data..."
		getRemaining()
		getTemp()
		getLeak()
		if(debugEnable) log.debug "Gas only toggle is ${plaatoGas}"
		if(plaatoGas != true ) getPouring()
		//updateAttr("vpinReturn"," ")
}


def getPin(pin){    
        makeGetHandler("$pin", "vpinReturn")	//Returns a string
}

def getRemaining(){    
		if(plaatoGas != true ) {
			makeGetHandler("v48", "beerLeft")	//Returns a number
		} else{
			makeGetHandler("v48", "gasLeft")	//Returns a number
		}
}

def getPouring(){    
        makeGetHandler("v49", "isPouring")	//Returns a string; 0 if no, 255 if yes
}

def getTemp(){    
        makeGetHandler("v56", "temperature")	//Returns a string
}

def getLeak(){    
        makeGetHandler("v83", "leakDetected")	//Returns a string; 0 if no leak, 1 if leak
}


def makeGetHandler(pin, attrName){
    if(security) {
		if(debugEnable) log.debug "Security enabled..."
        httpPost(
                [
                    uri: "http://remoteaccess.aws.hubitat.com",
                    path: "/login",
                    query: [ loginRedirect: "/" ],
                    body: [
                        username: username,
                        password: password,
                        submit: "Login"
                    ]
                ]
            ) { resp -> cookie = resp?.headers?.'Set-Cookie'?.split(';')?.getAt(0) }
     }    
        params = [
            uri: "http://plaato.blynk.cc",
            path: "/$auth_token/get/$pin",
            headers: [ "Cookie": cookie ],
        ]  
	
        asynchttpGet("sendGetHandler", params, [name: attrName])
}


def sendGetHandler(resp, data) {
    try {
        if(resp.getStatus() == 200) {
			if((data.name == "beerLeft") || (data.name == "gasLeft")) {
				pltoResp = resp.data.toFloat()
				pltoResp = pltoResp.round()
			} else if(data.name == "temperature"){
				pltoResp = resp.data.toString()
				pltoResp = pltoResp.substring(2,8).toFloat()
			} else if(data.name == "leakDetected"){
				pltoResp = resp.data.toString()
				pltoResp = pltoResp.substring(2,3).toInteger()
				if(pltoResp > 0){
					pltoResp = true
				} else
					pltoResp = false
			} else if(data.name == "isPouring"){
				pltoResp = resp.data.toString()
				pltoResp = pltoResp.substring(2,3).toInteger()
				if(pltoResp > 0){
					pltoResp = true
				} else
					pltoResp = false
			} else{
				pltoResp = resp.data.toString()
			}
    		if(debugEnable) log.debug "${data.name} returned ${pltoResp}"
	        updateAttr(data.name,pltoResp)
  	    } else if(resp.getStatus() != 200) {
            updateAttr(data.name,"Return Status: ${resp.getStatus()}")
			if(debugEnable) log.debug "${data.name} returned Error ${resp.getStatus()}"
		}
    } catch(Exception ex) { 
        updateAttr(data.name, ex)
    } 
}

def updated(){
	log.trace "updated()"
	if(debugEnable) runIn(1800,logsOff)
}

void logsOff(){
     device.updateSetting("debugEnable",[value:"false",type:"bool"])
}
