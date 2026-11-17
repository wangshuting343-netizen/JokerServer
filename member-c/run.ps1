param([ValidateSet('build','server','client','scoreboard','test','ui-test')][string]$Mode='build',[string]$JavaHome=$env:JAVA_HOME,[string]$JavaFx=$env:PATH_TO_FX,[string]$HostAddress='127.0.0.1',[int]$TcpPort=12345,[string]$Nic='',[int]$UdpPort=15557,[string]$Group='239.25.30.15')
$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
try {
if(!$JavaHome -and (Test-Path 'E:/大二下/OOP/JDK/bin/java.exe')){$JavaHome='E:/大二下/OOP/JDK'}
if(!$JavaFx){$JavaFx=Join-Path $PSScriptRoot '../BattleJoker/openjfx-25.0.4_windows-x64_bin-sdk/javafx-sdk-25.0.4/lib'}
$java=Join-Path $JavaHome 'bin/java.exe';$javac=Join-Path $JavaHome 'bin/javac.exe'
if($Mode -eq 'build'){
 New-Item -ItemType Directory -Force build,client-build | Out-Null
 $src=@(Get-ChildItem shared/*.java,server-src/*.java,tests/*.java | Where-Object Name -ne 'OriginalBugTest.java' | ForEach-Object FullName)
 & $javac -encoding UTF-8 -d build @src
 if($LASTEXITCODE){throw 'Server build failed'}
 $src=@(Get-ChildItem shared/*.java,client/src/*.java | ForEach-Object FullName)
 & $javac -encoding UTF-8 --module-path $JavaFx --add-modules javafx.controls,javafx.fxml -d client-build @src
 if($LASTEXITCODE){throw 'Client build failed'}
 Copy-Item client/src/*.fxml client-build/
}elseif($Mode -eq 'server'){
 & $java --enable-native-access=ALL-UNNAMED "-Dbj.tcp=$TcpPort" "-Dbj.udp=$UdpPort" "-Dbj.group=$Group" $(if($Nic){"-Dbj.nic=$Nic"}else{'-Dbj.mode=server'}) -cp 'build;lib/*' JokerServer
}elseif($Mode -eq 'test'){
 foreach($test in 'RepositoryTest','EngineTest','SettlementTest','CacheTest','MigrationTest','ResultCacheTest'){
  & $java --enable-native-access=ALL-UNNAMED -cp 'build;lib/*' $test
  if($LASTEXITCODE){throw "$test failed"}
 }
 }elseif($Mode -eq 'ui-test'){
 # Compile and run local JavaFX interaction checks with automatic window cleanup.
 foreach($test in 'ViewsTest','ScoreboardUiTest'){
  & $javac -encoding UTF-8 --module-path $JavaFx --add-modules javafx.controls,javafx.fxml -cp client-build -d client-build "evidence/$test.java"
  if($LASTEXITCODE){throw "$test build failed"}
  & $java --enable-native-access=ALL-UNNAMED,javafx.graphics --module-path $JavaFx --add-modules javafx.controls,javafx.fxml -cp client-build $test
  if($LASTEXITCODE){throw "$test failed"}
 }
}else{
 $entry=if($Mode -eq 'client'){'BattleJoker'}else{'ScoreboardApp'}
 $cp=(Join-Path $PSScriptRoot 'client-build')
 if($Mode -eq 'client'){Set-Location client}
 & $java --enable-native-access=ALL-UNNAMED,javafx.graphics --module-path $JavaFx --add-modules javafx.controls,javafx.fxml "-Dbj.host=$HostAddress" "-Dbj.tcp=$TcpPort" "-Dbj.udp=$UdpPort" "-Dbj.group=$Group" $(if($Nic){"-Dbj.nic=$Nic"}else{'-Dbj.mode=viewer'}) -cp $cp $entry
}

} finally { Pop-Location }
