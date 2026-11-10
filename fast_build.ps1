$b = "C:\Users\k02799\.gemini\antigravity\scratch"
$root = $PSScriptRoot
$jdk = (Get-ChildItem "$b\jdk" -Directory | Select-Object -First 1).FullName
$sdk = "$b\android-sdk"
$bt  = "$sdk\build-tools\34.0.0"
$androidJar = "$sdk\platforms\android-34\android.jar"
$proj = "$root\app\src\main"
$out  = "$root\build_out"

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force "$out\gen", "$out\obj", "$out\apk" | Out-Null

Write-Host "1. Compiling resources with aapt2..."
& "$bt\aapt2.exe" compile --dir "$proj\res" -o "$out\res.zip"
& "$bt\aapt2.exe" link -o "$out\apk\unaligned.apk" `
    -I $androidJar `
    --manifest "$proj\AndroidManifest.xml" `
    --java "$out\gen" `
    "$out\res.zip"

Write-Host "2. Compiling Java sources with javac..."
$javaFiles = Get-ChildItem -Path "$proj\java", "$out\gen" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& "$jdk\bin\javac.exe" --release 11 -cp $androidJar -d "$out\obj" $javaFiles

Write-Host "3. Converting bytecode to DEX with d8..."
$classFiles = Get-ChildItem -Path "$out\obj" -Recurse -Filter "*.class" | ForEach-Object { $_.FullName }
$env:JAVA_HOME = $jdk
& "$bt\d8.bat" --lib $androidJar --min-api 22 --output "$out\apk" $classFiles

Write-Host "4. Adding classes.dex to APK..."
Push-Location "$out\apk"
& "$jdk\bin\jar.exe" -uf "unaligned.apk" "classes.dex"
Pop-Location

Write-Host "5. Aligning APK with zipalign..."
& "$bt\zipalign.exe" -f -p 4 "$out\apk\unaligned.apk" "$out\apk\aligned.apk"

Write-Host "6. Generating debug keystore & signing APK..."
$ks = "$root\debug.keystore"
if (!(Test-Path $ks)) {
    & "$jdk\bin\keytool.exe" -genkeypair -keystore $ks -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=FireHome,O=Android,C=US"
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android --out "$out\FireHome.apk" "$out\apk\aligned.apk"
Copy-Item -Path "$out\FireHome.apk" -Destination "$root\FIRE_TV_CUSTOM_HS_VER1.apk" -Force

Get-Item "$root\FIRE_TV_CUSTOM_HS_VER1.apk" | Select-Object FullName, Length
