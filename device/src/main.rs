use esp32_nimble::utilities::BleUuid;
use esp32_nimble::{BLEAdvertisementData, BLEDevice};
use esp32_nimble::enums::{ConnMode, DiscMode};
use esp_idf_svc::hal::delay::FreeRtos;

const SERVICE_DATA: BleUuid = BleUuid::from_uuid32(0x9f3b8e2a); // Random UUID for identifying the
// umbrella between all advertisements.
const APPEARANCE_CODE: u16 = 512; // Mark "tag" as the device category (check Bluetooth assigned
// numbers document, section 2.6.2).

fn main() {
    // It is necessary to call this function once. Otherwise some patches to the runtime
    // implemented by esp-idf-sys might not link properly.
    // See https://github.com/esp-rs/esp-idf-template/issues/71.
    esp_idf_svc::sys::link_patches();

    // Bind the log crate to the ESP Logging facilities
    esp_idf_svc::log::EspLogger::initialize_default();

    log::info!("Setting up.");

    let ble_device = BLEDevice::take();
    let ble_advertiser = ble_device.get_advertising();

    let mut advertisement_data = BLEAdvertisementData::new();
    advertisement_data.appearance(APPEARANCE_CODE);
    advertisement_data.add_service_uuid(SERVICE_DATA);

    ble_advertiser.lock()
        .set_data(&mut advertisement_data)
        .expect("Failed to set advertisement data");

    // Set some advertising configurations. The intervals define how often the advertisement will
    // be sent. Shorter intervals will ensure more resolution for the signal intensity tracking in
    // the app side, but will also cause more power to be drawn. The numbers provided are units of
    // 0.625 ms. The minimum interval possible is 20 ms (so 32 units).
    // The other settings are for giving hints about connectability and discoverability details.
    // As our application is a closed system, they aren't really useful for us. So we can just let
    // them this way.
    ble_advertiser.lock()
        .min_interval(32)
        .max_interval(64)
        .advertisement_type(ConnMode::Non)
        .disc_mode(DiscMode::Gen)
        .scan_response(false);

    log::info!("BLE advertisement is set up and is starting now");
    ble_advertiser.lock()
        .start()
        .expect("Failed to start advertisement");

    loop {
        FreeRtos::delay_ms(10);
    }
}
