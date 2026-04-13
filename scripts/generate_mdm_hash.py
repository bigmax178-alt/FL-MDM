import hashlib
import base64
import os
import subprocess
import json

def get_signature_checksum(apk_path):
    """
    Извлекает SHA-256 хеш сертификата подписи из APK файла.
    Требует установленного 'keytool' (идет вместе с JDK/Android Studio).
    """
    try:
        # Запускаем keytool для получения информации о сертификате
        result = subprocess.run(
            ['keytool', '-printcert', '-jarfile', apk_path],
            capture_output=True,
            text=True,
            check=True
        )
        
        # Ищем строку с SHA256
        for line in result.stdout.splitlines():
            if "SHA256:" in line:
                # Извлекаем hex-строку
                sha256_hex = line.split("SHA256:")[1].strip().replace(":", "")
                # Конвертируем hex в байты
                sha256_bytes = bytes.fromhex(sha256_hex)
                # Конвертируем в Base64 URL-safe (без дополнения '=')
                checksum = base64.urlsafe_b64encode(sha256_bytes).decode('utf-8').rstrip('=')
                return checksum
    except Exception as e:
        print(f"Error extracting signature: {e}")
        return None

def main():
    apk_url = "https://github.com/puntusovdima/FoodLabelPro/releases/download/v1.9.19/FoodLabelPro.apk"
    apk_file = "app.apk"
    
    print(f"--- Утилита для настройки MDM ---")
    
    # Скачиваем APK, если его еще нет
    if not os.path.exists(apk_file):
        print(f"Скачивание APK из {apk_url}...")
        try:
            subprocess.run(['curl', '-L', apk_url, '-o', apk_file], check=True)
        except Exception as e:
            print(f"Ошибка при скачивании APK: {e}")
            return

    checksum = get_signature_checksum(apk_file)
    
    if checksum:
        print(f"\nУСПЕХ!")
        print(f"Ваш Signature Checksum (Base64 URL-safe):")
        print(f"\033[92m{checksum}\033[0m")
        
        payload = {
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.example.firebaselabelapp/com.example.firebaselabelapp.kiosk.KioskDeviceAdminReceiver",
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": apk_url,
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": checksum,
            "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": True,
            "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": True
        }
        
        print(f"\n--- JSON ДЛЯ НАСТРОЙКИ (QR-КОДА) ---")
        print(json.dumps(payload, indent=2))
        print(f"\nОтсканируйте этот JSON с помощью генератора QR-кодов для регистрации устройств.")
    else:
        print("\nОШИБКА: Не удалось извлечь подпись. Убедитесь, что 'keytool' установлен и доступен в PATH.")

if __name__ == "__main__":
    main()
