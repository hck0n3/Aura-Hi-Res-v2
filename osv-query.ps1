$pairs = @(
  @('com.arthenica:ffmpeg-kit-full','6.0-2'),
  @('org.jsoup:jsoup','1.22.2'),
  @('com.squareup.okhttp3:okhttp','4.12.0'),
  @('com.google.guava:guava','33.6.0-jre'),
  @('androidx.security:security-crypto','1.1.0-alpha06'),
  @('com.github.yalantis:ucrop','2.2.11'),
  @('org.brotli:dec','0.1.2'),
  @('io.ktor:ktor-client-core-jvm','3.5.0'),
  @('androidx.room:room-runtime','2.8.4'),
  @('io.coil-kt.coil3:coil','3.5.0'),
  @('com.airbnb.android:lottie','6.7.1'),
  @('org.jetbrains.kotlinx:kotlinx-serialization-json','1.9.0'),
  @('junit:junit','4.13.2'),
  @('org.apache.commons:commons-lang3','3.20.0'),
  @('com.jakewharton.timber:timber','5.0.1'),
  @('androidx.work:work-runtime-ktx','2.10.2'),
  @('androidx.datastore:datastore-preferences','1.2.1'),
  @('dev.chrisbanes.haze:haze','1.0.2'),
  @('sh.calvin.reorderable:reorderable','3.0.0'),
  @('com.google.android.exoplayer:exoplayer','1.10.1'),
  @('androidx.media3:media3-exoplayer','1.10.1'),
  @('com.google.dagger:hilt-android','2.60.1'),
  @('com.google.protobuf:protobuf-javalite','4.34.2'),
  @('com.atilika.kuromoji:kuromoji-ipadic','0.9.0'),
  @('com.github.promeg:tinypinyin','2.0.3.RELEASE')
)
foreach ($p in $pairs) {
  $body = @{ package = @{ name = $p[0]; ecosystem = 'Maven' }; version = $p[1] } | ConvertTo-Json -Compress
  try {
    $r = Invoke-RestMethod -Uri 'https://api.osv.dev/v1/query' -Method Post -Body $body -ContentType 'application/json' -TimeoutSec 30
    if ($r.vulns) {
      $ids = ($r.vulns | ForEach-Object { $_.id }) -join ', '
      Write-Output ("VULN  {0} {1} -> {2}" -f $p[0], $p[1], $ids)
    } else {
      Write-Output ("OK    {0} {1}" -f $p[0], $p[1])
    }
  } catch {
    Write-Output ("ERROR {0} {1} : {2}" -f $p[0], $p[1], $_.Exception.Message)
  }
}
