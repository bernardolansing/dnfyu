**dnfyu** is a smart tag-like product made with an ESP32 matched with an Android app. It served as the final assignment
for the embedded systems course. The motivation behind it was that countless times I have forgotten perfectly fine
umbrellas at stores, classrooms or in the bus, so I'd always have to go there and buy another one.

Once turned on, the ESP32 (device/tag) will start to emit BLE (Bluetooth Low Energy) advertisements. These advertising
packets are going to be captured by the Android app, which will profile the RSSI (signal intensity) and the packet
receipt rate.

If these metrics drop too low, it means that the smartphone and the umbrella were moved away from each other, probably
because the latter one has been forgotten. So in response, the app will ring a very unpleasant audio track of me
shouting "Hey dude you've forgotten your umbrella!". Hopefully that prevents me from having to buy three of them a year.

It's worth noticing that the app is clever enough to detect when the umbrella is turned off, so the alarm won't trigger
when you arrive home.

The biggest TODO left for this project was to configure the monitoring to happen under a foreground/background service.
Right now, the app can be closed without this interfering in the scan, but if many apps are launched after that the
Android system might shut `dnfyu` down to preserve resources. In that case, the protection would cease.
