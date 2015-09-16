kazeproxy
========
An encrypted TCP chained proxy using LittleProxy


##Build
```
mvn clean package assembly:assembly
```


##Modules
###Proxy
####Server mode
```
##Proxy model
mode=server

##Ip and Port to bind
ip=0.0.0.0
port=12345

keystore.pw=kaze-proxy
```
####Client mode
```
##Proxy model
mode=client

##Ip and Port to bind
ip=0.0.0.0
port=8888

server-ip=your.server.com
server-port=12345

keystore.pw=kaze-proxy
```


####KeyStore

You should add `-pw password` param to the command above while using your own keystores.


Commands below show you how to generate your keystores.

```
keytool -genkey -alias serverkey -keystore kserver.jks -validity 3650
keytool -genkey -alias clientkey -keystore kclient.jks -validity 3650
keytool -export -alias serverkey -keystore kserver.jks -file server.crt
keytool -export -alias clientkey -keystore kclient.jks -file client.crt
keytool -import -alias serverkey -file server.crt -keystore tclient.jks
keytool -import -alias clientkey -file client.crt -keystore tserver.jks
```
You'd better use the same password for the keystore and cert files.

## LICENSE
Kazeproxy is under Apache License Version 2.0

For the purposes of licensing: JxBrowser library is not part of Kazeproxy, but it is a proprietary software. The use of JxBrowser is governed by JxBrowser Product Licence Agreement. If you would like to use JxBrowser in your development, please contact TeamDev team: teamdev.com/jxbrowser
