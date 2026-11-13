package ai.heartz.server

import ai.heartz.core.seed.SeedData
import ai.heartz.core.service.FileBlobStore
import ai.heartz.core.service.FileHeartzStore
import ai.heartz.core.service.HeartzService
import java.io.File
import java.time.LocalTime

/**
 * 로컬 개발·시연 서버.
 *   --data <dir>        저장 폴더 (기본 build/heartz-server-data)
 *   --seed-audio <dir>  시드 합성 음원 폴더 (기본 seed/audio)
 *   --port <n>          기본 8787. 127.0.0.1에만 바인딩한다.
 *   --print-demo-route [stepMeters]  시연 경로(SeedData.demoRoute)를 "lng lat bearing" 줄로 출력하고 종료
 *                       (tools/demo-drive.sh가 에뮬레이터 geo fix 입력으로 쓴다. 테스트 입력 재현용)
 */
fun main(args: Array<String>) {
    val routeFlag = args.indexOf("--print-demo-route")
    if (routeFlag >= 0) {
        val step = args.getOrNull(routeFlag + 1)?.toDoubleOrNull() ?: 15.0
        printDemoRoute(step)
        return
    }
    val opts = args.toList().chunked(2).filter { it.size == 2 }.associate { it[0] to it[1] }
    val dataDir = File(opts["--data"] ?: "build/heartz-server-data")
    val seedDir = File(opts["--seed-audio"] ?: "seed/audio")
    val port = opts["--port"]?.toInt() ?: 8787

    val service = HeartzService(FileHeartzStore(File(dataDir, "db.json")), FileBlobStore(File(dataDir, "blobs")))
    val seedBlobs = SeedData.audioFiles.mapValues { (_, name) ->
        val f = File(seedDir, name)
        require(f.isFile) { "seed audio missing: ${f.path}" }
        f.readBytes()
    }
    service.seedIfEmpty(SeedData.db(System.currentTimeMillis()), seedBlobs)
    service.refreshSeedStories(SeedData.db(System.currentTimeMillis()).stories, seedBlobs)
    service.refreshSeedPlaces(ai.heartz.core.seed.SeoulLandmarks.places)

    val server = HeartzHttpServer(service, port = port, log = { println("${LocalTime.now().withNano(0)} $it") })
    server.start()
    println("Heartz dev server on http://127.0.0.1:${server.boundPort}  (emulator: http://10.0.2.2:${server.boundPort})")
    println("data=${dataDir.absolutePath}  test accounts only, no secrets")
    Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
    Thread.currentThread().join()
}

private fun printDemoRoute(stepMeters: Double) {
    SeedData.demoRoute.zipWithNext().forEach { (a, b) ->
        val n = (ai.heartz.core.geo.Geo.distanceMeters(a, b) / stepMeters).toInt().coerceAtLeast(1)
        val bearing = ai.heartz.core.geo.Geo.bearingDeg(a, b)
        for (i in 0 until n) {
            val t = i.toDouble() / n
            println(String.format(java.util.Locale.US, "%.6f %.6f %.1f", a.lng + (b.lng - a.lng) * t, a.lat + (b.lat - a.lat) * t, bearing))
        }
    }
    val last = SeedData.demoRoute.last()
    println(String.format(java.util.Locale.US, "%.6f %.6f %.1f", last.lng, last.lat, 0.0))
}
