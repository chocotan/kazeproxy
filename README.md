kazeproxy
========
An encrypted TCP chained proxy using LittleProxy

##Build
```
mvn clean package assembly:assembly
```


##Run

```
usage:  [-h] [-m mode] [-p PORT] [-ip HOST] [-server-ip SERVER HOST] [-server-port SERVER PORT] [-pw KEYSTORE PW]

Kaze Proxy

optional arguments:
  -h, --help             show this help message and exit
  -m mode, --mode mode   Server or client model
  -p PORT, --port PORT   Http port to listen on
  -ip HOST, --host HOST  Ip address to listen on
  -server-ip SERVER HOST, --remort-host SERVER HOST
                         Server ip
  -server-port SERVER HOST, --remote-port SERVER PORT
                         Server port
  -pw KEYSTORE PW, --jks-passwd KEYSTORE PW
                         keystore password

```

###server mode
```
java -jar kazeproxy.jar -m server -p 12345
```

###client mode
```
java -jar kazeproxy.jar -m client -p 8888 -server-ip `your.server.host` -server-port `12345`
```


##KeyStore

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

