# Guide d'intégration Flutter — Boîtier VoltCam Standard (BLE & WebSocket)

Ce document explique en détail comment connecter votre application **Flutter** native à l'application **VoltCam Box** (ce simulateur matériel Android) afin de tester la communication en temps réel, les mesures électriques et les scénarios de pannes.

---

## 1. Vue d'ensemble de la communication

L'application Android **VoltCam Box** agit comme le boîtier physique connecté. Elle propose deux canaux de communication :

1. **Bluetooth Low Energy (BLE GATT)** (Canal principal matériel) :
   - Émission d'annonces BLE sous le nom `VoltCam-001` (personnalisable).
   - Service GATT primaire dédié avec 5 caractéristiques (Lecture, Notifications, Indications, Écriture).
2. **WebSocket & HTTP Local** (Canal réseau/émulateur) :
   - Pratique si vous testez sur deux émulateurs Android ou via le réseau Wi-Fi local sans puces BLE réelles.
   - Serveur WebSocket actif sur `ws://<IP>:8080/ws` et HTTP API sur `http://<IP>:8080/api/device-info`.

---

## 2. Spécification des UUIDs BLE GATT

Voici les UUIDs exacts configurés dans le boîtier Android :

| Élément | UUID | Propriétés | Description |
|---|---|---|---|
| **Service Principal** | `4f4c5443-1000-8000-8000-00805f9b34fb` | Primary Service | Service VoltCam Standard |
| **Device Info** | `4f4c5443-1001-8000-8000-00805f9b34fb` | READ | Informations matérielles (JSON) |
| **Live Telemetry** | `4f4c5443-1002-8000-8000-00805f9b34fb` | NOTIFY | Mesures temps réel toutes les 1.5s (JSON) |
| **Event Stream** | `4f4c5443-1003-8000-8000-00805f9b34fb` | INDICATE / NOTIFY | Événements (OUTAGE, LAST_GASP, UNSTABLE) |
| **Device Health** | `4f4c5443-1004-8000-8000-00805f9b34fb` | NOTIFY | Heartbeat & état santé toutes les 5s (JSON) |
| **Configuration** | `4f4c5443-1005-8000-8000-00805f9b34fb` | READ / WRITE | Lecture/Écriture de la config (JSON) |

---

## 3. Formats des données JSON transmises

### A. Télémétrie Temps Réel (`live-telemetry`)
Exemple de trame reçue toutes les 1.5s :
```json
{
  "protocolVersion": 1,
  "sequence": 105,
  "sampledAt": "2026-07-25T11:20:00Z",
  "voltage": 220.4,
  "current": 2.35,
  "power": 491.5,
  "batteryPercent": 92,
  "frequency": 50.0,
  "powerFactor": 0.95,
  "isAcPowerPresent": true,
  "qualityState": "STABLE"
}
```
*`qualityState` peut valoir : `STABLE`, `LOW_VOLTAGE`, `HIGH_VOLTAGE`, `UNSTABLE`, `OUTAGE`.*

### B. Flux d'Événements (`event-stream`)
Exemple lors d'une coupure de courant avec Dernier Souffle :
```json
{
  "eventId": "VTC-2026-DEMO-001-4822",
  "type": "OUTAGE",
  "occurredAt": "2026-07-25T11:20:05Z",
  "lastGasp": true,
  "summary": {
    "voltageBeforeLoss": 218.7,
    "batteryPercent": 93,
    "description": "Coupure brute détectée. Signal de dernier souffle émis."
  }
}
```
*`type` peut valoir : `OUTAGE`, `VOLTAGE_UNSTABLE`, `RESTORED`, `DEVICE_DISCONNECTED`, `TAMPER_SUSPECTED`.*

---

## 4. Implémentation côté Flutter

### Étape 1 : Ajouter les packages Flutter requis
Dans votre projet Flutter (`pubspec.yaml`) :
```yaml
dependencies:
  flutter:
    sdk: flutter
  flutter_blue_plus: ^1.32.0   # Pour le Bluetooth LE nativement
  web_socket_channel: ^3.0.0   # Optionnel : pour WebSocket local
  permission_handler: ^11.3.0  # Pour gérer les permissions Bluetooth/Location
```

### Étape 2 : Service d'appairage & connexion BLE en Dart

Voici le code Dart propre et réutilisable pour votre application Flutter :

```dart
import 'dart:async';
import 'dart:convert';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';

class VoltCamBleService {
  static const String serviceUuid = "4f4c5443-1000-8000-8000-00805f9b34fb";
  static const String telemetryUuid = "4f4c5443-1002-8000-8000-00805f9b34fb";
  static const String eventUuid = "4f4c5443-1003-8000-8000-00805f9b34fb";

  BluetoothDevice? _connectedDevice;
  StreamSubscription? _telemetrySub;
  StreamSubscription? _eventSub;

  // StreamControllers pour mettre à jour votre UI Flutter (Riverpod/Provider/Bloc)
  final _telemetryController = StreamController<Map<String, dynamic>>.broadcast();
  final _eventController = StreamController<Map<String, dynamic>>.broadcast();

  Stream<Map<String, dynamic>> get telemetryStream => _telemetryController.stream;
  Stream<Map<String, dynamic>> get eventStream => _eventController.stream;

  /// 1. Démarrer le scan BLE pour trouver "VoltCam-001"
  Future<void> startScanAndConnect({required Function(BluetoothDevice) onDeviceFound}) async {
    // Vérifier et démarrer le scan
    await FlutterBluePlus.startScan(
      withServices: [Guid(serviceUuid)], // Filtre direct sur le service VoltCam
      timeout: const Duration(seconds: 15),
    );

    FlutterBluePlus.scanResults.listen((results) {
      for (ScanResult r in results) {
        if (r.device.platformName.contains("VoltCam") ||
            r.advertisementData.serviceUuids.contains(Guid(serviceUuid))) {
          FlutterBluePlus.stopScan();
          onDeviceFound(r.device);
          connectToDevice(r.device);
          break;
        }
      }
    });
  }

  /// 2. Se connecter au boîtier et s'abonner aux caractéristiques
  Future<void> connectToDevice(BluetoothDevice device) async {
    _connectedDevice = device;
    await device.connect(autoConnect: false);

    // Découvrir les services GATT
    List<BluetoothService> services = await device.discoverServices();
    BluetoothService? voltCamService = services.firstWhere(
      (s) => s.uuid == Guid(serviceUuid),
    );

    for (var characteristic in voltCamService.characteristics) {
      // A. S'abonner à la Télémétrie temps réel
      if (characteristic.uuid == Guid(telemetryUuid)) {
        await characteristic.setNotifyValue(true);
        _telemetrySub = characteristic.lastValueStream.listen((value) {
          if (value.isNotEmpty) {
            String jsonStr = utf8.decode(value);
            Map<String, dynamic> data = jsonDecode(jsonStr);
            _telemetryController.add(data);
          }
        });
      }

      // B. S'abonner au flux d'Événements (Coupures, Instabilités)
      if (characteristic.uuid == Guid(eventUuid)) {
        await characteristic.setNotifyValue(true);
        _eventSub = characteristic.lastValueStream.listen((value) {
          if (value.isNotEmpty) {
            String jsonStr = utf8.decode(value);
            Map<String, dynamic> eventData = jsonDecode(jsonStr);
            _eventController.add(eventData);
          }
        });
      }
    }
  }

  /// Disconnect and cleanup
  Future<void> dispose() async {
    await _telemetrySub?.cancel();
    await _eventSub?.cancel();
    await _connectedDevice?.disconnect();
    await _telemetryController.close();
    await _eventController.close();
  }
}
```

---

## 5. Comment tester la démo de bout en bout

1. **Lancez l'application Android "VoltCam Box"** sur un téléphone ou un émulateur.
2. Vérifiez dans l'onglet **"BLE / Network"** que le serveur BLE indique `"Annonce BLE active (Découvrable)"`.
3. Vous pouvez cliquer sur **"Appairer"** dans le haut de l'écran pour afficher les paramètres JSON ou copier l'URL WebSocket.
4. Dans votre application **Flutter** :
   - Lancez le scan BLE ou écoutez le WebSocket (`ws://<IP>:8080/ws`).
   - Observez les mesures de tension évoluer en direct.
5. **Tester les scénarios de pannes dans l'onglet "Scénarios"** :
   - Cliquez sur **"Scénario 1 : Coupure avec Dernier Souffle"** -> Votre application Flutter reçoit immédiatement un événement `OUTAGE` avec `lastGasp: true`.
   - Cliquez sur **"Scénario 2 : Instabilité de Tension"** -> Votre application Flutter voit la tension osciller de 150V à 265V et reçoit `VOLTAGE_UNSTABLE`.
   - Cliquez sur **"Scénario 3 : Restauration"** -> Reçoit l'événement `RESTORED`.
   - Cliquez sur **"Scénario 4 : Déconnexion Suspecte"** -> Déclenche `DEVICE_DISCONNECTED`.
