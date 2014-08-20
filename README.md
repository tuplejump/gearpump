GearPump
========

![](http://i.istockimg.com/file_thumbview_approve/27804028/3/stock-illustration-27804028-gear-pump.jpg)


A Actor Driven event processing framework.
It contains two part, Gears and GearPump.

Gears is a general resource scheduler, it is design to support multiple Heterogeneous resource scheduling requirements.
GearPump is a data flow processing engine built on top of Gears.

###How to Build
  ```bash
  ## Build Gearpump
  sbt clean pack
  ```
  This will generate scripts under target/pack/bin

##How to Package for distribution
  ```bash
  ## Package Gearpump
  sbt clean pack-archive
  ```
  This will produce target/gearpump${version}.tar.gz which contains the ./bin and ./lib files.

##How to Install to /usr/local
  ```bash
  ## Run Build step above
  cd target/pack
  sudo make install PREFIX="/usr/local"
  ```
  This will install scripts to /usr/local/bin and jars to /usr/local/lib

###Local Mode
1. Start Local Cluster in same process
  ```bash
  ## Create Cluster on port localhost:8092, create 4 worker, 4 worker exists in same process
  target/pack/bin/local -port 8092 -sameprocess -workernum 4
  ```

2. Start Local Cluster in different process
  ```bash
  ## Create Cluster on port localhost:8092, create 4 worker, 4 worker exists in seperate process
  target/pack/bin/local -port 8092 -workernum 4
  ```
3. Start Client Example Code
  
  ```bash
  ## Create Application
  target/pack/bin/wordcount <master ip> <master port> <split number> <sum number> <runseconds>
  ```


###Cluster Mode
1. On 1 node, Start Master
  ```bash
  ## Create Master on <master ip>:8092, 
  target/pack/bin/master -port 8092
  ```

2. On same or different machine, Start workers. If you want to start 3 worker, then you need to run this command 3 times.

  ```bash
  target/pack/bin/worker -ip <master ip> -port <master port>
  ```
3. Start Client Example Code

  ```bash
  ## Create Application
  target/pack/bin/wordcount -ip <master ip> -port <master port> -split <split number> -sum <sum number> -runseconds <runseconds>
  ```