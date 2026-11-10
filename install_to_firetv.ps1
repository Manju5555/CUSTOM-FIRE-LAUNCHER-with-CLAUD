param(
    [string]$FireTvIp = "192.168.29.29:5555"
)

$adb = "C:\Users\k02799\.gemini\antigravity\scratch\adb\platform-tools\adb.exe"
$apk = "$PSScriptRoot\FIRE_TV_CUSTOM_HS_VER1.apk"

Write-Host "Connecting to Fire TV at $FireTvIp..."
& $adb connect $FireTvIp

Write-Host "Installing FIRE_TV_CUSTOM_HS_VER1.apk..."
& $adb -s $FireTvIp install -r $apk

Write-Host "Granting permissions & enabling Home button Accessibility Service..."
& $adb -s $FireTvIp shell pm grant com.prasad.firehome android.permission.WRITE_SECURE_SETTINGS
& $adb -s $FireTvIp shell appops set com.prasad.firehome SYSTEM_ALERT_WINDOW allow
& $adb -s $FireTvIp shell settings put secure enabled_accessibility_services com.prasad.firehome/com.prasad.firehome.HomeAccessibilityService
& $adb -s $FireTvIp shell settings put secure accessibility_enabled 1

Write-Host "Launching Fire Home..."
& $adb -s $FireTvIp shell am start -n com.prasad.firehome/.HomeActivity
