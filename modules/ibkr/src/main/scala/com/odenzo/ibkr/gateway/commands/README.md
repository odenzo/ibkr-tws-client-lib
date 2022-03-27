# Commands

We have a write to IBKR and a reader from IBKR per client connection.

Read is IBWrapper from EWrapper that takes a series of callbacks, kind of a subscription
model for most stuff. Some have to explicitly unsubscibe, others don't.

IBWrapper will be responsible for forwarding a callback to the corresponding
Command based on Request ID.

1 IB Wrapper per Client/Connection.

Command will be called on IBWrapper thread, but should treat this thread like a 
dispatch thread and do short lived stuff or fork another thread.
(Thread == Fiber)

Can throw common stuff in trait/super-class, or Typeclass to experiment.


+ Start with a request. The request is submitted via IBClient and creates a 
tick that binds over the IBClient. 