package com.vayunmathur.maps

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.spatialk.geojson.Position
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.PriorityQueue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object OfflineRouter {

    private var nodesLookupFile: LargeMappedFile? = null
    private var nodesSpatialFile: LargeMappedFile? = null
    private var edgesFile: LargeMappedFile? = null
    private var edgeIndexFile: LargeMappedFile? = null

    // Helper class to bypass Java's 2GB MappedByteBuffer limit
    private class LargeMappedFile(file: File, val recordSize: Int) {
        private val channel = RandomAccessFile(file, "r").channel
        val numRecords = file.length() / recordSize

        // Chunk size: Maximum 1GB, rounded down to the nearest multiple of recordSize
        // This ensures a record is NEVER split across two different byte buffers
        private val maxChunkSize = (1 shl 30) - ((1 shl 30) % recordSize)
        private val buffers = mutableListOf<MappedByteBuffer>()

        init {
            var offset = 0L
            val fileSize = file.length()
            while (offset < fileSize) {
                val size = minOf(maxChunkSize.toLong(), fileSize - offset)
                val buffer = channel.map(FileChannel.MapMode.READ_ONLY, offset, size)
                buffer.order(ByteOrder.LITTLE_ENDIAN) // Must match C++ output
                buffers.add(buffer)
                offset += size
            }
        }

        fun getBufferAndOffset(recordIndex: Long): Pair<MappedByteBuffer, Int> {
            val globalByteOffset = recordIndex * recordSize
            val bufferIndex = (globalByteOffset / maxChunkSize).toInt()
            val localOffset = (globalByteOffset % maxChunkSize).toInt()
            return Pair(buffers[bufferIndex], localOffset)
        }
    }

    private class AStarNode(val localId: UInt, val fScore: Long) : Comparable<AStarNode> {
        override fun compareTo(other: AStarNode): Int = this.fScore.compareTo(other.fScore)
    }

    private data class EdgeData(val target: UInt, val distMm: Long, val type: UByte)

    @Synchronized
    fun init(context: Context) {
        if (nodesLookupFile != null) return // Already initialized

        val dir = context.getExternalFilesDir(null) ?: return
        nodesLookupFile = LargeMappedFile(File(dir, "nodes_lookup.bin"), 8)
        nodesSpatialFile = LargeMappedFile(File(dir, "nodes_spatial.bin"), 12)
        edgesFile = LargeMappedFile(File(dir, "edges.bin"), 13)
        edgeIndexFile = LargeMappedFile(File(dir, "edge_index.bin"), 8) // NEW 8-byte edge bounds index
    }

    /**
     * Calculates the shortest path between start and end.
     * Suspend function ensures the UI thread is not blocked during heavy disk I/O.
     */
    suspend fun findShortestRoute(context: Context, start: Position, end: Position): List<Position> = withContext(Dispatchers.IO) {
        init(context)

        val startId = findClosestNode(start.latitude, start.longitude) ?: return@withContext emptyList()
        val endId = findClosestNode(end.latitude, end.longitude) ?: return@withContext emptyList()

        if (startId == endId) return@withContext listOf(start)

        val openSet = PriorityQueue<AStarNode>()
        val cameFrom = HashMap<UInt, UInt>()
        val gScore = HashMap<UInt, Long>() // Exact distance from start to node

        gScore[startId] = 0L
        openSet.add(AStarNode(startId, heuristic(startId, end)))

        while (openSet.isNotEmpty()) {
            val current = openSet.poll() ?: break

            if (current.localId == endId) {
                return@withContext reconstructPath(cameFrom, current.localId, startId)
            }

            val currentGScore = gScore[current.localId] ?: continue

            // If we already found a shorter path to this node since queueing, skip it
            if (current.fScore - heuristic(current.localId, end) > currentGScore) continue

            val edges = getEdges(current.localId)
            for (edge in edges) {
                val tentativeGScore = currentGScore + edge.distMm
                val neighborGScore = gScore.getOrDefault(edge.target, Long.MAX_VALUE)

                if (tentativeGScore < neighborGScore) {
                    cameFrom[edge.target] = current.localId
                    gScore[edge.target] = tentativeGScore

                    val fScore = tentativeGScore + heuristic(edge.target, end)
                    openSet.add(AStarNode(edge.target, fScore))
                }
            }
        }

        return@withContext emptyList() // No path found
    }

    private fun reconstructPath(cameFrom: HashMap<UInt, UInt>, current: UInt, start: UInt): List<Position> {
        val path = mutableListOf<Position>()
        var curr = current
        while (cameFrom.containsKey(curr)) {
            getLookupNode(curr)?.let { path.add(it) }
            curr = cameFrom[curr]!!
        }
        getLookupNode(start)?.let { path.add(it) }
        path.reverse()
        return path
    }

    private fun getEdges(sourceId: UInt): List<EdgeData> {
        val indexFile = edgeIndexFile ?: return emptyList()
        val edgeFile = edgesFile ?: return emptyList()

        // Verify we aren't querying out of bounds (should never happen for valid IDs)
        if (sourceId.toLong() >= indexFile.numRecords - 1) return emptyList()

        // 1. O(1) Lookup: Find the exact start index of edges for this node
        val (bufStart, offStart) = indexFile.getBufferAndOffset(sourceId.toLong())
        val startIndex = bufStart.getLong(offStart).toULong()

        // 2. O(1) Lookup: Find where the NEXT node's edges start to get our upper bound
        val (bufEnd, offEnd) = indexFile.getBufferAndOffset(sourceId.toLong() + 1)
        val endIndex = bufEnd.getLong(offEnd).toULong()

        // If startIndex == endIndex, this node has 0 outgoing edges
        if (startIndex == endIndex) return emptyList()

        // 3. Extract the edges directly without any binary search looping!
        val result = mutableListOf<EdgeData>()
        for (i in startIndex until endIndex) {
            val (buffer, offset) = edgeFile.getBufferAndOffset(i.toLong())

            // Skip the first 4 bytes (sourceId) since we already know it
            val target = buffer.getInt(offset + 4).toUInt()
            val distMm = buffer.getInt(offset + 8).toUInt().toLong()
            val type = buffer.get(offset + 12).toUByte()

            result.add(EdgeData(target, distMm, type))
        }

        return result
    }

    private fun findClosestNode(lat: Double, lon: Double): UInt? {
        val file = nodesSpatialFile ?: return null
        val targetSpatial = latLngToSpatial(lat, lon)

        var low = 0L
        var high = file.numRecords - 1
        var bestIndex = -1L

        // Binary search for the closest Z-Order curve spatial ID
        while (low <= high) {
            val mid = (low + high) ushr 1
            val (buffer, offset) = file.getBufferAndOffset(mid)
            val midSpatial = buffer.getLong(offset).toULong()

            if (midSpatial < targetSpatial) {
                low = mid + 1
                bestIndex = mid
            } else if (midSpatial > targetSpatial) {
                high = mid - 1
                bestIndex = mid
            } else {
                bestIndex = mid
                break
            }
        }

        if (bestIndex == -1L) return null

        // Z-Order curves can have local discontinuities. We scan a 200-node window
        // around the insertion point to find the physical absolute closest node.
        val searchWindow = 200L
        val startIndex = maxOf(0L, bestIndex - searchWindow)
        val endIndex = minOf(file.numRecords - 1, bestIndex + searchWindow)

        var closestId: UInt? = null
        var minDistance = Double.MAX_VALUE

        for (i in startIndex..endIndex) {
            val (buf, off) = file.getBufferAndOffset(i)
            val localId = buf.getInt(off + 8).toUInt()

            val pos = getLookupNode(localId)
            if (pos != null) {
                val dist = haversine(lat, lon, pos.latitude, pos.longitude)
                if (dist < minDistance) {
                    minDistance = dist
                    closestId = localId
                }
            }
        }

        return closestId
    }

    private fun getLookupNode(localId: UInt): Position? {
        val file = nodesLookupFile ?: return null
        if (localId.toLong() >= file.numRecords) return null

        val (buffer, offset) = file.getBufferAndOffset(localId.toLong())
        val lat = buffer.getInt(offset) / 10000000.0
        val lon = buffer.getInt(offset + 4) / 10000000.0

        // Safety check to ensure we don't route to "Null Island" for missing data
        if (lat == 0.0 && lon == 0.0) return null

        return Position(lon, lat)
    }

    private fun heuristic(localId: UInt, endPos: Position): Long {
        val pos = getLookupNode(localId) ?: return Long.MAX_VALUE
        val distMeters = haversine(pos.latitude, pos.longitude, endPos.latitude, endPos.longitude)
        return (distMeters * 1000.0).toLong() // Return in millimeters to match Edge distance units
    }

    // Identical port of the C++ Z-Order implementation using native Kotlin Unsigned types
    private fun latLngToSpatial(lat: Double, lon: Double): ULong {
        val x = (lon + 180.0) / 360.0
        val y = (lat + 90.0) / 180.0
        val ix = (x * 4294967295.0).toUInt().toULong()
        val iy = (y * 4294967295.0).toUInt().toULong()
        var res = 0UL
        for (i in 0 until 32) {
            res = res or (((ix shr i) and 1UL) shl (2 * i))
            res = res or (((iy shr i) and 1UL) shl (2 * i + 1))
        }
        return res
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}