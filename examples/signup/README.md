# Conference Session Signup refactoring exercise

_From mutable beans, to immutable values, to unrepresentable illegal states_

## Scenario

The code we are working on implements sign-up for conference sessions.

```plantuml
:Attendee: as a
:Presenter: as p
:Admin: as b

component "Attendee's Phone" as aPhone {
    component [Conference App] as aApp
}
component "Presenter's Phone" as pPhone {
    component [Conference App] as pApp
}
component "Web Browser" as bBrowser
component "Conference Web Service" as webService

a -right-> aApp : "sign-up\ncancel sign-up"
aApp -down-> webService : HTTP

p -left-> pApp : "close signup\nlist attendees"
pApp -down-> webService : HTTP

b -right-> bBrowser : "add sessions\nlist attendees"
bBrowser -right-> webService : HTTP
```

An admin user creates sign-up sheets for sessions in an admin app (not covered in this example).
Sessions have limited capacity, set when the sign-up sheet is created.
Attendees sign up for sessions via mobile conference app.
Admins can also sign attendees up for sessions via the admin app.
The session presenter starts the session via the mobile conference app.  After that, the sign-up sheet cannot be changed.

## Simplifications

The code is simplified for the sake of brevity and clarity:

* It doesn't cover some edge cases.  The techniques we will use in the exercise apply equally well to those too.
* It doesn't include authentication, authorisation, monitoring, tracing, etc., to focus on the topic of the exercise.


## Review the Kotlin code in the conf.signup.server package

Classes:

* `SignupSheet` manages the sign-up for a single conference session

* A `SignupBook` is a collection of signup sheets for all the sessions in the conference
  * Hexagonal architecture: the `SignupBook` interface is defined in the application domain model and hides the choice of technical persistence layer
  * Sheets are added to the signup book out-of-band by an admin app, which is not shown in the example code.

* `SignupApp` implements the HTTP API by which a front-end controls the SignupSheet.
  * Routes on request path and method
  * Supports attendee sign up and cancellation, closing signup to a session, and listing who is signed up.
  * Translates exceptions from the SignupSheet into HTTP error responses
  * SignupApp is a typical Http4k handler... although Http4k implements the "server as a function" pattern, the handler is not a pure function. It reads and writes to the SignupSheet, which is our abstraction for the database.

* SessionSignupAppTests: behaviour is tested at the HTTP API, with fast, in-memory storage.
  * The InMemorySignupBook and InMemoryTransactor emulate the semantics of the persistence layer

* SignupServer:
  * Example of a real HTTP server storing signups in memory.  You can run this and play with the server using IntelliJ's HTTP requests plugin.


NOTE: The name of the SignupApp function follows Http4k conventions. An "App" includes the HTTP routing and business logic. A "Server" runs the App in an HTTP server, with an HTTP "Stack" that provides monitoring, authentication flows, etc.


## Getting started

Run the tests to confirm that they pass.

Now... Let's refactor this to *idiomatic*, *type safe* Kotlin.


### What code smells?

The class is a Kotlin implementation of Java style "bean" code.

* Essential behaviour implemented by mutation (signups)
* Also _inappropriate_ mutation (e.g. sessionId, capacity)
* Uses exceptions for error recovery. The type checker does not help us know...
  * if we have handled all possible errors
  * if we have error handling code that will never be invoked

Wouldn't it be better if the client code could NOT use the object incorrectly?

We can make that happen by refactoring to represent different states at the type level, and only define operations on those types that are applicable for the states they represent.

[Show animation]

However, we cannot do that while the code uses mutable state... Kotlin cannot represent _dynamic_ aspects of mutable state in its _static_ type system. To introduce type safety, we must first remove mutation.  That gives us our refactoring strategy:

## Refactoring task

* Eliminate unnecessary mutability
  * ... to make the challenge easier to see
* Refactor to a _functional_ design
  * ... to make improving type safety possible
  * ... moving mutation to the edge
* Improve type safety
  * ... moving error handling to the edge


We'll start by working in the domain model and work outwards towards the HTTP layer.

1. Make it not a bean
2. Make it an immutable data class.
3. Convert the immutable data class into a sealed class hierarchy in which subclasses represent different states, and operations are only defined on states for which they are applicable.



## Part 1: Remove unnecessary immutability

Use Alt-F7 to find usages of the no-arg constructor. There is only one, in SignupServer.

In SignupServer, replace the mutation of the sheet with a call to the secondary constructor and inline the `sheet` variable.

We don't have a test for the server — it itself is test code — but COMMIT! anyway.  The use of domain-specific value types means that the type checker gives us good confidence that this refactor is correct.

We can now delete the no-arg constructor.

* Alt-Enter on the empty constructor and convert to secondary constructor
* signups is now underlined in grey: Alt-Enter to join declaration and assignment and delete the explicit type declaration – it's now unnecessary
* The no-arg constructor is now empty.  IntelliJ cannot safe-delete it, so we have to do this last bit by a manual edit: remove the constructor and the call to this() in the secondary constructor.
* Run all the tests to be sure, and then Alt-Enter on the constructor and convert it to the primary constructor

ASIDE: Like a lot of real-world Java code, this example uses Java Bean naming conventions but not actual Java Beans.

Run the tests. They pass. COMMIT!

Convert the secondary constructor to a primary constructor via Alt-Enter on the declaration.

Run the tests. They pass. COMMIT!

Make sessionId a non-nullable val.

Make capacity a val. Delete the entire var property including the checks. Those are now enforced by the type system.  IntelliJ highlights the declaration in the class body as redundant.  Use Option-Enter to move the val declaration to the primary constructor.

Run the tests. They pass. COMMIT!


## Part 2: Converting the bean to an immutable data class

The SignupSheet is used in the SignupApp. If we are going to make the SignupSheet immutable, we'll need to change this HTTP handler to work with immutable values, rather than mutable beans.

A general strategy for converting classes from mutable to immutable is to push mutation from the inside outwards.  E.g. converting a val that holds a mutable object into a var that holds an immutable object.  And continuing this strategy to push mutation into external resources (databases, for example).


### Replacing mutability with immutability

We'll demonstrate this strategy "in the small", by making the `SignupSheet#signups` set immutable. We will replace the immutable reference to a MutableSet with a _mutable_ reference to an immutable Set:

* Find usages of the `signups` property.  There are several references in this class that mutate the Set by calling its methods.  All these methods are operator methods, and so the method calls can be replaced by operators that Kotlin desugars to mutation of a mutable set or to transformation of an immutable set held in a mutable variable. Using the operators will let us push mutation outwards without breaking the app. You can use Alt-Enter in IntelliJ to refactor between method call and operator.
  * Replace the call to the add method with `signups += attendeeId`
  * Replace the call to the remove method with `signups -= attendeeId`. (For some reason, IntelliJ does not have offer this when you hit Alt-Enter. You have to do this by a manual edit.)
  * For consistency, you can also replace the call to the contains method with the `in` operator.

Run the tests. They pass. COMMIT!

* Change the declaration of `signups` to: `var signups = emptySet<AttendeeId>()`.

Run the tests. They pass.

**Review**: We pushed mutation one level outwards. We did so without breaking the application by making the application use operators that are the same whether mutating a mutable object held in a `val` or transforming an immutable object held in a `var`.

Demonstrate this by using Alt-Enter and "Replace with ordinary assignment" to expand the `+=` to `+` and `-=` to `-`.

Use Alt-Enter to toggle back and forth between the `+=` and ordinary assignment operator. Leave the code using the ordinary assignment operator. It will come in handy later.

Run the tests. They pass. COMMIT!

### Now for the bean itself

We can apply the same strategy of using an API that has the same syntax for both mutable and immutable operations to let us convert the SignupSheet to an immutable data class without breaking lots of code.  However, we can't use Kotlin's operators to do so.  We'll have to create that API ourselves:

1. Change the SignupSheet so have a so-called "fluent" API.
2. Change clients to use the fluent API as if the SignupSheet were immutable.
3. Make the SignupSheet immutable.

#### Step 1: Change the SignupSheet so have a "fluent" API.

* Turn the mutator methods into a "fluent" API by adding `return this` at the end of the `signUp` and `cancelSignUp` and `close`, methods and using Alt-Enter to add the return type to the method signature.

Run the tests. They pass. COMMIT!

#### Step 2: Change clients to use the fluent API as if the SignupSheet were immutable

In SignupApp, replace sequential statements that mutate and then save with a single statement passes the result of the mutator to the `save` method, like:

~~~
book.save(sheet.close())
~~~

We can make IntelliJ do this automatically by extracting the call to the chainable mutator into a local variable called `sheet`.  IntelliJ will warn about shadowing. That's OK: inline the local `sheet` variable, and the call to the chainable mutator will be inlined as a parameter of `book.save`.

Run the tests. They pass. COMMIT!

#### Step 3: Make the SignupSheet immutable


Move the declaration of `signups` to primary constructor, initialised to `emptySet()`

Try running the tests...  The mutators do not compile.  Change them so that, instead of mutating a property, they return a new copy of the object that the property changed.  It's easiest to declare the class as a `data class` and call the `copy` method.

Run the tests... they fail!

We also have to update our in-memory simulation of persistence, the InMemorySignupBook. The code to return a copy of the stored SignupSheet is now unnecessary because SignupSheet is immutable. Delete it all, and return the value obtained from the map

Run the tests. They pass. COMMIT!

### Tidying up

We can turn some more methods into expression form.

* We cannot do this for signUp because of those checks.  We'll come back to those shortly...

ASIDE: I prefer to use block form for functions with side effects and expression for pure functions.

Run the tests. They pass. COMMIT!


The data class does allow us to make the state of a signup sheet inconsistent, by passing in more signups than the capacity.

Add a check in the init block:

    ~~~
    init {
        check(signups.size <= capacity) {
            "cannot have more sign-ups than capacity"
        }
    }
    ~~~

Now, if you have a reference to a SignupSheet, it's guaranteed to have a consistent state.

This makes the `isFull` check in `signUp` redundant, so delete it.

Run the tests. They pass. COMMIT!

### Part 1 Recap

Let's review what we did so far ...

\[Switch to keynote]



## Part 2. Converting the immutable data class into a sealed class hierarchy

Now, those checks... it would be better to prevent client code from using the SignupSheet incorrectly than to throw an exception after they have used it incorrectly.  In FP circles, this is sometimes referred to as "making illegal states unrepresentable".

The SignupSheet class implements a state machine:


~~~plantuml
state Open {
    state choice <<choice>>
    state closed <<exitPoint>>
    state open <<entryPoint>>

    open -down-> Available
    Available -down-> Available : cancelSignUp(a)
    Available -right-> choice : signUp(a)
    choice -right-> Full : [#signups = capacity]
    choice -up-> Available : [#signups < capacity]
    Full -left-> Available : cancelSignUp(a)

    Available -> closed : close()
    Full -> closed : close()
}

[*] -down-> open
closed -> Closed
~~~

* The _signUp_ operation only makes sense in the Available sub-state of Open.

* The _cancelSignUp_ operation only makes sense in the Open state.

* The _close_ operation only makes sense in the Open state.

We can express this in Kotlin with a _sealed type hierarchy_ (Kotlin's method of implementing _algebraic data types_)...

~~~plantuml
hide empty members
hide circle

class SignupSheet <<sealed>>
class Open <<sealed>> extends SignupSheet {
    close(): Closed
    cancelSignUp(a): Available
}

class Available extends Open {
    signUp(a): Open
}

class Full extends Open

class Closed extends SignupSheet
~~~

We'll introduce this state by state, replacing predicates over dynamic state with subtype relationships.

Unfortunately, IntelliJ doesn't have any automated refactorings to split a class into a sealed hierarchy, so we'll have to do it by combining manual and automated refactoring steps.


### 2.1 Open/Closed states

* Extract an abstract base class from SignupSheet
    * NOTE: IntelliJ seems to have lost the ability to rename a class and extract an interface with the original name.  So, we'll have to extract the base class with a temporary name and then rename class and interface to what we want.
    * call it anything, we're about to rename it.  SignupSheetBase, for example.
    * Pull up:
      * sessionId, capacity, signups and isClosed as abstract members
      * Pull isSignedUp and isFull as a concrete member.
    * This refactoring doesn't work 100% for Kotlin, so fix the errors in the interface by hand.

* Change the name of the subclass by hand (not a rename refactor) to Open, and then use a rename refactoring to rename the base class to SignupSheet.

* Create a factory function ... find all places in the code that instantiates SignupSheet ... they all create one with just sessionId and capacity.  Define a function to create new signup sheets in SignupSheet.kt, called `SignupSheet:

      ~~~
      fun SignupSheet(sessionId: SessionId, capacity: Int) =
          Available(sessionId, capacity)
      ~~~

* Repeatedly run all the tests to locate all the compilation errors...
    * In SignupApp, there are calls to methods of the Open class that are not defined on the SignupSheet class.
        * wrap the try/catch blocks in `when(sheet) { is Open -> try { ... } }` to get things compiling again. E.g.

          ~~~
          when (sheet) {
              is Open ->
                  try {
                      book.save(sheet.signUp(attendeeId))
                      sendResponse(exchange, OK, "subscribed")
                  } catch (e: IllegalStateException) {
                      sendResponse(exchange, CONFLICT, e.message)
                  }
              }
          }
          ~~~

* Run the tests.  They should all pass.
* Change the base class from "abstract" to "sealed".

Run the tests. They pass. COMMIT!

Now we can add the Closed subclass:

* NOTE: do not use the "Implement sealed class" action... it does not give the option to create the class in the same file. Instead...
* Define a new `data class Closed : SignupSheet()` in the same file
* The new class is highlighted with an error underline. Option-Enter on the highlighted error, choose "Implement as constructor parameters", ensure sessionId, capacity, and signups are selected in the pop-up (default behaviour), and perform the action.
* Option-Enter on the highlighted error again, choose "Implement members", select all the remaining members

We've broken our HTTP handler, so let's add clauses to the when expressions for how we want our App to handle the Closed state, so we're supported by the type checker...

* in handleSignup, return a CONFLICT status with an error message (e.g. "sign-up closed") as the body text.
* in handleStarted:
    * GET: replace with returning `sheet is Closed`
    * POST: there is nothing to do if the session is already started, replace the TODO() with an empty branch and a comment like "// nothing to do" and move the call to sendResponse after the `when` block.

Run the tests to verify that we have not broken anything... we are not actually using the Closed class yet.

Now make Open.close() return an instance of Closed:

~~~
fun close() =
    Closed(sessionId, capacity, signups)
~~~

Run the tests. They pass. COMMIT!

Look for uses of isClosed. The only calls are accessors in the checks.  Therefore, the value never changes, and is always false.  The checks are dead code, because we have replaced the use of the boolean property with subtyping.

* Delete the check statements
* Safe-Delete the isClosed constructor parameter

Run the tests. They pass. COMMIT!

Review the class... now we have methods that return the abstract SessionSignup type.  We can make the code express the state transitions explicitly in the type system be declaring the methods to return the concrete type (or letting Kotlin infer the result type).

* ASIDE: I prefer to explicitly declare the result type I want.
* Declare the result of close() as Closed, and of signUp & cancelSignUp as Open

Run the tests. They pass. COMMIT!

The exception handling for closing the sheet in the SignupApp is now dead code.  Remove it.

Run the tests. They pass. COMMIT!


### 2.2 Available/Full states

Extract a sealed baseclass, "Bob"
* Pull up sessionId, capacity, signups as abstract
* Pull up isClosed as concrete
* Pull up closed as abstract
* cancelSignup can be abstract

Edit to change the name of the subclass to `Available` and do a rename refactor to change the name of `Bob` to `Open`.

Change the SignupSheet() function to create an instance of `Available`.


### 2.3 Use the Full and Closed types

We can remove the isFull and isClused properties.
* `isFull` is only now only true for the `Full` type.
* `isClosed` is only true for the `Closed` type

For `isFull` ...
 * Change implementationn of isFull with `this is Full`
 * Inline the isFull property
 * Review the change to SignupApp – it reads well

Run all the tests. They pass. COMMIT!

Do the same for `isClosed` -> `this is Closed`


## Converting the methods to extensions

Convert methods to extensions (Option-Enter on the methods).

Change the result types to the most specific possible.

Gather the types and functions into two separate groups.

Fold away the function bodies. Ta-da!  The function signatures describe the state machine!


## Wrap up

Review the code of SignupSheet and SignupApp

What have we done?

* Refactored a mutable, java-bean-style domain model to an immutable, algebraic data type and operations on the data type.
  * Pushed mutation outward, to the edge of our system
* Replaced runtime tests throwing exceptions for invalid method calls, with type safety: it is impossible to call methods in the wrong state because those operations do not exist in those states
  * Pushed error checking outwards, to the edge of the system, where the system has the most context to handle or report the error
* Used copy/paste/refactor to discover the right abstraction
* Used a fluent interface to deliver the refactoring via expand/contract.  In a large system we could gradually migrate code that depends on the SignupSheet class to a functional style, to avoid disrupting people's work.


## The future?

That was the "traditional" approach to refactoring.

I say "traditional" in quotes because most languages do not have refactoring tools of the same capability as JetBrains' IDEs for Java or Kotlin. Even in Java or Kotlin, programmers work mostly by text editing rather than by applying program transformations despite having powerful program transformation tools at their fingertips.

But new tools have been released since we prepared this talk that may make these refactoring tools obsolete: LLM coding assistants.

Let's try to do the same exercise by prompting Junie, JetBrains' coding assistant.

Caveat: LLMs generate random results. Junie might generate code different to when we rehearsed this talk. So we'll have to dance with the chaos monkey and do this part as improv.

Prompt: Refactor the SignupSheet into a sealed class hierarchy of immutable data classes. Use subclasses to represent the different states of the SignupSheet. The states are Available, when the number of signups is less than the capacity, and Full, when the number of signups is equal to the capacity. Do not define the signUp operation for the Full state. Do not change the SignupAppTests class.

Review what Junie produces ... it is usually very close to what we did by IDE.

Tidy up what Junie produces.


