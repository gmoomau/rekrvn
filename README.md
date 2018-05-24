rekrvn
==============
a bot

Usage
--------------
list modules you want enabled in config.clj
eg. "example" will add modules/example.clj
    "irc.client" will add modules/irc/client.clj

See complete example in config.clj.example

Writing a Module
--------------
(:require rekrvn.hub)

publish events via (rekrvn.hub/broadcast content replyfn)
- content is a string beginning with a one-word identifier for your module
- replyFn is a function to be used by other modules in order to respond to published events

It takes two arguments, modId and msg. modId is a string name for the module calling replyFn and msg is the module's response.

For example, the irc module provides a replyFn that sends message to the channel from which
the message originated.

subscribe to events/messages via (rekrvn.hub/addListener modname matcher actFn)
- modname is the name of the module adding the listener
- matcher is an re-pattern to match applicable publications
- actFn is the function to be called on matching publications. As arguments, it takes a list containing the parenthesized groups in matcher and a replyFn.

See modules/example.clj for examples.



Copyright (C) 2012

License: MIT
