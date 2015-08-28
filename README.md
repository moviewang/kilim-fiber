This is a fiber implementation for java.  
It originates from the [kilim project](https://github.com/kilim/kilim),  
modified & simplified by @taowen,  
and I, pushed it into production use after fixing some annoying bugs.  

What a pity for we javaers could not using fiber/coroutines happily in our programming life.  
Even the most recent java release —— java8, has no fiber/coroutine support.  
And, we can't see any clue of this feature in the upcoming java9 plan, it's painful!  

Then this little lib —— kilim-fiber, would be a cure for this, at least in the near future.  

It's simple, fast and easy to use.  

It works smoothly in our high throughput online systems, which deals 0.2 billion requests per-day, under jdk1.6/1.7. So the correctness is now proven.  

For more documents and discussion, please refer to the wiki.  

—— notes by pf_miles

----------------------------------------------------------------------
This is a modified version
Only fiber related stuff remains, mailbox and scheduler has been removed. There is no implicit
multithreading, messaging or scheduling in this version.

----------------------------------------------------------------------

Kilim v1.0
Copyright (c) 2006 Sriram Srinivasan.
(kilim _at_ malhar.net)

----------------------------------------------------------------------

This software is released under an MIT-style licesne (please see the
License file). 

Please see docs/manual.txt and docs/kilim_ecoop08.pdf for a brief
introduction.

Please send comments, queries, code fixes, constructive criticisms to 
kilim _at_ malhar.net
