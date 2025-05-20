package com.example.geupjo_bus.api

import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

@Root(name = "response", strict = false)
data class BussLocationResponse(
    @field:Element(name = "body", required = false)
    var body: BusLocationBody? = null
)

@Root(name = "body", strict = false)
data class BusLocationBody(
    @field:Element(name = "items", required = false)
    var items: BusLocationBodyListItem? = null // ⬅️ 여기 수정
)

@Root(name = "items", strict = false)
data class BusLocationBodyListItem(
    @field:ElementList(entry = "item", inline = true, required = false)
    var itemList: List<BusLocationList>? = null
)

@Root(name = "item", strict = false)
data class BusLocationList(
    @field:Element(name = "gpslati", required = false) var latitude: Double? = null,
    @field:Element(name = "gpslong", required = false) var longitude: Double? = null,
    @field:Element(name = "nodenm", required = false) var nodeName: String? = null,
    @field:Element(name = "vehicleno", required = false) var vehicleNo: String? = null
)
