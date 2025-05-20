package com.example.geupjo_bus.api

import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

@Root(name = "response", strict = false)
data class BusStopSearchResponse(
    @field:Element(name = "body", required = false)
    var body: BodyResponse? = null
)

@Root(name = "body", strict = false)
data class BodyResponse(
    @field:Element(name = "items", required = false)
    var items: ItemsBody? = null
)

@Root(name = "items", strict = false)
data class ItemsBody(
    @field:ElementList(entry = "item", inline = true, required = false)
    var itemList: List<BusStopItem>? = null
)

@Root(name = "item", strict = false)
data class BusStopItem(
    @field:Element(name = "nodenm", required = false)
    var nodeName: String? = null,
    @field:Element(name = "nodeid", required = false)
    var nodeId: String? = null,
    @field:Element(name = "nodeno", required = false)
    var nodeNumber: String? = null,
    @field:Element(name = "gpslati", required = false)
    var nodeLati: Double? = null,
    @field:Element(name = "gpslong", required = false)
    var nodeLong: Double? = null,
    @Transient
    var cityCode: Int = 38030 // Gson 저장/복원에 사용됨
)