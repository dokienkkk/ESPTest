import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
  Button,
  EventSubscription,
  FlatList,
  ListRenderItem,
  Pressable,
  Text,
  View,
} from 'react-native';
import NativeEspModule, {EspDevice} from './specs/NativeEspModule';

const App = () => {
  // Lắng nghe event từ Telink
  const espSubscription = useRef<EventSubscription | null>(null);

  const [espDevices, setEspDevices] = useState<Array<EspDevice>>([]);

  useEffect(() => {
    espSubscription.current = NativeEspModule.onPeripheralFound(data => {
      console.log('onPeripheralFound', data);
      setEspDevices(prev => [...prev, data]);
    });
    return () => {
      espSubscription.current?.remove();
      espSubscription.current = null;
    };
  }, []);

  const renderItem: ListRenderItem<EspDevice> = useCallback(({item}) => {
    return (
      <Pressable
        onPress={() => {
          NativeEspModule.connectBLEDevice(item.serviceUuid);
        }}>
        <Text>{item.deviceName}</Text>
      </Pressable>
    );
  }, []);

  return (
    <View style={{flex: 1, gap: 12}}>
      <Button
        title="Start Scan"
        onPress={() => {
          NativeEspModule.startScan();
        }}
      />

      <Button
        title="Stop scan"
        onPress={() => {
          NativeEspModule.stopScan();
        }}
      />

      <Button
        title="Test Provision"
        onPress={() => {
          NativeEspModule.connectWiFi('DigitalR&D', 'DigitalRD@2804');
        }}
      />

      <FlatList
        data={espDevices}
        renderItem={renderItem}
        contentContainerStyle={{paddingHorizontal: 12, gap: 12}}
      />
    </View>
  );
};

export default App;
