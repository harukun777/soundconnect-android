package io.soundconnect.android;

import java.util.Locale;

/** SDP service UUIDs are authoritative; model/cache values only order bounded attempts. */
final class ProtocolSelector {
    private ProtocolSelector() {}
    static int[] order(String[] services, String name, int remembered) {
        boolean legacy = false, modern = false;
        if (services != null) for (String uuid : services) {
            legacy |= BluetoothTransport.LEGACY.equalsIgnoreCase(uuid);
            modern |= BluetoothTransport.MODERN.equalsIgnoreCase(uuid);
        }
        int first;
        if (legacy != modern) first = modern ? 2 : 1;
        else if (remembered == 1 || remembered == 2) first = remembered;
        else {
            String model = normalize(name);
            first = model.startsWith("WH-1000XM3") || model.startsWith("WH-1000XM4")
                || model.startsWith("WF-1000XM3") || model.startsWith("WI-1000X") ? 1 : 2;
        }
        return new int[] {first, first == 1 ? 2 : 1};
    }
    static boolean isSony(String name) {
        String model = normalize(name);
        return model.startsWith("WH-") || model.startsWith("WF-") || model.startsWith("WI-")
            || model.startsWith("LINKBUDS") || model.startsWith("ULT WEAR");
    }
    private static String normalize(String name) { return name == null ? "" : name.trim().toUpperCase(Locale.ROOT); }
}
