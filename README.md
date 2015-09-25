# jirecon

Jitsi Recording Container. A standalone recording container for Jitsi Videobridge conferences that would allow us to run a video recorder as a separate MUC participant.

----------

## Environment requirement 
1. 64-bit MacOS or Linux
2. JRE 1.6 (or latest)

## Usage
1. Jirecon
   Run jirecon.sh
2. Jirecon-component
   Run component.sh

## What can Jirecon do?
Jirecon is a server-side application to record audio/video streams and meta data of a specified Jitsi Meet conference into local files. 

## How can Jirecon record a conference?
In a nutshell, Jirecon firstly joins the conference as a dummy participant, then it captures various audio/video streams and outputs them into local files. 

## Top-down introduction
Jirecon can record different conferences simultaneously. Each recording progress is called a "task". There is a "task manager" which acts as a user interface for starting or stopping these tasks.
```
/*
 *                      /     +------+
 *    +------------+    |   +------+ |
 *    |task manager|<---| +------+ | +
 *    +------------+    | | task | +
 *                      | +------+
 *                      \
 *
 *        pic 1. TaskManager and tasks
 */
```

The task firstly joins the conference as a dummy participant, captures various audio/video streams and outputs them into local files. So its behavior is similar to a Jitsi Meet participant. 

Roughly speaking, there are three steps to record a conference:
  1.  Build [Jingle session][1] with Jitsi Meet conference.
  2.  Receiving audio/video streams
  3.  Recording audio/video streams
  
The task is composed of two important parts: "Jingle session manager" and "stream recorder manager". The "Jingle session manager" is responsible for handling session stuff and the "stream recorder manager" is responsible for handling receiving & recording stuff.

Here is what the Jingle session manager does, just like Jitsi Meet client:
  1.  Join MUC, wait for session-init packet.
  2.  Handle session-init packet. Harvest local candidates, establish ICE connectivity and construct session-accept packet.
  3. Send back session-accept packet, wait for ack packet.
  4. Monitor presence packet, see whether anyone join or leave the conference.
  
As for "Stream recorder manager", it's pretty simple too:
  1. Receive media streams.
  2. Record media streams.
  3. Record meta data.
  
The meta data is actually a human readable message that describes the whole recording procedure. We'll talk about it later.

So we got a primitive class graph:
```
/*
 *   +--------------------+    +---------------------+
 *   |JingleSessionManager|    |StreamRecorderManager|
 *   +--------------------+    +---------------------+
 *            |                           |
 *            |          +------+         |
 *            +--------->| task |<--------+
 *                       +------+
 *
 *             pic 2. basic class structure
 */
```


## Some details
### Managers
You may have noticed that there are so many "manager"s in Jirecon project. Well, that's because most classes in Jirecon are organized hierarchically, in other words, under "mediator pattern". For example:
```
/*
 *                   +-----------+
 *                   |TaskManager|
 *                   +-----------+
 *                         |
 *                      +----+
 *                      |Task|
 *                      +----+
 *                     /      \
 *  +--------------------+  +---------------------+
 *  |JingleSessionManager|  |StreamRecorderManager|
 *  +--------------------+  +---------------------+
 *                                    |
 *                                +--------+
 *                                |Recorder|
 *                                +--------+
 *
 *            pic 3. Class architecture
 */
```

Upper class can access lower class directly, but not the other way. Lower class can only send message to upper class through event mechanism. That's why most of the "manager"s implement listener-interface.

### Recording Procedure
The StreamRecorderManager reuses audio/video recorders in libjitsi. Audio streams and video streams are recorded separately in two recorders, audio recorder and video recorder. The recorder can recognize different media streams from different conference participants by its "ssrc" property. Here is a rough graph.
```
/*
 *                          \
 *          +-----------+   |
 *      +------------+  |   |     +---------+     +-----------+
 *   +------------+  |--+   |====>|Connector|====>|MediaStream|
 *   | RTP stream |--+      |     +---------+     +-----------+
 *   +------------+         |                           |
 *                          /                           |
 *                                                      |
 *                          \                           |
 *                 +----+   |                          \/
 *               +----+ |   |     +--------+     +----------+
 *             +----+ |-+   |<====|Recorder|<----|Translator|
 *             |file|-+     |     +--------+     +----------+
 *             +----+       |
 *                          /
 *
 *            pic 4. Recording procedure
 */
```


### Output files
In configuration files, you can specify where to save Jirecon output. Each task will create a new sub-directory and output all files into it. The sub-directory's name will be set with conference's jid and current timestamp, so there is no duplicate-name problem. 
In Jirecon, audio and video streams are recorded in different files, and each participant will be recorded separately. So a task will generally output many files. The output format of audio stream is mp3 and vp8 for video stream, it's determined by the specific recorder used in StreamRecorderManager.
Each media stream file will be named with its media stream's "ssrc" property indicating different participant, the meta data file will be just name with "metadata.json".

### Meta data
Meta data is actually a human readable JSON string. It contains three events:
  1. Recording started
  2. Recording ended
  3. Speaker changed
  
The actual metadata.json looks like:
```
{
  "audio" : [
    {"ssrc":3230188012,"rtpTimestamp":1247816712,"filename":"3230188012.mp3","type":"RECORDING_STARTED","instant":1408603362473,"mediaType":"audio"},
    {"ssrc":351458388,"rtpTimestamp":1247834047,"filename":"351458388.mp3","type":"RECORDING_STARTED","instant":1408603382142,"mediaType":"audio"},
    {"ssrc":2447521424,"audioSsrc":351458388,"endpointId":"60ae643e","type":"SPEAKER_CHANGED","instant":1408603394284,"mediaType":"audio"}
  ],

  "video" : [
    {"ssrc":1239366168,"rtpTimestamp":2112684268,"filename":"1239366168.webm","type":"RECORDING_STARTED","instant":1408603364693,"mediaType":"video"},
    {"ssrc":2447521424,"rtpTimestamp":2116293110,"filename":"2447521424.webm","type":"RECORDING_STARTED","instant":1408603381658,"mediaType":"video"}
  ]
}
```
All events are generated by recorders except speaker changed event. It's generated by the VideoBridge and sent to Jirecon through the DTLS/SCTP protocol.

### XMPP component
Jirecon can also works as an XMPP server external component, so users can simply click a button in Jitsi Meet to start/stop a recording task. You can run component.sh to start Jirecon as an XMPP component. We designed a new protocol to do this, and the brief introduction can be in class "XMPPComponent".


[1]: http://www.xmpp.org/extensions/xep-0166.html "Jingle protocol"
