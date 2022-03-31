# IBKR TWS API

Still experimental, but usable for connecting to TWS/IB Gateway and using:

+ Account Profit and Loss
+ Single Profit and Loss
+ Account Summary
+ Acciybt Updates
+ Contract Quotes
+ Matching Symbols (Search for Contracts)
+ Positions


Basic concept for requests with RqId.

+ Make a Request Class Instance  (RqCI) -> RqCI.submit returns ticket -> Data Routed to Ticket

There are a few types of request that work on a "current account" basis. These currently do a 
callback to ticket, but moving/thinking to have a Pub/Sub model to a queue.

The idea is you take this library and override existing *Rq *Ticket classes or create 
new ones from the existing traits. So, want to move more to EWrapper/IBWrapper actually
takes a  function value that is either regular function or a "method function" in a class.


Scala 3 / Cats3 / FS2 / Scribe / WeaverTest  is main stack. Intention is for this to be a library
not an application framework. See TWDevMain for an example app.

Trying to keep in ScalaJS/JVM compatable so can write web-apps that call the local gateway
to gather and dipslay information.

Some challenges dealing with async but even more in dealing with keeping strong types.
Evolving slowly...ss

(Insert linkes to IB documentation to setup IB Gateway or TWS App)

* https://interactivebrokers.github.io/tws-api/  API Reference
* https://www.interactivebrokers.com/en/index.php?f=5039

## TWS API
- API used through IBKR TWS / IB Gateway Apps
- This is the older lower level Java API where a thread for sending, a thread for receiving and program threads are used.


# ibkr-tws-client-lib




## Overall Architecture

This leverages the IBKR Java Client, which basically has a client that 
requests are sent to and an `EWrapper` that responses/inbound message are sent to.

There are two main patterns:

1) Ticket # Req => EClient => Gateway => Server => Gateway => EWrapper => Handler
2) Same, but the Req results in a subscription with multiple respones.

We implement IBClient and IBWrapper components for EClient and EWrapper.

The paradigm is a request creates a `Ticket` which has the ability to submit
the request and handle responses to the request. It can also cancel the requests/subscriptions.

When actioned, the ticket registers itself with the IBWrapper, and responses are routed
to it. 

## Action Types

### One-Off Actions (Single Response)
For one-off responses internally we are wrapping the response in a Cats deferred.
Will wrap that one level higher to make a ActionRequets: Request => FulfilledDeferred

### Subscriptions (Inifinite Stream)
For subcriptions going to try to have a Request =Ticket, and Ticket has FS2 Stream, via adding a 
Queue in front of Stream.

### Hybrid (Bounded/Finite Stream)
There are intermediate forms, where there is really only one respone, but it is spread over everal
response messages. Could aggregate responses and do a Requests => Fulfilled but actually putting
on a stream may be better. Then see if we can wrap the stream in a Fulfilled type thing.

MatchingSymbols is an example of this, earch for GOOGLE and it returns N results with contract 
information (which is static). It streams just one set of data. For UI its probably
better to stream and update, but internally better to get a completed list of results.


### Special Cases

Not there are lot of "side-cases" please see IBWrapper.
For example, Account PnL ?



