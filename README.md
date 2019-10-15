# About

This project is part of my clojure learning experience.
What's in here is a full-stack, cljs/clojure/redis solution
for a simple user/news-posting scenario.

Functionality and UI wise, it's as simple as it gets.
It's the whole full-stack aspect that I was more interested
in putting together.
I will continue to add to it as I see fit for my purposes.

It uses cemerik.friend for securing the app.

## Prerequisites

Knowledge of clojure, leiningen, redis.

You will need Leiningen 2.0.0 or above installed.
https://leiningen.org/

You will also need a Redis server installed and running:
https://redis.io/download

## Running

#### Redis
(OSX) To have launchd start redis now and restart at login:
 
 `brew services start redis`
 
Or, if you don't want/need a background service you can just run:

  `redis-server /usr/local/etc/redis.conf`
  
By default, redis will run on `redis://localhost:6379/`

#### App
Compile to JS files first with: `lein cljsbuild once`

To start a web server for the application (port 8080), simply run the main
fn in api.secure: `lein run`