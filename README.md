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


Scala 3 / Cats3 / FS2 / Scribe is main stack. Intention is for this to be a library
not an application framework. See TWDevMain for an example app.

(Insert linkes to IB documentation to setup IB Gateway or TWS App)

* https://interactivebrokers.github.io/tws-api/  API Reference
* https://www.interactivebrokers.com/en/index.php?f=5039

## TWS API
- API used through IBKR TWS / IB Gateway Apps
- This is the older lower level Java API where a thread for sending, a thread for receiving and program threads are used.


# ibkr-tws-client-lib
