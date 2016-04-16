# difficulty-api

## Description

`difficulty-api` is the backend for a script which helps estimate the difficulty
of targets in the game [Torn](http://www.torn.com). It stores an attack log for
each of its users. It uses its stored attacks to give an estimated difficulty
rating for a target, given a would-be attacker's battle stats.

In order to achieve this functionality, `difficulty-api` exposes two endpoints:
`api-key` and `difficulties`. The `api-key` endpoint is used for signing up by
submitting an api key or just updating the api key for an existing player if it
has changed. The `difficulties` endpoint requires an api key to determine the
identity of the caller as well as a list of torn player ids whose difficulties
will be estimated.

## Installation

You will need a license for Datomic Pro, which you can get
[here](http://www.datomic.com/get-datomic.html). In order to install it, follow
the instructions provided by
[Datomic](http://docs.datomic.com/getting-started.html) under Leiningen setup.
You can also set the environment variables `$DATOMIC_USERNAME` and
`$DATOMIC_PASSWORD`. If you persistent storage, like on a production server, you
will need to provision that storage. This project is set up to use PostgreSQL,
which Datomic provides instructions for
[here](http://docs.datomic.com/storage.html#sql-database). In order to connect
to a persistent database, you will need to have a
[transactor running](http://docs.datomic.com/storage.html#start-transactor).

Create a file `resources/env.edn` which has a map like `{:config-file
"resources/dev.edn"}` which points to the edn file containing your config
options.

## Usage

### Run the application locally

```
lein repl
(go)
```

### Run the tests

`lein test`

### Packaging and running as standalone jar

Before starting the application in a production setting (with persistent
storage), make sure the Datomic
[transactor is running](http://docs.datomic.com/storage.html#start-transactor).

```
lein do clean, uberjar
java -jar target/difficulty-api-$VERSION-standalone.jar
```

### Connecting to the embedded repl

If you are running with the default settings (and use leiningen), you can
connect to the embedded repl with

```
lein repl :connect localhost:7888
```

If you have the application running on a production server with your repl port
blocked (as it should be), you can connect to the embedded repl using an ssh
tunnel with port forwarding. In one terminal, set up the ssh tunnel:

```
ssh -nNT -L 9000:localhost:7888 username@serveraddress
```

Now you can connect to the embedded repl with

```
lein repl :connect localhost:9000
```
