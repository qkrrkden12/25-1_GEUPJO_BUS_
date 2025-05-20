package com.example.geupjo_bus

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.geupjo_bus.ui.rememberMapViewWithLifecycle
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// 데이터 클래스: 한 단계(step) 단위의 UI 정보를 담습니다.
data class DirectionStep(
    val instruction: String,
    val distance: String,
    val duration: String,
    val busInfo: String?,
    val transferInfo: String
)

// 전체 경로 결과
data class DirectionResult(
    val steps: List<DirectionStep>,
    val duration: String,
    val polyline: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSearchScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var departure by remember { mutableStateOf(TextFieldValue("")) }
    var destination by remember { mutableStateOf(TextFieldValue("")) }
    var selectedMode by remember { mutableStateOf("transit") }

    var routeResults by remember { mutableStateOf<List<DirectionResult>>(emptyList()) }
    var travelTime by remember { mutableStateOf("") }
    var polylinePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var expandedRouteIndex by remember { mutableStateOf<Int?>(null) }

    var currentLocation by remember { mutableStateOf<Location?>(null) }
    val departureMarker = remember { mutableStateOf<Marker?>(null) }
    val destinationMarker = remember { mutableStateOf<Marker?>(null) }
    val currentPolyline = remember { mutableStateOf<Polyline?>(null) }
    val recentSearches = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            addAll(loadRecentSearches(context))
        }
    }

    val polylineColor = 0xFF6200EE.toInt()
    val mapView = rememberMapViewWithLifecycle(context)
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }

    // 초기 위치 가져오기 및 지도 세팅
    LaunchedEffect(Unit) {
        currentLocation = getCurrentLocation(context)
        currentLocation?.let {
            val address = getAddressFromLocation(context, it.latitude, it.longitude)
            departure = TextFieldValue(address)
        }
        mapView.getMapAsync { gMap ->
            googleMap = gMap
            currentLocation?.let {
                val latLng = LatLng(it.latitude, it.longitude)
                gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
                departureMarker.value = gMap.addMarker(
                    MarkerOptions().position(latLng).title("출발지")
                )
            }
        }
    }

    fun performSearch() {
        if (departure.text.isBlank() || destination.text.isBlank()) {
            Toast.makeText(context, "출발지와 도착지를 모두 입력하세요", Toast.LENGTH_SHORT).show()
            return
        }
        isSearching = true
        keyboardController?.hide()
        coroutineScope.launch {
            val results = fetchDirections(departure.text, destination.text, selectedMode)
            routeResults = results
            travelTime = results.firstOrNull()?.duration ?: ""
            expandedRouteIndex = null

            // 최근 검색 관리
            recentSearches.removeAll { it.first == departure.text && it.second == destination.text }
            recentSearches.add(0, departure.text to destination.text)
            if (recentSearches.size > 5) recentSearches.removeLast()
            saveRecentSearches(context, recentSearches)

            // 지도 업데이트
            updateMapWithDirections(
                googleMap = googleMap,
                polyline = results.firstOrNull()?.polyline,
                polylineColor = polylineColor,
                currentPolyline = currentPolyline,
                destinationMarker = destinationMarker,
                departureMarker = departureMarker,
                currentLocation = currentLocation
            )
            isSearching = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 뒤로 가기 버튼
        IconButton(onClick = onBackClick) {
            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
        }

        Spacer(Modifier.height(16.dp))

        // 출발지 입력
        OutlinedTextField(
            value = departure,
            onValueChange = { departure = it },
            label = { Text("출발지를 입력하세요") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (departure.text.isNotEmpty()) {
                    IconButton(onClick = { departure = TextFieldValue("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
        )

        Spacer(Modifier.height(8.dp))

        // 도착지 입력
        OutlinedTextField(
            value = destination,
            onValueChange = { destination = it },
            label = { Text("도착지를 입력하세요") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (destination.text.isNotEmpty()) {
                    IconButton(onClick = { destination = TextFieldValue("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { performSearch() })
        )

        Spacer(Modifier.height(8.dp))

        // 모드 스왑 & 검색 버튼
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                val tmp = departure
                departure = destination
                destination = tmp
            }) {
                Icon(Icons.Default.SwapVert, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("스왑")
            }
            Button(onClick = { performSearch() }) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("검색")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isSearching) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
        }

        // 지도
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )

        Spacer(Modifier.height(24.dp))

        // 검색 결과
        routeResults.forEachIndexed { index, result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        // 토글 인덱스
                        expandedRouteIndex = if (expandedRouteIndex == index) {
                            null
                        } else {
                            // 클릭된 경로를 지도에 표시
                            updateMapWithDirections(
                                googleMap = googleMap,
                                polyline = result.polyline,
                                polylineColor = polylineColor,
                                currentPolyline = currentPolyline,
                                destinationMarker = destinationMarker,
                                departureMarker = departureMarker,
                                currentLocation = currentLocation
                            )
                            index
                        }
                    }
                    .animateContentSize(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "경로 ${index + 1} - ${result.duration}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (expandedRouteIndex == index) {
                        Spacer(Modifier.height(8.dp))
                        result.steps.forEach { step ->
                            RouteStepItem(
                                instruction = step.instruction,
                                distance    = step.distance,
                                duration    = step.duration,
                                busInfo     = step.busInfo,
                                transferInfo= step.transferInfo
                            )
                        }
                    }
                }
            }
        }


        Spacer(Modifier.height(24.dp))

        // 최근 검색
        if (recentSearches.isNotEmpty()) {
            Text("최근 검색", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            recentSearches.forEach { (from, to) ->
                Button(
                    onClick = {
                        departure = TextFieldValue(from)
                        destination = TextFieldValue(to)
                        performSearch()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("$from → $to")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                recentSearches.clear()
                saveRecentSearches(context, emptyList())
            }) {
                Text("모두 지우기")
            }
        }
    }
}

@Composable
fun RouteStepItem(
    instruction: String,
    distance: String,
    duration: String,
    busInfo: String?,
    transferInfo: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .animateContentSize()
    ) {
        // 안내 문구
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Place, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(instruction, style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(4.dp))

        // 거리
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DirectionsWalk, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("거리: $distance", style = MaterialTheme.typography.bodyMedium)
        }

        // 버스 정보 (존재할 때만)
        busInfo?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DirectionsBus, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(4.dp))

        // 소요 시간
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("소요 시간: $duration", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(4.dp))

        // 환승 정보
        Text(
            text = transferInfo,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 28.dp)
        )
    }
}

suspend fun fetchDirections(departure: String, destination: String, mode: String): List<DirectionResult> {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val encodedOrigin = URLEncoder.encode(departure, "UTF-8")
            val encodedDestination = URLEncoder.encode(destination, "UTF-8")
            val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=$encodedOrigin&destination=$encodedDestination&mode=$mode&alternatives=true&language=ko&key=AIzaSyA-XxR0OPZoPTA9-TxDyqQVqaRt9EOa-Eg"

            Log.d("Directions API", "URL: $url")

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val jsonData = response.body?.string()

            val resultList = mutableListOf<DirectionResult>()
            if (jsonData != null) {
                val jsonObject = JSONObject(jsonData)
                val routes = jsonObject.getJSONArray("routes")
                for (r in 0 until routes.length()) {
                    val route = routes.getJSONObject(r)
                    val polyline = route.getJSONObject("overview_polyline").getString("points")
                    val leg = route.getJSONArray("legs").getJSONObject(0)
                    val durationText = leg.getJSONObject("duration").getString("text")
                    val stepsJson = leg.getJSONArray("steps")

                    // 환승 횟수 계산
                    val numTransfers = (0 until stepsJson.length()).count {
                        stepsJson.getJSONObject(it).has("transit_details")
                    } - 1
                    val transferInfo = if (numTransfers > 0) "🔄 환승 ${numTransfers}회" else "직행"

                    val steps = mutableListOf<DirectionStep>()
                    for (i in 0 until stepsJson.length()) {
                        val stepObj = stepsJson.getJSONObject(i)
                        val instruction = stepObj.getString("html_instructions").replace(Regex("<.*?>"), "")
                        val distance = stepObj.getJSONObject("distance").getString("text")
                        val durationStep = stepObj.optJSONObject("duration")?.optString("text") ?: ""
                        val travelMode = if (stepObj.has("transit_details")) "버스" else stepObj.getString("travel_mode")
                        val busNumber = stepObj.optJSONObject("transit_details")
                            ?.getJSONObject("line")?.optString("short_name").orEmpty()
                        val busInfo = if (busNumber.isNotEmpty()) "$travelMode ($busNumber)" else null

                        steps.add(
                            DirectionStep(
                                instruction = instruction,
                                distance = distance,
                                duration = durationStep,
                                busInfo = busInfo,
                                transferInfo = transferInfo
                            )
                        )
                    }

                    resultList.add(
                        DirectionResult(
                            steps = steps,
                            duration = durationText,
                            polyline = polyline
                        )
                    )
                }
            }
            resultList
        } catch (e: Exception) {
            Log.e("Google Directions API", "Error fetching directions: ${e.message}")
            emptyList()
        }
    }
}

fun updateMapWithDirections(
    googleMap: GoogleMap?,
    polyline: String?,
    polylineColor: Int,
    currentPolyline: MutableState<Polyline?>,
    destinationMarker: MutableState<Marker?>,
    departureMarker: MutableState<Marker?>,
    currentLocation: Location?
) {
    googleMap?.let { map ->
        currentPolyline.value?.remove()
        currentLocation?.let {
            val currentLatLng = LatLng(it.latitude, it.longitude)
            departureMarker.value?.remove()
            departureMarker.value = map.addMarker(
                MarkerOptions().position(currentLatLng).title("출발지")
            )
        }
        if (!polyline.isNullOrEmpty()) {
            val points = decodePolyline(polyline)
            currentPolyline.value = map.addPolyline(
                PolylineOptions()
                    .addAll(points)
                    .color(polylineColor)
                    .width(10f)
            )
            points.lastOrNull()?.let {
                destinationMarker.value?.remove()
                destinationMarker.value = map.addMarker(
                    MarkerOptions().position(it).title("도착지")
                )
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 12f))
            }
        }
    }
}

fun decodePolyline(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        lat += dlat
        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        lng += dlng
        poly.add(LatLng(lat / 1E5, lng / 1E5))
    }
    return poly
}

@SuppressLint("MissingPermission")
suspend fun getCurrentLocation(context: Context): Location? {
    val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    return suspendCoroutine { cont ->
        client.lastLocation
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}

fun getAddressFromLocation(context: Context, latitude: Double, longitude: Double): String {
    val geocoder = Geocoder(context)
    return geocoder.getFromLocation(latitude, longitude, 1)
        ?.firstOrNull()?.getAddressLine(0)
        ?: "주소를 찾을 수 없습니다."
}

fun saveRecentSearches(context: Context, searches: List<Pair<String, String>>) {
    val prefs = context.getSharedPreferences("recent_searches", Context.MODE_PRIVATE)
    prefs.edit().putString(
        "recent_searches",
        searches.joinToString("|") { "${it.first}::${it.second}" }
    ).apply()
}

fun loadRecentSearches(context: Context): List<Pair<String, String>> {
    val encoded = context.getSharedPreferences("recent_searches", Context.MODE_PRIVATE)
        .getString("recent_searches", null) ?: return emptyList()
    return encoded.split("|").mapNotNull {
        it.split("::").takeIf { parts -> parts.size == 2 }?.let { parts ->
            parts[0] to parts[1]
        }
    }
}
