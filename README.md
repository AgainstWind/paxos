Paxos Implementation
====================

Provides a persistent log type construct. Not to be used for high-reliability 
systems at this point.

An example for using the framework can be found in the package `org.dancres.paxos.test.rest`.

`Backend.java` implements a basic key/value store accessed via a RESTful API. It uses most
features of the framework including clustering, checkpointing and recovery.

Detailed notes for the implementation of a service that uses the framework can be found in
the javadoc for `org.dancres.paxos.Paxos`
