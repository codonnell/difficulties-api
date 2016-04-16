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

## Usage

### Run the application locally

```
lein repl
(go)
```

### Run the tests

`lein test`

### Packaging and running as standalone jar

```
lein do clean, ring uberjar
java -jar target/server.jar
```

### Packaging as war

`lein ring uberwar`

## License

Copyright ©  FIXME
