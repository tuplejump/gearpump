/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.gearpump.dashboard.controllers

import com.greencatsoft.angularjs.core.{Compile, Route, RouteProvider, Scope}
import com.greencatsoft.angularjs.{AbstractController, Config, injectable}
import org.apache.gearpump.dashboard.services.RestApiService
import org.apache.gearpump.shared.Messages._
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js
import scala.scalajs.js.UndefOr
import scala.scalajs.js.annotation.{JSExport, JSExportAll}
import scala.util.{Failure, Success}

@JSExportAll
case class Tab(var heading: String, templateUrl: String, controller: String, var selected: Boolean = false)

@JSExportAll
case class TabIndex(tabIndex: Int, reload: Boolean)

@JSExportAll
case class GraphEdge(source: Int, target: Int, `type`: String)

@JSExportAll
case class SummaryEntry(name: String, value: Any)

@JSExportAll
case class Options(height: String)

@JSExportAll
case class Chart(title: String, options: Options, var data: js.Array[Int])

@JSExportAll
case class ProcessedMessages(total: Int, rate: Int)

@JSExportAll
case class ProcessorsData(processors: Map[ProcessorId, ProcessorDescription], hierarchyLevels: Map[Int, Int], weights: Map[Int, Int])

@JSExport
@injectable("AppMasterConfig")
class AppMasterConfig(routeProvider: RouteProvider) extends Config {
  println("AppMasterConfig")
  routeProvider.when ("/apps/app/:id", Route("views/apps/app/appmaster.html", "Application", "AppMasterCtrl") )
}

@JSExport
class StreamingDag(data: StreamingAppMasterDataDetail) {

  import StreamingDag._

  val appId = data.appId
  val processors = data.processors
  val processorHierarchyLevels = data.processorLevels
  val edges = data.dag.edges.map(tuple => {
    val (node1, edge, node2) = tuple
    (node1 + "_" + node2) -> GraphEdge(node1, node2, edge)
  }).toMap
  val executors = data.executors
  var meter = Map.empty[String, MetricInfo[Meter]]
  var histogram = Map.empty[String, MetricInfo[Histogram]]
  val d3 = js.Dynamic.global.d3

  @JSExport
  def hasMetrics: Boolean = {
    meter.nonEmpty && histogram.nonEmpty
  }

  @JSExport
  def updateMetrics(data: HistoryMetricsItem) = {
    data.value.typeName match {
      case MeterType =>
        val metric = upickle.read[Meter](data.value.json)
        val (appId, processorId, taskId) = decodeName(metric.name)
        val metricInfo = MetricInfo[Meter](appId, processorId, taskId, metric)
        meter += metric.name -> metricInfo
      case HistogramType =>
        val metric = upickle.read[Histogram](data.value.json)
        val (appId, processorId, taskId) = decodeName(metric.name)
        val metricInfo = MetricInfo[Histogram](appId, processorId, taskId, metric)
        histogram += metric.name -> metricInfo
      case _ =>
        println(s"unknown metric type ${data.value.typeName}")
    }
  }

  @JSExport
  def getNumOfTasks: Int = {
    processors.valuesIterator.map(processorDescription => {
      processorDescription.parallelism
    }).sum
  }

  @JSExport
  def getReceivedMessages(processorId: UndefOr[js.Any]): ProcessedMessages = {
    ProcessedMessages(0, 0)
    /*
        if (processorId !== undefined) {
          return this._getProcessedMessagesByProcessor(
            this.meter.receiveThroughput, processorId, false
          );
        } else {
          return this._getProcessedMessages(
            this.meter.receiveThroughput,
            this._getProcessorIdsByType('sink')
          );
        }
     */
  }

  @JSExport
  def getSentMessages(processorId: UndefOr[js.Any]): ProcessedMessages = {
    ProcessedMessages(0, 0)
  }

  @JSExport
  def getProcessingTime(processorId: UndefOr[js.Any]): Int = {
    0
  }

  @JSExport
  def getReceiveLatency(processorId: UndefOr[js.Any]): Int = {
    0
  }

  @JSExport
  def getProcessorsData(): ProcessorsData = {
    /*
        var weights = {};
        angular.forEach(this.processors, function (_, key) {
          var processorId = parseInt(key);
          weights[processorId] = this._calculateProcessorWeight(processorId);
        }, this);
        return {
          processors: angular.copy(this.processors),
          hierarchyLevels: angular.copy(this.processorHierarchyLevels),
          weights: weights
        };
     */
    val weights = processors.keys.map(processorId => {
      processorId -> calculateProcessorWeight(processorId)
    }).toMap[Int, Int]

    ProcessorsData(processors, processorHierarchyLevels, weights)
  }

  @JSExport
  def calculateProcessorWeight(processorId: Int): Int = {
    Array(getProcessorMetrics(processorId, "sendThroughput", "meanRate").sum,
    getProcessorMetrics(processorId, "receiveThroughput", "meanRate").sum).max
  }

  @JSExport
  def getProcessorMetrics(processorId: Int, metricGroup: String, metricType: String): Array[Int] = {
    val rt = Array.empty[Int]
    rt
  }

  @JSExport
  def hierarchyDepth(): Int = {
    processorHierarchyLevels.values.max
  }

  import js.JSConverters._

  def decodeName(name: String): (Int, Int, Int) = {
    val parts: js.Array[String] = name.split("\\.").toJSArray
    val appId = parts(0).substring("app".length).toInt
    val processorId = parts(1).substring("processor".length).toInt
    val taskId = parts(2).substring("task".length).toInt
    (appId, processorId, taskId)
  }
}

@JSExport
object StreamingDag {
  val MeterType = "org.apache.gearpump.shared.Messages.Meter" //"org.apache.gearpump.metrics.Metrics.Meter"
  val HistogramType = "org.apache.gearpump.shared.Messages.Histogram" //"org.apache.gearpump.metrics.Metrics.Histogram"

  def apply(data: StreamingAppMasterDataDetail) = new StreamingDag(data)
}


trait AppMasterScope extends Scope {
  var activeProcessorId: Int = js.native
  var app: StreamingAppMasterDataDetail = js.native
  var streamingDag: StreamingDag = js.native
  var summary: js.Array[SummaryEntry] = js.native
  var switchToTabIndex: TabIndex = js.native
  var tabs: js.Array[Tab] = js.native
  var charts: js.Array[Chart] = js.native

  var load: js.Function2[HTMLElement, Tab,Unit] = js.native
  var selectTab: js.Function1[Tab,Unit] = js.native
  var switchToTaskTab: js.Function1[Int,Unit] = js.native
}


@JSExport
@injectable("AppMasterCtrl")
class AppMasterCtrl(scope: AppMasterScope, restApi: RestApiService, compile: Compile)
  extends AbstractController[AppMasterScope](scope) {

  println("AppMasterCtrl")

  def load(elem: HTMLElement, tab: Tab): Unit = {
    restApi.getUrl(tab.templateUrl) onComplete {
      case Success(html) =>
        val templateScope = scope.$new(true).asInstanceOf[AppMasterScope]
        elem.innerHTML = html
        //tabSetCtrl(templateScope)
        elem.setAttribute("ngController", tab.controller)
        compile(elem.innerHTML, null, 0)(templateScope, null)
      case Failure(t) =>
        println(s"Failed to get workers ${t.getMessage}")
    }
  }

  def selectTab(tab: Tab): Unit = {
    println("!! selectTab !!")
    tab.selected = true
  }

  def switchToTaskTab(processorId: Int): Unit = {
    scope.activeProcessorId = processorId
    scope.switchToTabIndex = TabIndex(tabIndex=2, reload=true)
  }

  scope.app = StreamingAppMasterDataDetail(appId=1)
  scope.tabs = js.Array(
   Tab(heading="Status", templateUrl="views/apps/app/appstatus.html", controller="AppStatusCtrl"),
   Tab(heading="DAG", templateUrl="views/apps/app/appdag.html", controller="AppDagCtrl"),
   Tab(heading="Processor", templateUrl="views/apps/app/appprocessor.html", controller="AppProcessorCtrl"),
   Tab(heading="Metrics", templateUrl="views/apps/app/appmetrics.html", controller="AppMetricsCtrl")
  )

  scope.switchToTaskTab = switchToTaskTab _
  scope.selectTab = selectTab _
  scope.load = load _

  def readHistoryMetrics(data: String): HistoryMetrics = {
    upickle.read[HistoryMetrics](data)
  }

  restApi.subscribe("/appmaster/" + scope.app.appId + "?detail=true") onComplete {
    case Success(value) =>
      val data = upickle.read[StreamingAppMasterDataDetail](value)
      scope.app = data
      scope.streamingDag = new StreamingDag(scope.app)
      val hasMetrics = scope.streamingDag.hasMetrics
      hasMetrics match {
        case false =>
          val url = s"/metrics/app/${scope.app.appId}/app${scope.app.appId}?readLatest=true"
          restApi.subscribe(url) onComplete {
            case Success(rdata) =>
              val value = readHistoryMetrics(rdata)
              Option(value).foreach(_.metrics.map(metricItem => {
                scope.streamingDag.updateMetrics(metricItem)
              }))
            case Failure(t) =>
              println(s"failed ${t.getMessage}")
          }
        case true =>
      }
    case Failure(t) =>
      println(s"Failed to get workers ${t.getMessage}")
  }

}

