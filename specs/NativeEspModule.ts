import {TurboModule, TurboModuleRegistry} from 'react-native';
import {EventEmitter, Int32} from 'react-native/Libraries/Types/CodegenTypes';

export interface EspDevice {
  deviceName: string;
  serviceUuid: string;
}

export interface EspWifi {
  name: string;
  rssi: Int32;
}

export interface Spec extends TurboModule {
  startScan: () => void;
  stopScan: () => void;
  connectBLEDevice: (serviceUuid: string) => void;
  connectWiFi: (ssid: string, pass: string) => Promise<boolean>;

  readonly onPeripheralFound: EventEmitter<EspDevice>;
  readonly onWifiScan: EventEmitter<Array<EspWifi>>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('NativeEspModule');
